package cardistry.ui.lanterna;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;

/** Primitives de dessin au-dessus de {@link TextGraphics}. Tout ce qui déborde est coupé. */
final class Painter {

    private static final String SINGLE = "┌┐└┘─│";
    private static final String DOUBLE = "╔╗╚╝═║";

    private final TextGraphics g;
    private final TerminalSize size;
    private final boolean ascii;
    private final cardistry.ui.window.ImageLayer images;

    Painter(TextGraphics g, TerminalSize size) {
        this(g, size, false);
    }

    /** @param ascii si {@code true}, tout texte est converti en ASCII pur (voir {@link cardistry.ui.Glyphs}) */
    Painter(TextGraphics g, TerminalSize size, boolean ascii) {
        this(g, size, ascii, null);
    }

    /** @param images calque d'images de la fenêtre ; {@code null} en terminal (pas d'images) */
    Painter(TextGraphics g, TerminalSize size, boolean ascii, cardistry.ui.window.ImageLayer images) {
        this.g = g;
        this.size = size;
        this.ascii = ascii;
        this.images = images;
    }

    /** Largeur (en cellules) d'une icône d'objet ou de trinket. */
    static final int ICON_WIDTH = 2;

    /**
     * Petite icône (une ligne) de l'objet ou du trinket à ({@code col}, {@code row}) : son image, sinon
     * le placeholder de sa catégorie. Rien en terminal.
     *
     * @return colonnes occupées (icône + espace), 0 sans icône
     */
    int icon(int col, int row, cardistry.items.Collectible content) {
        return image(col, row, ICON_WIDTH, 1, CombatWindowScreen.icon(content), null, 1f) ? ICON_WIDTH + 1 : 0;
    }

    /** Retire les images et marques qui débordent sur les lignes {@code row} et suivantes. */
    void hideImagesFrom(int row) {
        if (images != null) {
            images.hideFrom(row);
        }
    }

    /** {@code true} si ce dessin peut afficher des images (fenêtre, images activées). */
    boolean hasImages() {
        return images != null;
    }

    /**
     * Image {@code path} (relative à {@code images/}) dans le rectangle de cellules donné, si les
     * images sont affichées et que le fichier existe.
     *
     * @return {@code true} si l'image sera dessinée (sinon, dessiner le repli ASCII)
     */
    boolean image(int col, int row, int cols, int rows, String path, TextColor tint, float alpha) {
        if (images == null || cardistry.ui.window.Images.get(path).isEmpty()) {
            return false;
        }
        java.awt.Color color = tint == null ? null : cardistry.ui.window.CellPalette.foreground(tint);
        images.add(new cardistry.ui.window.ImageLayer.Placement(col, row, cols, rows, path, color, alpha));
        return true;
    }

    /** Largeur de l'écran en unités du calque (voir {@link cardistry.ui.window.ImageLayer}). */
    int unitWidth() {
        return cols() * cardistry.ui.window.ImageLayer.UNITS_PER_COL;
    }

    int unitHeight() {
        return rows() * cardistry.ui.window.ImageLayer.UNITS_PER_ROW;
    }

    /** Rectangle plein au pixel près, en unités du calque (fenêtre seulement, sinon rien). */
    void fill(int x, int y, int width, int height, TextColor color) {
        if (images != null) {
            images.add(new cardistry.ui.window.ImageLayer.Fill(x, y, width, height,
                    cardistry.ui.window.CellPalette.foreground(color)));
        }
    }

    /** Rectangle plein translucide ({@code alpha} de 0 à 1), en unités du calque (fenêtre seulement). */
    void fill(int x, int y, int width, int height, TextColor color, float alpha) {
        if (images != null) {
            java.awt.Color c = cardistry.ui.window.CellPalette.foreground(color);
            images.add(new cardistry.ui.window.ImageLayer.Fill(x, y, width, height, new java.awt.Color(
                    c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * Math.max(0, Math.min(1, alpha))))));
        }
    }

    /**
     * Image centrée sur ({@code x}, {@code y}) en unités du calque, dans un carré de {@code size} unités.
     *
     * @return {@code true} si l'image sera dessinée
     */
    boolean stamp(int x, int y, int size, String path, float alpha) {
        if (images == null || cardistry.ui.window.Images.get(path).isEmpty()) {
            return false;
        }
        images.add(new cardistry.ui.window.ImageLayer.Stamp(x, y, size, path, null, alpha));
        return true;
    }

    int cols() {
        return size.getColumns();
    }

    int rows() {
        return size.getRows();
    }

    void text(int col, int row, String text, TextColor color) {
        text(col, row, text, color, false);
    }

    void text(int col, int row, String text, TextColor color, boolean bold) {
        text(col, row, text, color, bold, TextColor.ANSI.DEFAULT);
    }

    /** @param background couleur de fond des cellules (intérieur d'une carte, d'un panneau) */
    void text(int col, int row, String text, TextColor color, boolean bold, TextColor background) {
        if (row < 0 || row >= rows() || col >= cols() || text.isEmpty()) {
            return;
        }
        if (col < 0) {
            if (-col >= text.length()) {
                return;
            }
            text = text.substring(-col);
            col = 0;
        }
        if (col + text.length() > cols()) {
            text = text.substring(0, cols() - col);
        }
        if (ascii) {
            text = cardistry.ui.Glyphs.toAscii(text);
        }
        g.setForegroundColor(color);
        g.setBackgroundColor(background);
        if (bold) {
            g.putString(col, row, text, SGR.BOLD);
        } else {
            g.putString(col, row, text);
        }
    }

    /** Texte centré horizontalement sur la ligne {@code row}. */
    void centered(int row, String text, TextColor color, boolean bold) {
        text(Math.max(0, (cols() - text.length()) / 2), row, text, color, bold);
    }

    void box(int col, int row, int width, int height, TextColor color, boolean doubleLine) {
        String c = doubleLine ? DOUBLE : SINGLE;
        String horizontal = String.valueOf(c.charAt(4)).repeat(Math.max(0, width - 2));
        text(col, row, c.charAt(0) + horizontal + c.charAt(1), color, doubleLine);
        for (int r = row + 1; r < row + height - 1; r++) {
            text(col, r, String.valueOf(c.charAt(5)), color, doubleLine);
            text(col + width - 1, r, String.valueOf(c.charAt(5)), color, doubleLine);
        }
        text(col, row + height - 1, c.charAt(2) + horizontal + c.charAt(3), color, doubleLine);
    }

    /** Cadre de tout l'écran, avec un titre à gauche et une info à droite en haut. */
    void frame(String title, String rightInfo, String help) {
        box(0, 0, cols(), rows(), Palette.FRAME, false);
        text(2, 0, " " + title + " ", Palette.ACCENT, true);
        if (!rightInfo.isEmpty()) {
            text(cols() - rightInfo.length() - 4, 0, " " + rightInfo + " ", Palette.DIM);
        }
        if (!help.isEmpty()) {
            text(2, rows() - 1, fit(" " + help + " ", cols() - 4), Palette.DIM);
        }
    }

    /** Séparateur horizontal sur toute la largeur, raccordé au cadre, avec un titre. */
    void separator(int row, String title) {
        text(0, row, "├" + "─".repeat(cols() - 2) + "┤", Palette.FRAME);
        if (!title.isEmpty()) {
            text(2, row, " " + title + " ", Palette.TEXT, true);
        }
    }

    /** Barre de remplissage : ████░░░░. */
    static String bar(double value, double max, int width) {
        double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
        int filled = (int) Math.round(ratio * width);
        if (value > 0 && filled == 0) {
            filled = 1;
        }
        return "█".repeat(filled) + "░".repeat(width - filled);
    }

    /** Coupe {@code text} à {@code width} caractères avec « … ». */
    static String fit(String text, int width) {
        if (text.length() <= width) {
            return text;
        }
        return width <= 1 ? text.substring(0, width) : text.substring(0, width - 1) + "…";
    }

    /**
     * Texte trop long pour une ligne : découpé en pages (sans couper de mot), la page {@code tick}
     * (modulo leur nombre) est montrée, avec « … » quand il y a une suite ou un début. Tient dans une
     * ligne : inchangé.
     */
    static String paged(String text, int width, int tick) {
        if (text.length() <= width || width < 8) {
            return fit(text, width);
        }
        java.util.List<String> pages = cardistry.ui.Format.wrap(text, width - 4);
        int index = Math.floorMod(tick, pages.size());
        return (index > 0 ? "… " : "") + pages.get(index) + (index < pages.size() - 1 ? " …" : "");
    }

    /**
     * Lignes trop nombreuses pour {@code rows} lignes : la fenêtre {@code tick} (modulo) est montrée,
     * la dernière ligne finissant par « … » s'il y a une suite.
     */
    static java.util.List<String> pagedLines(java.util.List<String> lines, int rows, int tick) {
        if (lines.size() <= rows || rows < 2) {
            return lines.subList(0, Math.min(lines.size(), Math.max(0, rows)));
        }
        int step = rows - 1; // une ligne de recouvrement : on suit la lecture
        int pages = (lines.size() - 2) / step + 1;
        int start = Math.floorMod(tick, pages) * step;
        java.util.List<String> shown = new java.util.ArrayList<>(lines.subList(start, Math.min(lines.size(), start + rows)));
        if (start + rows < lines.size()) {
            shown.set(shown.size() - 1, shown.get(shown.size() - 1) + " …");
        }
        return shown;
    }

    /**
     * Coin haut droit d'une carte : lettre de rareté à la couleur de la rareté, et ★ juste à sa gauche
     * si la carte a un pouvoir.
     */
    void cardCorner(int col, int row, int width, cardistry.model.card.Card card) {
        String letter = cardistry.ui.CardText.rarityLetter(card);
        if (!letter.isEmpty()) {
            text(col + width - 2, row, letter, Palette.rarity(card.rarity()), true);
        }
        if (card.power().isPresent()) {
            text(col + width - (letter.isEmpty() ? 2 : 3), row, "★", Palette.ACCENT, true);
        }
    }
}
