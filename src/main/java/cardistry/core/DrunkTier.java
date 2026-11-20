package cardistry.core;

/**
 * Palier d'Ivresse : plus on a bu, plus les sorts frappent fort, jusqu'à la Cuite, qui se paie d'une
 * Gueule de bois au combat suivant. Le bonus s'applique aux dégâts élémentaires (comme CMS) et
 * s'additionne aux autres pourcentages.
 */
public enum DrunkTier {
    SOBER("", 0),
    /** 1 point d'Ivresse. */
    TIPSY("Pompette", 0.10),
    /** À partir de 2 points, sous le seuil de Gueule de bois. */
    DRUNK("Bourré", 0.25),
    /** Au seuil de Gueule de bois (3 par défaut, 5 avec Foie d'ingénieur) et au-delà. */
    WASTED("Cuite", 0.50);

    /** Chance qu'un sort rate pendant la Cuite. */
    public static final double WASTED_MISS_CHANCE = 0.50;
    /** PV max pendant la Cuite (×0,67 = −33 %). */
    public static final double WASTED_MAX_HP_FACTOR = 0.67;

    private final String displayName;
    private final double damageBonus;

    DrunkTier(String displayName, double damageBonus) {
        this.displayName = displayName;
        this.damageBonus = damageBonus;
    }

    public String displayName() {
        return displayName;
    }

    /** Bonus de dégâts élémentaires : 0.25 pour +25 %. */
    public double damageBonus() {
        return damageBonus;
    }

    /** Palier pour {@code drunkenness} points d'Ivresse et un seuil de Gueule de bois {@code threshold}. */
    public static DrunkTier of(int drunkenness, int threshold) {
        if (drunkenness <= 0) {
            return SOBER;
        }
        if (drunkenness >= threshold) {
            return WASTED;
        }
        return drunkenness >= 2 ? DRUNK : TIPSY;
    }
}
