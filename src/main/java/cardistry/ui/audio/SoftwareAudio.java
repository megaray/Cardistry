package cardistry.ui.audio;

import cardistry.utils.Log;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Son réel : une seule sortie audio (44,1 kHz, stéréo, 16 bits) alimentée par un fil qui mixe
 * ({@link Mixer}) par blocs de {@link #BLOCK_FRAMES} images (≈ 23 ms). Les effets sont chargés en
 * mémoire au démarrage ; les musiques sont décodées dans un fil à part au premier usage, puis gardées.
 */
final class SoftwareAudio implements Audio {

    static final int BLOCK_FRAMES = 1024;
    private static final AudioFormat FORMAT = new AudioFormat(Pcm.RATE, 16, 2, true, false);

    private final SoundTable table;
    private final Mixer mixer = new Mixer();
    private final SourceDataLine line;
    private final boolean noMusic;
    private final Map<String, Optional<Pcm>> cache = new HashMap<>();
    private final Map<String, Integer> nextVariant = new HashMap<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> daemon(r, "cardistry-musique"));
    private volatile boolean running = true;
    private MusicContext context;

    private SoftwareAudio(SoundTable table, SourceDataLine line, boolean noMusic) {
        this.table = table;
        this.line = line;
        this.noMusic = noMusic;
    }

    /** Ouvre la sortie audio ; vide si elle est indisponible (pas de carte son, CI…). */
    static Optional<Audio> start(SoundTable table, int music, int effects, boolean muted, boolean noMusic) {
        SourceDataLine line;
        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, BLOCK_FRAMES * 4 * 4);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            Log.warn("Pas de sortie audio : jeu muet", e);
            return Optional.empty();
        }
        SoftwareAudio audio = new SoftwareAudio(table, line, noMusic);
        audio.setLevels(music, effects, muted);
        audio.loader.submit(audio::preloadEffects);
        daemon(audio::pump, "cardistry-audio").start();
        return Optional.of(audio);
    }

    private static Thread daemon(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        return thread;
    }

    /** Fil audio : mixe et envoie à la sortie, sans fin (write bloque au rythme de la carte son). */
    private void pump() {
        float[] mix = new float[BLOCK_FRAMES * 2];
        byte[] bytes = new byte[BLOCK_FRAMES * 4];
        try {
            while (running) {
                mixer.mix(mix, BLOCK_FRAMES);
                for (int i = 0; i < mix.length; i++) {
                    int value = Math.round(mix[i] * 32767);
                    bytes[i * 2] = (byte) value;
                    bytes[i * 2 + 1] = (byte) (value >> 8);
                }
                line.write(bytes, 0, bytes.length);
            }
        } catch (RuntimeException e) {
            // Sortie audio perdue (périphérique débranché…) : on continue sans son.
            Log.warn("Sortie audio perdue : suite sans son", e);
        } finally {
            line.close();
        }
    }

    private void preloadEffects() {
        for (SoundTable.Entry entry : table.entries().values()) {
            if (entry.name().startsWith("sfx.")) {
                entry.files().forEach(this::pcm);
            }
        }
    }

    private Optional<Pcm> pcm(String file) {
        synchronized (cache) {
            return cache.computeIfAbsent(file, Pcm::load);
        }
    }

    @Override
    public void effect(String name) {
        table.get(name).ifPresent(entry -> {
            List<String> files = entry.files();
            int index;
            synchronized (nextVariant) {
                index = nextVariant.getOrDefault(name, 0);
                nextVariant.put(name, (index + 1) % files.size());
            }
            Optional<Pcm> pcm;
            synchronized (cache) {
                pcm = cache.get(files.get(index));
            }
            if (pcm == null) {
                pcm = pcm(files.get(index)); // pas encore préchargé : chargé maintenant (petit fichier)
            }
            pcm.ifPresent(p -> mixer.play(p, (float) entry.volume()));
        });
    }

    @Override
    public synchronized void music(MusicContext newContext) {
        if (newContext == context) {
            return;
        }
        context = newContext;
        if (noMusic || newContext == null) {
            mixer.music(null, 0, 0, false);
            return;
        }
        Optional<SoundTable.Entry> entry = table.get(newContext.tableName());
        if (entry.isEmpty()) {
            mixer.music(null, 0, 0, false);
            return;
        }
        SoundTable.Entry e = entry.get();
        loader.submit(() -> {
            Optional<Pcm> pcm = pcm(e.files().get(0));
            synchronized (this) {
                if (context == newContext) { // pas déjà changé pendant le chargement
                    mixer.music(pcm.orElse(null), (float) e.volume(), e.loopStart(), e.loops());
                }
            }
        });
    }

    @Override
    public void setLevels(int music, int effects, boolean muted) {
        mixer.setMusicGain(muted || noMusic ? 0f : Volume.gain(music));
        mixer.setEffectsGain(muted ? 0f : Volume.gain(effects));
    }

    @Override
    public void close() {
        running = false;
        loader.shutdownNow();
    }
}
