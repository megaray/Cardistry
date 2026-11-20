package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.StatModifier;
import cardistry.core.stats.StatType;
import cardistry.core.stats.Stats;
import cardistry.items.Item;
import cardistry.items.LootTable;
import cardistry.items.Rarity;
import cardistry.items.Tag;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.CardPool;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DrunkennessTest {

    private final List<GameEvent> events = new ArrayList<>();
    private GameState state;
    private CombatEngine engine;

    /** Deux combats ; la main contient un sort qui tue le premier adversaire d'un coup. */
    private void setUp(int enemies) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        List<Enemy> list = new ArrayList<>();
        for (int i = 0; i < enemies; i++) {
            list.add(new Enemy("E" + i, FIRE, 20, 5));
        }
        state = new GameState(new Player(100), list, new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON),
                List.of(new SpellCard("Tsunami", WATER, 100, cardistry.items.Rarity.COMMON)), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), bus, new LootTable(List::of));
    }

    private static Item drink() {
        return new Item("biere", "Bière", "x", "", Rarity.COMMON, Set.of(Tag.DRINK), ctx -> { });
    }

    private void drinkTimes(int n) {
        for (int i = 0; i < n; i++) {
            engine.give(drink());
            assertEquals(ActionResult.FREE_ACTION, engine.submit(new Action.UseItem(0)));
        }
    }

    private long count(Class<? extends GameEvent> type) {
        return events.stream().filter(type::isInstance).count();
    }

    // --- Paliers ---

    private double fireDamage() {
        return state.playerStats().get(StatType.FIRE_DAMAGE);
    }

    @Test
    void paliersPompetteBourreCuite() {
        setUp(2);
        assertEquals(DrunkTier.SOBER, state.drunkTier());
        assertEquals(1.0, fireDamage(), 1e-9);
        drinkTimes(1);
        assertEquals(DrunkTier.TIPSY, state.drunkTier());
        assertEquals(1.10, fireDamage(), 1e-9);
        drinkTimes(1);
        assertEquals(DrunkTier.DRUNK, state.drunkTier());
        assertEquals(1.25, fireDamage(), 1e-9);
        drinkTimes(1);
        assertEquals(DrunkTier.WASTED, state.drunkTier());
        assertEquals(1.50, fireDamage(), 1e-9);
        drinkTimes(2);
        assertEquals(1.50, fireDamage(), 1e-9, "la Cuite ne monte pas plus haut");
        assertEquals(1.50, state.playerStats().get(StatType.WATER_DAMAGE), 1e-9);
        assertEquals(1.50, state.playerStats().get(StatType.NATURE_DAMAGE), 1e-9);
    }

    @Test
    void leBonusDIvresseTombeALaFinDuCombat() {
        setUp(2);
        drinkTimes(2);
        engine.startTurn();
        engine.submit(new Action.PlayCard(0)); // tue : fin du combat
        assertEquals(DrunkTier.SOBER, state.drunkTier());
        assertEquals(1.0, fireDamage(), 1e-9);
    }

    @Test
    void avecUnSeuilPlusHautLaCuiteArrivePlusTard() {
        setUp(2);
        state.playerStats().add(StatModifier.flat(StatType.HANGOVER_THRESHOLD, 2, "test"));
        drinkTimes(4);
        assertEquals(DrunkTier.DRUNK, state.drunkTier(), "Bourré de 2 à 4 avec un seuil à 5");
        assertTrue(state.pendingCurses().isEmpty());
        drinkTimes(1);
        assertEquals(DrunkTier.WASTED, state.drunkTier());
        assertFalse(state.pendingCurses().isEmpty());
    }

    @Test
    void uneBoissonDonneUnPointDIvresse() {
        setUp(2);
        drinkTimes(1);
        assertEquals(1, state.drunkenness());
        assertTrue(events.contains(new GameEvent.DrunkennessChanged(1, 3)));
    }

    @Test
    void unObjetQuiNEstPasUneBoissonNeSaoulePas() {
        setUp(2);
        engine.give(new Item("mate", "Maté", "x", "", Rarity.COMMON, Set.of(Tag.HEAL), ctx -> { }));
        engine.submit(new Action.UseItem(0));
        assertEquals(0, state.drunkenness());
    }

    @Test
    void auSeuilGueuleDeBoisAuProchainCombat() {
        setUp(2);
        drinkTimes(2);
        assertTrue(state.pendingCurses().isEmpty());

        drinkTimes(1);
        assertEquals(Set.of(Curse.HANGOVER), state.pendingCurses());
        assertTrue(events.contains(new GameEvent.CurseGained(Curse.HANGOVER)));
        assertEquals(7, state.handSize(), "rien ne change pendant le combat en cours");
    }

    @Test
    void laGueuleDeBoisDureLeCombatSuivantPuisDisparait() {
        setUp(3);
        drinkTimes(3);

        engine.submit(new Action.PlayCard(0)); // tue E0
        assertEquals(0, state.drunkenness(), "l'Ivresse retombe à la fin du combat");
        assertEquals(Set.of(Curse.HANGOVER), state.activeCurses());
        assertEquals(6, state.handSize());
        assertEquals(GameState.STARTING_HAND, state.hand().size());
        int started = events.indexOf(new GameEvent.CurseStarted(Curse.HANGOVER));
        assertTrue(started > events.indexOf(new GameEvent.CombatStarted(state.currentEnemy(), 2, 3)));
        assertTrue(started < events.size() - 1, "avant la distribution");

        state.currentEnemy().takeDamage(100);
        engine.submit(new Action.DrawCard()); // tue E1
        assertTrue(state.activeCurses().isEmpty());
        assertTrue(events.contains(new GameEvent.CurseEnded(Curse.HANGOVER)));
        assertEquals(7, state.handSize());
    }

    @Test
    void uneSeuleGueuleDeBoisParCombatMaisLIvresseContinue() {
        setUp(2);
        drinkTimes(6);
        assertEquals(6, state.drunkenness());
        assertEquals(1, count(GameEvent.CurseGained.class));
    }

    @Test
    void seuilModifiable() {
        setUp(2);
        state.playerStats().add(StatModifier.flat(StatType.HANGOVER_THRESHOLD, 2, "foie"));
        drinkTimes(4);
        assertTrue(state.pendingCurses().isEmpty());
        drinkTimes(1);
        assertEquals(Set.of(Curse.HANGOVER), state.pendingCurses());
    }

    @Test
    void pasDeGueuleDeBoisPendantLeDernierCombat() {
        setUp(1);
        drinkTimes(3);
        assertTrue(state.pendingCurses().isEmpty());
        assertEquals(0, count(GameEvent.CurseGained.class));
    }

    @Test
    void lesEffetsPeuventAjouterDeLIvresse() {
        setUp(2);
        engine.give(new Item("x", "x", "x", "", Rarity.COMMON, Set.of(Tag.META), ctx -> ctx.addDrunkenness(2)));
        engine.submit(new Action.UseItem(0));
        assertEquals(2, state.drunkenness());
        assertFalse(state.pendingCurses().contains(Curse.HANGOVER));
    }

    @Test
    void sortNeutreBoosteParLIvresseSansCrash() {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        state = new GameState(new Player(100), List.of(new Enemy("E0", FIRE, 200, 5), new Enemy("E1", FIRE, 20, 5)),
                new TerrainCard("Dojo", cardistry.model.Element.NEUTRAL, Rarity.COMMON),
                List.of(new SpellCard("Pet de Mamouz", cardistry.model.Element.NEUTRAL, 10, Rarity.COMMON)),
                new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), bus, new LootTable(List::of));
        drinkTimes(1);
        assertEquals(1.10, state.playerStats().get(StatType.NEUTRAL_DAMAGE), 1e-9);
        engine.submit(new Action.PlayCard(0));
        assertEquals(200 - 11, state.currentEnemy().hp(), 1e-9);
    }

    // --- Cuite : sorts ratés et PV max réduits ---

    /** Un adversaire solide (le sort ne le tue pas) et deux sorts en main, sur {@code terrain}. */
    private void setUpSturdy(long seed, TerrainCard terrain) {
        EventBus bus = new EventBus();
        events.clear();
        bus.subscribe(events::add);
        state = new GameState(new Player(100), List.of(new Enemy("E0", FIRE, 1000, 5), new Enemy("E1", FIRE, 20, 5)),
                terrain, List.of(new SpellCard("Tsunami", WATER, 10, Rarity.COMMON),
                        new SpellCard("Tsunami", WATER, 10, Rarity.COMMON)), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(seed), bus, new LootTable(List::of));
    }

    private static TerrainCard plain() {
        return new TerrainCard("EPFL", FIRE, Rarity.COMMON);
    }

    @Test
    void laCuiteReduitLesPvMaxDUnTiers() {
        setUp(2);
        drinkTimes(2);
        assertEquals(100, state.maxHp(), 1e-9, "Bourré : PV max intacts");
        drinkTimes(1);
        assertEquals(DrunkTier.WASTED, state.drunkTier());
        assertEquals(67, state.maxHp(), 1e-9, "100 × 0,67");
        assertEquals(67, state.player().hp(), 1e-9, "les PV redescendent au nouveau plafond");
    }

    @Test
    void lesPvMaxReviennentQuandOnSortDeLaCuite() {
        setUp(2);
        drinkTimes(3);
        assertEquals(ActionResult.FREE_ACTION, engine.submit(new Action.Smoke())); // −1 Ivresse : Bourré
        assertEquals(DrunkTier.DRUNK, state.drunkTier());
        assertEquals(100, state.maxHp(), 1e-9);
        assertEquals(67, state.player().hp(), 1e-9,
                "les PV perdus ne reviennent pas ; la clope soigne avant de dessoûler, donc au plafond de 67 (+0)");
    }

    @Test
    void lesPvMaxReviennentALaFinDuCombat() {
        setUp(2);
        drinkTimes(3);
        engine.submit(new Action.PlayCard(0)); // tue E0 ou rate
        while (state.currentEnemy().name().equals("E0")) {
            engine.submit(new Action.DrawCard());
            engine.submit(new Action.PlayCard(0));
        }
        assertEquals(100, state.maxHp(), 1e-9);
    }

    @Test
    void enCuiteUnSortRateUneFoisSurDeux() {
        int missed = 0;
        int seeds = 200;
        for (long seed = 1; seed <= seeds; seed++) {
            setUpSturdy(seed, plain());
            drinkTimes(3);
            engine.submit(new Action.PlayCard(0));
            if (count(GameEvent.SpellMissed.class) == 1) {
                missed++;
                assertEquals(1000, state.currentEnemy().hp(), 1e-9, "raté : aucun dégât");
                assertEquals(0, count(GameEvent.SpellHit.class));
            } else {
                assertTrue(state.currentEnemy().hp() < 1000, "touché : des dégâts");
            }
        }
        assertTrue(missed > seeds * 0.35 && missed < seeds * 0.65, "environ 50 % de ratés : " + missed + "/" + seeds);
    }

    @Test
    void sansCuiteAucunSortNeRate() {
        for (long seed = 1; seed <= 50; seed++) {
            setUpSturdy(seed, plain());
            drinkTimes(2); // Bourré
            engine.submit(new Action.PlayCard(0));
            assertEquals(0, count(GameEvent.SpellMissed.class));
        }
    }

    @Test
    void unSortRateNEstPasRejoueSurLEspace() {
        boolean sawMiss = false;
        boolean sawHit = false;
        for (long seed = 1; seed <= 50 && !(sawMiss && sawHit); seed++) {
            setUpSturdy(seed, new TerrainCard("Espace de dimension n", cardistry.model.Element.ALGEBRAIC, Rarity.RARE)
                    .withEffect(TerrainCard.Effect.REPLAY, "x"));
            drinkTimes(3);
            engine.submit(new Action.PlayCard(0));
            if (count(GameEvent.SpellMissed.class) == 1) {
                sawMiss = true;
                assertTrue(state.echoes().isEmpty(), "raté : pas de rejeu");
            } else {
                sawHit = true;
                assertEquals(1, state.echoes().size(), "touché : rejoué au tour suivant");
            }
        }
        assertTrue(sawMiss && sawHit);
    }

    @Test
    void unSortRateGardeLeShotDeJagerPourLeSuivant() {
        for (long seed = 1; seed <= 50; seed++) {
            setUpSturdy(seed, plain());
            drinkTimes(2);
            engine.give(cardistry.items.ItemRegistry.item("shot-de-jager")); // +1 Ivresse : Cuite
            engine.submit(new Action.UseItem(0));
            assertEquals(DrunkTier.WASTED, state.drunkTier());
            engine.submit(new Action.PlayCard(0));
            if (count(GameEvent.SpellMissed.class) == 1) {
                assertTrue(state.nextSpellBoost().isPresent(), "le ×2 attend le prochain sort");
                return;
            }
        }
        throw new AssertionError("aucune seed ne rate");
    }

    @Test
    void toutesLesStatsDeDegatsEtDeSoinExistent() {
        for (cardistry.model.Element element : cardistry.model.Element.values()) {
            assertEquals(1.0, new Stats().get(StatType.damageMultiplier(element)), 1e-9);
            assertEquals(1.0, new Stats().get(StatType.healMultiplier(element)), 1e-9);
        }
    }
}
