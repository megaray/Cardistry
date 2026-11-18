package cardistry.model.enemy;

import static cardistry.core.stats.StatModifier.flat;
import static cardistry.core.stats.StatModifier.percent;
import static cardistry.items.Rarity.COMMON;
import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.NEUTRAL;
import static cardistry.model.Element.WATER;

import cardistry.core.stats.StatModifier;
import cardistry.core.stats.StatType;
import cardistry.model.Element;
import cardistry.model.card.CardPool;
import cardistry.model.card.TerrainCard;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Terrains des adversaires : chaque combat commence chez l'adversaire. Ces terrains ne sont
 * <b>jamais</b> dans le pool (ni récompense, ni shop) ; leurs malus touchent le joueur tant qu'il y
 * reste, et jouer une carte terrain, c'est « sortir de chez l'ennemi ». Les boss reçoivent le Dojo
 * en attendant les leurs.
 */
public final class EnemyTerrains {

    public static final double HEAL_MALUS = -0.20;
    public static final double CANOPY_SPELL_MALUS = -0.15;
    /** Malus de stat des terrains ennemis convertis au plan V3 (Amphi INT, Toit ESP, Passage RES). */
    public static final double STAT_MALUS = -2;
    public static final double ENEMY_DAMAGE_BONUS = 0.15;
    /** Bord d'autoroute : DEF perdue tant qu'on y reste. */
    public static final double HIGHWAY_DEFENSE_MALUS = -2;

    private static final Map<String, Supplier<TerrainCard>> TERRAINS = Map.ofEntries(
            Map.entry(EnemyRegistry.CADDIE, () -> terrain("Parking du Migros", NEUTRAL, "−1 taille de main",
                    flat(StatType.HAND_SIZE, -1, ""))),
            Map.entry(EnemyRegistry.CLARENCE, EnemyTerrains::couch),
            Map.entry(EnemyRegistry.PENEU, () -> terrain("Bord d'autoroute", NEUTRAL, "−2 DEF",
                    flat(StatType.DEFENSE, HIGHWAY_DEFENSE_MALUS, ""))),
            Map.entry(EnemyRegistry.CHOOMAH, EnemyTerrains::bush),
            Map.entry(EnemyRegistry.CHOOMAH_PRESSE, EnemyTerrains::bush),
            Map.entry(EnemyRegistry.CHOOMAH_COSTAUD, EnemyTerrains::bush),
            Map.entry(EnemyRegistry.PARESSEUX, () -> spellMalus("Canopée", NATURE, CANOPY_SPELL_MALUS)),
            Map.entry(EnemyRegistry.TREX, () -> terrain("Jurassic Ecublens", NATURE, "l'adversaire fait +15 % de dégâts",
                    percent(StatType.ENEMY_DAMAGE, ENEMY_DAMAGE_BONUS, ""))),
            Map.entry(EnemyRegistry.CAME, () -> terrain("Passage sous-gare", FIRE, "−2 RES",
                    flat(StatType.RESISTANCE, STAT_MALUS, ""))),
            Map.entry(EnemyRegistry.VAFI, () -> terrain("Amphi d'examen", FIRE, "−2 INT",
                    flat(StatType.INTELLIGENCE, STAT_MALUS, ""))),
            Map.entry(EnemyRegistry.SEAU, () -> terrain("Toit qui fuit", WATER, "−2 ESP",
                    flat(StatType.MIND, STAT_MALUS, ""))),
            Map.entry(EnemyRegistry.ETUDIANT, () -> terrain("Biblio à 3h du mat", WATER, "−1 taille de main",
                    flat(StatType.HAND_SIZE, -1, ""))),
            Map.entry(EnemyRegistry.GOUROU, () -> spellMalus("Salle de méditation", Element.SPIRIT, CANOPY_SPELL_MALUS)),
            Map.entry(EnemyRegistry.NG, () -> terrain("Salle d'exercices", Element.ALGEBRAIC, "−1 taille de main",
                    flat(StatType.HAND_SIZE, -1, ""))),
            Map.entry(EnemyRegistry.HYPERCUBE, () -> terrain("Cabinet psy scintillant", Element.GEOMETRIC,
                    "l'adversaire fait +15 % de dégâts", percent(StatType.ENEMY_DAMAGE, ENEMY_DAMAGE_BONUS, ""))),
            Map.entry(EnemyRegistry.GOBELIN, () -> terrain("Clairière d'adolescent de base", NATURE,
                    "−1 taille de main", flat(StatType.HAND_SIZE, -1, ""))),
            Map.entry(EnemyRegistry.ENGEANCE, EnemyTerrains::couch));

    private EnemyTerrains() {
    }

    /** Terrain sur lequel commence le combat contre {@code enemyId} (le Dojo pour les boss). */
    public static TerrainCard of(String enemyId) {
        Supplier<TerrainCard> terrain = TERRAINS.get(enemyId);
        return terrain != null ? terrain.get() : CardPool.dojo();
    }

    /** Adversaires qui ont leur propre terrain (les autres commencent sur le Dojo). */
    public static java.util.Set<String> enemiesWithTerrain() {
        return TERRAINS.keySet();
    }

    /** Le canapé (Clarence, Engeance des cookies) : aucun malus. */
    private static TerrainCard couch() {
        return new TerrainCard("Le canapé", NEUTRAL, COMMON);
    }

    private static TerrainCard bush() {
        return terrain("Buisson louche", NATURE, "−20 % de soins", percent(StatType.HEAL_EFFICIENCY, HEAL_MALUS, ""));
    }

    private static TerrainCard spellMalus(String name, Element element, double malus) {
        List<StatModifier> modifiers = StatType.allDamageMultipliers().stream()
                .map(stat -> percent(stat, malus, "")).toList();
        return new TerrainCard(name, element, COMMON).withBonuses(
                "−" + Math.round(-malus * 100) + " % de dégâts des sorts", modifiers);
    }

    private static TerrainCard terrain(String name, Element element, String text, StatModifier... modifiers) {
        return new TerrainCard(name, element, COMMON).withBonuses(text, modifiers);
    }


    public static TerrainCard terrainColonel() {
        return new TerrainCard("Royaume du Colonel", NATURE, COMMON)
                .withEffect(TerrainCard.Effect.NO_HEALING, "aucun soin possible dans le royaume du Colonel");
    }

}
