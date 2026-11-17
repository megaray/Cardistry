package cardistry.ui.text;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.GameState;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.Stats;
import cardistry.items.ItemRegistry;
import cardistry.items.Rarity;
import cardistry.items.Trinket;
import cardistry.model.Element;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.TerrainCard;
import cardistry.model.enemy.EnemyRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mode texte ({@code --no-ui}) : vol et rendu de trinket dans le journal (Nortin). */
class TextRendererTrinketTest {

    @Test
    void volEtRenduDeTrinketJournalises() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TextRenderer renderer = new TextRenderer(new Console(new ByteArrayInputStream(new byte[0]),
                new PrintStream(bytes, true, StandardCharsets.UTF_8), true, false, false));
        Enemy nortin = EnemyRegistry.create(EnemyRegistry.bossId(1), 1);
        GameState state = new GameState(new Player(100), List.of(nortin),
                new TerrainCard("Dojo", Element.NEUTRAL, Rarity.COMMON), List.of(), new Stats());
        renderer.showStatus(state);
        Trinket diplome = ItemRegistry.trinket("diplome-epfl");

        renderer.onEvent(new GameEvent.TrinketStolen(nortin, diplome));
        renderer.onEvent(new GameEvent.TrinketReturned(diplome));

        String out = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("→ Nortin te vole Diplôme EPFL !"), out);
        assertTrue(out.contains("→ Tu récupères Diplôme EPFL"), out);
    }
}
