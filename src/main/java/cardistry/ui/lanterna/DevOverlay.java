package cardistry.ui.lanterna;

import cardistry.run.Run;
import cardistry.ui.DevCommands;
import cardistry.utils.Log;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.ArrayList;
import java.util.List;

/**
 * Console de développement ({@code --debug}) : F12 ouvre, par-dessus la moitié basse de n'importe
 * quel écran, le journal en direct ({@link Log#recent()}) et une ligne de commande
 * ({@link DevCommands}). Tant qu'elle est ouverte, elle reçoit toutes les touches.
 *
 * <ul>
 *   <li>Entrée exécute, ↑/↓ parcourent les commandes déjà tapées, Échap ou F12 ferment ;</li>
 *   <li>PgUp/PgDn font défiler le journal, Tab change le niveau affiché (≥ DEBUG, INFO, WARN, ERROR).</li>
 * </ul>
 */
final class DevOverlay {

    private static final TextColor BACKGROUND = TextColor.ANSI.BLACK;
    private static final TextColor TEXT = TextColor.ANSI.WHITE;

    private boolean open = false;
    private Run run;
    private final StringBuilder input = new StringBuilder();
    private final List<String> typed = new ArrayList<>();
    private int typedIndex = 0;
    private int scroll = 0;
    private Log.Level shown = Log.Level.DEBUG;
    private long drawnId = Long.MIN_VALUE;

    boolean isOpen() {
        return open;
    }

    /** Partie visée par les commandes (une nouvelle à chaque retour au titre). */
    void attach(Run run) {
        this.run = run;
    }

    static boolean isToggle(KeyStroke key) {
        return key.getKeyType() == KeyType.F12;
    }

    void toggle() {
        open = !open;
        scroll = 0;
    }

    /** {@code true} si le journal a reçu une ligne depuis le dernier dessin. */
    boolean needsRedraw() {
        return open && Log.lastId() != drawnId;
    }

    /** Traite une touche quand la console est ouverte ; {@code true} si l'écran du dessous doit être redessiné. */
    boolean handle(KeyStroke key) {
        switch (key.getKeyType()) {
            case Escape -> open = false;
            case Enter -> {
                String line = input.toString();
                input.setLength(0);
                if (!line.isBlank()) {
                    typed.add(line);
                }
                typedIndex = typed.size();
                scroll = 0;
                DevCommands.execute(line, run);
            }
            case Backspace -> {
                if (!input.isEmpty()) {
                    input.setLength(input.length() - 1);
                }
            }
            case ArrowUp -> recall(-1);
            case ArrowDown -> recall(1);
            case PageUp -> scroll += 5;
            case PageDown -> scroll = Math.max(0, scroll - 5);
            case Tab -> shown = Log.Level.values()[(shown.ordinal() + 1) % Log.Level.values().length];
            case Character -> {
                if (!key.isCtrlDown()) {
                    input.append(key.getCharacter());
                }
            }
            default -> {
            }
        }
        return true;
    }

    private void recall(int delta) {
        if (typed.isEmpty()) {
            return;
        }
        typedIndex = Math.max(0, Math.min(typed.size(), typedIndex + delta));
        input.setLength(0);
        if (typedIndex < typed.size()) {
            input.append(typed.get(typedIndex));
        }
    }

    /** Panneau sur la moitié basse de l'écran, par-dessus ce qui est déjà dessiné. */
    void draw(Painter p) {
        drawnId = Log.lastId();
        int cols = p.cols();
        int top = p.rows() / 2;
        int bottom = p.rows() - 1;
        p.hideImagesFrom(top);
        String blank = " ".repeat(cols);
        for (int row = top; row <= bottom; row++) {
            p.text(0, row, blank, TEXT, false, BACKGROUND);
        }
        String title = " CONSOLE DE DEV · journal ≥ " + shown + (scroll > 0 ? " · ↑" + scroll : "") + " ";
        String help = " Tab niveau · PgUp/PgDn · Échap ";
        p.text(0, top, "═".repeat(cols), Palette.ACCENT, false, BACKGROUND);
        p.text(2, top, title, Palette.ACCENT, true, BACKGROUND);
        if (cols - help.length() - 2 > title.length() + 2) {
            p.text(cols - help.length() - 2, top, help, Palette.DIM, false, BACKGROUND);
        }
        List<Line> lines = lines(Math.max(10, cols - 2));
        int height = bottom - top - 1; // entre le titre et la ligne de saisie
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - height)));
        int end = lines.size() - scroll;
        int start = Math.max(0, end - height);
        for (int i = start; i < end; i++) {
            Line line = lines.get(i);
            p.text(1, top + 1 + (i - start), line.text(), line.color(), false, BACKGROUND);
        }
        String prompt = "> " + input + "_";
        if (prompt.length() > cols - 2) {
            prompt = "…" + prompt.substring(prompt.length() - (cols - 3));
        }
        p.text(1, bottom, prompt, Palette.ACCENT, true, BACKGROUND);
    }

    private record Line(String text, TextColor color) {
    }

    /** Lignes du journal au niveau affiché, découpées à la largeur (piles d'exceptions comprises). */
    private List<Line> lines(int width) {
        List<Line> out = new ArrayList<>();
        for (Log.Entry entry : Log.recent()) {
            if (entry.level().compareTo(shown) < 0) {
                continue;
            }
            TextColor color = switch (entry.level()) {
                case DEBUG -> Palette.DIM;
                case INFO -> TEXT;
                case WARN -> Palette.WARNING;
                case ERROR -> Palette.DAMAGE;
            };
            String time = entry.time().toString();
            String prefix = (time.length() >= 8 ? time.substring(0, 8) : time) + " ";
            boolean first = true;
            for (String raw : entry.message().split("\n", -1)) {
                String text = (first ? prefix : "    ") + raw.replace('\t', ' ');
                first = false;
                if (text.isEmpty()) {
                    out.add(new Line("", color));
                }
                while (!text.isEmpty()) {
                    int cut = Math.min(width, text.length());
                    out.add(new Line(text.substring(0, cut), color));
                    text = text.length() > cut ? "  " + text.substring(cut) : "";
                }
            }
        }
        return out;
    }
}
