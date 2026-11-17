package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.event.GameEvent.ActionRejected.Reason;
import cardistry.core.event.GameHooks;
import cardistry.core.stats.StatType;
import cardistry.core.stats.Stats;
import cardistry.items.Item;
import cardistry.items.LootTable;
import cardistry.items.Rarity;
import cardistry.items.Tag;
import cardistry.items.Trinket;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryTest {

    private static final double DELTA = 1e-9;

    private final List<GameEvent> events = new ArrayList<>();
    private GameState state;
    private CombatEngine engine;

    private void setUp(List<Card> hand, Enemy... enemies) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        state = new GameState(new Player(100), List.of(enemies), new TerrainCard("Forêt", cardistry.model.Element.NATURE, cardistry.items.Rarity.COMMON),
                hand, new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), bus, new LootTable(List::of));
    }

    private static Enemy hydra() {
        return new Enemy("Mannequin", FIRE, 20, 5);
    }

    private static Item item(String id, java.util.function.Consumer<EffectContext> effect) {
        return new Item(id, id, "", "", Rarity.COMMON, Set.of(), effect);
    }

    // --- Trinkets ---

    @Test
    void equiperUnTrinketAppliqueSesModificateursEtLeRetirerLesEnleve() {
        setUp(List.of(), hydra());
        Trinket trinket = Trinket.builder("t", "T", Rarity.COMMON).percent(StatType.FIRE_DAMAGE, 0.25).build();

        engine.equip(trinket);
        assertEquals(1.25, state.playerStats().get(StatType.FIRE_DAMAGE), DELTA);
        assertTrue(state.hasTrinket("t"));

        state.unequipTrinket(0);
        assertEquals(1.0, state.playerStats().get(StatType.FIRE_DAMAGE), DELTA);
        assertFalse(state.hasTrinket("t"));
    }

    @Test
    void emplacementsLimites() {
        setUp(List.of(), hydra());
        assertEquals(5, state.trinketSlots());
        assertEquals(3, state.itemSlots());
        for (int i = 0; i < 5; i++) {
            engine.equip(Trinket.builder("t" + i, "T", Rarity.COMMON).build());
        }
        for (int i = 0; i < 3; i++) {
            engine.give(item("o" + i, ctx -> { }));
        }
        assertThrows(IllegalStateException.class, () -> engine.equip(Trinket.builder("x", "X", Rarity.COMMON).build()));
        assertThrows(IllegalStateException.class, () -> engine.give(item("x", ctx -> { })));
    }

    @Test
    void lesHooksDUnTrinketEquipeRecoiventLesEvenementsApresLEvenementEnCours() {
        SpellCard spell = new SpellCard("Jet d'eau", WATER, 10, cardistry.items.Rarity.COMMON);
        setUp(List.of(spell), hydra());
        state.player().takeDamage(50);
        engine.equip(Trinket.builder("sangsue", "Sangsue", Rarity.RARE)
                .hooks(new GameHooks() {
                    @Override
                    public void onCardPlayed(GameEvent.CardPlayed event, EffectContext ctx) {
                        ctx.healPlayer(3);
                    }
                })
                .build());

        engine.submit(new Action.PlayCard(0));

        // Le soin du trinket est livré après l'événement CardPlayed qui l'a déclenché.
        assertEquals(new GameEvent.CardPlayed(spell), events.get(0));
        assertTrue(events.indexOf(new GameEvent.Healed(state.player(), 3, 1.0)) > 0);
        assertEquals(50 + 3 - 5, state.player().hp(), DELTA); // Forêt (nature) : attaque de le Mannequin ×1
    }

    // --- Objets ---

    @Test
    void utiliserUnObjetEstGratuit() {
        setUp(List.of(), hydra());
        state.player().takeDamage(30);
        engine.give(item("mate", ctx -> ctx.healPlayer(10)));

        assertEquals(ActionResult.FREE_ACTION, engine.submit(new Action.UseItem(0)));

        assertEquals(80, state.player().hp(), DELTA); // pas d'attaque adverse
        assertTrue(state.items().isEmpty());
        assertEquals(GameEvent.ItemUsed.class, events.get(0).getClass());
        assertEquals(new GameEvent.Healed(state.player(), 10, 1.0), events.get(1));
        assertFalse(events.stream().anyMatch(GameEvent.EnemyAttacked.class::isInstance));
    }

    @Test
    void lEfficaciteDesSoinsSAppliqueAuxObjets() {
        setUp(List.of(), hydra());
        state.player().takeDamage(50);
        state.playerStats().add(cardistry.core.stats.StatModifier.percent(StatType.HEAL_EFFICIENCY, 0.5, "test"));
        engine.give(item("mate", ctx -> ctx.healPlayer(10)));

        engine.submit(new Action.UseItem(0));

        assertEquals(new GameEvent.Healed(state.player(), 15, 1.5), events.get(1));
        assertEquals(65, state.player().hp(), DELTA);
    }

    @Test
    void objetAbsentRefuse() {
        setUp(List.of(), hydra());
        assertEquals(ActionResult.REJECTED, engine.submit(new Action.UseItem(0)));
        assertEquals(List.of(new GameEvent.ActionRejected(Reason.NO_ITEM)), events);
    }

    @Test
    void unObjetQuiTueLAdversaireTermineLeTour() {
        Enemy hydra = hydra();
        setUp(List.of(), hydra, new Enemy("Bouée", WATER, 30, 10));
        engine.give(item("bombe", ctx -> ctx.damageEnemy(100)));

        assertEquals(ActionResult.TURN_ENDED, engine.submit(new Action.UseItem(0)));

        assertTrue(events.contains(new GameEvent.EnemyKilled(hydra)));
        assertEquals(2, state.combatNumber());
        assertEquals(100, state.player().hp(), DELTA);
    }

    @Test
    void piocherParUnEffetRespecteLaTailleDeMain() {
        List<Card> hand = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            hand.add(new TerrainCard("T" + i, FIRE, cardistry.items.Rarity.COMMON));
        }
        setUp(hand, hydra());
        engine.give(item("cafe", ctx -> ctx.drawCards(2)));

        engine.submit(new Action.UseItem(0));

        assertEquals(7, state.hand().size());
        assertEquals(1, events.stream().filter(GameEvent.CardDrawn.class::isInstance).count());
    }

    // --- Trinkets volés (Nortin) ---

    private static Trinket plain(String id, Tag... tags) {
        return Trinket.builder(id, id, Rarity.COMMON).tags(tags).build();
    }

    @Test
    void volerUnTrinketLeDeplaceDansLesTrinketsVoles() {
        setUp(List.of(), hydra());
        Trinket trinket = plain("t");
        engine.equip(trinket);

        Trinket stolen = state.stealTrinket(0);

        assertEquals(trinket, stolen);
        assertFalse(state.hasTrinket("t"));
        assertTrue(state.trinkets().isEmpty());
        assertEquals(List.of(trinket), state.stolenTrinkets());
    }

    @Test
    void unTrinketVolePerdSonBonusDeStats() {
        setUp(List.of(), hydra());
        engine.equip(cardistry.items.ItemRegistry.trinket("redistribution-des-briquets"));
        assertEquals(1.25, state.playerStats().get(StatType.FIRE_DAMAGE), DELTA);

        state.stealTrinket(0);

        assertEquals(1.0, state.playerStats().get(StatType.FIRE_DAMAGE), DELTA);
    }

    @Test
    void unTrinketVoleRecalculeLesBonusDeSet() {
        setUp(List.of(), hydra());
        engine.equip(plain("a", Tag.EPFL));
        engine.equip(plain("b", Tag.EPFL));
        assertEquals(1.10, state.playerStats().get(StatType.WATER_DAMAGE), DELTA); // EPFL « Cursus » à 2
        assertFalse(state.activeSetTiers().isEmpty());

        state.stealTrinket(0);

        assertEquals(1.0, state.playerStats().get(StatType.WATER_DAMAGE), DELTA);
        assertTrue(state.activeSetTiers().isEmpty());
    }

    @Test
    void volerUnTrinketGardeLesAutresDansLOrdre() {
        setUp(List.of(), hydra());
        Trinket first = plain("a");
        Trinket middle = plain("b");
        Trinket last = plain("c");
        engine.equip(first);
        engine.equip(middle);
        engine.equip(last);

        state.stealTrinket(1);

        assertEquals(List.of(first, last), state.trinkets());
        assertEquals(List.of(middle), state.stolenTrinkets());
    }

    @Test
    void lesTrinketsVolesSontEnLectureSeule() {
        setUp(List.of(), hydra());
        assertThrows(UnsupportedOperationException.class, () -> state.stolenTrinkets().add(plain("t")));
    }

    // --- Trinkets rendus (Nortin vaincu) ---

    @Test
    void lesTrinketsRendusVontALaFinDansLOrdreDuVol() {
        setUp(List.of(), hydra());
        Trinket a = plain("a");
        Trinket b = plain("b");
        Trinket c = plain("c");
        Trinket d = plain("d");
        engine.equip(a);
        engine.equip(b);
        engine.equip(c);
        engine.equip(d);
        state.stealTrinket(2); // c
        state.stealTrinket(0); // a

        List<Trinket> returned = state.returnStolenTrinkets();

        assertEquals(List.of(c, a), returned);
        assertEquals(List.of(b, d, c, a), state.trinkets());
    }

    @Test
    void unTrinketRenduRetrouveSonBonusDeStats() {
        setUp(List.of(), hydra());
        engine.equip(cardistry.items.ItemRegistry.trinket("redistribution-des-briquets"));
        state.stealTrinket(0);
        assertEquals(1.0, state.playerStats().get(StatType.FIRE_DAMAGE), DELTA);

        state.returnStolenTrinkets();

        assertEquals(1.25, state.playerStats().get(StatType.FIRE_DAMAGE), DELTA);
    }

    @Test
    void unTrinketRenduRecalculeLesBonusDeSet() {
        setUp(List.of(), hydra());
        engine.equip(plain("a", Tag.EPFL));
        engine.equip(plain("b", Tag.EPFL));
        state.stealTrinket(0);
        assertTrue(state.activeSetTiers().isEmpty());

        state.returnStolenTrinkets();

        assertEquals(1.10, state.playerStats().get(StatType.WATER_DAMAGE), DELTA); // EPFL « Cursus » à 2
        assertFalse(state.activeSetTiers().isEmpty());
    }

    @Test
    void apresLeRetourPlusRienAVoleEtRienNEstRenduDeuxFois() {
        setUp(List.of(), hydra());
        Trinket trinket = plain("t");
        engine.equip(trinket);
        state.stealTrinket(0);
        state.returnStolenTrinkets();

        assertTrue(state.stolenTrinkets().isEmpty());
        assertTrue(state.returnStolenTrinkets().isEmpty());
        assertEquals(List.of(trinket), state.trinkets());
    }

    @Test
    void sansVolRienARendre() {
        setUp(List.of(), hydra());
        Trinket trinket = plain("t");
        engine.equip(trinket);

        assertTrue(state.returnStolenTrinkets().isEmpty());
        assertEquals(List.of(trinket), state.trinkets());
    }

    @Test
    void diplomeRenduEnDernierAvecUneBarrePleine() {
        setUp(List.of(), hydra());
        Trinket diplome = cardistry.items.ItemRegistry.trinket("diplome-epfl");
        engine.equip(diplome);
        equipPlains(5); // 6 / 6 : [Diplôme, t0, t1, t2, t3, t4]
        Trinket x = state.trinkets().get(3); // t2
        state.stealTrinket(3);
        state.stealTrinket(0); // le Diplôme : 4 trinkets pour 5 emplacements

        state.returnStolenTrinkets();

        assertEquals(6, state.trinkets().size());
        assertEquals(6, state.trinketSlots());
        assertEquals(x, state.trinkets().get(4));
        assertEquals(diplome, state.trinkets().get(5));
    }

    // --- Un trinket qui apporte sa place (Diplôme EPFL) ---

    private void equipPlains(int count) {
        for (int i = 0; i < count; i++) {
            engine.equip(plain("t" + i));
        }
    }

    @Test
    void barreNonPleineAccepteUnTrinketNormal() {
        setUp(List.of(), hydra());
        equipPlains(4);

        assertTrue(state.hasRoomFor(plain("x")));
    }

    @Test
    void barrePleineRefuseUnTrinketNormal() {
        setUp(List.of(), hydra());
        equipPlains(5);

        assertFalse(state.hasRoomFor(plain("x")));
    }

    @Test
    void barrePleineAccepteLeDiplomeQuiApporteSaPlace() {
        setUp(List.of(), hydra());
        equipPlains(5);

        assertTrue(state.hasRoomFor(cardistry.items.ItemRegistry.trinket("diplome-epfl")));
    }

    @Test
    void diplomeEquipeAvecUneBarrePleineVaALaFin() {
        setUp(List.of(), hydra());
        equipPlains(5);
        Trinket diplome = cardistry.items.ItemRegistry.trinket("diplome-epfl");

        engine.equip(diplome);

        assertEquals(6, state.trinkets().size());
        assertEquals(6, state.trinketSlots());
        assertEquals(diplome, state.trinkets().get(5));
    }

    @Test
    void trinketNormalAvecUneBarrePleineToujoursRefuse() {
        setUp(List.of(), hydra());
        equipPlains(5);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> engine.equip(plain("x")));

        assertEquals("Plus d'emplacement de trinket", e.getMessage());
        assertEquals(5, state.trinkets().size());
        assertFalse(state.hasTrinket("x"));
    }
}
