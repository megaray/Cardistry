package cardistry.core;

import cardistry.core.stats.StatModifier;
import cardistry.core.stats.StatType;
import cardistry.core.status.StatusTarget;
import cardistry.core.status.StatusType;
import cardistry.items.ItemRegistry;
import cardistry.model.Combatant;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Triches de la console de développement ({@code --debug}, F12). Chaque fabrique vérifie ses
 * arguments tout de suite ({@link IllegalArgumentException} au message lisible) et renvoie la
 * modification à appliquer : {@code run.Run#cheat} l'applique et la garde dans l'historique, pour
 * qu'un rejeu de Ctrl+Z la refasse au même moment.
 */
public final class DevCheats {

    /** Source des modificateurs de stats posés par la console (visible sur la fiche des stats). */
    public static final String SOURCE = "console";

    private DevCheats() {
    }

    /** PV du joueur fixés à {@code hp} (sans plafond). */
    public static Consumer<GameState> playerHp(int hp) {
        requirePositive(hp, "PV");
        return state -> setHp(state.player(), hp);
    }

    /** PV de l'adversaire en cours fixés à {@code hp} (au moins 1 : le prochain coup le tue). */
    public static Consumer<GameState> enemyHp(int hp) {
        if (hp < 1) {
            throw new IllegalArgumentException("Au moins 1 PV (le combat se résout au prochain coup)");
        }
        return state -> {
            if (!state.hasEnemy() || !state.currentEnemy().isAlive()) {
                throw new IllegalStateException("Pas d'adversaire en combat");
            }
            setHp(state.currentEnemy(), hp);
        };
    }

    public static Consumer<GameState> choomies(int amount) {
        requirePositive(amount, "Choomies");
        return state -> state.setChoomies(amount);
    }

    public static Consumer<GameState> cigarettes(int amount) {
        requirePositive(amount, "Clopes");
        return state -> state.setCigarettes(amount);
    }

    public static Consumer<GameState> drunkenness(int amount) {
        requirePositive(amount, "Ivresse");
        return state -> state.setDrunkenness(amount);
    }

    /** Ajoute à la main une copie de la carte {@code name} du pool (casse et accents ignorés). */
    public static Consumer<GameState> cardToHand(String name) {
        Card card = findCard(name);
        return state -> state.addToHand(card.copy());
    }

    /** Ajoute au deck une copie de la carte {@code name}. */
    public static Consumer<GameState> cardToDeck(String name) {
        Card card = findCard(name);
        return state -> state.addToDeck(card.copy());
    }

    /** Équipe le trinket {@code id} (s'il reste un emplacement). */
    public static Consumer<GameState> trinket(String id) {
        ItemRegistry.trinket(id); // inconnu : refusé tout de suite
        return state -> state.equipTrinket(ItemRegistry.trinket(id));
    }

    /** Ajoute l'objet {@code id} (s'il reste un emplacement). */
    public static Consumer<GameState> item(String id) {
        ItemRegistry.item(id);
        return state -> state.addItem(ItemRegistry.item(id));
    }

    /** Pose le statut {@code id} pour {@code turns} tours, sans tirage de chance. */
    public static Consumer<GameState> status(String id, int turns, StatusTarget target) {
        StatusType type = StatusType.byId(id);
        if (turns < 1) {
            throw new IllegalArgumentException("Au moins 1 tour");
        }
        return state -> {
            if (target == StatusTarget.ENEMY && !state.hasEnemy()) {
                throw new IllegalStateException("Pas d'adversaire en combat");
            }
            state.statuses(target).apply(type, turns, StatusTarget.PLAYER);
        };
    }

    /** Ajoute {@code value} (plat) à la stat {@code stat} du joueur, source « console ». */
    public static Consumer<GameState> stat(StatType stat, double value) {
        return state -> state.playerStats().add(StatModifier.flat(stat, value, SOURCE));
    }

    /** Stat de personnage par abréviation (FOR, INT…) ou nom d'enum (STRENGTH…). */
    public static StatType statType(String text) {
        for (StatType type : StatType.values()) {
            if (type.name().equalsIgnoreCase(text) || type.abbreviation().equalsIgnoreCase(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Stat inconnue : " + text);
    }

    /** Carte du pool dont le nom correspond (casse, accents et espaces ignorés). */
    public static Card findCard(String name) {
        String wanted = key(name);
        return new CardPool().cards().stream()
                .filter(c -> key(c.name()).equals(wanted))
                .findFirst()
                .or(() -> new CardPool().cards().stream().filter(c -> key(c.name()).contains(wanted)).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("Carte inconnue : " + name));
    }

    private static String key(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void setHp(Combatant combatant, int hp) {
        combatant.takeDamage(combatant.hp() - hp); // dégâts négatifs : soin sans plafond
    }

    private static void requirePositive(int value, String what) {
        if (value < 0) {
            throw new IllegalArgumentException(what + " : valeur positive attendue");
        }
    }
}
