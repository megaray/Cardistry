package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.event.GameEvent.ActionRejected.Reason;
import cardistry.core.stats.Stats;
import cardistry.items.Collectible;
import cardistry.items.Item;
import cardistry.items.LootTable;
import cardistry.items.Rarity;
import cardistry.items.Tag;
import cardistry.items.Trinket;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.CardPool;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RewardTest {

    private final List<GameEvent> events = new ArrayList<>();
    private GameState state;
    private CombatEngine engine;
    private Enemy hydra;
    private Enemy siren;

    private static Trinket trinket(String id) {
        return Trinket.builder(id, id, Rarity.COMMON).description("x").tags(Tag.META).build();
    }

    private static Item item(String id) {
        return new Item(id, id, "x", "", Rarity.COMMON, Set.of(Tag.META), ctx -> { });
    }

    /** Mannequin tuée en un sort ; la table propose les candidats donnés. */
    private void killHydraWith(List<Collectible> candidates) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        hydra = new Enemy("Mannequin", FIRE, 20, 5);
        siren = new Enemy("Bouée", WATER, 30, 10);
        state = new GameState(new Player(100), List.of(hydra, siren), new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON),
                List.of(new SpellCard("Tsunami", WATER, 15, cardistry.items.Rarity.COMMON)), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(3), bus, new LootTable(() -> candidates));
        assertEquals(ActionResult.TURN_ENDED, engine.submit(new Action.PlayCard(0)));
    }

    @Test
    void adversaireTueProposeTroisRecompensesEtAttend() {
        killHydraWith(List.of(trinket("a"), trinket("b"), item("c"), item("d")));

        assertTrue(engine.hasPendingReward());
        assertEquals(3, engine.pendingReward().size());
        assertEquals(new GameEvent.RewardOffered(engine.pendingReward()), events.get(events.size() - 1));
        assertSame(hydra, state.currentEnemy()); // le combat suivant n'a pas commencé
        assertThrows(IllegalStateException.class, () -> engine.submit(new Action.DrawCard()));
    }

    @Test
    void prendreUnTrinketLEquipePuisLanceLeCombatSuivant() {
        killHydraWith(List.of(trinket("a")));

        assertTrue(engine.resolveReward(RewardChoice.Take.into(0)));

        assertTrue(state.hasTrinket("a"));
        assertFalse(engine.hasPendingReward());
        assertSame(siren, state.currentEnemy());
        int taken = indexOf(GameEvent.RewardTaken.class);
        assertTrue(taken >= 0 && taken < indexOf(GameEvent.CombatStarted.class));
        assertNull(((GameEvent.RewardTaken) events.get(taken)).replaced());
    }

    @Test
    void prendreUnObjetLAjouteALInventaire() {
        killHydraWith(List.of(item("maté")));
        engine.resolveReward(RewardChoice.Take.into(0));
        assertEquals("maté", state.items().get(0).id());
    }

    @Test
    void refuserLanceLeCombatSuivantSansRienAjouter() {
        killHydraWith(List.of(trinket("a")));

        assertTrue(engine.resolveReward(new RewardChoice.Skip()));

        assertTrue(state.trinkets().isEmpty());
        assertTrue(events.contains(new GameEvent.RewardSkipped()));
        assertSame(siren, state.currentEnemy());
    }

    @Test
    void inventairePleinIlFautRemplacer() {
        killHydraWith(List.of(item("nouveau")));
        for (int i = 0; i < 3; i++) {
            engine.give(item("vieux" + i));
        }

        assertFalse(engine.resolveReward(RewardChoice.Take.into(0)));
        assertEquals(new GameEvent.ActionRejected(Reason.INVENTORY_FULL), events.get(events.size() - 1));
        assertTrue(engine.hasPendingReward());

        assertTrue(engine.resolveReward(new RewardChoice.Take(0, 1)));
        assertEquals(List.of("vieux0", "vieux2", "nouveau"), state.items().stream().map(Item::id).toList());
        GameEvent.RewardTaken taken = (GameEvent.RewardTaken) events.get(indexOf(GameEvent.RewardTaken.class));
        assertEquals("vieux1", taken.replaced().id());
    }

    @Test
    void diplomePrisAvecUneBarrePleineSansRemplacer() {
        Trinket diplome = cardistry.items.ItemRegistry.trinket("diplome-epfl");
        killHydraWith(List.of(diplome));
        for (int i = 0; i < 5; i++) {
            engine.equip(trinket("vieux" + i));
        }

        assertTrue(engine.resolveReward(RewardChoice.Take.into(0)));

        assertFalse(events.contains(new GameEvent.ActionRejected(Reason.INVENTORY_FULL)));
        assertTrue(state.hasTrinket("diplome-epfl"));
        assertEquals(6, state.trinkets().size());
        assertEquals(6, state.trinketSlots());
    }

    @Test
    void trinketNormalPrisAvecUneBarrePleineToujoursRefuse() {
        killHydraWith(List.of(trinket("nouveau")));
        for (int i = 0; i < 5; i++) {
            engine.equip(trinket("vieux" + i));
        }

        assertFalse(engine.resolveReward(RewardChoice.Take.into(0)));

        assertEquals(new GameEvent.ActionRejected(Reason.INVENTORY_FULL), events.get(events.size() - 1));
        assertTrue(engine.hasPendingReward());
        assertFalse(state.hasTrinket("nouveau"));
        assertEquals(5, state.trinkets().size());
    }

    @Test
    void remplacerUnTrinketRetireSesModificateurs() {
        Trinket briquets = cardistry.items.ItemRegistry.trinkets().get(0);
        killHydraWith(List.of(trinket("nouveau")));
        engine.equip(briquets);
        for (int i = 1; i < 5; i++) {
            engine.equip(trinket("t" + i));
        }
        double withBriquets = state.playerStats().get(cardistry.core.stats.StatType.FIRE_DAMAGE);

        engine.resolveReward(new RewardChoice.Take(0, 0));

        assertEquals(1.25, withBriquets, 1e-9);
        assertEquals(1.0, state.playerStats().get(cardistry.core.stats.StatType.FIRE_DAMAGE), 1e-9);
        assertFalse(state.hasTrinket(briquets.id()));
    }

    @Test
    void choixInvalidesRefuses() {
        killHydraWith(List.of(item("a")));
        assertFalse(engine.resolveReward(RewardChoice.Take.into(5)));
        assertFalse(engine.resolveReward(new RewardChoice.Take(0, 2))); // rien à remplacer à l'emplacement 2
        assertTrue(engine.hasPendingReward());
    }

    @Test
    void pasDeCandidatPasDeRecompense() {
        killHydraWith(List.of());
        assertFalse(engine.hasPendingReward());
        assertSame(siren, state.currentEnemy());
    }

    @Test
    void pasDeRecompenseApresLeDernierAdversaire() {
        EventBus bus = new EventBus();
        Enemy last = new Enemy("Mannequin", FIRE, 20, 5);
        GameState solo = new GameState(new Player(100), List.of(last), new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON),
                List.of(new SpellCard("Tsunami", WATER, 15, cardistry.items.Rarity.COMMON)), new Stats());
        CombatEngine e = new CombatEngine(solo, new CardPool(), new Rng(3), bus, new LootTable(() -> List.of(item("a"))));
        e.submit(new Action.PlayCard(0));
        assertTrue(solo.isWon());
        assertFalse(e.hasPendingReward());
    }

    private int indexOf(Class<? extends GameEvent> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
