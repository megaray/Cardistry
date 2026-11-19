package cardistry.model.card;

import java.util.ArrayList;
import java.util.List;

import cardistry.core.Rng;
import cardistry.core.stats.StatType;
import cardistry.core.status.StatusExtras;
import cardistry.core.status.StatusTarget;
import cardistry.core.status.StatusType;
import static cardistry.core.stats.StatModifier.flat;
import static cardistry.core.stats.StatModifier.percent;
import static cardistry.items.Rarity.COMMON;
import static cardistry.items.Rarity.LEGENDARY;
import static cardistry.items.Rarity.RARE;
import static cardistry.items.Rarity.UNCOMMON;
import static cardistry.model.Element.ALGEBRAIC;
import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.GEOMETRIC;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.NEUTRAL;
import static cardistry.model.Element.SPIRIT;
import static cardistry.model.Element.WATER;

/**
 * Registre des cartes : tirées pour les récompenses de cartes et les effets qui créent une carte
 * (George), et pour la pioche des parties sans deck (tests). Une carte présente plusieurs fois a
 * plus de chances d'être tirée.
 */
public class CardPool {

    private final List<Card> cards = new ArrayList<>();

    /** Pool de cartes personnalisé (tests, variantes). */
    public CardPool(List<Card> cards) {
        if (cards.isEmpty()) {
            throw new IllegalArgumentException("Le pool est vide");
        }
        this.cards.addAll(cards);
    }

    /** Pool standard du jeu. */
    public CardPool() {
        // --- Sortilèges (nom, élément, dégâts) ---
        // Commun
        cards.add(bouleDeFeu());
        cards.add(jetDEau());
        cards.add(fouetDeLianes());
        cards.add(petDeMamouz());
        cards.add(carteBasiquePasOuf());
        cards.add(lancerDeManette());
        cards.add(cayouuu());
        cards.add(painPita());
        cards.add(matrix());
        // Peu commun
        cards.add(pluieDeFlammes());
        cards.add(tsunami());
        cards.add(tempeteDEpines());
        cards.add(moindreCarre());
        cards.add(distPlayer());
        cards.add(somnifere());
        cards.add(reveilBrutal());
        // Rare
        cards.add(purificationDesChakra());
        cards.add(pyromane());
        cards.add(contreExemple());
        // Légendaire
        cards.add(bigLezShotgun());
        cards.add(professeurDOr());

        // --- Terrains (nom, élément) ---
        // Commun
        cards.add(new TerrainCard("Marécage", WATER, COMMON));
        cards.add(new TerrainCard("Forêt", NATURE, COMMON));
        cards.add(new TerrainCard("Volcan", FIRE, COMMON));
        cards.add(dojo());
        cards.add(new TerrainCard("Migros", NEUTRAL, COMMON).withBonuses(
                "si le combat se termine ici : +25 % de Choomies", percent(StatType.CHOOMIES_GAIN, 0.25, "")));
        cards.add(new TerrainCard("Gare", NEUTRAL, COMMON).withEffect(TerrainCard.Effect.FREE_DEPARTURE,
                "jouer un terrain depuis la Gare ne coûte pas le tour et donne +1 clope"));
        // Peu commun
        cards.add(new TerrainCard("Amazonie", NATURE, UNCOMMON).withBonuses("attaques plante +20 %",
                percent(StatType.NATURE_DAMAGE, ELEMENT_TERRAIN_BONUS, "")));
        cards.add(new TerrainCard("Enfer", FIRE, UNCOMMON).withBonuses("attaques feu +20 %",
                percent(StatType.FIRE_DAMAGE, ELEMENT_TERRAIN_BONUS, "")));
        cards.add(new TerrainCard("Atlantis", WATER, UNCOMMON).withBonuses("attaques eau +20 %",
                percent(StatType.WATER_DAMAGE, ELEMENT_TERRAIN_BONUS, "")));
        // Rare
        cards.add(new TerrainCard("Désert kaléidoscopique", SPIRIT, RARE)
                .withBonuses("+1 ESP", flat(StatType.MIND, TERRAIN_STAT_BONUS, ""))
                .withEffect(TerrainCard.Effect.DEEP_HYPNOSIS, "l'hypnose fait ×4 au lieu de ×2"));
        cards.add(new TerrainCard("Espace de dimension n", ALGEBRAIC, RARE)
                .withBonuses("+1 INT", flat(StatType.INTELLIGENCE, TERRAIN_STAT_BONUS, ""))
                .withEffect(TerrainCard.Effect.REPLAY,
                        "chaque carte jouée ici est rejouée une fois à 50 % au tour suivant, si tu y es encore"));
        cards.add(new TerrainCard("Fractale", GEOMETRIC, RARE).withEffect(TerrainCard.Effect.CEILING,
                "tous les multiplicateurs (toi et l'adversaire) arrondis à l'entier supérieur"));
        // Légendaire
        cards.add(epfl());

        // --- Soins (nom, élément, PV rendus) ---
        // Commun
        cards.add(new HealCard("Potion", NATURE, 10.0, COMMON));
        cards.add(petiteTaffe());
        cards.add(new HealCard("Verre d'eau", WATER, 10, COMMON));
        cards.add(sparadrap());
        cards.add(soupeDeMamie());
        cards.add(stinginRoger());
        // Peu commun
        cards.add(new HealCard("Seau d'eau", WATER, 20, UNCOMMON));
        cards.add(berceuseInconnue());
        cards.add(new HealCard("Grande potion", NATURE, 20.0, UNCOMMON));
        // Rare
        cards.add(taffeDeFoufouBatard());
    }

    /** Cartes du registre (avec leurs doublons), en lecture seule. */
    public List<Card> cards() {
        return java.util.Collections.unmodifiableList(cards);
    }

    /**
     * Deck de départ (12 cartes) : 6 sorts (un de 10 et un de 15 par élément), 3 soins, 3 terrains.
     * Le terrain de départ (EPFL) est en jeu, pas dans le deck.
     */
    public static List<Card> startingDeck() {
        return List.of(
            bouleDeFeu(),
            jetDEau(),
            fouetDeLianes(),
            petDeMamouz(),
            carteBasiquePasOuf(),
            lancerDeManette(),
            new HealCard("Potion", NATURE, 10.0, COMMON),
            new HealCard("Potion", NATURE, 10.0, COMMON),
            new HealCard("Potion", NATURE, 10.0, COMMON),
            new TerrainCard("Marécage", WATER, COMMON),
            new TerrainCard("Forêt", NATURE, COMMON),
            new TerrainCard("Volcan", FIRE, COMMON)

        );
    }

    /** Terrain sur lequel commence la partie. */
    public static TerrainCard startingTerrain() {
        return dojo();
    }

    /** Stat donnée par le Dojo (FOR), le Désert kaléidoscopique (ESP) et l'Espace de dimension n (INT). */
    public static final double TERRAIN_STAT_BONUS = 1;
    /** Bonus d'Amazonie, Enfer, Atlantis à leur élément. */
    public static final double ELEMENT_TERRAIN_BONUS = 0.20;

    // --- Cartes à pouvoir partagées (pool, deck de départ, événements, trinkets) ---

    public static final double BOULE_DE_FEU_BURN = 0.10;
    public static final double FOUET_BONDAGE = 0.10;
    public static final double PETITE_TAFFE_FONS = 0.15;
    /** Soin par tour (en part des PV max) pendant la Fons de la Petite taffe. */
    public static final double PETITE_TAFFE_HEAL_PER_TURN = 0.05;

    /** Boule de feu : 10 dégâts feu, 10 % d'infliger Brûlure. */
    public static SpellCard bouleDeFeu() {
        return new SpellCard("Boule de feu", FIRE, 10, COMMON).withPower("10 % d'infliger Brûlure",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.BURN, BOULE_DE_FEU_BURN));
    }

    /** Fouet de lianes : 10 dégâts plante, 10 % d'infliger Bondage. */
    public static SpellCard fouetDeLianes() {
        return new SpellCard("Fouet de lianes", NATURE, 10, COMMON).withPower("10 % d'infliger Bondage",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.BONDAGE, FOUET_BONDAGE));
    }

    /** Pet de Mamouz : aucun dégât, Intox à coup sûr. */
    public static SpellCard petDeMamouz() {
        return new SpellCard("Pet de Mamouz", NEUTRAL, 0, COMMON).withPower("inflige Intox",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.INTOX, 1));
    }

    /**
     * Petite taffe (ex-Petit pétou) : soin feu de 15 ; 15 % de tomber en Fons, pendant laquelle tu
     * récupères 5 % de tes PV max par tour.
     */
    public static HealCard petiteTaffe() {
        return new HealCard("Petite taffe", FIRE, 15, COMMON).withPower(
                "15 % de tomber en Fons (+5 % de tes PV max par tour pendant la Fons)",
                ctx -> ctx.applyStatus(StatusTarget.PLAYER, StatusType.FONS, PETITE_TAFFE_FONS, 0,
                        new StatusExtras(PETITE_TAFFE_HEAL_PER_TURN, false)));
    }

    // --- Sorts du plan V3 (stats) ---

    /** DEF perdue par l'adversaire, pour le combat, à chaque Jet d'eau. */
    public static final double JET_D_EAU_DEFENSE_LOSS = 1;
    public static final double PLUIE_DE_FLAMMES_BURN = 0.20;
    public static final double TEMPETE_D_EPINES_BONDAGE = 0.20;
    /** CHA à partir de laquelle le Lancer de manette est toujours critique. */
    public static final int LANCER_DE_MANETTE_LUCK = 3;
    /** DEF ignorée par CAYOUUU. */
    public static final double CAYOUUU_PIERCE = 2;
    /** FOR perdue pour le reste du combat après un Big Lez Shotgun (recul). */
    public static final double BIG_LEZ_RECOIL = 2;

    /** Jet d'eau : 10 dégâts eau ; l'adversaire perd 1 DEF pour le combat (l'eau use). */
    public static SpellCard jetDEau() {
        return new SpellCard("Jet d'eau", WATER, 10, COMMON).withPower("l'adversaire perd 1 DEF pour le combat",
                ctx -> ctx.addCombatStat(StatusTarget.ENEMY, StatType.DEFENSE, -JET_D_EAU_DEFENSE_LOSS));
    }

    /** Tsunami : 15 dégâts eau, ignore la DEF (la vague passe par-dessus tout). */
    public static SpellCard tsunami() {
        return new SpellCard("Tsunami", WATER, 15, UNCOMMON).piercing(Double.POSITIVE_INFINITY)
                .withPower("ignore la DEF", ctx -> { });
    }

    /** Pluie de flammes : 15 dégâts feu, 20 % d'infliger Brûlure. */
    public static SpellCard pluieDeFlammes() {
        return new SpellCard("Pluie de flammes", FIRE, 15, UNCOMMON).withPower("20 % d'infliger Brûlure",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.BURN, PLUIE_DE_FLAMMES_BURN));
    }

    /** Tempête d'épines : 15 dégâts plante, 20 % d'infliger Bondage. */
    public static SpellCard tempeteDEpines() {
        return new SpellCard("Tempête d'épines", NATURE, 15, UNCOMMON).withPower("20 % d'infliger Bondage",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.BONDAGE, TEMPETE_D_EPINES_BONDAGE));
    }

    /** Lancer de manette : 12 dégâts neutres, toujours critique avec 3 CHA ou plus. */
    public static SpellCard lancerDeManette() {
        return new SpellCard("Lancer de manette", NEUTRAL, 12, COMMON).criticalFrom(LANCER_DE_MANETTE_LUCK)
                .withPower("critique garanti si CHA ≥ " + LANCER_DE_MANETTE_LUCK, ctx -> { });
    }

    /** CAYOUUU : 12 dégâts neutres, ignore 2 points de DEF. */
    public static SpellCard cayouuu() {
        return new SpellCard("CAYOUUU", NEUTRAL, 12, COMMON).piercing(CAYOUUU_PIERCE)
                .withPower("ignore 2 points de DEF", ctx -> { });
    }

    /** Pain pita : 10 dégâts géométriques, ignore la DEF (trouver le bon angle). */
    public static SpellCard painPita() {
        return new SpellCard("Pain pita", GEOMETRIC, 10, COMMON).piercing(Double.POSITIVE_INFINITY)
                .withPower("ignore la DEF", ctx -> { });
    }

    /** Big Lez Shotgun : 80 dégâts neutres ; recul : −2 FOR pour le reste du combat. */
    public static SpellCard bigLezShotgun() {
        return new SpellCard("Big Lez Shotgun", NEUTRAL, 80, LEGENDARY).withPower(
                "recul : −2 FOR pour le reste du combat",
                ctx -> ctx.addCombatStat(StatusTarget.PLAYER, StatType.STRENGTH, -BIG_LEZ_RECOIL));
    }

    /** Matrix : dégâts de base, doublés si les PV de l'adversaire finissent par 0 ou 1 (avant le coup). */
    public static final double MATRIX_DAMAGE = 10;

    /** Matrix : 10 dégâts, ×2 si les PV de l'adversaire finissent par 0 ou 1 (vérifié avant le coup). */
    public static SpellCard matrix() {
        return new SpellCard("Matrix", ALGEBRAIC, MATRIX_DAMAGE, COMMON).withDamageRule("10 / 20",
                ctx -> {
                    int lastDigit = Math.floorMod(ctx.enemyHp(), 10);
                    return lastDigit == 0 || lastDigit == 1 ? 2 * MATRIX_DAMAGE : MATRIX_DAMAGE;
                }).withPower("×2 si les PV de l'adversaire finissent par 0 ou 1", ctx -> { });
    }

    public static final int MOINDRE_CARRE_MIN = 1;
    /** Maximum du tirage sans INT : il monte de {@link #MOINDRE_CARRE_PER_INT} par point d'INT. */
    public static final int MOINDRE_CARRE_MAX = 30;
    public static final int MOINDRE_CARRE_PER_INT = 2;

    /**
     * Moindre carré : dégâts tirés entre 1 et 30 + 2 × INT (tirage en ta faveur : deux avec CLAUDE.md,
     * le meilleur). L'INT est déjà dans le tirage : la formule ne l'ajoute pas une seconde fois.
     */
    public static SpellCard moindreCarre() {
        return new SpellCard("Moindre carré", ALGEBRAIC, 0, UNCOMMON).withDamageRule(
                MOINDRE_CARRE_MIN + "-" + MOINDRE_CARRE_MAX + "+2×INT",
                ctx -> {
                    int max = Math.max(MOINDRE_CARRE_MIN, MOINDRE_CARRE_MAX + (int) Math.floor(
                            MOINDRE_CARRE_PER_INT * ctx.state().playerStats().get(StatType.INTELLIGENCE)));
                    return ctx.favorable(() -> MOINDRE_CARRE_MIN + ctx.rng().nextInt(max - MOINDRE_CARRE_MIN + 1),
                            Integer::compare);
                }).statInDamage();
    }

    public static final double CARTE_BASIQUE_DAMAGE = 6;
    public static final double CARTE_BASIQUE_PER_STRENGTH = 2;

    /**
     * Carte basique pas ouf : 6 + 2 × FOR dégâts neutres (la FOR est comptée par la carte, pas une
     * seconde fois par la formule). « Pas ouf, sauf si t'es fort. »
     */
    public static SpellCard carteBasiquePasOuf() {
        return new SpellCard("Carte basique pas ouf", NEUTRAL, CARTE_BASIQUE_DAMAGE, COMMON).withDamageRule(
                "6+2×FOR", ctx -> CARTE_BASIQUE_DAMAGE
                        + CARTE_BASIQUE_PER_STRENGTH * ctx.state().playerStats().get(StatType.STRENGTH))
                .statInDamage();
    }

    public static final double DIST_PLAYER_DAMAGE = 10;

    /** dist(PLAYER) : 10 dégâts + le nombre d'étages parcourus. */
    public static SpellCard distPlayer() {
        return new SpellCard("dist(PLAYER)", GEOMETRIC, DIST_PLAYER_DAMAGE, UNCOMMON).withDamageRule(
                "10 + étages", ctx -> DIST_PLAYER_DAMAGE + ctx.floorsTraveled());
    }

    /** Purification des Chakra : aucun dégât, l'adversaire est hypnotisé pour ton prochain tour. */
    public static SpellCard purificationDesChakra() {
        return new SpellCard("Purification des Chakra", SPIRIT, 0, RARE).withPower(
                "hypnotise l'adversaire (tes attaques ×2 à ton prochain tour)",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.HYPNOTIZED, 1, 1));
    }

    public static final double PROFESSEUR_D_OR_DAMAGE = 35;
    public static final int PROFESSEUR_D_OR_HYPNOSIS = 5;

    /** Professeur d'or : 35 dégâts non multipliés, puis l'adversaire est hypnotisé 5 tours. */
    public static SpellCard professeurDOr() {
        return new SpellCard("Professeur d'or", SPIRIT, PROFESSEUR_D_OR_DAMAGE, LEGENDARY).unmultiplied().withPower(
                "hypnotise l'adversaire pendant " + PROFESSEUR_D_OR_HYPNOSIS + " tours",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.HYPNOTIZED, 1, PROFESSEUR_D_OR_HYPNOSIS));
    }

    public static final double PYROMANE_DAMAGE = 15;

    /** Pyromane : 15 dégâts feu, ×2 si l'adversaire brûle. */
    public static SpellCard pyromane() {
        return new SpellCard("Pyromane", FIRE, PYROMANE_DAMAGE, RARE).withDamageRule("15 / 30",
                ctx -> ctx.state().statuses(StatusTarget.ENEMY).has(StatusType.BURN)
                        ? 2 * PYROMANE_DAMAGE : PYROMANE_DAMAGE)
                .withPower("×2 si l'adversaire brûle", ctx -> { });
    }

    /** Somnifère : aucun dégât, l'adversaire s'endort (Fons 2 tours). */
    public static SpellCard somnifere() {
        return new SpellCard("Somnifère", SPIRIT, 0, UNCOMMON).withPower("endort l'adversaire (Fons 2 tours)",
                ctx -> ctx.applyStatus(StatusTarget.ENEMY, StatusType.FONS, 1, 2));
    }

    public static final double REVEIL_BRUTAL_ASLEEP = 20;
    public static final double REVEIL_BRUTAL_AWAKE = 8;

    /** Réveil brutal : 20 dégâts à un adversaire en Fons (et le réveille), sinon 8. */
    public static SpellCard reveilBrutal() {
        return new SpellCard("Réveil brutal", NEUTRAL, REVEIL_BRUTAL_AWAKE, UNCOMMON).withDamageRule("8 / 20",
                ctx -> ctx.state().statuses(StatusTarget.ENEMY).has(StatusType.FONS)
                        ? REVEIL_BRUTAL_ASLEEP : REVEIL_BRUTAL_AWAKE)
                .withPower("20 si l'adversaire dort (Fons), et il se réveille",
                        ctx -> ctx.removeStatus(StatusTarget.ENEMY, StatusType.FONS));
    }

    /** INT par statut annulé en plus par le Contre-exemple. */
    public static final int CONTRE_EXEMPLE_INT_PER_STATUS = 3;

    /**
     * Contre-exemple : aucun dégât, annule les 1 + INT ÷ 3 (arrondi en dessous) prochains statuts que
     * tu subirais (pendant ce combat).
     */
    public static SpellCard contreExemple() {
        return new SpellCard("Contre-exemple", ALGEBRAIC, 0, RARE).withPower(
                "annule les 1 + INT ÷ 3 prochains statuts que tu subirais (ce combat)",
                ctx -> {
                    int count = 1 + Math.max(0, (int) Math.floor(
                            ctx.state().playerStats().get(StatType.INTELLIGENCE) / CONTRE_EXEMPLE_INT_PER_STATUS));
                    ctx.addEffect(cardistry.core.TemporaryEffect.builder("contre-exemple", "Contre-exemple")
                            .summary(count > 1 ? "annule les " + count + " prochains statuts" : "annule le prochain statut")
                            .charges(count).cancelsNextStatus().build());
                });
    }

    /** Soupe de mamie : +10 PV, retire l'Intox et +1 RES pour le combat (la soupe de mamie immunise). */
    public static HealCard soupeDeMamie() {
        return new HealCard("Soupe de mamie", NEUTRAL, 10, COMMON).withPower("retire l'Intox ; +1 RES pour le combat",
                ctx -> {
                    ctx.removeStatus(StatusTarget.PLAYER, StatusType.INTOX);
                    ctx.addCombatStat(StatusTarget.PLAYER, StatType.RESISTANCE, 1);
                });
    }

    /** Sparadrap : +12 PV et +1 DEF pour le combat. */
    public static HealCard sparadrap() {
        return new HealCard("Sparadrap", NEUTRAL, 12, COMMON).withPower("+1 DEF pour le combat",
                ctx -> ctx.addCombatStat(StatusTarget.PLAYER, StatType.DEFENSE, 1));
    }

    public static final double STINGIN_ROGER_COST = 5;
    /** FOR gagnée pour tout le combat par Stingin' Roger. */
    public static final double STINGIN_ROGER_STRENGTH = 1;

    /** Stingin' Roger : −5 PV, +1 FOR pour tout le combat (cumulable). */
    public static HealCard stinginRoger() {
        return new HealCard("Stingin' Roger", FIRE, 0, COMMON).withPower(
                "−5 PV, +1 FOR pour tout le combat (cumulable)", ctx -> {
                    ctx.damagePlayer(STINGIN_ROGER_COST);
                    ctx.addCombatStat(StatusTarget.PLAYER, StatType.STRENGTH, STINGIN_ROGER_STRENGTH);
                });
    }

    /** PV rendus par la Berceuse inconnue par point d'ESP. */
    public static final double BERCEUSE_HEAL_PER_MIND = 2;

    /**
     * Berceuse inconnue : retire tous tes statuts négatifs (pas la Gueule de bois ni l'Ivresse), puis
     * soigne de 2 × ESP PV (soin spirituel : terrain et bonus de soin, sans ESP en plus).
     */
    public static HealCard berceuseInconnue() {
        return new HealCard("Berceuse inconnue", SPIRIT, 0, UNCOMMON).withPower(
                "retire tous tes statuts négatifs (Intox, Fons, Bondage, Brûlure) ; +2 × ESP PV", ctx -> {
                    ctx.purgeNegativeStatuses();
                    double heal = BERCEUSE_HEAL_PER_MIND * ctx.state().playerStats().get(StatType.MIND);
                    if (heal > 0) {
                        ctx.elementalHeal(heal, SPIRIT);
                    }
                });
    }

    public static final int TAFFE_FOUFOU_TURNS = 3;
    public static final double TAFFE_FOUFOU_HEAL_PER_TURN = 0.08;

    /**
     * Taffe de foufou bâtard : Fons 3 tours ; pendant cette Fons, immunisé aux coups adverses et +8 %
     * des PV max par tour (les statuts infligés par les adversaires passent toujours).
     */
    public static HealCard taffeDeFoufouBatard() {
        return new HealCard("Taffe de foufou bâtard", FIRE, 0, RARE).withPower(
                "Fons " + TAFFE_FOUFOU_TURNS + " tours : immunisé aux coups, +8 % de tes PV max par tour",
                ctx -> ctx.applyStatus(StatusTarget.PLAYER, StatusType.FONS, 1, TAFFE_FOUFOU_TURNS,
                        new StatusExtras(TAFFE_FOUFOU_HEAL_PER_TURN, true)));
    }

    /** Tours passés à l'EPFL avant le retour automatique au Dojo. */
    public static final int EPFL_TURNS = 3;

    /**
     * EPFL : pendant {@link #EPFL_TURNS} tours, toutes les valeurs du joueur sont doublées (dégâts,
     * soins, réduction de dégâts, PV max ; PV actuels ×2 à l'arrivée, ÷2 au départ). Pas la taille de
     * main, les Choomies ni les clopes.
     */
    public static TerrainCard epfl() {
        java.util.List<cardistry.core.stats.StatModifier> doubled = new java.util.ArrayList<>();
        for (StatType stat : StatType.allDamageMultipliers()) {
            doubled.add(cardistry.core.stats.StatModifier.multiply(stat, 2, ""));
        }
        for (StatType stat : java.util.List.of(StatType.HEAL_EFFICIENCY, StatType.DEFENSE, StatType.MAX_HP)) {
            doubled.add(cardistry.core.stats.StatModifier.multiply(stat, 2, ""));
        }
        return new TerrainCard("EPFL", FIRE, LEGENDARY)
                .withBonuses("", doubled)
                .withEffect(TerrainCard.Effect.EPFL, "pendant " + EPFL_TURNS + " tours, tout est doublé : dégâts,"
                        + " soins, réduction, PV (÷2 en partant) ; puis retour au Dojo");
    }

    /** Dojo : terrain de départ, +1 FOR (on s'entraîne). */
    public static TerrainCard dojo() {
        return new TerrainCard("Dojo", NEUTRAL, COMMON).withBonuses("+1 FOR", flat(StatType.STRENGTH, TERRAIN_STAT_BONUS, ""));
    }

    /** Tire une carte au hasard et en renvoie une copie. */
    /**
     * Tire une carte en pondérant chaque entrée du registre par {@code weight} (1 = normal) ;
     * renvoie une copie. Si tous les poids valent 1, équivaut à {@link #draw(Rng)}.
     */
    public Card draw(Rng rng, java.util.function.ToDoubleFunction<Card> weight) {
        double[] weights = new double[cards.size()];
        double total = 0;
        boolean uniform = true;
        for (int i = 0; i < cards.size(); i++) {
            weights[i] = Math.max(0, weight.applyAsDouble(cards.get(i)));
            total += weights[i];
            uniform &= weights[i] == 1.0;
        }
        if (uniform || total <= 0) {
            return draw(rng);
        }
        double roll = rng.nextDouble() * total;
        for (int i = 0; i < cards.size(); i++) {
            roll -= weights[i];
            if (roll < 0) {
                return cards.get(i).copy();
            }
        }
        return cards.get(cards.size() - 1).copy();
    }

    public Card draw(Rng rng) {
        return rng.pick(cards).copy();
    }
}
