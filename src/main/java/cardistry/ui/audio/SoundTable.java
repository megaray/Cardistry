package cardistry.ui.audio;

import cardistry.utils.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Table son → fichier, lue dans {@code src/main/resources/audio/sounds.txt} :
 * <pre>
 * # commentaire
 * music.combat = music/combat.ogg ; loop=4.0 ; volume=0.9
 * music.victory = music/victory.ogg ; loop=no
 * sfx.choomah = sfx/choomah-1.wav, sfx/choomah-2.wav ; volume=0.7
 * </pre>
 * Plusieurs fichiers = variantes, jouées chacune à son tour. {@code loop} (musiques) : point de
 * boucle en secondes (0 par défaut), ou {@code no} pour jouer une seule fois. Ligne illisible :
 * ignorée.
 */
public final class SoundTable {

    /** Une entrée de la table. */
    public record Entry(String name, List<String> files, double volume, double loopStart, boolean loops) {
    }

    private final Map<String, Entry> entries;

    private SoundTable(Map<String, Entry> entries) {
        this.entries = entries;
    }

    public Optional<Entry> get(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    public Map<String, Entry> entries() {
        return entries;
    }

    /** La table du jeu ({@code /audio/sounds.txt}) ; vide si elle manque. */
    public static SoundTable load() {
        try (InputStream in = SoundTable.class.getResourceAsStream("/audio/sounds.txt")) {
            return in == null ? parse("") : parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.warn("Table des sons illisible : jeu sans effets", e);
            return parse("");
        }
    }

    public static SoundTable parse(String text) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (String raw : text.lines().toList()) {
            String line = raw.strip();
            int eq = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || eq <= 0) {
                continue;
            }
            String name = line.substring(0, eq).strip();
            String[] parts = line.substring(eq + 1).split(";");
            List<String> files = new ArrayList<>();
            for (String file : parts[0].split(",")) {
                if (!file.isBlank()) {
                    files.add(file.strip());
                }
            }
            double volume = 1.0;
            double loopStart = 0;
            boolean loops = true;
            try {
                for (int i = 1; i < parts.length; i++) {
                    String[] option = parts[i].split("=", 2);
                    String key = option[0].strip();
                    String value = option.length > 1 ? option[1].strip() : "";
                    switch (key) {
                        case "volume" -> volume = Math.max(0, Math.min(1, Double.parseDouble(value)));
                        case "loop" -> {
                            if (value.equalsIgnoreCase("no") || value.equalsIgnoreCase("non")) {
                                loops = false;
                            } else {
                                loopStart = Math.max(0, Double.parseDouble(value));
                            }
                        }
                        default -> {
                            // option inconnue : ignorée
                        }
                    }
                }
            } catch (NumberFormatException e) {
                Log.warn("Ligne de sounds.txt ignorée : " + line);
                continue;
            }
            if (!files.isEmpty()) {
                entries.put(name, new Entry(name, List.copyOf(files), volume, loopStart, loops));
            }
        }
        return new SoundTable(entries);
    }
}
