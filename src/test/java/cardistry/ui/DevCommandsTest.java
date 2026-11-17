package cardistry.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Action;
import cardistry.core.ActionResult;
import cardistry.core.GameState;
import cardistry.core.event.EventBus;
import cardistry.core.stats.StatType;
import cardistry.core.status.StatusTarget;
import cardistry.core.status.StatusType;
import cardistry.items.ItemRegistry;
import cardistry.model.card.CardPool;
import cardistry.run.Run;
import cardistry.run.Secret;
import cardistry.utils.Log;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DevCommandsTest {

    private Run run;

    /** Partie en combat (seed 5, nœud 1), Ctrl+Z équipé ; le pool ne contient qu'un terrain. */
    @BeforeEach
    void setUp() {
        Log.clearMemory();
        run = new Run(5, Set.of(), new EventBus(), GameState::newGame,
                () -> new CardPool(List.of(new cardistry.model.card.TerrainCard("Forêt",
                        cardistry.model.Element.NATURE, cardistry.items.Rarity.COMMON))));
        run.addCode(Secret.CTRL_Z);
        run.start();
        run.chooseNode(1);
    }

    @AfterEach
    void tearDown() {
        Log.clearMemory();
    }

    private GameState state() {
        return run.state();
    }

    private boolean exec(String line) {
        return DevCommands.execute(line, run);
    }

    @Test
    void pvDuJoueurFixes() {
        assertTrue(exec("hp 42"));
        assertEquals(42, state().player().hp());
        assertTrue(exec("hp 500")); // sans plafond
        assertEquals(500, state().player().hp());
    }

    @Test
    void pvDeLAdversaire() {
        assertTrue(exec("ehp 3"));
        assertEquals(3, state().currentEnemy().hp());
        assertTrue(exec("kill"));
        assertEquals(1, state().currentEnemy().hp());
        assertFalse(exec("ehp 0"));
    }

    @Test
    void choomiesClopesIvresse() {
        assertTrue(exec("choomies 99"));
        assertEquals(99, state().choomies());
        assertTrue(exec("cigs 4"));
        assertEquals(4, state().cigarettes());
        assertTrue(exec("drunk 2"));
        assertEquals(2, state().drunkenness());
        assertFalse(exec("choomies -1"));
        assertFalse(exec("choomies beaucoup"));
    }

    @Test
    void carteParSonNomSansAccents() {
        int before = state().hand().size();
        assertTrue(exec("card boule de FEU"));
        assertEquals(before + 1, state().hand().size());
        assertEquals("Boule de feu", state().hand().get(state().hand().size() - 1).name());
        assertFalse(exec("card carte qui n'existe pas"));
    }

    @Test
    void objetEtTrinket() {
        String item = ItemRegistry.items().get(0).id();
        String trinket = ItemRegistry.trinkets().get(0).id();
        assertTrue(exec("item " + item));
        assertTrue(state().items().stream().anyMatch(i -> i.id().equals(item)));
        assertTrue(exec("trinket " + trinket));
        assertTrue(state().hasTrinket(trinket));
        assertFalse(exec("trinket inconnu"));
    }

    @Test
    void statutSurLAdversaireOuLeJoueur() {
        assertTrue(exec("status " + StatusType.BURN.id() + " 3"));
        assertEquals(3, state().statuses(StatusTarget.ENEMY).get(StatusType.BURN).orElseThrow().turnsLeft());
        assertTrue(exec("status " + StatusType.BURN.id() + " 2 joueur"));
        assertTrue(state().statuses(StatusTarget.PLAYER).has(StatusType.BURN));
    }

    @Test
    void bonusDeStat() {
        double before = state().playerStats().get(StatType.STRENGTH);
        assertTrue(exec("stat FOR 5"));
        assertEquals(before + 5, state().playerStats().get(StatType.STRENGTH), 1e-9);
    }

    @Test
    void commandeInconnueOuMalTapee() {
        assertFalse(exec("vole"));
        assertFalse(exec("hp"));
        assertEquals(Log.Level.WARN, Log.recent().get(Log.recent().size() - 1).level());
        assertFalse(exec("   "));
    }

    @Test
    void laCommandeEtSonResultatSontJournalises() {
        exec("seed");
        List<Log.Entry> lines = Log.recent();
        assertEquals("> seed", lines.get(lines.size() - 2).message());
        assertEquals("Seed : 5", lines.get(lines.size() - 1).message());
    }

    @Test
    void uneTricheEstRejoueeParCtrlZ() {
        assertTrue(exec("choomies 77"));
        run.startTurn();
        run.submit(new Action.PlayCard(0));
        run.startTurn();
        assertTrue(run.canUndo());
        assertEquals(ActionResult.FREE_ACTION, run.submit(new Action.Undo()));
        assertEquals(77, state().choomies()); // l'état rejoué depuis la seed garde la triche
    }

    @Test
    void sansPartieLesTrichesSontRefusees() {
        assertFalse(DevCommands.execute("hp 10", null));
        assertTrue(DevCommands.execute("help", null));
    }
}
