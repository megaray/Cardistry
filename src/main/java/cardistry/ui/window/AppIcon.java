package cardistry.ui.window;

import cardistry.utils.Log;
import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Icône de l'app (provisoire, pixel art) : {@code src/main/resources/icon/cardistry-<taille>.png}. */
public final class AppIcon {

    public static final int[] SIZES = {16, 32, 64, 128, 256, 512};

    private AppIcon() {
    }

    /** Toutes les tailles disponibles (la fenêtre choisit la bonne). */
    public static List<Image> images() {
        List<Image> images = new ArrayList<>();
        for (int size : SIZES) {
            try (InputStream in = AppIcon.class.getResourceAsStream("/icon/cardistry-" + size + ".png")) {
                if (in != null) {
                    images.add(ImageIO.read(in));
                }
            } catch (IOException e) {
                // Icône manquante : la fenêtre garde celle par défaut.
                Log.warn("Icône illisible : cardistry-" + size + ".png", e);
            }
        }
        return images;
    }
}
