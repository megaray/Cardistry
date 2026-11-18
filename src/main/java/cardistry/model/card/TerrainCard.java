package cardistry.model.card;

import cardistry.core.stats.StatModifier;
import cardistry.items.Rarity;
import cardistry.model.Element;
import java.util.List;
import java.util.Optional;

/**
 * Terrain : remplace le terrain actuel et modifie l'attaque adverse. Un terrain peut aussi porter
 * des bonus de stats et un effet spécial, actifs <b>tant qu'il est en jeu</b> (retirés quand le
 * terrain change, puis à la fin du combat).
 */
public final class TerrainCard extends Card {

    /** Effets spéciaux de terrain (règles que des bonus de stats ne suffisent pas à décrire). */
    public enum Effect {
        NONE,
        /** Gare : jouer une carte terrain depuis ce terrain ne coûte pas le tour et donne +1 clope. */
        FREE_DEPARTURE,
        /** Désert kaléidoscopique : l'hypnose fait ×4 au lieu de ×2. */
        DEEP_HYPNOSIS,
        /** Fractale : tous les multiplicateurs (joueur et adversaire) arrondis à l'entier supérieur. */
        CEILING,
        /** Espace de dimension n : chaque carte jouée est rejouée une fois à 50 % au tour suivant. */
        REPLAY,
        /**
         * EPFL : pendant 3 tours, valeurs du joueur doublées (ses bonus ×2), PV actuels ×2 à
         * l'arrivée et ÷2 au départ ; puis retour au Dojo.
         */
        EPFL,

        /** Terrain du boss 3 : le joueur ne peut pas se soigner. */
        NO_HEALING
    }

    private List<StatModifier> bonuses = List.of();
    private Effect effect = Effect.NONE;
    /** Texte de l'effet affiché au joueur ; {@code null} : aucun. */
    private String effectText;

    public TerrainCard(String name, Element element, Rarity rarity) {
        super(name, element, rarity);
    }

    /** Bonus de stats tant que le terrain est en jeu (la source est remplacée par « terrain »). */
    public List<StatModifier> bonuses() {
        return bonuses;
    }

    public Effect effect() {
        return effect;
    }

    /** Ce que fait le terrain, en une phrase (vide : rien de plus que l'élément). */
    public Optional<String> effectText() {
        return Optional.ofNullable(effectText);
    }

    /** Copie avec des bonus de stats ({@code text} : leur description). */
    public TerrainCard withBonuses(String text, StatModifier... modifiers) {
        TerrainCard copy = copy();
        copy.bonuses = List.of(modifiers);
        copy.effectText = text;
        return copy;
    }

    /** Copie avec des bonus de stats pris dans une liste. */
    public TerrainCard withBonuses(String text, List<StatModifier> modifiers) {
        return withBonuses(text, modifiers.toArray(StatModifier[]::new));
    }

    /** Copie avec un effet spécial ({@code text} : sa description). */
    public TerrainCard withEffect(Effect special, String text) {
        TerrainCard copy = copy();
        copy.effect = special;
        copy.effectText = text;
        return copy;
    }

    /** Copie avec un pouvoir (texte + effet après le changement de terrain). */
    public TerrainCard withPower(String text, java.util.function.Consumer<cardistry.core.EffectContext> power) {
        return withPower(copy(), new CardPower(text, power));
    }

    @Override
    public TerrainCard copy() {
        TerrainCard copy = samePower(this, new TerrainCard(name(), element(), rarity()));
        copy.bonuses = bonuses;
        copy.effect = effect;
        copy.effectText = effectText;
        return copy;
    }
}
