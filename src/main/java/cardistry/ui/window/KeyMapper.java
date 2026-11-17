package cardistry.ui.window;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.awt.event.KeyEvent;

/**
 * Traduit les touches de la fenêtre en touches Lanterna. Les touches spéciales et Ctrl+lettre
 * viennent de {@code keyPressed} ; les caractères (accents, clavier QWERTZ…) de {@code keyTyped}.
 */
public final class KeyMapper {

    private KeyMapper() {
    }

    /** Touche spéciale ou Ctrl+lettre (keyPressed), ou {@code null} si c'est un caractère ordinaire. */
    public static KeyStroke pressed(KeyEvent e) {
        boolean ctrl = e.isControlDown();
        boolean alt = e.isAltDown();
        boolean shift = e.isShiftDown();
        KeyType type = switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_KP_LEFT -> KeyType.ArrowLeft;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_KP_RIGHT -> KeyType.ArrowRight;
            case KeyEvent.VK_UP, KeyEvent.VK_KP_UP -> KeyType.ArrowUp;
            case KeyEvent.VK_DOWN, KeyEvent.VK_KP_DOWN -> KeyType.ArrowDown;
            case KeyEvent.VK_ENTER -> KeyType.Enter;
            case KeyEvent.VK_ESCAPE -> KeyType.Escape;
            case KeyEvent.VK_BACK_SPACE -> KeyType.Backspace;
            case KeyEvent.VK_TAB -> KeyType.Tab;
            case KeyEvent.VK_DELETE -> KeyType.Delete;
            case KeyEvent.VK_HOME -> KeyType.Home;
            case KeyEvent.VK_END -> KeyType.End;
            case KeyEvent.VK_PAGE_UP -> KeyType.PageUp;
            case KeyEvent.VK_PAGE_DOWN -> KeyType.PageDown;
            case KeyEvent.VK_F12 -> KeyType.F12; // console de développement (--debug)
            default -> null;
        };
        if (type != null) {
            return new KeyStroke(type, ctrl, alt, shift);
        }
        int code = e.getKeyCode();
        if (ctrl && !alt && code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            return new KeyStroke((char) ('a' + code - KeyEvent.VK_A), true, false, shift);
        }
        return null;
    }

    /** Caractère tapé (keyTyped), ou {@code null} (caractère de contrôle, ou Ctrl/Cmd enfoncé). */
    public static KeyStroke typed(KeyEvent e) {
        char c = e.getKeyChar();
        // AltGr (Ctrl+Alt sous Windows) donne des caractères (@, #…) : on les garde.
        boolean ctrlOnly = e.isControlDown() && !e.isAltDown();
        if (c == KeyEvent.CHAR_UNDEFINED || c < 32 || c == 127 || ctrlOnly || e.isMetaDown()) {
            return null;
        }
        return new KeyStroke(c, false, false, e.isShiftDown());
    }
}
