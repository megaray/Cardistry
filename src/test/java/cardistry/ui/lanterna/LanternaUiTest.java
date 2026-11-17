package cardistry.ui.lanterna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Action;
import cardistry.core.CombatEngine;
import cardistry.core.GameState;
import cardistry.core.RewardChoice;
import cardistry.core.Rng;
import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.Stats;
import cardistry.items.Collectible;
import cardistry.items.Item;
import cardistry.items.ItemRegistry;
import cardistry.items.Rarity;
import cardistry.items.Trinket;
import cardistry.model.Element;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.HealCard;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import cardistry.run.Run;
import cardistry.ui.GameLoop;
import cardistry.ui.QuitGameException;
import cardistry.ui.Renderer;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.terminal.virtual.DefaultVirtualTerminal;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * L'interface est testée sur un terminal virtuel. Chaque écran testé est aussi écrit dans
 * {@code build/screens/} pour pouvoir relire la mise en page.
 */
class LanternaUiTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private DefaultVirtualTerminal terminal;
    private LanternaUi ui;

    private void open(int cols, int rows) throws IOException {
        terminal = new DefaultVirtualTerminal(new TerminalSize(cols, rows));
        ui = new LanternaUi(terminal, true);
    }

    @AfterEach
    void close() throws IOException {
        if (ui != null) {
            ui.close();
        }
    }

    private String screen() {
        TerminalSize size = terminal.getTerminalSize();
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < size.getRows(); row++) {
            StringBuilder line = new StringBuilder();
            for (int col = 0; col < size.getColumns(); col++) {
                line.append(terminal.getCharacter(col, row).getCharacterString());
            }
            text.append(line.toString().stripTrailing()).append('\n');
        }
        return text.toString();
    }

    private String dump(String name) throws IOException {
        String text = screen();
        Path dir = Path.of("build", "screens");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(name + ".txt"), text);
        return text;
    }

    private void type(String keys) {
        for (char c : keys.toCharArray()) {
            terminal.addInput(new KeyStroke(c, false, false));
        }
    }

    private void press(KeyType type) {
        terminal.addInput(new KeyStroke(type));
    }

    private static GameState combat(List<Card> hand, Enemy... enemies) {
        return new GameState(new Player(100), List.of(enemies), new TerrainCard("EPFL", Element.FIRE, cardistry.items.Rarity.COMMON), hand, new Stats());
    }

    // --- Titre ---

    @Test
    void titreEntreeLanceSansIntro() throws IOException {
        open(80, 24);
        press(KeyType.Enter);

        assertFalse(assertTimeoutPreemptively(TIMEOUT, () -> ui.title(42)).showRules());

        String text = dump("01-titre");
        assertTrue(text.contains("▶ Jouer"));
        assertTrue(text.contains("Options") && text.contains("Quitter"));
        assertTrue(text.contains("Seed 42"));
    }

    @Test
    void titreRAfficheLesRegles() throws IOException {
        open(80, 24);
        type("r");
        assertTrue(assertTimeoutPreemptively(TIMEOUT, () -> ui.title(1)).showRules());

        GameState state = GameState.newGame();
        press(KeyType.Enter);
        assertTimeoutPreemptively(TIMEOUT, () -> ui.showIntro(state));

        String text = dump("02-regles");
        assertTrue(text.contains("8 étages"));
        assertTrue(text.contains("prochain coup"));
    }

    @Test
    void titreQQuitte() throws IOException {
        open(80, 24);
        type("q");
        assertThrows(QuitGameException.class, () -> assertTimeoutPreemptively(TIMEOUT, () -> ui.title(1)));
    }

    // --- Combat ---

    @Test
    void ecranDeCombat() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(
                new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON),
                new SpellCard("Pluie de flammes", Element.FIRE, 15, cardistry.items.Rarity.COMMON),
                new HealCard("Caïpirinha", Element.WATER, 10, cardistry.items.Rarity.COMMON),
                new TerrainCard("Marécage", Element.WATER, cardistry.items.Rarity.COMMON)),
                new Enemy("Mannequin", Element.FIRE, 20, 5), new Enemy("Bouée", Element.WATER, 30, 10),
                new Enemy("Pantin", Element.NATURE, 40, 15));
        ui.onEvent(new GameEvent.GameStarted(42));
        ui.showStatus(state);

        String text = dump("03-combat");
        assertTrue(text.contains("MANNEQUIN"));
        assertTrue(text.contains("Combat 1/3"));
        assertTrue(text.contains("Prochain : Attaque : 6 (×1.25)"));
        assertTrue(text.contains("EPFL (feu)"));
        assertTrue(text.contains("100/100"));
        assertTrue(text.contains("Ta main 4/7"));
        assertTrue(text.contains("Jet d'eau"));
        assertTrue(text.contains("Caïpiri-"));
        assertTrue(text.contains("vide"));
        // Détail de la carte sélectionnée (la première) avec son multiplicateur réel.
        assertTrue(text.contains("10 ×1.5 = 15 dégâts à Mannequin"));
        assertTrue(text.contains("P piocher"));
    }

    @Test
    void statutsACoteDesPvEtAdversaireEndormi() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)),
                new Enemy("Mannequin", Element.FIRE, 20, 5), new Enemy("Bouée", Element.WATER, 30, 10));
        var enemyStatuses = state.statuses(cardistry.core.status.StatusTarget.ENEMY);
        enemyStatuses.apply(cardistry.core.status.StatusType.INTOX, 3, cardistry.core.status.StatusTarget.PLAYER);
        enemyStatuses.apply(cardistry.core.status.StatusType.FONS, 2, cardistry.core.status.StatusTarget.PLAYER);
        state.statuses(cardistry.core.status.StatusTarget.PLAYER)
                .apply(cardistry.core.status.StatusType.BURN, 3, cardistry.core.status.StatusTarget.ENEMY);
        ui.onEvent(new GameEvent.GameStarted(42));
        ui.showStatus(state);
        String text = dump("03b-combat-statuts");
        assertTrue(text.contains("Int3 Fon2"), text);
        assertTrue(text.contains("Brû3"), text);
        assertTrue(text.contains("Prochain : Endormi (2 tours)"), text);
    }

    @Test
    void pouvoirDeCarteAfficheSurLaCarteEtDansLaDescription() throws IOException {
        open(80, 24);
        SpellCard boule = new SpellCard("Boule de feu", Element.FIRE, 10, cardistry.items.Rarity.COMMON)
                .withPower("10 % d'infliger Brûlure", ctx -> { });
        SpellCard aleatoire = new SpellCard("Moindre carré", Element.ALGEBRAIC, 0, cardistry.items.Rarity.UNCOMMON)
                .withDamageRule("1-40", ctx -> 1);
        GameState state = combat(List.of(boule, aleatoire), new Enemy("Mannequin", Element.NATURE, 20, 5));
        assertEquals("Boule de feu · sortilège feu : 10 ×1.5 = 15 dégâts à Mannequin · ★ 10 % d'infliger Brûlure",
                CombatScreen.describe(state, boule));
        assertEquals("Moindre carré · sortilège algébrique : 1-40 dégâts à Mannequin",
                CombatScreen.describe(state, aleatoire));
        ui.onEvent(new GameEvent.GameStarted(42));
        ui.showStatus(state);
        String text = dump("03c-combat-pouvoir");
        assertTrue(text.contains("★"), text);
        assertTrue(text.contains("1-40 dgt"), text);
        assertTrue(cardistry.ui.text.TextRendererAccess.describe(boule).endsWith("★ 10 % d'infliger Brûlure"));
    }

    @Test
    void flechesPuisEntreeJoueLaCarteSelectionnee() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(
                new TerrainCard("A", Element.FIRE, cardistry.items.Rarity.COMMON), new TerrainCard("B", Element.WATER, cardistry.items.Rarity.COMMON),
                new TerrainCard("C", Element.NATURE, cardistry.items.Rarity.COMMON)), hydra());
        press(KeyType.ArrowRight);
        press(KeyType.ArrowRight);
        press(KeyType.Enter);

        assertEquals(new Action.PlayCard(2), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void flecheGaucheBoucle() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new TerrainCard("A", Element.FIRE, cardistry.items.Rarity.COMMON), new TerrainCard("B", Element.WATER, cardistry.items.Rarity.COMMON)), hydra());
        press(KeyType.ArrowLeft);
        press(KeyType.Enter);

        assertEquals(new Action.PlayCard(1), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void chiffresEtPioche() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new TerrainCard("A", Element.FIRE, cardistry.items.Rarity.COMMON)), hydra());
        type("3p0");

        assertEquals(new Action.PlayCard(2), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        assertEquals(new Action.DrawCard(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        assertEquals(new Action.DrawCard(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void quitterDemandeConfirmation() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new TerrainCard("A", Element.FIRE, cardistry.items.Rarity.COMMON)), hydra());
        type("qn1");
        assertEquals(new Action.PlayCard(0), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));

        type("qo");
        // Abandonner la partie ramène au titre : ce n'est pas QuitGameException (quitter l'application).
        assertThrows(cardistry.ui.LeaveGameException.class,
                () -> assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void terminalTropPetit() throws IOException {
        open(60, 20);
        ui.showStatus(combat(List.of(), hydra()));
        String text = dump("05-trop-petit");
        assertTrue(text.contains("Terminal trop petit : 60×20"));
    }

    @Test
    void grandTerminalAgranditLeJournal() throws IOException {
        open(120, 40);
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)), hydra());
        ui.showStatus(state);
        for (int i = 1; i <= 15; i++) {
            ui.onEvent(new GameEvent.TurnStarted(i));
        }
        ui.showHand(state);
        String text = dump("06-grand-terminal");
        assertTrue(text.contains("— Tour 1 —"));
        assertTrue(text.contains("— Tour 15 —"));
    }

    // --- Choix du personnage ---

    @Test
    void choixDuPersonnageAvecLesFlechesEtEntree() throws IOException {
        open(80, 24);
        press(KeyType.ArrowRight);
        press(KeyType.ArrowRight);
        press(KeyType.Enter);
        java.util.Optional<cardistry.hero.Hero> hero = assertTimeoutPreemptively(TIMEOUT,
                () -> ui.chooseHero(cardistry.hero.HeroRegistry.all()));
        assertEquals(cardistry.hero.HeroRegistry.SASSY, hero.orElseThrow());
        String text = dump("92-personnages");
        for (cardistry.hero.Hero h : cardistry.hero.HeroRegistry.all()) {
            assertTrue(text.contains(" " + h.name() + " "), text);
        }
        assertTrue(text.contains("130 PV") && text.contains("100 PV"), text);
        // Le personnage sélectionné (Sassy) : capacité et description entières, sur toute la largeur.
        assertTrue(text.contains("▶ Sassy · wadayatalkinabit"), text);
        String flat = text.replaceAll("[│\\s]+", " ");
        assertTrue(flat.contains(cardistry.hero.HeroRegistry.SASSY.description()), text);
    }

    @Test
    void choixDuPersonnageEchapRevientAuTitre() throws IOException {
        open(80, 24);
        press(KeyType.Escape);
        assertTrue(assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseHero(cardistry.hero.HeroRegistry.all()))
                .isEmpty());
    }

    // --- Partie complète ---

    @Test
    void partieCompleteJusquAEcranDeFin() throws IOException {
        open(80, 24);
        EventBus bus = new EventBus();
        Run engine = new Run(7, java.util.Set.of(), bus);
        bus.subscribe(ui::onEvent);

        press(KeyType.Enter); // titre
        press(KeyType.Enter); // personnage : Louis
        for (int i = 0; i < 3000; i++) {
            type("12ps"); // 1er nœud, 1re carte (ou 2e choix d'un événement), piocher, refuser les récompenses
            press(KeyType.Escape); // quitter le shop (ailleurs : demande de confirmation…
            type("n"); // … refusée)
        }

        // Capture l'écran de combat au tour 3.
        Renderer recorder = new Renderer() {
            @Override public void onEvent(GameEvent event) { }
            @Override public void showIntro(GameState state) { ui.showIntro(state); }
            @Override public void showStatus(GameState state) { ui.showStatus(state); }
            @Override public void showHand(GameState state) {
                ui.showHand(state);
                if (state.turn() == 3) {
                    try {
                        dump("04-combat-tour-3");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            @Override public void showGameOver(GameState state, List<String> secrets) { ui.showGameOver(state, secrets); }
        };

        assertTimeoutPreemptively(TIMEOUT, () -> GameLoop.run(engine, recorder, ui));

        assertTrue(engine.isOver());
        String text = dump("07-fin");
        assertTrue(text.contains(engine.state().isWon() ? "VICTOIRE" : "DÉFAITE"));
        assertTrue(text.contains("--seed 7"));
    }

    // --- Objets, trinkets, récompenses ---

    private static Item mate() {
        return ItemRegistry.items().get(0);
    }

    private static Trinket briquets() {
        return ItemRegistry.trinkets().get(0);
    }

    /** État avec inventaire : passe par le moteur pour équiper. */
    private static GameState withInventory(List<Item> items, List<Trinket> trinkets) {
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)), hydra());
        CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus());
        items.forEach(engine::give);
        trinkets.forEach(engine::equip);
        return state;
    }

    @Test
    void combatAfficheObjetsEtTrinkets() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(mate()), List.of(briquets()));
        ui.showStatus(state);
        String text = dump("08-combat-inventaire");
        assertTrue(text.contains("[W] Maté"));
        assertTrue(text.contains("[E] ·"));
        assertTrue(text.contains("Trinkets 1/5"));
        assertTrue(text.contains("Redistribution des briquets"));
        assertTrue(text.contains("W/E/R objets"));
        assertTrue(text.contains("Q quitter ─"), "l'aide tient dans le cadre");
    }

    @Test
    void toucheWUtiliseLePremierObjet() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(mate()), List.of());
        type("w");
        assertEquals(new Action.UseItem(0), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void toucheIOuvreLInventaire() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(mate()), List.of(briquets()));
        Action[] result = new Action[1];
        Thread player = new Thread(() -> result[0] = ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("i");

        // La ligne d'aide du bas est écrite en dernier : l'écran est alors complet.
        waitFor(() -> screen().contains("Appuie sur une touche pour revenir"));
        String text = dump("09-inventaire");
        type("x");   // retour au combat
        type("p");   // puis piocher
        player.join(TIMEOUT.toMillis());

        assertTrue(text.contains("Maté"));
        assertTrue(text.contains("+25 % dégâts feu"));
        assertEquals(new Action.DrawCard(), result[0]);
    }

    @Test
    void ecranDeRecompensePrendreLaDeuxieme() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        List<Collectible> options = List.of(mate(), briquets(), mate());
        press(KeyType.ArrowRight);
        press(KeyType.Enter);

        RewardChoice choice = assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseReward(state, options));

        assertEquals(RewardChoice.Take.into(1), choice);
    }

    @Test
    void ecranDeRecompenseRenduPuisRefus() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        List<Collectible> options = List.of(mate(), briquets());
        RewardChoice[] result = new RewardChoice[1];
        Thread player = new Thread(() -> result[0] = ui.chooseReward(state, options));
        player.setDaemon(true);
        player.start();

        waitFor(() -> screen().contains("S refuser · Q quitter"));
        String text = dump("10-recompense");
        type("s");
        player.join(TIMEOUT.toMillis());

        assertTrue(text.contains("Objet · Commun"));
        assertTrue(text.contains("Trinket · Commun"));
        assertTrue(text.contains("Maté"));
        assertTrue(text.contains("S refuser"));
        assertEquals(new RewardChoice.Skip(), result[0]);
    }

    @Test
    void inventairePleinDemandeQuoiRemplacer() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(mate(), mate(), mate()), List.of());
        List<Collectible> options = List.of(mate());
        press(KeyType.Enter);          // prendre → inventaire plein → écran de remplacement
        press(KeyType.ArrowRight);     // 1er objet
        press(KeyType.ArrowRight);     // 2e objet
        press(KeyType.Enter);

        assertEquals(new RewardChoice.Take(0, 1), assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseReward(state, options)));
    }

    @Test
    void trinketsPleinsEntreeRepeteeNeRemplaceRien() throws IOException {
        open(100, 32);
        List<Trinket> owned = ItemRegistry.trinkets().subList(0, 5);
        GameState state = withInventory(List.of(), owned);
        List<Collectible> options = List.of(ItemRegistry.trinkets().get(6));
        press(KeyType.Enter);          // prendre → écran de remplacement
        press(KeyType.Enter);          // Entrée répétée : rien n'est sélectionné, rien ne se passe
        press(KeyType.Enter);
        type("3");                     // choix explicite du 3e trinket
        press(KeyType.Enter);

        assertEquals(new RewardChoice.Take(0, 2), assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseReward(state, options)));
    }

    @Test
    void ecranDeRemplacementSansPreselection() throws IOException {
        open(100, 32);
        GameState state = withInventory(List.of(), ItemRegistry.trinkets().subList(0, 5));
        List<Collectible> options = List.of(ItemRegistry.trinkets().get(6));
        press(KeyType.Enter);          // écran de remplacement
        press(KeyType.Escape);         // retour aux récompenses
        type("s");
        assertEquals(new RewardChoice.Skip(), assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseReward(state, options)));
    }

    @Test
    void inventairePleinTientALEcran() throws IOException {
        open(80, 24);
        GameState state = withInventory(
                List.of(ItemRegistry.item("cafffeeeeinnnn"), ItemRegistry.item("gaz-du-bat-inf"), mate()),
                List.of(ItemRegistry.trinket("latte-macchiato-vanille"), ItemRegistry.trinket("comprehension-algebrique"),
                        ItemRegistry.trinket("george"), ItemRegistry.trinket("cms"), ItemRegistry.trinket("pax")));
        ui.showStatus(state);
        String text = dump("11-inventaire-plein");
        assertTrue(text.contains("[W] "));
        assertTrue(text.contains("[E] "));
        assertTrue(text.contains("[R] Maté"));
        assertTrue(text.contains("Trinkets 5/5"));
    }

    @Test
    void recompenseAvecDescriptionLongue() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        List<Collectible> options = List.of(ItemRegistry.trinket("george"),
                ItemRegistry.trinket("comprehension-algebrique"), ItemRegistry.item("gaz-du-bat-inf"));
        Thread player = new Thread(() -> ui.chooseReward(state, options));
        player.setDaemon(true);
        player.start();
        waitFor(() -> screen().contains("S refuser · Q quitter"));
        String text = dump("12-recompense-george");
        type("s");
        player.join(TIMEOUT.toMillis());
        assertTrue(text.contains("George"));
        assertTrue(text.contains("Légendaire"));
        assertTrue(text.contains("║pool "), "description complète (dernier mot)");
        assertTrue(text.contains("biens communs"), "phrase d'ambiance");
    }

    @Test
    void ivresseEtGueuleDeBoisAffichees() throws IOException {
        open(80, 24);
        Enemy first = hydra();
        GameState state = new GameState(new Player(100), List.of(first, new Enemy("Bouée", Element.WATER, 30, 10),
                new Enemy("Pantin", Element.NATURE, 40, 15)), new TerrainCard("EPFL", Element.FIRE, cardistry.items.Rarity.COMMON),
                List.of(new SpellCard("Tsunami", Element.WATER, 100, cardistry.items.Rarity.COMMON)), new Stats());
        CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(),
                new cardistry.items.LootTable(List::of));
        for (int i = 0; i < 3; i++) {
            engine.give(ItemRegistry.item("spritz"));
            engine.submit(new Action.UseItem(0));
        }
        ui.showStatus(state);
        String during = dump("13-ivresse");
        assertTrue(during.contains("Ivresse 3/3"));
        assertTrue(during.contains("Gueule de bois à venir"));

        engine.submit(new Action.PlayCard(0)); // tue le Mannequin → combat suivant avec Gueule de bois
        ui.showStatus(state);
        String after = dump("14-gueule-de-bois");
        assertTrue(after.contains("Gueule de bois (−1 taille de main)"));
        assertTrue(after.contains("Ta main 5/6"));
    }

    @Test
    void journalDistingueCarteEtObjetHomonymes() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        ui.showStatus(state);
        ui.onEvent(new GameEvent.CardPlayed(new cardistry.model.card.HealCard("Petit pétou", Element.FIRE, 15, cardistry.items.Rarity.COMMON)));
        ui.onEvent(new GameEvent.ItemUsed(ItemRegistry.item("petit-petou")));
        ui.showHand(state);
        String text = screen();
        assertTrue(text.contains("Tu joues la carte Petit pétou"));
        assertTrue(text.contains("Tu utilises l'objet Petit pétou"));
    }

    @Test
    void effetsEnCoursAffichesAvecLeurDuree() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(),
                new cardistry.items.LootTable(List::of));
        for (String id : List.of("shot-de-jager", "space-cake", "moscow-mule")) {
            engine.give(ItemRegistry.item(id));
            engine.submit(new Action.UseItem(0));
        }
        ui.showStatus(state);
        String text = dump("15-effets");
        // Trop long en version complète : version courte, objets d'abord.
        assertTrue(text.contains("Shot de Jäger · Space cake (dans 3) · +1"), text); // Moscow Mule ne tient pas

        // L'inventaire liste tout, avec la durée.
        Thread player = new Thread(() -> ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("i");
        waitFor(() -> screen().contains("Appuie sur une touche pour revenir"));
        String inventory = dump("16-inventaire-effets");
        type("xqo");
        player.join(TIMEOUT.toMillis());
        assertTrue(inventory.contains("Shot de Jäger : prochain sort ×2"));
        assertTrue(inventory.contains("Space cake : dans 3 tours"));
        assertTrue(inventory.contains("Moscow Mule : annule la prochaine attaque"));
    }

    @Test
    void effetSeulEnVersionLongue() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(),
                new cardistry.items.LootTable(List::of));
        engine.give(ItemRegistry.item("shot-de-jager"));
        engine.submit(new Action.UseItem(0));
        ui.showStatus(state);
        assertTrue(screen().contains("Shot de Jäger : prochain sort ×2"));
    }

    @Test
    void paquetDeClopesJaugeHonoreEtPvAuDessusDuMax() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(ItemRegistry.item("spritz")),
                List.of(ItemRegistry.trinket("honore"), ItemRegistry.trinket("ross")));
        CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(),
                new cardistry.items.LootTable(List::of));
        engine.submit(new Action.UseItem(0));   // Spritz ×2 sans plafond → 160 PV
        engine.startTurn();
        engine.startTurn();                      // Honoré 2/5
        engine.submit(new Action.Smoke());       // 4 clopes, +5 PV ×2 (Ross), sans plafond
        ui.showStatus(state);
        String text = dump("17-clopes");
        assertTrue(text.contains("4/20 clopes"), text);
        assertTrue(text.contains("Honoré [##...]"), text);
        assertTrue(text.contains("F fumer · G rouler"));
        assertTrue(text.contains("170/100"), text);
        assertTrue(text.contains("┌╥──"), "1 bâton sur 5 pour 4 clopes sur 20");
    }

    @Test
    void toucheFFumeEtGRoule() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        type("fg");
        assertEquals(new Action.Smoke(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        assertEquals(new Action.RollCigarette(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void setsDansLInventaireEtNotification() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus(),
                new cardistry.items.LootTable(List::of));
        ui.showStatus(state);
        engine.equip(ItemRegistry.trinket("diplome-epfl"));
        // L'UI n'est pas abonnée au bus de ce moteur : on lui envoie l'événement de palier directement.
        ui.onEvent(new GameEvent.SetTierActivated("EPFL « Cursus »", cardistry.items.Tag.EPFL, 2, "+10 % dégâts élémentaires"));
        engine.equip(ItemRegistry.trinket("pichet-de-sat"));
        engine.equip(ItemRegistry.trinket("pax"));
        ui.showHand(state);
        String combat = dump("18-notification-set");
        assertTrue(combat.contains("★ Set EPFL « Cursus » 2 activé"), combat);

        Thread player = new Thread(() -> ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("i");
        waitFor(() -> screen().contains("Appuie sur une touche pour revenir"));
        String inventory = dump("19-inventaire-sets");
        type("xqo");
        player.join(TIMEOUT.toMillis());
        assertTrue(inventory.contains("EPFL « Cursus » (2) ×2 : palier 2 · prochain 3 : +1 taille de main"), inventory);
        assertTrue(inventory.contains("+25 % dégâts eau (effet ×2)"), inventory);
        assertTrue(inventory.contains("Soirée « Before → Afterparty » (1) : aucun palier · prochain 2"), inventory);
    }

    @Test
    void ecranDeDefausseObligatoire() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of(ItemRegistry.trinket("pax"), ItemRegistry.trinket("ross")));
        int[] result = {-1};
        Thread player = new Thread(() -> result[0] = ui.chooseTrinketToDiscard(state));
        player.setDaemon(true);
        player.start();
        waitFor(() -> screen().contains("↑/↓ choisir · Entrée jeter"));
        String text = dump("20-defausse");
        press(KeyType.Escape);        // pas d'échappatoire
        press(KeyType.ArrowDown);
        press(KeyType.Enter);
        player.join(TIMEOUT.toMillis());
        assertTrue(text.contains("choisis-en un à jeter"), text);
        assertTrue(text.contains("▶ Pax"), text);
        assertEquals(1, result[0]);
    }

    @Test
    void ecranDePiocheAuChoix() throws Exception {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of());
        List<cardistry.model.card.Card> options = List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON),
                new cardistry.model.card.HealCard("Petit pétou", Element.FIRE, 15, cardistry.items.Rarity.COMMON), new TerrainCard("Forêt", Element.NATURE, cardistry.items.Rarity.COMMON));
        int[] first = {-1};
        Thread player = new Thread(() -> first[0] = ui.chooseDrawnCard(state, options));
        player.setDaemon(true);
        player.start();
        waitFor(() -> screen().contains("les autres sont défaussées"));
        String text = dump("21-pioche-au-choix");
        type("3");
        player.join(TIMEOUT.toMillis());

        assertEquals(2, first[0]);
        assertTrue(text.contains("Choisis la carte à garder"), text);
        assertTrue(text.contains("Un jeu de cartes dans ton jeu de cartes."), text);
        assertTrue(text.contains("10 ×1.5 = 15 dégâts à Mannequin"), "effet réel de la sélectionnée");

        // Deuxième fois : plus de phrase méta ; flèches + Entrée.
        int[] second = {-1};
        Thread again = new Thread(() -> second[0] = ui.chooseDrawnCard(state, options));
        again.setDaemon(true);
        again.start();
        waitFor(() -> screen().contains("les autres sont défaussées"));
        String secondScreen = screen();
        press(KeyType.ArrowRight);
        press(KeyType.Enter);
        again.join(TIMEOUT.toMillis());
        assertEquals(1, second[0]);
        assertTrue(!secondScreen.contains("Un jeu de cartes dans ton jeu de cartes."), "phrase méta seulement la 1re fois");
    }

    @Test
    void modeAsciiPurSurTousLesEcrans() throws Exception {
        terminal = new DefaultVirtualTerminal(new TerminalSize(80, 24));
        ui = new LanternaUi(terminal, true, true);
        GameState state = withInventory(List.of(ItemRegistry.item("caipirinha")), List.of(ItemRegistry.trinket("diplome-epfl")));
        ui.onEvent(new GameEvent.GameStarted(7));
        ui.showStatus(state);
        String combat = dump("22-ascii-combat");
        assertTrue(combat.chars().allMatch(c -> c < 128), combat);
        assertTrue(combat.contains("+- Cardistry -"), combat);
        assertTrue(combat.contains("####################"), "barres de PV");
        assertTrue(combat.contains("Caipirinha"), "accents retirés");

        type("q");
        assertThrows(QuitGameException.class, () -> assertTimeoutPreemptively(TIMEOUT, () -> ui.title(1)));
        String title = dump("23-ascii-titre");
        assertTrue(title.chars().allMatch(c -> c < 128), title);
        assertTrue(title.contains("###"), "bannière en #");
    }

    // --- Secrets ---

    private void ctrl(char c) {
        terminal.addInput(new KeyStroke(c, true, false));
    }

    @Test
    void titreCodesSecrets() throws IOException {
        open(80, 24);
        ctrl('z');
        type("xclaude");
        ctrl('z'); // déjà pris : pas de doublon
        press(KeyType.Enter);
        cardistry.ui.TitleChoice choice = assertTimeoutPreemptively(TIMEOUT, () -> ui.title(3));
        assertEquals(List.of(cardistry.run.Secret.CTRL_Z, cardistry.run.Secret.CLAUDE_MD), choice.codes());
        assertFalse(choice.showRules());
        String text = dump("24-titre-codes");
        assertTrue(text.contains("Code accepté : Ctrl+Z équipé pour cette partie"), text);
        assertTrue(text.contains("Code accepté : CLAUDE.md équipé pour cette partie"), text);
    }

    @Test
    void titreCodeIncompletIgnore() throws IOException {
        open(80, 24);
        type("claud cloude");
        press(KeyType.Enter);
        assertTrue(assertTimeoutPreemptively(TIMEOUT, () -> ui.title(3)).codes().isEmpty());
        assertFalse(dump("24b-titre-sans-code").contains("Code accepté"));
    }

    @Test
    void ctrlZEnCombatDemandeLAnnulation() throws IOException {
        open(80, 24);
        GameState state = withInventory(List.of(), List.of(ItemRegistry.trinket(ItemRegistry.CTRL_Z)));
        ctrl('z');
        assertEquals(new Action.Undo(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        String text = dump("25-combat-ctrl-z");
        assertTrue(text.contains("Ctrl+Z annuler"), text);
        assertTrue(text.contains("Q quitter ─"), "l'aide tient dans le cadre");
    }

    @Test
    void ctrlZDisponiblePuisUtiliseAffiche() throws IOException {
        open(80, 24);
        EventBus bus = new EventBus();
        Run run = new Run(1, java.util.Set.of(), bus);
        run.addCode(cardistry.run.Secret.CTRL_Z);
        run.start();
        run.chooseNode(0);
        run.startTurn();
        ui.showStatus(run.state());
        String available = dump("29-ctrl-z-disponible");
        assertTrue(available.contains("Ctrl+Z : disponible"), available);

        run.submit(new Action.DrawCard());
        run.startTurn();
        run.submit(new Action.Undo());
        ui.showStatus(run.state());
        String used = dump("30-ctrl-z-utilise");
        assertTrue(used.contains("Ctrl+Z : utilisé"), used);
    }

    @Test
    void sansCtrlZPasDEtat() throws IOException {
        open(80, 24);
        ui.showStatus(withInventory(List.of(), List.of()));
        assertFalse(screen().contains("Ctrl+Z :"));
    }

    @Test
    void carteMorteAffichee() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new cardistry.model.card.DeadCard(), new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)),
                hydra());
        ui.showHand(state);
        String text = dump("26-no-cards");
        assertTrue(text.contains("NO CARDS"), text);
        assertTrue(text.contains("bloquée"), text);
        assertTrue(text.contains("injouable jusqu"), text);
    }

    @Test
    void ecranDeFinSecretDebloque() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(), hydra());
        state.player().takeDamage(1000);
        ui.showGameOver(state, List.of("Ctrl+Z"));
        String text = dump("27-fin-secret");
        assertTrue(text.contains("Secret débloqué : Ctrl+Z"), text);
        assertTrue(text.contains("--seed"), text);
    }

    @Test
    void ecranDeFinSansSecret() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(), hydra());
        ui.showGameOver(state, List.of());
        assertFalse(dump("28-fin-sans-secret").contains("Secret"));
    }

    private void waitFor(java.util.function.BooleanSupplier condition) {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                throw new AssertionError("Écran attendu non affiché :\n" + screen());
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Enemy hydra() {
        return new Enemy("Mannequin", Element.FIRE, 20, 5);
    }

    // --- Sprites ---

    @Test
    void visionneuseDeSprites() throws IOException {
        open(80, 24);
        press(KeyType.ArrowRight);
        type("2");
        type("q");
        assertTimeoutPreemptively(TIMEOUT, () -> ui.viewSprites());
        String text = dump("40-sprites");
        assertTrue(text.contains("Sprites"));
        assertTrue(text.contains("Choomah"), text); // 2e sprite du registre
        assertTrue(text.contains("sprites/choomah.txt"));
        assertTrue(text.contains("1 idle"));
    }

    @Test
    void spriteEnCouleurDansLeCombat() throws IOException {
        open(80, 24);
        Enemy caddie = cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.CADDIE, 1);
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)), caddie);
        ui.onEvent(new GameEvent.GameStarted(42));
        ui.showStatus(state);
        String text = dump("41-combat-caddie");
        assertTrue(text.contains("(O)      (O)"), text);
        assertTrue(text.contains("Prochain : Roue voilée : 7"), text); // 6 + FOR 1
        assertTrue(text.contains("CADDIE ÉNERVÉ · neutre DEF 2 · RES 2"), "DEF et RES près de sa barre de PV");
        int row = text.lines().toList().indexOf(text.lines().filter(l -> l.contains("(O)")).findFirst().orElseThrow());
        int col = text.lines().toList().get(row).indexOf("(O)") + 1;
        assertEquals(com.googlecode.lanterna.TextColor.ANSI.YELLOW_BRIGHT,
                terminal.getCharacter(col, row).getForegroundColor());
    }

    // --- Carte ---

    private static cardistry.run.map.MapView mapView() {
        Run run = new Run(4, java.util.Set.of(), new EventBus());
        run.start();
        return new cardistry.run.map.MapView(run.state(), run.map().orElseThrow(), run.visited(), run.position(),
                run.choices());
    }

    @Test
    void ecranDeLaCarte() throws IOException {
        open(80, 24);
        cardistry.run.map.MapView view = mapView();
        press(KeyType.ArrowRight);
        press(KeyType.Enter);
        int chosen = assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseNode(view));
        assertEquals(1 % view.choices().size(), chosen);
        ui.showMap(view);
        String text = dump("50-carte");
        assertTrue(text.contains("Carte · Niveau 1/3"), text);
        assertTrue(text.contains("Étage 0/8"));
        assertTrue(text.contains("Légende"));
        assertTrue(text.contains("[C]"), "nœud sélectionné");
        assertTrue(text.contains("B"), "boss");
        assertTrue(text.contains("15 Choomies"));
        assertTrue(text.contains("→ Étage 1 : Combat"));
    }

    @Test
    void carteChiffreDirect() throws IOException {
        open(80, 24);
        cardistry.run.map.MapView view = mapView();
        type("2");
        assertEquals(1, (int) assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseNode(view)));
    }

    // --- Deck, récompense de carte, Sieste ---

    private static GameState deckState() {
        GameState state = GameState.newGame();
        cardistry.core.CombatEngine engine = new cardistry.core.CombatEngine(state, new cardistry.model.card.CardPool(),
                new cardistry.core.Rng(1), new EventBus());
        engine.start();
        engine.startCombat(cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.CAME, 1), false);
        return state;
    }

    @Test
    void toucheDOuvreLeDeckEnCombat() throws Exception {
        open(80, 24);
        GameState state = deckState();
        ui.showStatus(state);
        Action[] result = new Action[1];
        Thread player = new Thread(() -> result[0] = ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("d");
        waitFor(() -> screen().contains(DeckScreen.HELP));
        String text = dump("64-deck");
        type("d"); // ferme le deck
        type("p"); // puis pioche : on était bien revenu au combat
        player.join(TIMEOUT.toMillis());
        assertTrue(result[0] instanceof Action.DrawCard);
        assertTrue(text.contains("Ton deck"), text);
        assertTrue(text.contains("12 cartes · Pioche 7 · Défausse 0"), text);
        assertTrue(text.contains("Sorts (6)"), text);
        assertTrue(text.contains("×3 Potion · soin plante · Commun : +10 PV"), text);
        assertTrue(text.contains("Boule de feu · sort feu · Commun : 10 dégâts · ★ 10 % d'infliger Brûlure"), text);
    }

    @Test
    void toucheDOuvreLeDeckSurLaCarte() throws Exception {
        open(80, 24);
        cardistry.run.map.MapView view = mapView();
        int[] chosen = new int[1];
        Thread player = new Thread(() -> chosen[0] = ui.chooseNode(view));
        player.setDaemon(true);
        player.start();
        type("D");
        waitFor(() -> screen().contains(DeckScreen.HELP));
        String text = screen();
        press(KeyType.Escape);
        press(KeyType.Enter); // va au premier nœud : retour à la carte
        player.join(TIMEOUT.toMillis());
        assertEquals(0, chosen[0]);
        assertTrue(text.contains("Sorts (6)") && !text.contains("Pioche"), text);
    }

    @Test
    void fichesDeStatsDuJoueurEtDeLAdversaireEnCombat() throws Exception {
        open(80, 24);
        GameState state = GameState.newGame(cardistry.hero.HeroRegistry.LOUIS);
        cardistry.core.CombatEngine engine = new cardistry.core.CombatEngine(state, new cardistry.model.card.CardPool(),
                new cardistry.core.Rng(1), new EventBus());
        engine.start();
        engine.equip(cardistry.items.ItemRegistry.trinket("donut-muscle"));
        engine.startCombat(cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.PENEU, 2), false);
        ui.showStatus(state);
        Thread player = new Thread(() -> ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("c");
        waitFor(() -> screen().contains("Fiche de Louis"));
        String mine = dump("65-fiche-joueur");
        assertTrue(mine.contains("FOR 5"), mine);
        assertTrue(mine.contains("personnage +3, Donut Musclé +2"), mine);
        assertTrue(mine.contains("DEF −1"), mine); // 1 − 2 (Bord d'autoroute)
        assertTrue(mine.contains("terrain Bord d'autoroute −2"), mine);
        type("c");
        waitFor(() -> !screen().contains("Fiche de Louis"));
        type("a");
        waitFor(() -> screen().contains("Fiche de Le Peneu"));
        String his = dump("66-fiche-adversaire");
        assertTrue(his.contains("DEF 5"), his); // 4 + 1 (niveau 2)
        assertTrue(his.contains("RES 5"), his);
        press(KeyType.Escape);
        waitFor(() -> !screen().contains("Fiche de Le Peneu"));
        type("p");
        player.join(TIMEOUT.toMillis());
    }

    @Test
    void unGrosDeckDefileAvecLesFleches() throws Exception {
        open(80, 24);
        GameState state = GameState.newGame();
        cardistry.core.CombatEngine engine = new cardistry.core.CombatEngine(state, new cardistry.model.card.CardPool(),
                new cardistry.core.Rng(1), new EventBus());
        engine.start();
        new cardistry.model.card.CardPool().cards().forEach(c -> engine.addCardToDeck(c.copy()));
        engine.startCombat(cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.CAME, 1), false);
        ui.showStatus(state);
        Thread player = new Thread(() -> ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("d");
        String[] top = new String[1];
        waitFor(() -> {
            top[0] = screen();
            return top[0].contains(DeckScreen.HELP) && top[0].contains("▼") && top[0].contains("Sorts (");
        });
        assertFalse(top[0].contains("▲"), top[0]);
        for (int i = 0; i < 60; i++) {
            press(KeyType.ArrowDown);
        }
        String[] bottom = new String[1];
        waitFor(() -> {
            bottom[0] = screen(); // une seule capture, complète : pas de lecture en cours de dessin
            return bottom[0].contains("EPFL · terrain feu") && bottom[0].contains("▲")
                    && bottom[0].contains(DeckScreen.HELP);
        });
        Files.writeString(Path.of("build", "screens", "65-deck-defile.txt"), bottom[0]);
        assertFalse(bottom[0].contains("▼"), bottom[0]);
        type("d");
        type("p");
        player.join(TIMEOUT.toMillis());
        assertTrue(bottom[0].contains("Terrains"), bottom[0]);
    }

    @Test
    void deckDansLaFenetreAvecMiniatures() throws Exception {
        openWithImages(100, 32);
        GameState state = deckState();
        ui.showStatus(state);
        Thread player = new Thread(() -> ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("d");
        waitFor(() -> screen().contains(DeckScreen.HELP) && screen().contains("Volcan"));
        Thread.sleep(100);
        cardistry.ui.window.ImageLayer.Frame frame = layer.frame();
        java.awt.image.BufferedImage png = new cardistry.ui.window.GridRenderer(new cardistry.ui.window.CellFonts(2))
                .render(terminal, frame.images(), frame.marks(), false);
        javax.imageio.ImageIO.write(png, "png", Path.of("build", "screens", "66-fenetre-deck.png").toFile());
        assertTrue(frame.images().stream().anyMatch(i -> i.path().equals("cards/boule-de-feu.png")));
        type("d");
        type("p");
        player.join(TIMEOUT.toMillis());
    }

    @Test
    void toucheVAfficheLaFicheDeLaCarteEnCombat() throws Exception {
        open(80, 24);
        GameState state = deckState();
        ui.showStatus(state);
        Action[] result = new Action[1];
        Thread player = new Thread(() -> result[0] = ui.nextAction(state));
        player.setDaemon(true);
        player.start();
        type("v");
        waitFor(() -> screen().contains(CardDetailScreen.HELP) && screen().contains("Élément "));
        String text = dump("67-fiche-carte");
        type("v");
        type("p");
        player.join(TIMEOUT.toMillis());
        assertTrue(result[0] instanceof Action.DrawCard);
        String first = state.hand().get(0).name();
        assertTrue(text.contains(first), text);
        assertTrue(text.contains("Si tu la joues maintenant"), text);
    }

    @Test
    void toucheVAfficheLaFicheDUneCarteAuShop() throws Exception {
        openWithImages(100, 32);
        GameState state = deckState();
        cardistry.core.CombatEngine engine = new cardistry.core.CombatEngine(GameState.newGame(),
                new cardistry.model.card.CardPool(), new cardistry.core.Rng(2), new EventBus());
        engine.start();
        engine.openShop();
        cardistry.core.Shop shop = engine.shop().orElseThrow();
        GameState shopState = engine.state();
        Thread player = new Thread(() -> ui.chooseShopAction(shopState, shop, 75));
        player.setDaemon(true);
        player.start();
        type("v");
        waitFor(() -> screen().contains(CardDetailScreen.HELP) && screen().contains("Élément "));
        Thread.sleep(100);
        cardistry.ui.window.ImageLayer.Frame frame = layer.frame();
        java.awt.image.BufferedImage png = new cardistry.ui.window.GridRenderer(new cardistry.ui.window.CellFonts(2))
                .render(terminal, frame.images(), frame.marks(), false);
        javax.imageio.ImageIO.write(png, "png", Path.of("build", "screens", "68-fenetre-fiche-shop.png").toFile());
        String text = screen();
        type("v");
        type("p");
        player.join(TIMEOUT.toMillis());
        cardistry.model.card.Card card = ((cardistry.core.shop.ShopOffer.CardOffer) shop.offers().get(0)).card();
        assertTrue(text.contains(card.name()), text);
        assertTrue(!text.contains("Si tu la joues"), "au shop : valeurs de base seulement");
        assertTrue(frame.images().stream().anyMatch(i -> i.path().startsWith("cards/")), "grande illustration");
        assertTrue(state.hand().size() > 0);
    }

    @Test
    void piocheEtDefausseAffichees() throws IOException {
        open(80, 24);
        ui.showStatus(deckState());
        String text = dump("60-combat-deck");
        assertTrue(text.contains("Ta main 5/7 · Pioche 7 · Défausse 0"), text);
    }

    @Test
    void recompenseDeCarte() throws IOException {
        open(80, 24);
        GameState state = deckState();
        List<Card> options = List.of(new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON), new HealCard("Potion", Element.NATURE, 10, cardistry.items.Rarity.COMMON),
                new TerrainCard("Forêt", Element.NATURE, cardistry.items.Rarity.COMMON));
        press(KeyType.ArrowRight);
        press(KeyType.Enter);
        assertEquals(1, (int) assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseCardReward(state, options)));
        String text = dump("61-recompense-carte");
        assertTrue(text.contains("Ajoute une carte à ton deck"), text);
        // Hors combat : valeurs de base, pas contre l'adversaire qu'on vient de battre.
        assertTrue(text.contains("Potion · soin plante · Commun : +10 PV"), text);
        assertTrue(text.contains("Deck 12 cartes"), text);
        type("s");
        assertEquals(-1, (int) assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseCardReward(state, options)));
    }

    @Test
    void pointsDeStatsApresUnBoss() throws IOException {
        open(80, 24);
        GameState state = GameState.newGame(cardistry.hero.HeroRegistry.LOUIS);
        cardistry.core.CombatEngine engine = new cardistry.core.CombatEngine(state, new cardistry.model.card.CardPool(),
                new cardistry.core.Rng(1), new EventBus());
        engine.start();
        engine.recoverAfterBoss();
        press(KeyType.ArrowDown);
        press(KeyType.ArrowDown);
        press(KeyType.ArrowDown);
        press(KeyType.Enter);
        assertEquals(cardistry.core.stats.StatType.DEFENSE,
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseStatPoint(state)));
        String text = dump("62b-points-de-stats");
        assertTrue(text.contains("Boss vaincu"), text);
        assertTrue(text.contains("2 points de stats à placer"), text);
        assertTrue(text.contains("FOR 3"), text);
        assertTrue(text.contains("DEF 1"), text);
        assertTrue(text.contains("+25 pour toute la partie"), text);
        type("6");
        assertEquals(cardistry.core.stats.StatType.LUCK,
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseStatPoint(state)));
    }

    @Test
    void siesteEtTri() throws IOException {
        open(80, 24);
        GameState state = deckState();
        press(KeyType.ArrowDown);
        press(KeyType.ArrowDown);
        press(KeyType.Enter);
        List<cardistry.run.RestOption> options = List.of(cardistry.run.RestOption.values());
        assertEquals(cardistry.run.RestOption.REMOVE_CARD,
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseRest(state, options)));
        String rest = dump("62-sieste");
        assertTrue(rest.contains("Sieste"), rest);
        assertTrue(rest.contains("Dormir : +30 % de tes PV max"));
        assertTrue(rest.contains("Faire le tri"));

        press(KeyType.ArrowRight);
        press(KeyType.Enter);
        assertEquals(1, (int) assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseCardToRemove(state)));
        String deck = dump("63-tri");
        assertTrue(deck.contains("Choisis la carte à retirer"), deck);
        assertTrue(deck.contains("Marécage"), deck);
    }

    @Test
    void resumeDuDeck() {
        List<Card> deck = List.of(new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON), new HealCard("Potion", Element.NATURE, 10, cardistry.items.Rarity.COMMON),
                new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON));
        assertEquals("Tsunami ×2, Potion", ItemScreens.deckSummary(deck));
    }

    // --- Shop ---

    @Test
    void marchandEtFumeeDansUneGrandeFenetre() throws IOException {
        openWithImages(100, 32);
        cardistry.core.CombatEngine engine = shopEngine(15, 0);
        type("p");
        assertTimeoutPreemptively(TIMEOUT,
                () -> ui.chooseShopAction(engine.state(), engine.shop().orElseThrow(), 75));
        cardistry.ui.window.ImageLayer.Placement merchant = placed("npc/marchand.idle.1.png");
        assertEquals(ShopScreen.MERCHANT_COLS, merchant.cols());
        assertEquals(ShopScreen.MERCHANT_ROWS, merchant.rows());
        long puffs = layer.marks().stream().filter(cardistry.ui.window.ImageLayer.Fill.class::isInstance).count();
        assertTrue(puffs > 0, "la fumée est dessinée");
    }

    @Test
    void petiteFenetreSansSpriteDeMarchandMaisAvecLeFumeurEnTexte() throws IOException {
        openWithImages(80, 24);
        cardistry.core.CombatEngine engine = shopEngine(15, 0);
        type("p");
        assertTimeoutPreemptively(TIMEOUT,
                () -> ui.chooseShopAction(engine.state(), engine.shop().orElseThrow(), 75));
        assertTrue(layer.snapshot().stream().noneMatch(i -> i.path().startsWith("npc/")));
        assertTrue(screen().contains("(-_-)y-"), screen());
    }

    @Test
    void laFumeeMonteSEffaceEtChangeAChaquePas() {
        for (int tick = 0; tick < 200; tick++) {
            List<ShopScreen.Puff> puffs = ShopScreen.smoke(tick);
            assertEquals(ShopScreen.SMOKE_PUFFS, puffs.size());
            for (ShopScreen.Puff puff : puffs) {
                assertTrue(puff.dy() <= 0, "au-dessus de la clope");
                assertTrue(puff.dy() >= -ShopScreen.SMOKE_LIFE * 2.5 - 1, "ne monte pas sans fin");
                assertTrue(puff.alpha() > 0 && puff.alpha() <= 0.75f);
                assertTrue(puff.size() >= 2);
            }
            assertFalse(puffs.equals(ShopScreen.smoke(tick + 1)), "animée");
        }
        assertEquals(ShopScreen.smoke(5), ShopScreen.smoke(5), "sans hasard");
    }

    /** Partie avec un shop ouvert et {@code choomies} Choomies ; {@code items} objets en inventaire. */
    private static cardistry.core.CombatEngine shopEngine(int choomies, int items) {
        GameState state = GameState.newGame();
        cardistry.core.CombatEngine engine = new cardistry.core.CombatEngine(state, new cardistry.model.card.CardPool(),
                new cardistry.core.Rng(3), new EventBus());
        engine.start();
        engine.gainChoomies(choomies - state.choomies());
        for (int i = 0; i < items; i++) {
            engine.give(mate());
        }
        engine.openShop();
        return engine;
    }

    @Test
    void shopAffichePrixEtServices() throws IOException {
        open(80, 24);
        cardistry.core.CombatEngine engine = shopEngine(15, 0);
        GameState state = engine.state();
        cardistry.core.Shop shop = engine.shop().orElseThrow();
        press(KeyType.ArrowDown); // vers les objets
        press(KeyType.Enter);
        cardistry.core.shop.ShopAction action = assertTimeoutPreemptively(TIMEOUT,
                () -> ui.chooseShopAction(state, shop, 75));
        assertEquals(cardistry.core.shop.ShopAction.Buy.of(cardistry.core.Shop.CARDS), action);
        String text = dump("70-shop");
        assertTrue(text.contains("Shop"), text);
        assertTrue(text.contains("15 Choomies"));
        assertTrue(text.contains("Entre, entre"));
        assertTrue(text.contains("Objets") && text.contains("Trinkets"));
        assertTrue(text.contains("[R] Reroll : 20"));
        assertTrue(text.contains("[T] Retirer une carte : 75"));
        // 15 Choomies : tout est trop cher, prix grisés.
        String[] lines = text.split("\n");
        int prices = 0;
        for (int row = 0; row < lines.length; row++) {
            int col = lines[row].indexOf(" Ch.");
            while (col > 0) {
                assertEquals(Palette.DIM, terminal.getCharacter(col - 1, row).getForegroundColor(), lines[row]);
                prices++;
                col = lines[row].indexOf(" Ch.", col + 1);
            }
        }
        assertEquals(9, prices);
    }

    @Test
    void shopPrixAbordablesEnCouleur() throws IOException {
        open(80, 24);
        cardistry.core.CombatEngine engine = shopEngine(1000, 0);
        type("p"); // l'écran du shop reste affiché après le départ
        assertEquals(new cardistry.core.shop.ShopAction.Leave(), assertTimeoutPreemptively(TIMEOUT,
                () -> ui.chooseShopAction(engine.state(), engine.shop().orElseThrow(), 75)));
        String text = screen();
        int row = 0;
        for (String line : text.split("\n")) {
            int col = line.indexOf(" Ch.");
            if (col > 0) {
                assertEquals(Palette.ACCENT, terminal.getCharacter(col - 1, row).getForegroundColor(), line);
            }
            row++;
        }
    }

    @Test
    void shopRerollEtRetrait() throws IOException {
        open(80, 24);
        cardistry.core.CombatEngine engine = shopEngine(500, 0);
        GameState state = engine.state();
        cardistry.core.Shop shop = engine.shop().orElseThrow();
        type("r");
        assertEquals(new cardistry.core.shop.ShopAction.Reroll(),
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseShopAction(state, shop, 75)));
        type("t");
        press(KeyType.ArrowRight);
        press(KeyType.Enter);
        assertEquals(new cardistry.core.shop.ShopAction.RemoveCard(1),
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseShopAction(state, shop, 75)));
    }

    @Test
    void shopInventairePleinEcranDeRemplacement() throws IOException {
        open(80, 24);
        cardistry.core.CombatEngine engine = shopEngine(1000, 3);
        GameState state = engine.state();
        cardistry.core.Shop shop = engine.shop().orElseThrow();
        press(KeyType.ArrowDown); // premier objet
        press(KeyType.Enter); // inventaire plein : écran de remplacement (rien de présélectionné)
        press(KeyType.Enter); // sans choix : rien
        type("2");
        press(KeyType.Enter);
        assertEquals(new cardistry.core.shop.ShopAction.Buy(cardistry.core.Shop.CARDS, 1),
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseShopAction(state, shop, 75)));
        String text = dump("71-shop-remplacer");
        assertTrue(text.contains("Que remplace"), text);
    }

    // --- Images (fenêtre) ---

    private cardistry.ui.window.ImageLayer layer;

    private void openWithImages(int cols, int rows) throws IOException {
        terminal = new DefaultVirtualTerminal(new TerminalSize(cols, rows));
        layer = new cardistry.ui.window.ImageLayer();
        ui = new LanternaUi(terminal, true, false, layer);
    }

    private static Enemy withImage() {
        return Enemy.builder("test-image", "Imagé", Element.FIRE, cardistry.model.enemy.EnemyRank.COMMON).hp(20)
                .behavior(cardistry.model.enemy.EnemyBehavior.always(cardistry.model.enemy.EnemyMove.basicAttack(5)))
                .build();
    }

    @Test
    void imageDeLAdversaireALaPlaceDuSpriteAscii() throws IOException {
        openWithImages(80, 24);
        ui.showStatus(combat(List.of(), withImage()));
        cardistry.ui.window.ImageLayer.Placement image = placed("enemies/test-image.idle.1.png");
        CombatWindowScreen.Layout l = CombatWindowScreen.Layout.of(80, 24);
        assertEquals(80 - 3 - 14, image.col(), "à droite");
        assertEquals(14, image.cols());
        assertEquals(7, image.rows());
        assertEquals(l.spriteTop(), image.row());
        assertFalse(screen().contains("? ?"), "pas de sprite ASCII (inconnu) dessous");
        cardistry.ui.window.ImageLayer.Placement player = placed(CombatWindowScreen.PLAYER_IMAGE);
        assertEquals(3, player.col(), "le joueur à gauche, en face");
        assertEquals(image.row(), player.row());
        assertEquals(image.cols(), player.cols());
    }

    @Test
    void framesDAnimationEnImages() {
        // Images de test : idle ×2, attack ×1, pas de hurt ni de death.
        assertEquals(2, cardistry.ui.window.Images.frames("test-image", "idle"));
        assertEquals(1, cardistry.ui.window.Images.frames("test-image", "attack"));
        assertEquals(0, cardistry.ui.window.Images.frames("test-image", "death"));
        assertEquals("enemies/test-image.attack.1.png",
                cardistry.ui.window.Images.enemy("test-image", "attack", 0).orElseThrow());
        assertEquals("enemies/test-image.attack.1.png",
                cardistry.ui.window.Images.enemy("test-image", "attack", 3).orElseThrow(),
                "au-delà de la dernière frame : on reste sur la dernière, pas de retour à l'image de base");
        assertEquals("enemies/test-image.png", cardistry.ui.window.Images.enemy("test-image", "hurt", 0).orElseThrow(),
                "animation absente : image de base");
        // Les vrais sprites ont les quatre animations (la mort se joue en images, pas en ASCII).
        for (String id : List.of("caddie-enerve", "seau-de-fuite", "came", "paresseux", "le-peneu", "t-rex-defonce",
                "etudiant-post-examen", "vafi")) {
            assertEquals(2, cardistry.ui.window.Images.frames(id, "idle"), id);
            assertEquals(2, cardistry.ui.window.Images.frames(id, "attack"), id);
            assertEquals(1, cardistry.ui.window.Images.frames(id, "hurt"), id);
            assertEquals(2, cardistry.ui.window.Images.frames(id, "death"), id);
        }
    }

    @Test
    void sansImageLeSpriteAscii() throws IOException {
        openWithImages(80, 24);
        ui.showStatus(combat(List.of(), hydra())); // pas d'image pour cet adversaire
        assertTrue(layer.snapshot().stream().noneMatch(p -> p.path().startsWith("enemies/")));
        assertTrue(screen().contains("? ?"), "sprite inconnu.txt");
    }

    @Test
    void sansCalqueJamaisDImage() throws IOException {
        open(80, 24); // terminal ou --no-images : pas de calque
        ui.showStatus(combat(List.of(), withImage()));
        assertTrue(screen().contains("? ?"));
    }

    @Test
    void carteEnPixelsDansLaFenetre() throws IOException {
        openWithImages(80, 24);
        cardistry.run.map.MapView view = mapView();
        ui.showMap(view);
        String text = screen();
        assertTrue(text.contains("Carte · Niveau 1/3"), text);
        assertTrue(text.contains("15 Choomies"));
        assertTrue(text.contains("→ Étage 1 : Combat — Un adversaire commun."), text);
        assertFalse(text.contains("Légende"), "plus de panneau latéral");
        assertTrue(text.contains("Sieste") && text.contains("Boss"), "légende sur une ligne");
        List<cardistry.ui.window.ImageLayer.Mark> marks = layer.marks();
        long icons = marks.stream().filter(m -> m instanceof cardistry.ui.window.ImageLayer.Stamp).count();
        assertEquals(view.map().nodes().size(), icons, "une icône par nœud");
        assertTrue(marks.stream().anyMatch(m -> m instanceof cardistry.ui.window.ImageLayer.Stamp s
                && s.path().equals("map/boss.png")));
        assertTrue(marks.stream().filter(m -> m instanceof cardistry.ui.window.ImageLayer.Fill).count() > 100,
                "chemins en pointillés");
        assertEquals(cardistry.run.map.NodeType.values().length, layer.snapshot().size(), "icônes de la légende");
    }

    @Test
    void carteEnPixelsEstompeCeQuiNEstPlusAtteignable() throws IOException {
        openWithImages(100, 32);
        Run run = new Run(4, java.util.Set.of(), new EventBus());
        run.start();
        cardistry.run.map.RunMap map = run.map().orElseThrow();
        cardistry.run.map.MapNode start = map.floor(1).get(0);
        cardistry.run.map.MapView view = new cardistry.run.map.MapView(run.state(), map, List.of(start), start,
                map.choicesAfter(start));
        ui.showMap(view);
        java.util.Set<cardistry.run.map.MapNode> reachable = MapScreen.reachable(view);
        assertTrue(reachable.contains(map.boss()));
        long faded = layer.marks().stream().filter(m -> m instanceof cardistry.ui.window.ImageLayer.Stamp s
                && s.alpha() < 0.3f).count();
        assertEquals(map.nodes().size() - reachable.size(), faded);
        assertTrue(faded > 0, "les autres départs ne sont plus atteignables");
    }

    private cardistry.ui.window.ImageLayer.Placement placed(String path) {
        return layer.snapshot().stream().filter(p -> p.path().equals(path)).findFirst()
                .orElseThrow(() -> new AssertionError("image absente : " + path + " dans " + layer.snapshot()));
    }

    private static java.awt.Color rgb(com.googlecode.lanterna.TextColor color) {
        return cardistry.ui.window.CellPalette.foreground(color);
    }

    // --- Combat dans la fenêtre ---

    @Test
    void combatFenetreTientEn80x24() {
        CombatWindowScreen.Layout l = CombatWindowScreen.Layout.of(80, 24);
        assertEquals(6, l.cardHeight());
        assertEquals(11, l.separator());
        assertEquals(7, l.spriteRows());
        assertEquals(22, l.lastActionRow());
        assertEquals(l.lastActionRow() - 1, l.barRow() + 1, "barre sur 2 lignes, puis la dernière action");
        assertEquals(l.separator() - 1, l.hpRow(), "PV sous le sprite, juste au-dessus de la main");
        assertEquals(3, l.spriteTop(), "une ligne d'intent au-dessus");
        assertTrue(l.statusWidth() >= 14, "états du joueur à côté de son sprite");
        // 64 px ×3 = 192 px : il faut 7 lignes de 28 px, sinon le sprite retombe à ×2.
        assertTrue(l.spriteRows() * 28 >= 64 * 3, "sprite ×3 en 80×24");
        CombatWindowScreen.Layout big = CombatWindowScreen.Layout.of(100, 32);
        assertEquals(6, big.cardHeight(), "en 100×32 la place va aux sprites");
        assertEquals(big.separator() - big.top() - 2, big.spriteRows(), "le sprite occupe toute la hauteur de l'arène");
        assertTrue(big.spriteRows() * 28 >= 64 * 6, "sprite ×6 en 100×32");
        assertTrue(big.statusWidth() >= 14);
        assertEquals(8, CombatWindowScreen.Layout.of(120, 40).cardHeight(), "grandes cartes sur un grand écran");
        assertEquals(big.separator() - 1, big.hpRow());
        assertTrue(CombatWindowScreen.HELP.length() <= 80 - 6 && CombatWindowScreen.HELP_UNDO.length() <= 80 - 6,
                "aide jamais tronquée en 80 colonnes");
        assertTrue(ShopScreen.HELP.length() <= 80 - 6);
    }

    @Test
    void nomsDeCartesSansCoupureDeMot() {
        assertEquals(List.of("Caïpiri…"), CombatWindowScreen.nameLines("Caïpirinha", 8));
        assertEquals(List.of("Caïpirinha"), CombatWindowScreen.nameLines("Caïpirinha", 10));
        assertEquals(List.of("Fouet de", "lianes"), CombatWindowScreen.nameLines("Fouet de lianes", 8));
        assertEquals(List.of("Tempête", "d'épines"), CombatWindowScreen.nameLines("Tempête d'épines", 8));
        assertEquals(List.of("Un nom", "bien tr…"), CombatWindowScreen.nameLines("Un nom bien trop long ici", 8));
        for (cardistry.model.card.Card card : new CardPool().cards()) {
            for (String line : CombatWindowScreen.nameLines(card.name(), 8)) {
                assertFalse(line.endsWith("-"), card.name());
                assertTrue(line.length() <= 8, line);
            }
        }
    }

    @Test
    void combatFenetreJoueurAGaucheAdversaireADroite() throws IOException {
        openWithImages(80, 24);
        GameState state = combat(List.of(new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON),
                new HealCard("Potion", Element.NATURE, 10, cardistry.items.Rarity.COMMON), new TerrainCard("Forêt", Element.NATURE, cardistry.items.Rarity.COMMON)), withImage());
        ui.showStatus(state);
        String text = dump("84-fenetre-combat-texte");
        List<String> lines = text.lines().toList();
        CombatWindowScreen.Layout l = CombatWindowScreen.Layout.of(80, 24);
        String info = lines.get(l.hpRow());
        assertTrue(lines.get(l.hpRow() - 1).contains("DEF 0 · RES 0"), "DEF et RES juste au-dessus de sa barre de PV");
        assertTrue(info.indexOf("TOI") == 3 && info.strip().endsWith("IMAGÉ │") || info.contains("IMAGÉ"), info);
        assertTrue(info.indexOf("100/100") < 40 && info.indexOf("20/20") > 40, "une barre de PV sous chaque sprite");
        assertTrue(text.contains("100/100") && text.contains("20/20"), text);
        assertTrue(text.contains("IMAGÉ · feu"), "élément à côté du nom");
        assertTrue(text.contains("Terrain : EPFL (feu)"));
        assertFalse(text.contains("Journal ─"), "journal fermé par défaut");
        assertTrue(text.contains("J journal"));
        assertTrue(lines.get(2).contains(cardistry.ui.IntentText.intent(state, state.currentEnemy())),
                "intent au-dessus de l'adversaire");
        assertEquals("ui/intent-attack.png", CombatWindowScreen.intentIcon(state, state.currentEnemy()));
        assertEquals(2, placed("ui/intent-attack.png").row());
    }

    @Test
    void cartesIllustreesAvecIconeDeTypeEtCouleurDElement() throws IOException {
        openWithImages(80, 24);
        // Cartes inconnues : pas de images/cards/<id>.png, donc l'illustration générique.
        ui.showStatus(combat(List.of(new SpellCard("Sort test", Element.WATER, 15, cardistry.items.Rarity.COMMON),
                new HealCard("Soin test", Element.NATURE, 10, cardistry.items.Rarity.COMMON), new TerrainCard("Terrain test", Element.NATURE, cardistry.items.Rarity.COMMON),
                new cardistry.model.card.DeadCard("Morte test"), new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON)),
                hydra()));
        List<cardistry.ui.window.ImageLayer.Placement> images = layer.snapshot();
        assertEquals(2, placed("cards/tsunami.png").rows(), "illustration de la carte quand elle existe");
        assertEquals(null, placed("cards/tsunami.png").tint());
        for (String type : List.of("spell", "heal", "terrain", "dead")) {
            List<cardistry.ui.window.ImageLayer.Placement> ofType = images.stream()
                    .filter(p -> p.path().equals("ui/type-" + type + ".png")).toList();
            assertEquals(type.equals("spell") ? 3 : 2, ofType.size(), type + " : illustration générique + icône de type");
            assertEquals(type.equals("spell") ? 2 : 1, ofType.stream().filter(p -> p.tint() == null && p.rows() == 1).count());
        }
        // Sans images/cards/<id>.png : l'icône de type en grand, à la couleur de l'élément.
        assertEquals(rgb(Palette.element(Element.WATER)), images.stream()
                .filter(p -> p.path().equals("ui/type-spell.png") && p.rows() == 2).findFirst().orElseThrow().tint());
        assertEquals(rgb(Palette.element(Element.NATURE)), images.stream()
                .filter(p -> p.path().equals("ui/type-heal.png") && p.rows() == 2).findFirst().orElseThrow().tint());
        String text = screen();
        assertTrue(text.contains("Tsunami") && text.contains("15") && text.contains("+10"), text);
        assertFalse(text.contains("-\n"), "pas de mot coupé par un tiret");
        assertEquals("", CombatWindowScreen.value(new TerrainCard("Forêt", Element.NATURE, cardistry.items.Rarity.COMMON)));
        assertEquals("ui/type-dead.png", CombatWindowScreen.typeIcon(new cardistry.model.card.DeadCard()));
    }

    @Test
    void barreDIconesAuxCouleursDeRarete() throws IOException {
        openWithImages(100, 32);
        Trinket rare = ItemRegistry.trinkets().stream().filter(t -> t.rarity() == Rarity.RARE).findFirst().orElseThrow();
        Trinket legendary = ItemRegistry.trinkets().stream().filter(t -> t.rarity() == Rarity.LEGENDARY).findFirst()
                .orElseThrow();
        GameState state = withInventory(List.of(mate()), List.of(rare, legendary));
        ui.showStatus(state);
        List<cardistry.ui.window.ImageLayer.Mark> marks = layer.marks();
        java.util.Set<java.awt.Color> frames = new java.util.HashSet<>();
        for (cardistry.ui.window.ImageLayer.Mark mark : marks) {
            if (mark instanceof cardistry.ui.window.ImageLayer.Fill fill) {
                frames.add(fill.color());
            }
        }
        assertTrue(frames.contains(rgb(Palette.rarity(mate().rarity()))), "cadre de l'objet");
        assertTrue(frames.contains(rgb(Palette.rarity(Rarity.RARE))), "cadre rare");
        assertTrue(frames.contains(rgb(Palette.rarity(Rarity.LEGENDARY))), "cadre légendaire");
        List<String> icons = marks.stream().filter(m -> m instanceof cardistry.ui.window.ImageLayer.Stamp)
                .map(m -> ((cardistry.ui.window.ImageLayer.Stamp) m).path()).toList();
        assertEquals(List.of("items/mate.png", "items/" + rare.id() + ".png", "items/" + legendary.id() + ".png"),
                icons, "une icône par objet et par trinket");
        String text = screen();
        assertTrue(text.contains("Maté") && text.contains("[W]"), text);
        assertFalse(text.contains(rare.name()), "trinkets en icônes seules");
        assertTrue(text.contains("5/20 clopes") && text.contains("F fumer · G rouler"));
        assertEquals("ui/pack.png", placed("ui/pack.png").path());
    }

    @Test
    void couleursDeRarete() {
        assertEquals(com.googlecode.lanterna.TextColor.ANSI.WHITE_BRIGHT, Palette.rarity(Rarity.COMMON));
        assertEquals(com.googlecode.lanterna.TextColor.ANSI.GREEN_BRIGHT, Palette.rarity(Rarity.UNCOMMON));
        assertEquals(com.googlecode.lanterna.TextColor.ANSI.BLUE_BRIGHT, Palette.rarity(Rarity.RARE));
        assertEquals(com.googlecode.lanterna.TextColor.ANSI.YELLOW_BRIGHT, Palette.rarity(Rarity.LEGENDARY));
        assertEquals(com.googlecode.lanterna.TextColor.ANSI.MAGENTA_BRIGHT, Palette.rarity(Rarity.SECRET));
    }

    @Test
    void jaugeDeTrinketACoteDesClopes() {
        cardistry.items.Gauge gauge = new cardistry.items.Gauge("Honoré", 5);
        gauge.add(3);
        assertEquals("Honoré [###..]", CombatWindowScreen.gaugeText(gauge));
    }

    @Test
    void journalFermePuisOuvertAvecJ() throws IOException {
        openWithImages(80, 24);
        GameState state = combat(List.of(new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON)), withImage());
        ui.onEvent(new GameEvent.TurnStarted(1));
        ui.onEvent(new GameEvent.CardPlayed(state.hand().get(0)));
        type("j");
        type("p");
        assertEquals(new Action.DrawCard(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        String open = dump("85-fenetre-journal");
        assertTrue(open.contains("Journal") && open.contains("J fermer"), open);
        assertTrue(open.contains("— Tour 1 —"), "tout le journal, pas seulement la dernière ligne");
        assertTrue(layer.snapshot().stream().noneMatch(p -> p.path().startsWith("enemies/")
                || p.path().startsWith("player/")), "aucune image sous le panneau");
        type("j");
        type("p");
        assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state));
        String closed = screen();
        assertFalse(closed.contains("— Tour 1 —"));
        assertTrue(closed.contains("› Tu joues la carte Tsunami"), "dernière action sur une ligne : " + closed);
        assertTrue(closed.contains("J journal"));
        assertEquals("enemies/test-image.idle.1.png", placed("enemies/test-image.idle.1.png").path());
    }

    @Test
    void journalToucheJSansEffetEnTerminal() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new SpellCard("Tsunami", Element.WATER, 15, cardistry.items.Rarity.COMMON)), hydra());
        type("j");
        type("p");
        assertEquals(new Action.DrawCard(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        assertTrue(screen().contains("Journal"), "le terminal garde son journal toujours visible");
    }

    /** Dessine avec un Painter muni d'un calque, et renvoie les images posées. */
    private static List<cardistry.ui.window.ImageLayer.Placement> drawn(java.util.function.Consumer<Painter> draw) {
        DefaultVirtualTerminal t = new DefaultVirtualTerminal(new TerminalSize(80, 30));
        cardistry.ui.window.ImageLayer images = new cardistry.ui.window.ImageLayer();
        images.begin();
        draw.accept(new Painter(t.newTextGraphics(), new TerminalSize(80, 30), false, images));
        images.commit();
        return images.snapshot();
    }

    @Test
    void iconesDObjets() {
        GameState state = withInventory(List.of(mate()), List.of());
        List<cardistry.ui.window.ImageLayer.Placement> inventory = drawn(p -> ItemScreens.inventory(p, state));
        assertEquals(1, inventory.size());
        assertEquals("items/mate.png", inventory.get(0).path());
        assertEquals(Painter.ICON_WIDTH, inventory.get(0).cols());
        assertEquals(1, inventory.get(0).rows());
        assertTrue(drawn(p -> ItemScreens.inventory(p, withInventory(List.of(), List.of()))).isEmpty());
        List<cardistry.ui.window.ImageLayer.Placement> trinkets = drawn(p -> ItemScreens.inventory(p,
                withInventory(List.of(), List.of(ItemRegistry.trinket("pax"), sansImage()))));
        assertEquals("items/pax.png", trinkets.get(0).path());
        assertEquals("ui/trinket.png", trinkets.get(1).path(), "sans image : le placeholder de la catégorie");
        assertEquals("ui/item.png", CombatWindowScreen.icon(new Item("sans-image", "Sans image", "rien", "",
                Rarity.COMMON, java.util.Set.of(), ctx -> { })));
    }

    /** Trinket sans icône dans images/items/. */
    private static Trinket sansImage() {
        return Trinket.builder("sans-image", "Sans image", Rarity.COMMON).description("rien").build();
    }

    @Test
    void alienDeSassyAvecSaBarreDeVieEtSaCible() throws IOException {
        openWithImages(80, 24);
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)),
                hydra()).withHero(cardistry.hero.HeroRegistry.SASSY);
        cardistry.model.Familiar alien = state.familiar().orElseThrow();
        alien.setTargeted(true);
        ui.showStatus(state);
        String text = dump("86-fenetre-alien");
        CombatWindowScreen.Layout l = CombatWindowScreen.Layout.of(80, 24);
        List<String> lines = text.lines().toList();
        assertTrue(lines.get(l.hpRow() - l.familiarRows() - 1).contains("20/20"), "barre de vie au-dessus de l'alien");
        assertTrue(placed("player/alien.idle.1.png").rows() == l.familiarRows(), "emplacement dédié du familier");
        assertTrue(text.contains("Vise l'alien · Attaque"), text);
        assertTrue(text.contains("visé !"), text);
        alien.takeDamage(20);
        alien.setTargeted(false);
        ui.showStatus(state);
        assertTrue(placed("player/alien.ko.1.png") != null, "assommé : sprite KO");
        assertTrue(screen().contains("KO"), screen());
    }

    @Test
    void alienEnTerminal() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)),
                hydra()).withHero(cardistry.hero.HeroRegistry.SASSY);
        ui.showStatus(state);
        String text = dump("87-terminal-alien");
        assertTrue(text.contains("ALIEN 20/20"), text);
    }

    @Test
    void imageDuPersonnageSelonSonAnimation() {
        GameState state = GameState.newGame(cardistry.hero.HeroRegistry.LOUIS);
        ViewState view = new ViewState();
        assertEquals("player/louis.idle.1.png", CombatWindowScreen.playerImage(state, view));
        view.idleFrame = 3;
        assertEquals("player/louis.idle.2.png", CombatWindowScreen.playerImage(state, view), "l'idle boucle");
        view.playerAnimation = "attack";
        view.playerFrame = 1;
        assertEquals("player/louis.attack.2.png", CombatWindowScreen.playerImage(state, view));
        view.playerFrame = 5;
        assertEquals("player/louis.attack.2.png", CombatWindowScreen.playerImage(state, view), "reste sur la fin");
        view.playerAnimation = "hurt";
        view.playerFrame = 0;
        assertEquals("player/louis.hurt.1.png", CombatWindowScreen.playerImage(state, view));
        assertEquals(CombatWindowScreen.PLAYER_IMAGE, CombatWindowScreen.playerImage(GameState.newGame(), view),
                "sans personnage : le placeholder commun");
    }

    @Test
    void chaqueSpriteAUnFichierParAnimation() {
        for (String id : cardistry.model.enemy.EnemyRegistry.ids()) {
            String sprite = cardistry.model.enemy.EnemyRegistry.create(id, 1).spriteId();
            for (String animation : List.of("idle", "attack", "hurt", "death")) {
                assertTrue(cardistry.ui.window.Images.frames(sprite, animation) >= 1,
                        "adversaire " + sprite + " sans animation " + animation);
            }
        }
        for (cardistry.hero.Hero hero : cardistry.hero.HeroRegistry.all()) {
            for (String animation : List.of("idle", "attack", "hurt", "death")) {
                String path = "player/" + hero.id() + "." + animation + ".1.png";
                assertEquals(64, cardistry.ui.window.Images.get(path)
                        .orElseThrow(() -> new AssertionError("personnage sans image : " + path)).getWidth());
            }
        }
        for (String animation : List.of("idle", "attack", "smoke", "ko")) {
            String path = "player/alien." + animation + ".1.png";
            assertTrue(cardistry.ui.window.Images.get(path).isPresent(), "alien sans image : " + path);
        }
    }

    @Test
    void toutLeContenuAUneIcone() {
        for (Item item : ItemRegistry.items()) {
            assertTrue(cardistry.ui.window.Images.item(item.id()).isPresent(), "objet sans icône : " + item.id());
        }
        for (Trinket trinket : ItemRegistry.trinkets()) {
            assertTrue(cardistry.ui.window.Images.item(trinket.id()).isPresent(), "trinket sans icône : " + trinket.id());
        }
        List<String> cards = new java.util.ArrayList<>(new CardPool().cards().stream()
                .map(c -> cardistry.model.Ids.slug(c.name())).distinct().toList());
        cards.add(cardistry.model.Ids.slug(cardistry.model.card.DeadCard.NAME));
        cards.add("exercice-3b");
        for (String id : cards) {
            java.awt.image.BufferedImage image = cardistry.ui.window.Images.get("cards/" + id + ".png")
                    .orElseThrow(() -> new AssertionError("carte sans illustration : " + id));
            assertEquals(24, image.getWidth());
            assertEquals(24, image.getHeight());
        }
        for (cardistry.run.map.NodeType type : cardistry.run.map.NodeType.values()) {
            assertTrue(cardistry.ui.window.Images.mapNode(type).isPresent(), "nœud sans icône : " + type);
        }
        assertEquals(16, cardistry.ui.window.Images.get("items/mate.png").orElseThrow().getWidth());
        for (cardistry.core.status.StatusType type : cardistry.core.status.StatusType.values()) {
            java.awt.image.BufferedImage image = cardistry.ui.window.Images.get("status/" + type.id() + ".png")
                    .orElseThrow(() -> new AssertionError("statut sans icône : " + type.id()));
            assertEquals(8, image.getWidth());
            assertEquals(8, image.getHeight());
        }
        for (String id : cardistry.model.enemy.EnemyRegistry.ids()) {
            String sprite = cardistry.model.enemy.EnemyRegistry.create(id, 1).spriteId();
            java.awt.image.BufferedImage image = cardistry.ui.window.Images.enemy(sprite, "idle", 0)
                    .flatMap(cardistry.ui.window.Images::get)
                    .orElseThrow(() -> new AssertionError("adversaire sans sprite : " + id));
            assertEquals(64, image.getWidth(), sprite);
            assertEquals(64, image.getHeight(), sprite);
        }
    }

    /** Marques posées par un dessin (cadres de rareté, icônes encadrées). */
    private static List<cardistry.ui.window.ImageLayer.Mark> marked(java.util.function.Consumer<Painter> draw) {
        DefaultVirtualTerminal t = new DefaultVirtualTerminal(new TerminalSize(80, 30));
        cardistry.ui.window.ImageLayer images = new cardistry.ui.window.ImageLayer();
        images.begin();
        draw.accept(new Painter(t.newTextGraphics(), new TerminalSize(80, 30), false, images));
        images.commit();
        return images.marks();
    }

    private static boolean framed(List<cardistry.ui.window.ImageLayer.Mark> marks, Rarity rarity) {
        return marks.stream().anyMatch(m -> m instanceof cardistry.ui.window.ImageLayer.Fill f
                && f.color().equals(rgb(Palette.rarity(rarity))));
    }

    @Test
    void memeCodeCouleurDeRareteDansLesRecompensesEtLeShop() {
        GameState state = withInventory(List.of(mate()), List.of());
        Trinket rare = ItemRegistry.trinkets().stream().filter(t -> t.rarity() == Rarity.RARE).findFirst().orElseThrow();
        List<cardistry.ui.window.ImageLayer.Mark> reward = marked(p -> ItemScreens.reward(p, state,
                List.of(mate(), rare), 0, 1));
        assertTrue(framed(reward, mate().rarity()) && framed(reward, Rarity.RARE), "cadres de rareté");
        assertEquals(List.of("items/mate.png", "items/" + rare.id() + ".png"), reward.stream()
                .filter(m -> m instanceof cardistry.ui.window.ImageLayer.Stamp)
                .map(m -> ((cardistry.ui.window.ImageLayer.Stamp) m).path()).toList());

        cardistry.core.CombatEngine engine = shopEngine(500, 0);
        cardistry.core.Shop shop = engine.shop().orElseThrow();
        List<Rarity> onSale = shop.offers().stream()
                .filter(o -> o instanceof cardistry.core.shop.ShopOffer.CollectibleOffer)
                .map(o -> ((cardistry.core.shop.ShopOffer.CollectibleOffer) o).collectible().rarity()).toList();
        List<cardistry.ui.window.ImageLayer.Mark> shopMarks = marked(p -> ShopScreen.draw(p, engine.state(), shop, 0,
                75, 1, null));
        for (Rarity rarity : onSale) {
            assertTrue(framed(shopMarks, rarity), "cadre " + rarity + " au shop");
        }
        assertEquals(onSale.size(), shopMarks.stream()
                .filter(m -> m instanceof cardistry.ui.window.ImageLayer.Stamp).count(), "une icône par article");
        List<cardistry.ui.window.ImageLayer.Placement> shopImages = drawn(p -> ShopScreen.draw(p, engine.state(),
                shop, 0, 75, 1, null));
        assertEquals(2 * cardistry.core.Shop.CARDS, shopImages.size(), "illustration + icône de type par carte");
        assertEquals(cardistry.core.Shop.CARDS, shopImages.stream().filter(i -> i.path().startsWith("cards/")).count(),
                "une illustration par carte");
    }

    @Test
    void cartesIllustreesDansLesChoixDeCartes() {
        GameState state = withInventory(List.of(), List.of());
        List<cardistry.ui.window.ImageLayer.Placement> images = drawn(p -> ItemScreens.cardReward(p, state,
                List.of(new HealCard("Potion", Element.NATURE, 10, cardistry.items.Rarity.COMMON), new TerrainCard("Terrain test", Element.NATURE, cardistry.items.Rarity.COMMON)),
                0, 1));
        assertEquals(1, images.stream().filter(i -> i.path().equals("cards/potion.png")).count());
        assertEquals(1, images.stream().filter(i -> i.path().equals("ui/type-heal.png")).count());
        assertEquals(2, images.stream().filter(i -> i.path().equals("ui/type-terrain.png")).count(),
                "sans illustration : l'icône de type en grand");
    }

    // --- Événements ---

    @Test
    void ecranDEvenementEtResultat() throws IOException {
        open(80, 24);
        GameState state = GameState.newGame(); // 15 Choomies : le paquet de la machine est trop cher
        cardistry.story.Story machine = cardistry.story.StoryRegistry.byId("machine-a-clopes").orElseThrow();
        type("1"); // indisponible : ignoré
        press(KeyType.ArrowDown);
        press(KeyType.Enter);
        assertEquals(1, (int) assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseStory(state, machine)));
        String text = dump("90-evenement");
        assertTrue(text.contains("Machine à clopes"), text);
        assertTrue(text.contains("[1] Acheter un paquet"));
        assertTrue(text.contains("(il te faut 20 Choomies)"));
        assertTrue(text.contains("[2] Taper dessus"));
        assertTrue(text.contains("[3] Partir"));

        press(KeyType.Enter);
        assertTimeoutPreemptively(TIMEOUT, () -> ui.showStoryResult(state,
                new GameEvent.StoryResolved("Machine à clopes", "Taper dessus", List.of("Deux clopes tombent ! (+2)"))));
        String result = dump("91-evenement-resultat");
        assertTrue(result.contains("Tu choisis : Taper dessus"), result);
        assertTrue(result.contains("Deux clopes tombent !"));
    }

    // --- Menu pause, options, sons d'interface ---

    @Test
    void titreOptionsPuisJouer() throws IOException {
        open(80, 24);
        cardistry.ui.OptionsController settings = cardistry.ui.OptionsController.inMemory(cardistry.ui.Settings.defaults());
        ui.setOptions(settings);
        press(KeyType.ArrowRight);
        press(KeyType.ArrowRight); // Options
        press(KeyType.Enter);
        press(KeyType.ArrowLeft); // musique 6 → 5
        press(KeyType.ArrowDown);
        press(KeyType.ArrowRight); // effets 8 → 9
        press(KeyType.ArrowDown);
        press(KeyType.Enter); // muet
        press(KeyType.Escape); // retour au titre
        press(KeyType.ArrowLeft);
        press(KeyType.ArrowLeft); // Jouer
        press(KeyType.Enter);
        assertFalse(assertTimeoutPreemptively(TIMEOUT, () -> ui.title(1)).showRules());
        assertEquals(new cardistry.ui.Settings(5, 9, true), settings.settings());
    }

    @Test
    void ecranOptions() throws IOException {
        open(80, 24);
        ui.setOptions(cardistry.ui.OptionsController.inMemory(new cardistry.ui.Settings(6, 8, false)));
        press(KeyType.ArrowDown);
        press(KeyType.ArrowDown);
        press(KeyType.Enter); // Options
        // Capture pendant l'écran Options : Échap (retour au titre) puis Échap (quitter le jeu).
        press(KeyType.Escape);
        press(KeyType.Escape);
        assertThrows(cardistry.ui.QuitGameException.class,
                () -> assertTimeoutPreemptively(TIMEOUT, () -> ui.title(1)));
        String text = MenuScreensProbe.options(new cardistry.ui.Settings(6, 8, false), 0);
        assertTrue(text.contains("Musique  ◀ ██████░░░░ ▶ 6/10"), text);
        assertTrue(text.contains("Effets   ◀ ████████░░ ▶ 8/10"));
        assertTrue(text.contains("Muet     [ ] non"));
        assertTrue(text.contains("Retour"));
    }

    /** Rendu d'un écran de menu dans un terminal virtuel, en texte. */
    static final class MenuScreensProbe {
        static String options(cardistry.ui.Settings settings, int selected) {
            DefaultVirtualTerminal t = new DefaultVirtualTerminal(new TerminalSize(80, 24));
            MenuScreens.options(new Painter(t.newTextGraphics(), new TerminalSize(80, 24)), settings, selected, 1);
            StringBuilder text = new StringBuilder();
            for (int row = 0; row < 24; row++) {
                for (int col = 0; col < 80; col++) {
                    text.append(t.getCharacter(col, row).getCharacterString());
                }
                text.append('\n');
            }
            return text.toString();
        }
    }

    @Test
    void echapOuvreLaPauseEnCombatPuisReprendre() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(new SpellCard("Jet d'eau", Element.WATER, 10, cardistry.items.Rarity.COMMON)), hydra());
        press(KeyType.Escape); // pause
        press(KeyType.Enter); // Reprendre
        type("p");
        assertEquals(new Action.DrawCard(), assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
    }

    @Test
    void pauseOptionsPuisQuitter() throws IOException {
        open(80, 24);
        GameState state = combat(List.of(), hydra());
        cardistry.ui.OptionsController settings = cardistry.ui.OptionsController.inMemory(cardistry.ui.Settings.defaults());
        ui.setOptions(settings);
        press(KeyType.Escape); // pause
        press(KeyType.ArrowDown);
        press(KeyType.Enter); // Options
        press(KeyType.ArrowRight); // musique 7
        press(KeyType.Escape); // retour à la pause
        press(KeyType.ArrowDown);
        press(KeyType.Enter); // Quitter
        type("o"); // confirmer
        assertThrows(cardistry.ui.LeaveGameException.class,
                () -> assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state)));
        assertEquals(7, settings.settings().music());
    }

    @Test
    void echapAnnuleDAbordDansLesSousEcrans() throws IOException {
        open(80, 24);
        GameState state = deckState();
        // « Faire le tri » : Échap annule (−1), sans ouvrir la pause.
        press(KeyType.Escape);
        assertEquals(-1, (int) assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseCardToRemove(state)));
        // Remplacement d'objet : Échap revient à la récompense, puis S refuse.
        GameState full = withInventory(List.of(mate(), mate(), mate()), List.of());
        press(KeyType.Enter); // inventaire plein → remplacement
        press(KeyType.Escape); // retour à la récompense (pas la pause)
        type("s");
        assertEquals(new RewardChoice.Skip(),
                assertTimeoutPreemptively(TIMEOUT, () -> ui.chooseReward(full, List.of(mate()))));
    }

    @Test
    void sonsDesTouches() throws IOException {
        open(80, 24);
        List<cardistry.ui.audio.UiSound> played = new java.util.ArrayList<>();
        ui.setSounds(played::add);
        GameState state = combat(List.of(new SpellCard("A", Element.WATER, 10, cardistry.items.Rarity.COMMON), new SpellCard("B", Element.WATER, 10, cardistry.items.Rarity.COMMON)),
                hydra());
        press(KeyType.ArrowRight);
        press(KeyType.Escape);
        press(KeyType.Escape);
        press(KeyType.Enter);
        assertTimeoutPreemptively(TIMEOUT, () -> ui.nextAction(state));
        assertEquals(List.of(cardistry.ui.audio.UiSound.CURSOR, cardistry.ui.audio.UiSound.BACK,
                cardistry.ui.audio.UiSound.BACK, cardistry.ui.audio.UiSound.CONFIRM), played);
    }

    // --- Console de développement ---

    @Test
    void consoleDeDevF12AfficheLeJournalEtExecute() throws Exception {
        cardistry.utils.Log.clearMemory();
        open(80, 24);
        Run run = new Run(5, java.util.Set.of(), new EventBus());
        run.start();
        ui.enableDevConsole();
        ui.attachDevRun(run);
        press(KeyType.F12);
        type("seed");
        press(KeyType.Enter);
        type("vole");
        press(KeyType.Enter);
        type("hp 7");
        press(KeyType.Enter);
        java.util.concurrent.atomic.AtomicReference<cardistry.ui.TitleChoice> choice =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread title = new Thread(() -> choice.set(ui.title(42)));
        title.setDaemon(true);
        title.start();
        long end = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (!screen().contains("PV du joueur : 7") && System.currentTimeMillis() < end) {
            Thread.sleep(20);
        }
        String text = dump("90-console-dev");
        assertTrue(text.contains("CONSOLE DE DEV"), text);
        assertTrue(text.contains("> seed") && text.contains("Seed : 5"), text);
        assertTrue(text.contains("Commande inconnue : vole"), text);
        assertTrue(text.contains("> _"), text);
        assertTrue(text.contains("Seed 42"), text); // l'écran du dessous reste visible en haut
        assertEquals(7, run.state().player().hp());

        press(KeyType.Escape); // ferme la console : les touches reviennent au titre
        press(KeyType.Enter);
        title.join(TIMEOUT.toMillis());
        assertFalse(title.isAlive());
        assertFalse(choice.get().showRules());
        assertFalse(screen().contains("CONSOLE DE DEV"));
        cardistry.utils.Log.clearMemory();
    }

    @Test
    void sansDebugF12NeFaitRien() throws IOException {
        open(80, 24);
        press(KeyType.F12);
        press(KeyType.Enter);
        assertFalse(assertTimeoutPreemptively(TIMEOUT, () -> ui.title(42)).showRules());
        assertFalse(screen().contains("CONSOLE DE DEV"));
    }
}
