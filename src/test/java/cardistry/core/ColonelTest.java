package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.NEUTRAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.Stats;
import cardistry.items.LootTable;
import cardistry.items.Rarity;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.CardPool;
import cardistry.model.card.HealCard;
import cardistry.model.card.TerrainCard;
import cardistry.model.enemy.EnemyRegistry;
import cardistry.model.enemy.EnemyTerrains;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Colonel Eclatax (boss du niveau 3) : son potager pose le Royaume du Colonel, où aucun soin ne passe. */
class ColonelTest {

    private static final double DELTA = 1e-9;

    private final List<GameEvent> events = new ArrayList<>();
    private GameState state;
    private CombatEngine engine;

    /** Un adversaire inoffensif, joueur à 80 / 100 PV, sur {@code terrain}. */
    private void setUp(TerrainCard terrain) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        state = new GameState(new Player(100), List.of(new Enemy("A", FIRE, 500, 0)), terrain, List.of(), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), bus, new LootTable(List::of));
        state.player().takeDamage(20);
    }

    /** Joue une carte de soin neutre de 10 PV (×1 sur un terrain plante comme sur la Forêt). */
    private void playHeal() {
        engine.startTurn();
        state.addToHand(new HealCard("Soin test", NEUTRAL, 10, Rarity.COMMON));
        engine.submit(new Action.PlayCard(state.hand().size() - 1));
    }

    @Test
    void lePotagerPoseLeRoyaumeDuColonel() {
        state = GameState.newGame();
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        engine = new CombatEngine(state, new CardPool(), new Rng(3), bus, new LootTable(List::of));
        engine.start();
        engine.startCombat(EnemyRegistry.create(EnemyRegistry.bossId(3), 3), false);
        assertEquals("Dojo", state.terrain().name(), "le combat commence sur le Dojo");

        engine.startTurn();
        engine.submit(new Action.DrawCard()); // fin du tour : le Colonel joue son potager

        assertEquals("Royaume du Colonel", state.terrain().name());
        assertEquals(TerrainCard.Effect.NO_HEALING, state.terrain().effect());
        assertEquals(NATURE, state.terrain().element());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.TerrainChanged t
                && t.previous().name().equals("Dojo") && t.current().name().equals("Royaume du Colonel")));
    }

    @Test
    void aucunSoinDansLeRoyaumeDuColonel() {
        setUp(EnemyTerrains.terrainColonel());
        playHeal();
        assertEquals(80, state.player().hp(), DELTA, "carte de soin bloquée");
        engine.submit(new Action.Smoke());
        assertEquals(80, state.player().hp(), DELTA, "clope bloquée aussi (question ouverte)");
        assertTrue(events.stream().noneMatch(GameEvent.Healed.class::isInstance));
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.AbilityTriggered a
                && a.message().contains("Royaume du Colonel")), "le journal annonce le soin bloqué");
    }

    @Test
    void ailleursLeMemeSoinPasse() {
        setUp(new TerrainCard("Forêt", NATURE, Rarity.COMMON));
        playHeal();
        assertEquals(90, state.player().hp(), DELTA);
    }

    @Test
    void sortirDuRoyaumeRendLesSoins() {
        setUp(EnemyTerrains.terrainColonel());
        engine.startTurn();
        state.addToHand(new TerrainCard("Forêt", NATURE, Rarity.COMMON));
        engine.submit(new Action.PlayCard(state.hand().size() - 1));
        assertEquals("Forêt", state.terrain().name());
        playHeal();
        assertEquals(90, state.player().hp(), DELTA);
    }
}
