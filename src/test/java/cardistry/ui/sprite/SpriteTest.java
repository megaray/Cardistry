package cardistry.ui.sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.model.Enemy;
import cardistry.model.enemy.EnemyRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpriteTest {

    @Test
    void framesEtCouleurs() {
        Sprite sprite = Sprite.parse("""
                @name Test
                @# commentaire
                @frame idle
                 ab
                @colors
                 rY
                @frame idle
                 cd
                @frame attack
                 ef

                """, "fichier");
        assertEquals("Test", sprite.name());
        assertEquals(2, sprite.idle().size());
        assertEquals(List.of(" ab"), sprite.idle().get(0).lines());
        assertEquals('r', sprite.idle().get(0).colorAt(1, 0));
        assertEquals('Y', sprite.idle().get(0).colorAt(2, 0));
        assertEquals('.', sprite.idle().get(0).colorAt(5, 0));
        // Sans @colors : les couleurs de la première frame.
        assertEquals('Y', sprite.idle().get(1).colorAt(2, 0));
        assertEquals(List.of(" ef"), sprite.frames(Sprite.ATTACK).get(0).lines());
        assertTrue(sprite.frames(Sprite.DEATH).isEmpty());
        assertEquals(3, sprite.width());
        assertEquals(1, sprite.height());
    }

    @Test
    void fichierSansDirectiveEstUneFrameIdle() {
        Sprite sprite = Sprite.parse("  /\\\n (oo)\n\n", "vieux");
        assertEquals("vieux", sprite.name());
        assertEquals(1, sprite.idle().size());
        assertEquals(List.of("  /\\", " (oo)"), sprite.idle().get(0).lines());
        assertEquals('.', sprite.idle().get(0).colorAt(2, 0));
    }

    @Test
    void sansIdleLaPremiereFrameSert() {
        Sprite sprite = Sprite.parse("@frame attack\nxx\n", "x");
        assertEquals(List.of("xx"), sprite.idle().get(0).lines());
    }

    @Test
    void placeholderEncadreLeNom() {
        Sprite sprite = Sprite.placeholder("Choomah");
        List<String> lines = sprite.idle().get(0).lines();
        assertTrue(lines.get(2).contains("Choomah"));
        assertTrue(lines.get(0).startsWith("+-"));
    }

    // --- Fichiers du jeu ---

    private static final Set<Character> COLOR_LETTERS = Set.of('r', 'R', 'g', 'G', 'y', 'Y', 'b', 'B', 'm', 'M',
            'c', 'C', 'w', 'W', 'k', 'K', '.', ' ');

    @Test
    void chaqueAdversaireAUnFichierDeSprite() {
        for (String id : EnemyRegistry.ids()) {
            Enemy enemy = EnemyRegistry.create(id, 1);
            assertTrue(Sprites.byId(enemy.spriteId()).isPresent(), "sprite manquant : " + enemy.spriteId());
        }
    }

    @Test
    void spritesOriginauxAnimes() {
        for (String id : List.of("caddie-enerve", "came", "paresseux", "seau-de-fuite", "vafi", "le-peneu",
                "t-rex-defonce", "etudiant-post-examen", "nortin", "zo")) {
            Sprite sprite = Sprites.byId(id).orElseThrow();
            assertTrue(sprite.idle().size() >= 2 && sprite.idle().size() <= 3, id + " : 2-3 frames idle");
            assertFalse(sprite.frames(Sprite.ATTACK).isEmpty(), id + " : attaque");
            assertFalse(sprite.frames(Sprite.HURT).isEmpty(), id + " : dégâts");
        }
    }

    @Test
    void spritesEnAsciiPurDansLaZoneEtCouleursValides() throws IOException {
        for (Map.Entry<String, Sprite> entry : Sprites.catalog()) {
            assertAsciiInZoneWithValidColors(entry.getKey(), entry.getValue());
        }
    }

    private static void assertAsciiInZoneWithValidColors(String id, Sprite sprite) throws IOException {
        String raw = read(id);
        for (String line : raw.lines().toList()) {
            if (line.startsWith("@name") || line.startsWith("@#")) {
                continue; // le nom peut avoir des accents : il n'est pas dessiné tel quel
            }
            assertTrue(line.chars().allMatch(c -> c >= 32 && c < 127), id + " : non ASCII : " + line);
        }
        for (String animation : sprite.animations()) {
            for (Sprite.Frame frame : sprite.frames(animation)) {
                assertTrue(frame.lines().size() <= Sprite.MAX_HEIGHT, id + " trop haut");
                assertTrue(frame.width() <= Sprite.MAX_WIDTH, id + " trop large");
                if (frame.colors() != null) {
                    for (String colors : frame.colors()) {
                        assertTrue(colors.chars().allMatch(c -> COLOR_LETTERS.contains((char) c)),
                                id + " : couleur inconnue : " + colors);
                    }
                }
            }
        }
    }

    private static String read(String id) throws IOException {
        try (InputStream in = SpriteTest.class.getResourceAsStream("/sprites/" + id + ".txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
