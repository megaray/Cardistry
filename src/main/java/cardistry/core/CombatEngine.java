package cardistry.core;

import java.util.List;
import java.util.Optional;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.event.GameEvent.ActionRejected.Reason;
import cardistry.core.event.HookDispatcher;
import cardistry.core.stats.StatType;
import cardistry.core.status.Status;
import cardistry.core.status.StatusExtras;
import cardistry.core.status.StatusTarget;
import cardistry.core.status.StatusType;
import cardistry.core.status.Statuses;
import cardistry.items.Collectible;
import cardistry.items.Item;
import cardistry.items.LootTable;
import cardistry.items.Tag;
import cardistry.items.Trinket;
import cardistry.model.Combatant;
import cardistry.model.Element;
import cardistry.model.ElementRules;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.DeadCard;
import cardistry.model.card.HealCard;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import cardistry.model.enemy.EnemyMove;
import cardistry.model.enemy.MoveEffect;

/**
 * Règles du combat. N'affiche rien : chaque changement d'état est publié sur le bus.
 *
 * <p>Une partie est une suite de combats, un par adversaire ; chaque combat commence avec une
 * nouvelle main de {@link GameState#STARTING_HAND} cartes. Déroulement d'un tour :
 * {@link #startTurn()}, puis {@link #submit(Action)} jusqu'à ce que le tour soit terminé
 * ({@link ActionResult#TURN_ENDED}). Jouer une carte ou piocher termine le tour et l'adversaire
 * réagit ; utiliser un objet est gratuit.
 *
 * <p>Après la mort d'un adversaire (sauf le dernier), le moteur propose des récompenses et attend
 * {@link #resolveReward(RewardChoice)} avant de lancer le combat suivant.
 *
 * <p>Les trinkets équipés reçoivent chaque événement (voir {@link cardistry.core.event.GameHooks}).
 */
public final class CombatEngine {

    private final GameState state;
    private final CardPool pool;
    private final Rng rng;
    private final EventBus bus;
    private final LootTable loot;
    private final EffectContext effects = new Effects();
    private List<Collectible> pendingReward = List.of();
    private PlayerDecisions decisions = PlayerDecisions.FIRST;

    public CombatEngine(GameState state, CardPool pool, Rng rng, EventBus bus) {
        this(state, pool, rng, bus, new LootTable());
    }

    public CombatEngine(GameState state, CardPool pool, Rng rng, EventBus bus, LootTable loot) {
        this.state = state;
        this.pool = pool;
        this.rng = rng;
        this.bus = bus;
        this.loot = loot;
        bus.subscribe(this::dispatchToTrinkets);
        if (!state.isOpenEnded()) {
            applyTerrainBonuses(); // adversaires fixés : le premier combat est déjà en cours
        }
    }

    public GameState state() {
        return state;
    }

    /** Qui répond aux décisions demandées en cours d'action (l'interface, ou un test). */
    public void setDecisions(PlayerDecisions decisions) {
        this.decisions = decisions;
    }

    /** Lance la partie ; avec des adversaires fixés, lance aussi le premier combat. */
    public void start() {
        bus.publish(new GameEvent.GameStarted(rng.seed()));
        if (!state.isOpenEnded()) {
            beginCombat();
        }
    }

    /**
     * Partie ouverte : lance un combat contre {@code enemy} (nœud de la carte).
     *
     * @param isFinal dernier combat de la partie : le gagner gagne la partie
     */
    public void startCombat(Enemy enemy, boolean isFinal) {
        if (!state.isOpenEnded()) {
            throw new IllegalStateException("Les adversaires de cette partie sont fixés");
        }
        if (inCombat() || hasPendingReward() || isOver()) {
            throw new IllegalStateException("Un combat est déjà en cours");
        }
        state.addEncounter(enemy, isFinal);
        beginCombat();
    }

    /** {@code true} pendant un combat (adversaire vivant, partie pas finie). */
    public boolean inCombat() {
        return state.hasEnemy() && state.currentEnemy().isAlive() && !isOver();
    }

    /**
     * {@code true} tant que le combat en cours n'est pas terminé : adversaire vivant, ou mort mais
     * pas encore traité (tué par un effet au début du combat : traité au prochain {@link #startTurn()}).
     */
    public boolean combatActive() {
        return state.hasEnemy() && state.currentEnemy() != lastKilled && !isOver();
    }

    // --- Carte de la run ---

    /** Nouveau niveau de la carte. */
    public void startLevel(int level) {
        state.setLocation(level, 0);
        bus.publish(new GameEvent.LevelStarted(level));
    }

    /** Le joueur entre dans un nœud ({@code kind} : nom affiché de son type). */
    public void enterNode(int floor, String kind) {
        state.setLocation(state.level(), floor);
        state.addFloorTraveled();
        bus.publish(new GameEvent.NodeEntered(state.level(), floor, kind));
    }

    /**
     * Début de tour : les effets temporaires avancent d'un tour, puis le joueur joue ou saute son
     * tour (l'adversaire attaque alors directement).
     *
     * @return {@code true} si le joueur doit choisir une action ce tour-ci
     */
    public boolean startTurn() {
        if (!state.hasEnemy() || state.currentEnemy() == lastKilled || isOver()) {
            throw new IllegalStateException("Aucun combat en cours");
        }
        state.nextTurn();
        bus.publish(new GameEvent.TurnStarted(state.turn()));
        tickEffects();
        if (isOver() || resolveEnemyDeath()) {
            return false;
        }
        tickEpfl();
        startOfTurnStatuses(StatusTarget.PLAYER);
        if (isOver()) {
            return false;
        }
        replayEchoes();
        if (isOver() || resolveEnemyDeath()) {
            return false;
        }
        List<String> skipCauses = skipCauses();
        if (skipCauses.isEmpty()) {
            bus.publish(new GameEvent.PlayerTurnStarted(state.turn()));
            return !isOver();
        }
        bus.publish(new GameEvent.PlayerTurnSkipped(skipCauses));
        endPlayerTurn();
        enemyTurn();
        if (!state.isLost()) {
            resolveEnemyDeath();
        }
        return false;
    }

    // --- Effets temporaires ---

    private void addEffect(TemporaryEffect effect) {
        state.addEffect(effect);
        bus.publish(new GameEvent.EffectAdded(effect));
        if (effect.delayTurns() <= 0) {
            activateEffect(effect);
        }
    }

    private void activateEffect(TemporaryEffect effect) {
        state.activateEffect(effect);
        if (effect.delayTurns() > 0) {
            bus.publish(new GameEvent.EffectActivated(effect));
        }
        effect.runOnActivate(effects);
    }

    private void endEffect(TemporaryEffect effect) {
        if (state.effects().contains(effect)) {
            state.removeEffect(effect);
            bus.publish(new GameEvent.EffectEnded(effect));
        }
    }

    /** Début de tour : compte à rebours, déclenchements, durées. */
    private void tickEffects() {
        for (TemporaryEffect effect : List.copyOf(state.effects())) {
            if (!effect.isActive()) {
                if (effect.tickDelay()) {
                    activateEffect(effect);
                }
            } else if (effect.tickDuration()) {
                endEffect(effect);
            }
        }
    }

    /** Effets qui font sauter ce tour ; ceux à charges en consomment une. */
    private List<String> skipCauses() {
        List<String> causes = new java.util.ArrayList<>();
        if (state.statuses(StatusTarget.PLAYER).has(StatusType.FONS)) {
            causes.add(StatusType.FONS.displayName());
        }
        for (TemporaryEffect effect : List.copyOf(state.effects())) {
            if (!effect.isActive()) {
                continue;
            }
            if (effect.skipsNextTurn()) {
                causes.add(effect.name());
                consume(effect);
            }
        }
        return causes;
    }

    private void consume(TemporaryEffect effect) {
        if (effect.consumeCharge()) {
            endEffect(effect);
        }
    }

    public boolean isOver() {
        return state.isWon() || state.isLost();
    }

    /**
     * Joue l'action du joueur, puis, si elle termine le tour, la réaction de l'adversaire.
     *
     * @return {@link ActionResult#REJECTED} si l'action est refusée (un {@link GameEvent.ActionRejected}
     *     est publié et rien d'autre ne change)
     */
    public ActionResult submit(Action action) {
        if (isOver()) {
            throw new IllegalStateException("La partie est terminée");
        }
        if (hasPendingReward() || hasPendingCardReward()) {
            throw new IllegalStateException("Une récompense attend d'être choisie");
        }
        Optional<Reason> rejection = validate(action);
        if (rejection.isPresent()) {
            bus.publish(new GameEvent.ActionRejected(rejection.get()));
            return ActionResult.REJECTED;
        }
        boolean endsTurn = switch (action) {
            case Action.DrawCard draw -> {
                int count = Math.max(1, state.playerStats().getInt(StatType.CARDS_PER_DRAW));
                for (int i = 0; i < count && state.handCount() < state.handSize(); i++) {
                    drawCard();
                }
                yield true;
            }
            case Action.PlayCard play -> {
                boolean departure = state.hand().get(play.slot()) instanceof TerrainCard
                        && state.terrain().effect() == TerrainCard.Effect.FREE_DEPARTURE;
                String from = state.terrain().name();
                playCard(play.slot());
                if (departure) {
                    freeDeparture(from);
                    yield false;
                }
                yield true;
            }
            case Action.UseItem use -> {
                useItem(use.slot());
                yield false;
            }
            case Action.Smoke smoke -> {
                smoke();
                yield false;
            }
            case Action.RollCigarette roll -> {
                state.setCigarettes(state.cigarettes() + 1);
                bus.publish(new GameEvent.CigaretteRolled());
                bus.publish(new GameEvent.CigarettesChanged(state.cigarettes(), state.cigaretteMax()));
                yield true;
            }
            case Action.Undo undo -> throw new AssertionError("refusée par validate");
        };
        if (state.isLost()) {
            return ActionResult.TURN_ENDED; // mort de son propre effet (GameLost déjà publié)
        }
        if (resolveEnemyDeath()) {
            return ActionResult.TURN_ENDED;
        }
        if (!endsTurn) {
            return ActionResult.FREE_ACTION;
        }
        endPlayerTurn();
        enemyTurn();
        if (state.isLost()) {
            return ActionResult.TURN_ENDED;
        }
        resolveEnemyDeath(); // fringale ratée… ou mort par un effet pendant son tour
        return ActionResult.TURN_ENDED;
    }

    private Optional<Reason> validate(Action action) {
        return switch (action) {
            case Action.DrawCard draw -> {
                if (state.handCount() >= state.handSize()) {
                    yield Optional.of(Reason.HAND_FULL);
                }
                if (state.usesDeck() && state.drawPile().isEmpty() && state.discardPile().isEmpty()) {
                    yield Optional.of(Reason.DECK_EMPTY);
                }
                yield Optional.empty();
            }
            case Action.PlayCard play -> {
                // Une main peut dépasser la taille max (taille réduite en cours de combat) : on garde ses cartes.
                if (play.slot() < 0 || play.slot() >= Math.max(state.handSize(), state.hand().size())) {
                    yield Optional.of(Reason.INVALID_CHOICE);
                }
                if (play.slot() >= state.hand().size()) {
                    yield Optional.of(Reason.EMPTY_SLOT);
                }
                if (state.hand().get(play.slot()) instanceof DeadCard) {
                    yield Optional.of(Reason.UNPLAYABLE_CARD);
                }
                yield Optional.empty();
            }
            case Action.UseItem use -> use.slot() < 0 || use.slot() >= state.items().size()
                    ? Optional.of(Reason.NO_ITEM)
                    : Optional.empty();
            case Action.Smoke smoke -> state.cigarettes() <= 0
                    ? Optional.of(Reason.NO_CIGARETTE)
                    : Optional.empty();
            case Action.RollCigarette roll -> state.cigarettes() >= state.cigaretteMax()
                    ? Optional.of(Reason.PACK_FULL)
                    : Optional.empty();
            // Seul run.Run sait annuler un tour (en rejouant la partie).
            case Action.Undo undo -> Optional.of(Reason.UNDO_UNAVAILABLE);
        };
    }

    // --- Combats ---

    /** Nouvelle main, compteur de tours du combat remis à zéro. */
    private void beginCombat() {
        state.familiar().ifPresent(cardistry.model.Familiar::restore); // le familier revient avec tous ses PV
        state.currentEnemy().terrain().ifPresent(state::setTerrain); // chez l'adversaire
        applyTerrainBonuses();
        state.resetCombatTurn();
        state.setUndoUsedThisCombat(false);
        state.clearHand();
        bus.publish(new GameEvent.CombatStarted(state.currentEnemy(), state.combatNumber(),
                state.isOpenEnded() ? 0 : state.enemies().size()));
        for (Curse curse : state.activatePendingCurses()) {
            bus.publish(new GameEvent.CurseStarted(curse));
        }
        if (state.usesDeck()) {
            state.resetPiles(rng);
        }
        int dealt = Math.min(state.playerStats().getInt(StatType.STARTING_HAND), state.handSize());
        for (int i = 0; i < dealt; i++) {
            Optional<Card> card = drawOne();
            if (card.isEmpty()) {
                break;
            }
            state.addToHand(card.get());
        }
        bus.publish(new GameEvent.HandDealt(state.hand()));
        if (state.currentEnemy().isAlive()) {
            announceIntent();
        }
    }

    private void announceIntent() {
        Enemy enemy = state.currentEnemy();
        EnemyMove move = enemy.chooseIntent();
        // Une attaque peut viser le familier à la place du joueur : tirage de l'adversaire (pas doublé).
        state.familiar().ifPresent(f -> f.setTargeted(!f.isKnockedOut()
                && move.effects().stream().anyMatch(e -> e instanceof MoveEffect.Hit)
                && rng.nextDouble() < f.spec().targetChance()));
        bus.publish(new GameEvent.IntentChosen(enemy, move));
    }

    /** Fin du combat : l'Ivresse retombe, les malédictions du combat s'arrêtent. */
    private void endCombat() {
        if (state.terrain().effect() == TerrainCard.Effect.EPFL) {
            changeTerrain(CardPool.dojo()); // PV ÷2 avant la fin du combat
        }
        for (TemporaryEffect effect : List.copyOf(state.effects())) {
            if (!effect.keepsAcrossCombats()) {
                endEffect(effect);
            }
        }
        if (state.drunkenness() != 0) {
            state.setDrunkenness(0);
            bus.publish(new GameEvent.DrunkennessChanged(0, state.hangoverThreshold()));
        }
        for (Curse curse : state.endActiveCurses()) {
            bus.publish(new GameEvent.CurseEnded(curse));
        }
        state.playerStats().removeBySource(COMBAT_BONUS_SOURCE);
        state.playerStats().removeBySource(TERRAIN_SOURCE);
        state.takeEchoes();
        for (StatusTarget target : StatusTarget.values()) {
            for (Status status : state.statuses(target).clear()) {
                bus.publish(new GameEvent.StatusEnded(target, status.type()));
            }
        }
    }

    /** Source des bonus « pour tout le combat » (Stingin' Roger), retirés en fin de combat. */
    public static final String COMBAT_BONUS_SOURCE = "combat";

    /** Statuts négatifs du joueur (purge, Berceuse) : pas l'Hypnose, ni la Gueule de bois, ni l'Ivresse. */
    public static final java.util.Set<StatusType> NEGATIVE_STATUSES = java.util.EnumSet.of(
            StatusType.INTOX, StatusType.FONS, StatusType.BONDAGE, StatusType.BURN);

    /** Retire {@code type} de {@code target} ; {@code true} s'il était actif. */
    private boolean removeStatus(StatusTarget target, StatusType type) {
        if (state.statuses(target).remove(type)) {
            bus.publish(new GameEvent.StatusEnded(target, type));
            return true;
        }
        return false;
    }

    // --- Statuts ---

    /**
     * Pose {@code type} sur {@code target} pour {@code turns} tours (de la cible), selon la règle de
     * réapplication du statut.
     *
     * @return {@code true} s'il a été posé ou rafraîchi
     */
    private boolean applyStatus(StatusTarget target, StatusType type, int turns, StatusTarget inflictedBy,
            StatusExtras extras) {
        if (!state.hasEnemy() || !state.combatant(target).isAlive()) {
            return false;
        }
        if (type == StatusType.BURN && state.terrain().element() == Element.WATER) {
            bus.publish(new GameEvent.StatusRefused(target, type, "l'eau éteint la Brûlure"));
            return false;
        }
        if (target == StatusTarget.PLAYER) {
            Optional<TemporaryEffect> ward = state.effects().stream()
                    .filter(e -> e.isActive() && e.cancelsNextStatus()).findFirst();
            if (ward.isPresent()) {
                bus.publish(new GameEvent.StatusRefused(target, type, ward.get().name() + " l'annule"));
                consume(ward.get());
                return false;
            }
        }
        Statuses.Outcome outcome = state.statuses(target).apply(type, turns, inflictedBy, extras);
        if (outcome == Statuses.Outcome.REFUSED) {
            bus.publish(new GameEvent.StatusRefused(target, type, type.displayName() + " déjà actif"));
            return false;
        }
        bus.publish(new GameEvent.StatusApplied(target, type, state.statuses(target).get(type).orElseThrow().turnsLeft(),
                outcome == Statuses.Outcome.REFRESHED));
        return true;
    }

    /**
     * Tire la chance puis pose le statut. {@code turns} ≤ 0 : durée tirée entre le min et le max du
     * statut. Contre l'adversaire, la chance est en faveur du joueur (doublée par CLAUDE.md).
     * {@code withStats} : la chance gagne 3 % par INT du lanceur et perd 5 % par RES de la cible (même
     * à coup sûr), et la RES de la cible peut raccourcir la durée ({@link CombatFormula}).
     */
    private boolean tryStatus(StatusTarget target, StatusType type, double probability, int turns,
            StatusExtras extras, StatusTarget inflictedBy, boolean withStats) {
        if (!state.hasEnemy()) {
            return false;
        }
        double resistance = withStats ? state.stats(target).get(StatType.RESISTANCE) : 0;
        double chance = withStats
                ? CombatFormula.statusChance(probability, state.stats(inflictedBy).get(StatType.INTELLIGENCE), resistance)
                : probability;
        if (chance <= 0) {
            return false;
        }
        boolean lands = chance >= 1 || (target == StatusTarget.ENEMY && inflictedBy == StatusTarget.PLAYER
                ? Luck.chance(state, rng, chance)
                : rng.nextDouble() < chance);
        if (!lands) {
            return false;
        }
        int duration = turns > 0 ? turns
                : type.minTurns() + rng.nextInt(type.maxTurns() - type.minTurns() + 1);
        return applyStatus(target, type, CombatFormula.statusTurns(duration, resistance), inflictedBy, extras);
    }

    /**
     * Coup critique du joueur sur un sort : 3 % par CHA, tirage en sa faveur (doublé par CLAUDE.md) ;
     * sûr si le sort le garantit à cette CHA (Lancer de manette).
     */
    private boolean playerCritical(SpellCard spell) {
        double luck = state.playerStats().get(StatType.LUCK);
        if (spell.sureCriticalLuck() > 0 && luck >= spell.sureCriticalLuck()) {
            return true;
        }
        double chance = CombatFormula.critChance(luck);
        return chance > 0 && Luck.chance(state, rng, chance);
    }

    /** Début du tour de {@code target} : effets par tour de ses statuts. */
    private void startOfTurnStatuses(StatusTarget target) {
        for (Status status : List.copyOf(state.statuses(target).all())) {
            if (!state.combatant(target).isAlive() || state.isLost()) {
                return;
            }
            switch (status.type()) {
                case INTOX -> statusDamage(target, StatusType.INTOX_DAMAGE);
                case BURN -> statusDamage(target, state.terrain().element() == Element.FIRE
                        ? StatusType.BURN_DAMAGE_FIRE : StatusType.BURN_DAMAGE);
                case BONDAGE -> {
                    double stolen = statusDamage(target, state.terrain().element() == Element.NATURE
                            ? StatusType.BONDAGE_STEAL_PLANT : StatusType.BONDAGE_STEAL);
                    healCombatant(status.inflictedBy(), stolen);
                }
                case FONS -> {
                    if (status.extras().healMaxHpPerTurn() > 0) {
                        double max = target == StatusTarget.PLAYER ? state.maxHp() : state.currentEnemy().maxHp();
                        double base = max * status.extras().healMaxHpPerTurn();
                        if (target == StatusTarget.PLAYER) {
                            double mult = state.playerStats().get(StatType.HEAL_EFFICIENCY);
                            healPlayer(base * mult, mult); // un vrai soin (efficacité, Ross)
                        } else {
                            healCombatant(target, base);
                        }
                    }
                }
                case HYPNOTIZED -> {
                    // Agit sur les multiplicateurs (GameState), rien par tour.
                }
            }
        }
    }

    /** Fin du tour de {@code side} : les statuts qui décomptent sur ce camp perdent un tour. */
    private void endOfTurnStatuses(StatusTarget side) {
        for (StatusTarget owner : StatusTarget.values()) {
            for (Status ended : state.statuses(owner).endTurn(owner, side)) {
                bus.publish(new GameEvent.StatusEnded(owner, ended.type()));
            }
        }
    }

    /** Rend des PV bruts à {@code target} (vol de PV du Bondage), plafonnés à ses PV max. */
    private void healCombatant(StatusTarget target, double amount) {
        if (amount <= 0) {
            return;
        }
        if (target == StatusTarget.PLAYER) {
            healPlayer(amount, 1.0);
        } else {
            Enemy enemy = state.currentEnemy();
            double healed = enemy.healSelf(amount);
            if (healed > 0) {
                bus.publish(new GameEvent.EnemyHealed(enemy, healed));
            }
        }
    }

    /** Départ de l'EPFL : PV actuels ÷2 (arrondis), minuteur annulé ; les bonus partent avec le terrain. */
    private void leaveEpfl() {
        Player player = state.player();
        int before = player.hp();
        int after = Combatant.round(before / 2.0);
        player.takeDamage(before - after);
        state.setEpflTurnsLeft(0);
        bus.publish(new GameEvent.AbilityTriggered("Tu quittes l'EPFL : PV ÷2 (" + before + " → " + after + ")"));
    }

    /** Début du tour du joueur : après ses {@link CardPool#EPFL_TURNS} tours à l'EPFL, retour au Dojo. */
    private void tickEpfl() {
        if (state.terrain().effect() == TerrainCard.Effect.EPFL && state.epflTurnsLeft() <= 0) {
            changeTerrain(CardPool.dojo());
        }
    }

    /** Arrivé à l'EPFL pendant le tour en cours : ce tour-là ne compte pas. */
    private boolean epflArrivedThisTurn;

    /** Fin du tour du joueur : ses statuts décomptent, et un tour d'EPFL de moins. */
    private void endPlayerTurn() {
        endOfTurnStatuses(StatusTarget.PLAYER);
        if (state.terrain().effect() == TerrainCard.Effect.EPFL) {
            if (epflArrivedThisTurn) {
                epflArrivedThisTurn = false;
            } else {
                state.setEpflTurnsLeft(state.epflTurnsLeft() - 1);
            }
        }
    }

    /** Source des bonus du terrain en jeu (retirés quand il change, puis en fin de combat). */
    public static final String TERRAIN_SOURCE = "terrain";

    /** Bonus de stats du terrain en jeu : on retire ceux de l'ancien, puis on pose ceux du nouveau. */
    private void applyTerrainBonuses() {
        state.playerStats().removeBySource(TERRAIN_SOURCE);
        for (cardistry.core.stats.StatModifier bonus : state.terrain().bonuses()) {
            state.playerStats().add(new cardistry.core.stats.StatModifier(bonus.stat(), bonus.kind(), bonus.value(),
                    TERRAIN_SOURCE));
        }
    }

    /** Gare : quitter la Gare par une carte terrain ne coûte pas le tour et donne +1 clope. */
    private void freeDeparture(String from) {
        String message = "Départ de " + from + " : changement gratuit";
        if (state.cigarettes() < state.cigaretteMax()) {
            state.setCigarettes(state.cigarettes() + 1);
            bus.publish(new GameEvent.CigarettesChanged(state.cigarettes(), state.cigaretteMax()));
            message += ", +1 clope";
        }
        bus.publish(new GameEvent.AbilityTriggered(message));
    }

    /** Change de terrain (ses bonus suivent) ; un nouveau terrain eau éteint toutes les Brûlures. */
    private void changeTerrain(TerrainCard terrain) {
        TerrainCard previous = state.terrain();
        boolean leavingEpfl = previous.effect() == TerrainCard.Effect.EPFL && terrain.effect() != TerrainCard.Effect.EPFL;
        boolean enteringEpfl = previous.effect() != TerrainCard.Effect.EPFL && terrain.effect() == TerrainCard.Effect.EPFL;
        if (leavingEpfl) {
            leaveEpfl();
        }
        state.setTerrain(terrain);
        applyTerrainBonuses();
        if (terrain.effect() == TerrainCard.Effect.EPFL) {
            state.setEpflTurnsLeft(CardPool.EPFL_TURNS);
            epflArrivedThisTurn = true;
            if (enteringEpfl) {
                Player player = state.player();
                player.heal(Math.max(0, player.hp())); // PV actuels ×2 (pas un soin)
                bus.publish(new GameEvent.AbilityTriggered("EPFL : tout est doublé pendant " + CardPool.EPFL_TURNS
                        + " tours (PV " + player.hp() + "/" + Combatant.round(state.maxHp()) + ")"));
            }
        }
        if (terrain.effect() != TerrainCard.Effect.REPLAY) {
            state.takeEchoes(); // quitter l'Espace : plus rien à rejouer
        }
        bus.publish(new GameEvent.TerrainChanged(previous, terrain));
        if (terrain.element() == Element.WATER) {
            for (StatusTarget owner : StatusTarget.values()) {
                if (state.statuses(owner).remove(StatusType.BURN)) {
                    bus.publish(new GameEvent.StatusEnded(owner, StatusType.BURN));
                }
            }
        }
    }

    /**
     * Dégâts d'un statut : bruts (aucun multiplicateur, aucune réduction, pas de Rebond).
     *
     * @return PV réellement enlevés (pas au-delà de ce qu'il restait)
     */
    private double statusDamage(StatusTarget target, double amount) {
        Combatant victim = state.combatant(target);
        boolean wasAlive = victim.isAlive();
        double before = Math.max(0, victim.hp());
        double dealt = victim.takeDamage(amount);
        bus.publish(new GameEvent.DamageDealt(victim, dealt, 1.0, GameEvent.DamageDealt.Source.STATUS));
        if (target == StatusTarget.PLAYER && wasAlive && !victim.isAlive()) {
            bus.publish(new GameEvent.GameLost());
        }
        return Math.min(dealt, before);
    }

    /**
     * Ajoute de l'Ivresse (le palier et son bonus de dégâts suivent, voir {@link DrunkTier}). En
     * atteignant le seuil (la Cuite), une Gueule de bois est programmée pour le combat suivant : une à
     * chaque fois qu'on franchit le seuil vers le haut ; aucune pendant le dernier combat.
     */
    private void addDrunkenness(int amount) {
        int previous = state.drunkenness();
        int updated = Math.max(0, previous + amount);
        if (updated == previous) {
            return;
        }
        state.setDrunkenness(updated);
        bus.publish(new GameEvent.DrunkennessChanged(state.drunkenness(), state.hangoverThreshold()));
        int threshold = state.hangoverThreshold();
        if (previous < threshold && updated >= threshold && !state.isFinalCombat()) {
            state.addPendingCurse(Curse.HANGOVER, 1);
            bus.publish(new GameEvent.CurseGained(Curse.HANGOVER));
        }
    }

    /**
     * Si l'adversaire est mort, passe au combat suivant (le nouvel adversaire n'attaque pas ce
     * tour-ci) ou termine la partie.
     *
     * @return {@code true} si l'adversaire était mort
     */
    private boolean resolveEnemyDeath() {
        Enemy enemy = state.currentEnemy();
        if (enemy.isAlive()) {
            return false;
        }
        if (enemy == lastKilled) {
            return true; // déjà traité
        }
        lastKilled = enemy;
        List<Trinket> returnedStolenTrinkets = state.returnStolenTrinkets();
        for(Trinket trinketReturned: returnedStolenTrinkets){
            bus.publish(new GameEvent.TrinketReturned(trinketReturned));
        }
        publishSetChanges();
        bus.publish(new GameEvent.EnemyKilled(enemy));
        changeChoomies(choomiesFor(enemy));
        endCombat();
        if (state.isFinalCombat()) {
            state.setWon();
            bus.publish(new GameEvent.GameWon());
            return true;
        }
        List<Collectible> offer = loot.offer(state, rng, REWARD_OPTIONS, minimumRarity(enemy));
        pendingCardReward = List.of();
        if (state.usesDeck()) {
            pendingCardReward = cardRewardOptions(); // proposée après la récompense d'objet
        }
        if (offer.isEmpty()) {
            offerCardReward();
            if (!state.isOpenEnded()) {
                nextCombat();
            }
        } else {
            pendingReward = offer;
            bus.publish(new GameEvent.RewardOffered(offer));
        }
        return true;
    }

    /** Choomies gagnés par rang : [min, max] (tirés au hasard), avant la stat {@link StatType#CHOOMIES_GAIN}. */
    public static final int[] COMMON_CHOOMIES = {12, 18};
    public static final int[] ELITE_CHOOMIES = {30, 40};
    public static final int[] BOSS_CHOOMIES = {80, 100};

    /** Choomies pour avoir battu {@code enemy} : sa valeur propre (Clarence), sinon selon son rang. */
    private int choomiesFor(Enemy enemy) {
        int base;
        if (enemy.choomiesReward() >= 0) {
            base = enemy.choomiesReward();
        } else {
            int[] range = switch (enemy.rank()) {
                case COMMON -> COMMON_CHOOMIES;
                case ELITE -> ELITE_CHOOMIES;
                case BOSS -> BOSS_CHOOMIES;
            };
            base = range[0] + rng.nextInt(range[1] - range[0] + 1);
        }
        return (int) Math.round(base * state.playerStats().get(StatType.CHOOMIES_GAIN));
    }

    /** Dernier adversaire dont la mort a été traitée (une seule fois par adversaire). */
    private Enemy lastKilled;

    private void nextCombat() {
        state.advanceEnemy();
        beginCombat();
    }

    // --- Récompenses ---

    // --- Deck ---

    /** Cartes proposées après chaque combat (deck seulement). */
    public static final int CARD_REWARD_OPTIONS = 3;
    /** Le deck ne descend jamais sous cette taille (retirer une carte). */
    public static final int MIN_DECK_SIZE = 5;

    private List<Card> pendingCardReward = List.of();

    /** {@code CARD_REWARD_OPTIONS} cartes de noms différents, tirées du pool (poids des trinkets compris). */
    private List<Card> cardRewardOptions() {
        List<Card> options = new java.util.ArrayList<>();
        int guard = 0;
        while (options.size() < CARD_REWARD_OPTIONS && guard++ < 200) {
            Card card = offerFromPool();
            if (options.stream().noneMatch(c -> c.name().equals(card.name()))) {
                options.add(card);
            }
        }
        return List.copyOf(options);
    }

    private void offerCardReward() {
        if (hasPendingCardReward()) {
            bus.publish(new GameEvent.CardRewardOffered(pendingCardReward));
        }
    }

    /** {@code true} si une récompense de carte attend (après la récompense d'objet). */
    public boolean hasPendingCardReward() {
        return !pendingCardReward.isEmpty();
    }

    public List<Card> pendingCardReward() {
        return pendingCardReward;
    }

    /**
     * Ajoute la carte {@code index} au deck, ou passe ({@code index} &lt; 0). À appeler après la
     * récompense d'objet.
     */
    public void resolveCardReward(int index) {
        if (!hasPendingCardReward()) {
            throw new IllegalStateException("Aucune carte en attente");
        }
        if (index >= pendingCardReward.size()) {
            bus.publish(new GameEvent.ActionRejected(Reason.INVALID_REWARD));
            return;
        }
        List<Card> options = pendingCardReward;
        pendingCardReward = List.of();
        if (index < 0) {
            bus.publish(new GameEvent.CardRewardSkipped());
        } else {
            addCardToDeck(options.get(index));
        }
    }

    /** Ajoute une carte au deck de la run (sans deck : rien). */
    public void addCardToDeck(Card card) {
        if (!state.usesDeck()) {
            return;
        }
        state.addToDeck(card.copy());
        bus.publish(new GameEvent.CardAddedToDeck(card));
    }

    /** {@code true} si on peut encore retirer une carte du deck (au-dessus de {@link #MIN_DECK_SIZE}). */
    public boolean canRemoveCard() {
        return state.usesDeck() && state.deck().size() > MIN_DECK_SIZE;
    }

    /** Retire la carte {@code index} du deck. */
    public void removeCardFromDeck(int index) {
        if (!canRemoveCard() || index < 0 || index >= state.deck().size()) {
            throw new IllegalArgumentException("Carte impossible à retirer : " + index);
        }
        bus.publish(new GameEvent.CardRemovedFromDeck(state.removeFromDeck(index)));
    }

    /** Nombre de propositions après chaque adversaire vaincu. */
    public static final int REWARD_OPTIONS = 3;

    /** Rareté garantie des récompenses : commun = aucune, élite = Peu commun, boss = Rare. */
    public static cardistry.items.Rarity minimumRarity(Enemy enemy) {
        return switch (enemy.rank()) {
            case COMMON -> cardistry.items.Rarity.COMMON;
            case ELITE -> cardistry.items.Rarity.UNCOMMON;
            case BOSS -> cardistry.items.Rarity.RARE;
        };
    }

    // --- Sieste et fin de niveau ---

    /** Part des PV max rendue par la Sieste. */
    public static final double REST_HEAL = 0.30;
    /** Clopes gagnées à la Sieste (« Rouler tranquille »), sans dépasser le paquet. */
    public static final int REST_CIGARETTES = 3;
    /** Part des PV manquants rendue après chaque boss (sauf le dernier). */
    public static final double BOSS_RECOVERY = 0.5;

    /**
     * Sieste : soin de {@link #REST_HEAL} des PV max. C'est un soin : efficacité des soins et Ross
     * s'appliquent (avec Ross, pas de plafond).
     */
    public void restHeal(String choice) {
        bus.publish(new GameEvent.RestTaken(choice));
        effects.healPlayer(state.maxHp() * REST_HEAL);
    }

    /** Sieste : +{@link #REST_CIGARETTES} clopes (sans dépasser le paquet) ; renvoie le nombre ajouté. */
    public int restCigarettes(String choice) {
        bus.publish(new GameEvent.RestTaken(choice));
        return effects.addCigarettes(REST_CIGARETTES);
    }

    /** Sieste : retire la carte {@code index} du deck. */
    public void restRemoveCard(String choice, int index) {
        bus.publish(new GameEvent.RestTaken(choice));
        removeCardFromDeck(index);
    }

    /** PV max gagnés pour toute la partie après chaque boss (sauf le dernier), avec autant de PV actuels. */
    public static final double BOSS_MAX_HP_BONUS = 25;
    /** Points de stats à répartir après chaque boss (sauf le dernier). */
    public static final int BOSS_STAT_POINTS = 2;
    /** Source des PV max gagnés sur les boss. */
    public static final String BOSS_BONUS_SOURCE = "boss";
    /** Source des points de stats répartis. */
    public static final String PROGRESSION_SOURCE = "progression";

    /**
     * Après un boss : la moitié des PV manquants (récupération, pas un soin : aucun bonus de soin),
     * puis +{@link #BOSS_MAX_HP_BONUS} PV max (et actuels) pour toute la partie et
     * {@link #BOSS_STAT_POINTS} points de stats à répartir.
     */
    public void recoverAfterBoss() {
        double missing = Math.max(0, state.maxHp() - state.player().hp());
        if (missing > 0) {
            healPlayer(missing * BOSS_RECOVERY, 1.0);
        }
        state.playerStats().add(cardistry.core.stats.StatModifier.flat(StatType.MAX_HP, BOSS_MAX_HP_BONUS,
                BOSS_BONUS_SOURCE));
        state.player().heal(BOSS_MAX_HP_BONUS);
        state.setStatPoints(state.statPoints() + BOSS_STAT_POINTS);
        bus.publish(new GameEvent.BossBonus(BOSS_MAX_HP_BONUS, BOSS_STAT_POINTS));
    }

    /**
     * Place un point de stats dans {@code stat} (+1 pour toute la partie).
     *
     * @return {@code false} s'il n'y a pas de point à placer ou si ce n'est pas une des 6 stats
     */
    public boolean spendStatPoint(StatType stat) {
        if (state.statPoints() <= 0 || !StatType.characterStats().contains(stat)) {
            bus.publish(new GameEvent.ActionRejected(GameEvent.ActionRejected.Reason.INVALID_CHOICE));
            return false;
        }
        state.playerStats().add(cardistry.core.stats.StatModifier.flat(stat, 1, PROGRESSION_SOURCE));
        state.setStatPoints(state.statPoints() - 1);
        bus.publish(new GameEvent.StatPointSpent(stat, state.statPoints()));
        return true;
    }

    /**
     * Définition de l'autel sacrificiel, permet de choisir une stat a augmenter d un point en echange de 10 pv
     */
    public static final double ALTAR_MAX_HP_COST=10;


    //*Sacrifice, mouahahahahahaha */
    public boolean sacrifice(StatType stat){
        if (!StatType.characterStats().contains(stat)){
            return false;
        }
        boolean surplus = state.player().hp() > state.maxHp(); //dans le cas de ross
        state.playerStats().add(cardistry.core.stats.StatModifier.flat(StatType.MAX_HP, -ALTAR_MAX_HP_COST, "autel"));
        state.playerStats().add(cardistry.core.stats.StatModifier.flat(stat, 1, "autel"));

        if (surplus){ //cas de ross
            state.player().takeDamage(ALTAR_MAX_HP_COST);
        }
        else{//cas normal
            int over = state.player().hp() - (int) state.maxHp();
            if (over>0){
                state.player().takeDamage(over);
            }
        }
        return true;
    }


    public boolean hasPendingReward() {
        return !pendingReward.isEmpty();
    }

    /** Propositions en attente (vide s'il n'y en a pas). */
    public List<Collectible> pendingReward() {
        return pendingReward;
    }

    /**
     * Applique le choix du joueur puis lance le combat suivant.
     *
     * @return {@code false} si le choix est invalide (un {@link GameEvent.ActionRejected} est publié,
     *     l'offre reste en attente)
     */
    public boolean resolveReward(RewardChoice choice) {
        if (!hasPendingReward()) {
            throw new IllegalStateException("Aucune récompense en attente");
        }
        switch (choice) {
            case RewardChoice.Skip skip -> bus.publish(new GameEvent.RewardSkipped());
            case RewardChoice.Take take -> {
                Optional<Reason> rejection = validateReward(take);
                if (rejection.isPresent()) {
                    bus.publish(new GameEvent.ActionRejected(rejection.get()));
                    return false;
                }
                Collectible taken = pendingReward.get(take.option());
                Collectible replaced = putInInventory(taken, take.replaceSlot());
                bus.publish(new GameEvent.RewardTaken(taken, replaced));
                publishSetChanges();
                enforceTrinketSlots();
            }
        }
        pendingReward = List.of();
        offerCardReward();
        if (!state.isOpenEnded()) {
            nextCombat();
        }
        return true;
    }

    /** Met {@code taken} dans l'inventaire (à la place de {@code replaceSlot} si ≥ 0) ; renvoie l'ancien. */
    private Collectible putInInventory(Collectible taken, int replaceSlot) {
        return switch (taken) {
            case Trinket trinket -> {
                if (replaceSlot >= 0) {
                    yield state.replaceTrinket(replaceSlot, trinket);
                }
                state.equipTrinket(trinket);
                yield null;
            }
            case Item item -> {
                Item old = replaceSlot >= 0 ? state.removeItem(replaceSlot) : null;
                state.addItem(item);
                yield old;
            }
        };
    }

    /** {@code null} si {@code taken} peut aller dans l'inventaire (en remplaçant {@code replaceSlot}). */
    private Reason inventoryProblem(Collectible taken, int replaceSlot) {
        int used = taken instanceof Trinket ? state.trinkets().size() : state.items().size();
        int slots = taken instanceof Trinket ? state.trinketSlots() : state.itemSlots();
        if (replaceSlot < 0) {
            if (taken instanceof Trinket trinket) {
                return state.hasRoomFor(trinket) ? null : Reason.INVENTORY_FULL;
            }
            return used < slots ? null : Reason.INVENTORY_FULL;
        }
        return replaceSlot < used ? null : Reason.INVALID_REWARD;
    }

    // --- Événements ---

    private cardistry.story.Story story;
    private GameEvent.StoryResolved lastStoryResult;

    /** Résultat du dernier choix d'événement (pour l'afficher). */
    public Optional<GameEvent.StoryResolved> lastStoryResult() {
        return Optional.ofNullable(lastStoryResult);
    }

    /** Événement en cours (nœud {@code ?} de la carte), s'il y en a un. */
    public Optional<cardistry.story.Story> story() {
        return Optional.ofNullable(story);
    }

    /** Commence un événement : le moteur attend {@link #chooseStory(int)}. */
    public void startStory(cardistry.story.Story newStory) {
        if (inCombat() || hasPendingReward() || hasPendingCardReward() || shop != null || story != null || isOver()) {
            throw new IllegalStateException("Pas d'événement maintenant");
        }
        story = newStory;
        bus.publish(new GameEvent.StoryStarted(newStory));
    }

    /**
     * Applique le choix {@code index} de l'événement en cours, puis le termine.
     *
     * @return {@code false} si le choix n'existe pas ou n'est pas disponible (l'événement reste ouvert)
     */
    public boolean chooseStory(int index) {
        if (story == null) {
            throw new IllegalStateException("Aucun événement en cours");
        }
        if (index < 0 || index >= story.choices().size() || !story.choices().get(index).isAvailable(state)) {
            bus.publish(new GameEvent.ActionRejected(Reason.CHOICE_UNAVAILABLE));
            return false;
        }
        cardistry.story.Story current = story;
        cardistry.story.StoryChoice choice = current.choices().get(index);
        List<String> lines = new java.util.ArrayList<>();
        story = null;
        choice.outcome().accept(new StoryEffects(lines));
        lastStoryResult = new GameEvent.StoryResolved(current.title(), choice.label(), lines);
        bus.publish(lastStoryResult);
        return true;
    }

    /** Conséquences d'un choix d'événement. */
    private final class StoryEffects implements cardistry.story.StoryContext {

        private final List<String> lines;

        StoryEffects(List<String> lines) {
            this.lines = lines;
        }

        @Override
        public GameState state() {
            return state;
        }

        @Override
        public void say(String line) {
            lines.add(line);
        }

        @Override
        public boolean chance(double probability) {
            return effects.chance(probability);
        }

        @Override
        public void choomies(int amount) {
            changeChoomies(amount);
        }

        @Override
        public void heal(double amount) {
            effects.healPlayer(amount);
        }

        @Override
        public void hurt(double amount) {
            double dealt = Math.min(amount, Math.max(0, state.player().hp() - 1));
            if (dealt > 0) {
                effects.damagePlayer(dealt);
            }
        }

        @Override
        public int cigarettes(int count) {
            return effects.addCigarettes(count);
        }

        @Override
        public void hangover() {
            state.addPendingCurse(Curse.HANGOVER, 1);
            bus.publish(new GameEvent.CurseGained(Curse.HANGOVER));
        }

        @Override
        public void addCard(Card card) {
            addCardToDeck(card);
        }
    }

    // --- Shop ---

    private Shop shop;

    /** Shop ouvert (nœud Shop de la carte), s'il y en a un. */
    public Optional<Shop> shop() {
        return Optional.ofNullable(shop);
    }

    /** Ouvre un shop : articles tirés, rerolls à zéro. */
    public void openShop() {
        if (inCombat() || hasPendingReward() || hasPendingCardReward() || story != null || isOver()) {
            throw new IllegalStateException("Pas de shop maintenant");
        }
        shop = new Shop();
        shop.restock(stock());
        bus.publish(new GameEvent.ShopEntered(shop));
    }

    /** {@link Shop#CARDS} cartes de noms différents, {@link Shop#ITEMS} objets, {@link Shop#TRINKETS} trinkets (sans secret). */
    private List<cardistry.core.shop.ShopOffer> stock() {
        List<cardistry.core.shop.ShopOffer> stock = new java.util.ArrayList<>();
        List<String> names = new java.util.ArrayList<>();
        int guard = 0;
        while (names.size() < Shop.CARDS && guard++ < 500) {
            Card card = offerFromPool();
            if (!names.contains(card.name())) {
                names.add(card.name());
                stock.add(new cardistry.core.shop.ShopOffer.CardOffer(card, cardistry.core.shop.ShopPrices.price(card)));
            }
        }
        LootTable items = new LootTable(() -> List.copyOf(cardistry.items.ItemRegistry.items()));
        LootTable trinkets = new LootTable(() -> List.copyOf(cardistry.items.ItemRegistry.trinkets()));
        for (Collectible c : items.offer(state, rng, Shop.ITEMS, cardistry.items.Rarity.COMMON, false)) {
            stock.add(new cardistry.core.shop.ShopOffer.CollectibleOffer(c, cardistry.core.shop.ShopPrices.price(c)));
        }
        for (Collectible c : trinkets.offer(state, rng, Shop.TRINKETS, cardistry.items.Rarity.COMMON, false)) {
            stock.add(new cardistry.core.shop.ShopOffer.CollectibleOffer(c, cardistry.core.shop.ShopPrices.price(c)));
        }
        return stock;
    }

    /** Prix du prochain retrait de carte au shop. */
    public int cardRemovalPrice() {
        return cardistry.core.shop.ShopPrices.removal(state.cardRemovals());
    }

    /**
     * Applique une action au shop.
     *
     * @return {@code false} si elle est refusée (un {@link GameEvent.ActionRejected} est publié)
     */
    public boolean shopAction(cardistry.core.shop.ShopAction action) {
        if (shop == null) {
            throw new IllegalStateException("Aucun shop ouvert");
        }
        Optional<Reason> rejection = switch (action) {
            case cardistry.core.shop.ShopAction.Buy buy -> buy(buy);
            case cardistry.core.shop.ShopAction.RemoveCard remove -> removeInShop(remove.deckIndex());
            case cardistry.core.shop.ShopAction.Reroll reroll -> rerollShop();
            case cardistry.core.shop.ShopAction.Leave leave -> {
                shop = null;
                bus.publish(new GameEvent.ShopLeft());
                yield Optional.empty();
            }
        };
        rejection.ifPresent(r -> bus.publish(new GameEvent.ActionRejected(r)));
        return rejection.isEmpty();
    }

    private Optional<Reason> buy(cardistry.core.shop.ShopAction.Buy buy) {
        if (buy.offer() < 0 || buy.offer() >= shop.offers().size() || shop.isSold(buy.offer())) {
            return Optional.of(Reason.SOLD_OUT);
        }
        cardistry.core.shop.ShopOffer offer = shop.offers().get(buy.offer());
        if (state.choomies() < offer.price()) {
            return Optional.of(Reason.NOT_ENOUGH_CHOOMIES);
        }
        Collectible replaced = null;
        if (offer instanceof cardistry.core.shop.ShopOffer.CollectibleOffer c) {
            Reason problem = inventoryProblem(c.collectible(), buy.replaceSlot());
            if (problem != null) {
                return Optional.of(problem);
            }
        }
        changeChoomies(-offer.price());
        shop.markSold(buy.offer());
        switch (offer) {
            case cardistry.core.shop.ShopOffer.CardOffer card -> {
                bus.publish(new GameEvent.ItemBought(offer, null));
                addCardToDeck(card.card());
            }
            case cardistry.core.shop.ShopOffer.CollectibleOffer c -> {
                replaced = putInInventory(c.collectible(), buy.replaceSlot());
                bus.publish(new GameEvent.ItemBought(offer, replaced));
                publishSetChanges();
                enforceTrinketSlots();
            }
        }
        return Optional.empty();
    }

    private Optional<Reason> removeInShop(int deckIndex) {
        if (!canRemoveCard()) {
            return Optional.of(Reason.DECK_TOO_SMALL);
        }
        if (deckIndex < 0 || deckIndex >= state.deck().size()) {
            return Optional.of(Reason.INVALID_CHOICE);
        }
        int price = cardRemovalPrice();
        if (state.choomies() < price) {
            return Optional.of(Reason.NOT_ENOUGH_CHOOMIES);
        }
        changeChoomies(-price);
        state.countCardRemoval();
        bus.publish(new GameEvent.CardRemovalBought(price));
        removeCardFromDeck(deckIndex);
        return Optional.empty();
    }

    private Optional<Reason> rerollShop() {
        int price = shop.rerollPrice();
        if (state.choomies() < price) {
            return Optional.of(Reason.NOT_ENOUGH_CHOOMIES);
        }
        changeChoomies(-price);
        shop.countReroll();
        shop.restock(stock());
        bus.publish(new GameEvent.ShopRestocked(price));
        return Optional.empty();
    }

    /** S'il y a plus de trinkets que d'emplacements (Diplôme perdu), le joueur en jette au choix. */
    private void enforceTrinketSlots() {
        while (state.trinkets().size() > state.trinketSlots()) {
            int slot = decisions.chooseTrinketToDiscard(state);
            if (slot < 0 || slot >= state.trinkets().size()) {
                continue; // réponse invalide : on redemande
            }
            Trinket discarded = state.unequipTrinket(slot);
            bus.publish(new GameEvent.TrinketDiscarded(discarded));
            publishSetChanges();
        }
    }

    private Optional<Reason> validateReward(RewardChoice.Take take) {
        if (take.option() < 0 || take.option() >= pendingReward.size()) {
            return Optional.of(Reason.INVALID_REWARD);
        }
        return Optional.ofNullable(inventoryProblem(pendingReward.get(take.option()), take.replaceSlot()));
    }

    // --- Actions du joueur ---

    /** Pioche sans deck (tests, adversaires fixés) : une carte du pool, poids des trinkets seulement. */
    private Card drawFromPool() {
        return pool.draw(rng, state::cardWeight);
    }

    /**
     * Carte proposée (récompense de carte, shop, George) : tirée selon sa rareté, comme les objets
     * ({@link cardistry.items.Rarity#dropWeight()}), et les poids des trinkets.
     */
    private Card offerFromPool() {
        return pool.draw(rng, card -> offerWeight(state, card));
    }

    /** Poids d'une carte proposée : poids de sa rareté × poids des trinkets (Grinder…). */
    public static double offerWeight(GameState state, Card card) {
        return card.rarity().cardWeight() * state.cardWeight(card);
    }

    /**
     * Une carte de la pioche : avec un deck, le dessus de la pioche (la défausse est remélangée si
     * elle est vide ; rien si les deux sont vides) ; sans deck, une carte du pool.
     */
    private Optional<Card> drawOne() {
        if (!state.usesDeck()) {
            return Optional.of(drawFromPool());
        }
        if (state.drawPile().isEmpty()) {
            if (state.discardPile().isEmpty()) {
                return Optional.empty();
            }
            bus.publish(new GameEvent.DeckShuffled(state.reshuffleDiscard(rng)));
        }
        return Optional.of(state.takeFromDrawPile());
    }

    /** Carte qui quitte la main (jouée, remplacée, transformée) : à la défausse s'il y a un deck. */
    private void discard(Card card) {
        if (state.usesDeck()) {
            state.discard(card);
        }
    }

    /**
     * Pioche une carte. Si la stat {@link StatType#DRAW_CHOICES} vaut N &gt; 1 (Paquet de cartes), N
     * cartes sont tirées de la pioche et le joueur en garde une ; les autres vont à la défausse.
     */
    private void drawCard() {
        int choices = state.playerStats().getInt(StatType.DRAW_CHOICES);
        Optional<Card> card = choices <= 1 ? drawOne() : chooseAmong(choices);
        if (card.isEmpty()) {
            return;
        }
        state.addToHand(card.get());
        bus.publish(new GameEvent.CardDrawn(card.get()));
    }

    private Optional<Card> chooseAmong(int count) {
        List<Card> options = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            drawOne().ifPresent(options::add);
        }
        if (options.size() <= 1) {
            return options.stream().findFirst();
        }
        bus.publish(new GameEvent.CardChoiceOffered(options));
        int index;
        do {
            index = decisions.chooseDrawnCard(state, List.copyOf(options));
        } while (index < 0 || index >= options.size());
        Card kept = options.remove(index);
        options.forEach(this::discard);
        bus.publish(new GameEvent.CardChosen(kept, options));
        return Optional.of(kept);
    }

    private void playCard(int slot) {
        Card card = state.removeFromHand(slot);
        boolean onSpace = state.terrain().effect() == TerrainCard.Effect.REPLAY;
        bus.publish(new GameEvent.CardPlayed(card));
        if (card instanceof SpellCard spell && state.drunkTier() == DrunkTier.WASTED
                && rng.nextDouble() < DrunkTier.WASTED_MISS_CHANCE) {
            bus.publish(new GameEvent.SpellMissed(spell));
            discard(spell); // raté : pas de dégâts, pas de pouvoir, pas de rejeu
            return;
        }
        resolveCard(card, 1.0);
        if (onSpace && !(card instanceof TerrainCard) && state.terrain().effect() == TerrainCard.Effect.REPLAY) {
            state.addEcho(card);
        }
        discard(card);
    }

    /** Part des dégâts et des soins d'une carte rejouée par l'Espace de dimension n. */
    public static final double ECHO_FACTOR = 0.5;

    /**
     * Effet d'une carte : dégâts ou soin × {@code factor} (1 au jeu, {@link #ECHO_FACTOR} au rejeu),
     * changement de terrain, puis son pouvoir (en entier, avec ses chances normales). Un rejeu ne
     * consomme pas le prochain sort ×N.
     */
    private void resolveCard(Card card, double factor) {
        boolean echo = factor != 1.0;
        switch (card) {
            case SpellCard spell -> {
                Optional<TemporaryEffect> boost = echo ? Optional.empty() : state.nextSpellBoost();
                double base = spell.baseDamage(effects); // calculé avant le coup (Matrix, Pyromane…)
                double mult = spell.isUnmultiplied() ? 1.0 : state.spellMultiplier(spell, !echo);
                Enemy target = state.currentEnemy();
                if (base > 0) {
                    boolean critical = !spell.isUnmultiplied() && playerCritical(spell);
                    int amount = state.spellDamage(spell, base, factor, critical, !echo);
                    if (critical && !target.dodgeReady()) {
                        bus.publish(new GameEvent.CriticalHit(target));
                    }
                    double hpLost = damageEnemy(amount, mult, GameEvent.DamageDealt.Source.SPELL);
                    if (!spell.isUnmultiplied()) {
                        boost.ifPresent(this::consume);
                    }
                    if (hpLost > 0) {
                        bus.publish(new GameEvent.SpellHit(spell, target, hpLost));
                    }
                }
            }
            case HealCard heal -> {
                if (heal.amount() > 0) { // un « soin » à 0 n'agit que par son pouvoir
                    double mult = state.healMultiplier(heal);
                    healPlayer(state.healAmount(heal) * factor, mult);
                }
            }
            case TerrainCard terrain -> changeTerrain(terrain);
            case DeadCard dead -> throw new AssertionError("refusée par validate");
        }
        card.power().ifPresent(power -> {
            bus.publish(new GameEvent.CardPowerTriggered(card, power.text()));
            power.effect().accept(effects);
        });
    }

    /** Début du tour du joueur : rejoue à 50 % les cartes jouées sur l'Espace, s'il y est encore. */
    private void replayEchoes() {
        List<Card> echoes = state.takeEchoes();
        if (state.terrain().effect() != TerrainCard.Effect.REPLAY) {
            return;
        }
        for (Card card : echoes) {
            if (state.isLost() || !state.currentEnemy().isAlive()) {
                return;
            }
            bus.publish(new GameEvent.CardReplayed(card, ECHO_FACTOR));
            resolveCard(card, ECHO_FACTOR);
        }
    }

    /** Identifiant de l'objet dont une clope transformée donne l'effet (Googz). */
    static final String PETIT_PETOU = "petit-petou";

    /** PV rendus par une clope (un soin : l'efficacité des soins s'applique). */
    public static final double CIGARETTE_HEAL = 5;

    /**
     * Fumer : +{@link #CIGARETTE_HEAL} PV et −1 Ivresse (ou, selon la stat
     * {@link StatType#CIGARETTE_TRANSFORM_CHANCE}, l'effet de l'objet Petit pétou à la place). La
     * Gueule de bois en cours perd 1 d'intensité ; celle à venir aussi, mais seulement si l'Ivresse
     * est repassée sous le seuil (on ne garde pas la Cuite sans la payer).
     */
    private void smoke() {
        state.setCigarettes(state.cigarettes() - 1);
        bus.publish(new GameEvent.CigarettesChanged(state.cigarettes(), state.cigaretteMax()));
        double chance = state.playerStats().get(StatType.CIGARETTE_TRANSFORM_CHANCE);
        boolean transformed = chance > 0 && Luck.chance(state, rng, chance);
        bus.publish(new GameEvent.CigaretteSmoked(transformed));
        if (transformed) {
            cardistry.items.ItemRegistry.item(PETIT_PETOU).use(effects);
        } else {
            effects.healPlayer(CIGARETTE_HEAL);
            addDrunkenness(-1);
        }
        if (state.activeIntensity(Curse.HANGOVER) > 0 || state.drunkenness() < state.hangoverThreshold()) {
            reduceCurse(Curse.HANGOVER);
        }
    }

    /** Retire 1 d'intensité : d'abord à la malédiction active, sinon à celle à venir. */
    private void reduceCurse(Curse curse) {
        int active = state.activeIntensity(curse);
        if (active > 0) {
            state.setActiveIntensity(curse, active - 1);
            if (active - 1 == 0) {
                bus.publish(new GameEvent.CurseEnded(curse));
            } else {
                bus.publish(new GameEvent.CurseReduced(curse, active - 1, false));
            }
            return;
        }
        int pending = state.pendingIntensity(curse);
        if (pending > 0) {
            state.setPendingIntensity(curse, pending - 1);
            bus.publish(new GameEvent.CurseReduced(curse, pending - 1, true));
        }
    }

    private void useItem(int slot) {
        Item item = state.removeItem(slot);
        bus.publish(new GameEvent.ItemUsed(item));
        item.use(effects);
        if (item.tags().contains(Tag.DRINK)) {
            addDrunkenness(1);
        }
    }

    /**
     * Tour de l'adversaire : il exécute l'attaque annoncée, puis annonce la suivante. Un tour sauté
     * (Pétou ancestral) garde l'intent et n'avance pas ses compteurs.
     */
    /** Tour de l'adversaire : ses statuts (début), son action, puis le décompte de ses statuts (fin). */
    private void enemyTurn() {
        Enemy enemy = state.currentEnemy();
        startOfTurnStatuses(StatusTarget.ENEMY);
        if (!enemy.isAlive()) {
            return; // mort de ses statuts : le combat se résout chez l'appelant
        }
        enemyAct();
        if (enemy.isAlive() && state.player().isAlive()) {
            endOfTurnStatuses(StatusTarget.ENEMY);
        }
    }

    private void enemyAct() {
        Enemy enemy = state.currentEnemy();
        if (state.statuses(StatusTarget.ENEMY).has(StatusType.FONS)) {
            bus.publish(new GameEvent.EnemyTurnSkipped(enemy, StatusType.FONS.displayName(), enemy.name() + " dort."));
            return; // l'attaque annoncée attend son réveil
        }
        Optional<TemporaryEffect> skip = state.effects().stream()
                .filter(e -> e.isActive() && e.skipsEnemyTurn()).findFirst();
        if (skip.isPresent()) {
            bus.publish(new GameEvent.EnemyTurnSkipped(enemy, skip.get().name(), skip.get().enemySkipMessage(enemy.name())));
            consume(skip.get());
            return;
        }
        EnemyMove move = enemy.intent() != null ? enemy.intent() : enemy.chooseIntent();
        bus.publish(new GameEvent.EnemyMoveUsed(enemy, move));
        MoveRun run = new MoveRun(enemy);
        for (MoveEffect effect : move.effects()) {
            if (!state.player().isAlive() || !enemy.isAlive()) {
                break;
            }
            execute(effect, run);
        }
        enemy.recordAction(move);
        if (state.player().isAlive() && enemy.isAlive()) {
            announceIntent();
        }
    }

    /** État d'une attaque en cours : une annulation (Moscow Mule) vaut pour tous ses coups. */
    private static final class MoveRun {

        final Enemy enemy;
        boolean shieldChecked;
        boolean cancelled;
        /** Au moins un coup de cette attaque a porté (statuts « après un coup »). */
        boolean landed;

        MoveRun(Enemy enemy) {
            this.enemy = enemy;
        }
    }

    private void execute(MoveEffect effect, MoveRun run) {
        Enemy enemy = run.enemy;
        switch (effect) {
            case MoveEffect.Hit hit -> {
                for (int i = 0; i < hit.hits() && state.player().isAlive(); i++) {
                    strike(hit, run);
                }
            }
            case MoveEffect.Pickpocket steal -> {
                if (steal.choomies() > 0 && state.choomies() >= steal.choomies()) {
                    changeChoomies(-steal.choomies());
                    bus.publish(new GameEvent.ChoomiesStolen(enemy, steal.choomies()));
                } else if (steal.cigarettes() > 0 && state.cigarettes() > 0) {
                    takeCigarettes(enemy, steal.cigarettes());
                } else if (steal.fallbackDamage() > 0) {
                    strike(MoveEffect.Hit.of(steal.fallbackDamage()), run);
                }
            }
            case MoveEffect.Tax tax -> {
                if (state.cigarettes() > 0) {
                    takeCigarettes(enemy, tax.cigarettes());
                } else {
                    execute(MoveEffect.Hit.of(tax.damage(), tax.fallbackHits()), run);
                }
            }
            case MoveEffect.Gamble gamble -> execute(
                    rng.nextDouble() < gamble.chance() ? gamble.onSuccess() : gamble.onFailure(), run);

            case MoveEffect.StealTrinket steal -> {
                if (attackBlocked(run)) {
                    return;
                }
                if(state.trinkets().isEmpty()) {
                    strike(MoveEffect.Hit.of(steal.fallbackDamage()), run);
                    return;
                }
                if(rng.nextDouble() >= steal.chance()) {
                    return;
                }
                stealRandomTrinket(run);
                publishSetChanges();
            }
            case MoveEffect.Dodge dodge -> {
                enemy.setDodgeReady(true);
                bus.publish(new GameEvent.DodgeReady(enemy));
            }
            case MoveEffect.CurseCard curse -> {
                List<Integer> slots = new java.util.ArrayList<>();
                for (int i = 0; i < state.hand().size(); i++) {
                    if (!(state.hand().get(i) instanceof DeadCard)) {
                        slots.add(i);
                    }
                }
                if (!slots.isEmpty()) {
                    int slot = rng.pick(slots);
                    Card dead = new DeadCard(curse.cardName());
                    Card removed = state.replaceInHand(slot, dead);
                    discard(removed);
                    bus.publish(new GameEvent.CardCursed(enemy, removed, dead));
                }
            }
            case MoveEffect.HealSelf heal -> {
                double healed = enemy.healSelf(CombatFormula.heal(heal.amount() * enemy.damageScale(),
                        enemy.stat(StatType.MIND), 1.0));
                bus.publish(new GameEvent.EnemyHealed(enemy, healed));
            }
            case MoveEffect.ChangeElement change -> {
                Element previous = enemy.element();
                List<Element> others = Element.PLAYABLE.stream().filter(e -> e != previous && e != Element.GLITCH)
                        .toList();
                Element next = rng.pick(others);
                enemy.changeElement(next);
                bus.publish(new GameEvent.EnemyElementChanged(enemy, previous, next));
            }

            case MoveEffect.SetTerrain set -> {
                changeTerrain(set.terrain().get());
            }

            case MoveEffect.Inflict inflict -> {
                if (!inflict.afterHit() || run.landed) {
                    tryStatus(StatusTarget.PLAYER, StatusType.byId(inflict.status()), inflict.chance(),
                            inflict.turns(), StatusExtras.NONE, StatusTarget.ENEMY, true);
                }
            }

            case MoveEffect.Drink drink -> {
                addDrunkenness(drink.amount());
            }

        }
    }


    /** Vole un trinket équipé, tiré au hasard, le met de côté et publie TrinketStolen. */
    private void stealRandomTrinket(MoveRun run) {
        int slot = rng.nextInt(state.trinkets().size());
        Trinket stolenTrinket = state.stealTrinket(slot);
        bus.publish(new GameEvent.TrinketStolen(run.enemy, stolenTrinket));
    }


    /** Annulation d’une attaque : immunité de Fons ou Moscow Mule, vérifiée une fois par attaque */
    private boolean attackBlocked(MoveRun run) {
        if (!run.shieldChecked) {
            run.shieldChecked = true;
            boolean immune = state.statuses(StatusTarget.PLAYER).get(StatusType.FONS)
                    .map(f -> f.extras().immuneToAttacks()).orElse(false);
            if (immune) {
                bus.publish(new GameEvent.AttackCancelled(run.enemy, StatusType.FONS.displayName()));
                run.cancelled = true;
                return true;
            }
            Optional<TemporaryEffect> shield = state.effects().stream()
                    .filter(e -> e.isActive() && e.cancelsEnemyAttack()).findFirst();
            if (shield.isPresent()) {
                bus.publish(new GameEvent.AttackCancelled(run.enemy, shield.get().name()));
                consume(shield.get());
                run.cancelled = true;
            }
        }
        return run.cancelled;
    }

    /** Un coup : annulé (Moscow Mule, une fois pour toute l'attaque), raté, ou subi. */
    private void strike(MoveEffect.Hit hit, MoveRun run) {
        Enemy enemy = run.enemy;
        if(attackBlocked(run)){return;}
        if (hit.missChance() > 0 && rng.nextDouble() < hit.missChance()) {
            double healed = hit.healOnMiss() > 0 ? enemy.healSelf(CombatFormula.heal(hit.healOnMiss() * enemy.damageScale(),
                    enemy.stat(StatType.MIND), 1.0)) : 0;
            bus.publish(new GameEvent.EnemyMissed(enemy, healed));
            return;
        }
        Player player = state.player();
        double mult = state.enemyAttackMultiplier();
        // Tirage de l'adversaire : pas un tirage de chance du joueur (CLAUDE.md ne le double pas).
        double critChance = CombatFormula.critChance(enemy.stat(StatType.LUCK));
        boolean critical = critChance > 0 && rng.nextDouble() < critChance;
        Optional<cardistry.model.Familiar> familiar = state.familiar().filter(cardistry.model.Familiar::isTargeted);
        if (familiar.isPresent()) {
            strikeFamiliar(familiar.get(), hit, critical, mult, enemy);
            return;
        }
        double amount = Combatant.round(state.hitDamage(hit, critical));
        bus.publish(new GameEvent.EnemyAttacked(enemy));
        if (critical) {
            bus.publish(new GameEvent.CriticalHit(player));
        }
        player.takeDamage(amount);
        run.landed = true;
        bus.publish(new GameEvent.DamageDealt(player, amount, mult, GameEvent.DamageDealt.Source.ENEMY_ATTACK));
        if (!player.isAlive()) {
            bus.publish(new GameEvent.GameLost());
        }
    }

    /**
     * Coup sur le familier visé : sans la DEF du joueur ; aucun statut « après un coup » (le familier
     * n'en subit pas). Assommé, les coups suivants de l'attaque se perdent.
     */
    private void strikeFamiliar(cardistry.model.Familiar familiar, MoveEffect.Hit hit, boolean critical, double mult,
            Enemy enemy) {
        if (familiar.isKnockedOut()) {
            return;
        }
        double amount = Combatant.round(state.familiarHitDamage(hit, critical));
        bus.publish(new GameEvent.EnemyAttacked(enemy));
        if (critical) {
            bus.publish(new GameEvent.CriticalHit(familiar));
        }
        familiar.takeDamage(amount);
        bus.publish(new GameEvent.DamageDealt(familiar, amount, mult, GameEvent.DamageDealt.Source.ENEMY_ATTACK));
        if (familiar.isKnockedOut()) {
            bus.publish(new GameEvent.FamiliarKnockedOut(familiar));
        }
    }

    private void takeCigarettes(Enemy enemy, int count) {
        int taken = Math.min(count, state.cigarettes());
        if (taken <= 0) {
            return;
        }
        state.setCigarettes(state.cigarettes() - taken);
        bus.publish(new GameEvent.CigarettesStolen(enemy, taken));
        bus.publish(new GameEvent.CigarettesChanged(state.cigarettes(), state.cigaretteMax()));
    }

    /** Donne (ou retire, si négatif) des Choomies hors combat (événements, tests), jamais sous 0. */
    public void gainChoomies(int amount) {
        changeChoomies(amount);
    }

    /** Ajoute (ou retire) des Choomies, jamais sous 0. */
    private void changeChoomies(int delta) {
        int updated = Math.max(0, state.choomies() + delta);
        int real = updated - state.choomies();
        if (real != 0) {
            state.setChoomies(updated);
            bus.publish(new GameEvent.ChoomiesChanged(updated, real));
        }
    }

    // --- Primitives partagées par les cartes et les effets ---

    /**
     * Dégâts à l'adversaire. Un sort contre un adversaire prêt à esquiver ne fait rien. Rebond :
     * une part des dégâts est renvoyée au joueur, sans jamais le tuer.
     *
     * @return PV réellement enlevés à l'adversaire (0 s'il esquive)
     */
    private double damageEnemy(double amount, double mult, GameEvent.DamageDealt.Source source) {
        Enemy enemy = state.currentEnemy();
        if (source == GameEvent.DamageDealt.Source.SPELL && enemy.dodgeReady()) {
            enemy.setDodgeReady(false);
            bus.publish(new GameEvent.EnemyDodged(enemy));
            return 0;
        }
        amount = Combatant.round(amount);
        double hpLost = Math.max(0, Math.min(amount, enemy.hp()));
        enemy.takeDamage(amount);
        bus.publish(new GameEvent.DamageDealt(enemy, amount, mult, source));
        if (enemy.reflectRatio() > 0 && amount > 0) {
            Player player = state.player();
            double reflected = Math.min(Combatant.round(amount * enemy.reflectRatio()), Math.max(0, player.hp() - 1));
            if (reflected > 0) {
                player.takeDamage(reflected);
                bus.publish(new GameEvent.DamageDealt(player, reflected, enemy.reflectRatio(),
                        GameEvent.DamageDealt.Source.REFLECTED));
            }
        }
        return hpLost;
    }



    /** Royaume du Colonel : les soins du joueur sont divisés par deux. */
    private static final double COLONEL_HEAL_FACTOR = 0.5;

    /**
     * Soigne sans dépasser les PV max (sauf si les soins ne sont pas plafonnés) ; l'événement porte
     * les PV réellement rendus.
     * Soins réduits sur certains terrains
     */
    private void healPlayer(double amount, double mult) {
        if(state.terrain().effect() == TerrainCard.Effect.NO_HEALING){

            /*return; // clopes autorisées ? voir Questions ouvertes*/
            amount *= COLONEL_HEAL_FACTOR;
            mult*= COLONEL_HEAL_FACTOR;
            effects.announce("Heal réduit de moitié par " + state.terrain().name());
        }

        Player player = state.player();
        double missing = Math.max(0, state.maxHp() - player.hp());
        double healed = Combatant.round(state.healingUncapped() ? amount : Math.min(amount, missing));
        player.heal(healed);
        bus.publish(new GameEvent.Healed(player, healed, mult));
    }

    private void dispatchToTrinkets(GameEvent event) {
        state.hero().ifPresent(hero -> HookDispatcher.dispatch(hero.hooks(), event, effects));
        List<Trinket> equipped = List.copyOf(state.trinkets());
        for (Trinket trinket : equipped) {
            HookDispatcher.dispatch(trinket.hooks(), event, effects);
        }
        for (GameState.ActiveSetTier active : List.copyOf(state.activeSetTiers())) {
            HookDispatcher.dispatch(active.tier().hooks(), event, effects);
        }
    }

    /** Notifie les paliers de set activés / désactivés depuis le dernier changement d'inventaire. */
    private void publishSetChanges() {
        for (GameState.SetChange change : state.drainSetChanges()) {
            var set = change.tier().set();
            var tier = change.tier().tier();
            bus.publish(change.activated()
                    ? new GameEvent.SetTierActivated(set.displayName(), set.tag(), tier.count(), tier.description())
                    : new GameEvent.SetTierDeactivated(set.displayName(), set.tag(), tier.count(), tier.description()));
        }
    }

    /** Actions offertes aux objets et trinkets. */
    private final class Effects implements EffectContext {

        @Override
        public GameState state() {
            return state;
        }

        @Override
        public Rng rng() {
            return rng;
        }

        @Override
        public void damageEnemy(double amount) {
            CombatEngine.this.damageEnemy(amount, 1.0, GameEvent.DamageDealt.Source.EFFECT);
        }

        @Override
        public void elementalDamage(double amount, Element element) {
            double mult = state.terrainRounding(ElementRules.spellMultiplier(element, state.currentEnemy().element())
                    * state.playerStats().get(StatType.damageMultiplier(element)));
            // Ni stat offensive ni critique (pas un sort), mais la DEF de l'adversaire compte.
            int dealt = CombatFormula.damage(amount, 0, mult, false, state.currentEnemy().stat(StatType.DEFENSE), 0);
            CombatEngine.this.damageEnemy(dealt, mult, GameEvent.DamageDealt.Source.EFFECT);
        }

        @Override
        public void damagePlayer(double amount) {
            Player player = state.player();
            boolean wasAlive = player.isAlive();
            amount = Combatant.round(amount);
            player.takeDamage(amount);
            bus.publish(new GameEvent.DamageDealt(player, amount, 1.0, GameEvent.DamageDealt.Source.EFFECT));
            if (wasAlive && !player.isAlive()) {
                bus.publish(new GameEvent.GameLost());
            }
        }

        @Override
        public void announce(String message) {
            bus.publish(new GameEvent.AbilityTriggered(message));
        }

        @Override
        public void familiarActs(GameEvent.FamiliarActed.Action action) {
            state.familiar().ifPresent(f -> bus.publish(new GameEvent.FamiliarActed(f, action)));
        }

        @Override
        public double potency(String trinketId) {
            return state.trinkets().stream().filter(t -> t.id().equals(trinketId)).findFirst()
                    .map(state::potency).orElse(1.0);
        }

        @Override
        public int addCigarettes(int count) {
            int added = Math.max(0, Math.min(count, state.cigaretteMax() - state.cigarettes()));
            if (added > 0) {
                state.setCigarettes(state.cigarettes() + added);
                bus.publish(new GameEvent.CigarettesChanged(state.cigarettes(), state.cigaretteMax()));
            }
            return added;
        }

        @Override
        public void addEffect(TemporaryEffect effect) {
            CombatEngine.this.addEffect(effect);
        }

        @Override
        public boolean removeStatus(StatusTarget target, StatusType type) {
            return CombatEngine.this.removeStatus(target, type);
        }

        @Override
        public int purgeNegativeStatuses() {
            int removed = 0;
            for (StatusType type : NEGATIVE_STATUSES) {
                removed += CombatEngine.this.removeStatus(StatusTarget.PLAYER, type) ? 1 : 0;
            }
            return removed;
        }

        @Override
        public void addCombatDamageBonus(double percent) {
            for (StatType stat : StatType.allDamageMultipliers()) {
                state.playerStats().add(cardistry.core.stats.StatModifier.percent(stat, percent, COMBAT_BONUS_SOURCE));
            }
        }

        @Override
        public void addCombatStat(StatusTarget target, StatType stat, double amount) {
            if (!state.hasEnemy()) {
                return;
            }
            // L'adversaire ne vit qu'un combat ; le bonus du joueur part à la fin du combat.
            state.stats(target).add(cardistry.core.stats.StatModifier.flat(stat, amount, COMBAT_BONUS_SOURCE));
            bus.publish(new GameEvent.CombatStatChanged(target, stat, amount));
        }

        @Override
        public boolean applyStatus(StatusTarget target, StatusType type, double probability) {
            return applyStatus(target, type, probability, 0, StatusExtras.NONE);
        }

        @Override
        public boolean applyStatus(StatusTarget target, StatusType type, double probability, int turns) {
            return applyStatus(target, type, probability, turns, StatusExtras.NONE);
        }

        @Override
        public boolean applyStatus(StatusTarget target, StatusType type, double probability, int turns,
                StatusExtras extras) {
            // Un statut que le joueur se pose lui-même (Petite taffe) ne passe pas par INT ni RES.
            return CombatEngine.this.tryStatus(target, type, probability, turns, extras, target.other(),
                    target == StatusTarget.ENEMY);
        }

        @Override
        public void addDrunkenness(int amount) {
            CombatEngine.this.addDrunkenness(amount);
        }

        @Override
        public void replaceCardInHand(int slot) {
            if (state.hand().get(slot) instanceof DeadCard) {
                return;
            }
            Card replacement = offerFromPool();
            Card removed = state.replaceInHand(slot, replacement);
            discard(removed);
            bus.publish(new GameEvent.CardReplaced(removed, replacement));
        }

        @Override
        public void elementalHeal(double amount, Element element) {
            double mult = state.healMultiplier(element);
            CombatEngine.this.healPlayer(amount * mult, mult);
        }

        @Override
        public void healPlayer(double amount) {
            double mult = state.playerStats().get(StatType.HEAL_EFFICIENCY);
            CombatEngine.this.healPlayer(amount * mult, mult);
        }

        @Override
        public void itemHeal(double amount) {
            double mult = state.playerStats().get(StatType.HEAL_EFFICIENCY);
            CombatEngine.this.healPlayer(CombatFormula.heal(amount, state.playerStats().get(StatType.MIND), mult), mult);
        }

        @Override
        public void itemHeal(double amount, Element element) {
            double mult = state.healMultiplier(element);
            CombatEngine.this.healPlayer(CombatFormula.heal(amount, state.playerStats().get(StatType.MIND), mult), mult);
        }

        @Override
        public void nullifyEnemy() {
            Enemy enemy = state.currentEnemy();
            if (!enemy.isAlive()) {
                return;
            }
            enemy.takeDamage(enemy.hp());
            bus.publish(new GameEvent.EnemyNulled(enemy));
        }

        @Override
        public void voidCardInHand(int slot) {
            Card dead = new DeadCard();
            Card removed = state.replaceInHand(slot, dead);
            discard(removed);
            bus.publish(new GameEvent.CardVoided(removed, dead));
        }

        @Override
        public void addCardToDeck(Card card) {
            CombatEngine.this.addCardToDeck(card);
        }

        @Override
        public void drawCards(int count) {
            for (int i = 0; i < count && state.handCount() < state.handSize(); i++) {
                drawCard();
            }
        }
    }

    // --- Inventaire (récompenses, tests) ---

    /** Équipe un trinket (ses modificateurs de stats s'appliquent immédiatement). */
    public void equip(Trinket trinket) {
        state.equipTrinket(trinket);
        publishSetChanges();
    }

    /** Note que Ctrl+Z a servi pendant ce combat (appelé par {@code run.Run} après l'annulation). */
    public void markUndoUsed() {
        state.setUndoUsedThisCombat(true);
    }

    /** Ajoute un objet à l'inventaire. */
    public void give(Item item) {
        state.addItem(item);
    }
}
