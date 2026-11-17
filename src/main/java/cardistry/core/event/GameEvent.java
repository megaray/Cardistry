package cardistry.core.event;

import cardistry.core.Curse;
import cardistry.core.TemporaryEffect;
import cardistry.items.Collectible;
import cardistry.items.Item;
import cardistry.items.Tag;
import cardistry.items.Trinket;
import cardistry.model.Combatant;
import cardistry.model.Enemy;
import cardistry.model.card.Card;
import cardistry.model.card.TerrainCard;
import cardistry.model.enemy.EnemyMove;
import java.util.List;

/**
 * Tout ce qui se passe dans la partie. Le moteur les publie sur l'{@link EventBus} ;
 * l'UI (et plus tard les trinkets) les consomment.
 */
public sealed interface GameEvent {

    record GameStarted(long seed) implements GameEvent {
    }

    record TurnStarted(int turn) implements GameEvent {
    }

    /** Le joueur va jouer ce tour (publié après {@link TurnStarted}, sauf si le tour est sauté). */
    record PlayerTurnStarted(int turn) implements GameEvent {
    }

    record CardPlayed(Card card) implements GameEvent {
    }

    /** Une carte jouée sur l'Espace de dimension n est rejouée ({@code factor} : part de ses dégâts ou soins). */
    record CardReplayed(Card card, double factor) implements GameEvent {
    }

    /** Le pouvoir d'une carte jouée se déclenche (après son effet principal). */
    record CardPowerTriggered(Card card, String text) implements GameEvent {
    }

    record CardDrawn(Card card) implements GameEvent {
    }

    record ItemUsed(Item item) implements GameEvent {
    }

    /** Pioche au choix : cartes proposées (une seule sera gardée). */
    record CardChoiceOffered(List<Card> options) implements GameEvent {

        public CardChoiceOffered {
            options = List.copyOf(options);
        }
    }

    /** Pioche au choix : carte gardée et cartes défaussées (suivi d'un {@link CardDrawn}). */
    record CardChosen(Card kept, List<Card> discarded) implements GameEvent {

        public CardChosen {
            discarded = List.copyOf(discarded);
        }
    }

    /** Une carte de la main a été remplacée par une autre (effet). */
    record CardReplaced(Card removed, Card replacement) implements GameEvent {
    }

    /**
     * {@code amount} = dégâts finaux, {@code multiplier} déjà appliqué.
     *
     * @param source ce qui a causé les dégâts
     */
    record DamageDealt(Combatant target, double amount, double multiplier, Source source) implements GameEvent {

        public enum Source {
            /** Sortilège joué depuis la main. */
            SPELL,
            /** Effet d'objet ou de trinket. */
            EFFECT,
            /** Attaque de l'adversaire. */
            ENEMY_ATTACK,
            /** Dégâts renvoyés par l'adversaire (Rebond du Peneu). */
            REFLECTED,
            /** Statut (Intox…) au début du tour de la cible. */
            STATUS
        }
    }

    /**
     * Un sortilège joué depuis la main a touché l'adversaire (pas publié s'il est esquivé).
     *
     * @param hpLost PV réellement enlevés à l'adversaire (sans les dégâts en trop s'il meurt)
     */
    /** Le familier agit à son tour : il attaque ou, raté, il fume un joint. */
    record FamiliarActed(cardistry.model.Familiar familiar, Action action) implements GameEvent {

        public enum Action {
            ATTACK,
            SMOKE
        }
    }

    /** Le familier tombe à 0 PV : assommé jusqu'à la fin du combat. */
    record FamiliarKnockedOut(cardistry.model.Familiar familiar) implements GameEvent {
    }

    /** Boss vaincu (sauf le dernier) : PV max gagnés pour toute la partie, points de stats à répartir. */
    record BossBonus(double maxHp, int statPoints) implements GameEvent {
    }

    /** {@code amount} points de {@code stat} pour {@code target}, jusqu'à la fin du combat. */
    record CombatStatChanged(cardistry.core.status.StatusTarget target, cardistry.core.stats.StatType stat,
            double amount) implements GameEvent {
    }

    /** Un point de stats placé ; {@code remaining} points restent à placer. */
    record StatPointSpent(cardistry.core.stats.StatType stat, int remaining) implements GameEvent {
    }

    /** Coup critique (×1,5) sur {@code target} : le joueur (coup adverse) ou l'adversaire (sort). */
    record CriticalHit(Combatant target) implements GameEvent {
    }

        record SpellHit(cardistry.model.card.SpellCard spell, Enemy enemy, double hpLost) implements GameEvent {
    }

    /** Une capacité (personnage) se manifeste : {@code message} est la ligne à afficher. */
    record AbilityTriggered(String message) implements GameEvent {
    }

    /** {@code amount} = PV réellement rendus (multiplicateur appliqué, plafonné aux PV max). */
    record Healed(Combatant target, double amount, double multiplier) implements GameEvent {
    }

    record TerrainChanged(TerrainCard previous, TerrainCard current) implements GameEvent {
    }

    /** L'adversaire attaque ; suivi d'un {@link DamageDealt} sur le joueur. */
    record EnemyAttacked(Enemy enemy) implements GameEvent {
    }

    record EnemyKilled(Enemy enemy) implements GameEvent {
    }

    /** Prochaine attaque de l'adversaire, annoncée à l'avance (intent). */
    record IntentChosen(Enemy enemy, EnemyMove move) implements GameEvent {
    }

    /** L'adversaire exécute son attaque annoncée ; ses effets suivent (dégâts, vol…). */
    record EnemyMoveUsed(Enemy enemy, EnemyMove move) implements GameEvent {
    }

    /** Un coup de l'adversaire rate ; {@code healed} = PV qu'il se rend en ratant (fringale). */
    record EnemyMissed(Enemy enemy, double healed) implements GameEvent {
    }

    /** L'adversaire esquivera ton prochain sort. */
    record DodgeReady(Enemy enemy) implements GameEvent {
    }

    /** L'adversaire esquive ton sort : aucun dégât. */
    record EnemyDodged(Enemy enemy) implements GameEvent {
    }

    /** L'adversaire se soigne (PV réellement rendus). */
    record EnemyHealed(Enemy enemy, double amount) implements GameEvent {
    }

    /** Une carte de ta main devient une carte morte (Série d'algèbre de Vafi). */
    record CardCursed(Enemy enemy, Card removed, Card dead) implements GameEvent {
    }

    /** L'adversaire te vole des Choomies. */
    record ChoomiesStolen(Enemy enemy, int amount) implements GameEvent {
    }

    /** L'adversaire te prend des clopes (vol ou Taxe). */
    record CigarettesStolen(Enemy enemy, int amount) implements GameEvent {
    }

    /** L'adversaire te prend un trinket. */
    record TrinketStolen(Enemy enemy, Trinket trinket) implements GameEvent {
    }

    /** L'adversaire te rend un trinket. */
    record TrinketReturned(Trinket trinket) implements GameEvent {
    }

    /** Nouveau niveau de la carte (1 à 3). */
    record LevelStarted(int level) implements GameEvent {
    }

    /**
     * Le joueur entre dans un nœud de la carte.
     *
     * @param kind nom affiché du type de nœud (« Combat », « Sieste »…)
     */
    record NodeEntered(int level, int floor, String kind) implements GameEvent {
    }

    /** Un événement commence (nœud {@code ?}) : texte et choix dans {@code story}. */
    record StoryStarted(cardistry.story.Story story) implements GameEvent {
    }

    /** Le joueur a fait un choix ; {@code lines} = ce qui s'est passé, prêt à afficher. */
    record StoryResolved(String title, String choice, List<String> lines) implements GameEvent {

        public StoryResolved {
            lines = List.copyOf(lines);
        }
    }

    /** Entrée dans un shop (articles : {@code shop.offers()}). */
    record ShopEntered(cardistry.core.Shop shop) implements GameEvent {
    }

    /** Articles renouvelés (reroll payé {@code price}). */
    record ShopRestocked(int price) implements GameEvent {
    }

    /**
     * Article acheté ; {@code replaced} est l'objet ou le trinket retiré pour lui faire de la place,
     * ou null.
     */
    record ItemBought(cardistry.core.shop.ShopOffer offer, Collectible replaced) implements GameEvent {
    }

    /** Carte retirée du deck au shop, pour {@code price} Choomies (suivi d'un {@link CardRemovedFromDeck}). */
    record CardRemovalBought(int price) implements GameEvent {
    }

    record ShopLeft() implements GameEvent {
    }

    /** La défausse est remélangée dans la pioche ({@code size} cartes). */
    record DeckShuffled(int size) implements GameEvent {
    }

    /** Récompense de carte : ajoute 1 carte parmi {@code options} à ton deck (ou passe). */
    record CardRewardOffered(List<Card> options) implements GameEvent {

        public CardRewardOffered {
            options = List.copyOf(options);
        }
    }

    record CardRewardSkipped() implements GameEvent {
    }

    /** Carte ajoutée au deck de la run. */
    record CardAddedToDeck(Card card) implements GameEvent {
    }

    /** Carte retirée du deck de la run. */
    record CardRemovedFromDeck(Card card) implements GameEvent {
    }

    /** Sieste : le joueur a choisi {@code choice} (nom affiché). */
    record RestTaken(String choice) implements GameEvent {
    }

    /** Les Choomies changent ; {@code delta} = gain (positif) ou perte. */
    record ChoomiesChanged(int choomies, int delta) implements GameEvent {
    }

    /** L'Ivresse change ; {@code threshold} = seuil de Gueule de bois actuel. */
    record DrunkennessChanged(int drunkenness, int threshold) implements GameEvent {
    }

    /** Nouvel effet temporaire (actif tout de suite ou en attente). */
    record EffectAdded(TemporaryEffect effect) implements GameEvent {
    }

    /** Un effet en attente se déclenche. */
    record EffectActivated(TemporaryEffect effect) implements GameEvent {
    }

    /** L'adversaire change d'élément jusqu'à la fin du combat. */
    record EnemyElementChanged(Enemy enemy, cardistry.model.Element previous, cardistry.model.Element current)
            implements GameEvent {
    }

    /** Un effet se termine (durée, charges épuisées ou fin de combat). */
    record EffectEnded(TemporaryEffect effect) implements GameEvent {
    }

    /** Un statut est posé sur {@code target} ({@code refreshed} : il l'était déjà, durée remise au max). */
    record StatusApplied(cardistry.core.status.StatusTarget target, cardistry.core.status.StatusType type, int turns,
            boolean refreshed) implements GameEvent {
    }

    /** Un statut n'a pas pu être posé ({@code reason} : affichée au joueur). */
    record StatusRefused(cardistry.core.status.StatusTarget target, cardistry.core.status.StatusType type,
            String reason) implements GameEvent {
    }

    /** Un statut se termine (durée écoulée, retiré, ou fin du combat). */
    record StatusEnded(cardistry.core.status.StatusTarget target, cardistry.core.status.StatusType type)
            implements GameEvent {
    }

    /** Le joueur saute ce tour ; {@code causes} = noms des effets responsables. */
    record PlayerTurnSkipped(List<String> causes) implements GameEvent {

        public PlayerTurnSkipped {
            causes = List.copyOf(causes);
        }
    }

    /** L'adversaire saute son tour (il n'attaque pas) ; {@code message} est prêt à afficher. */
    record EnemyTurnSkipped(Enemy enemy, String cause, String message) implements GameEvent {
    }

    /** L'attaque de l'adversaire est annulée par un effet. */
    record AttackCancelled(Enemy enemy, String cause) implements GameEvent {
    }

    /** Malédiction obtenue : elle s'activera au prochain combat. */
    record CurseGained(Curse curse) implements GameEvent {
    }

    /** Intensité d'une malédiction réduite (par une clope) ; {@code pending} si elle était à venir. */
    record CurseReduced(Curse curse, int intensity, boolean pending) implements GameEvent {
    }

    /** Le nombre de clopes change. */
    record CigarettesChanged(int cigarettes, int max) implements GameEvent {
    }

    /** Une clope est fumée ; {@code transformedIntoPetitPetou} si Googz l'a transformée. */
    record CigaretteSmoked(boolean transformedIntoPetitPetou) implements GameEvent {
    }

    /** Une clope est roulée (fin du tour). */
    record CigaretteRolled() implements GameEvent {
    }

    /** Malédiction active pour ce combat. */
    record CurseStarted(Curse curse) implements GameEvent {
    }

    /** Malédiction terminée (fin du combat). */
    record CurseEnded(Curse curse) implements GameEvent {
    }

    /** Récompenses proposées après la mort d'un adversaire ; le moteur attend un choix. */
    record RewardOffered(List<Collectible> options) implements GameEvent {

        public RewardOffered {
            options = List.copyOf(options);
        }
    }

    /** Récompense prise ; {@code replaced} est l'item retiré pour lui faire de la place, ou null. */
    record RewardTaken(Collectible taken, Collectible replaced) implements GameEvent {
    }

    record RewardSkipped() implements GameEvent {
    }

    /** Un trinket est jeté parce qu'il y en avait plus que d'emplacements. */
    record TrinketDiscarded(Trinket trinket) implements GameEvent {
    }

    /** Un palier de set s'active (ex. set « EPFL « Cursus » », palier 3). */
    record SetTierActivated(String setName, Tag tag, int count, String description) implements GameEvent {
    }

    /** Un palier de set se désactive (trinket perdu). */
    record SetTierDeactivated(String setName, Tag tag, int count, String description) implements GameEvent {
    }

    /**
     * Début d'un combat (un par adversaire), y compris le premier.
     *
     * @param number numéro du combat, à partir de 1
     * @param total nombre de combats de la partie
     */
    record CombatStarted(Enemy enemy, int number, int total) implements GameEvent {
    }

    /** Nouvelle main distribuée en début de combat (l'ancienne est défaussée). */
    record HandDealt(List<Card> cards) implements GameEvent {

        public HandDealt {
            cards = List.copyOf(cards);
        }
    }

    record ActionRejected(Reason reason) implements GameEvent {

        public enum Reason {
            /** Numéro hors des slots de la main. */
            INVALID_CHOICE,
            /** Pioche impossible : main pleine. */
            HAND_FULL,
            /** Le slot choisi ne contient pas de carte. */
            EMPTY_SLOT,
            /** Pas d'objet à cet emplacement. */
            NO_ITEM,
            /** Proposition de récompense inexistante. */
            INVALID_REWARD,
            /** Inventaire plein : il faut choisir quoi remplacer. */
            INVENTORY_FULL,
            /** Plus de clope à fumer. */
            NO_CIGARETTE,
            /** Paquet plein : impossible de rouler. */
            PACK_FULL,
            /** Carte injouable (NO CARDS). */
            UNPLAYABLE_CARD,
            /** Annulation impossible (pas de Ctrl+Z, déjà utilisé ce combat, ou pas de tour à annuler). */
            UNDO_UNAVAILABLE,
            /** Plus aucune carte à piocher (pioche et défausse vides). */
            DECK_EMPTY,
            /** Shop : pas assez de Choomies. */
            NOT_ENOUGH_CHOOMIES,
            /** Shop : article déjà vendu (ou inexistant). */
            SOLD_OUT,
            /** Retrait impossible : le deck est au minimum. */
            DECK_TOO_SMALL,
            /** Événement : ce choix n'est pas disponible (il manque quelque chose). */
            CHOICE_UNAVAILABLE
        }
    }

    /** NullPointerException : l'adversaire devient null (mort sur le coup). */
    record EnemyNulled(Enemy enemy) implements GameEvent {
    }

    /** NullPointerException : une carte de la main devient « NO CARDS » ({@code dead}). */
    record CardVoided(Card removed, Card dead) implements GameEvent {
    }

    /** Ctrl+Z : la partie est revenue au début du tour {@code turn}. */
    record TurnUndone(int turn) implements GameEvent {
    }

    /** Secret débloqué pour les parties suivantes. */
    record SecretUnlocked(String name) implements GameEvent {
    }

    record GameWon() implements GameEvent {
    }

    record GameLost() implements GameEvent {
    }
}
