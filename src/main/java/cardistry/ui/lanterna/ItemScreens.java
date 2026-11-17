package cardistry.ui.lanterna;

import cardistry.core.GameState;
import cardistry.items.Collectible;
import cardistry.items.Item;
import cardistry.items.SetBonus;
import cardistry.items.Tag;
import cardistry.items.Trinket;
import cardistry.ui.Format;
import cardistry.ui.ItemKeys;
import java.util.List;
import java.util.stream.Collectors;

/** Écrans de récompense et d'inventaire. */
final class ItemScreens {

    private static final int CARD_HEIGHT = 17;
    private static final int MAX_CARD_WIDTH = 26;

    private ItemScreens() {
    }

    /** Choix d'une récompense : les propositions côte à côte, la sélectionnée en double trait. */
    static void reward(Painter p, GameState state, List<Collectible> options, int selected, long seed) {
        reward(p, state, options, selected, seed, 0);
    }

    /** {@code tick} : page des descriptions trop longues pour leur carte. */
    static void reward(Painter p, GameState state, List<Collectible> options, int selected, long seed, int tick) {
        p.frame("Récompense", inventorySummary(state), "←/→ choisir · Entrée prendre · S refuser · Q quitter");
        p.centered(2, "Adversaire vaincu ! Choisis une récompense.", Palette.ACCENT, true);
        drawCards(p, options, selected, 4, tick);
        Collectible chosen = options.get(selected);
        if (isFull(state, chosen)) {
            p.centered(5 + cardHeight(p), Painter.fit("Tes " + (chosen instanceof Trinket ? "trinkets" : "objets")
                    + " sont au complet : la prendre remplacera un de tes items.", p.cols() - 4), Palette.WARNING, false);
        }
    }

    /** Choix de l'item à remplacer quand l'inventaire est plein. */
    static void replace(Painter p, GameState state, Collectible incoming, int selected, long seed) {
        replace(p, state, incoming, selected, seed, 0);
    }

    static void replace(Painter p, GameState state, Collectible incoming, int selected, long seed, int tick) {
        List<? extends Collectible> current = incoming instanceof Trinket ? state.trinkets() : state.items();
        p.frame("Remplacer", "Seed " + seed, "←/→ ou 1-" + current.size() + " choisir · Entrée remplacer · Échap revenir");
        p.centered(2, "Que remplace " + incoming.name() + " ?", Palette.ACCENT, true);
        drawCards(p, List.copyOf(current), selected, 4, tick);
        String hint = selected < 0 || selected >= current.size()
                ? "Choisis d'abord ce que tu abandonnes (←/→ ou son numéro). Échap : tu gardes tout."
                : "Entrée : " + incoming.name() + " prend la place de " + current.get(selected).name() + ".";
        p.centered(5 + cardHeight(p), Painter.fit(hint, p.cols() - 4), Palette.WARNING, false);
    }

    static final String META_PHRASE = "Un jeu de cartes dans ton jeu de cartes.";

    /** Pioche au choix (Paquet de cartes) : les propositions côte à côte et l'effet de la sélectionnée. */
    static void cardChoice(Painter p, GameState state, List<cardistry.model.card.Card> options, int selected,
            boolean showMeta, long seed) {
        cardChoice(p, state, options, selected, showMeta, seed, 0);
    }

    static void cardChoice(Painter p, GameState state, List<cardistry.model.card.Card> options, int selected,
            boolean showMeta, long seed, int tick) {
        p.frame("Pioche", "Ta main " + state.handCount() + "/" + state.handSize(),
                "1-" + options.size() + " ou ←/→ + Entrée garder · les autres sont défaussées");
        p.centered(2, "Choisis la carte à garder", Palette.ACCENT, true);
        if (showMeta) {
            p.centered(3, "« " + META_PHRASE + " »", Palette.DIM, false);
        }
        int width = Math.min(18, (p.cols() - 4 - 2 * (options.size() - 1)) / options.size());
        int total = options.size() * width + 2 * (options.size() - 1);
        int left = Math.max(2, (p.cols() - total) / 2);
        for (int i = 0; i < options.size(); i++) {
            boolean isSelected = i == selected;
            card(p, left + i * (width + 2), isSelected ? 6 : 7, width, i + 1, options.get(i), isSelected);
        }
        detail(p, 15, CombatScreen.describe(state, options.get(selected)), tick);
    }

    /** Description sur plusieurs lignes à partir de {@code top}, jusqu'au bas du cadre ; au-delà, par pages. */
    private static void detail(Painter p, int top, String text, int tick) {
        int rows = Math.max(1, p.rows() - 1 - top);
        List<String> lines = Painter.pagedLines(Format.wrap(text, p.cols() - 6), rows, tick);
        for (int i = 0; i < lines.size(); i++) {
            p.text(2, top + i, lines.get(i), Palette.TEXT);
        }
    }

    /** Hauteur des cartes d'objets : grandit avec l'écran pour que les longues descriptions tiennent. */
    static int cardHeight(Painter p) {
        return Math.max(CARD_HEIGHT, p.rows() - 7);
    }

    /** Récompense de carte : 1 carte parmi les propositions à ajouter au deck, ou passer. */
    static void cardReward(Painter p, GameState state, List<cardistry.model.card.Card> options, int selected,
            long seed) {
        cardReward(p, state, options, selected, seed, 0);
    }

    static void cardReward(Painter p, GameState state, List<cardistry.model.card.Card> options, int selected,
            long seed, int tick) {
        p.frame("Nouvelle carte", "Deck " + state.deck().size() + " cartes · Seed " + seed,
                "1-" + options.size() + " ou ←/→ + Entrée ajouter au deck · S passer");
        p.centered(2, "Ajoute une carte à ton deck", Palette.ACCENT, true);
        int width = Math.min(18, (p.cols() - 4 - 2 * (options.size() - 1)) / options.size());
        int total = options.size() * width + 2 * (options.size() - 1);
        int left = Math.max(2, (p.cols() - total) / 2);
        for (int i = 0; i < options.size(); i++) {
            boolean isSelected = i == selected;
            card(p, left + i * (width + 2), isSelected ? 5 : 6, width, i + 1, options.get(i), isSelected);
        }
        // Hors combat : valeurs de base (pas contre l'adversaire qu'on vient de battre).
        detail(p, 14, cardistry.ui.CardText.summary(options.get(selected)), tick);
    }

    /** Carte à choisir : illustrée dans la fenêtre (comme en combat), en texte dans le terminal. */
    private static void card(Painter p, int col, int row, int width, int number, cardistry.model.card.Card card,
            boolean selected) {
        if (p.hasImages()) {
            CombatWindowScreen.card(p, col, row, width, 8, number, card, selected, false);
        } else {
            CombatScreen.drawCard(p, col, row, width, number, card, selected, false);
        }
    }

    /** Colonnes de la liste du deck (« Faire le tri »). */
    static final int DECK_COLUMNS = 2;

    /** Sieste « Faire le tri » : le deck, le joueur choisit la carte à retirer. */
    static void deckRemove(Painter p, GameState state, int selected, long seed) {
        p.frame("Faire le tri", "Deck " + state.deck().size() + " cartes · Seed " + seed,
                "←/→/↑/↓ choisir · Entrée retirer · Échap revenir");
        p.centered(2, "Choisis la carte à retirer de ton deck", Palette.ACCENT, true);
        int columns = DECK_COLUMNS;
        int width = (p.cols() - 6) / columns;
        for (int i = 0; i < state.deck().size(); i++) {
            cardistry.model.card.Card card = state.deck().get(i);
            int col = 3 + (i % columns) * width;
            int row = 4 + i / columns;
            if (row >= p.rows() - 2) {
                break;
            }
            boolean isSelected = i == selected;
            p.text(col, row, isSelected ? "▶" : " ", Palette.ACCENT, true);
            p.text(col + 2, row, Painter.fit(card.name() + " (" + Format.cardKind(card) + ")", width - 3),
                    Palette.element(card.element()), isSelected);
        }
    }

    /** Trop de trinkets pour les emplacements : liste, le joueur en choisit un à jeter. */
    static void discard(Painter p, GameState state, int selected, long seed) {
        p.frame("Jeter un trinket", "Seed " + seed, "↑/↓ choisir · Entrée jeter");
        p.centered(2, "Tu as " + state.trinkets().size() + " trinkets pour " + state.trinketSlots()
                + " emplacements : choisis-en un à jeter.", Palette.WARNING, true);
        int row = 4;
        for (int i = 0; i < state.trinkets().size() && row < p.rows() - 2; i++) {
            Trinket trinket = state.trinkets().get(i);
            boolean isSelected = i == selected;
            p.text(3, row, isSelected ? "▶" : " ", Palette.ACCENT, true);
            p.text(5, row, trinket.name(), Palette.rarity(trinket.rarity()), isSelected);
            p.text(6 + trinket.name().length(), row, Painter.fit("— " + trinket.description(),
                    Math.max(1, p.cols() - 9 - trinket.name().length())), isSelected ? Palette.TEXT : Palette.DIM);
            row += 2;
        }
    }

    /** Inventaire détaillé. */
    static void inventory(Painter p, GameState state) {
        p.frame("Inventaire", "", "Appuie sur une touche pour revenir");
        int row = 2;
        if (state.hero().isPresent()) {
            cardistry.hero.Hero hero = state.hero().get();
            p.text(3, row, hero.name() + " · " + hero.abilityName(), Palette.SECRET, true);
            for (String text : cardistry.ui.Format.wrap(hero.description(), p.cols() - 8)) {
                p.text(5, ++row, text, Palette.TEXT);
            }
            row += 2;
        }
        p.text(3, row++, "Objets " + state.items().size() + "/" + state.itemSlots()
                + "  (action gratuite : " + ItemKeys.label(state.itemSlots()) + " en combat)", Palette.TEXT, true);
        for (int i = 0; i < state.items().size(); i++) {
            Item item = state.items().get(i);
            row = line(p, row, "[" + Character.toUpperCase(ItemKeys.key(i)) + "] ", item, "");
        }
        if (state.items().isEmpty()) {
            p.text(5, row++, "aucun", Palette.DIM);
        }
        row++;
        p.text(3, row++, "Trinkets " + state.trinkets().size() + "/" + state.trinketSlots(), Palette.TEXT, true);
        for (Trinket trinket : state.trinkets()) {
            double potency = state.potency(trinket);
            row = line(p, row, "• ", trinket, potency == 1.0 ? "" : " (effet ×" + cardistry.ui.Format.num(potency) + ")");
        }
        if (state.trinkets().isEmpty()) {
            p.text(5, row++, "aucun", Palette.DIM);
        }
        row++;
        p.text(3, row++, "Sets", Palette.TEXT, true);
        boolean anySet = false;
        for (SetBonus set : state.setBonuses()) {
            int count = state.tagCount(set.tag());
            if (count == 0 || row >= p.rows() - 1) {
                continue;
            }
            anySet = true;
            p.text(5, row++, Painter.fit(setLine(state, set, count), p.cols() - 7),
                    set.reached(count).isEmpty() ? Palette.DIM : Palette.ACCENT);
        }
        if (!anySet) {
            p.text(5, row++, "aucun", Palette.DIM);
        }
        row++;
        p.text(3, row++, "Effets en cours", Palette.TEXT, true);
        for (cardistry.core.TemporaryEffect effect : state.effects()) {
            if (row < p.rows() - 1) {
                p.text(5, row++, Painter.fit("• " + effect.describe(), p.cols() - 7),
                        effect.isActive() ? Palette.INFO : Palette.DIM);
            }
        }
        if (state.effects().isEmpty()) {
            p.text(5, row++, "aucun", Palette.DIM);
        }
        if (state.usesDeck() && row + 1 < p.rows() - 1) {
            row++;
            p.text(3, row++, "Deck " + state.deck().size() + " cartes", Palette.TEXT, true);
            p.text(5, row, Painter.fit(deckSummary(state.deck()), p.cols() - 7), Palette.TEXT);
        }
    }

    /** « Boule de feu, Tsunami ×2, Potion… » (dans l'ordre du deck, doublons regroupés). */
    static String deckSummary(List<cardistry.model.card.Card> deck) {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        deck.forEach(c -> counts.merge(c.name(), 1, Integer::sum));
        return String.join(", ", counts.entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getKey() + " ×" + e.getValue() : e.getKey()).toList());
    }

    private static int line(Painter p, int row, String prefix, Collectible c, String suffix) {
        if (row >= p.rows() - 1) {
            return row;
        }
        p.text(5, row, prefix, Palette.DIM);
        int col = 5 + prefix.length();
        col += p.icon(col, row, c);
        p.text(col, row, c.name(), Palette.rarity(c.rarity()), true);
        col += c.name().length();
        String rest = "  " + c.rarity().displayName() + " · " + tags(c) + " — " + c.description() + suffix;
        List<String> lines = Format.wrap(rest.strip(), Math.max(10, p.cols() - col - 3));
        p.text(col + 2, row, lines.get(0), Palette.TEXT);
        for (int i = 1; i < lines.size() && row + i < p.rows() - 1; i++) {
            p.text(col + 2, row + i, lines.get(i), Palette.TEXT);
        }
        return row + lines.size();
    }

    private static void drawCards(Painter p, List<Collectible> cards, int selected, int top, int tick) {
        int count = cards.size();
        int width = Math.min(MAX_CARD_WIDTH, (p.cols() - 4 - 2 * (count - 1)) / Math.max(1, count));
        int total = count * width + 2 * (count - 1);
        int left = Math.max(2, (p.cols() - total) / 2);
        for (int i = 0; i < count; i++) {
            boolean isSelected = i == selected;
            drawCard(p, left + i * (width + 2), isSelected ? top - 1 : top, width, cards.get(i), isSelected,
                    isSelected ? tick : 0);
        }
    }

    /**
     * Carte d'objet ou de trinket. La description passe avant la citation (montrée seulement si elle
     * tient en entier) ; une description encore trop longue défile par pages ({@code tick}).
     */
    private static void drawCard(Painter p, int col, int row, int width, Collectible c, boolean selected, int tick) {
        int inner = width - 2;
        int height = cardHeight(p);
        p.box(col, row, width, height, Palette.rarity(c.rarity()), selected);
        int last = row + height - 3; // dernière ligne de texte libre (la suivante = tags)
        int r = row + 1;
        int nameEnd = row + 4;
        if (p.hasImages()) {
            // Fenêtre : icône encadrée à la couleur de rareté, catégorie et rareté à côté.
            CombatWindowScreen.slot(p, col + 1, row + 1, c);
            int side = col + 2 + CombatWindowScreen.SLOT_COLS;
            p.text(side, r++, Painter.fit(c.kindName(), col + width - 1 - side), Palette.rarity(c.rarity()));
            p.text(side, r++, Painter.fit(c.rarity().displayName(), col + width - 1 - side), Palette.rarity(c.rarity()));
            nameEnd = row + 5;
        } else {
            p.text(col + 1, r++, Painter.fit(c.kindName() + " · " + c.rarity().displayName(), inner),
                    Palette.rarity(c.rarity()));
        }
        for (String line : Format.wrap(c.name(), inner)) {
            if (r < nameEnd) {
                p.text(col + 1, r++, line, Palette.TEXT, true);
            }
        }
        r = Math.max(r, nameEnd);
        List<String> description = Format.wrap(c.description(), inner);
        for (String line : Painter.pagedLines(description, last - r + 1, tick)) {
            p.text(col + 1, r++, line, Palette.TEXT);
        }
        if (!c.flavor().isEmpty()) {
            List<String> flavor = Format.wrap("« " + c.flavor() + " »", inner);
            if (r + flavor.size() <= last) { // la citation seulement si elle tient en entier
                r++;
                for (String line : flavor) {
                    p.text(col + 1, r++, line, Palette.DIM);
                }
            }
        }
        p.text(col + 1, row + height - 2, Painter.fit(tags(c), inner), Palette.DIM);
    }

    /** « EPFL « Cursus » (3) ×2 : palier 3 · prochain 4 : +25 PV max et +15 % dégâts élémentaires ». */
    static String setLine(GameState state, SetBonus set, int count) {
        StringBuilder line = new StringBuilder("• ").append(set.displayName()).append(" (").append(count).append(")");
        double potency = state.setPotency(set.tag());
        if (potency != 1.0) {
            line.append(" ×").append(cardistry.ui.Format.num(potency));
        }
        List<SetBonus.Tier> reached = set.reached(count);
        line.append(" : ").append(reached.isEmpty() ? "aucun palier" : "palier " + reached.get(reached.size() - 1).count());
        set.next(count).ifPresentOrElse(
                next -> line.append(" · prochain ").append(next.count()).append(" : ").append(next.description()),
                () -> line.append(" · complet"));
        return line.toString();
    }

    static String tags(Collectible c) {
        return c.tags().stream().sorted().map(Tag::displayName).collect(Collectors.joining(", "));
    }

    static boolean isFull(GameState state, Collectible c) {
        return c instanceof Trinket trinket
                ? !state.hasRoomFor(trinket)
                : state.items().size() >= state.itemSlots();
    }

    private static String inventorySummary(GameState state) {
        return "Objets " + state.items().size() + "/" + state.itemSlots()
                + " · Trinkets " + state.trinkets().size() + "/" + state.trinketSlots();
    }
}
