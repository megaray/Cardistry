package cardistry.run;

import cardistry.core.Action;
import cardistry.core.ActionResult;
import cardistry.core.CombatEngine;
import cardistry.core.GameState;
import cardistry.core.PlayerDecisions;
import cardistry.core.RewardChoice;
import cardistry.core.Rng;
import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.items.Collectible;
import cardistry.items.ItemRegistry;
import cardistry.items.LootTable;
import cardistry.model.Enemy;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.enemy.EnemyRegistry;
import cardistry.run.map.MapGenerator;
import cardistry.run.map.MapNode;
import cardistry.run.map.NodeType;
import cardistry.run.map.RunMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Une partie : la carte des 3 niveaux, le moteur de combat, et ce qui dépasse un moteur seul.
 * <ul>
 *   <li>la <b>carte</b> : à chaque niveau, une carte générée par un sous-flux de la seed ; le
 *       joueur choisit un nœud ({@link #chooseNode}), le combat se joue, puis il choisit le suivant.
 *       Le boss du niveau 3 termine la partie ;</li>
 *   <li>les <b>codes</b> : secrets équipés dès le départ, pour cette partie seulement ;</li>
 *   <li>les <b>secrets débloqués</b>, qui entrent dans le pool des récompenses ;</li>
 *   <li>l'<b>annulation de tour</b> (trinket Ctrl+Z) : chaque appel et chaque décision du joueur
 *       sont enregistrés ; annuler = reconstruire une partie neuve avec la même seed et rejouer
 *       l'historique jusqu'au début du tour précédent. Tout l'aléatoire passant par des {@link Rng}
 *       dérivés de la seed, le rejeu redonne exactement le même état.</li>
 * </ul>
 * Avec un état à adversaires fixés (tests), il n'y a pas de carte : le moteur enchaîne les combats.
 * Les événements sont relayés sur le bus {@code front} (interface, déblocages), sauf pendant un rejeu.
 */
public final class Run {

    /** Ce que la partie attend du joueur. */
    public enum Phase {
        /** Choisir le prochain nœud de la carte. */
        MAP,
        /** Jouer un tour de combat. */
        COMBAT,
        /** Choisir une récompense. */
        REWARD,
        /** Ajouter une carte au deck (ou passer), après la récompense d'objet. */
        CARD_REWARD,
        /** Sieste : choisir quoi faire ({@link RestOption}). */
        REST,
        /** Shop : acheter, retirer une carte, reroll, ou partir. */
        SHOP,
        /** Événement : choisir une option. */
        EVENT,
        /** Après un boss : placer un point de stats (un à la fois). */
        STAT_POINTS,
        /** autel sacrificielle */
        ALTAR,
        /** Partie terminée. */
        OVER
    }

    /** Un appel (ou une réponse du joueur pendant un appel), pour le rejeu. */
    private sealed interface Step {
    }

    private record StartTurn() implements Step {
    }

    private record Submit(Action action) implements Step {
    }

    private record Reward(RewardChoice choice) implements Step {
    }

    private record Decision(int index) implements Step {
    }

    private record ChooseNode(int index) implements Step {
    }

    private record Rest(RestOption option, int cardIndex) implements Step {
    }

    private record CardReward(int index) implements Step {
    }

    private record ShopStep(cardistry.core.shop.ShopAction action) implements Step {
    }

    private record StoryStep(int choice) implements Step {
    }

    private record StatPoint(cardistry.core.stats.StatType stat) implements Step {
    }

    private record Sacrifice(cardistry.core.stats.StatType stat) implements Step{
    }

    private record LeaveAltar() implements Step{
    }

    /** Triche de la console de développement ({@link cardistry.core.DevCheats}). */
    private record Cheat(java.util.function.Consumer<GameState> change) implements Step {
    }

    /** Début d'un tour où le joueur agit : {@code historySize} étapes le précèdent. */
    private record TurnMark(int historySize, int combat, int turn) {
    }


    private final long seed;
    private final Set<String> unlockedIds;
    private final EventBus front;
    private final Supplier<GameState> newState;
    private final Supplier<CardPool> newPool;
    private final List<Secret> codes = new ArrayList<>();
    private final List<Step> history = new ArrayList<>();
    private final List<TurnMark> marks = new ArrayList<>();
    private PlayerDecisions decisions = PlayerDecisions.FIRST;
    /** Personnage joué ; {@code null} = aucun. */
    private cardistry.hero.Hero hero;
    private Session session;

    /** Partie standard. */
    public Run(long seed, Set<Secret> unlocked, EventBus front) {
        this(seed, unlocked, front, null, CardPool::new);
    }

    /** Partie sur un état et un pool donnés (tests) ; {@code newState} doit créer un état neuf à chaque appel. */
    public Run(long seed, Set<Secret> unlocked, EventBus front, Supplier<GameState> newState, Supplier<CardPool> newPool) {
        this.seed = seed;
        this.unlockedIds = unlocked.stream().map(Secret::trinketId).collect(Collectors.toUnmodifiableSet());
        this.front = front;
        this.newState = newState;
        this.newPool = newPool;
    }

    public long seed() {
        return seed;
    }

    /** Code entré avant le début : le secret est équipé pour cette partie seulement. */
    public void addCode(Secret secret) {
        if (session != null) {
            throw new IllegalStateException("La partie a déjà commencé");
        }
        if (!codes.contains(secret)) {
            codes.add(secret);
        }
    }

    /** Personnage joué (avant le début) ; sans effet sur une partie à état fourni (tests). */
    public void setHero(cardistry.hero.Hero hero) {
        if (session != null) {
            throw new IllegalStateException("La partie a déjà commencé");
        }
        this.hero = hero;
    }

    public List<Secret> codes() {
        return List.copyOf(codes);
    }

    /** Secrets débloqués au début de la partie, qui sont dans le pool des récompenses. */
    public Set<Secret> unlockedSecrets() {
        java.util.EnumSet<Secret> secrets = java.util.EnumSet.noneOf(Secret.class);
        for (Secret secret : Secret.values()) {
            if (unlockedIds.contains(secret.trinketId())) {
                secrets.add(secret);
            }
        }
        return secrets;
    }

    /** Qui répond aux décisions demandées en cours d'action (l'interface, ou un test). */
    public void setDecisions(PlayerDecisions decisions) {
        this.decisions = decisions;
    }

    /** Lance la partie (codes équipés, premier niveau ou premier combat). */
    public void start() {
        if (session != null) {
            throw new IllegalStateException("La partie a déjà commencé");
        }
        EventBus bus = new EventBus();
        bus.subscribe(front::publish);
        session = new Session(bus);
        session.engine.setDecisions(new Recording());
        session.start();
    }

    // --- État ---

    public GameState state() {
        return session.engine.state();
    }

    public boolean isOver() {
        return session.engine.isOver();
    }

    /** Ce que la partie attend maintenant. */
    public Phase phase() {
        return session.phase();
    }

    /** Carte du niveau en cours ; vide sans carte (adversaires fixés). */
    public Optional<RunMap> map() {
        return Optional.ofNullable(session.map);
    }

    /** Dernier nœud visité du niveau en cours ({@code null} au départ du niveau). */
    public MapNode position() {
        return session.position;
    }

    /** Nœuds visités dans le niveau en cours, dans l'ordre. */
    public List<MapNode> visited() {
        return List.copyOf(session.visited);
    }

    /** Nœuds où le joueur peut aller maintenant (phase {@link Phase#MAP}). */
    public List<MapNode> choices() {
        return session.map == null ? List.of() : session.map.choicesAfter(session.position);
    }

    // --- Actions, avec enregistrement ---

    /** Entre dans le nœud {@code index} de {@link #choices()}. */
    public void chooseNode(int index) {
        if (phase() != Phase.MAP) {
            throw new IllegalStateException("Pas de choix de nœud maintenant");
        }
        if (index < 0 || index >= choices().size()) {
            throw new IllegalArgumentException("Nœud inexistant : " + index);
        }
        history.add(new ChooseNode(index));
        session.chooseNode(index);
    }

    /** Choix de la Sieste possibles maintenant (retirer une carte : seulement avec un deck). */
    public List<RestOption> restOptions() {
        return session.restOptions();
    }

    /** Sieste : applique {@code option} (pas « Faire le tri ») puis revient à la carte. */
    public void rest(RestOption option) {
        rest(option, -1);
    }

    /**
     * Sieste : applique {@code option} puis revient à la carte. « Faire le tri » retire la carte
     * {@code cardIndex} du deck.
     */
    public void rest(RestOption option, int cardIndex) {
        if (phase() != Phase.REST) {
            throw new IllegalStateException("Pas de sieste maintenant");
        }
        if (!restOptions().contains(option)) {
            throw new IllegalArgumentException("Choix indisponible : " + option);
        }
        if (option == RestOption.REMOVE_CARD && (cardIndex < 0 || cardIndex >= state().deck().size())) {
            throw new IllegalArgumentException("Carte inexistante : " + cardIndex);
        }
        history.add(new Rest(option, cardIndex));
        session.rest(option, cardIndex);
    }

    /** Événement en cours (phase {@link Phase#EVENT}). */
    public Optional<cardistry.story.Story> story() {
        return session.engine.story();
    }

    /** Résultat du dernier choix d'événement. */
    public Optional<GameEvent.StoryResolved> lastStoryResult() {
        return session.engine.lastStoryResult();
    }

    /** Choix {@code index} de l'événement en cours ; {@code false} s'il n'est pas disponible. */
    public boolean chooseStory(int index) {
        if (phase() != Phase.EVENT) {
            throw new IllegalStateException("Pas d'événement maintenant");
        }
        history.add(new StoryStep(index));
        return session.engine.chooseStory(index);
    }

    /** Shop ouvert (phase {@link Phase#SHOP}). */
    public Optional<cardistry.core.Shop> shop() {
        return session.engine.shop();
    }

    /** Prix du prochain retrait de carte au shop. */
    public int cardRemovalPrice() {
        return session.engine.cardRemovalPrice();
    }

    /**
     * Action au shop ; {@code false} si elle est refusée (pas assez de Choomies, vendu…). Partir
     * ramène à la carte.
     */
    public boolean shop(cardistry.core.shop.ShopAction action) {
        if (phase() != Phase.SHOP) {
            throw new IllegalStateException("Pas de shop maintenant");
        }
        history.add(new ShopStep(action));
        return session.engine.shopAction(action);
    }

    /** Points de stats à placer ({@link Phase#STAT_POINTS}). */
    public int statPoints() {
        return state().statPoints();
    }

    /**
     * Place un point de stats dans {@code stat} (une des 6 stats de personnage).
     *
     * @return {@code false} si le choix est refusé (le point reste à placer)
     */
    public boolean spendStatPoint(cardistry.core.stats.StatType stat) {
        if (phase() != Phase.STAT_POINTS) {
            throw new IllegalStateException("Pas de point de stats à placer maintenant");
        }
        boolean spent = session.engine.spendStatPoint(stat);
        if (spent) {
            history.add(new StatPoint(stat));
        }
        return spent;
    }

    public boolean sacrifice(cardistry.core.stats.StatType stat){
        if (phase() != Phase.ALTAR){
            throw new IllegalStateException("Pas d'autel maintenant.");
        }
        boolean done = session.engine.sacrifice(stat);
        if(done){
            history.add(new Sacrifice(stat));
        }
        return done;
    }

    public void leaveAltar(){
        if (phase() != Phase.ALTAR){
            throw new IllegalStateException("Pas d'autel maintenant.");
        }
        session.atAltar = false;
        history.add(new LeaveAltar());
    }

    public boolean hasPendingCardReward() {
        return session.engine.hasPendingCardReward();
    }

    public List<Card> pendingCardReward() {
        return session.engine.pendingCardReward();
    }

    /** Ajoute la carte {@code index} de {@link #pendingCardReward()} au deck, ou passe ({@code index} &lt; 0). */
    public void resolveCardReward(int index) {
        if (phase() != Phase.CARD_REWARD) {
            throw new IllegalStateException("Pas de carte à choisir maintenant");
        }
        if (index >= pendingCardReward().size()) {
            throw new IllegalArgumentException("Carte inexistante : " + index);
        }
        history.add(new CardReward(index));
        session.engine.resolveCardReward(index);
        session.settle();
    }

    /** Voir {@link CombatEngine#startTurn()}. */
    public boolean startTurn() {
        history.add(new StartTurn());
        boolean playerActs = session.engine.startTurn();
        if (playerActs) {
            marks.add(new TurnMark(history.size(), state().combatNumber(), state().turn()));
        }
        session.settle();
        return playerActs;
    }

    /**
     * Voir {@link CombatEngine#submit(Action)}. {@link Action.Undo} est traitée ici : si
     * l'annulation est possible, la partie revient au début du tour précédent et le résultat est
     * {@link ActionResult#FREE_ACTION} (le joueur rejoue ce tour) ; sinon {@link ActionResult#REJECTED}.
     */
    public ActionResult submit(Action action) {
        if (action instanceof Action.Undo) {
            if (!canUndo()) {
                front.publish(new GameEvent.ActionRejected(GameEvent.ActionRejected.Reason.UNDO_UNAVAILABLE));
                return ActionResult.REJECTED;
            }
            undo();
            return ActionResult.FREE_ACTION;
        }
        history.add(new Submit(action));
        ActionResult result = session.engine.submit(action);
        session.settle();
        return result;
    }

    public boolean hasPendingReward() {
        return session.engine.hasPendingReward();
    }

    public List<Collectible> pendingReward() {
        return session.engine.pendingReward();
    }

    /** Voir {@link CombatEngine#resolveReward(RewardChoice)}. */
    public boolean resolveReward(RewardChoice choice) {
        history.add(new Reward(choice));
        boolean resolved = session.engine.resolveReward(choice);
        session.settle();
        return resolved;
    }

    // --- Ctrl+Z ---

    /**
     * Applique une triche de la console de développement et la garde dans l'historique : un rejeu
     * (Ctrl+Z) la refait au même moment, et l'annuler avec le tour qui la suit l'efface.
     *
     * @throws IllegalStateException si la partie n'a pas commencé ou si la triche est impossible ici
     */
    public void cheat(java.util.function.Consumer<GameState> change) {
        if (session == null) {
            throw new IllegalStateException("Partie pas encore commencée");
        }
        change.accept(state());
        history.add(new Cheat(change));
    }

    /**
     * {@code true} si le joueur peut annuler son dernier tour maintenant : Ctrl+Z équipé, pas
     * encore utilisé pendant ce combat, et un tour précédent dans ce même combat.
     */
    public boolean canUndo() {
        return session != null && phase() == Phase.COMBAT
                && state().hasTrinket(ItemRegistry.CTRL_Z)
                && !state().undoUsedThisCombat()
                && previousTurn().isPresent();
    }

    /** Début du tour précédent du combat en cours (le dernier repère est le tour en cours). */
    private Optional<TurnMark> previousTurn() {
        if (marks.size() < 2) {
            return Optional.empty();
        }
        TurnMark current = marks.get(marks.size() - 1);
        TurnMark previous = marks.get(marks.size() - 2);
        boolean sameCombat = current.combat() == state().combatNumber() && previous.combat() == current.combat();
        return sameCombat ? Optional.of(previous) : Optional.empty();
    }

    private void undo() {
        TurnMark target = previousTurn().orElseThrow();
        history.subList(target.historySize(), history.size()).clear();
        marks.subList(marks.indexOf(target) + 1, marks.size()).clear();
        session = replay();
        session.engine.markUndoUsed();
        front.publish(new GameEvent.TurnUndone(target.turn()));
    }

    /** Partie neuve qui a rejoué tout l'historique sans rien relayer, puis branchée sur {@code front}. */
    private Session replay() {
        EventBus bus = new EventBus();
        Session replayed = new Session(bus);
        Iterator<Step> steps = history.iterator();
        replayed.engine.setDecisions(new Replaying(steps));
        replayed.start();
        while (steps.hasNext()) {
            switch (steps.next()) {
                case StartTurn s -> {
                    replayed.engine.startTurn();
                    replayed.settle();
                }
                case Submit s -> {
                    replayed.engine.submit(s.action());
                    replayed.settle();
                }
                case Reward r -> {
                    replayed.engine.resolveReward(r.choice());
                    replayed.settle();
                }
                case ChooseNode c -> replayed.chooseNode(c.index());
                case Rest r -> replayed.rest(r.option(), r.cardIndex());
                case ShopStep s -> replayed.engine.shopAction(s.action());
                case StoryStep s -> replayed.engine.chooseStory(s.choice());
                case StatPoint s -> replayed.engine.spendStatPoint(s.stat());
                case Sacrifice s-> replayed.engine.sacrifice(s.stat());
                case LeaveAltar s-> replayed.atAltar=false;
                case Cheat c -> c.change().accept(replayed.engine.state());
                case CardReward c -> {
                    replayed.engine.resolveCardReward(c.index());
                    replayed.settle();
                }
                case Decision d -> throw new IllegalStateException("Décision hors d'une action");
            }
        }
        bus.subscribe(front::publish);
        replayed.engine.setDecisions(new Recording());
        return replayed;
    }

    /** Le moteur et la progression sur la carte : tout ce qu'un rejeu reconstruit. */
    private final class Session {

        final CombatEngine engine;
        final boolean mapped;
        RunMap map;
        MapNode position;
        final List<MapNode> visited = new ArrayList<>();
        Rng encounters;
        String previousCommon;
        String previousElite;
        boolean resting;
        boolean atAltar;

        Session(EventBus bus) {
            GameState state = newState != null ? newState.get() : GameState.newGame(hero);
            engine = new CombatEngine(state, newPool.get(), new Rng(seed), bus, new LootTable(unlockedIds));
            for (Secret code : codes) {
                engine.equip(ItemRegistry.trinket(code.trinketId()));
            }
            mapped = state.isOpenEnded();
        }

        void start() {
            engine.start();
            if (mapped) {
                startLevel(1);
            }
        }

        Phase phase() {
            if (engine.isOver()) {
                return Phase.OVER;
            }
            if (engine.hasPendingReward()) {
                return Phase.REWARD;
            }
            if (engine.hasPendingCardReward()) {
                return Phase.CARD_REWARD;
            }
            if (engine.combatActive() || !mapped) {
                return Phase.COMBAT;
            }
            if (engine.state().statPoints() > 0) {
                return Phase.STAT_POINTS;
            }
            if (engine.shop().isPresent()) {
                return Phase.SHOP;
            }
            if (engine.story().isPresent()) {
                return Phase.EVENT;
            }
            if (atAltar){
                return Phase.ALTAR;
            }
            return resting ? Phase.REST : Phase.MAP;
        }

        void startLevel(int level) {
            map = MapGenerator.generate(new Rng(seed).fork("carte-" + level), level);
            position = null;
            visited.clear();
            encounters = new Rng(seed).fork("rencontres-" + level);
            engine.startLevel(level);
        }

        void chooseNode(int index) {
            MapNode node = map.choicesAfter(position).get(index);
            position = node;
            visited.add(node);
            engine.enterNode(node.floor(), node.type().displayName());
            int level = map.level();
            switch (node.type()) {
                case COMBAT -> {
                    String id = EnemyRegistry.pickCommon(encounters, previousCommon);
                    previousCommon = id;
                    engine.startCombat(EnemyRegistry.create(id, level), false);
                }
                case ELITE -> {
                    String id = EnemyRegistry.pickElite(encounters, previousElite);
                    previousElite = id;
                    engine.startCombat(EnemyRegistry.create(id, level), false);
                }
                case BOSS -> {
                    Enemy boss = EnemyRegistry.create(EnemyRegistry.bossId(level), level);
                    engine.startCombat(boss, level == EnemyRegistry.LEVELS);
                }
                case REST -> resting = true;
                case SHOP -> engine.openShop();
                case EVENT -> engine.startStory(nextStory());
                case ALTAR -> atAltar=true;
            }
            settle();
        }

        /** Événements déjà vus dans la partie (on n'en revoit un que quand tous sont passés). */
        final List<String> seenStories = new ArrayList<>();
        final Rng stories = new Rng(seed).fork("evenements");

        cardistry.story.Story nextStory() {
            List<cardistry.story.Story> all = cardistry.story.StoryRegistry.all();
            List<cardistry.story.Story> fresh = all.stream().filter(s -> !seenStories.contains(s.id())).toList();
            if (fresh.isEmpty()) {
                seenStories.clear();
                fresh = all;
            }
            cardistry.story.Story story = stories.pick(fresh);
            seenStories.add(story.id());
            return story;
        }

        List<RestOption> restOptions() {
            return engine.canRemoveCard()
                    ? List.of(RestOption.HEAL, RestOption.CIGARETTES, RestOption.REMOVE_CARD)
                    : List.of(RestOption.HEAL, RestOption.CIGARETTES);
        }

        void rest(RestOption option, int cardIndex) {
            switch (option) {
                case HEAL -> engine.restHeal(option.displayName());
                case CIGARETTES -> engine.restCigarettes(option.displayName());
                case REMOVE_CARD -> {
                    engine.restRemoveCard(option.displayName(), cardIndex);
                }
            }
            resting = false;
        }

        /**
         * Après chaque étape : le boss d'un niveau battu (récompense prise) rend la moitié des PV
         * manquants et ouvre le niveau suivant.
         */
        void settle() {
            if (!mapped || phase() != Phase.MAP || position == null || position.type() != NodeType.BOSS) {
                return;
            }
            engine.recoverAfterBoss();
            startLevel(map.level() + 1);
        }
    }

    /** Transmet les décisions du joueur et les enregistre. */
    private final class Recording implements PlayerDecisions {

        @Override
        public int chooseDrawnCard(GameState state, List<Card> options) {
            return record(decisions.chooseDrawnCard(state, options));
        }

        @Override
        public int chooseTrinketToDiscard(GameState state) {
            return record(decisions.chooseTrinketToDiscard(state));
        }

        private int record(int index) {
            history.add(new Decision(index));
            return index;
        }
    }

    /** Redonne les décisions enregistrées, dans l'ordre. */
    private static final class Replaying implements PlayerDecisions {

        private final Iterator<Step> steps;

        Replaying(Iterator<Step> steps) {
            this.steps = steps;
        }

        @Override
        public int chooseDrawnCard(GameState state, List<Card> options) {
            return next();
        }

        @Override
        public int chooseTrinketToDiscard(GameState state) {
            return next();
        }

        private int next() {
            if (steps.hasNext() && steps.next() instanceof Decision d) {
                return d.index();
            }
            throw new IllegalStateException("Rejeu désynchronisé : décision attendue");
        }
    }
}
