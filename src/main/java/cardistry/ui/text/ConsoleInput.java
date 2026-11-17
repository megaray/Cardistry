package cardistry.ui.text;

import cardistry.core.Action;
import cardistry.core.GameState;
import cardistry.core.RewardChoice;
import cardistry.items.Collectible;
import cardistry.items.Trinket;
import cardistry.run.Secret;
import cardistry.ui.Input;
import cardistry.ui.ItemKeys;
import cardistry.ui.TitleChoice;
import java.util.List;
import java.util.Optional;

public final class ConsoleInput implements Input {

    private final Console console;

    public ConsoleInput(Console console) {
        this.console = console;
    }

    /** Commande d'annulation du dernier tour (trinket Ctrl+Z) : Ctrl+Z est pris par le terminal. */
    static final String UNDO = "z";

    /** Code secret de l'écran titre (CLAUDE.md). En mode texte, Ctrl+Z n'est pas lisible. */
    static final String CLAUDE_CODE = "claude";

    @Override
    public TitleChoice title(long seed) {
        console.println("Bienvenue dans " + Ansi.yellow("Cardistry") + " !");
        console.println("Seed : " + seed);
        console.println();
        List<Secret> codes = new java.util.ArrayList<>();
        while (true) {
            console.println("(Entrée : règles puis partie · s : passer les règles · options : réglages du son)");
            String line = console.readLine().trim();
            if (line.equalsIgnoreCase(OPTIONS_COMMAND)) {
                optionsMenu();
                continue;
            }
            if (line.equalsIgnoreCase(CLAUDE_CODE)) {
                if (!codes.contains(Secret.CLAUDE_MD)) {
                    codes.add(Secret.CLAUDE_MD);
                }
                console.println(Ansi.MAGENTA + "Code accepté : " + Secret.CLAUDE_MD.displayName()
                        + " équipé pour cette partie." + Ansi.END_COLOR);
                continue;
            }
            return new TitleChoice(!line.equalsIgnoreCase("s"), codes);
        }
    }

    @Override
    public java.util.Optional<cardistry.hero.Hero> chooseHero(List<cardistry.hero.Hero> heroes) {
        console.println();
        console.println(Ansi.yellow("Choisis ton personnage"));
        for (int i = 0; i < heroes.size(); i++) {
            cardistry.hero.Hero hero = heroes.get(i);
            console.println("[" + (i + 1) + "] " + hero.name() + " · "
                    + cardistry.ui.Format.num(GameState.newGame(hero).maxHp()) + " PV · " + hero.abilityName()
                    + " : " + hero.description());
        }
        return java.util.Optional.of(heroes.get(readNumber("Ton choix ? ", 1, heroes.size()) - 1));
    }

    /** Commande de l'écran titre qui ouvre les réglages. */
    static final String OPTIONS_COMMAND = "options";

    private cardistry.ui.OptionsController options = cardistry.ui.OptionsController.inMemory(
            cardistry.ui.Settings.defaults());

    /** Réglages modifiables depuis la commande « options » de l'écran titre. */
    public void setOptions(cardistry.ui.OptionsController options) {
        this.options = options;
    }

    /** Menu des réglages : 1 musique, 2 effets (0 à 10), 3 muet, 0 retour. Appliqués tout de suite. */
    private void optionsMenu() {
        while (true) {
            cardistry.ui.Settings s = options.settings();
            console.println();
            console.println(Ansi.yellow("Options"));
            console.println("[1] Musique : " + s.music() + "/10");
            console.println("[2] Effets : " + s.effects() + "/10");
            console.println("[3] Muet : " + (s.muted() ? "oui" : "non"));
            console.println("[0] Retour");
            switch (readNumber("Ton choix ? ", 0, 3)) {
                case 1 -> options.apply(s.withMusic(readNumber("Volume de la musique (0 à 10) ? ", 0, 10)));
                case 2 -> options.apply(s.withEffects(readNumber("Volume des effets (0 à 10) ? ", 0, 10)));
                case 3 -> options.apply(s.withMuted(!s.muted()));
                default -> {
                    return;
                }
            }
        }
    }

    @Override
    public void waitForContinue() {
        console.println("(Appuie sur Entrée)");
        console.readLine();
    }

    /** 0 = piocher, n = jouer la carte n, a/z/e = utiliser un objet. Redemande si la saisie est invalide. */
    @Override
    public Action nextAction(GameState state) {
        while (true) {
            current = state;
            console.print("Que veux-tu jouer ? ");
            String line = read("Que veux-tu jouer ? ");
            if (line.equalsIgnoreCase(UNDO) && state.hasTrinket(cardistry.items.ItemRegistry.CTRL_Z)) {
                return new Action.Undo();
            }
            if (line.equalsIgnoreCase("f")) {
                return new Action.Smoke();
            }
            java.util.Optional<Integer> detail = detailIndex(line);
            if (detail.isPresent()) {
                if (detail.get() < state.hand().size()) {
                    printDetail(state, state.hand().get(detail.get()), true);
                } else {
                    console.println("Pas de carte à ce numéro.");
                }
                continue;
            }
            if (line.equalsIgnoreCase("g")) {
                return new Action.RollCigarette();
            }
            if (line.length() == 1 && ItemKeys.slot(line.charAt(0), state.itemSlots()) >= 0) {
                return new Action.UseItem(ItemKeys.slot(line.charAt(0), state.itemSlots()));
            }
            try {
                int choice = Integer.parseInt(line);
                return choice == 0 ? new Action.DrawCard() : new Action.PlayCard(choice - 1);
            } catch (NumberFormatException e) {
                console.println("Entre un nombre entre 0 et " + Math.max(state.handSize(), state.hand().size())
                        + (state.items().isEmpty() ? "" : ", ou la lettre d'un objet"));
            }
        }
    }

    /** 1..n = prendre, 0 = refuser ; si l'inventaire est plein, demande quoi remplacer. */
    @Override
    public RewardChoice chooseReward(GameState state, List<Collectible> options) {
        current = state;
        while (true) {
            int choice = readNumber("Ton choix (0 pour refuser) ? ", 0, options.size());
            if (choice == 0) {
                return new RewardChoice.Skip();
            }
            Collectible taken = options.get(choice - 1);
            List<? extends Collectible> owned = taken instanceof Trinket ? state.trinkets() : state.items();
            int slots = taken instanceof Trinket  ? state.trinketSlots() : state.itemSlots();
            boolean room = taken instanceof Trinket trinket ? state.hasRoomFor(trinket) : owned.size() < slots;
            if (room) {
                return RewardChoice.Take.into(choice - 1);
            }
            console.println("Inventaire plein. Que remplace " + taken.name() + " ?");
            for (int i = 0; i < owned.size(); i++) {
                console.println("[" + (i + 1) + "] " + owned.get(i).name() + " : " + owned.get(i).description());
            }
            int replaced = readNumber("Numéro (0 pour revenir) ? ", 0, owned.size());
            if (replaced > 0) {
                return new RewardChoice.Take(choice - 1, replaced - 1);
            }
        }
    }

    /** 1..n : nœud de la carte (la liste est affichée par le rendu de la carte). */
    @Override
    public int chooseNode(cardistry.run.map.MapView view) {
        current = view.state();
        if (view.choices().size() == 1) {
            console.println("(Appuie sur Entrée pour y aller)");
            console.readLine();
            return 0;
        }
        return readNumber("Où vas-tu ? ", 1, view.choices().size()) - 1;
    }

    /** 1..n = ajouter la carte, 0 = passer (la liste est affichée par le rendu). */
    @Override
    public int chooseCardReward(GameState state, List<cardistry.model.card.Card> options) {
        current = state;
        return readNumber("Carte à ajouter (0 pour passer) ? ", 0, options.size()) - 1;
    }

    @Override
    public int chooseCardToRemove(GameState state) {
        current = state;
        console.println("Ton deck :");
        for (int i = 0; i < state.deck().size(); i++) {
            console.println("[" + (i + 1) + "] " + TextRenderer.describe(state.deck().get(i)));
        }
        return readNumber("Carte à retirer (0 pour revenir) ? ", 0, state.deck().size()) - 1;
    }

    /**
     * Shop : affiche les articles, puis lit un numéro (acheter), r (reroll), t (retirer une carte)
     * ou 0 (partir). Inventaire plein : demande quoi remplacer.
     */
    @Override
    public cardistry.core.shop.ShopAction chooseShopAction(GameState state, cardistry.core.Shop shop, int removalPrice) {
        while (true) {
            current = state;
            printShop(state, shop, removalPrice);
            console.print("Ton choix ? ");
            String line = read("Ton choix ? ").toLowerCase(java.util.Locale.ROOT);
            if (line.equals("0")) {
                return new cardistry.core.shop.ShopAction.Leave();
            }
            if (line.equals("r")) {
                return new cardistry.core.shop.ShopAction.Reroll();
            }
            java.util.Optional<Integer> detail = detailIndex(line);
            if (detail.isPresent()) {
                if (detail.get() < shop.offers().size()
                        && shop.offers().get(detail.get()) instanceof cardistry.core.shop.ShopOffer.CardOffer offer) {
                    printDetail(state, offer.card(), false);
                } else {
                    console.println("Pas de carte à ce numéro.");
                }
                console.print("(Entrée pour revenir au shop) ");
                read("");
                continue;
            }
            if (line.equals("t")) {
                int index = chooseCardToRemove(state);
                if (index >= 0) {
                    return new cardistry.core.shop.ShopAction.RemoveCard(index);
                }
                continue;
            }
            try {
                int choice = Integer.parseInt(line) - 1;
                if (choice >= 0 && choice < shop.offers().size()) {
                    cardistry.core.shop.ShopAction buy = buy(state, shop, choice);
                    if (buy != null) {
                        return buy;
                    }
                    continue;
                }
            } catch (NumberFormatException e) {
                // redemande
            }
            console.println("Entre un numéro d'article, r, t ou 0");
        }
    }

    private cardistry.core.shop.ShopAction buy(GameState state, cardistry.core.Shop shop, int index) {
        if (shop.offers().get(index) instanceof cardistry.core.shop.ShopOffer.CollectibleOffer c && !shop.isSold(index)
                && state.choomies() >= c.price()) {
            List<? extends Collectible> owned = c.collectible() instanceof Trinket ? state.trinkets() : state.items();
            int slots = c.collectible() instanceof Trinket ? state.trinketSlots() : state.itemSlots();
            boolean room = c.collectible() instanceof Trinket trinket ? state.hasRoomFor(trinket) : owned.size() < slots;
            if (!room) {
                console.println("Inventaire plein. Que remplace " + c.collectible().name() + " ?");
                for (int i = 0; i < owned.size(); i++) {
                    console.println("[" + (i + 1) + "] " + owned.get(i).name() + " : " + owned.get(i).description());
                }
                int replaced = readNumber("Numéro (0 pour revenir) ? ", 0, owned.size());
                return replaced == 0 ? null : new cardistry.core.shop.ShopAction.Buy(index, replaced - 1);
            }
        }
        return cardistry.core.shop.ShopAction.Buy.of(index);
    }

    private void printShop(GameState state, cardistry.core.Shop shop, int removalPrice) {
        console.println();
        console.println(Ansi.yellow("SHOP") + " · " + state.choomies() + " Choomies");
        console.println("« Entre, entre ! Tout est à vendre... enfin, presque. »");
        String previous = "";
        for (int i = 0; i < shop.offers().size(); i++) {
            cardistry.core.shop.ShopOffer offer = shop.offers().get(i);
            String title = offer instanceof cardistry.core.shop.ShopOffer.CardOffer ? "Cartes :"
                    : ((cardistry.core.shop.ShopOffer.CollectibleOffer) offer).collectible() instanceof Trinket
                    ? "Trinkets :" : "Objets :";
            if (!title.equals(previous)) {
                console.println(title);
                previous = title;
            }
            String text = switch (offer) {
                case cardistry.core.shop.ShopOffer.CardOffer card -> TextRenderer.describe(card.card());
                case cardistry.core.shop.ShopOffer.CollectibleOffer c -> c.collectible().name() + " ("
                        + c.collectible().rarity().displayName() + ") : " + c.collectible().description();
            };
            String price = shop.isSold(i) ? "vendu"
                    : offer.price() + " Choomies" + (state.choomies() < offer.price() ? " (trop cher)" : "");
            console.println("  [" + (i + 1) + "] " + text + " — " + price);
        }
        console.println("  r) Reroll : " + shop.rerollPrice() + "   t) Retirer une carte : " + removalPrice
                + " (deck " + state.deck().size() + ")   0) Partir");
        console.println("  v N) Détail de la carte N   d) Ton deck");
    }

    /** Événement : le texte, les choix numérotés (indisponibles marqués), puis le numéro choisi. */
    @Override
    public int chooseStory(GameState state, cardistry.story.Story story) {
        current = state;
        console.println();
        console.println(Ansi.yellow("? " + story.title() + " ?"));
        console.println(story.text());
        for (int i = 0; i < story.choices().size(); i++) {
            cardistry.story.StoryChoice choice = story.choices().get(i);
            boolean available = choice.isAvailable(state);
            String detail = available ? choice.hint() : "indisponible : " + choice.requirement();
            console.println("[" + (i + 1) + "] " + choice.label() + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
        while (true) {
            int index = readNumber("Ton choix ? ", 1, story.choices().size()) - 1;
            if (story.choices().get(index).isAvailable(state)) {
                return index;
            }
            console.println("Ce choix n'est pas possible : " + story.choices().get(index).requirement());
        }
    }

    @Override
    public cardistry.run.RestOption chooseRest(GameState state, List<cardistry.run.RestOption> options) {
        current = state;
        console.println();
        console.println(Ansi.yellow("Sieste") + " : une pause avant le boss. Choisis :");
        for (int i = 0; i < options.size(); i++) {
            console.println("[" + (i + 1) + "] " + options.get(i).displayName() + " : " + options.get(i).description());
        }
        return options.get(readNumber("Ton choix ? ", 1, options.size()) - 1);
    }

    @Override
    public cardistry.core.stats.StatType chooseStatPoint(GameState state) {
        current = state;
        List<cardistry.core.stats.StatType> stats = cardistry.core.stats.StatType.characterStats();
        console.println();
        console.println(Ansi.yellow("Progression") + " : " + state.statPoints() + " point(s) de stats à placer.");
        for (int i = 0; i < stats.size(); i++) {
            cardistry.core.stats.StatType stat = stats.get(i);
            console.println("[" + (i + 1) + "] " + stat.abbreviation() + " " + cardistry.ui.Format.num(state.playerStats().get(stat))
                    + " · " + stat.displayName() + " : " + stat.effectText());
        }
        return stats.get(readNumber("Stat à augmenter ? ", 1, stats.size()) - 1);
    }

    @Override
    public Optional<cardistry.core.stats.StatType> chooseAltar(GameState state) {
        current = state;
        List<cardistry.core.stats.StatType> stats = cardistry.core.stats.StatType.characterStats();
        console.println();
        console.println("PV:"+ state.player().hp());
        console.println("PV MAX:"+ state.maxHp());
        console.println(Ansi.yellow("Progression") + " : " + state.statPoints() + " point(s) de stats à placer.");
        for (int i = 0; i < stats.size(); i++) {
            cardistry.core.stats.StatType stat = stats.get(i);
            console.println("[" + (i + 1) + "] " + stat.abbreviation() + " " + cardistry.ui.Format.num(state.playerStats().get(stat))
                    + " · " + stat.displayName() + " : " + stat.effectText());
        }
        console.println("[0] Partir");
        int choice=readNumber("Stat à augmenter", 1, stats.size());
        if (choice==0){
            return Optional.empty();
        }
        else{
            return Optional.of(stats.get(choice-1));
        }
    }
    
    private boolean metaPhraseShown = false;
    
    @Override
    public int chooseDrawnCard(GameState state, List<cardistry.model.card.Card> options) {
        current = state;
        console.println();
        console.println("Pioche : choisis la carte à garder (les autres sont défaussées).");
        if (!metaPhraseShown) {
            console.println("« Un jeu de cartes dans ton jeu de cartes. »");
            metaPhraseShown = true;
        }
        for (int i = 0; i < options.size(); i++) {
            console.println("[" + (i + 1) + "] " + TextRenderer.describe(options.get(i)));
        }
        return readNumber("Carte à garder ? ", 1, options.size()) - 1;
    }

    @Override
    public int chooseTrinketToDiscard(GameState state) {
        current = state;
        console.println("Tu as " + state.trinkets().size() + " trinkets pour " + state.trinketSlots()
                + " emplacements : choisis-en un à jeter.");
        for (int i = 0; i < state.trinkets().size(); i++) {
            Trinket trinket = state.trinkets().get(i);
            console.println("[" + (i + 1) + "] " + trinket.name() + " : " + trinket.description());
        }
        return readNumber("Trinket à jeter ? ", 1, state.trinkets().size()) - 1;
    }

    /** Dernier état reçu : la commande « d » affiche son deck, à n'importe quelle saisie. */
    private GameState current;

    /** Ligne saisie ; « d » affiche le deck (pendant une partie) puis redemande. */
    private String read(String prompt) {
        while (true) {
            String line = console.readLine().trim();
            if (line.equalsIgnoreCase(DECK_COMMAND) && current != null && current.usesDeck()) {
                for (String deckLine : cardistry.ui.CardText.deckLines(current.deck())) {
                    console.println(deckLine);
                }
                console.print(prompt);
                continue;
            }
            boolean enemySheet = line.equalsIgnoreCase(ENEMY_SHEET_COMMAND) && current != null && current.hasEnemy()
                    && current.currentEnemy().isAlive();
            if ((line.equalsIgnoreCase(SHEET_COMMAND) || enemySheet) && current != null) {
                printSheet(current, enemySheet);
                console.print(prompt);
                continue;
            }
            return line;
        }
    }

    /** Commandes du mode texte : ta fiche de stats, celle de l'adversaire. */
    static final String SHEET_COMMAND = "c";
    static final String ENEMY_SHEET_COMMAND = "a";

    private void printSheet(GameState state, boolean enemy) {
        console.println("=== Fiche de " + (enemy ? state.currentEnemy().name() : state.playerName()) + " ===");
        for (cardistry.ui.StatSheet.Row row : enemy ? cardistry.ui.StatSheet.enemy(state)
                : cardistry.ui.StatSheet.player(state)) {
            String detail = row.detail();
            console.println(row.head() + " · " + row.stat().displayName() + " : " + row.stat().effectText()
                    + (detail.isEmpty() ? "" : " (" + detail + ")"));
        }
    }

    /** « v 2 » ou « v2 » : index 1 (fiche de la carte n° 2) ; vide sinon. */
    static java.util.Optional<Integer> detailIndex(String line) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)v\\s*(\\d+)").matcher(line.trim());
        return m.matches() ? java.util.Optional.of(Integer.parseInt(m.group(1)) - 1) : java.util.Optional.empty();
    }

    private void printDetail(GameState state, cardistry.model.card.Card card, boolean inCombat) {
        for (cardistry.ui.CardDetail.Line line : cardistry.ui.CardDetail.lines(state, card, inCombat)) {
            console.println(line.kind() == cardistry.ui.CardDetail.Kind.TITLE ? "=== " + line.text() + " ===" : line.text());
        }
    }

    /** Commande du mode texte qui affiche le deck. */
    static final String DECK_COMMAND = "d";

    private int readNumber(String prompt, int min, int max) {
        while (true) {
            console.print(prompt);
            try {
                int value = Integer.parseInt(read(prompt));
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // redemande
            }
            console.println("Entre un nombre entre " + min + " et " + max);
        }
    }
}
