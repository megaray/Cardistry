package cardistry.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.stats.StatModifier;
import cardistry.core.stats.StatType;
import cardistry.core.stats.Stats;
import cardistry.items.LootTable;
import cardistry.items.Rarity;
import cardistry.model.Element;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.TerrainCard;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Nouvelles cartes du plan V2 (§8), lot par lot. */
class NewCardsTest {

    private GameState state;
    private CombatEngine engine;
    private Enemy enemy;

    /** Adversaire neutre de {@code hp} PV, terrain neutre sans effet, main = {@code hand}. */
    private void setUp(double hp, long seed, Stats stats, Card... hand) {
        enemy = new Enemy("Cible", Element.NEUTRAL, hp, 0);
        state = new GameState(new Player(100), List.of(enemy, new Enemy("Suivant", Element.NEUTRAL, 10, 0)),
                new TerrainCard("Neutre", Element.NEUTRAL, Rarity.COMMON), List.of(hand), stats);
        engine = new CombatEngine(state, new CardPool(), new Rng(seed), new EventBus(), new LootTable(List::of));
        engine.startTurn();
    }

    private void setUp(double hp, Card... hand) {
        setUp(hp, 1, new Stats(), hand);
    }

    private void play() {
        engine.submit(new Action.PlayCard(0));
    }

    private static Card card(String name) {
        return new CardPool().cards().stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow().copy();
    }

    // --- Lot 1 ---

    @Test
    void cayouuuEtPainPita() {
        setUp(100, card("CAYOUUU"), card("Pain pita"));
        play();
        assertEquals(88, enemy.hp());
        engine.startTurn();
        play();
        assertEquals(78, enemy.hp());
        assertEquals(Element.GEOMETRIC, card("Pain pita").element());
        assertEquals(Rarity.COMMON, card("CAYOUUU").rarity());
        assertEquals(Rarity.COMMON, card("Pain pita").rarity());
    }

    @Test
    void matrixDoubleSiLesPvFinissentParZeroOuUnAvantLeCoup() {
        for (double hp : List.of(50.0, 31.0, 100.0, 1001.0)) {
            setUp(hp, CardPool.matrix());
            play();
            assertEquals(hp - 20, enemy.hp(), "PV " + hp);
        }
        for (double hp : List.of(42.0, 39.0, 55.0)) {
            setUp(hp, CardPool.matrix());
            play();
            assertEquals(hp - 10, enemy.hp(), "PV " + hp);
        }
        setUp(30, CardPool.matrix()); // 30 → 10 : vérifié avant le coup, pas après
        play();
        assertEquals(10, enemy.hp());
        assertEquals(Element.ALGEBRAIC, CardPool.matrix().element());
        assertEquals(Rarity.COMMON, CardPool.matrix().rarity());
    }

    @Test
    void moindreCarreDegatsDeUnATrentePlusDeuxIntEnTaFaveur() {
        int min = Integer.MAX_VALUE;
        int max = 0;
        double sum = 0;
        double sumLucky = 0;
        int runs = 600;
        for (long seed = 0; seed < runs; seed++) {
            setUp(1000, seed, new Stats(), CardPool.moindreCarre());
            play();
            int dealt = 1000 - enemy.hp();
            min = Math.min(min, dealt);
            max = Math.max(max, dealt);
            sum += dealt;
            Stats lucky = new Stats();
            lucky.add(StatModifier.flat(StatType.LUCKY_ROLLS, 1, "claude"));
            setUp(1000, seed, lucky, CardPool.moindreCarre());
            play();
            sumLucky += 1000 - enemy.hp();
        }
        assertEquals(1, min);
        assertEquals(30, max, "sans INT");
        assertEquals(15.5, sum / runs, 1.5);
        assertEquals(20.5, sumLucky / runs, 1.5, "CLAUDE.md : le meilleur de deux tirages");
        int maxWithInt = 0;
        for (long seed = 0; seed < 300; seed++) {
            Stats smart = new Stats();
            smart.add(StatModifier.flat(StatType.INTELLIGENCE, 5, "test"));
            setUp(1000, seed, smart, CardPool.moindreCarre());
            play();
            maxWithInt = Math.max(maxWithInt, 1000 - enemy.hp());
        }
        assertEquals(40, maxWithInt, "INT 5 : jusqu'à 30 + 10, sans ajouter l'INT une seconde fois");
        assertEquals(Rarity.UNCOMMON, CardPool.moindreCarre().rarity());
    }

    @Test
    void distPlayerDixPlusLesEtagesParcourus() {
        setUp(100, CardPool.distPlayer());
        engine.enterNode(1, "Combat");
        engine.enterNode(2, "Combat");
        engine.enterNode(3, "Combat");
        play();
        assertEquals(100 - 13, enemy.hp());
        assertEquals(Element.GEOMETRIC, CardPool.distPlayer().element());
        assertEquals(Rarity.UNCOMMON, CardPool.distPlayer().rarity());
    }

    @Test
    void lesDegatsCalculesRestentMultiplies() {
        Stats stats = new Stats();
        stats.add(StatModifier.percent(StatType.ALGEBRAIC_DAMAGE, 0.5, "test"));
        setUp(42, 1, stats, CardPool.matrix());
        play();
        assertEquals(42 - 15, enemy.hp());
    }

    // --- Lot 2 ---

    private static Card spell(double damage) {
        return new cardistry.model.card.SpellCard("Coup", Element.NEUTRAL, damage, Rarity.COMMON);
    }

    @Test
    void purificationDesChakraAucunDegatPuisAttaquesX2AuProchainTour() {
        setUp(100, CardPool.purificationDesChakra(), spell(10), spell(10));
        play();
        assertEquals(100, enemy.hp());
        engine.startTurn();
        play();
        assertEquals(80, enemy.hp(), "hypnotisé : ×2");
        engine.startTurn();
        play();
        assertEquals(70, enemy.hp(), "un tour seulement");
        assertEquals(Element.SPIRIT, CardPool.purificationDesChakra().element());
        assertEquals(Rarity.RARE, CardPool.purificationDesChakra().rarity());
    }

    @Test
    void professeurDOrTrenteCinqNonMultipliesPuisHypnoseCinqTours() {
        Stats stats = new Stats();
        stats.add(StatModifier.percent(StatType.SPIRIT_DAMAGE, 1.0, "test"));
        Card[] hand = {CardPool.professeurDOr(), spell(1), spell(1), spell(1), spell(1), spell(1), spell(1)};
        setUp(1000, 1, stats, hand);
        play();
        assertEquals(1000 - 35, enemy.hp(), "ni l'élément, ni les stats, ni sa propre hypnose");
        int hp = enemy.hp();
        for (int turn = 1; turn <= 5; turn++) {
            engine.startTurn();
            play();
            assertEquals(hp - 2, enemy.hp(), "tour " + turn + " : ×2");
            hp = enemy.hp();
        }
        engine.startTurn();
        play();
        assertEquals(hp - 1, enemy.hp(), "fini après 5 tours");
        assertEquals(Rarity.LEGENDARY, CardPool.professeurDOr().rarity());
    }

    @Test
    void pyromaneQuinzeOuTrenteSiLAdversaireBrule() {
        setUp(100, CardPool.pyromane());
        play();
        assertEquals(85, enemy.hp());
        setUp(100, CardPool.pyromane());
        state.statuses(cardistry.core.status.StatusTarget.ENEMY).apply(cardistry.core.status.StatusType.BURN, 3,
                cardistry.core.status.StatusTarget.PLAYER);
        play();
        assertEquals(100 - 30 - 4, enemy.hp(), "30, puis 4 de Brûlure au début de son tour");
        assertEquals(Element.FIRE, CardPool.pyromane().element());
        assertEquals(Rarity.RARE, CardPool.pyromane().rarity());
    }

    @Test
    void somnifereEndortLAdversaireDeuxTours() {
        enemy = new Enemy("Cible", Element.NEUTRAL, 100, 10);
        state = new GameState(new Player(100), List.of(enemy), new TerrainCard("Neutre", Element.NEUTRAL,
                Rarity.COMMON), List.of(CardPool.somnifere()), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(), new LootTable(List::of));
        engine.startTurn();
        play();
        assertEquals(100, state.player().hp(), "endormi");
        assertEquals(100, enemy.hp(), "aucun dégât");
        engine.startTurn();
        engine.submit(new Action.DrawCard());
        assertEquals(100, state.player().hp(), "toujours endormi (2e tour)");
        engine.startTurn();
        engine.submit(new Action.DrawCard());
        assertEquals(90, state.player().hp(), "réveillé");
        assertEquals(Rarity.UNCOMMON, CardPool.somnifere().rarity());
    }

    // --- Lot 3 ---

    /** Terrain feu (sans effet sur les soins eau et neutres), adversaire neutre qui frappe de {@code attack}. */
    private void setUpFire(double attack, Card... hand) {
        enemy = new Enemy("Cible", Element.NEUTRAL, 100, attack);
        state = new GameState(new Player(100), List.of(enemy), new TerrainCard("Braise", Element.FIRE, Rarity.COMMON),
                List.of(hand), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(), new LootTable(List::of));
        engine.startTurn();
    }

    /** Pose {@code type} sur le joueur avec un objet (action gratuite) ; {@code true} s'il a été posé. */
    private boolean inflictOnPlayer(cardistry.core.status.StatusType type) {
        boolean[] landed = new boolean[1];
        engine.give(new cardistry.items.Item("test", "Test", "", "", Rarity.COMMON, java.util.Set.of(),
                ctx -> landed[0] = ctx.applyStatus(cardistry.core.status.StatusTarget.PLAYER, type, 1, 3)));
        engine.submit(new Action.UseItem(state.items().size() - 1));
        return landed[0];
    }

    @Test
    void reveilBrutalHuitOuVingtEtReveille() {
        setUp(100, CardPool.reveilBrutal());
        play();
        assertEquals(92, enemy.hp());
        setUpFire(10, CardPool.reveilBrutal());
        state.statuses(cardistry.core.status.StatusTarget.ENEMY).apply(cardistry.core.status.StatusType.FONS, 3,
                cardistry.core.status.StatusTarget.PLAYER);
        play();
        assertEquals(80, enemy.hp());
        assertEquals(90, state.player().hp(), "réveillé : il attaque à son tour");
        assertEquals(Rarity.UNCOMMON, CardPool.reveilBrutal().rarity());
    }

    @Test
    void contreExempleAnnuleLeProchainStatutSubi() {
        setUpFire(0, CardPool.contreExemple(), CardPool.contreExemple());
        play();
        assertEquals(100, enemy.hp(), "aucun dégât");
        engine.startTurn();
        assertTrue(!inflictOnPlayer(cardistry.core.status.StatusType.INTOX), "annulé");
        assertTrue(inflictOnPlayer(cardistry.core.status.StatusType.BURN), "un seul");
        play(); // deuxième Contre-exemple : deux gardes possibles d'affilée
        engine.startTurn();
        assertTrue(!inflictOnPlayer(cardistry.core.status.StatusType.BONDAGE));
        assertEquals(Element.ALGEBRAIC, CardPool.contreExemple().element());
        assertEquals(Rarity.RARE, CardPool.contreExemple().rarity());
    }

    @Test
    void contreExemplePerduALaFinDuCombat() {
        enemy = new Enemy("Cible", Element.NEUTRAL, 5, 0);
        state = new GameState(new Player(100), List.of(enemy, new Enemy("Suivant", Element.NEUTRAL, 100, 0)),
                new TerrainCard("Braise", Element.FIRE, Rarity.COMMON),
                List.of(CardPool.contreExemple(), new cardistry.model.card.SpellCard("Fin", Element.NEUTRAL, 10,
                        Rarity.COMMON)), new Stats());
        engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(), new LootTable(List::of));
        engine.startTurn();
        play();
        engine.startTurn();
        play(); // tue le premier adversaire
        assertTrue(state.effects().stream().noneMatch(TemporaryEffect::cancelsNextStatus));
    }

    @Test
    void verreDEauSparadrapSoupeDeMamie() {
        setUpFire(0, card("Verre d'eau"), card("Sparadrap"), CardPool.soupeDeMamie());
        state.player().takeDamage(50);
        play();
        assertEquals(60, state.player().hp());
        engine.startTurn();
        play();
        assertEquals(72, state.player().hp());
        state.statuses(cardistry.core.status.StatusTarget.PLAYER).apply(cardistry.core.status.StatusType.INTOX, 3,
                cardistry.core.status.StatusTarget.ENEMY);
        engine.startTurn(); // Intox −5
        assertEquals(67, state.player().hp());
        play();
        assertEquals(77, state.player().hp());
        assertTrue(!state.statuses(cardistry.core.status.StatusTarget.PLAYER).has(cardistry.core.status.StatusType.INTOX));
        assertEquals(Element.WATER, card("Verre d'eau").element());
        assertEquals(Element.NEUTRAL, card("Sparadrap").element());
        for (String name : List.of("Verre d'eau", "Sparadrap", "Soupe de mamie")) {
            assertEquals(Rarity.COMMON, card(name).rarity(), name);
        }
    }

    // --- Lot 4 ---

    @Test
    void stinginRogerMoinsCinqPvEtUnForCumulables() {
        setUpFire(0, CardPool.stinginRoger(), CardPool.stinginRoger(), spell(10));
        play();
        assertEquals(95, state.player().hp());
        engine.startTurn();
        play();
        assertEquals(90, state.player().hp());
        assertEquals(2, state.playerStats().get(StatType.STRENGTH), 1e-9);
        engine.startTurn();
        play();
        assertEquals(100 - 12, enemy.hp());
        assertEquals(Element.FIRE, CardPool.stinginRoger().element());
        assertEquals(Rarity.COMMON, CardPool.stinginRoger().rarity());
    }

    @Test
    void seauDEauVingtPv() {
        setUpFire(0, card("Seau d'eau"));
        state.player().takeDamage(50);
        play();
        assertEquals(70, state.player().hp());
        assertEquals(Rarity.UNCOMMON, card("Seau d'eau").rarity());
    }

    @Test
    void berceuseInconnueRetireLesStatutsNegatifsSeulement() {
        setUpFire(0, CardPool.berceuseInconnue());
        var statuses = state.statuses(cardistry.core.status.StatusTarget.PLAYER);
        for (var type : List.of(cardistry.core.status.StatusType.INTOX, cardistry.core.status.StatusType.FONS,
                cardistry.core.status.StatusType.BONDAGE, cardistry.core.status.StatusType.BURN,
                cardistry.core.status.StatusType.HYPNOTIZED)) {
            statuses.apply(type, 3, cardistry.core.status.StatusTarget.ENEMY);
        }
        play();
        assertEquals(List.of(cardistry.core.status.StatusType.HYPNOTIZED),
                statuses.all().stream().map(cardistry.core.status.Status::type).toList());
        assertEquals(Element.SPIRIT, CardPool.berceuseInconnue().element());
        assertEquals(Rarity.UNCOMMON, CardPool.berceuseInconnue().rarity());
    }

    @Test
    void taffeDeFoufouBatardTroisToursImmuniseEtHuitPourcentParTour() {
        setUpFire(10, CardPool.taffeDeFoufouBatard());
        state.player().takeDamage(50);
        play(); // l'adversaire frappe : immunisé
        assertEquals(50, state.player().hp());
        List<Integer> hp = new java.util.ArrayList<>();
        for (int turn = 1; turn <= 3; turn++) {
            assertTrue(!engine.startTurn(), "tour " + turn + " sauté");
            hp.add(state.player().hp());
        }
        assertEquals(List.of(58, 66, 64), hp, "+8 par tour ; après le 3e tour, la Fons finit et le coup passe");
        assertTrue(engine.startTurn(), "réveillé");
        assertEquals(Rarity.RARE, CardPool.taffeDeFoufouBatard().rarity());
    }

    @Test
    void taffeDeFoufouBatardLesStatutsAdversesPassentToujours() {
        setUpFire(0, CardPool.taffeDeFoufouBatard());
        state.player().takeDamage(50);
        play();
        state.statuses(cardistry.core.status.StatusTarget.PLAYER)
                .apply(cardistry.core.status.StatusType.INTOX, 3, cardistry.core.status.StatusTarget.ENEMY);
        engine.startTurn(); // Intox −5 malgré l'immunité aux coups, puis +8 de la Fons
        assertEquals(50 - 5 + 8, state.player().hp());
    }

    @Test
    void unSoinSansPvNeSoignePas() {
        java.util.List<cardistry.core.event.GameEvent> events = new java.util.ArrayList<>();
        enemy = new Enemy("Cible", Element.NEUTRAL, 100, 0);
        state = new GameState(new Player(100), List.of(enemy), new TerrainCard("Braise", Element.FIRE, Rarity.COMMON),
                List.of(CardPool.berceuseInconnue()), new Stats());
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        engine = new CombatEngine(state, new CardPool(), new Rng(1), bus, new LootTable(List::of));
        engine.startTurn();
        play();
        assertTrue(events.stream().noneMatch(e -> e instanceof cardistry.core.event.GameEvent.Healed));
    }
}
