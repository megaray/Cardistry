package cardistry.ui.audio;

import cardistry.utils.Log;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Son décodé en mémoire : échantillons flottants stéréo entrelacés (gauche, droite…) à
 * {@link #RATE} Hz, quel que soit le fichier d'origine (WAV 8/16 bits, mono ou stéréo, toute
 * fréquence ; OGG Vorbis grâce à VorbisSPI).
 */
public final class Pcm {

    public static final int RATE = 44_100;

    private final float[] samples;

    public Pcm(float[] samples) {
        this.samples = samples;
    }

    /** Échantillons entrelacés gauche/droite. */
    public float[] samples() {
        return samples;
    }

    /** Nombre d'images (une image = un échantillon gauche + un droit). */
    public int frames() {
        return samples.length / 2;
    }

    // --- Normalisation ---

    /** Niveau visé pour tous les sons : RMS de leurs passages forts, en dBFS. */
    public static final double TARGET_DB = -18;
    /** Crête maximale après normalisation (un peu sous 1 : pas de saturation). */
    static final double PEAK_LIMIT = 0.97;
    /** Amplification maximale : au-delà, on ne ferait que monter le bruit de fond. */
    static final double MAX_BOOST_DB = 30;
    /** Sous ce niveau de crête (dBFS), le fichier est considéré comme silencieux et laissé tel quel. */
    static final double SILENCE_DB = -70;
    private static final int WINDOW = RATE / 50; // 20 ms
    private static final double GATE_DB = 20;

    /**
     * Niveau perçu, en dBFS : RMS des fenêtres de 20 ms situées à moins de {@value #GATE_DB} dB de la
     * plus forte. Les silences et les queues de réverbération ne comptent donc pas : un son court
     * suivi de silence et une musique continue se comparent. {@code -Infinity} pour un son vide.
     */
    public double loudnessDb() {
        int frames = frames();
        int windows = Math.max(1, (frames + WINDOW - 1) / WINDOW);
        double[] power = new double[windows];
        double loudest = 0;
        for (int w = 0; w < windows; w++) {
            int from = w * WINDOW;
            int to = Math.min(frames, from + WINDOW);
            double sum = 0;
            for (int i = from * 2; i < to * 2; i++) {
                sum += samples[i] * samples[i];
            }
            power[w] = to > from ? sum / ((to - from) * 2) : 0;
            loudest = Math.max(loudest, power[w]);
        }
        if (loudest <= 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double gate = loudest * Math.pow(10, -GATE_DB / 10);
        double sum = 0;
        int kept = 0;
        for (double p : power) {
            if (p >= gate) {
                sum += p;
                kept++;
            }
        }
        return 10 * Math.log10(sum / kept);
    }

    /** Crête : plus grande valeur absolue d'un échantillon (0 à 1). */
    public double peak() {
        double peak = 0;
        for (float sample : samples) {
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    /**
     * Le même son ramené à {@link #TARGET_DB} : tous les fichiers sonnent au même niveau, quel que soit
     * leur enregistrement ; le volume de {@code sounds.txt} règle ensuite le mixage. Le gain est limité
     * par la crête ({@link #PEAK_LIMIT} : un son très dynamique reste un peu sous le niveau visé plutôt
     * que de saturer) et par {@link #MAX_BOOST_DB}. Un fichier silencieux n'est pas touché.
     */
    public Pcm normalized() {
        double peak = peak();
        if (peak < Math.pow(10, SILENCE_DB / 20)) {
            return this;
        }
        double gain = Math.pow(10, Math.min(MAX_BOOST_DB, TARGET_DB - loudnessDb()) / 20);
        gain = Math.min(gain, PEAK_LIMIT / peak);
        float[] out = new float[samples.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (samples[i] * gain);
        }
        return new Pcm(out);
    }

    /** Son du fichier {@code /audio/<path>}, normalisé ({@link #normalized()}) ; vide s'il manque ou est illisible. */
    public static Optional<Pcm> load(String path) {
        try (InputStream raw = Pcm.class.getResourceAsStream("/audio/" + path)) {
            if (raw == null) {
                Log.warn("Son manquant : audio/" + path);
                return Optional.empty();
            }
            return Optional.of(decode(new BufferedInputStream(raw)).normalized());
        } catch (IOException | UnsupportedAudioFileException | IllegalArgumentException e) {
            Log.warn("Son illisible : audio/" + path, e);
            return Optional.empty();
        }
    }

    /** Décode un flux audio (WAV, OGG…) en PCM stéréo 44,1 kHz. */
    static Pcm decode(InputStream in) throws IOException, UnsupportedAudioFileException {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(in)) {
            AudioFormat base = source.getFormat();
            AudioInputStream pcm = source;
            if (base.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
                    && base.getEncoding() != AudioFormat.Encoding.PCM_UNSIGNED) {
                // OGG Vorbis (VorbisSPI) : décodage en PCM 16 bits à la fréquence d'origine.
                AudioFormat decoded = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, base.getSampleRate(), 16,
                        base.getChannels(), base.getChannels() * 2, base.getSampleRate(), false);
                pcm = AudioSystem.getAudioInputStream(decoded, source);
            }
            AudioFormat format = pcm.getFormat();
            byte[] bytes = readAll(pcm);
            int channels = format.getChannels();
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            int frames = bytes.length / (bytesPerSample * channels);
            float[] stereo = new float[frames * 2];
            for (int f = 0; f < frames; f++) {
                for (int c = 0; c < 2; c++) {
                    int channel = Math.min(c, channels - 1);
                    int offset = (f * channels + channel) * bytesPerSample;
                    stereo[f * 2 + c] = sample(bytes, offset, format);
                }
            }
            return new Pcm(resample(stereo, format.getSampleRate()));
        }
    }

    private static byte[] readAll(AudioInputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        // Le décodeur OGG renvoie parfois 0 octet avant la fin : on lit jusqu'à -1.
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Un échantillon dans [-1, 1]. */
    private static float sample(byte[] bytes, int offset, AudioFormat format) {
        int bits = format.getSampleSizeInBits();
        if (bits == 8) {
            int value = bytes[offset];
            if (format.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
                value = (value & 0xFF) - 128;
            }
            return value / 128f;
        }
        int lo = format.isBigEndian() ? bytes[offset + 1] : bytes[offset];
        int hi = format.isBigEndian() ? bytes[offset] : bytes[offset + 1];
        int value = (hi << 8) | (lo & 0xFF);
        if (format.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
            value = (value & 0xFFFF) - 32768;
        }
        return value / 32768f;
    }

    /** Rééchantillonnage linéaire vers {@link #RATE} (rien à faire si la fréquence y est déjà). */
    static float[] resample(float[] stereo, float rate) {
        if (Math.abs(rate - RATE) < 1) {
            return stereo;
        }
        int frames = stereo.length / 2;
        int outFrames = (int) Math.round(frames * (double) RATE / rate);
        float[] out = new float[outFrames * 2];
        double step = rate / (double) RATE;
        for (int f = 0; f < outFrames; f++) {
            double pos = f * step;
            int i = (int) pos;
            double t = pos - i;
            int j = Math.min(i + 1, frames - 1);
            for (int c = 0; c < 2; c++) {
                float a = stereo[Math.min(i, frames - 1) * 2 + c];
                float b = stereo[j * 2 + c];
                out[f * 2 + c] = (float) (a + (b - a) * t);
            }
        }
        return out;
    }
}
