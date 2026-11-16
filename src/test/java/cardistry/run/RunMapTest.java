package cardistry.run;

import static cardistry.model.Element.FIRE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Action;
import cardistry.core.ActionResult;
import cardistry.core.GameState;
import cardistry.core.RewardChoice;
import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.model.card.CardPool;
import cardistry.model.card.SpellCard;
import cardistry.model.enemy.EnemyRank;
import cardistry.run.map.MapNode;
import cardistry.run.map.NodeType;
import cardistry.run.map.RunMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Une partie sur la carte : 3 niveaux, nœuds, boss, déterminisme. */
class RunMapTest {

    private final List<GameEvent> events = new ArrayList<>();

    /** Partie où chaque sort tue (pool d'une seule carte surpuissante). */
    private Run strongRun(long seed) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        Run run = new Run(seed, Set.of(), bus, () -> GameState.newGame(java.util.Collections.nCopies(12, new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))),
                () -> new CardPool(List.of(new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))));
        run.start();
        return run;
    }

    /** Joue jusqu'à la fin en prenant toujours le nœud {@code pick} (modulo les choix) ; renvoie les nœuds. */
    private static List<MapNode> playToEnd(Run run, int pick) {
        List<MapNode> path = new ArrayList<>();
        int guard = 0;
        while (!run.isOver() && guard++ < 10_000) {
            switch (run.phase()) {
                case MAP -> {
                    int index = pick % run.choices().size();
                    path.add(run.choices().get(index));
                    run.chooseNode(index);
                }
                case COMBAT -> {
                    if (run.startTurn()) {
                        run.submit(new Action.PlayCard(0));
                    }
                }
                case REWARD -> run.resolveReward(new RewardChoice.Skip());
                case CARD_REWARD -> run.resolveCardReward(-1);
                case SHOP -> run.shop(new cardistry.core.shop.ShopAction.Leave());
                case EVENT -> run.chooseStory(run.story().orElseThrow().choices().size() - 1);
                case REST -> run.rest(RestOption.HEAL);
                case STAT_POINTS -> run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH);
                case ALTAR -> run.leaveAltar();
                case OVER -> {
                }
            }
        }
        return path;
    }

    /** Joue (premier choix partout) jusqu'à ce que {@code stop} soit vrai. */
    private static void playUntil(Run run, java.util.function.Predicate<Run> stop) {
        int guard = 0;
        while (!stop.test(run) && !run.isOver() && guard++ < 10_000) {
            // Le trajet n'est pas ce qu'on teste : PV remis au max à chaque étape (le Rebond du Peneu
            // renvoie une part des 10 000 dégâts du Météore) ; chaque test pose ses dégâts à l'arrivée.
            run.state().player().heal(run.state().maxHp() - run.state().player().hp());
            switch (run.phase()) {
                case MAP -> run.chooseNode(0);
                case COMBAT -> {
                    if (run.startTurn()) {
                        run.submit(new Action.PlayCard(0));
                    }
                }
                case REWARD -> run.resolveReward(new RewardChoice.Skip());
                case CARD_REWARD -> run.resolveCardReward(-1);
                case SHOP -> run.shop(new cardistry.core.shop.ShopAction.Leave());
                case EVENT -> run.chooseStory(run.story().orElseThrow().choices().size() - 1);
                case REST -> run.rest(RestOption.HEAL);
                case STAT_POINTS -> run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH);
                case ALTAR -> run.leaveAltar();
                case OVER -> {
                }
            }
        }
    }

    // --- Sieste ---

    @Test
    void siesteAvantLeBoss() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.REST && r.state().floor() == RunMap.REST_FLOOR);
        assertEquals(RunMap.REST_FLOOR, run.state().floor());
        assertEquals(3, run.restOptions().size());
        assertThrows(IllegalStateException.class, () -> run.chooseNode(0));
        run.state().player().takeDamage(50);
        run.rest(RestOption.HEAL);
        assertEquals(80, run.state().player().hp(), 1e-9); // 50 + 30 % de 100
        assertEquals(Run.Phase.MAP, run.phase());
        assertEquals(NodeType.BOSS, run.choices().get(0).type());
        assertTrue(events.contains(new GameEvent.RestTaken("Dormir")));
    }

    @Test
    void siesteSoinPlafonne() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.REST && r.state().floor() == RunMap.REST_FLOOR);
        run.state().player().takeDamage(10);
        run.rest(RestOption.HEAL);
        assertEquals(100, run.state().player().hp(), 1e-9);
    }

    @Test
    void siesteEstUnSoinBoosteParLesSoinsEtRoss() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.REST && r.state().floor() == RunMap.REST_FLOOR);
        run.state().playerStats().add(cardistry.core.stats.StatModifier.percent(
                cardistry.core.stats.StatType.HEAL_EFFICIENCY, 0.5, "test"));
        run.state().player().takeDamage(80); // 20/100
        run.rest(RestOption.HEAL);
        assertEquals(65, run.state().player().hp(), 1e-9); // 20 + 30 × 1.5
    }

    @Test
    void siesteAvecRossDepasseLeMax() {
        EventBus bus = new EventBus();
        Run run = new Run(2, Set.of(), bus,
                () -> GameState.newGame(java.util.Collections.nCopies(12, new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))),
                () -> new CardPool(List.of(new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))));
        run.start();
        playUntil(run, r -> r.phase() == Run.Phase.REST && r.state().floor() == RunMap.REST_FLOOR);
        double before = run.state().player().hp();
        run.state().playerStats().add(cardistry.core.stats.StatModifier.flat(
                cardistry.core.stats.StatType.UNCAPPED_HEALING, 1, "test"));
        run.rest(RestOption.HEAL);
        assertEquals(before + 30, run.state().player().hp(), 1e-9);
        assertTrue(run.state().player().hp() > 100);
    }

    @Test
    void recuperationApresBossSansBonus() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.COMBAT && r.state().isBossFight());
        run.state().playerStats().add(cardistry.core.stats.StatModifier.percent(
                cardistry.core.stats.StatType.HEAL_EFFICIENCY, 1.0, "test"));
        run.state().player().takeDamage(60);
        run.startTurn();
        run.submit(new Action.PlayCard(0));
        run.resolveReward(new RewardChoice.Skip());
        run.resolveCardReward(-1);
        assertEquals(70 + 25, run.state().player().hp(), 1e-9); // récupération sans bonus de soin, puis +25
    }

    @Test
    void siesteClopes() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.REST && r.state().floor() == RunMap.REST_FLOOR);
        int before = run.state().cigarettes();
        run.rest(RestOption.CIGARETTES);
        assertEquals(before + 3, run.state().cigarettes());
    }

    @Test
    void faireLeTriRetireUneCarteDuDeck() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.REST && r.state().floor() == RunMap.REST_FLOOR);
        assertEquals(List.of(RestOption.HEAL, RestOption.CIGARETTES, RestOption.REMOVE_CARD), run.restOptions());
        int before = run.state().deck().size();
        assertThrows(IllegalArgumentException.class, () -> run.rest(RestOption.REMOVE_CARD));
        run.rest(RestOption.REMOVE_CARD, 0);
        assertEquals(before - 1, run.state().deck().size());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.CardRemovedFromDeck));
    }

    // --- Après un boss ---

    @Test
    void apresUnBossLaMoitieDesPvManquants() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.COMBAT && r.state().isBossFight());
        run.state().player().takeDamage(60); // 40/100
        assertTrue(run.startTurn());
        run.submit(new Action.PlayCard(0));
        if (run.phase() == Run.Phase.REWARD) {
            run.resolveReward(new RewardChoice.Skip());
        }
        run.resolveCardReward(-1);
        assertEquals(2, run.state().level());
        assertEquals(70 + 25, run.state().player().hp(), 1e-9); // 40 + 60 / 2, puis +25 PV max et actuels
        assertEquals(125, run.state().maxHp(), 1e-9);
    }

    @Test
    void apresUnBossDeuxPointsDeStatsARepartir() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.COMBAT && r.state().isBossFight());
        assertTrue(run.startTurn());
        run.submit(new Action.PlayCard(0));
        if (run.phase() == Run.Phase.REWARD) {
            run.resolveReward(new RewardChoice.Skip());
        }
        run.resolveCardReward(-1);
        assertEquals(Run.Phase.STAT_POINTS, run.phase());
        assertEquals(2, run.statPoints());
        assertTrue(events.contains(new GameEvent.BossBonus(25, 2)));
        assertThrows(IllegalStateException.class, () -> run.chooseNode(0));
        double strength = run.state().playerStats().get(cardistry.core.stats.StatType.STRENGTH);
        assertFalse(run.spendStatPoint(cardistry.core.stats.StatType.MAX_HP), "seulement les 6 stats");
        assertTrue(run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH));
        assertEquals(Run.Phase.STAT_POINTS, run.phase());
        assertTrue(run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH));
        assertEquals(strength + 2, run.state().playerStats().get(cardistry.core.stats.StatType.STRENGTH), 1e-9);
        assertEquals(Run.Phase.MAP, run.phase());
        assertEquals(2, run.state().level());
    }

    @Test
    void lesBonusDeBossDurentToutLaPartie() {
        Run run = strongRun(2);
        playUntil(run, r -> r.state().level() == 3 && r.phase() == Run.Phase.MAP);
        assertEquals(150, run.state().maxHp(), 1e-9);
        assertEquals(4, run.state().playerStats().modifiers().stream()
                .filter(m -> m.source().equals(cardistry.core.CombatEngine.PROGRESSION_SOURCE)).count());
    }

    @Test
    void recompensesDeBossAuMoinsRares() {
        Run run = strongRun(2);
        playUntil(run, r -> r.phase() == Run.Phase.COMBAT && r.state().isBossFight());
        run.startTurn();
        run.submit(new Action.PlayCard(0));
        assertEquals(Run.Phase.REWARD, run.phase());
        assertTrue(run.pendingReward().stream()
                .allMatch(c -> c.rarity().compareTo(cardistry.items.Rarity.RARE) >= 0), run.pendingReward().toString());
    }

    @Test
    void departSurLaCarte() {
        Run run = strongRun(1);
        assertEquals(Run.Phase.MAP, run.phase());
        assertEquals(1, run.state().level());
        assertEquals(0, run.state().floor());
        assertEquals(run.map().orElseThrow().floor(1), run.choices());
        assertTrue(events.contains(new GameEvent.LevelStarted(1)));
        assertThrows(IllegalStateException.class, run::startTurn);
    }

    @Test
    void unCombatParNoeudDeCombat() {
        Run run = strongRun(1);
        run.chooseNode(0);
        assertEquals(Run.Phase.COMBAT, run.phase());
        assertEquals(1, run.state().floor());
        assertEquals(EnemyRank.COMMON, run.state().currentEnemy().rank());
        assertTrue(run.startTurn());
        assertEquals(ActionResult.TURN_ENDED, run.submit(new Action.PlayCard(0)));
        assertTrue(run.phase() == Run.Phase.REWARD || run.phase() == Run.Phase.CARD_REWARD);
        if (run.phase() == Run.Phase.REWARD) {
            run.resolveReward(new RewardChoice.Skip());
        }
        assertEquals(Run.Phase.CARD_REWARD, run.phase());
        run.resolveCardReward(-1);
        assertEquals(Run.Phase.MAP, run.phase());
        assertEquals(run.map().orElseThrow().choicesAfter(run.position()), run.choices());
        assertEquals(2, run.choices().get(0).floor());
    }

    @Test
    void troisNiveauxPuisVictoire() {
        Run run = strongRun(3);
        List<MapNode> path = playToEnd(run, 0);
        assertTrue(run.state().isWon());
        assertEquals(3, run.state().level());
        assertEquals(RunMap.FLOORS * 3, path.size());
        assertEquals(3, path.stream().filter(n -> n.type() == NodeType.BOSS).count());
        assertTrue(events.contains(new GameEvent.LevelStarted(2)));
        assertTrue(events.contains(new GameEvent.LevelStarted(3)));
        assertEquals(new GameEvent.GameWon(), events.stream().filter(e -> e instanceof GameEvent.GameWon).findFirst()
                .orElseThrow());
        List<String> bosses = events.stream().filter(e -> e instanceof GameEvent.CombatStarted)
                .map(e -> ((GameEvent.CombatStarted) e).enemy())
                .filter(e -> e.rank() == EnemyRank.BOSS).map(e -> e.id()).toList();
        assertEquals(List.of("boss-1", "boss-2", "boss-3"), bosses);
    }

    @Test
    void elitesEtNiveauDesAdversaires() {
        // Sur plusieurs seeds et chemins : élites sur les nœuds Élite, niveau appliqué.
        for (long seed = 0; seed < 20; seed++) {
            events.clear();
            Run run = strongRun(seed);
            playToEnd(run, (int) seed);
            List<GameEvent> list = List.copyOf(events);
            int level = 0;
            String kind = null;
            for (GameEvent event : list) {
                if (event instanceof GameEvent.LevelStarted l) {
                    level = l.level();
                } else if (event instanceof GameEvent.NodeEntered n) {
                    kind = n.kind();
                } else if (event instanceof GameEvent.CombatStarted c) {
                    EnemyRank expected = switch (kind) {
                        case "Élite" -> EnemyRank.ELITE;
                        case "Boss" -> EnemyRank.BOSS;
                        default -> EnemyRank.COMMON;
                    };
                    assertEquals(expected, c.enemy().rank(), kind);
                    assertEquals(cardistry.model.enemy.EnemyRegistry.DAMAGE_SCALE[level - 1], c.enemy().damageScale());
                }
            }
        }
    }

    @Test
    void memeSeedMemesChoixMemePartie() {
        Run a = strongRun(9);
        List<MapNode> pathA = playToEnd(a, 1);
        List<String> enemiesA = enemies();
        events.clear();
        Run b = strongRun(9);
        List<MapNode> pathB = playToEnd(b, 1);
        assertEquals(pathA, pathB);
        assertEquals(enemiesA, enemies());
        assertEquals(a.state().choomies(), b.state().choomies());
    }

    private List<String> enemies() {
        return events.stream().filter(e -> e instanceof GameEvent.CombatStarted)
                .map(e -> ((GameEvent.CombatStarted) e).enemy().id()).toList();
    }

    @Test
    void ctrlZRejoueLaCarte() {
        EventBus bus = new EventBus();
        Run run = new Run(5, Set.of(), bus, GameState::newGame,
                () -> new CardPool(List.of(new cardistry.model.card.TerrainCard("Forêt", cardistry.model.Element.NATURE, cardistry.items.Rarity.COMMON))));
        run.addCode(Secret.CTRL_Z);
        run.start();
        run.chooseNode(1);
        MapNode position = run.position();
        String enemy = run.state().currentEnemy().id();
        run.startTurn();
        run.submit(new Action.PlayCard(0));
        run.startTurn();
        double hp = run.state().player().hp();
        assertTrue(run.canUndo());
        assertEquals(ActionResult.FREE_ACTION, run.submit(new Action.Undo()));
        assertEquals(position, run.position());
        assertEquals(enemy, run.state().currentEnemy().id());
        assertEquals(1, run.state().turn());
        assertEquals(Run.Phase.COMBAT, run.phase());
        assertNotNull(run.map().orElseThrow());
        assertTrue(run.state().player().hp() >= hp);
        assertSame(run.choices().size(), run.map().orElseThrow().choicesAfter(position).size());
    }

    // --- Shop ---

    /** Va au premier shop atteignable en le préférant partout (Ctrl+Z équipé si {@code undo}). */
    private Run runToShop(long seed, boolean undo) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        Run run = new Run(seed, Set.of(), bus,
                () -> GameState.newGame(java.util.Collections.nCopies(12, new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))),
                () -> new CardPool(List.of(new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))));
        if (undo) {
            run.addCode(Secret.CTRL_Z);
        }
        run.start();
        int guard = 0;
        while (run.phase() != Run.Phase.SHOP && guard++ < 10_000) {
            switch (run.phase()) {
                case MAP -> {
                    List<MapNode> choices = run.choices();
                    int shop = -1;
                    for (int i = 0; i < choices.size(); i++) {
                        if (choices.get(i).type() == NodeType.SHOP) {
                            shop = i;
                        }
                    }
                    run.chooseNode(Math.max(0, shop));
                }
                case COMBAT -> {
                    if (run.startTurn()) {
                        run.submit(new Action.PlayCard(0));
                    }
                }
                case REWARD -> run.resolveReward(new RewardChoice.Skip());
                case CARD_REWARD -> run.resolveCardReward(-1);
                case REST -> run.rest(RestOption.HEAL);
                case STAT_POINTS -> run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH);
                case ALTAR -> run.leaveAltar();
                case EVENT -> run.chooseStory(run.story().orElseThrow().choices().size() - 1);
                case SHOP, OVER -> {
                }
            }
        }
        return run;
    }

    @Test
    void noeudShopOuvreLeShop() {
        Run run = runToShop(4, false);
        assertEquals(Run.Phase.SHOP, run.phase());
        assertEquals(NodeType.SHOP, run.position().type());
        // Pool d'une seule carte : une seule carte proposée, puis 2 objets et 2 trinkets.
        assertEquals(1 + 2 + 2, run.shop().orElseThrow().offers().size());
        assertEquals(75, run.cardRemovalPrice());
        int choomies = run.state().choomies();
        assertTrue(run.shop(new cardistry.core.shop.ShopAction.Reroll()) == (choomies >= 20));
        run.shop(new cardistry.core.shop.ShopAction.Leave());
        assertEquals(Run.Phase.MAP, run.phase());
    }

    @Test
    void ctrlZRejoueLesAchats() {
        Run run = runToShop(4, true);
        run.state(); // shop ouvert
        int choomies = run.state().choomies();
        boolean bought = run.shop(cardistry.core.shop.ShopAction.Buy.of(0)); // une carte
        int deck = run.state().deck().size();
        run.shop(new cardistry.core.shop.ShopAction.Leave());
        // Combat suivant, deux tours, puis annulation : l'achat est rejoué à l'identique.
        while (run.phase() != Run.Phase.COMBAT) {
            switch (run.phase()) {
                case MAP -> run.chooseNode(0);
                case REST -> run.rest(RestOption.HEAL);
                case STAT_POINTS -> run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH);
                case ALTAR -> run.leaveAltar();
                case SHOP -> run.shop(new cardistry.core.shop.ShopAction.Leave());
                case EVENT -> run.chooseStory(run.story().orElseThrow().choices().size() - 1);
                default -> throw new AssertionError(run.phase());
            }
        }
        run.state().currentEnemy().heal(100_000); // survit au premier sort
        run.startTurn();
        run.submit(new Action.RollCigarette());
        run.startTurn();
        assertEquals(ActionResult.FREE_ACTION, run.submit(new Action.Undo()));
        assertEquals(deck, run.state().deck().size());
        if (bought) {
            assertTrue(run.state().choomies() < choomies + 200);
        }
    }

    // --- Événements ---

    @Test
    void evenementsSansRepetitionPuisRejouables() {
        // Une partie qui préfère les « ? » : chaque événement vu une fois avant d'en revoir un.
        EventBus bus = new EventBus();
        List<String> seen = new ArrayList<>();
        bus.subscribe(e -> {
            if (e instanceof GameEvent.StoryStarted s) {
                seen.add(s.story().id());
            }
        });
        Run run = new Run(6, Set.of(), bus,
                () -> GameState.newGame(java.util.Collections.nCopies(12, new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))),
                () -> new CardPool(List.of(new SpellCard("Météore", FIRE, 10_000, cardistry.items.Rarity.COMMON))));
        run.start();
        int guard = 0;
        while (!run.isOver() && guard++ < 10_000) {
            switch (run.phase()) {
                case MAP -> {
                    List<MapNode> choices = run.choices();
                    int pick = 0;
                    for (int i = 0; i < choices.size(); i++) {
                        if (choices.get(i).type() == NodeType.EVENT) {
                            pick = i;
                        }
                    }
                    run.chooseNode(pick);
                }
                case EVENT -> {
                    assertEquals(NodeType.EVENT, run.position().type());
                    run.chooseStory(run.story().orElseThrow().choices().size() - 1);
                    assertTrue(run.lastStoryResult().isPresent());
                }
                case COMBAT -> {
                    if (run.startTurn()) {
                        run.submit(new Action.PlayCard(0));
                    }
                }
                case REWARD -> run.resolveReward(new RewardChoice.Skip());
                case CARD_REWARD -> run.resolveCardReward(-1);
                case REST -> run.rest(RestOption.HEAL);
                case STAT_POINTS -> run.spendStatPoint(cardistry.core.stats.StatType.STRENGTH);
                case ALTAR -> run.leaveAltar();
                case SHOP -> run.shop(new cardistry.core.shop.ShopAction.Leave());
                case OVER -> {
                }
            }
        }
        assertTrue(seen.size() >= 3, "au moins un tour complet : " + seen);
        int n = cardistry.story.StoryRegistry.all().size();
        for (int start = 0; start + n <= seen.size(); start += n) {
            assertEquals(n, seen.subList(start, start + n).stream().distinct().count(), "répétition : " + seen);
        }
    }
}
