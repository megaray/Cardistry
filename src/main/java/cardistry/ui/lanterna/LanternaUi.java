package cardistry.ui.lanterna;

import static cardistry.ui.Format.mult;
import static cardistry.ui.Format.num;

import cardistry.core.Action;
import cardistry.core.GameState;
import cardistry.core.RewardChoice;
import cardistry.core.event.GameEvent;
import cardistry.items.Collectible;
import cardistry.model.Enemy;
import cardistry.run.Secret;
import cardistry.ui.ItemKeys;
import cardistry.ui.Input;
import cardistry.ui.QuitGameException;
import cardistry.ui.Renderer;
import cardistry.ui.TitleChoice;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorAutoCloseTrigger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import cardistry.utils.Log;

/** Interface plein écran (Lanterna) : affichage, animations et clavier. */
public final class LanternaUi implements Renderer, Input, AutoCloseable {

    private enum Page { TITLE, RULES, COMBAT, INVENTORY, REWARD, REPLACE, DISCARD, CARD_CHOICE, GAME_OVER, SPRITES, MAP, REST, CARD_REWARD, DECK_REMOVE, SHOP, STORY, STORY_RESULT,
        PAUSE, OPTIONS, HERO, DECK, CARD_DETAIL, STAT_POINTS, STATS, ALTAR }

    /** Pages où D n'ouvre pas le deck (pas encore de partie, menus, visionneuse). */
    private static final java.util.Set<Page> NO_DECK_PAGES = java.util.EnumSet.of(Page.TITLE, Page.HERO, Page.RULES,
            Page.SPRITES, Page.PAUSE, Page.OPTIONS, Page.DECK);

    private static final int FRAME_MS = 45;
    private static final int POLL_MS = 15;

    private final Screen screen;
    private final boolean fast;
    private final boolean ascii;
    private final ViewState view = new ViewState();
    private final cardistry.ui.window.ImageLayer images;
    private cardistry.ui.window.CellWindow window;

    private Page page = Page.TITLE;
    private GameState state;
    /** Console de développement (F12) ; {@code null} sans {@code --debug}. */
    private DevOverlay dev;
    private long seed;
    private List<Collectible> rewardOptions = List.of();
    private int rewardSelected = 0;
    private int replaceSelected = 0;
    private int discardSelected = 0;
    private List<cardistry.model.card.Card> cardOptions = List.of();
    private int cardSelected = 0;
    private boolean metaPhraseShown = false;
    private boolean showMetaPhrase = false;
    private final List<Secret> titleCodes = new java.util.ArrayList<>();
    private List<String> unlockedSecrets = List.of();
    private cardistry.run.map.MapView mapView;
    private List<cardistry.run.RestOption> restOptions = List.of();
    private int restSelected = 0;
    private int mapSelected = 0;


    /**ALTAR */
    private int altarStat=0;
    private int altarButton=1;
    /** Ouvre le terminal courant en plein écran. */
    public static LanternaUi open(boolean fast, boolean ascii) throws IOException {
        Terminal terminal = new DefaultTerminalFactory(System.out, System.in, StandardCharsets.UTF_8)
                .setForceTextTerminal(true)
                .createTerminal();
        return new LanternaUi(terminal, fast, ascii);
    }

    /**
     * Ouvre le jeu dans sa propre fenêtre ({@link cardistry.ui.window.CellWindow}) : grille dessinée
     * par le jeu, police pixel embarquée, images PNG, filtre CRT. Fermer la fenêtre quitte le jeu.
     *
     * @param crt filtre CRT activé au départ (F10 le bascule)
     * @param showImages {@code false} : sprites ASCII même si une image existe ({@code --no-images})
     */
    public static LanternaUi openWindow(boolean fast, boolean ascii, boolean crt, boolean showImages) {
        cardistry.ui.window.CellWindow window = cardistry.ui.window.CellWindow.open("Cardistry", crt);
        try {
            LanternaUi ui = new LanternaUi(window.terminal(), fast, ascii, showImages ? window.images() : null);
            ui.window = window;
            return ui;
        } catch (IOException e) {
            window.close();
            throw new UncheckedIOException(e);
        }
    }

    /** @param fast si {@code true}, les animations ne font aucune pause */
    public LanternaUi(Terminal terminal, boolean fast) throws IOException {
        this(terminal, fast, false);
    }

    /**
     * @param fast si {@code true}, les animations ne font aucune pause
     * @param ascii si {@code true}, affichage en ASCII pur (cadres, barres, flèches, accents)
     */
    public LanternaUi(Terminal terminal, boolean fast, boolean ascii) throws IOException {
        this(terminal, fast, ascii, null);
    }

    /** @param images calque d'images (fenêtre) ; {@code null} = pas d'images */
    public LanternaUi(Terminal terminal, boolean fast, boolean ascii, cardistry.ui.window.ImageLayer images)
            throws IOException {
        this.images = images;
        this.screen = new TerminalScreen(terminal);
        this.fast = fast;
        this.ascii = ascii;
        screen.startScreen();
        screen.setCursorPosition(null);
    }

    @Override
    public void close() throws IOException {
        screen.stopScreen();
        if (window != null) {
            window.close();
        }
    }

    /**
     * Prépare l'interface pour une nouvelle partie (après un abandon ou une fin de partie) : journal,
     * notifications, sélections et animations de la partie précédente sont oubliés.
     */
    public void backToTitle() {
        view.log.clear();
        view.toast = null;
        view.selected = 0;
        view.confirmingQuit = false;
        view.journalOpen = false;
        view.journalScroll = 0;
        view.resetEffects();
        mapSelected = 0;
        rewardSelected = 0;
        replaceSelected = 0;
        discardSelected = 0;
        cardSelected = 0;
        restSelected = 0;
        shopSelected = 0;
        heroSelected = 0;
        unlockedSecrets = List.of();
        state = null;
        page = Page.TITLE;
    }

    // ===================== Renderer =====================

    @Override
    public void onEvent(GameEvent event) {
        switch (event) {
            case GameEvent.GameStarted e -> seed = e.seed();
            case GameEvent.TurnStarted e -> {
                view.log("— Tour " + e.turn() + " —", Palette.DIM);
                view.toast = null;
            }
            case GameEvent.PlayerTurnStarted e -> {
                // Rien de plus à afficher.
            }
            case GameEvent.CardPlayed e -> {
                view.log("Tu joues la carte " + e.card().name(), Palette.TEXT);
                if (e.card() instanceof cardistry.model.card.SpellCard) {
                    animatePlayerAttack();
                }
            }
            case GameEvent.CardReplayed e -> view.log("Espace : " + e.card().name() + " rejouée à "
                    + num(e.factor() * 100) + " %", Palette.INFO);
            case GameEvent.CardReplaced e -> {
                view.log(e.removed().name() + " remplacée par " + e.replacement().name(), Palette.INFO);
                render();
            }
            case GameEvent.CardChoiceOffered e -> {
                // Le choix est affiché par l'écran de choix.
            }
            case GameEvent.CardChosen e -> view.log("Tu gardes " + e.kept().name() + ", tu défausses "
                    + String.join(", ", e.discarded().stream().map(cardistry.model.card.Card::name).toList()), Palette.INFO);
            case GameEvent.ItemUsed e -> {
                view.log("Tu utilises l'objet " + e.item().name(), Palette.INFO);
                render();
            }
            case GameEvent.CardDrawn e -> {
                view.log("Tu pioches " + e.card().name(), Palette.INFO);
                animateDraw();
            }
            case GameEvent.DamageDealt e -> {
                if (e.target() instanceof Enemy enemy) {
                    view.log(enemy.name() + " subit " + num(e.amount()) + " dégâts" + mult(e.multiplier()), Palette.ACCENT);
                    animateEnemyHit(enemy, e.amount());
                } else if (e.target() instanceof cardistry.model.Familiar f) {
                    view.log(cardistry.ui.FamiliarText.hitLine(f, e.amount()) + mult(e.multiplier()), Palette.WARNING);
                    animateFamiliarHit(e.amount());
                } else {
                    view.log("Tu subis " + num(e.amount()) + " dégâts" + mult(e.multiplier()), Palette.DAMAGE);
                    animatePlayerHit(e.amount());
                }
            }
            case GameEvent.Healed e -> {
                view.log("Tu récupères " + num(e.amount()) + " PV" + mult(e.multiplier()), Palette.HEAL);
                animateHeal(e.amount());
            }
            case GameEvent.TerrainChanged e -> {
                view.log("Téléportation : " + e.previous().name() + " → " + e.current().name(), Palette.INFO);
                animateTerrain();
            }
            case GameEvent.EnemyAttacked e -> {
                view.log(e.enemy().name() + " t'attaque !", Palette.DAMAGE);
                animateEnemyAttack();
            }
            case GameEvent.EnemyKilled e -> {
                view.log("Tu as vaincu " + e.enemy().name() + " !", Palette.ACCENT);
                animateEnemyDeath();
            }
            case GameEvent.CombatStarted e -> {
                view.journalOpen = false;
                view.journalScroll = 0;
                view.log((e.total() > 0 ? "Combat " + e.number() + "/" + e.total() : "Combat") + " : "
                        + e.enemy().name(), Palette.WARNING);
                e.enemy().terrain().ifPresent(t -> view.log("Chez lui : " + t.name()
                        + t.effectText().map(text -> " (" + text + ")").orElse(""), Palette.element(t.element())));
                if (e.number() > 1) {
                    animateEnemyArrival();
                }
            }
            case GameEvent.HandDealt e -> {
                view.log("Nouvelle main : " + e.cards().size() + " cartes", Palette.INFO);
                view.selected = 0;
            }
            case GameEvent.ActionRejected e -> {
                String message = switch (e.reason()) {
                    case INVALID_CHOICE -> "Pas de carte à ce numéro.";
                    case HAND_FULL -> "Ta main est pleine : joue une carte.";
                    case EMPTY_SLOT -> "Cet emplacement est vide.";
                    case NO_ITEM -> "Pas d'objet à cet emplacement.";
                    case INVALID_REWARD -> "Choix de récompense invalide.";
                    case INVENTORY_FULL -> "Inventaire plein : choisis quoi remplacer.";
                    case NO_CIGARETTE -> "Plus de clope : G pour en rouler une.";
                    case PACK_FULL -> "Paquet plein : impossible de rouler.";
                    case UNPLAYABLE_CARD -> "NO CARDS : carte injouable.";
                    case UNDO_UNAVAILABLE -> "Impossible d'annuler maintenant.";
                    case DECK_EMPTY -> "Plus aucune carte à piocher.";
                    case NOT_ENOUGH_CHOOMIES -> "Pas assez de Choomies.";
                    case SOLD_OUT -> "Déjà vendu.";
                    case DECK_TOO_SMALL -> "Ton deck est au minimum (" + cardistry.core.CombatEngine.MIN_DECK_SIZE + " cartes).";
                    case CHOICE_UNAVAILABLE -> "Ce choix n'est pas possible.";
                };
                view.log(message, Palette.WARNING);
                if (page != Page.COMBAT) {
                    view.toast = message; // hors combat (shop), pas de journal à l'écran
                }
                render();
            }
            case GameEvent.EffectAdded e -> view.log(e.effect().describe(), Palette.INFO);
            case GameEvent.EffectActivated e -> {
                view.log(e.effect().name() + " se déclenche !", Palette.ACCENT);
                render();
            }
            case GameEvent.EffectEnded e -> view.log(e.effect().name() + " : terminé", Palette.DIM);
            case GameEvent.PlayerTurnSkipped e -> {
                view.log("Tu sautes ce tour (" + String.join(", ", e.causes()) + ")", Palette.DAMAGE);
                render();
                pause(600);
            }
            case GameEvent.EnemyTurnSkipped e -> {
                view.log(e.message(), Palette.HEAL);
                render();
            }
            case GameEvent.AttackCancelled e -> view.log(e.cause() + " annule l'attaque de " + e.enemy().name(),
                    Palette.HEAL);
            case GameEvent.DrunkennessChanged e -> {
                if (e.drunkenness() > 0) {
                    view.log(state == null ? "Ivresse " + e.drunkenness() + "/" + e.threshold()
                            : cardistry.ui.Format.drunkenness(state), Palette.WARNING);
                }
                render();
            }
            case GameEvent.CurseGained e -> view.log(e.curse().displayName() + " au prochain combat ("
                    + curseText(e.curse(), 1) + ")", Palette.DAMAGE);
            case GameEvent.CurseStarted e -> view.log(e.curse().displayName() + " : "
                    + curseText(e.curse(), state == null ? 1 : state.activeIntensity(e.curse())), Palette.DAMAGE);
            case GameEvent.CurseReduced e -> view.log(e.curse().displayName()
                    + (e.intensity() == 0 ? " évitée !" : " réduite (" + curseText(e.curse(), e.intensity()) + ")"),
                    Palette.HEAL);
            case GameEvent.AbilityTriggered e -> view.log(e.message(), Palette.SECRET);
            case GameEvent.CigarettesChanged e -> render();
            case GameEvent.CigaretteSmoked e -> {
                if (e.transformedIntoPetitPetou()) {
                    view.log("La clope se transforme en Petit pétou !", Palette.ACCENT);
                    for (int i = 0; i < 4; i++) {
                        view.floating.clear();
                        view.floating.add(new ViewState.Floating("Petit pétou !", Palette.ACCENT, false, i / 2));
                        frame();
                    }
                    view.resetEffects();
                } else {
                    view.log("Tu fumes une clope", Palette.TEXT);
                }
                render();
            }
            case GameEvent.CigaretteRolled e -> view.log("Tu roules une clope", Palette.TEXT);
            case GameEvent.CurseEnded e -> view.log(e.curse().displayName() + " terminée", Palette.DIM);
            case GameEvent.RewardOffered e -> view.log("Récompense : choisis 1 parmi " + e.options().size(), Palette.ACCENT);
            case GameEvent.RewardTaken e -> view.log("Tu prends " + e.taken().name()
                    + (e.replaced() != null ? " (à la place de " + e.replaced().name() + ")" : ""), Palette.ACCENT);
            case GameEvent.SetTierActivated e -> {
                String text = "Set " + e.setName() + " " + e.count() + " activé : " + e.description();
                view.log(text, Palette.ACCENT);
                view.toast = text;
            }
            case GameEvent.SetTierDeactivated e -> view.log("Set " + e.setName() + " " + e.count() + " perdu : "
                    + e.description(), Palette.WARNING);
            case GameEvent.TrinketDiscarded e -> view.log("Tu jettes " + e.trinket().name(), Palette.WARNING);
            case GameEvent.RewardSkipped e -> view.log("Récompense refusée", Palette.DIM);
            case GameEvent.EnemyNulled e -> {
                view.log(e.enemy().name() + " = null", Palette.SECRET);
                view.toast = "NullPointerException : " + e.enemy().name() + " devient null";
                render();
            }
            case GameEvent.CardVoided e -> {
                view.log(e.removed().name() + " devient " + e.dead().name(), Palette.SECRET);
                render();
            }
            case GameEvent.TurnUndone e -> {
                view.log("Ctrl+Z : retour au début du tour " + e.turn(), Palette.SECRET);
                view.toast = "Ctrl+Z : tour " + e.turn() + " annulé";
                view.resetEffects();
            }
            case GameEvent.SecretUnlocked e -> view.log("Secret débloqué : " + e.name(), Palette.SECRET);
            case GameEvent.GameWon e -> view.log("Victoire !", Palette.ACCENT);
            case GameEvent.FamiliarActed e -> animateFamiliar(e.action() == GameEvent.FamiliarActed.Action.ATTACK
                    ? cardistry.ui.sprite.Sprite.ATTACK : "smoke");
            case GameEvent.FamiliarKnockedOut e -> render();
            case GameEvent.GameLost e -> {
                view.log("Défaite...", Palette.DAMAGE);
                animatePlayerDeath();
            }
            case GameEvent.IntentChosen e -> render();
            case GameEvent.RestTaken e -> view.log("Sieste : " + e.choice(), Palette.HEAL);
            case GameEvent.DeckShuffled e -> view.log("Défausse remélangée : " + e.size() + " cartes", Palette.DIM);
            case GameEvent.CardAddedToDeck e -> view.log(e.card().name() + " ajoutée au deck", Palette.ACCENT);
            case GameEvent.CardRemovedFromDeck e -> view.log(e.card().name() + " retirée du deck", Palette.WARNING);
            case GameEvent.CardRewardOffered e -> {
            }
            case GameEvent.CardRewardSkipped e -> view.log("Carte refusée", Palette.DIM);
            case GameEvent.StoryStarted e -> view.log("Événement : " + e.story().title(), Palette.INFO);
            case GameEvent.StoryResolved e -> e.lines().forEach(line -> view.log(line, Palette.INFO));
            case GameEvent.ShopEntered e -> {
                view.log("Shop", Palette.ACCENT);
                view.toast = null;
            }
            case GameEvent.ItemBought e -> {
                view.log("Acheté : " + e.offer().name() + " (" + e.offer().price() + " Choomies)"
                        + (e.replaced() != null ? ", à la place de " + e.replaced().name() : ""), Palette.ACCENT);
                view.toast = "Acheté : " + e.offer().name();
            }
            case GameEvent.ShopRestocked e -> view.toast = "Nouveaux articles (" + e.price() + " Choomies)";
            case GameEvent.CardRemovalBought e -> view.toast = "Carte retirée (" + e.price() + " Choomies)";
            case GameEvent.ShopLeft e -> view.toast = null;
            case GameEvent.LevelStarted e -> {
                view.log("— Niveau " + e.level() + " —", Palette.ACCENT);
                view.toast = "Niveau " + e.level() + "/3";
            }
            case GameEvent.NodeEntered e -> {
                view.log("Étage " + e.floor() + " : " + e.kind(), Palette.INFO);
            }
            case GameEvent.EnemyMoveUsed e -> {
                view.log(cardistry.ui.IntentText.journal(e), Palette.WARNING);
                if (e.move().id().equals(cardistry.model.enemy.EnemyRegistry.SIX_SEVEN)) {
                    view.floating.add(new ViewState.Floating("SIX SEVEEEN !", Palette.ACCENT, true, 0));
                }
            }
            case GameEvent.EnemyDodged e -> {
                view.log(cardistry.ui.IntentText.journal(e), Palette.WARNING);
                view.floating.clear();
                view.floating.add(new ViewState.Floating("esquive", Palette.WARNING, true, 0));
                frame();
                view.resetEffects();
                render();
            }
            case GameEvent.StatusApplied e -> {
                if (state != null && state.hasEnemy()) {
                    view.log(cardistry.ui.StatusText.journal(state, e), Palette.status(e.type()));
                }
                render();
            }
            case GameEvent.StatusRefused e -> {
                if (state != null && state.hasEnemy()) {
                    view.log(cardistry.ui.StatusText.journal(state, e), Palette.DIM);
                }
            }
            case GameEvent.StatusEnded e -> {
                if (state != null && state.hasEnemy()) {
                    view.log(cardistry.ui.StatusText.journal(state, e), Palette.DIM);
                }
                render();
            }
            default -> {
                String line = cardistry.ui.IntentText.journal(event);
                if (line != null) {
                    boolean good = event instanceof GameEvent.ChoomiesChanged || event instanceof GameEvent.TrinketReturned;
                    view.log(line, good ? Palette.ACCENT : Palette.WARNING);
                    render();
                }
            }
        }
    }

    @Override
    public void showIntro(GameState state) {
        this.state = state;
        page = Page.RULES;
        render();
        while (true) {
            KeyStroke key = readKey();
            if (isQuit(key)) {
                throw new cardistry.ui.LeaveGameException();
            }
            if (key.getKeyType() == KeyType.Enter || isChar(key, ' ')) {
                return;
            }
        }
    }

    @Override
    public void showStatus(GameState state) {
        enterCombat(state);
    }

    @Override
    public void showHand(GameState state) {
        enterCombat(state);
    }

    @Override
    public void showMap(cardistry.run.map.MapView view) {
        this.state = view.state();
        this.mapView = view;
        mapSelected = Math.min(mapSelected, Math.max(0, view.choices().size() - 1));
        page = Page.MAP;
        render();
    }

    @Override
    public int chooseNode(cardistry.run.map.MapView view) {
        showMap(view);
        int count = view.choices().size();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft, ArrowUp -> mapSelected = Math.floorMod(mapSelected - 1, count);
                case ArrowRight, ArrowDown -> mapSelected = Math.floorMod(mapSelected + 1, count);
                case Enter -> {
                    int chosen = mapSelected;
                    mapSelected = 0;
                    return chosen;
                }
                case Escape -> pauseMenu();
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 'q') {
                        confirmQuit();
                    } else if (c == 'i') {
                        showInventory();
                    } else if (c >= '1' && c < '1' + count) {
                        mapSelected = 0;
                        return c - '1';
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    @Override
    public int chooseCardReward(GameState state, List<cardistry.model.card.Card> options) {
        this.state = state;
        cardOptions = options;
        cardSelected = 0;
        page = Page.CARD_REWARD;
        render();
        flushInput();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft -> cardSelected = Math.floorMod(cardSelected - 1, options.size());
                case ArrowRight -> cardSelected = Math.floorMod(cardSelected + 1, options.size());
                case Escape -> pauseMenu();
                case Enter -> {
                    return cardSelected;
                }
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 's') {
                        return -1;
                    } else if (c == 'q') {
                        confirmQuit();
                    } else if (c >= '1' && c < '1' + options.size()) {
                        return c - '1';
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    private int deckSelected = 0;
    private cardistry.story.Story story;
    private int storySelected = 0;
    private GameEvent.StoryResolved storyResult;

    @Override
    public int chooseStory(GameState state, cardistry.story.Story story) {
        this.state = state;
        this.story = story;
        int count = story.choices().size();
        storySelected = 0;
        page = Page.STORY;
        render();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowUp, ArrowLeft -> storySelected = Math.floorMod(storySelected - 1, count);
                case ArrowDown, ArrowRight -> storySelected = Math.floorMod(storySelected + 1, count);
                case Enter -> {
                    if (story.choices().get(storySelected).isAvailable(state)) {
                        return storySelected;
                    }
                }
                case Escape -> pauseMenu();
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 'q') {
                        confirmQuit();
                    } else if (c >= '1' && c < '1' + count) {
                        storySelected = c - '1';
                        if (story.choices().get(storySelected).isAvailable(state)) {
                            return storySelected;
                        }
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    @Override
    public void showStoryResult(GameState state, GameEvent.StoryResolved result) {
        this.state = state;
        this.storyResult = result;
        page = Page.STORY_RESULT;
        render();
        while (true) {
            KeyStroke key = readKey();
            if (key.getKeyType() == KeyType.Enter || key.getKeyType() == KeyType.Escape || isChar(key, ' ')) {
                return;
            }
            if (isChar(key, 'q')) {
                confirmQuit();
            }
        }
    }
    private cardistry.core.Shop shop;
    private int shopSelected = 0;
    private int removalPrice = 0;
    /** Objet ou trinket qui demande un remplacement (récompense ou achat). */
    private Collectible replaceTarget;

    @Override
    public cardistry.core.shop.ShopAction chooseShopAction(GameState state, cardistry.core.Shop shop, int removalPrice) {
        this.state = state;
        this.shop = shop;
        this.removalPrice = removalPrice;
        int count = shop.offers().size();
        shopSelected = Math.min(shopSelected, Math.max(0, count - 1));
        page = Page.SHOP;
        render();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft -> shopSelected = Math.floorMod(shopSelected - 1, count);
                case ArrowRight -> shopSelected = Math.floorMod(shopSelected + 1, count);
                case ArrowUp -> shopSelected = shopRow(-1);
                case ArrowDown -> shopSelected = shopRow(1);
                case Escape -> {
                    pauseMenu();
                    page = Page.SHOP;
                }
                case Enter -> {
                    cardistry.core.shop.ShopAction buy = buyChoice(state, shop);
                    if (buy != null) {
                        page = Page.SHOP;
                        return buy;
                    }
                    page = Page.SHOP;
                }
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 'p') {
                        return leaveShop();
                    } else if (c == 'r') {
                        return new cardistry.core.shop.ShopAction.Reroll();
                    } else if (c == 't') {
                        int index = chooseCardToRemove(state);
                        page = Page.SHOP;
                        if (index >= 0) {
                            return new cardistry.core.shop.ShopAction.RemoveCard(index);
                        }
                    } else if (c == 'q') {
                        confirmQuit();
                    } else if (c == 'v' && shop.offers().get(shopSelected)
                            instanceof cardistry.core.shop.ShopOffer.CardOffer offer) {
                        showCardDetail(offer.card(), false);
                        page = Page.SHOP;
                    } else if (c >= '1' && c <= '9' && c - '1' < count) {
                        shopSelected = c - '1';
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    private cardistry.core.shop.ShopAction leaveShop() {
        shopSelected = 0;
        page = Page.MAP;
        return new cardistry.core.shop.ShopAction.Leave();
    }

    /** Ligne au-dessus / au-dessous (cartes, objets, trinkets), même position si possible. */
    private int shopRow(int delta) {
        List<List<Integer>> rows = ShopScreen.rows(shop).stream().filter(r -> !r.isEmpty()).toList();
        int row = 0;
        for (int r = 0; r < rows.size(); r++) {
            if (rows.get(r).contains(shopSelected)) {
                row = r;
            }
        }
        int offset = Math.max(0, rows.get(row).indexOf(shopSelected));
        List<Integer> target = rows.get(Math.floorMod(row + delta, rows.size()));
        return target.get(Math.min(offset, target.size() - 1));
    }

    /** Achat de l'article sélectionné ; inventaire plein : écran de remplacement ({@code null} = annulé). */
    private cardistry.core.shop.ShopAction buyChoice(GameState state, cardistry.core.Shop shop) {
        cardistry.core.shop.ShopOffer offer = shop.offers().get(shopSelected);
        if (shop.isSold(shopSelected) || state.choomies() < offer.price()
                || !(offer instanceof cardistry.core.shop.ShopOffer.CollectibleOffer c)
                || !ItemScreens.isFull(state, c.collectible())) {
            return cardistry.core.shop.ShopAction.Buy.of(shopSelected); // le moteur refuse si besoin
        }
        replaceTarget = c.collectible();
        replaceSelected = NO_REPLACEMENT;
        page = Page.REPLACE;
        render();
        int owned = c.collectible() instanceof cardistry.items.Trinket ? state.trinkets().size() : state.items().size();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case Enter -> {
                    if (replaceSelected != NO_REPLACEMENT) {
                        return new cardistry.core.shop.ShopAction.Buy(shopSelected, replaceSelected);
                    }
                }
                case Escape -> {
                    return null;
                }
                default -> replaceSelected = moveReplacement(key, owned);
            }
            render();
        }
    }

    @Override
    public int chooseCardToRemove(GameState state) {
        this.state = state;
        deckSelected = 0;
        page = Page.DECK_REMOVE;
        render();
        int count = state.deck().size();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft -> deckSelected = Math.floorMod(deckSelected - 1, count);
                case ArrowRight -> deckSelected = Math.floorMod(deckSelected + 1, count);
                case ArrowUp -> deckSelected = Math.floorMod(deckSelected - ItemScreens.DECK_COLUMNS, count);
                case ArrowDown -> deckSelected = Math.floorMod(deckSelected + ItemScreens.DECK_COLUMNS, count);
                case Enter -> {
                    return deckSelected;
                }
                case Escape -> {
                    return -1;
                }
                default -> {
                }
            }
            render();
        }
    }

    @Override
    public cardistry.run.RestOption chooseRest(GameState state, List<cardistry.run.RestOption> options) {
        this.state = state;
        restOptions = options;
        restSelected = 0;
        page = Page.REST;
        render();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft, ArrowUp -> restSelected = Math.floorMod(restSelected - 1, options.size());
                case ArrowRight, ArrowDown -> restSelected = Math.floorMod(restSelected + 1, options.size());
                case Enter -> {
                    return options.get(restSelected);
                }
                case Escape -> pauseMenu();
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 'q') {
                        confirmQuit();
                    } else if (c >= '1' && c < '1' + options.size()) {
                        return options.get(c - '1');
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    private int statSelected = 0;

    @Override
    public cardistry.core.stats.StatType chooseStatPoint(GameState state) {
        this.state = state;
        List<cardistry.core.stats.StatType> stats = cardistry.core.stats.StatType.characterStats();
        page = Page.STAT_POINTS;
        render();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft, ArrowUp -> statSelected = Math.floorMod(statSelected - 1, stats.size());
                case ArrowRight, ArrowDown -> statSelected = Math.floorMod(statSelected + 1, stats.size());
                case Enter -> {
                    return stats.get(statSelected);
                }
                case Escape -> pauseMenu();
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 'q') {
                        confirmQuit();
                    } else if (c >= '1' && c < '1' + stats.size()) {
                        statSelected = c - '1';
                        return stats.get(statSelected);
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    @Override
    public Optional<cardistry.core.stats.StatType> chooseAltar(GameState state) {
        this.state = state;
        List<cardistry.core.stats.StatType> stats = cardistry.core.stats.StatType.characterStats();
        page = Page.ALTAR;
        render();
        while (true) {
            //Log.println("DEBUG_ALTAR_BUTTON"+ altarButton); //DEBUG
            //Log.println("DEBUG_ALTAR_STAT"+ altarStat); //DEBUG
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft -> altarButton = Math.floorMod(altarButton - 1, 2);
                case ArrowUp -> altarStat = Math.floorMod(altarStat - 1, stats.size());
                case ArrowRight -> altarButton = Math.floorMod(altarButton + 1, 2);
                case ArrowDown -> altarStat = Math.floorMod(altarStat + 1, stats.size());
                case Enter -> {
                    if(altarButton==0){
                        return Optional.empty();
                    }else{
                        return Optional.of(stats.get(altarStat));
                    }
                }
                case Escape -> pauseMenu();
                default -> {
                }
            }
            render();
        }
    }

    @Override
    public void showGameOver(GameState state, List<String> unlockedSecrets) {
        this.state = state;
        this.unlockedSecrets = List.copyOf(unlockedSecrets);
        pause(600);
        page = Page.GAME_OVER;
        render();
    }

    /** Effet d'une malédiction pour le joueur actuel (malus doublé pour Louis). */
    private String curseText(cardistry.core.Curse curse, int intensity) {
        return state == null ? curse.description(intensity) : state.describeCurse(curse, intensity);
    }

    private void enterCombat(GameState state) {
        this.state = state;
        page = Page.COMBAT;
        clampSelection();
        render();
    }

    // ===================== Input =====================

    /** Code tapé sur l'écran titre pour CLAUDE.md. */
    static final String CLAUDE_CODE = "claude";

    @Override
    public TitleChoice title(long seed) {
        this.seed = seed;
        titleCodes.clear();
        titleSelected = 0;
        page = Page.TITLE;
        render();
        StringBuilder typed = new StringBuilder();
        while (true) {
            KeyStroke key = readKey();
            if (isCtrl(key, 'z')) {
                addTitleCode(Secret.CTRL_Z);
                continue;
            }
            if (isQuit(key)) {
                throw new QuitGameException();
            }
            switch (key.getKeyType()) {
                case ArrowUp, ArrowLeft -> titleSelected = Math.floorMod(titleSelected - 1, MenuScreens.TITLE_ENTRIES.size());
                case ArrowDown, ArrowRight -> titleSelected = Math.floorMod(titleSelected + 1,
                        MenuScreens.TITLE_ENTRIES.size());
                case Enter -> {
                    switch (titleSelected) {
                        case 0 -> {
                            return new TitleChoice(false, titleCodes);
                        }
                        case 1 -> {
                            return new TitleChoice(true, titleCodes);
                        }
                        case 2 -> {
                            optionsMenu();
                            page = Page.TITLE;
                        }
                        default -> throw new QuitGameException();
                    }
                }
                case Character -> {
                    if (isChar(key, 'r')) {
                        return new TitleChoice(true, titleCodes);
                    }
                    typed.append(Character.toLowerCase(key.getCharacter()));
                    if (typed.length() > CLAUDE_CODE.length()) {
                        typed.deleteCharAt(0);
                    }
                    if (typed.toString().equals(CLAUDE_CODE)) {
                        addTitleCode(Secret.CLAUDE_MD);
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    private int titleSelected = 0;
    private List<cardistry.hero.Hero> heroes = List.of();
    private int heroSelected = 0;

    @Override
    public java.util.Optional<cardistry.hero.Hero> chooseHero(List<cardistry.hero.Hero> heroes) {
        this.heroes = heroes;
        heroSelected = Math.min(heroSelected, heroes.size() - 1);
        page = Page.HERO;
        render();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowLeft, ArrowUp -> heroSelected = Math.floorMod(heroSelected - 1, heroes.size());
                case ArrowRight, ArrowDown -> heroSelected = Math.floorMod(heroSelected + 1, heroes.size());
                case Enter -> {
                    return java.util.Optional.of(heroes.get(heroSelected));
                }
                case Escape -> {
                    return java.util.Optional.empty();
                }
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c >= '1' && c - '1' < heroes.size()) {
                        heroSelected = c - '1';
                    } else if (c == 'q') {
                        return java.util.Optional.empty();
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    private void addTitleCode(Secret secret) {
        if (!titleCodes.contains(secret)) {
            titleCodes.add(secret);
            render();
        }
    }

    @Override
    public void waitForContinue() {
        // Plein écran : pas besoin de valider entre l'état et la main.
    }

    @Override
    public Action nextAction(GameState state) {
        this.state = state;
        page = Page.COMBAT;
        clampSelection();
        render();
        while (true) {
            KeyStroke key = readKey();
            int handSize = state.hand().size();
            if (isCtrl(key, 'z')) {
                return new Action.Undo();
            }
            if (journalKey(key)) {
                render();
                continue;
            }
            switch (key.getKeyType()) {
                case ArrowLeft -> moveSelection(-1, handSize);
                case ArrowRight -> moveSelection(1, handSize);
                case Enter -> {
                    if (handSize > 0) {
                        return new Action.PlayCard(view.selected);
                    }
                    return new Action.DrawCard();
                }
                case Escape -> pauseMenu();
                case Character -> {
                    char c = Character.toLowerCase(key.getCharacter());
                    if (c == 'q') {
                        confirmQuit();
                    } else if (c == 'p' || c == '0') {
                        return new Action.DrawCard();
                    } else if (c == 'f') {
                        return new Action.Smoke();
                    } else if (c == 'g') {
                        return new Action.RollCigarette();
                    } else if (c == 'i') {
                        showInventory();
                    } else if (c == 'v' && handSize > 0) {
                        showCardDetail(state.hand().get(Math.min(view.selected, handSize - 1)), true);
                    } else if (ItemKeys.slot(c, state.itemSlots()) >= 0) {
                        return new Action.UseItem(ItemKeys.slot(c, state.itemSlots()));
                    } else if (c >= '1' && c <= '9') {
                        int slot = c - '1';
                        if (slot < handSize) {
                            view.selected = slot;
                        }
                        return new Action.PlayCard(slot);
                    } else if (c == 'h') {
                        moveSelection(-1, handSize);
                    } else if (c == 'l') {
                        moveSelection(1, handSize);
                    }
                }
                default -> {
                }
            }
        }
    }

    /**
     * Journal du combat (fenêtre seulement) : J l'ouvre et le ferme ; ouvert, ↑/↓ le font défiler et
     * Échap le ferme.
     *
     * @return {@code true} si la touche a été consommée
     */
    private boolean journalKey(KeyStroke key) {
        if (images == null) {
            return false;
        }
        if (isChar(key, 'j') && !key.isCtrlDown()) {
            view.journalOpen = !view.journalOpen;
            view.journalScroll = 0;
            return true;
        }
        if (!view.journalOpen) {
            return false;
        }
        int capacity = CombatWindowScreen.journalCapacity(screen.getTerminalSize().getRows());
        int maxScroll = Math.max(0, view.log.size() - capacity);
        switch (key.getKeyType()) {
            case Escape -> view.journalOpen = false;
            case ArrowUp -> view.journalScroll = Math.min(maxScroll, view.journalScroll + 1);
            case ArrowDown -> view.journalScroll = Math.max(0, view.journalScroll - 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public RewardChoice chooseReward(GameState state, List<Collectible> options) {
        this.state = state;
        this.rewardOptions = options;
        rewardSelected = Math.min(rewardSelected, options.size() - 1);
        page = Page.REWARD;
        render();
        flushInput();
        while (true) {
            KeyStroke key = readKey();
            if (page == Page.REWARD) {
                switch (key.getKeyType()) {
                    case ArrowLeft -> rewardSelected = Math.floorMod(rewardSelected - 1, options.size());
                    case ArrowRight -> rewardSelected = Math.floorMod(rewardSelected + 1, options.size());
                    case Escape -> pauseMenu();
                    case Enter -> {
                        if (!ItemScreens.isFull(state, options.get(rewardSelected))) {
                            return leaveReward(RewardChoice.Take.into(rewardSelected));
                        }
                        replaceSelected = NO_REPLACEMENT;
                        replaceTarget = options.get(rewardSelected);
                        page = Page.REPLACE;
                    }
                    case Character -> {
                        char c = Character.toLowerCase(key.getCharacter());
                        if (c == 's') {
                            return leaveReward(new RewardChoice.Skip());
                        } else if (c == 'q') {
                            confirmQuit();
                        }
                    }
                    default -> {
                    }
                }
            } else {
                int owned = options.get(rewardSelected) instanceof cardistry.items.Trinket
                        ? state.trinkets().size() : state.items().size();
                switch (key.getKeyType()) {
                    case Enter -> {
                        if (replaceSelected != NO_REPLACEMENT) {
                            return leaveReward(new RewardChoice.Take(rewardSelected, replaceSelected));
                        }
                    }
                    case Escape -> page = Page.REWARD;
                    default -> replaceSelected = moveReplacement(key, owned);
                }
            }
            render();
        }
    }

    @Override
    public int chooseDrawnCard(GameState state, List<cardistry.model.card.Card> options) {
        this.state = state;
        Page previous = page;
        cardOptions = options;
        cardSelected = 0;
        showMetaPhrase = !metaPhraseShown;
        metaPhraseShown = true;
        page = Page.CARD_CHOICE;
        render();
        while (true) {
            KeyStroke key = readKey();
            Integer chosen = null;
            switch (key.getKeyType()) {
                case ArrowLeft -> cardSelected = Math.floorMod(cardSelected - 1, options.size());
                case ArrowRight -> cardSelected = Math.floorMod(cardSelected + 1, options.size());
                case Enter -> chosen = cardSelected;
                case Character -> {
                    char c = key.getCharacter();
                    if (c >= '1' && c < '1' + options.size()) {
                        chosen = c - '1';
                    }
                }
                default -> {
                }
            }
            if (chosen != null) {
                page = previous;
                render();
                return chosen;
            }
            render();
        }
    }

    @Override
    public int chooseTrinketToDiscard(GameState state) {
        this.state = state;
        Page previous = page;
        discardSelected = Math.min(discardSelected, state.trinkets().size() - 1);
        page = Page.DISCARD;
        render();
        while (true) {
            KeyStroke key = readKey();
            int count = state.trinkets().size();
            switch (key.getKeyType()) {
                case ArrowUp, ArrowLeft -> discardSelected = Math.floorMod(discardSelected - 1, count);
                case ArrowDown, ArrowRight -> discardSelected = Math.floorMod(discardSelected + 1, count);
                case Enter -> {
                    page = previous;
                    int chosen = discardSelected;
                    discardSelected = 0;
                    return chosen;
                }
                default -> {
                }
            }
            render();
        }
    }

    /** Écran de remplacement : rien n'est présélectionné, Entrée seule ne remplace donc jamais rien. */
    static final int NO_REPLACEMENT = -1;

    /** Sélection de l'écran de remplacement après {@code key} : ←/→ ou le numéro de l'emplacement. */
    private int moveReplacement(KeyStroke key, int owned) {
        switch (key.getKeyType()) {
            case ArrowLeft -> {
                return replaceSelected == NO_REPLACEMENT ? owned - 1 : Math.floorMod(replaceSelected - 1, owned);
            }
            case ArrowRight -> {
                return replaceSelected == NO_REPLACEMENT ? 0 : Math.floorMod(replaceSelected + 1, owned);
            }
            case Character -> {
                char c = key.getCharacter();
                return c >= '1' && c - '1' < owned ? c - '1' : replaceSelected;
            }
            default -> {
                return replaceSelected;
            }
        }
    }

    /**
     * Oublie les touches tapées pendant les animations : un écran de choix (récompense, carte) ne doit
     * pas être validé par un appui antérieur à son affichage. Sans animations ({@code fast}), rien à
     * oublier : les touches en attente sont voulues (tests, saisie scriptée).
     */
    private void flushInput() {
        if (fast) {
            return;
        }
        try {
            KeyStroke key;
            while ((key = screen.pollInput()) != null) {
                if (key.getKeyType() == KeyType.EOF) {
                    throw new QuitGameException();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private RewardChoice leaveReward(RewardChoice choice) {
        page = Page.COMBAT;
        rewardSelected = 0;
        return choice;
    }

    private int deckScroll = 0;
    private boolean deckPiles = false;

    /** D ouvre le deck pendant une partie (pas au titre, dans les menus, ni pendant la confirmation de quitter). */
    private boolean opensDeck(KeyStroke key) {
        return key.getKeyType() == KeyType.Character && Character.toLowerCase(key.getCharacter()) == 'd'
                && !key.isCtrlDown() && !key.isAltDown() && state != null && state.usesDeck()
                && !NO_DECK_PAGES.contains(page) && !view.confirmingQuit;
    }

    /** Écran du deck jusqu'à D, Échap ou Entrée ; ↑/↓ font défiler. */
    private void showDeck() {
        Page previous = page;
        page = Page.DECK;
        deckScroll = 0;
        deckPiles = previous == Page.COMBAT || previous == Page.CARD_CHOICE;
        render();
        while (true) {
            KeyStroke key = readKey();
            boolean close = key.getKeyType() == KeyType.Escape || key.getKeyType() == KeyType.Enter
                    || key.getKeyType() == KeyType.Character && Character.toLowerCase(key.getCharacter()) == 'd';
            if (close) {
                break;
            }
            if (key.getKeyType() == KeyType.ArrowDown || key.getKeyType() == KeyType.ArrowUp) {
                int max = DeckScreen.maxScroll(new Painter(screen.newTextGraphics(), screen.getTerminalSize(), ascii,
                        images), state);
                deckScroll = Math.max(0, Math.min(max, deckScroll + (key.getKeyType() == KeyType.ArrowDown ? 1 : -1)));
            }
            render();
        }
        page = previous;
        render();
    }

    private boolean statsOfEnemy;

    /** C (fiche personnage) ou A (fiche adversaire) : comme D, pendant une partie, hors menus. */
    private boolean opensStats(KeyStroke key, char letter) {
        return key.getKeyType() == KeyType.Character && Character.toLowerCase(key.getCharacter()) == letter
                && !key.isCtrlDown() && !key.isAltDown() && state != null && page != Page.STATS
                && !NO_DECK_PAGES.contains(page) && !view.confirmingQuit;
    }

    /** Fiche des stats jusqu'à C / A, Échap ou Entrée. */
    private void showStats(boolean enemy) {
        Page previous = page;
        page = Page.STATS;
        statsOfEnemy = enemy;
        render();
        while (true) {
            KeyStroke key = readKey();
            if (key.getKeyType() == KeyType.Escape || key.getKeyType() == KeyType.Enter
                    || isChar(key, enemy ? 'a' : 'c')) {
                break;
            }
        }
        page = previous;
        render();
    }

    private cardistry.model.card.Card detailCard;
    private boolean detailInCombat;

    /** Fiche de {@code card} jusqu'à V, Échap ou Entrée ; {@code inCombat} : effet réel contre l'adversaire. */
    private void showCardDetail(cardistry.model.card.Card card, boolean inCombat) {
        Page previous = page;
        page = Page.CARD_DETAIL;
        detailCard = card;
        detailInCombat = inCombat;
        render();
        while (true) {
            KeyStroke key = readKey();
            if (key.getKeyType() == KeyType.Escape || key.getKeyType() == KeyType.Enter || isChar(key, 'v')) {
                break;
            }
        }
        page = previous;
        render();
    }

    private void showInventory() {
        Page previous = page;
        page = Page.INVENTORY;
        render();
        readKey();
        page = previous;
        render();
    }

    @Override
    public void waitForExit() {
        readKey();
    }

    private void moveSelection(int delta, int handSize) {
        if (handSize == 0) {
            return;
        }
        view.selected = Math.floorMod(view.selected + delta, handSize);
        render();
    }

    private void clampSelection() {
        int size = state.hand().size();
        view.selected = size == 0 ? 0 : Math.min(view.selected, size - 1);
    }

    // ===================== Son, pause, options =====================

    private java.util.function.Consumer<cardistry.ui.audio.UiSound> sounds = s -> { };
    private cardistry.ui.OptionsController options = cardistry.ui.OptionsController.inMemory(
            cardistry.ui.Settings.defaults());
    private int menuSelected = 0;
    private int optionsSelected = 0;

    /** Sons des touches (curseur, validation, retour). */
    public void setSounds(java.util.function.Consumer<cardistry.ui.audio.UiSound> sounds) {
        this.sounds = sounds;
    }

    /** Réglages modifiables depuis l'écran Options. */
    public void setOptions(cardistry.ui.OptionsController options) {
        this.options = options;
    }

    /** Son d'une touche : flèches = curseur, Entrée = validation, Échap = retour. */
    private void keySound(KeyStroke key) {
        switch (key.getKeyType()) {
            case ArrowLeft, ArrowRight, ArrowUp, ArrowDown -> sounds.accept(cardistry.ui.audio.UiSound.CURSOR);
            case Enter -> sounds.accept(cardistry.ui.audio.UiSound.CONFIRM);
            case Escape -> sounds.accept(cardistry.ui.audio.UiSound.BACK);
            default -> {
            }
        }
    }

    /** Entrées du menu pause. */
    static final List<String> PAUSE_ENTRIES = List.of("Reprendre", "Options", "Quitter la partie");

    /**
     * Menu pause (Échap au niveau principal) : Reprendre, Options, Quitter (avec confirmation).
     * Échap ou « Reprendre » revient où on était.
     */
    private void pauseMenu() {
        Page previous = page;
        menuSelected = 0;
        page = Page.PAUSE;
        render();
        while (true) {
            KeyStroke key = readKey();
            switch (key.getKeyType()) {
                case ArrowUp -> menuSelected = Math.floorMod(menuSelected - 1, PAUSE_ENTRIES.size());
                case ArrowDown -> menuSelected = Math.floorMod(menuSelected + 1, PAUSE_ENTRIES.size());
                case Escape -> {
                    page = previous;
                    render();
                    return;
                }
                case Enter -> {
                    switch (menuSelected) {
                        case 0 -> {
                            page = previous;
                            render();
                            return;
                        }
                        case 1 -> {
                            optionsMenu();
                            page = Page.PAUSE;
                        }
                        default -> confirmQuit();
                    }
                }
                default -> {
                }
            }
            render();
        }
    }

    /** Lignes de l'écran Options : musique, effets, muet, retour. */
    static final int OPTION_ROWS = 4;

    /** Écran Options : ↑/↓ choisir, ←/→ régler, Entrée (muet, retour), Échap retour. Appliqué en direct. */
    private void optionsMenu() {
        Page previous = page;
        optionsSelected = 0;
        page = Page.OPTIONS;
        render();
        while (true) {
            KeyStroke key = readKey();
            cardistry.ui.Settings s = options.settings();
            switch (key.getKeyType()) {
                case ArrowUp -> optionsSelected = Math.floorMod(optionsSelected - 1, OPTION_ROWS);
                case ArrowDown -> optionsSelected = Math.floorMod(optionsSelected + 1, OPTION_ROWS);
                case ArrowLeft, ArrowRight -> {
                    int delta = key.getKeyType() == KeyType.ArrowRight ? 1 : -1;
                    switch (optionsSelected) {
                        case 0 -> options.apply(s.withMusic(s.music() + delta));
                        case 1 -> options.apply(s.withEffects(s.effects() + delta));
                        case 2 -> options.apply(s.withMuted(!s.muted()));
                        default -> {
                        }
                    }
                }
                case Enter -> {
                    if (optionsSelected == 2) {
                        options.apply(s.withMuted(!s.muted()));
                    } else if (optionsSelected == 3) {
                        page = previous;
                        render();
                        return;
                    }
                }
                case Escape -> {
                    page = previous;
                    render();
                    return;
                }
                default -> {
                }
            }
            render();
        }
    }

    private void confirmQuit() {
        view.confirmingQuit = true;
        render();
        KeyStroke key = readKey();
        view.confirmingQuit = false;
        if (isChar(key, 'o') || isChar(key, 'y') || isChar(key, 'q')) {
            throw new cardistry.ui.LeaveGameException(); // la partie s'arrête, pas l'application
        }
        render();
    }

    // ===================== Visionneuse de sprites =====================

    private List<java.util.Map.Entry<String, cardistry.ui.sprite.Sprite>> catalog = List.of();
    private int spriteIndex = 0;
    private String spriteAnimation = cardistry.ui.sprite.Sprite.IDLE;
    private int spriteFrame = 0;
    private boolean spriteFlash = false;
    private boolean spriteHidden = false;

    /**
     * Visionneuse ({@code --sprites}) : fait défiler les sprites de tous les adversaires et leurs
     * animations, sans lancer de partie. Q ou Échap pour quitter.
     */
    public void viewSprites() {
        catalog = cardistry.ui.sprite.Sprites.catalog();
        page = Page.SPRITES;
        render();
        while (true) {
            KeyStroke key = readKey();
            if (isQuit(key)) {
                return;
            }
            switch (key.getKeyType()) {
                case ArrowLeft -> selectSprite(-1);
                case ArrowRight -> selectSprite(1);
                case Character -> {
                    char c = key.getCharacter();
                    if (c >= '1' && c <= '4') {
                        playSprite(cardistry.ui.sprite.Sprite.ANIMATIONS.get(c - '1'));
                    }
                }
                default -> {
                }
            }
        }
    }

    private void selectSprite(int delta) {
        spriteIndex = Math.floorMod(spriteIndex + delta, catalog.size());
        spriteAnimation = cardistry.ui.sprite.Sprite.IDLE;
        spriteFrame = 0;
        render();
    }

    /** Joue une animation une fois (l'idle : deux tours), puis revient à l'idle. */
    private void playSprite(String animation) {
        cardistry.ui.sprite.Sprite sprite = catalog.get(spriteIndex).getValue();
        int frames = Math.max(1, sprite.frames(animation).size());
        boolean generic = sprite.frames(animation).isEmpty();
        int steps = animation.equals(cardistry.ui.sprite.Sprite.IDLE) ? frames * 2 : Math.max(frames, generic ? 6 : 1);
        spriteAnimation = animation;
        for (int i = 0; i < steps; i++) {
            spriteFrame = generic ? 0 : i % frames;
            spriteFlash = generic && animation.equals(cardistry.ui.sprite.Sprite.HURT) && i % 2 == 0;
            spriteHidden = generic && animation.equals(cardistry.ui.sprite.Sprite.DEATH) && i % 2 == 1;
            render();
            if (!fast) {
                sleep(generic ? FRAME_MS * 3 : IDLE_MS);
            }
        }
        spriteAnimation = cardistry.ui.sprite.Sprite.IDLE;
        spriteFrame = 0;
        spriteFlash = false;
        spriteHidden = false;
        render();
    }

    // ===================== Animations =====================

    private void animateEnemyHit(Enemy enemy, double amount) {
        double before = enemy.hp() + amount;
        int[] shake = {2, -2, 1, -1, 0, 0};
        boolean hurtFrames = enemyFrames(cardistry.ui.sprite.Sprite.HURT) > 0;
        for (int i = 0; i < shake.length; i++) {
            view.enemyOffsetX = shake[i];
            view.spriteAnimation = cardistry.ui.sprite.Sprite.HURT;
            view.spriteFrame = i / 2;
            // Avec une frame « hurt », un seul clignotement ; sans, clignotement comme avant.
            view.enemyFlash = (hurtFrames ? i == 0 : i < 3) ? TextColor.ANSI.WHITE_BRIGHT : null;
            view.enemyHpShown = before - amount * (i + 1) / shake.length;
            view.floating.clear();
            view.floating.add(new ViewState.Floating("-" + num(amount), Palette.ACCENT, true, i / 2));
            frame();
        }
        view.resetEffects();
        render();
    }

    /** Frames d'une animation du personnage dans la fenêtre (0 sans images ou sans personnage). */
    private int playerFrames(String animation) {
        return images != null && state != null && state.hero().isPresent()
                ? cardistry.ui.window.Images.playerFrames(state.hero().get().id(), animation) : 0;
    }

    /** Le familier attaque (frames attack) ou fume un joint (frames smoke). */
    private void animateFamiliar(String animation) {
        int frames = images != null && state != null
                ? state.familiar().map(f -> cardistry.ui.window.Images.playerFrames(f.id(), animation)).orElse(0) : 0;
        for (int i = 0; i < frames * 3; i++) {
            view.familiarAnimation = animation;
            view.familiarFrame = i / 3;
            frame();
        }
        view.familiarAnimation = cardistry.ui.sprite.Sprite.IDLE;
        view.familiarFrame = 0;
        render();
    }

    /** Le familier est touché : sa barre descend, il clignote. */
    private void animateFamiliarHit(double amount) {
        double before = state.familiar().map(f -> f.hp() + amount).orElse(0.0);
        for (int i = 0; i < 6; i++) {
            view.familiarFlash = i % 2 == 0 ? Palette.DAMAGE : null;
            view.familiarHpShown = before - amount * (i + 1) / 6;
            frame();
        }
        view.resetEffects();
        render();
    }

    /** Le personnage lance un sort : ses frames d'attaque, en avançant vers l'adversaire. */
    private void animatePlayerAttack() {
        int frames = playerFrames(cardistry.ui.sprite.Sprite.ATTACK);
        if (frames == 0) {
            return;
        }
        int[] offsets = {1, 2, 2, 0};
        for (int i = 0; i < offsets.length; i++) {
            view.playerAnimation = cardistry.ui.sprite.Sprite.ATTACK;
            view.playerFrame = Math.min(frames - 1, i * frames / offsets.length);
            view.playerOffsetX = offsets[i];
            frame();
        }
        view.playerAnimation = cardistry.ui.sprite.Sprite.IDLE;
        view.playerOffsetX = 0;
    }

    /** Le personnage tombe : ses frames de mort, puis il reste sur la dernière. */
    private void animatePlayerDeath() {
        int frames = playerFrames(cardistry.ui.sprite.Sprite.DEATH);
        for (int i = 0; i < frames * 3; i++) {
            view.playerAnimation = cardistry.ui.sprite.Sprite.DEATH;
            view.playerFrame = i / 3;
            frame();
        }
    }

    private void animatePlayerHit(double amount) {
        double before = state.player().hp() + amount;
        boolean hurtFrames = playerFrames(cardistry.ui.sprite.Sprite.HURT) > 0;
        for (int i = 0; i < 6; i++) {
            if (hurtFrames) {
                view.playerAnimation = cardistry.ui.sprite.Sprite.HURT;
                view.playerFrame = i / 3;
                view.playerOffsetX = i < 3 ? -1 : 0;
            }
            view.playerFlash = (hurtFrames ? i == 0 : i % 2 == 0) ? Palette.DAMAGE : null;
            view.playerHpShown = before - amount * (i + 1) / 6;
            view.floating.clear();
            view.floating.add(new ViewState.Floating("-" + num(amount), Palette.DAMAGE, false, i / 2));
            frame();
        }
        view.resetEffects();
        render();
    }

    private void animateHeal(double amount) {
        double before = state.player().hp() - amount;
        for (int i = 0; i < 6; i++) {
            view.playerFlash = i % 2 == 0 ? Palette.HEAL : null;
            view.playerHpShown = before + amount * (i + 1) / 6;
            view.floating.clear();
            view.floating.add(new ViewState.Floating("+" + num(amount), Palette.HEAL, false, i / 2));
            frame();
        }
        view.resetEffects();
        render();
    }

    private void animateTerrain() {
        for (int i = 0; i < 4; i++) {
            view.terrainFlash = i % 2 == 0 ? TextColor.ANSI.WHITE_BRIGHT : null;
            frame();
        }
        view.resetEffects();
        render();
    }

    private void animateEnemyAttack() {
        pause(250);
        int[] offsets = {-2, -4, -2, 0};
        for (int i = 0; i < offsets.length; i++) {
            view.enemyOffsetX = offsets[i];
            view.spriteAnimation = cardistry.ui.sprite.Sprite.ATTACK;
            view.spriteFrame = i;
            frame();
        }
        view.resetEffects();
    }

    private void animateEnemyDeath() {
        int death = enemyFrames(cardistry.ui.sprite.Sprite.DEATH);
        for (int i = 0; i < death * 3; i++) {
            view.spriteAnimation = cardistry.ui.sprite.Sprite.DEATH;
            view.spriteFrame = i / 3;
            frame();
        }
        for (int i = 0; i < 6; i++) {
            view.enemyHidden = i % 2 == 1;
            view.enemyDim = true;
            frame();
        }
        view.resetEffects();
        view.enemyHidden = true;
        render();
        pause(300);
        view.resetEffects();
    }

    private void animateEnemyArrival() {
        for (int offset = 12; offset >= 0; offset -= 3) {
            view.enemyOffsetX = offset;
            frame();
        }
        view.resetEffects();
        render();
    }

    private void animateDraw() {
        view.selected = state.hand().size() - 1;
        for (int i = 0; i < 4; i++) {
            view.highlightSlot = i % 2 == 0 ? state.hand().size() - 1 : -1;
            frame();
        }
        view.resetEffects();
        render();
    }

    /**
     * Nombre de frames de l'animation de l'adversaire en cours : celles de ses images dans la fenêtre
     * (0 s'il a des images mais pas cette animation), sinon celles de son sprite ASCII.
     */
    private int enemyFrames(String animation) {
        if (images != null && state != null && state.hasEnemy()) {
            String sprite = state.currentEnemy().spriteId();
            if (cardistry.ui.window.Images.enemy(sprite, cardistry.ui.sprite.Sprite.IDLE, 0).isPresent()) {
                return cardistry.ui.window.Images.frames(sprite, animation);
            }
        }
        return currentSprite().frames(animation).size();
    }

    private cardistry.ui.sprite.Sprite currentSprite() {
        return state != null && state.hasEnemy()
                ? cardistry.ui.sprite.Sprites.of(state.currentEnemy())
                : cardistry.ui.sprite.Sprite.placeholder("?");
    }

    /** Durée d'une frame idle. */
    static final int IDLE_MS = 500;

    /** Dessine une image d'animation puis attend. */
    private void frame() {
        render();
        pause(FRAME_MS);
    }

    private void pause(int millis) {
        if (fast || page != Page.COMBAT) {
            return;
        }
        sleep(millis);
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ===================== Écran et clavier =====================

    /** Active la console de développement (F12), lancée avec {@code --debug}. */
    public void enableDevConsole() {
        if (dev == null) {
            dev = new DevOverlay();
        }
    }

    /** Partie visée par les commandes de la console (sans effet si elle n'est pas activée). */
    public void attachDevRun(cardistry.run.Run run) {
        if (dev != null) {
            dev.attach(run);
        }
    }

    private void render() {
        try {
            screen.doResizeIfNecessary();
            TerminalSize size = screen.getTerminalSize();
            screen.clear();
            if (images != null) {
                images.begin();
            }
            Painter painter = new Painter(screen.newTextGraphics(), size, ascii, images);
            if (size.getColumns() < MenuScreens.MIN_COLS || size.getRows() < MenuScreens.MIN_ROWS) {
                MenuScreens.tooSmall(painter);
            } else {
                switch (page) {
                    case TITLE -> MenuScreens.title(painter, seed, titleCodes, titleSelected);
                    case HERO -> MenuScreens.heroes(painter, heroes, heroSelected, seed);
                    case RULES -> MenuScreens.rules(painter, state);
                    case COMBAT -> {
                        if (state != null && images != null) {
                            CombatWindowScreen.draw(painter, state, view, seed);
                        } else if (state != null) {
                            CombatScreen.draw(painter, state, view, seed);
                        }
                    }
                    case INVENTORY -> ItemScreens.inventory(painter, state);
                    case REWARD -> ItemScreens.reward(painter, state, rewardOptions, rewardSelected, seed, view.pageTick);
                    case REPLACE -> ItemScreens.replace(painter, state, replaceTarget, replaceSelected, seed,
                            view.pageTick);
                    case STORY -> StoryScreen.choices(painter, state, story, storySelected, seed);
                    case STORY_RESULT -> StoryScreen.result(painter, state, storyResult, seed);
                    case PAUSE -> MenuScreens.pause(painter, menuSelected, seed);
                    case OPTIONS -> MenuScreens.options(painter, options.settings(), optionsSelected, seed);
                    case SHOP -> ShopScreen.draw(painter, state, shop, shopSelected, removalPrice, seed, view.toast,
                            shopTick, view.pageTick);
                    case DISCARD -> ItemScreens.discard(painter, state, discardSelected, seed);
                    case CARD_CHOICE -> ItemScreens.cardChoice(painter, state, cardOptions, cardSelected,
                            showMetaPhrase, seed, view.pageTick);
                    case GAME_OVER -> MenuScreens.gameOver(painter, state, seed, unlockedSecrets);
                    case CARD_REWARD -> ItemScreens.cardReward(painter, state, cardOptions, cardSelected, seed,
                            view.pageTick);
                    case DECK_REMOVE -> ItemScreens.deckRemove(painter, state, deckSelected, seed);
                    case REST -> MenuScreens.rest(painter, state, restOptions, restSelected, seed);
                    case STAT_POINTS -> MenuScreens.statPoints(painter, state, statSelected, seed);
                    case ALTAR -> AltarScreen.draw(painter, state, altarStat, altarButton, seed);
                    case MAP -> MapScreen.draw(painter, mapView, mapSelected, seed, view.toast, view.idleFrame);
                    case DECK -> DeckScreen.draw(painter, state, deckScroll, deckPiles, seed);
                    case STATS -> StatsScreen.draw(painter, state, statsOfEnemy, seed);
                    case CARD_DETAIL -> CardDetailScreen.draw(painter, state, detailCard, detailInCombat, seed);
                    case SPRITES -> SpriteViewerScreen.draw(painter, catalog, spriteIndex, spriteAnimation,
                            spriteAnimation.equals(cardistry.ui.sprite.Sprite.IDLE) ? view.idleFrame + spriteFrame
                                    : spriteFrame, spriteFlash, spriteHidden);
                }
                if (view.confirmingQuit) {
                    painter.text(1, size.getRows() - 1, "─".repeat(size.getColumns() - 2), Palette.FRAME);
                    painter.text(2, size.getRows() - 1, " Quitter la partie et revenir au titre ? [O]ui / [N]on ", Palette.WARNING, true);
                }
            }
            if (dev != null && dev.isOpen()) {
                dev.draw(painter);
            }
            if (images != null) {
                images.commit(); // avant refresh : la fenêtre lit le calque au flush
            }
            screen.refresh();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Durée d'une page des textes trop longs. */
    static final int PAGE_MS = 2500;
    /** Pages dont les descriptions trop longues défilent par pages. */
    private static final java.util.Set<Page> PAGED_PAGES = java.util.EnumSet.of(Page.COMBAT, Page.REWARD,
            Page.CARD_REWARD, Page.CARD_CHOICE, Page.SHOP, Page.REPLACE);

    /** Durée d'un pas de la fumée du marchand. */
    static final int SMOKE_MS = 110;
    private int shopTick = 0;

    /** Attend une touche ; redessine si le terminal change de taille. */
    private KeyStroke readKey() {
        long nextIdle = System.currentTimeMillis() + IDLE_MS;
        long nextSmoke = System.currentTimeMillis() + SMOKE_MS;
        long nextPage = System.currentTimeMillis() + PAGE_MS;
        try {
            while (true) {
                KeyStroke key = screen.pollInput();
                if (key != null) {
                    if (key.getKeyType() == KeyType.EOF) {
                        throw new QuitGameException();
                    }
                    if (dev != null && (DevOverlay.isToggle(key) || dev.isOpen())) {
                        if (DevOverlay.isToggle(key)) {
                            dev.toggle(); // n'importe où : la console, puis retour à l'écran en cours
                        } else {
                            dev.handle(key);
                        }
                        render();
                        continue;
                    }
                    keySound(key);
                    view.pageTick = 0; // une touche : les textes longs reprennent au début
                    if (opensDeck(key)) {
                        showDeck(); // n'importe où : le deck, puis retour à l'écran en cours
                        continue;
                    }
                    if (opensStats(key, 'c')) {
                        showStats(false); // fiche personnage, n'importe où pendant une partie
                        continue;
                    }
                    if (opensStats(key, 'a') && state.hasEnemy() && state.currentEnemy().isAlive()
                            && (page == Page.COMBAT || page == Page.CARD_CHOICE)) {
                        showStats(true); // fiche de l'adversaire, en combat
                        continue;
                    }
                    return key;
                }
                if (screen.doResizeIfNecessary() != null || dev != null && dev.needsRedraw()) {
                    render(); // nouvelle ligne du journal : la console suit en direct
                }
                if (!fast && PAGED_PAGES.contains(page) && System.currentTimeMillis() >= nextPage) {
                    view.pageTick++; // descriptions trop longues : page suivante
                    nextPage = System.currentTimeMillis() + PAGE_MS;
                    render();
                }
                if (!fast && idleAnimated() && System.currentTimeMillis() >= nextIdle) {
                    view.idleFrame++;
                    nextIdle = System.currentTimeMillis() + IDLE_MS;
                    render();
                }
                if (!fast && page == Page.SHOP && System.currentTimeMillis() >= nextSmoke) {
                    shopTick++; // le marchand fume
                    nextSmoke = System.currentTimeMillis() + SMOKE_MS;
                    render();
                }
                sleep(POLL_MS);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Idle animé : écran de combat, adversaire vivant avec plusieurs frames idle. */
    private boolean idleAnimated() {
        if (page == Page.SPRITES || page == Page.MAP && images != null) {
            return true; // carte en pixels : les nœuds accessibles pulsent
        }
        return page == Page.COMBAT && state != null && state.hasEnemy() && state.currentEnemy().isAlive()
                && (currentSprite().idle().size() > 1
                        || images != null && cardistry.ui.window.Images.idleFrames(state.currentEnemy().spriteId()) > 1
                        || playerFrames(cardistry.ui.sprite.Sprite.IDLE) > 1
                        || images != null && state.familiar().isPresent());
    }

    private static boolean isChar(KeyStroke key, char expected) {
        return key.getKeyType() == KeyType.Character
                && Character.toLowerCase(key.getCharacter()) == expected;
    }

    /** Ctrl+lettre (Lanterna capte Ctrl+Z comme une touche : le jeu n'est pas suspendu). */
    private static boolean isCtrl(KeyStroke key, char letter) {
        return key.getKeyType() == KeyType.Character && key.isCtrlDown()
                && Character.toLowerCase(key.getCharacter()) == letter;
    }

    private static boolean isQuit(KeyStroke key) {
        return key.getKeyType() == KeyType.Escape || isChar(key, 'q');
    }
}
