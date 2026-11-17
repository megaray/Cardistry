package cardistry.ui.window;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Ce que la fenêtre dessine par-dessus la grille. L'interface le remplit pendant qu'elle dessine un
 * écran ({@link #begin()}, {@link #add}, {@link #commit()}) ; la fenêtre lit le dernier calque validé
 * au moment d'afficher. Sans fenêtre (terminal), il n'y a pas de calque.
 * <ul>
 *   <li>{@link Placement} : une image alignée sur les cellules ;</li>
 *   <li>{@link Mark} : du dessin au pixel près ({@link Fill}, {@link Stamp}), en <em>unités</em> :
 *       {@link #UNITS_PER_COL} unités par colonne et {@link #UNITS_PER_ROW} par ligne, soit un pixel
 *       de la police à sa taille de base. Les marques sont dessinées après les images, dans l'ordre
 *       d'ajout.</li>
 * </ul>
 */
public final class ImageLayer {

    /** Unités par colonne (une cellule fait 7×14 px à la taille de base de la police). */
    public static final int UNITS_PER_COL = 7;
    /** Unités par ligne. */
    public static final int UNITS_PER_ROW = 14;

    /**
     * Une image dans le rectangle de cellules ({@code col}, {@code row}, {@code cols} × {@code rows}),
     * centrée et agrandie sans lissage.
     *
     * @param tint si non null, l'image est entièrement de cette couleur (dégâts reçus)
     * @param alpha opacité, de 0 à 1
     */
    public record Placement(int col, int row, int cols, int rows, String path, Color tint, float alpha) {
    }

    /** Dessin au pixel près, en unités. */
    public sealed interface Mark permits Fill, Stamp {
    }

    /** Rectangle plein ({@code x}, {@code y} = coin haut gauche). */
    public record Fill(int x, int y, int width, int height, Color color) implements Mark {
    }

    /**
     * Image centrée sur ({@code x}, {@code y}), agrandie d'un facteur entier sans lissage pour tenir
     * dans un carré de {@code size} unités.
     *
     * @param tint si non null, l'image est entièrement de cette couleur
     * @param alpha opacité, de 0 à 1
     */
    public record Stamp(int x, int y, int size, String path, Color tint, float alpha) implements Mark {
    }

    /** Un calque validé : images puis marques. */
    public record Frame(List<Placement> images, List<Mark> marks) {
    }

    private static final Frame EMPTY = new Frame(List.of(), List.of());

    private final List<Placement> drawing = new ArrayList<>();
    private final List<Mark> drawingMarks = new ArrayList<>();
    private volatile Frame committed = EMPTY;

    public synchronized void begin() {
        drawing.clear();
        drawingMarks.clear();
    }

    public synchronized void add(Placement placement) {
        drawing.add(placement);
    }

    public synchronized void add(Mark mark) {
        drawingMarks.add(mark);
    }

    /**
     * Retire du calque en cours ce qui déborde sur les lignes {@code row} et suivantes : un panneau
     * de texte dessiné là (console de développement) n'est pas recouvert par les images.
     */
    public synchronized void hideFrom(int row) {
        drawing.removeIf(p -> p.row() + p.rows() > row);
        int limit = row * UNITS_PER_ROW;
        drawingMarks.removeIf(m -> switch (m) {
            case Fill f -> f.y() + f.height() > limit;
            case Stamp s -> s.y() + (s.size() + 1) / 2 > limit;
        });
    }

    public synchronized void commit() {
        committed = new Frame(List.copyOf(drawing), List.copyOf(drawingMarks));
    }

    /** Dernier calque validé. */
    public Frame frame() {
        return committed;
    }

    /** Images du dernier calque validé. */
    public List<Placement> snapshot() {
        return committed.images();
    }

    /** Marques du dernier calque validé. */
    public List<Mark> marks() {
        return committed.marks();
    }
}
