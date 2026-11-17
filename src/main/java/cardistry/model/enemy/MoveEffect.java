package cardistry.model.enemy;

/**
 * Ce que fait une attaque adverse ({@link EnemyMove}). Données pures : le moteur les exécute dans
 * l'ordre. Les dégâts déclarés sont ceux du niveau 1 : le moteur les multiplie par le facteur de
 * niveau de l'adversaire, puis par le terrain, et retire la réduction de dégâts du joueur.
 */
public sealed interface MoveEffect {

    /** {@code true} si l'effet inflige des dégâts (annulable par Moscow Mule). */
    boolean isAttack();

    /**
     * Attaque : {@code hits} coups de {@code damage}, plus {@code perMissingCard} par carte qui manque
     * à ta main pour atteindre {@link #FULL_HAND}. Chaque coup a {@code missChance} de rater ; un coup
     * raté soigne l'adversaire de {@code healOnMiss}.
     */
    record Hit(double damage, int hits, double perMissingCard, double missChance, double healOnMiss)
            implements MoveEffect {

        /** Main de référence pour {@link #perMissingCard}. */
        public static final int FULL_HAND = 5;

        public static Hit of(double damage) {
            return new Hit(damage, 1, 0, 0, 0);
        }

        public static Hit of(double damage, int hits) {
            return new Hit(damage, hits, 0, 0, 0);
        }

        public Hit times(int count) {
            return new Hit(damage, count, perMissingCard, missChance, healOnMiss);
        }

        @Override
        public boolean isAttack() {
            return true;
        }
    }

    /**
     * Vol : {@code choomies} Choomies si tu en as assez ; sinon {@code cigarettes} clope(s) ; sinon un
     * coup de {@code fallbackDamage}. Le vol n'est pas une attaque (Moscow Mule ne l'empêche pas).
     */
    record Pickpocket(int choomies, int cigarettes, double fallbackDamage) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

    /**
     * Augmente Ivresse de {@code amount}.
     * Pas une attaque => pas blocable
     * */
    record Drink(int amount) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

     /**
     * Vol de trinket: {@code chance} la proba que le vol fonctionne; Rendu quand l’adversaire est vaincu;
      * si tu n’as aucun trinket, un coup de {@code fallbackDamage}à la place ;
      * vol raté : rien
     *(Moscow Mule peut l'empêcher).
     */
    record StealTrinket(double chance, double fallbackDamage) implements MoveEffect {
        @Override
        public boolean isAttack() {
            return true;
        }
    }

    /**
     * Taxe : te prend {@code cigarettes} clope(s) ; si tu n'en as pas, {@code fallbackHits} coups de
     * {@code damage} (ceux-là sont une attaque).
     */
    record Tax(int cigarettes, double damage, int fallbackHits) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

    /** {@code chance} (tirage de l'adversaire) de faire {@code onSuccess}, sinon {@code onFailure}. */
    record Gamble(double chance, MoveEffect onSuccess, MoveEffect onFailure) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return onSuccess.isAttack() || onFailure.isAttack();
        }
    }

    /** L'adversaire esquivera ton prochain sort (aucun dégât). */
    record Dodge() implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

    /** Une carte de ta main (au hasard) devient une carte morte {@code cardName} jusqu'à la fin du combat. */
    record CurseCard(String cardName) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

    /** L'adversaire se soigne de {@code amount} (sans dépasser ses PV max). */
    record HealSelf(double amount) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

    /**
     * Inflige un statut au joueur ({@code status} = identifiant d'un {@code core.status.StatusType}) avec
     * {@code chance} (tirage de l'adversaire, pas doublé par CLAUDE.md), pour {@code turns} tours (0 =
     * durée par défaut du statut). {@code afterHit} : seulement si un coup de cette attaque a porté.
     */
    record Inflict(String status, double chance, int turns, boolean afterHit) implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }

    /** L'adversaire passe à un autre élément (tiré au hasard) jusqu'à la fin du combat. */
    record ChangeElement() implements MoveEffect {

        @Override
        public boolean isAttack() {
            return false;
        }
    }
}
