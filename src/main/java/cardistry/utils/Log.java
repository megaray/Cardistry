package cardistry.utils;

import cardistry.platform.SaveDirectory;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Journal de débogage : {@code debug.log} dans le dossier de sauvegarde (voir {@link SaveDirectory}),
 * plus les dernières lignes en mémoire pour la console de développement.
 *
 * <ul>
 *   <li>Niveaux {@link Level} : INFO et au-delà par défaut, tout avec {@code --debug}.</li>
 *   <li>Le fichier n'est ouvert qu'au premier message ; le précédent est gardé en {@code debug.1.log}
 *       (utile après un plantage : relancer le jeu n'efface pas la trace).</li>
 *   <li>{@code -Dcardistry.log=off} (posé par les tests) : aucun fichier, la mémoire reste remplie.</li>
 *   <li>Le fichier et la classe:ligne de l'appelant sont ajoutés automatiquement.</li>
 * </ul>
 * N'affiche jamais rien à l'écran : on peut l'appeler depuis la logique.
 */
public final class Log {

    /** Gravité d'un message, du plus bavard au plus grave. */
    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /** Une ligne du journal. */
    public record Entry(long id, LocalTime time, Level level, String location, String message) {

        /** Ligne telle qu'écrite dans le fichier. */
        public String format() {
            return "[" + time.format(TIME_FORMAT) + "] [" + level + "] [" + location + "] " + message;
        }
    }

    /** Lignes gardées en mémoire pour la console de développement. */
    public static final int MEMORY = 300;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final Deque<Entry> recent = new ArrayDeque<>();

    private static Level threshold = Level.INFO;
    private static Path file = null; // null = dossier de sauvegarde, résolu à l'ouverture
    private static PrintWriter writer;
    private static boolean opened = false;
    private static long nextId = 0;

    private Log() {
    }

    /** Niveau minimal journalisé (fichier et mémoire). */
    public static synchronized void setLevel(Level level) {
        threshold = level;
    }

    public static synchronized Level level() {
        return threshold;
    }

    public static synchronized boolean enabled(Level level) {
        return level.compareTo(threshold) >= 0;
    }

    /** Fichier du journal ; à appeler avant le premier message (sinon : dossier de sauvegarde). */
    public static synchronized void setFile(Path path) {
        closeFile();
        file = path;
        opened = false;
    }

    /** Fichier effectivement ouvert, s'il y en a un. */
    public static synchronized java.util.Optional<Path> file() {
        return writer == null ? java.util.Optional.empty() : java.util.Optional.ofNullable(file);
    }

    public static void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    public static void info(String message) {
        log(Level.INFO, message, null);
    }

    public static void warn(String message) {
        log(Level.WARN, message, null);
    }

    public static void warn(String message, Throwable error) {
        log(Level.WARN, message, error);
    }

    public static void error(String message, Throwable error) {
        log(Level.ERROR, message, error);
    }

    /** Ancien nom : un message INFO. */
    public static void println(String message) {
        log(Level.INFO, message, null);
    }

    /** Copie des dernières lignes, de la plus ancienne à la plus récente. */
    public static synchronized List<Entry> recent() {
        return new ArrayList<>(recent);
    }

    /** Identifiant de la dernière ligne : change à chaque nouveau message (−1 si aucun). */
    public static synchronized long lastId() {
        return nextId - 1;
    }

    /** Vide la mémoire (tests). */
    public static synchronized void clearMemory() {
        recent.clear();
    }

    private static void log(Level level, String message, Throwable error) {
        if (!enabled(level)) {
            return;
        }
        String location = WALKER.walk(frames -> frames
                .filter(f -> !f.getClassName().equals(Log.class.getName()))
                .findFirst()
                .map(f -> f.getFileName() + ":" + f.getLineNumber())
                .orElse("?"));
        String text = error == null ? message : message + " — " + error + "\n" + stackTrace(error);
        write(level, location, text);
    }

    private static synchronized void write(Level level, String location, String message) {
        Entry entry = new Entry(nextId++, LocalTime.now(), level, location, message);
        recent.addLast(entry);
        while (recent.size() > MEMORY) {
            recent.removeFirst();
        }
        PrintWriter out = writer();
        if (out != null) {
            out.println(entry.format());
        }
    }

    private static String stackTrace(Throwable error) {
        StringWriter text = new StringWriter();
        error.printStackTrace(new PrintWriter(text));
        return text.toString().stripTrailing();
    }

    /** Ouvre le fichier au premier message ; null si journal fichier coupé ou impossible. */
    private static PrintWriter writer() {
        if (opened) {
            return writer;
        }
        opened = true;
        if ("off".equals(System.getProperty("cardistry.log"))) {
            return null;
        }
        try {
            if (file == null) {
                file = SaveDirectory.resolve().resolve("debug.log");
            }
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(file)) {
                Files.move(file, file.resolveSibling("debug.1.log"), StandardCopyOption.REPLACE_EXISTING);
            }
            writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8), true);
        } catch (IOException | SecurityException | java.nio.file.InvalidPathException e) {
            writer = null; // pas de fichier : la mémoire suffit
        }
        return writer;
    }

    private static void closeFile() {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
