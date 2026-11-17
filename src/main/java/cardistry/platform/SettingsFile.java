package cardistry.platform;

import cardistry.utils.Log;
import cardistry.ui.Settings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Réglages du joueur, dans {@code settings.txt} du dossier de sauvegarde (à côté de
 * {@code unlocks.txt}). Format texte :
 * <pre>
 * cardistry-settings 1
 * music=6
 * effects=8
 * muted=false
 * </pre>
 * Fichier absent ou abîmé : réglages par défaut (chaque ligne illisible garde sa valeur par défaut).
 * Erreur d'écriture : ignorée (le jeu continue).
 */
public final class SettingsFile {

    public static final String FILE_NAME = "settings.txt";
    static final String HEADER = "cardistry-settings 1";

    private final Path file;

    public SettingsFile(Path file) {
        this.file = file;
    }

    public static SettingsFile inSaveDirectory() {
        return new SettingsFile(SaveDirectory.resolve().resolve(FILE_NAME));
    }

    public Path path() {
        return file;
    }

    public Settings load() {
        Settings settings = Settings.defaults();
        List<String> lines;
        try {
            if (!Files.isRegularFile(file)) {
                return settings;
            }
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException | SecurityException e) {
            Log.warn("Réglages illisibles (" + file + ") : valeurs par défaut", e);
            return settings;
        }
        if (lines.isEmpty() || !lines.get(0).strip().equals(HEADER)) {
            Log.warn("Réglages sans en-tête (" + file + ") : valeurs par défaut");
            return settings;
        }
        for (String raw : lines.subList(1, lines.size())) {
            String[] kv = raw.strip().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String value = kv[1].strip();
            try {
                settings = switch (kv[0].strip()) {
                    case "music" -> settings.withMusic(Integer.parseInt(value));
                    case "effects" -> settings.withEffects(Integer.parseInt(value));
                    case "muted" -> value.equals("true") || value.equals("false")
                            ? settings.withMuted(Boolean.parseBoolean(value)) : settings;
                    default -> settings;
                };
            } catch (NumberFormatException e) {
                Log.warn("Réglage illisible ignoré : " + raw.strip());
            }
        }
        return settings;
    }

    public void save(Settings settings) {
        List<String> lines = List.of(HEADER, "music=" + settings.music(), "effects=" + settings.effects(),
                "muted=" + settings.muted());
        Path temp = null;
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, "settings", ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException e) {
            Log.warn("Sauvegarde des réglages impossible (" + file + ")", e); // on joue quand même
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException | SecurityException e) {
                    // Tant pis.
                }
            }
        }
    }
}
