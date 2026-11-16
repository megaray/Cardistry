package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.Stats;
import cardistry.items.Collectible;
import cardistry.items.ItemRegistry;
import cardistry.items.LootTable;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.DeadCard;
import cardistry.model.card.HealCard;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Deck construit : pioche, défausse, remélange, récompense de carte, retrait. */
class DeckTest {

    private final List<GameEvent> events = new ArrayList<>();
    private GameState state;
    private CombatEngine engine;

    private void setUp(List<Card> deck, CardPool pool, Supplier<List<Collectible>> loot, long seed) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        state = GameState.openRun(new Player(100), new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON), new Stats(), deck);
        engine = new CombatEngine(state, pool, new Rng(seed), bus, new LootTable(loot));
        engine.start();
    }

    private void setUp(List<Card> deck) {
        setUp(deck, new CardPool(List.of(new TerrainCard("Pool", WATER, cardistry.items.Rarity.COMMON))), List::of, 1);
    }

    /** Sorts de 1 dégât numérotés : « S0 », « S1 »… */
    private static List<Card> numbered(int count) {
        return IntStream.range(0, count).<Card>mapToObj(i -> new SpellCard("S" + i, FIRE, 1, cardistry.items.Rarity.COMMON)).toList();
    }

    private void fight(double hp) {
        engine.startCombat(new Enemy("Cible", NATURE, hp, 0), false);
    }

    private static List<String> names(List<Card> cards) {
        return cards.stream().map(Card::name).toList();
    }

    @Test
    void deckDeDepart() {
        List<Card> deck = GameState.newGame().deck();
        assertEquals(12, deck.size());
        assertEquals(6, deck.stream().filter(c -> c instanceof SpellCard).count());
        assertEquals(3, deck.stream().filter(c -> c instanceof HealCard).count());
        assertEquals(3, deck.stream().filter(c -> c instanceof TerrainCard).count());
        assertFalse(names(deck).contains("Dojo"), "le terrain de départ est en jeu, pas dans le deck");
        assertTrue(deck.stream().filter(c -> c instanceof SpellCard)
                .allMatch(c -> c.rarity() == cardistry.items.Rarity.COMMON), "sorts de départ communs");
    }

    @Test
    void mainDistribueeDepuisLeDeck() {
        setUp(numbered(12));
        fight(100);
        assertEquals(5, state.hand().size());
        assertEquals(7, state.drawPile().size());
        assertTrue(state.discardPile().isEmpty());
        List<String> all = new ArrayList<>(names(state.hand()));
        all.addAll(names(state.drawPile()));
        assertEquals(names(numbered(12)).stream().sorted().toList(), all.stream().sorted().toList());
        assertEquals(12, state.deck().size(), "le deck ne bouge pas");
    }

    @Test
    void melangeDeterministe() {
        setUp(numbered(12));
        fight(100);
        List<String> first = names(state.hand());
        events.clear();
        setUp(numbered(12));
        fight(100);
        assertEquals(first, names(state.hand()));
    }

    @Test
    void carteJoueeVaALaDefausse() {
        setUp(numbered(12));
        fight(100);
        Card played = state.hand().get(0);
        engine.submit(new Action.PlayCard(0));
        assertEquals(List.of(played.name()), names(state.discardPile()));
    }

    @Test
    void piocheDuDessusPuisRemelange() {
        setUp(numbered(6)); // main 5, pioche 1
        fight(100);
        Card top = state.drawPile().get(0);
        engine.submit(new Action.PlayCard(0)); // défausse : 1
        engine.submit(new Action.DrawCard()); // pioche la dernière
        assertEquals(top.name(), state.hand().get(state.hand().size() - 1).name());
        assertTrue(state.drawPile().isEmpty());
        engine.submit(new Action.PlayCard(0)); // défausse : 2
        engine.submit(new Action.DrawCard()); // pioche vide : la défausse est remélangée
        assertTrue(events.contains(new GameEvent.DeckShuffled(2)));
        assertEquals(1, state.drawPile().size());
        assertTrue(state.discardPile().isEmpty());
    }

    @Test
    void lesCartesMortesNeComptentPasDansLaTailleDeMain() {
        // Vafi / NG : une main de 7 cartes toutes mortes ne doit pas bloquer le combat.
        setUp(numbered(12));
        fight(100);
        engine.submit(new Action.DrawCard());
        engine.submit(new Action.DrawCard());
        assertEquals(state.handSize(), state.hand().size());
        for (int i = 0; i < state.hand().size(); i++) {
            state.replaceInHand(i, new DeadCard());
        }
        assertEquals(0, state.handCount());
        assertEquals(ActionResult.TURN_ENDED, engine.submit(new Action.DrawCard()));
        assertEquals(8, state.hand().size());
        assertEquals(1, state.handCount());
    }

    @Test
    void uneMainPleineDeCartesVivantesResteBloqueeALaPioche() {
        setUp(numbered(12));
        fight(100);
        engine.submit(new Action.DrawCard());
        engine.submit(new Action.DrawCard());
        state.replaceInHand(0, new DeadCard());
        assertEquals(ActionResult.TURN_ENDED, engine.submit(new Action.DrawCard())); // 6 vivantes + 1 morte
        assertEquals(ActionResult.REJECTED, engine.submit(new Action.DrawCard())); // 7 vivantes
        assertTrue(events.contains(new GameEvent.ActionRejected(GameEvent.ActionRejected.Reason.HAND_FULL)));
    }

    @Test
    void lesCartesProposeesSontTireesSelonLeurRarete() {
        GameState game = GameState.newGame();
        SpellCard common = new SpellCard("Commune", FIRE, 1, cardistry.items.Rarity.COMMON);
        SpellCard legendary = new SpellCard("Légendaire", FIRE, 1, cardistry.items.Rarity.LEGENDARY);
        assertEquals(40, CombatEngine.offerWeight(game, common), 1e-9);
        assertEquals(3, CombatEngine.offerWeight(game, legendary), 1e-9);
        assertEquals(java.util.List.of(40, 30, 20, 3), java.util.stream.Stream.of(cardistry.items.Rarity.COMMON,
                cardistry.items.Rarity.UNCOMMON, cardistry.items.Rarity.RARE, cardistry.items.Rarity.LEGENDARY)
                .map(cardistry.items.Rarity::cardWeight).toList(), "poids des cartes par rareté");
        CardPool pool = new CardPool(List.of(common, legendary));
        Rng rng = new Rng(3);
        int legendaries = 0;
        int trials = 30_500;
        for (int i = 0; i < trials; i++) {
            legendaries += pool.draw(rng, c -> CombatEngine.offerWeight(game, c)).rarity()
                    == cardistry.items.Rarity.LEGENDARY ? 1 : 0;
        }
        assertEquals(3.0 / 43, (double) legendaries / trials, 0.004);
    }

    @Test
    void plusRienAPiocher() {
        setUp(numbered(5)); // tout est en main
        fight(100);
        assertEquals(ActionResult.REJECTED, engine.submit(new Action.DrawCard()));
        assertTrue(events.contains(new GameEvent.ActionRejected(GameEvent.ActionRejected.Reason.DECK_EMPTY)));
    }

    @Test
    void nouveauCombatNouvellePioche() {
        setUp(numbered(12), new CardPool(List.of(new TerrainCard("Pool", WATER, cardistry.items.Rarity.COMMON))), List::of, 1);
        fight(1);
        engine.startTurn();
        engine.submit(new Action.PlayCard(0)); // tue
        engine.resolveCardReward(-1);
        fight(100);
        assertEquals(5, state.hand().size());
        assertEquals(7, state.drawPile().size());
        assertTrue(state.discardPile().isEmpty());
    }

    @Test
    void georgeRemplaceDepuisLePoolEtDefausse() {
        setUp(numbered(12));
        fight(100);
        engine.equip(ItemRegistry.trinket("george"));
        engine.submit(new Action.DrawCard());
        assertEquals(1, state.discardPile().size(), "la carte remplacée part à la défausse");
        assertTrue(names(state.hand()).contains("Pool"));
        assertEquals(12, state.deck().size());
    }

    @Test
    void carteMorteNeRetournePasAuDeck() {
        setUp(numbered(12));
        fight(100);
        engine.equip(ItemRegistry.trinket(ItemRegistry.NULL_POINTER_EXCEPTION));
        // La carte transformée part à la défausse ; « NO CARDS » ne va jamais à la défausse.
        state.discard(new DeadCard());
        assertTrue(state.discardPile().isEmpty());
    }

    @Test
    void paquetDeCartesPiocheDansLaPioche() {
        setUp(numbered(12));
        engine.setDecisions(new PlayerDecisions() {
            @Override
            public int chooseDrawnCard(GameState s, List<Card> options) {
                return 0;
            }

            @Override
            public int chooseTrinketToDiscard(GameState s) {
                return 0;
            }
        });
        engine.equip(ItemRegistry.trinket("paquet-de-cartes"));
        fight(100);
        engine.submit(new Action.DrawCard());
        assertEquals(6, state.hand().size());
        assertEquals(4, state.drawPile().size()); // 7 − 3 proposées
        assertEquals(2, state.discardPile().size(), "les deux autres sont défaussées");
    }

    // --- Récompense de carte ---

    @Test
    void recompenseDeCarteApresLObjet() {
        setUp(numbered(12), new CardPool(), () -> List.of(ItemRegistry.items().get(0)), 3);
        fight(1);
        engine.startTurn();
        engine.submit(new Action.PlayCard(0));
        assertTrue(engine.hasPendingReward());
        assertTrue(engine.hasPendingCardReward());
        assertFalse(events.stream().anyMatch(e -> e instanceof GameEvent.CardRewardOffered), "après l'objet");
        engine.resolveReward(new RewardChoice.Skip());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.CardRewardOffered));
        List<Card> options = engine.pendingCardReward();
        assertEquals(3, options.size());
        assertEquals(3, options.stream().map(Card::name).distinct().count());
        engine.resolveCardReward(1);
        assertEquals(13, state.deck().size());
        assertEquals(options.get(1).name(), state.deck().get(12).name());
        assertFalse(engine.hasPendingCardReward());
    }

    @Test
    void recompenseDeCartePassee() {
        setUp(numbered(12), new CardPool(), List::of, 3);
        fight(1);
        engine.startTurn();
        engine.submit(new Action.PlayCard(0));
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.CardRewardOffered), "sans objet : tout de suite");
        engine.resolveCardReward(-1);
        assertEquals(12, state.deck().size());
        assertTrue(events.contains(new GameEvent.CardRewardSkipped()));
    }

    @Test
    void sansDeckPasDeRecompenseDeCarte() {
        EventBus bus = new EventBus();
        GameState fixed = new GameState(new Player(100), List.of(new Enemy("A", NATURE, 1, 0), new Enemy("B", NATURE, 9, 0)),
                new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON), List.of(new SpellCard("S", FIRE, 5, cardistry.items.Rarity.COMMON)), new Stats());
        CombatEngine fixedEngine = new CombatEngine(fixed, new CardPool(), new Rng(1), bus, new LootTable(List::of));
        fixedEngine.submit(new Action.PlayCard(0));
        assertFalse(fixedEngine.hasPendingCardReward());
    }

    // --- Grinder ---

    @Test
    void grinderAjouteUnePetiteTaffeEtLaRendPlusFrequente() {
        setUp(numbered(12), new CardPool(), () -> List.of(ItemRegistry.trinket("grinder")), 3);
        fight(1);
        engine.startTurn();
        engine.submit(new Action.PlayCard(0));
        engine.resolveReward(RewardChoice.Take.into(0));
        assertEquals("Petite taffe", state.deck().get(state.deck().size() - 1).name());
        assertEquals(13, state.deck().size());
        assertEquals(2.0, state.cardWeight(new HealCard("Petite taffe", FIRE, 15, cardistry.items.Rarity.COMMON)));
    }

    @Test
    void petiteTaffeDeuxFoisPlusFrequenteDansLesRecompenses() {
        int without = countPetiteTaffe(false);
        int with = countPetiteTaffe(true);
        assertTrue(with > without * 1.5, "avec Grinder " + with + ", sans " + without);
    }

    private int countPetiteTaffe(boolean grinder) {
        int count = 0;
        for (long seed = 0; seed < 300; seed++) {
            setUp(numbered(12), new CardPool(), List::of, seed);
            if (grinder) {
                engine.equip(ItemRegistry.trinket("grinder"));
            }
            fight(1);
            engine.startTurn();
            engine.submit(new Action.PlayCard(0));
            count += (int) engine.pendingCardReward().stream().filter(c -> c.name().equals("Petite taffe")).count();
        }
        return count;
    }

    // --- Retrait ---

    @Test
    void retirerUneCarteJamaisSousCinq() {
        setUp(numbered(6));
        assertTrue(engine.canRemoveCard());
        engine.removeCardFromDeck(0);
        assertEquals(5, state.deck().size());
        assertFalse(engine.canRemoveCard());
        assertThrows(IllegalArgumentException.class, () -> engine.removeCardFromDeck(0));
        assertTrue(events.contains(new GameEvent.CardRemovedFromDeck(numbered(6).get(0))) || events.stream()
                .anyMatch(e -> e instanceof GameEvent.CardRemovedFromDeck r && r.card().name().equals("S0")));
    }
}
