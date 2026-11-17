package cardistry.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogTest {

    private Log.Level before;

    @BeforeEach
    void setUp() {
        before = Log.level();
        Log.clearMemory();
    }

    @AfterEach
    void tearDown() {
        Log.setLevel(before);
        Log.clearMemory();
    }

    @Test
    void sousLeNiveauRienNEstGarde() {
        Log.setLevel(Log.Level.INFO);
        Log.debug("bavard");
        Log.info("utile");
        List<Log.Entry> lines = Log.recent();
        assertEquals(1, lines.size());
        assertEquals("utile", lines.get(0).message());
        assertEquals(Log.Level.INFO, lines.get(0).level());
    }

    @Test
    void debugGardeTout() {
        Log.setLevel(Log.Level.DEBUG);
        Log.debug("bavard");
        assertEquals(1, Log.recent().size());
    }

    @Test
    void lAppelantEstRetrouve() {
        Log.info("ici");
        assertTrue(Log.recent().get(0).location().startsWith("LogTest.java:"),
                Log.recent().get(0).location());
    }

    @Test
    void uneExceptionEcritSaTrace() {
        Log.warn("raté", new IllegalStateException("boum"));
        String message = Log.recent().get(0).message();
        assertTrue(message.contains("IllegalStateException: boum"), message);
        assertTrue(message.contains("LogTest"), message); // la pile
    }

    @Test
    void laMemoireEstBornee() {
        for (int i = 0; i < Log.MEMORY + 50; i++) {
            Log.info("ligne " + i);
        }
        List<Log.Entry> lines = Log.recent();
        assertEquals(Log.MEMORY, lines.size());
        assertEquals("ligne " + (Log.MEMORY + 49), lines.get(lines.size() - 1).message());
        assertEquals(lines.get(lines.size() - 1).id(), Log.lastId());
    }

    @Test
    void leFichierEstEcritEtLAncienGarde(@TempDir Path dir) throws Exception {
        String off = System.getProperty("cardistry.log");
        System.clearProperty("cardistry.log");
        try {
            Path file = dir.resolve("logs").resolve("debug.log");
            Log.setFile(file);
            Log.info("première partie");
            Log.setFile(file); // relance du jeu
            Log.info("deuxième partie");
            assertEquals(file, Log.file().orElseThrow());
            String current = Files.readString(file);
            assertTrue(current.contains("[INFO]") && current.contains("deuxième partie"), current);
            assertFalse(current.contains("première partie"));
            assertTrue(Files.readString(dir.resolve("logs").resolve("debug.1.log")).contains("première partie"));
        } finally {
            if (off != null) {
                System.setProperty("cardistry.log", off);
            }
            Log.setFile(dir.resolve("unused.log")); // ferme le fichier ; rouvert seulement si propriété absente
        }
    }
}
