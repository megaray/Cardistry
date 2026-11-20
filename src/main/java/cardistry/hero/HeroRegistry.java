package cardistry.hero;

import java.util.List;
import java.util.NoSuchElementException;

import cardistry.core.EffectContext;
import cardistry.core.event.GameEvent;
import cardistry.core.event.GameHooks;
import cardistry.core.stats.StatModifier;
import cardistry.core.stats.StatType;
import cardistry.model.Element;

/** Registre des personnages jouables : tout leur contenu (chiffres compris) est ici. */
public final class HeroRegistry {

    // --- Louis : MAN ---
    /** Bonus de dégâts permanent (tous éléments, additionné aux autres pourcentages). */
    public static final double MAN_DAMAGE_BONUS = 0.5;

    // --- Sassy : wadayatalkinabit ---
    /** PV max en plus. */
    public static final double SASSY_BONUS_HP = 30;
    /** Dégâts bruts de l'alien, à chaque tour. */
    public static final double ALIEN_DAMAGE = 4;
    /** Chance que l'alien rate son tour (il fume un joint). */
    public static final double ALIEN_MISS_CHANCE = 0.30;
    /** PV de l'alien. */
    public static final double ALIEN_HP = 20;
    /** Chance qu'une attaque adverse vise l'alien à la place de Sassy (tirage de l'adversaire). */
    public static final double ALIEN_TARGET_CHANCE = 0.25;
    /** L'alien de Sassy : un familier. */
    public static final cardistry.model.Familiar.Spec ALIEN =
            new cardistry.model.Familiar.Spec("alien", "Alien", ALIEN_HP, ALIEN_TARGET_CHANCE);

    // --- Stats de base (FOR, INT, ESP, DEF, RES, CHA) : 11 points pour Louis, 9 pour Dave et Sassy, 12 pour Frederic BLC ---
    public static final int[] LOUIS_STATS = {3, 2, 1, 3, 1, 1};
    public static final int[] DAVE_STATS = {2, 3, 1, 1, 1, 1};
    public static final int[] SASSY_STATS = {2, 0, 2, 2, 2, 1};
    public static final int[] FREDERIC_STATS = {2, 2, 2, 2, 2, 2};

    public static final Hero LOUIS = new Hero("louis", "Louis", "MAN",
            "+50 % de dégâts, tout le temps.",
            with(stats("louis", LOUIS_STATS), StatType.allDamageMultipliers().stream()
                    .map(stat -> StatModifier.percent(stat, MAN_DAMAGE_BONUS, Hero.modifierSource("louis"))).toList()),
            new GameHooks() {
            });

    public static final Hero DAVE = new Hero("dave", "Dave", "Full skill Mr. Le Juge",
            "Un sort du même élément que le terrain te soigne les PV que tu enlève à l'ennemi",
            stats("dave", DAVE_STATS),
            new GameHooks() {
                @Override
                public void onSpellHit(GameEvent.SpellHit event, EffectContext ctx) {
                    Element spell = event.spell().element();
                    if (spell == ctx.state().terrain().element()) {
                        ctx.announce("Full skill Mr. Le Juge : le sort te soigne.");
                        ctx.healPlayer(event.hpLost());
                    }
                }
            });

    public static final Hero SASSY = new Hero("sassy", "Sassy", "wadayatalkinabit",
            "+30 PV max. À chaque tour, son alien (20 PV) inflige 4 dégâts à l'adversaire (30 % de chance qu'il"
                    + " fume un joint à la place). 25 % des attaques adverses le visent ; à 0 PV, il est assommé"
                    + " jusqu'au combat suivant.",
            with(stats("sassy", SASSY_STATS),
                    List.of(StatModifier.flat(StatType.MAX_HP, SASSY_BONUS_HP, Hero.modifierSource("sassy")))),
            new GameHooks() {
                @Override
                public void onTurnStart(GameEvent.TurnStarted event, EffectContext ctx) {
                    if (!ctx.state().hasEnemy() || !ctx.state().currentEnemy().isAlive()) {
                        return;
                    }
                    if (ctx.state().familiar().map(cardistry.model.Familiar::isKnockedOut).orElse(false)) {
                        return; // assommé jusqu'à la fin du combat
                    }
                    // Tirage défavorable au joueur : pas un tirage de chance (CLAUDE.md ne le double pas).
                    if (ctx.rng().nextDouble() < ALIEN_MISS_CHANCE) {
                        ctx.announce("L'alien rate son tour : il fume un joint à la place.");
                        ctx.familiarActs(GameEvent.FamiliarActed.Action.SMOKE);
                    } else {
                        ctx.announce("L'alien de Sassy attaque.");
                        ctx.familiarActs(GameEvent.FamiliarActed.Action.ATTACK);
                        ctx.damageEnemy(ALIEN_DAMAGE);
                    }
                }
            }, ALIEN);

    /** Frederic BLC : aucune compétence, rien de spécial. */
    public static final Hero FREDERIC = new Hero("frederic-blc", "Frederic BLC", "Aucune",
            "Aucune compétence, mais des stats parfaitement moyennes : 2 partout.", stats("frederic-blc", FREDERIC_STATS),
            new GameHooks() {
            });

    private static final List<Hero> ALL = List.of(LOUIS, DAVE, SASSY, FREDERIC);

    private HeroRegistry() {
    }

    /** Stats de base du personnage {@code id} (FOR, INT, ESP, DEF, RES, CHA), en modificateurs plats. */
    private static List<StatModifier> stats(String id, int[] values) {
        List<StatType> types = StatType.characterStats();
        java.util.ArrayList<StatModifier> modifiers = new java.util.ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            if (values[i] != 0) {
                modifiers.add(StatModifier.flat(types.get(i), values[i], Hero.modifierSource(id)));
            }
        }
        return modifiers;
    }

    private static List<StatModifier> with(List<StatModifier> first, List<StatModifier> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    /** Personnages proposés au début d'une partie, dans l'ordre d'affichage. */
    public static List<Hero> all() {
        return ALL;
    }

    public static Hero hero(String id) {
        return ALL.stream().filter(h -> h.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Personnage inconnu : " + id));
    }
}
