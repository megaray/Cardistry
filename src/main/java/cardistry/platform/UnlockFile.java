package cardistry.platform;

import cardistry.utils.Log;
import cardistry.run.Secret;
import cardistry.run.UnlockStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Secrets débloqués, dans {@code unlocks.txt} du dossier de sauvegarde ({@link SaveDirectory}).
 * Format texte, lisible et modifiable à la main :
 * <pre>
 * cardistry-unlocks 1
 * # Secrets débloqués, un par ligne
 * null-pointer-exception
 * </pre>
 * Fichier absent, illisible ou corrompu (en-tête manquant, ligne inconnue) : aucun secret, sans
 * planter ; il sera réécrit au prochain déblocage. Une erreur d'écriture est ignorée (la partie
 * continue, le déblocage n'est simplement pas gardé).
 */
public final class UnlockFile implements UnlockStore {

    public static final String FILE_NAME = "unlocks.txt";
    static final String HEADER = "cardistry-unlocks 1";

    private final Path file;

    /** Fichier dans le dossier de sauvegarde de cette machine. */
    public static UnlockFile inSaveDirectory() {
        return new UnlockFile(SaveDirectory.resolve().resolve(FILE_NAME));
    }

    /** Version testable : nom d'OS, variables d'environnement et dossier personnel injectés. */
    static UnlockFile inSaveDirectory(String osName, java.util.function.Function<String, String> env, Path home) {
        return new UnlockFile(SaveDirectory.resolve(osName, env, home).resolve(FILE_NAME));
    }

    public UnlockFile(Path file) {
        this.file = file;
    }

    public Path path() {
        return file;
    }

    @Override
    public Set<Secret> load() {
        return read().orElseGet(() -> EnumSet.noneOf(Secret.class));
    }

    /** Contenu du fichier ; vide s'il est absent, illisible ou corrompu. */
    Optional<Set<Secret>> read() {
        List<String> lines;
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException | SecurityException e) {
            Log.warn("Secrets débloqués illisibles (" + file + ")", e);
            return Optional.empty();
        }
        if (lines.isEmpty() || !lines.get(0).strip().equals(HEADER)) {
            Log.warn("Fichier des secrets sans en-tête (" + file + ") : considéré corrompu");
            return Optional.empty();
        }
        Set<Secret> secrets = EnumSet.noneOf(Secret.class);
        for (String raw : lines.subList(1, lines.size())) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Optional<Secret> secret = Secret.byTrinketId(line);
            if (secret.isEmpty()) {
                return Optional.empty(); // ligne inconnue : fichier corrompu
            }
            secrets.add(secret.get());
        }
        return Optional.of(secrets);
    }

    @Override
    public void save(Set<Secret> unlocked) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add("# Secrets débloqués, un par ligne (spoilers : docs/SECRETS.md)");
        for (Secret secret : Secret.values()) {
            if (unlocked.contains(secret)) {
                lines.add(secret.trinketId());
            }
        }
        Path temp = null;
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            // Écrit à côté puis remplace : un arrêt brutal ne laisse pas un fichier à moitié écrit.
            temp = Files.createTempFile(dir, "unlocks", ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | SecurityException e) {
            Log.warn("Sauvegarde des secrets impossible (" + file + ")", e); // on joue quand même
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void deleteQuietly(Path temp) {
        if (temp == null) {
            return;
        }
        try {
            Files.deleteIfExists(temp);
        } catch (IOException | SecurityException e) {
            // Tant pis : un fichier temporaire oublié ne gêne pas le jeu.
        }
    }

    @Override
    public void reset() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException | SecurityException e) {
            Log.warn("Suppression des secrets impossible (" + file + ") : fichier vidé", e);
            save(EnumSet.noneOf(Secret.class));
        }
    }
}
