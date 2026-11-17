package cardistry.ui.window;

import cardistry.utils.Log;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Images PNG du jeu, lues dans {@code src/main/resources/images/} et mises en cache :
 * <ul>
 *   <li>{@code images/enemies/<sprite>.png} : un adversaire ; {@code <sprite>.<animation>.<n>.png} :
 *       la frame n (à partir de 1) d'une animation (idle, attack, hurt, death) ;</li>
 *   <li>{@code images/items/<id>.png} : l'icône d'un objet ou d'un trinket ;</li>
 *   <li>{@code images/cards/<id>.png} : l'illustration d'une carte ;</li>
 *   <li>{@code images/ui/} : icônes de type de carte, d'intent, placeholders ; {@code images/player/} :
 *       le joueur ;</li>
 *   <li>{@code images/npc/<id>.idle.<n>.png} : un personnage non joueur (le marchand du shop) ;</li>
 *   <li>{@code images/map/<type>.png} : l'icône d'un nœud de la carte (combat, elite, boss, shop,
 *       rest, event).</li>
 * </ul>
 * Image absente ou illisible : rien (l'interface garde le sprite ASCII).
 */
public final class Images {

    private static final Map<String, Optional<BufferedImage>> CACHE = new ConcurrentHashMap<>();

    private Images() {
    }

    /**
     * Chemin de l'image d'un adversaire pour une frame : la frame si elle existe, sinon la dernière
     * frame de cette animation (une animation plus courte que prévu reste sur sa fin), sinon l'image
     * de base.
     */
    public static Optional<String> enemy(String spriteId, String animation, int frame) {
        String framePath = "enemies/" + spriteId + "." + animation + "." + (frame + 1) + ".png";
        if (get(framePath).isPresent()) {
            return Optional.of(framePath);
        }
        int count = frames(spriteId, animation);
        if (count > 0 && frame >= count) {
            return Optional.of("enemies/" + spriteId + "." + animation + "." + count + ".png");
        }
        String idle = "enemies/" + spriteId + ".idle." + (frame + 1) + ".png";
        if (animation.equals("idle") && get(idle).isPresent()) {
            return Optional.of(idle);
        }
        String base = "enemies/" + spriteId + ".png";
        return get(base).isPresent() ? Optional.of(base) : Optional.empty();
    }

    /**
     * Chemin de l'image d'un personnage jouable ou d'un familier ({@code player/<id>.<animation>.<n>.png}) :
     * la frame, sinon la dernière de l'animation, sinon l'idle (en boucle), sinon {@code player/<id>.png}.
     */
    public static Optional<String> player(String id, String animation, int frame) {
        int count = playerFrames(id, animation);
        if (count > 0) {
            int n = animation.equals("idle") ? Math.floorMod(frame, count) : Math.min(frame, count - 1);
            return Optional.of("player/" + id + "." + animation + "." + (n + 1) + ".png");
        }
        int idle = playerFrames(id, "idle");
        if (idle > 0) {
            return Optional.of("player/" + id + ".idle." + (Math.floorMod(frame, idle) + 1) + ".png");
        }
        String base = "player/" + id + ".png";
        return get(base).isPresent() ? Optional.of(base) : Optional.empty();
    }

    /** Nombre de frames d'une animation d'un personnage jouable ou d'un familier (0 s'il n'y en a pas). */
    public static int playerFrames(String id, String animation) {
        int count = 0;
        while (get("player/" + id + "." + animation + "." + (count + 1) + ".png").isPresent()) {
            count++;
        }
        return count;
    }

    /** Nombre de frames idle en images (0 s'il n'y en a pas). */
    public static int idleFrames(String spriteId) {
        return frames(spriteId, "idle");
    }

    /** Nombre de frames en images de l'animation (idle, attack, hurt, death) ; 0 s'il n'y en a pas. */
    public static int frames(String spriteId, String animation) {
        int count = 0;
        while (get("enemies/" + spriteId + "." + animation + "." + (count + 1) + ".png").isPresent()) {
            count++;
        }
        return count;
    }

    /**
     * Chemin de l'image d'un personnage non joueur ({@code npc/<id>.idle.<n>.png}, la frame bouclant
     * sur ses frames idle ; sinon {@code npc/<id>.png}), si elle existe.
     */
    public static Optional<String> npc(String id, int frame) {
        int count = 0;
        while (get("npc/" + id + ".idle." + (count + 1) + ".png").isPresent()) {
            count++;
        }
        if (count > 0) {
            return Optional.of("npc/" + id + ".idle." + (Math.floorMod(frame, count) + 1) + ".png");
        }
        String base = "npc/" + id + ".png";
        return get(base).isPresent() ? Optional.of(base) : Optional.empty();
    }

    /** Chemin de l'icône d'un objet ou trinket, si elle existe. */
    public static Optional<String> item(String id) {
        String path = "items/" + id + ".png";
        return get(path).isPresent() ? Optional.of(path) : Optional.empty();
    }

    /** Chemin de l'illustration d'une carte ({@code cards/<id>.png}, id = nom sans accents), si elle existe. */
    public static Optional<String> card(String id) {
        String path = "cards/" + id + ".png";
        return get(path).isPresent() ? Optional.of(path) : Optional.empty();
    }

    /** Chemin de l'icône d'un type de nœud de la carte ({@code map/combat.png}…), si elle existe. */
    public static Optional<String> mapNode(cardistry.run.map.NodeType type) {
        String path = "map/" + type.name().toLowerCase(java.util.Locale.ROOT) + ".png";
        return get(path).isPresent() ? Optional.of(path) : Optional.empty();
    }

    /** Image au chemin {@code path} (relatif à {@code images/}). */
    public static Optional<BufferedImage> get(String path) {
        return CACHE.computeIfAbsent(path, Images::load);
    }

    private static Optional<BufferedImage> load(String path) {
        try (InputStream in = Images.class.getResourceAsStream("/images/" + path)) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(ImageIO.read(in));
        } catch (IOException e) {
            Log.warn("Image illisible : images/" + path, e);
            return Optional.empty();
        }
    }
}
