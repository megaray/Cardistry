package cardistry.model;

import cardistry.model.enemy.EnemyBehavior;
import cardistry.model.enemy.EnemyMove;
import cardistry.model.enemy.EnemyRank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Un adversaire : ses attaques sont choisies par son {@link EnemyBehavior} et annoncées à l'avance
 * (son intent). Construit par {@link #builder}, ou par le constructeur court (une seule attaque de
 * base, pour les tests). Les adversaires du jeu sont déclarés dans {@code enemies.EnemyRegistry}.
 */
public class Enemy extends Combatant {

    /** Façon de parler : {@link #SCREAM} affiche attaques et descriptions sous forme de cris (Choomah). */
    public enum Voice {
        NORMAL,
        SCREAM
    }

    private final String id;
    private final String name;
    /** Peut changer en cours de combat (Méditation triptyque de l'Engeance des cookies). */
    private Element element;
    private final double maxHp;
    private final EnemyRank rank;
    private final double damageScale;
    private final EnemyBehavior behavior;
    private final double reflectRatio;
    private final int choomiesReward;
    private final Voice voice;
    private final String spriteId;
    /** Terrain sur lequel commence le combat ; {@code null} : celui du joueur reste. */
    private final cardistry.model.card.TerrainCard terrain;
    /** FOR, INT, ESP, DEF, RES, CHA (même système que le joueur, voir {@code core.CombatFormula}). */
    private final cardistry.core.stats.Stats stats = new cardistry.core.stats.Stats();

    private EnemyMove intent;
    private int actions = 0;
    private final Map<String, Integer> uses = new HashMap<>();
    private boolean dodgeReady = false;

    /** Adversaire simple : attaque de {@code attack} à chaque tour. */
    public Enemy(String name, Element element, double hp, double attack) {
        this(builder(Ids.slug(name), name, element, EnemyRank.COMMON).hp(hp)
                .behavior(EnemyBehavior.always(EnemyMove.basicAttack(attack))));
    }

    private Enemy(Builder b) {
        super(b.hp * b.hpScale);
        this.id = b.id;
        this.name = b.name;
        this.element = b.element;
        this.maxHp = round(b.hp * b.hpScale);
        this.rank = b.rank;
        this.damageScale = b.damageScale;
        this.behavior = Objects.requireNonNull(b.behavior, "comportement manquant");
        this.reflectRatio = b.reflectRatio;
        this.choomiesReward = b.choomiesReward;
        this.voice = b.voice;
        this.spriteId = b.spriteId != null ? b.spriteId : b.id;
        this.terrain = b.terrain;
        b.stats.forEach(stats::setBase);
    }

    public static Builder builder(String id, String name, Element element, EnemyRank rank) {
        return new Builder(id, name, element, rank);
    }

    public String id() {
        return id;
    }

    /** PV de départ de l'adversaire (niveau compris). */
    public double maxHp() {
        return maxHp;
    }

    public String name() {
        return name;
    }

    public Element element() {
        return element;
    }

    /** Change d'élément jusqu'à la fin du combat. */
    public void changeElement(Element value) {
        element = value;
    }

    public EnemyRank rank() {
        return rank;
    }

    /** Multiplicateur des dégâts déclarés (niveau de la run). */
    public double damageScale() {
        return damageScale;
    }

    /** Part des dégâts reçus renvoyée au joueur (Le Peneu : 0,1). */
    public double reflectRatio() {
        return reflectRatio;
    }

    /** Choomies gagnés en le battant, ou −1 pour la valeur par défaut de son rang. */
    public int choomiesReward() {
        return choomiesReward;
    }

    public Voice voice() {
        return voice;
    }

    /** Nom du fichier de sprite (sans extension). */
    public String spriteId() {
        return spriteId;
    }

    /** Terrain de l'adversaire (le combat commence dessus), s'il en a un. */
    public java.util.Optional<cardistry.model.card.TerrainCard> terrain() {
        return java.util.Optional.ofNullable(terrain).map(cardistry.model.card.TerrainCard::copy);
    }

    /** Attaque annoncée pour son prochain tour ; {@code null} avant le début du combat. */
    public EnemyMove intent() {
        return intent;
    }

    /** Intent annoncé, ou celui qui serait choisi maintenant s'il n'y en a pas encore. */
    public EnemyMove peekIntent() {
        return intent != null ? intent : behavior.next(this);
    }

    /** Attaques déjà exécutées pendant ce combat (les tours sautés ne comptent pas). */
    public int actions() {
        return actions;
    }

    /** Nombre d'utilisations d'une attaque pendant ce combat. */
    public int uses(String moveId) {
        return uses.getOrDefault(moveId, 0);
    }

    /** {@code true} s'il esquivera le prochain sort. */
    public boolean dodgeReady() {
        return dodgeReady;
    }

    public double hpRatio() {
        return maxHp <= 0 ? 0 : hp() / maxHp;
    }

    // --- Mutations (moteur) ---

    /** Choisit et mémorise le prochain intent. */
    public EnemyMove chooseIntent() {
        intent = behavior.next(this);
        return intent;
    }

    /** Note qu'une attaque a été exécutée. */
    public void recordAction(EnemyMove move) {
        actions++;
        uses.merge(move.id(), 1, Integer::sum);
    }

    public void setDodgeReady(boolean value) {
        dodgeReady = value;
    }

    /**
     * Stats de l'adversaire (FOR, INT, ESP, DEF, RES, CHA ; niveau compris). Modifiables en combat
     * (Jet d'eau…) : l'instance ne vit qu'un combat.
     */
    public cardistry.core.stats.Stats stats() {
        return stats;
    }

    /** Valeur d'une stat de l'adversaire. */
    public double stat(cardistry.core.stats.StatType stat) {
        return stats.get(stat);
    }

    /** Se soigne sans dépasser ses PV max ; renvoie les PV rendus. */
    public double healSelf(double amount) {
        return heal(Math.max(0, Math.min(round(amount), maxHp - hp())));
    }

    public static final class Builder {

        private final String id;
        private final String name;
        private final Element element;
        private final EnemyRank rank;
        private double hp = 1;
        private double hpScale = 1;
        private double damageScale = 1;
        private EnemyBehavior behavior;
        private double reflectRatio = 0;
        private int choomiesReward = -1;
        private Voice voice = Voice.NORMAL;
        private String spriteId;
        private cardistry.model.card.TerrainCard terrain;
        private final java.util.Map<cardistry.core.stats.StatType, Double> stats =
                new java.util.EnumMap<>(cardistry.core.stats.StatType.class);

        private Builder(String id, String name, Element element, EnemyRank rank) {
            this.id = id;
            this.name = name;
            this.element = element;
            this.rank = rank;
        }

        /** PV au niveau 1. */
        public Builder hp(double value) {
            hp = value;
            return this;
        }

        /** Multiplicateurs de niveau : PV et dégâts. */
        public Builder scale(double hpFactor, double damageFactor) {
            hpScale = hpFactor;
            damageScale = damageFactor;
            return this;
        }

        public Builder behavior(EnemyBehavior value) {
            behavior = value;
            return this;
        }

        public Builder reflect(double ratio) {
            reflectRatio = ratio;
            return this;
        }

        public Builder choomies(int reward) {
            choomiesReward = reward;
            return this;
        }

        public Builder screams() {
            voice = Voice.SCREAM;
            return this;
        }

        public Builder sprite(String value) {
            spriteId = value;
            return this;
        }

        /** Le combat commence sur ce terrain (voir {@code EnemyTerrains}). */
        public Builder terrain(cardistry.model.card.TerrainCard value) {
            terrain = value;
            return this;
        }

        /** Stats de base (niveau 1) : FOR, INT, ESP, DEF, RES, CHA. */
        public Builder stats(int strength, int intelligence, int mind, int defense, int resistance, int luck) {
            int[] values = {strength, intelligence, mind, defense, resistance, luck};
            List<cardistry.core.stats.StatType> types = cardistry.core.stats.StatType.characterStats();
            for (int i = 0; i < values.length; i++) {
                stats.put(types.get(i), (double) values[i]);
            }
            return this;
        }

        /** Ajoute {@code amount} à la stat de base {@code stat} (scaling par niveau). */
        public Builder addStat(cardistry.core.stats.StatType stat, int amount) {
            stats.merge(stat, (double) amount, Double::sum);
            return this;
        }

        public Enemy build() {
            return new Enemy(this);
        }
    }
}
