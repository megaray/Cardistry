package cardistry.model.enemy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cardistry.core.Rng;
import cardistry.model.Element;
import static cardistry.model.Element.ALGEBRAIC;
import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.GEOMETRIC;
import static cardistry.model.Element.GLITCH;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.NEUTRAL;
import static cardistry.model.Element.SPIRIT;
import static cardistry.model.Element.WATER;
import cardistry.model.Enemy;
import cardistry.model.enemy.MoveEffect.Hit;

/**
 * Registre de tous les adversaires : PV, attaques et stats au niveau 1, multipliés par niveau
 * ({@link #HP_SCALE}, {@link #DAMAGE_SCALE}, +{@link #STAT_GAIN_PER_LEVEL} FOR et DEF par niveau). Ajouter un adversaire : une entrée dans
 * {@link #definitions()} (builder de {@link Enemy}, comportement de {@link EnemyBehavior}) et son
 * sprite dans {@code src/main/resources/sprites/<id>.txt}.
 */
public final class EnemyRegistry {

    /** Multiplicateur des PV par niveau (index 0 = niveau 1). Les boss ont des PV fixés. */
    public static final double[] HP_SCALE = {1.0, 1.4, 1.9};
    /** Multiplicateur des dégâts par niveau (boss compris). */
    public static final double[] DAMAGE_SCALE = {1.0, 1.25, 1.5};
    /** Chance qu'un combat commun soit contre Clarence. */
    public static final double CLARENCE_CHANCE = 0.03;
    public static final int LEVELS = 3;
    /** Chance du Camé de brûler à chaque attaque qui porte. */
    public static final double CAME_BURN_CHANCE = 0.3;
    /** Chance du « Badoing, badoing ! » du Paresseux d'endormir (Fons 1 tour). */
    public static final double PARESSEUX_FONS_CHANCE = 0.25;
    /** Identifiants des statuts infligés (voir {@code core.status.StatusType#id()}). */
    static final String INTOX = "intox";
    static final String FONS = "fons";
    static final String BURN = "brulure";
    static final String HYPNOTIZED = "hypnotise";

    public static final String CADDIE = "caddie-enerve";
    public static final String CHOOMAH = "choomah";
    public static final String CHOOMAH_PRESSE = "choomah-presse";
    public static final String CHOOMAH_COSTAUD = "choomah-costaud";
    public static final String CAME = "came";
    public static final String PARESSEUX = "paresseux";
    public static final String CLARENCE = "clarence";
    public static final String SEAU = "seau-de-fuite";
    public static final String VAFI = "vafi";
    public static final String PENEU = "le-peneu";
    public static final String TREX = "t-rex-defonce";
    public static final String ETUDIANT = "etudiant-post-examen";
    public static final String GOUROU = "gourou-du-sat";
    public static final String NG = "ng";
    public static final String HYPERCUBE = "hypercube-misogyne";
    public static final String GOBELIN = "gobelin";
    public static final String ENGEANCE = "engeance-des-cookies";

    /** Communs tirés pour un combat (Clarence à part) ; les Choomah comptent pour un. */
    public static final List<String> COMMONS = List.of(CADDIE, CHOOMAH, CAME, PARESSEUX, SEAU, GOUROU, NG, HYPERCUBE,
            GOBELIN);
    public static final List<String> CHOOMAH_VARIANTS = List.of(CHOOMAH, CHOOMAH_PRESSE, CHOOMAH_COSTAUD);
    public static final List<String> ELITES = List.of(VAFI, PENEU, TREX, ETUDIANT, ENGEANCE);

    /** FOR et DEF gagnées par niveau au-delà du premier (pas les boss : leurs stats sont fixées par niveau). */
    public static final int STAT_GAIN_PER_LEVEL = 1;

    /** Stats au niveau 1 : FOR, INT, ESP, DEF, RES, CHA (voir {@code core.CombatFormula}). */
    private static final Map<String, int[]> STATS = Map.ofEntries(
            Map.entry(CADDIE, new int[] {1, 0, 0, 2, 2, 0}),
            Map.entry(CHOOMAH, new int[] {0, 0, 0, 0, 1, 3}),
            Map.entry(CHOOMAH_PRESSE, new int[] {0, 0, 0, 0, 0, 4}),
            Map.entry(CHOOMAH_COSTAUD, new int[] {2, 0, 0, 1, 1, 1}),
            Map.entry(CAME, new int[] {1, 2, 0, 0, 3, 1}),
            Map.entry(PARESSEUX, new int[] {0, 1, 1, 1, 2, 2}),
            Map.entry(CLARENCE, new int[] {0, 0, 0, 0, 0, 0}),
            Map.entry(SEAU, new int[] {0, 0, 0, 1, 4, 0}),
            Map.entry(GOUROU, new int[] {0, 1, 3, 0, 2, 0}),
            Map.entry(NG, new int[] {0, 4, 0, 1, 1, 0}),
            Map.entry(HYPERCUBE, new int[] {1, 2, 0, 2, 1, 1}),
            Map.entry(GOBELIN, new int[] {1, 0, 0, 0, 1, 3}),
            Map.entry(VAFI, new int[] {2, 4, 2, 1, 3, 1}),
            Map.entry(PENEU, new int[] {1, 0, 0, 4, 5, 0}),
            Map.entry(TREX, new int[] {4, 0, 1, 2, 0, 0}),
            Map.entry(ETUDIANT, new int[] {1, 3, 0, 0, 0, 2}),
            Map.entry(ENGEANCE, new int[] {2, 3, 2, 1, 3, 2}),
            Map.entry("boss-1", new int[] {3, 1, 1, 2, 3, 1}),
            Map.entry("boss-2", new int[] {4, 4, 2, 3, 4, 2}),
            Map.entry("boss-3", new int[] {5, 3, 3, 4, 5, 3}));

    /** Stats de base (niveau 1) de l'adversaire {@code id} : FOR, INT, ESP, DEF, RES, CHA. */
    public static int[] baseStats(String id) {
        int[] stats = STATS.get(id);
        if (stats == null) {
            throw new IllegalArgumentException("Adversaire sans stats : " + id);
        }
        return stats.clone();
    }

    private interface Definition {
        Enemy.Builder create();
    }

    private static final Map<String, Definition> DEFINITIONS = definitions();

    private EnemyRegistry() {
    }

    /** Identifiants de tous les adversaires (boss compris), dans l'ordre du registre. */
    public static List<String> ids() {
        return List.copyOf(DEFINITIONS.keySet());
    }

    /** Nouvelle instance de l'adversaire {@code id} au niveau {@code level} (1 à 3). */
    public static Enemy create(String id, int level) {
        Definition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Adversaire inconnu : " + id);
        }
        int index = Math.max(0, Math.min(LEVELS, level) - 1);
        Enemy.Builder builder = definition.create();
        boolean boss = id.startsWith("boss-");
        int[] stats = baseStats(id);
        builder.stats(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        if (!boss) {
            builder.addStat(cardistry.core.stats.StatType.STRENGTH, STAT_GAIN_PER_LEVEL * index)
                    .addStat(cardistry.core.stats.StatType.DEFENSE, STAT_GAIN_PER_LEVEL * index);
        }
        return builder.scale(boss ? 1.0 : HP_SCALE[index], DAMAGE_SCALE[index]).terrain(EnemyTerrains.of(id)).build();
    }

    /** Identifiant du boss du niveau {@code level}. */
    public static String bossId(int level) {
        return "boss-" + level;
    }

    /**
     * Adversaire d'un combat commun : Clarence ({@link #CLARENCE_CHANCE}), sinon un commun au hasard
     * différent de {@code previousId} (une variante au hasard pour les Choomah).
     */
    public static String pickCommon(Rng rng, String previousId) {
        if (rng.nextDouble() < CLARENCE_CHANCE) {
            return CLARENCE;
        }
        String previous = previousId != null && CHOOMAH_VARIANTS.contains(previousId) ? CHOOMAH : previousId;
        List<String> choices = COMMONS.stream().filter(id -> !id.equals(previous)).toList();
        String id = rng.pick(choices);
        return id.equals(CHOOMAH) ? rng.pick(CHOOMAH_VARIANTS) : id;
    }

    /** Élite au hasard, différente de {@code previousId}. */
    public static String pickElite(Rng rng, String previousId) {
        return rng.pick(ELITES.stream().filter(id -> !id.equals(previousId)).toList()); // previousId peut être null
    }

    // ===================== Contenu =====================

    private static Map<String, Definition> definitions() {
        Map<String, Definition> all = new LinkedHashMap<>();
        common(all, CADDIE, "Caddie énervé", NEUTRAL, 28, caddie());
        choomah(all, CHOOMAH, "Choomah", 22, 4, 3);
        choomah(all, CHOOMAH_PRESSE, "Choomah pressé", 18, 3, 2);
        choomah(all, CHOOMAH_COSTAUD, "Choomah costaud", 30, 6, 3);
        common(all, CAME, "Camé", FIRE, 25, came());
        common(all, PARESSEUX, "Paresseux", NATURE, 24, paresseux());
        all.put(CLARENCE, () -> Enemy.builder(CLARENCE, "Clarence", NEUTRAL, EnemyRank.COMMON).hp(1).choomies(75)
                .behavior(EnemyBehavior.always(EnemyMove.of("regard", "Regard", "Clarence te regarde."))));
        common(all, SEAU, "Seau de fuite", WATER, 26, seau());
        elite(all, VAFI, "Vafi", FIRE, 55, vafi(), 0);
        elite(all, PENEU, "Le Peneu", NEUTRAL, 90, peneu(), 0.2);
        elite(all, TREX, "T-Rex défoncé", NATURE, 65, EnemyBehavior.always(EnemyMove.of("morsure", "Morsure",
                "Bras trop courts : 30 % de rater ; quand il rate, fringale (il se soigne)",
                new Hit(13, 1, 0, 0.30, 8))), 0);
        elite(all, ETUDIANT, "Étudiant post-examen", WATER, 60, etudiant(), 0);
        common(all, GOUROU, "Gourou du Sat", SPIRIT, 24, gourou());
        common(all, NG, "NG", ALGEBRAIC, 26, ng());
        common(all, HYPERCUBE, "Hypercube misogyne", GEOMETRIC, 25, hypercube());
        common(all, GOBELIN, "Gobelin", NATURE, 22, gobelin());
        elite(all, ENGEANCE, "Engeance des cookies", GLITCH, 60, engeance(), 0);
        all.put(bossId(1), () -> Enemy.builder(bossId(1), "Nortin", NEUTRAL, EnemyRank.BOSS)
        .hp(120)
        .sprite("nortin")
                .behavior(nortin()));
        all.put(bossId(2), () -> Enemy.builder(bossId(2), "Zo le StreMon", ALGEBRAIC, EnemyRank.BOSS)
                .hp(230)
                .sprite("zo")
                .behavior(zo()));
        boss(all, 10, NATURE, 25000, 10, 22);
        return all;
    }

    private static void common(Map<String, Definition> all, String id, String name, Element element, double hp,
            EnemyBehavior behavior) {
        all.put(id, () -> Enemy.builder(id, name, element, EnemyRank.COMMON).hp(hp).behavior(behavior));
    }

    private static void elite(Map<String, Definition> all, String id, String name, Element element, double hp,
            EnemyBehavior behavior, double reflect) {
        all.put(id, () -> Enemy.builder(id, name, element, EnemyRank.ELITE).hp(hp).behavior(behavior).reflect(reflect));
    }

    /** Boss provisoire (nom à venir) : une attaque, et une grosse attaque annoncée toutes les 4. */
    private static void boss(Map<String, Definition> all, int level, Element element, double hp, double hit,
            double heavy) {
        String id = bossId(level);
        EnemyMove normal = EnemyMove.of("coup", "Coup", "Coup", Hit.of(hit));
        EnemyMove big = EnemyMove.of("grosse-attaque", "Grosse attaque", "Grosse attaque, annoncée", Hit.of(heavy));
        all.put(id, () -> Enemy.builder(id, "Boss du niveau " + level, element, EnemyRank.BOSS).hp(hp)
                .sprite("boss").behavior(EnemyBehavior.every(4, big, normal)));
    }

    private static EnemyBehavior caddie() {
        EnemyMove wheel = EnemyMove.of("roue-voilee", "Roue voilée", "Roue voilée", Hit.of(6));
        EnemyMove charge = EnemyMove.of("charge-de-parking", "Charge de parking",
                "Tous les 3 tours : dégâts ×2", Hit.of(12));
        return EnemyBehavior.every(3, charge, wheel);
    }

    /**
     * Choomah : tout ce qu'il dit est un cri. Coup, et tous les {@code period} tours « Vol à la
     * tire » (5 Choomies, sinon 1 clope, sinon un coup).
     */
    private static void choomah(Map<String, Definition> all, String id, String name, double hp, double hit,
            int period) {
        EnemyMove strike = EnemyMove.of("coup", "ooOOOaAAAOaAOOA", "OAUUAOOAOAUAOOOooooooO", Hit.of(hit));
        EnemyMove steal = EnemyMove.of("vol-a-la-tire", "AAAOOAaAAOAOaAOOA", "oOOOOAOOAOAOAOAOOoOOOOOAAAAAAAA",
                new MoveEffect.Pickpocket(5, 1, hit), new MoveEffect.Inflict(INTOX, 1, 0, false));
        all.put(id, () -> Enemy.builder(id, name, NATURE, EnemyRank.COMMON).hp(hp).screams().sprite(id)
                .behavior(EnemyBehavior.every(period, steal, strike)));
    }

    private static EnemyBehavior came() {
        MoveEffect burn = new MoveEffect.Inflict(BURN, CAME_BURN_CHANCE, 0, true);
        EnemyMove hit = EnemyMove.of("coup", "Coup de coude", "Coup de coude (30 % de Brûlure)", Hit.of(5), burn);
        EnemyMove tax = EnemyMove.of("taxe", "Taxe",
                "Te prend 1 clope ; si tu n'en as pas, attaque deux fois (30 % de Brûlure)",
                new MoveEffect.Tax(1, 5, 2), burn);
        return EnemyBehavior.every(2, tax, hit);
    }

    /** Accroché à sa branche : peu de dégâts, et il se balance pour esquiver. */
    private static EnemyBehavior paresseux() {
        EnemyMove claw = EnemyMove.of("griffe", "Griffe molle", "Griffe molle", Hit.of(3));
        EnemyMove swing = EnemyMove.of("badoing", "Badoing, badoing !",
                "Il se balance à sa branche : 50 % d'esquiver ton prochain sort, sinon deux petits coups ;"
                        + " 25 % de t'endormir (Fons 1 tour)",
                new MoveEffect.Gamble(0.5, new MoveEffect.Dodge(), Hit.of(2, 2)),
                new MoveEffect.Inflict(FONS, PARESSEUX_FONS_CHANCE, 1, false));
        return EnemyBehavior.every(2, swing, claw);
    }

    /** Le seau se remplit : 2, 3, 4, 5, puis « Débordement » et retour à zéro. */
    private static EnemyBehavior seau() {
        EnemyMove overflow = EnemyMove.of("debordement", "Débordement", "Le seau déborde !", Hit.of(14));
        return self -> {
            int step = self.actions() % 5;
            if (step == 4) {
                return overflow;
            }
            return EnemyMove.of("ca-goutte", "Ça goutte", "Le seau se remplit : +1 dégât par tour",
                    Hit.of(2 + step));
        };
    }

    private static EnemyBehavior vafi() {
        EnemyMove hit = EnemyMove.of("correction", "Correction", "Correction", Hit.of(8));
        EnemyMove series = EnemyMove.of("serie-d-algebre", "Série d'algèbre",
                "Une carte de ta main devient « Exercice 3b » (injouable pendant ce combat), et tu"
                        + " t'endors en cours (Fons 1 tour)",
                new MoveEffect.CurseCard("Exercice 3b"), new MoveEffect.Inflict(FONS, 1, 1, false));
        EnemyMove pause = EnemyMove.of("pause-petou", "Pause pétou", "Se soigne et n'attaque pas",
                new MoveEffect.HealSelf(12));
        return self -> {
            if (self.hpRatio() < 0.5 && self.uses(pause.id()) < 2) {
                return pause;
            }
            return (self.actions() + 1) % 3 == 0 ? series : hit;
        };
    }

    /** Gourou du Sat (spirituel) : Coup d'encens ; tous les 3 tours, Regard fixe (t'hypnotise 1 tour). */
    private static EnemyBehavior gourou() {
        EnemyMove incense = EnemyMove.of("coup-d-encens", "Coup d'encens", "Coup d'encens", Hit.of(4));
        EnemyMove stare = EnemyMove.of("regard-fixe", "Regard fixe",
                "Il te fixe : tu es hypnotisé, son prochain coup fait ×2",
                new MoveEffect.Inflict(HYPNOTIZED, 1, 1, false));
        return EnemyBehavior.every(3, stare, incense);
    }

    /** NG (algébrique) : Démonstration ; tous les 3 tours, Contre-exemple (une carte devient « Exercice 3b »). */
    private static EnemyBehavior ng() {
        EnemyMove proof = EnemyMove.of("demonstration", "Démonstration", "Démonstration", Hit.of(5));
        EnemyMove counter = EnemyMove.of("contre-exemple", "Contre-exemple",
                "Une carte de ta main devient « Exercice 3b » (injouable pendant ce combat)",
                new MoveEffect.CurseCard("Exercice 3b"));
        return EnemyBehavior.every(3, counter, proof);
    }

    /** Hypercube misogyne (géométrique) : Tunnel topologique ; tous les 3 tours, Célibat forcé (2 coups). */
    private static EnemyBehavior hypercube() {
        EnemyMove tunnel = EnemyMove.of("tunnel-topologique", "Tunnel topologique", "Tunnel topologique", Hit.of(5));
        EnemyMove celibacy = EnemyMove.of("celibat-force", "Célibat forcé", "Ouin ouin : deux coups", Hit.of(4, 2));
        return EnemyBehavior.every(3, celibacy, tunnel);
    }

    /** Gobelin (plante) : « 67 », 6 ou 7 dégâts (six seveeen). */
    private static EnemyBehavior gobelin() {
        return EnemyBehavior.always(EnemyMove.of(SIX_SEVEN, "67", "Six seveeen ! 6 ou 7 dégâts",
                new MoveEffect.Gamble(0.5, Hit.of(6), Hit.of(7))));
    }

    /** Identifiant de l'attaque « 67 » du Gobelin (animation et texte flottant). */
    public static final String SIX_SEVEN = "six-seven";

    /**
     * Engeance des cookies (GLITCH, défoncée au space cookie) : Piège pâtissier ; tous les 3 tours,
     * Green out (t'endort 1 tour) ; une fois sous 50 % PV, Méditation triptyque (change d'élément).
     */
    private static EnemyBehavior engeance() {
        EnemyMove trap = EnemyMove.of("piege-patissier", "Piège pâtissier", "Piège pâtissier", Hit.of(9));
        EnemyMove greenOut = EnemyMove.of("green-out", "Green out", "Tu t'endors (Fons 1 tour)",
                new MoveEffect.Inflict(FONS, 1, 1, false));
        EnemyMove meditation = EnemyMove.of("meditation-triptyque", "Méditation triptyque",
                "Change d'élément jusqu'à la fin du combat", new MoveEffect.ChangeElement());
        return self -> {
            if (self.hpRatio() < 0.5 && self.uses(meditation.id()) < 1) {
                return meditation;
            }
            return (self.actions() + 1) % 3 == 0 ? greenOut : trap;
        };
    }

    /** Rebond (passif) et « Crevaison », qui monte de 2 à chaque utilisation. */
    private static EnemyBehavior peneu() {
        return self -> EnemyMove.of("crevaison", "Crevaison",
                "Monte à chaque utilisation. Rebond : te renvoie 20 % des dégâts reçus",
                Hit.of(6 + 2 * self.uses("crevaison")));
    }

    private static EnemyBehavior etudiant() {
        EnemyMove tears = EnemyMove.of("larmes", "Larmes", "Plus fortes si ta main est presque vide",
                new Hit(6, 1, 3, 0, 0));
        EnemyMove crisis = EnemyMove.of("crise-de-nerfs", "Crise de nerfs", "Deux attaques par tour",
                new Hit(6, 2, 3, 0, 0));
        return self -> self.hpRatio() < 0.5 ? crisis : tears;
    }


    private static EnemyBehavior nortin() {
        EnemyMove bitchSlap = EnemyMove.of("bitchslap", "Bitch Slap", "bcz he's a bitch",
                Hit.of(8));
        EnemyMove dodge = EnemyMove.of("dodge", "Esquive",
                "Il se cache comme... you know what : 70 % d'esquiver ton prochain sort, sinon bitch slap ",
                new MoveEffect.Gamble(0.7, new MoveEffect.Dodge(), Hit.of(8)));
        EnemyMove steal = EnemyMove.of("vol-trinket", "Vole de trinket", "Il a 20 % de chance de te voler un trinket",
                new MoveEffect.StealTrinket(0.2, 8));
        EnemyMove kickNuts = EnemyMove.of("kicknuts", "Hit in the nuts", "Yes like reeeaaal b...",
                Hit.of(16));

        return self -> switch (self.actions() % 4) {
            case 0 -> bitchSlap;
            case 1 -> dodge;
            case 2 -> steal;
            default -> kickNuts;
        };
    }


    private static EnemyBehavior zo() {
        EnemyMove coupTeub = EnemyMove.of("coup-de-teub", "Coup de teub", "Il va te mettre un gros coup de reins",
                Hit.of(8));
        EnemyMove regen = EnemyMove.of("petite-clope", "Ptite clope",
                "Il se fait une petite clope",
                new MoveEffect.HealSelf(10));
        EnemyMove ptitVerre = EnemyMove.of("ptit-verre", "Un dernier p'tit verre", "Il fait monter ton ivresse",
                Hit.of(5),new MoveEffect.Drink(1));
        EnemyMove capoeira = EnemyMove.of("capoeira", "move de Capoeira", "Un backflip ou une danse on ne sait pas trop",
                Hit.of(12));

        return self -> switch (self.actions() % 6) {
            //case 0 -> coupTeub;
            //case 1 -> coupTeub;
            case 2 -> ptitVerre;
            //case 3 -> ;
            case 4 -> regen;
            case 5 -> capoeira;
            default -> coupTeub;
        };
    }

}
