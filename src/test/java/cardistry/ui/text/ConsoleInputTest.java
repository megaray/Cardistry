package cardistry.ui.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Action;
import cardistry.core.CombatEngine;
import cardistry.core.GameState;
import cardistry.core.RewardChoice;
import cardistry.core.Rng;
import cardistry.core.event.EventBus;
import cardistry.items.ItemRegistry;
import cardistry.run.Secret;
import cardistry.ui.TitleChoice;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mode texte : code secret sur l'écran titre, annulation. */
class ConsoleInputTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private ConsoleInput input(String typed) {
        Console console = new Console(new ByteArrayInputStream(typed.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), true, false, false);
        return new ConsoleInput(console);
    }

    @Test
    void codeClaudeSurLEcranTitre() {
        TitleChoice choice = input("claude\ns\n").title(12);
        assertEquals(List.of(Secret.CLAUDE_MD), choice.codes());
        assertFalse(choice.showRules());
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Seed : 12"));
        assertTrue(text.contains("Code accepté : CLAUDE.md"));
    }

    @Test
    void sansCode() {
        TitleChoice choice = input("\n").title(1);
        assertTrue(choice.codes().isEmpty());
        assertTrue(choice.showRules());
    }

    @Test
    void zAnnuleAvecCtrlZ() {
        GameState state = GameState.newGame();
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(1), new EventBus());
        assertEquals(new Action.DrawCard(), input("z\n0\n").nextAction(state), "sans Ctrl+Z, z n'annule rien");
        engine.equip(ItemRegistry.trinket(ItemRegistry.CTRL_Z));
        assertEquals(new Action.Undo(), input("z\n").nextAction(state));
    }

    @Test
    void pointsDeStatsEnModeTexte() {
        GameState state = GameState.newGame(cardistry.hero.HeroRegistry.SASSY);
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(1), new EventBus());
        engine.start();
        engine.recoverAfterBoss();
        assertEquals(cardistry.core.stats.StatType.RESISTANCE, input("9\n5\n").chooseStatPoint(state));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("2 point(s) de stats à placer"), text);
        assertTrue(text.contains("[5] RES 2 · Résistance"), text);
    }

    @Test
    void fichesDeStatsEnModeTexte() {
        GameState state = GameState.newGame(cardistry.hero.HeroRegistry.FREDERIC);
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(1), new EventBus());
        engine.start();
        engine.startCombat(cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.CADDIE, 1),
                false);
        assertEquals(new Action.DrawCard(), input("c\na\n0\n").nextAction(state));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("=== Fiche de Frederic BLC ==="), text);
        assertTrue(text.contains("FOR 2 · Force"), text);
        assertTrue(text.contains("=== Fiche de Caddie énervé ==="), text);
        assertTrue(text.contains("DEF 2 · Défense"), text);
    }

    @Test
    void carteEnModeTexte() {
        cardistry.run.Run run = new cardistry.run.Run(4, java.util.Set.of(), new EventBus());
        run.start();
        cardistry.run.map.MapView view = new cardistry.run.map.MapView(run.state(), run.map().orElseThrow(),
                run.visited(), run.position(), run.choices());
        Console console = new Console(new ByteArrayInputStream("9\n2\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), true, false, false);
        new TextRenderer(console).showMap(view);
        assertEquals(1, new ConsoleInput(console).chooseNode(view));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Carte · Niveau 1/3"), text);
        assertTrue(text.contains("[1] Étage 1 : Combat"));
        assertTrue(text.contains("[2] Étage 1 : Combat"));
        assertTrue(text.contains("B"));
        assertTrue(text.contains("Entre un nombre entre 1 et"), "9 est refusé");
    }

    @Test
    void shopEnModeTexte() {
        GameState state = GameState.newGame();
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(3), new EventBus());
        engine.start();
        for (int i = 0; i < 3; i++) {
            engine.give(ItemRegistry.item("mate"));
        }
        engine.gainChoomies(985); // 1000
        engine.openShop();
        cardistry.core.Shop shop = engine.shop().orElseThrow();
        // « 6 » : premier objet, inventaire plein → remplacer le 2e ; puis r, t + carte 3, 0.
        ConsoleInput input = input("x\n6\n2\nr\nt\n3\n0\n");
        assertEquals(new cardistry.core.shop.ShopAction.Buy(5, 1), input.chooseShopAction(state, shop, 75));
        assertEquals(new cardistry.core.shop.ShopAction.Reroll(), input.chooseShopAction(state, shop, 75));
        assertEquals(new cardistry.core.shop.ShopAction.RemoveCard(2), input.chooseShopAction(state, shop, 75));
        assertEquals(new cardistry.core.shop.ShopAction.Leave(), input.chooseShopAction(state, shop, 75));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("SHOP · 1000 Choomies"), text);
        assertTrue(text.contains("Cartes :") && text.contains("Objets :") && text.contains("Trinkets :"));
        assertTrue(text.contains("r) Reroll : 20"));
        assertTrue(text.contains("t) Retirer une carte : 75"));
        assertTrue(text.contains("Inventaire plein"));
        assertTrue(text.contains("Entre un numéro d'article"), "« x » est refusé");
    }

    // --- Un trinket qui apporte sa place (Diplôme EPFL) ---

    private static cardistry.items.Trinket plain(String id) {
        return cardistry.items.Trinket.builder(id, id, cardistry.items.Rarity.COMMON).build();
    }

    private static void fillTrinkets(GameState state, CombatEngine engine) {
        for (int i = 0; state.trinkets().size() < state.trinketSlots(); i++) {
            engine.equip(plain("t" + i));
        }
    }

    @Test
    void recompenseDiplomeAvecUneBarrePleineSansRemplacer() {
        GameState state = GameState.newGame();
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(1), new EventBus());
        fillTrinkets(state, engine);

        RewardChoice choice = input("1\n").chooseReward(state, List.of(ItemRegistry.trinket("diplome-epfl")));

        assertEquals(RewardChoice.Take.into(0), choice);
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Inventaire plein"));
    }

    @Test
    void recompenseTrinketNormalAvecUneBarrePleineDemandeLeRemplacement() {
        GameState state = GameState.newGame();
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(1), new EventBus());
        fillTrinkets(state, engine);

        RewardChoice choice = input("1\n2\n").chooseReward(state, List.of(plain("nouveau")));

        assertEquals(new RewardChoice.Take(0, 1), choice);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Inventaire plein"));
    }

    @Test
    void shopDiplomeAvecUneBarrePleineSansRemplacer() {
        GameState state = null;
        cardistry.core.Shop shop = null;
        int index = -1;
        for (long seed = 0; index < 0; seed++) {
            assertTrue(seed < 2000, "aucune graine ne met le Diplôme en vente");
            state = GameState.newGame();
            CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(seed), new EventBus());
            engine.start();
            fillTrinkets(state, engine);
            engine.gainChoomies(10_000);
            engine.openShop();
            shop = engine.shop().orElseThrow();
            for (int i = 0; i < shop.offers().size(); i++) {
                if (shop.offers().get(i) instanceof cardistry.core.shop.ShopOffer.CollectibleOffer c
                        && c.collectible().id().equals("diplome-epfl")) {
                    index = i;
                }
            }
        }

        cardistry.core.shop.ShopAction action = input((index + 1) + "\n").chooseShopAction(state, shop, 75);

        assertEquals(cardistry.core.shop.ShopAction.Buy.of(index), action);
        assertFalse(out.toString(StandardCharsets.UTF_8).contains("Inventaire plein"));
    }

    @Test
    void shopTropCherEnModeTexte() {
        GameState state = GameState.newGame();
        CombatEngine engine = new CombatEngine(state, new cardistry.model.card.CardPool(), new Rng(3), new EventBus());
        engine.start();
        engine.openShop();
        input("0\n").chooseShopAction(state, engine.shop().orElseThrow(), 75);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("(trop cher)"));
    }

    @Test
    void evenementEnModeTexte() {
        GameState state = GameState.newGame();
        cardistry.story.Story machine = cardistry.story.StoryRegistry.byId("machine-a-clopes").orElseThrow();
        assertEquals(2, input("1\n3\n").chooseStory(state, machine));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("? Machine à clopes ?"), text);
        assertTrue(text.contains("[1] Acheter un paquet (indisponible : il te faut 20 Choomies)"));
        assertTrue(text.contains("Ce choix n'est pas possible"));
    }
}
