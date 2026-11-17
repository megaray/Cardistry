package cardistry.tools;

import cardistry.core.Action;
import cardistry.core.ActionResult;
import cardistry.core.GameState;
import cardistry.core.RewardChoice;
import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.shop.ShopAction;
import cardistry.core.shop.ShopOffer;
import cardistry.hero.Hero;
import cardistry.hero.HeroRegistry;
import cardistry.items.Collectible;
import cardistry.items.Item;
import cardistry.items.Rarity;
import cardistry.items.Trinket;
import cardistry.model.Enemy;
import cardistry.model.card.Card;
import cardistry.model.card.DeadCard;
import cardistry.model.card.HealCard;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import cardistry.model.enemy.EnemyRank;
import cardistry.run.RestOption;
import cardistry.run.Run;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Équilibrage ({@code ./gradlew balanceRun [-Pseeds=N] [-Pheroes=id,id]}) : un bot simple joue des
 * parties complètes sans affichage, avec chaque personnage, et écrit sur la sortie la durée des
 * combats, les PV autour des boss, le taux de victoire et la fréquence des Légendaires proposées.
 *
 * <p>Le bot : soin sous 40 % de PV, sinon le sort le plus fort (multiplicateurs compris) ou un
 * terrain qui affaiblit l'adversaire (Dave : qui rend un sort de sa main « Full skill »), sinon pioche ; objet sous 45 % de PV ; clope sous 60 % ; prend la
 * première récompense qui rentre, la carte de récompense qui frappe le plus fort ; sieste : dormir
 * sous 70 % de PV ; au shop, achète le premier objet ou trinket abordable. Ses propres choix (carte)
 * sont tirés d'un {@link Random} seedé : même seed, même partie.
 */
public final class BalanceRun {

    private static final int MAX_STEPS = 20_000;
    /** Au-delà, un combat est considéré comme bloqué (main pleine de cartes mortes…) : la partie s'arrête. */
    private static final int MAX_COMBAT_TURNS = 150;

    /** Totaux d'un rang d'adversaire (sur toutes les parties d'un personnage). */
    private static final class RankStats {
        int combats;
        int turns;
        double hpLost;
    }

    /** Totaux d'un personnage. */
    private static final class HeroStats {
        int runs;
        int wins;
        int stuck;
        int floorsReached;
        final Map<EnemyRank, RankStats> ranks = new EnumMap<>(EnemyRank.class);
        final double[] bossHpBefore = new double[3];
        final double[] bossHpAfter = new double[3];
        final int[] bossFights = new int[3];
        final int[] bossWins = new int[3];
        final int[] deathsByLevel = new int[3];
    }

    /** Légendaires proposées, toutes parties confondues. */
    private static final class LootStats {
        int runs;
        int itemOffers;
        int itemLegendaries;
        int cardOffers;
        int cardLegendaries;
        int shopItemOffers;
        int shopItemLegendaries;
        int shopCardOffers;
        int shopCardLegendaries;
        int runsWithLegendary;
    }

    private BalanceRun() {
    }

    public static void main(String[] args) {
        int seeds = Integer.getInteger("seeds", 30);
        List<Hero> heroes = new ArrayList<>(HeroRegistry.all());
        String only = System.getProperty("heroes", "");
        if (!only.isBlank()) {
            List<String> ids = List.of(only.split(","));
            heroes.removeIf(h -> !ids.contains(h.id()));
        }
        LootStats loot = new LootStats();
        System.out.printf("Équilibrage : %d parties par personnage (seeds 1 à %d)%n%n", seeds, seeds);
        for (Hero hero : heroes) {
            HeroStats stats = new HeroStats();
            for (long seed = 1; seed <= seeds; seed++) {
                play(hero, seed, stats, loot);
            }
            report(hero, stats);
        }
        reportLoot(loot);
    }

    private static void play(Hero hero, long seed, HeroStats stats, LootStats loot) {
        EventBus bus = new EventBus();
        Run run = new Run(seed, Set.of(), bus);
        Tracker tracker = new Tracker(run, stats, loot);
        bus.subscribe(tracker::onEvent);
        run.setHero(hero);
        Bot bot = new Bot(seed);
        run.setDecisions(bot);
        run.start();
        int steps = 0;
        while (!run.isOver() && steps++ < MAX_STEPS) {
            if (run.phase() == Run.Phase.COMBAT && run.state().combatTurn() > MAX_COMBAT_TURNS) {
                break;
            }
            switch (run.phase()) {
                case MAP -> run.chooseNode(bot.random.nextInt(run.choices().size()));
                case COMBAT -> combatTurn(run, bot);
                case REWARD -> run.resolveReward(bot.reward(run.state(), run.pendingReward()));
                case CARD_REWARD -> run.resolveCardReward(bot.cardReward(run.state(), run.pendingCardReward()));
                case REST -> run.rest(run.state().player().hp() < 0.7 * run.state().maxHp()
                        ? RestOption.HEAL : RestOption.CIGARETTES);
                case EVENT -> {
                    cardistry.story.Story story = run.story().orElseThrow();
                    run.chooseStory(bot.chooseStory(run.state(), story));
                }
                case SHOP -> run.shop(bot.shop(run.state(), run.shop().orElseThrow()));
                case STAT_POINTS -> run.spendStatPoint(bot.statPoint(run.state()));
                case ALTAR -> run.leaveAltar();
                case OVER -> {
                }
            }
        }
        if (!run.isOver()) {
            stats.stuck++;
            System.out.printf("  ! %s seed %d : partie bloquée (phase %s, niveau %d, étage %d, %s)%n",
                    hero.name(), seed, run.phase(), run.state().level(), run.state().floor(),
                    run.state().hasEnemy() ? run.state().currentEnemy().name() + " " + run.state().currentEnemy().hp()
                            + " PV, main " + run.state().hand().stream().map(Card::name).toList()
                            + ", pioche " + run.state().drawPile().size() + ", défausse "
                            + run.state().discardPile().size() : "");
        }
        stats.runs++;
        GameState state = run.state();
        if (state.isWon()) {
            stats.wins++;
        } else if (run.isOver()) {
            stats.deathsByLevel[Math.max(0, Math.min(2, state.level() - 1))]++;
        }
        stats.floorsReached += (state.level() - 1) * 8 + state.floor();
        loot.runs++;
        if (tracker.legendaryThisRun) {
            loot.runsWithLegendary++;
        }
    }

    private static void combatTurn(Run run, Bot bot) {
        if (!run.startTurn() || run.isOver()) {
            return;
        }
        for (int guard = 0; guard < 30; guard++) {
            Action action = bot.action(run.state());
            ActionResult result = run.submit(action);
            if (result == ActionResult.TURN_ENDED || run.isOver()) {
                return;
            }
            if (Boolean.getBoolean("trace")) {
                System.out.println("tour " + run.state().turn() + " " + action + " -> " + result + " PV "
                        + run.state().player().hp() + " clopes " + run.state().cigarettes() + "/"
                        + run.state().cigaretteMax() + " adversaire " + run.state().currentEnemy().hp()
                        + " intent " + run.state().currentEnemy().peekIntent().name());
            }
            if (result != ActionResult.FREE_ACTION) {
                bot.rejected(action);
            }
        }
        if (run.submit(new Action.DrawCard()) != ActionResult.TURN_ENDED) {
            run.submit(new Action.RollCigarette());
        }
    }

    /** Relève les chiffres à partir des événements. */
    private static final class Tracker {
        private final Run run;
        private final HeroStats stats;
        private final LootStats loot;
        private Enemy enemy;
        private int turns;
        private double hpAtStart;
        boolean legendaryThisRun;

        Tracker(Run run, HeroStats stats, LootStats loot) {
            this.run = run;
            this.stats = stats;
            this.loot = loot;
        }

        void onEvent(GameEvent event) {
            GameState state = run.state();
            switch (event) {
                case GameEvent.CombatStarted e -> {
                    enemy = e.enemy();
                    turns = 0;
                    hpAtStart = state.player().hp();
                    if (enemy.rank() == EnemyRank.BOSS) {
                        int level = level(state);
                        stats.bossFights[level]++;
                        stats.bossHpBefore[level] += hpAtStart / state.maxHp();
                    }
                }
                case GameEvent.PlayerTurnStarted e -> turns++;
                case GameEvent.EnemyKilled e -> {
                    if (enemy == null) {
                        return;
                    }
                    RankStats rank = stats.ranks.computeIfAbsent(enemy.rank(), r -> new RankStats());
                    rank.combats++;
                    rank.turns += turns;
                    rank.hpLost += hpAtStart - state.player().hp();
                    if (enemy.rank() == EnemyRank.BOSS) {
                        int level = level(state);
                        stats.bossWins[level]++;
                        stats.bossHpAfter[level] += state.player().hp() / state.maxHp();
                    }
                    enemy = null;
                }
                case GameEvent.RewardOffered e -> {
                    loot.itemOffers += e.options().size();
                    loot.itemLegendaries += countCollectibles(e.options());
                }
                case GameEvent.CardRewardOffered e -> {
                    loot.cardOffers += e.options().size();
                    loot.cardLegendaries += countCards(e.options());
                }
                case GameEvent.ShopEntered e -> {
                    for (ShopOffer offer : e.shop().offers()) {
                        switch (offer) {
                            case ShopOffer.CardOffer c -> {
                                loot.shopCardOffers++;
                                if (legendary(c.card().rarity())) {
                                    loot.shopCardLegendaries++;
                                    legendaryThisRun = true;
                                }
                            }
                            case ShopOffer.CollectibleOffer c -> {
                                loot.shopItemOffers++;
                                if (legendary(c.collectible().rarity())) {
                                    loot.shopItemLegendaries++;
                                    legendaryThisRun = true;
                                }
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }

        private int countCollectibles(List<Collectible> options) {
            int n = (int) options.stream().filter(c -> legendary(c.rarity())).count();
            legendaryThisRun |= n > 0;
            return n;
        }

        private int countCards(List<Card> options) {
            int n = (int) options.stream().filter(c -> legendary(c.rarity())).count();
            legendaryThisRun |= n > 0;
            return n;
        }

        private static boolean legendary(Rarity rarity) {
            return rarity == Rarity.LEGENDARY;
        }

        private static int level(GameState state) {
            return Math.max(0, Math.min(2, state.level() - 1));
        }
    }

    /** Le joueur automatique. */
    private static final class Bot implements cardistry.ui.Input {
        final Random random;
        private final List<Action> refused = new ArrayList<>();

        Bot(long seed) {
            random = new Random(seed * 31 + 7);
        }

        void rejected(Action action) {
            refused.add(action);
        }

        Action action(GameState state) {
            double hp = state.player().hp();
            double max = state.maxHp();
            if (hp < 0.45 * max && !state.items().isEmpty()) {
                Action use = new Action.UseItem(0);
                if (!refused.contains(use)) {
                    return use;
                }
            }
            if (hp < 0.6 * max && state.cigarettes() > 0 && !refused.contains(new Action.Smoke())) {
                return new Action.Smoke();
            }
            List<Card> hand = state.hand();
            int best = -1;
            double bestScore = 0;
            for (int i = 0; i < hand.size(); i++) {
                double score = score(state, hand.get(i), hp / max);
                if (score > bestScore && !refused.contains(new Action.PlayCard(i))) {
                    best = i;
                    bestScore = score;
                }
            }
            boolean drawRefused = refused.contains(new Action.DrawCard());
            refused.clear();
            if (best >= 0 && (bestScore >= 3 || hand.size() >= state.handSize() || drawRefused)) {
                return new Action.PlayCard(best);
            }
            if (!drawRefused) {
                return new Action.DrawCard();
            }
            // Main pleine de cartes mortes : rouler une clope, après en avoir fumé une si le paquet est plein.
            return state.cigarettes() >= state.cigaretteMax() ? new Action.Smoke() : new Action.RollCigarette();
        }

        private static double score(GameState state, Card card, double hpRatio) {
            return switch (card) {
                case SpellCard spell -> (spell.hasDamageRule() ? 15 : spell.damage())
                        * (spell.isUnmultiplied() ? 1.0 : state.spellMultiplier(spell, false));
                case HealCard heal -> hpRatio < 0.4 ? 100 + heal.amount() : 0.5;
                case TerrainCard terrain -> terrainScore(state, terrain);
                case DeadCard dead -> 0;
            };
        }

        /**
         * Un terrain vaut le coup s'il affaiblit l'adversaire par rapport au terrain actuel, ou s'il
         * permet à un sort de la main de battre l'adversaire sur son propre élément (Dave).
         */
        private static double terrainScore(GameState state, TerrainCard terrain) {
            if (terrain.name().equals(state.terrain().name())) {
                return 0;
            }
            cardistry.model.Element enemy = state.currentEnemy().element();
            boolean dave = state.hero().map(h -> h.id().equals("dave")).orElse(false);
            if (dave && terrain.element() != state.terrain().element() && state.hand().stream()
                    .anyMatch(c -> c instanceof SpellCard s && s.element() == terrain.element() && s.element().beats(enemy))) {
                return 30;
            }
            double now = cardistry.model.ElementRules.terrainAttackMultiplier(state.terrain().element(), enemy);
            double then = cardistry.model.ElementRules.terrainAttackMultiplier(terrain.element(), enemy);
            return then < now ? 8 : 0;
        }

        RewardChoice reward(GameState state, List<Collectible> options) {
            for (int i = 0; i < options.size(); i++) {
                Collectible c = options.get(i);
                boolean fits = c instanceof Trinket ? state.trinkets().size() < state.trinketSlots()
                        : state.items().size() < state.itemSlots();
                if (fits) {
                    return RewardChoice.Take.into(i);
                }
            }
            return new RewardChoice.Skip();
        }

        int cardReward(GameState state, List<Card> options) {
            int best = -1;
            double bestDamage = 10;
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i) instanceof SpellCard spell && !spell.hasDamageRule() && spell.damage() > bestDamage) {
                    best = i;
                    bestDamage = spell.damage();
                }
            }
            return best;
        }

        ShopAction shop(GameState state, cardistry.core.Shop shop) {
            for (int i = 0; i < shop.offers().size(); i++) {
                if (shop.isSold(i) || shop.offers().get(i).price() > state.choomies()) {
                    continue;
                }
                if (shop.offers().get(i) instanceof ShopOffer.CollectibleOffer c) {
                    boolean fits = c.collectible() instanceof Item ? state.items().size() < state.itemSlots()
                            : state.trinkets().size() < state.trinketSlots();
                    if (fits) {
                        return ShopAction.Buy.of(i);
                    }
                }
            }
            return new ShopAction.Leave();
        }

        /** Points de stats : un dans la meilleure stat offensive, un en DEF, en alternance. */
        cardistry.core.stats.StatType statPoint(GameState state) {
            if (state.statPoints() % 2 == 1) {
                return cardistry.core.stats.StatType.DEFENSE;
            }
            cardistry.core.stats.Stats stats = state.playerStats();
            return java.util.stream.Stream.of(cardistry.core.stats.StatType.STRENGTH,
                    cardistry.core.stats.StatType.INTELLIGENCE, cardistry.core.stats.StatType.MIND)
                    .max(java.util.Comparator.comparingDouble(stats::get)).orElseThrow();
        }

        @Override
        public cardistry.ui.TitleChoice title(long seed) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void waitForContinue() {
        }

        @Override
        public Action nextAction(GameState state) {
            return action(state);
        }

        @Override
        public int chooseTrinketToDiscard(GameState state) {
            return 0;
        }
    }

    private static void report(Hero hero, HeroStats s) {
        System.out.printf(Locale.ROOT, "== %s : %d/%d victoires (%.0f %%), étage moyen atteint %.1f / 24%n",
                hero.name(), s.wins, s.runs, 100.0 * s.wins / s.runs, (double) s.floorsReached / s.runs);
        System.out.printf(Locale.ROOT, "   morts : niveau 1 %d, niveau 2 %d, niveau 3 %d ; parties bloquées %d%n",
                s.deathsByLevel[0], s.deathsByLevel[1], s.deathsByLevel[2], s.stuck);
        for (EnemyRank rank : EnemyRank.values()) {
            RankStats r = s.ranks.get(rank);
            if (r != null && r.combats > 0) {
                System.out.printf(Locale.ROOT, "   %-7s %4d combats gagnés, %.1f tours, %.1f PV perdus en moyenne%n",
                        rank, r.combats, (double) r.turns / r.combats, r.hpLost / r.combats);
            }
        }
        for (int level = 0; level < 3; level++) {
            if (s.bossFights[level] > 0) {
                System.out.printf(Locale.ROOT, "   boss %d : %d atteints, %d battus ; PV avant %.0f %% ; PV après %s%n",
                        level + 1, s.bossFights[level], s.bossWins[level],
                        100 * s.bossHpBefore[level] / s.bossFights[level],
                        s.bossWins[level] > 0 ? String.format(Locale.ROOT, "%.0f %%",
                                100 * s.bossHpAfter[level] / s.bossWins[level]) : "—");
            }
        }
        System.out.println();
    }

    private static void reportLoot(LootStats l) {
        System.out.printf("== Légendaires proposées (%d parties)%n", l.runs);
        line("récompenses d'objets/trinkets", l.itemLegendaries, l.itemOffers, l.runs);
        line("récompenses de cartes", l.cardLegendaries, l.cardOffers, l.runs);
        line("shop : objets/trinkets", l.shopItemLegendaries, l.shopItemOffers, l.runs);
        line("shop : cartes", l.shopCardLegendaries, l.shopCardOffers, l.runs);
        System.out.printf(Locale.ROOT, "   parties avec au moins une Légendaire proposée : %d/%d (%.0f %%)%n",
                l.runsWithLegendary, l.runs, 100.0 * l.runsWithLegendary / l.runs);
    }

    private static void line(String what, int legendaries, int offers, int runs) {
        System.out.printf(Locale.ROOT, "   %-30s %.2f par partie (%d sur %d propositions, %.1f %%)%n", what,
                (double) legendaries / runs, legendaries, offers, offers == 0 ? 0 : 100.0 * legendaries / offers);
    }
}
