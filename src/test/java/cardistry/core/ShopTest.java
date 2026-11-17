package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.WATER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.event.GameEvent.ActionRejected.Reason;
import cardistry.core.shop.ShopAction;
import cardistry.core.shop.ShopOffer;
import cardistry.core.shop.ShopPrices;
import cardistry.core.stats.Stats;
import cardistry.items.Collectible;
import cardistry.items.ItemRegistry;
import cardistry.items.LootTable;
import cardistry.items.Rarity;
import cardistry.items.Trinket;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.HealCard;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Shop : prix, articles, achats, remplacement, reroll, retrait de carte. */
class ShopTest {

    private final List<GameEvent> events = new ArrayList<>();
    private GameState state;
    private CombatEngine engine;

    private void setUp(long seed, int deckSize) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        List<Card> deck = IntStream.range(0, deckSize).<Card>mapToObj(i -> new SpellCard("S" + i, FIRE, 1, cardistry.items.Rarity.COMMON)).toList();
        state = GameState.openRun(new Player(100), new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON), new Stats(), deck);
        engine = new CombatEngine(state, new CardPool(), new Rng(seed), bus, new LootTable(List::of));
        engine.start();
    }

    private void open(long seed, int choomies) {
        setUp(seed, 12);
        state.setChoomies(choomies);
        engine.openShop();
    }

    private Shop shop() {
        return engine.shop().orElseThrow();
    }

    private int firstOf(Class<?> kind, java.util.function.Predicate<ShopOffer> filter) {
        for (int i = 0; i < shop().offers().size(); i++) {
            ShopOffer offer = shop().offers().get(i);
            if (kind.isInstance(offer) && filter.test(offer)) {
                return i;
            }
        }
        throw new AssertionError("article introuvable");
    }

    private boolean rejected(Reason reason) {
        return events.contains(new GameEvent.ActionRejected(reason));
    }

    // --- Prix ---

    @Test
    void prixDesObjetsEtTrinketsSelonLaRarete() {
        assertEquals(50, ShopPrices.price(ItemRegistry.item("mate")));
        for (Collectible c : ItemRegistry.all()) {
            assertEquals(c.rarity().price(), ShopPrices.price(c));
        }
        assertEquals(List.of(50, 80, 130, 200), List.of(Rarity.COMMON.price(), Rarity.UNCOMMON.price(),
                Rarity.RARE.price(), Rarity.LEGENDARY.price()));
    }

    @Test
    void cartesMoinsCheresQueLesObjetsDeMemeRarete() {
        assertEquals(Rarity.COMMON, ShopPrices.cardRarity(CardPool.bouleDeFeu()));
        assertEquals(Rarity.RARE, ShopPrices.cardRarity(CardPool.pyromane()));
        assertEquals(Rarity.COMMON, ShopPrices.cardRarity(new cardistry.model.card.DeadCard()));
        // 60 % du prix d'un objet de même rareté, arrondi à 5 : 30 / 50 / 80 / 120.
        assertEquals(30, ShopPrices.price(CardPool.bouleDeFeu()));
        assertEquals(50, ShopPrices.price(CardPool.moindreCarre()));
        assertEquals(80, ShopPrices.price(CardPool.pyromane()));
        assertEquals(120, ShopPrices.price(CardPool.professeurDOr()));
        assertEquals(30, ShopPrices.price(new SpellCard("Gros sort commun", FIRE, 80, Rarity.COMMON)),
                "le prix suit la rareté, pas les dégâts");
        for (Card card : new CardPool().cards()) {
            assertEquals(card.rarity(), ShopPrices.cardRarity(card), card.name());
            assertTrue(ShopPrices.price(card) < card.rarity().price(), card.name());
        }
    }

    @Test
    void prixDuRetraitEtDuReroll() {
        assertEquals(List.of(75, 100, 125), List.of(ShopPrices.removal(0), ShopPrices.removal(1), ShopPrices.removal(2)));
        assertEquals(List.of(20, 30, 40), List.of(ShopPrices.reroll(0), ShopPrices.reroll(1), ShopPrices.reroll(2)));
    }

    // --- Articles ---

    @Test
    void cinqCartesDeuxObjetsDeuxTrinkets() {
        for (long seed = 0; seed < 50; seed++) {
            open(seed, 0);
            List<ShopOffer> offers = shop().offers();
            assertEquals(9, offers.size());
            List<String> cards = offers.subList(0, 5).stream().map(o -> ((ShopOffer.CardOffer) o).card().name()).toList();
            assertEquals(5, cards.stream().distinct().count());
            for (ShopOffer o : offers.subList(5, 7)) {
                assertInstanceOf(cardistry.items.Item.class, ((ShopOffer.CollectibleOffer) o).collectible());
            }
            for (ShopOffer o : offers.subList(7, 9)) {
                Collectible c = ((ShopOffer.CollectibleOffer) o).collectible();
                assertInstanceOf(Trinket.class, c);
                assertNotEquals(Rarity.SECRET, c.rarity());
            }
            assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.ShopEntered));
        }
    }

    @Test
    void pasDeTrinketDejaEquipe() {
        for (long seed = 0; seed < 50; seed++) {
            setUp(seed, 12);
            for (Trinket t : ItemRegistry.trinkets().subList(0, 5)) {
                engine.equip(t);
            }
            engine.openShop();
            for (ShopOffer o : shop().offers().subList(7, 9)) {
                assertFalse(state.hasTrinket(((ShopOffer.CollectibleOffer) o).collectible().id()));
            }
        }
    }

    @Test
    void pasDeShopPendantUnCombat() {
        setUp(1, 12);
        engine.startCombat(cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.CAME, 1), false);
        assertThrows(IllegalStateException.class, engine::openShop);
    }

    // --- Achats ---

    @Test
    void acheterUneCarte() {
        open(1, 200);
        ShopOffer.CardOffer offer = (ShopOffer.CardOffer) shop().offers().get(0);
        assertTrue(engine.shopAction(ShopAction.Buy.of(0)));
        assertEquals(200 - offer.price(), state.choomies());
        assertEquals(13, state.deck().size());
        assertEquals(offer.card().name(), state.deck().get(12).name());
        assertTrue(shop().isSold(0));
        assertTrue(events.contains(new GameEvent.ItemBought(offer, null)));

        assertFalse(engine.shopAction(ShopAction.Buy.of(0)));
        assertTrue(rejected(Reason.SOLD_OUT));
        assertEquals(13, state.deck().size());
    }

    @Test
    void pasAssezDeChoomies() {
        open(1, 10);
        assertFalse(engine.shopAction(ShopAction.Buy.of(0)));
        assertTrue(rejected(Reason.NOT_ENOUGH_CHOOMIES));
        assertEquals(10, state.choomies());
        assertFalse(shop().isSold(0));
        assertEquals(12, state.deck().size());
    }

    @Test
    void acheterUnObjet() {
        open(1, 500);
        int index = Shop.CARDS;
        ShopOffer.CollectibleOffer offer = (ShopOffer.CollectibleOffer) shop().offers().get(index);
        assertTrue(engine.shopAction(ShopAction.Buy.of(index)));
        assertEquals(List.of(offer.collectible()), state.items());
        assertEquals(500 - offer.price(), state.choomies());
    }

    @Test
    void inventairePleinRemplacer() {
        open(1, 1000);
        for (int i = 0; i < state.itemSlots(); i++) {
            state.addItem(ItemRegistry.item("mate"));
        }
        int index = Shop.CARDS;
        assertFalse(engine.shopAction(ShopAction.Buy.of(index)));
        assertTrue(rejected(Reason.INVENTORY_FULL));
        assertEquals(1000, state.choomies());

        ShopOffer.CollectibleOffer offer = (ShopOffer.CollectibleOffer) shop().offers().get(index);
        assertTrue(engine.shopAction(new ShopAction.Buy(index, 1)));
        assertEquals(offer.collectible(), state.items().get(state.items().size() - 1));
        assertEquals(state.itemSlots(), state.items().size());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.ItemBought b && b.offer().equals(offer)
                && b.replaced() != null && b.replaced().id().equals("mate")));
    }

    /** Shop ouvert avec 5 trinkets équipés ; l'index du Diplôme EPFL en vente, ou -1. */
    private int openFullShopWithDiplome(long seed) {
        setUp(seed, 12);
        for (int i = 0; i < 5; i++) {
            engine.equip(Trinket.builder("t" + i, "t" + i, Rarity.COMMON).build());
        }
        state.setChoomies(10_000);
        engine.openShop();
        for (int i = 0; i < shop().offers().size(); i++) {
            if (shop().offers().get(i) instanceof ShopOffer.CollectibleOffer c
                    && c.collectible().id().equals("diplome-epfl")) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void diplomeAcheteAvecUneBarrePleineSansRemplacer() {
        long seed = 0;
        int index;
        while ((index = openFullShopWithDiplome(seed)) < 0) {
            assertTrue(++seed < 2000, "aucune graine ne met le Diplôme en vente");
        }
        int price = ((ShopOffer.CollectibleOffer) shop().offers().get(index)).price();

        assertTrue(engine.shopAction(ShopAction.Buy.of(index)));

        assertFalse(rejected(Reason.INVENTORY_FULL));
        assertTrue(state.hasTrinket("diplome-epfl"));
        assertEquals(6, state.trinkets().size());
        assertEquals(10_000 - price, state.choomies());
    }

    @Test
    void trinketsPleinsRemplacer() {
        open(2, 1000);
        List<Trinket> five = ItemRegistry.trinkets().stream().filter(t -> t.rarity() == Rarity.COMMON).limit(5).toList();
        engine.shopAction(new ShopAction.Leave());
        five.forEach(engine::equip);
        engine.openShop();
        int index = Shop.CARDS + Shop.ITEMS;
        assertFalse(engine.shopAction(ShopAction.Buy.of(index)));
        assertTrue(engine.shopAction(new ShopAction.Buy(index, 0)));
        assertFalse(state.hasTrinket(five.get(0).id()));
        assertEquals(5, state.trinkets().size());
    }

    @Test
    void grinderAchetéAjouteUnePetiteTaffe() {
        boolean found = false;
        for (long seed = 0; seed < 400 && !found; seed++) {
            events.clear();
            open(seed, 1000);
            for (int i = Shop.CARDS + Shop.ITEMS; i < shop().offers().size(); i++) {
                ShopOffer.CollectibleOffer c = (ShopOffer.CollectibleOffer) shop().offers().get(i);
                if (c.collectible().id().equals("grinder")) {
                    engine.shopAction(ShopAction.Buy.of(i));
                    assertEquals("Petite taffe", state.deck().get(state.deck().size() - 1).name());
                    found = true;
                }
            }
        }
        assertTrue(found, "Grinder jamais proposé");
    }

    // --- Reroll ---

    @Test
    void rerollPayantAuCoutCroissantParShop() {
        open(1, 100);
        List<ShopOffer> before = shop().offers().stream().toList();
        assertTrue(engine.shopAction(new ShopAction.Reroll()));
        assertEquals(80, state.choomies());
        assertEquals(30, shop().rerollPrice());
        assertNotEquals(before, shop().offers());
        assertTrue(events.contains(new GameEvent.ShopRestocked(20)));
        assertTrue(engine.shopAction(new ShopAction.Reroll()));
        assertEquals(50, state.choomies());

        engine.shopAction(new ShopAction.Leave());
        engine.openShop();
        assertEquals(20, shop().rerollPrice(), "le prix repart à 20 dans un nouveau shop");
    }

    @Test
    void rerollTropCher() {
        open(1, 19);
        assertFalse(engine.shopAction(new ShopAction.Reroll()));
        assertTrue(rejected(Reason.NOT_ENOUGH_CHOOMIES));
        assertEquals(0, shop().rerolls());
    }

    // --- Retrait de carte ---

    @Test
    void retraitDeCarteAuCoutCroissantSurLaPartie() {
        open(1, 300);
        assertEquals(75, engine.cardRemovalPrice());
        assertTrue(engine.shopAction(new ShopAction.RemoveCard(0)));
        assertEquals(11, state.deck().size());
        assertEquals(225, state.choomies());
        assertEquals(100, engine.cardRemovalPrice());
        engine.shopAction(new ShopAction.Leave());
        engine.openShop();
        assertEquals(100, engine.cardRemovalPrice(), "le prix ne repart pas à zéro");
        assertTrue(engine.shopAction(new ShopAction.RemoveCard(0)));
        assertEquals(125, state.choomies());
    }

    @Test
    void retraitJamaisSousCinqCartes() {
        setUp(1, 5);
        state.setChoomies(500);
        engine.openShop();
        assertFalse(engine.shopAction(new ShopAction.RemoveCard(0)));
        assertTrue(rejected(Reason.DECK_TOO_SMALL));
        assertEquals(500, state.choomies());
    }

    @Test
    void retraitTropCher() {
        open(1, 74);
        assertFalse(engine.shopAction(new ShopAction.RemoveCard(0)));
        assertTrue(rejected(Reason.NOT_ENOUGH_CHOOMIES));
        assertEquals(12, state.deck().size());
    }

    @Test
    void partir() {
        open(1, 0);
        assertTrue(engine.shopAction(new ShopAction.Leave()));
        assertTrue(engine.shop().isEmpty());
        assertTrue(events.contains(new GameEvent.ShopLeft()));
        assertThrows(IllegalStateException.class, () -> engine.shopAction(new ShopAction.Reroll()));
    }

    @Test
    void lesTrinketsReagissentAuShop() {
        open(1, 0);
        List<String> seen = new ArrayList<>();
        cardistry.core.event.HookDispatcher.dispatch(new cardistry.core.event.GameHooks() {
            @Override
            public void onShopEnter(GameEvent.ShopEntered event, EffectContext ctx) {
                seen.add("shop");
            }
        }, events.stream().filter(e -> e instanceof GameEvent.ShopEntered).findFirst().orElseThrow(), null);
        assertEquals(List.of("shop"), seen);
    }
}
