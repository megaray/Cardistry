package cardistry.ui;

import cardistry.core.event.GameEvent;
import cardistry.items.Collectible;
import cardistry.model.Enemy;
import cardistry.model.Familiar;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.enemy.EnemyMove;
import cardistry.utils.Log;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.function.Consumer;

/**
 * Écrit chaque événement du bus de l'interface dans le journal (niveau DEBUG, donc seulement avec
 * {@code --debug}). Abonné au bus {@code front} : un rejeu de Ctrl+Z n'y passe pas, il n'y a donc
 * pas de doublons.
 *
 * <p>Les champs sont lus par réflexion sur le record, chaque valeur résumée en une ligne courte
 * (nom de la carte, adversaire avec ses PV…) : un nouvel événement est journalisé sans rien ajouter ici.
 */
public final class EventLog implements Consumer<GameEvent> {

    @Override
    public void accept(GameEvent event) {
        if (Log.enabled(Log.Level.DEBUG)) {
            Log.debug(describe(event));
        }
    }

    /** {@code DamageDealt(target=Choomah (12 PV), amount=14, …)}. */
    public static String describe(GameEvent event) {
        Class<?> type = event.getClass();
        if (!type.isRecord()) {
            return event.toString();
        }
        StringJoiner fields = new StringJoiner(", ", type.getSimpleName() + "(", ")");
        for (RecordComponent component : type.getRecordComponents()) {
            Object value;
            try {
                component.getAccessor().setAccessible(true);
                value = component.getAccessor().invoke(event);
            } catch (ReflectiveOperationException | RuntimeException e) {
                value = "?";
            }
            fields.add(component.getName() + "=" + value(value));
        }
        return fields.toString();
    }

    static String value(Object value) {
        return switch (value) {
            case null -> "-";
            case Card card -> card.name();
            case Enemy enemy -> enemy.name() + " (" + enemy.hp() + " PV)";
            case Familiar familiar -> familiar.name() + " (" + familiar.hp() + " PV)";
            case Player player -> "joueur (" + player.hp() + " PV)";
            case Collectible collectible -> collectible.name();
            case EnemyMove move -> move.name();
            case cardistry.story.Story story -> story.title();
            case cardistry.core.shop.ShopOffer offer -> offer.name() + " (" + offer.price() + ")";
            case cardistry.core.Shop shop -> "shop";
            case Double d -> number(d);
            case Float f -> number(f);
            case Collection<?> list -> {
                StringJoiner items = new StringJoiner(", ", "[", "]");
                list.forEach(item -> items.add(value(item)));
                yield items.toString();
            }
            default -> value.toString();
        };
    }

    private static String number(double value) {
        return value == Math.rint(value) && Math.abs(value) < 1e15
                ? Long.toString((long) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
