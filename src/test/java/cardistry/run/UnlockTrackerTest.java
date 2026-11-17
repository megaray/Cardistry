package cardistry.run;

import static cardistry.model.Element.FIRE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Action;
import cardistry.core.CombatEngine;
import cardistry.core.GameState;
import cardistry.core.Rng;
import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.StatType;
import cardistry.core.stats.Stats;
import cardistry.items.LootTable;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.CardPool;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Conditions de déblocage des secrets. */
class UnlockTrackerTest {

    private final List<GameEvent> events = new ArrayList<>();
    private final UnlockStore store = new UnlockStore.InMemory();
    private GameState state;
    private CombatEngine engine;
    private UnlockTracker tracker;

    /**
     * Adversaires donnés ; pool d'un seul sort (10 dégâts) ; {@code startingHand} cartes en main au
     * début de chaque combat.
     */
    private void setUp(int startingHand, Enemy... enemies) {
        EventBus bus = new EventBus();
        Stats stats = new Stats();
        stats.setBase(StatType.STARTING_HAND, startingHand);
        state = new GameState(new Player(100), List.of(enemies), new TerrainCard("EPFL", FIRE, cardistry.items.Rarity.COMMON), List.of(), stats);
        engine = new CombatEngine(state, new CardPool(List.of(new SpellCard("Boule de feu", FIRE, 10, cardistry.items.Rarity.COMMON))), new Rng(1),
                bus, new LootTable(List::of));
        tracker = new UnlockTracker(() -> state, store, bus);
        bus.subscribe(tracker);
        bus.subscribe(events::add);
        engine.start();
    }

    private void playFirstCard() {
        engine.startTurn();
        engine.submit(new Action.PlayCard(0));
    }

    // --- NullPointerException : gagner un combat avec 0 carte en main ---

    @Test
    void nullPointerExceptionGagnerUnCombatMainVide() {
        setUp(1, new Enemy("A", FIRE, 10, 1), new Enemy("B", FIRE, 100, 1));
        playFirstCard(); // dernière carte jouée, l'adversaire meurt
        assertEquals(Set.of(Secret.NULL_POINTER_EXCEPTION), store.load());
        assertEquals(List.of(Secret.NULL_POINTER_EXCEPTION), tracker.unlockedThisRun());
        assertTrue(events.contains(new GameEvent.SecretUnlocked("NullPointerException")));
    }

    @Test
    void nullPointerExceptionPasAvecUneCarteRestante() {
        setUp(2, new Enemy("A", FIRE, 10, 1), new Enemy("B", FIRE, 100, 1));
        playFirstCard();
        assertEquals(2, state.combatNumber(), "combat gagné");
        assertTrue(store.load().isEmpty());
        assertTrue(tracker.unlockedThisRun().isEmpty());
    }

    @Test
    void nullPointerExceptionAussiAuDernierCombat() {
        setUp(1, new Enemy("A", FIRE, 10, 1));
        playFirstCard();
        assertTrue(state.isWon());
        assertTrue(store.load().contains(Secret.NULL_POINTER_EXCEPTION));
    }

    // --- Ctrl+Z : perdre contre le dernier adversaire à ≤ 5 PV ---

    /** Le joueur meurt au premier tour contre le dernier adversaire, qui a {@code hp} PV. */
    private void loseAgainstLastEnemyWith(double hp) {
        setUp(1, new Enemy("Boss", FIRE, hp + 10, 1000));
        playFirstCard(); // 10 dégâts, puis l'attaque tue le joueur
        assertTrue(state.isLost());
        assertEquals(hp, state.currentEnemy().hp(), 1e-9);
    }

    @Test
    void ctrlZPerdreQuandLeBossAEncoreCinqPv() {
        loseAgainstLastEnemyWith(5);
        assertEquals(Set.of(Secret.CTRL_Z), store.load());
        assertEquals(List.of(Secret.CTRL_Z), tracker.unlockedThisRun());
    }

    @Test
    void ctrlZPasAvecSixPv() {
        loseAgainstLastEnemyWith(6);
        assertTrue(store.load().isEmpty());
    }

    @Test
    void ctrlZPasContreUnAdversaireQuiNEstPasLeDernier() {
        setUp(1, new Enemy("A", FIRE, 12, 1000), new Enemy("B", FIRE, 100, 1));
        playFirstCard();
        assertTrue(state.isLost());
        assertTrue(store.load().isEmpty());
    }

    // --- CLAUDE.md : gagner en ayant débloqué les deux autres ---

    @Test
    void claudeMdGagnerAvecLesDeuxAutres() {
        store.save(EnumSet.of(Secret.NULL_POINTER_EXCEPTION, Secret.CTRL_Z));
        setUp(2, new Enemy("A", FIRE, 10, 1));
        playFirstCard();
        assertTrue(state.isWon());
        assertEquals(EnumSet.allOf(Secret.class), store.load());
        assertEquals(List.of(Secret.CLAUDE_MD), tracker.unlockedThisRun());
    }

    @Test
    void claudeMdPasAvecUnSeulDesDeux() {
        store.save(EnumSet.of(Secret.CTRL_Z));
        setUp(2, new Enemy("A", FIRE, 10, 1));
        playFirstCard();
        assertTrue(state.isWon());
        assertEquals(Set.of(Secret.CTRL_Z), store.load());
    }

    @Test
    void claudeMdAvecNullPointerExceptionDebloqueDansLaMemePartie() {
        store.save(EnumSet.of(Secret.CTRL_Z));
        setUp(1, new Enemy("A", FIRE, 10, 1)); // victoire avec la main vide
        playFirstCard();
        assertEquals(List.of(Secret.NULL_POINTER_EXCEPTION, Secret.CLAUDE_MD), tracker.unlockedThisRun());
        assertEquals(EnumSet.allOf(Secret.class), store.load());
    }

    @Test
    void claudeMdPasEnPerdant() {
        store.save(EnumSet.of(Secret.NULL_POINTER_EXCEPTION, Secret.CTRL_Z));
        setUp(1, new Enemy("Boss", FIRE, 100, 1000));
        playFirstCard();
        assertTrue(state.isLost());
        assertEquals(EnumSet.of(Secret.NULL_POINTER_EXCEPTION, Secret.CTRL_Z), store.load());
    }

    @Test
    void unSecretDejaDebloqueNEstPasReannonce() {
        store.save(EnumSet.of(Secret.NULL_POINTER_EXCEPTION));
        setUp(1, new Enemy("A", FIRE, 10, 1), new Enemy("B", FIRE, 100, 1));
        playFirstCard();
        assertTrue(tracker.unlockedThisRun().isEmpty());
        assertTrue(events.stream().noneMatch(e -> e instanceof GameEvent.SecretUnlocked));
    }

    // --- Sur la carte : le « dernier adversaire » est le boss du niveau 3 ---

    /** Partie sur la carte, sort surpuissant ; joue jusqu'au combat contre le boss du niveau {@code level}. */
    private cardistry.run.Run runToBoss(int level) {
        EventBus bus = new EventBus();
        cardistry.run.Run run = new cardistry.run.Run(2, Set.of(), bus, () -> GameState.newGame(java.util.Collections.nCopies(12, new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))),
                () -> new CardPool(List.of(new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))));
        tracker = new UnlockTracker(run::state, store, bus);
        bus.subscribe(tracker);
        run.start();
        int guard = 0;
        while (!(run.phase() == cardistry.run.Run.Phase.COMBAT && run.state().isBossFight()
                && run.state().level() == level) && guard++ < 10_000) {
            switch (run.phase()) {
                case MAP -> run.chooseNode(0);
                case COMBAT -> {
                    if (run.startTurn()) {
                        run.submit(new Action.PlayCard(0));
                    }
                }
                case REWARD -> run.resolveReward(new cardistry.core.RewardChoice.Skip());
                case CARD_REWARD -> run.resolveCardReward(-1);
                case SHOP -> run.shop(new cardistry.core.shop.ShopAction.Leave());
                case EVENT -> run.chooseStory(run.story().orElseThrow().choices().size() - 1);
                case REST -> run.rest(RestOption.HEAL);
                case STAT_POINTS -> run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH);
                case ALTAR -> run.leaveAltar();
                case OVER -> throw new AssertionError("partie finie trop tôt");
            }
        }
        return run;
    }

    /** Le boss garde 3 PV, le joueur 1 : il pioche et meurt sous l'attaque du boss. */
    private static void loseAgainstWeakBoss(cardistry.run.Run run) {
        run.state().currentEnemy().takeDamage(run.state().currentEnemy().hp() - 3);
        run.state().player().takeDamage(run.state().player().hp() - 1);
        run.startTurn();
        run.submit(new Action.DrawCard());
        assertTrue(run.state().isLost());
    }

    @Test
    void ctrlZSeulementContreLeBossDuNiveau3() {
        cardistry.run.Run first = runToBoss(1);
        assertTrue(!first.state().isFinalCombat());
        loseAgainstWeakBoss(first);
        assertTrue(store.load().isEmpty(), "le boss du niveau 1 n'est pas le dernier adversaire");

        cardistry.run.Run last = runToBoss(3);
        assertTrue(last.state().isFinalCombat());
        loseAgainstWeakBoss(last);
        assertEquals(EnumSet.of(Secret.CTRL_Z), store.load());
    }

    @Test
    void bossDeChaqueNiveauImmuniseElitesNon() {
        cardistry.run.Run run = runToBoss(1);
        assertTrue(run.state().isBossFight(), "boss du niveau 1 : pas de null");
        assertTrue(!run.state().isFinalCombat());
        setUp(5, cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.VAFI, 1),
                new Enemy("Fin", FIRE, 10, 1));
        assertTrue(!state.isBossFight(), "une élite peut devenir null");
    }
}
