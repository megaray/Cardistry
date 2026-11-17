package cardistry.core;

import static cardistry.model.Element.FIRE;
import static cardistry.model.Element.NATURE;
import static cardistry.model.Element.NEUTRAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.EventBus;
import cardistry.core.event.GameEvent;
import cardistry.core.stats.Stats;
import cardistry.items.ItemRegistry;
import cardistry.items.LootTable;
import cardistry.model.Enemy;
import cardistry.model.Player;
import cardistry.model.card.Card;
import cardistry.model.card.CardPool;
import cardistry.model.card.DeadCard;
import cardistry.model.card.SpellCard;
import cardistry.model.card.TerrainCard;
import cardistry.model.enemy.EnemyBehavior;
import cardistry.model.enemy.EnemyMove;
import cardistry.model.enemy.EnemyRank;
import cardistry.model.enemy.MoveEffect;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Attaques adverses génériques : intents, effets, annulation, esquive, renvoi. */
class EnemyMoveTest {

    private static final double DELTA = 1e-9;

    private final List<GameEvent> events = new ArrayList<>();
    /** Graine du moteur (changée par les tests qui cherchent un tirage précis). */
    private long seed = 3;
    private GameState state;
    private CombatEngine engine;
    private Enemy enemy;

    private static final EnemyMove NORMAL = EnemyMove.of("normal", "Normal", "Normal", MoveEffect.Hit.of(5));
    private static final EnemyMove SPECIAL = EnemyMove.of("special", "Spécial", "Spécial", MoveEffect.Hit.of(10));

    /** Adversaire neutre (terrain sans effet) de 100 PV, main de {@code hand}. */
    private void setUp(EnemyBehavior behavior, List<Card> hand) {
        setUp(Enemy.builder("cible", "Cible", NEUTRAL, EnemyRank.COMMON).hp(100).behavior(behavior), hand);
    }

    private void setUp(Enemy.Builder builder, List<Card> hand) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        enemy = builder.build();
        state = new GameState(new Player(100), List.of(enemy), new TerrainCard("Forêt", NATURE, cardistry.items.Rarity.COMMON), hand, new Stats());
        engine = new CombatEngine(state, new CardPool(List.of(new TerrainCard("Pioche", FIRE, cardistry.items.Rarity.COMMON))), new Rng(seed), bus,
                new LootTable(List::of));
    }

    private static EnemyBehavior always(MoveEffect... effects) {
        return EnemyBehavior.always(EnemyMove.of("coup", "Coup", "Coup", effects));
    }

    /** Fin de tour sans rien changer à la main : rouler une clope. */
    private void endTurn() {
        assertEquals(ActionResult.TURN_ENDED, engine.submit(new Action.RollCigarette()));
    }

    private void giveMoscowMule() {
        engine.give(ItemRegistry.item("moscow-mule"));
        engine.submit(new Action.UseItem(0));
    }

    // --- Intents ---

    @Test
    void intentAnnonceAuDebutPuisApresChaqueAttaque() {
        setUp(EnemyBehavior.every(3, SPECIAL, NORMAL), List.of());
        engine.start();
        assertSame(NORMAL, enemy.intent());
        assertTrue(events.contains(new GameEvent.IntentChosen(enemy, NORMAL)));

        endTurn();
        assertEquals(95, state.player().hp(), DELTA);
        assertSame(NORMAL, enemy.intent());
        endTurn();
        assertSame(SPECIAL, enemy.intent()); // 3e attaque
        endTurn();
        assertEquals(100 - 5 - 5 - 10, state.player().hp(), DELTA);
        assertSame(NORMAL, enemy.intent());
        assertEquals(3, enemy.actions());
        assertEquals(1, enemy.uses("special"));
    }

    @Test
    void attaqueUtiliseePublieeAvantSesEffets() {
        setUp(EnemyBehavior.always(SPECIAL), List.of());
        engine.start();
        events.clear();
        endTurn();
        int used = events.indexOf(new GameEvent.EnemyMoveUsed(enemy, SPECIAL));
        assertTrue(used >= 0);
        assertInstanceOf(GameEvent.EnemyAttacked.class, events.get(used + 1));
    }

    @Test
    void niveauEtTerrainMultiplientLesDegats() {
        setUp(Enemy.builder("feu", "Feu", FIRE, EnemyRank.COMMON).hp(50).scale(1.4, 1.5)
                .behavior(EnemyBehavior.always(SPECIAL)), List.of());
        assertEquals(70, enemy.maxHp(), DELTA);
        endTurn();
        // 10 × 1.5 (niveau) × 1 (Forêt : nature, ni pour ni contre le feu en attaque)
        assertEquals(85, state.player().hp(), DELTA);
    }

    @Test
    void neutreIgnoreLeTerrain() {
        setUp(EnemyBehavior.always(SPECIAL), List.of());
        assertEquals(1.0, state.enemyAttackMultiplier(), DELTA);
    }

    // --- Coups ---

    @Test
    void plusieursCoupsEtReductionParCoup() {
        setUp(always(MoveEffect.Hit.of(4, 2)), List.of());
        state.playerStats().add(cardistry.core.stats.StatModifier.flat(
                cardistry.core.stats.StatType.DEFENSE, 1, "test"));
        endTurn();
        assertEquals(100 - 3 - 3, state.player().hp(), DELTA);
        assertEquals(2, events.stream().filter(e -> e instanceof GameEvent.EnemyAttacked).count());
    }

    @Test
    void moscowMuleAnnuleTousLesCoups() {
        setUp(always(MoveEffect.Hit.of(4, 3)), List.of());
        giveMoscowMule();
        endTurn();
        assertEquals(100, state.player().hp(), DELTA);
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.AttackCancelled).count());
        assertTrue(state.effects().isEmpty());
    }

    @Test
    void degatsSelonLesCartesQuiManquent() {
        // 6 + 3 par carte sous 5 : main de 2 cartes → 6 + 9
        setUp(always(new MoveEffect.Hit(6, 1, 3, 0, 0)),
                List.of(new TerrainCard("A", FIRE, cardistry.items.Rarity.COMMON), new TerrainCard("B", FIRE, cardistry.items.Rarity.COMMON)));
        assertEquals(15, state.enemyAttackDamage(), DELTA);
        endTurn();
        assertEquals(85, state.player().hp(), DELTA);
    }

    @Test
    void coupRateSoigneLAdversaire() {
        setUp(always(new MoveEffect.Hit(13, 1, 0, 1.0, 8)), List.of());
        enemy.takeDamage(20);
        endTurn();
        assertEquals(100, state.player().hp(), DELTA);
        assertEquals(88, enemy.hp(), DELTA);
        assertTrue(events.contains(new GameEvent.EnemyMissed(enemy, 8)));
    }

    // --- Vols ---

    @Test
    void volPrendDesChoomiesEnPriorite() {
        setUp(always(new MoveEffect.Pickpocket(5, 1, 4)), List.of());
        assertEquals(GameState.STARTING_CHOOMIES, state.choomies());
        endTurn();
        assertEquals(GameState.STARTING_CHOOMIES - 5, state.choomies());
        assertEquals(GameState.STARTING_CIGARETTES + 1, state.cigarettes()); // roulée, pas volée
        assertEquals(100, state.player().hp(), DELTA);
        assertTrue(events.contains(new GameEvent.ChoomiesStolen(enemy, 5)));
    }

    @Test
    void volSansChoomiesPrendUneClopePuisFrappe() {
        setUp(always(new MoveEffect.Pickpocket(50, 1, 4)), List.of());
        endTurn();
        assertEquals(GameState.STARTING_CIGARETTES, state.cigarettes()); // +1 roulée, −1 volée
        assertEquals(GameState.STARTING_CHOOMIES, state.choomies());

        engine.submit(new Action.DrawCard()); // pioche : ne change pas les clopes
        assertEquals(GameState.STARTING_CIGARETTES - 1, state.cigarettes());
        while (state.cigarettes() > 0) {
            engine.submit(new Action.Smoke());
        }
        double hp = state.player().hp();
        engine.submit(new Action.DrawCard());
        assertEquals(hp - 4, state.player().hp(), DELTA);
    }

    @Test
    void volPasAnnuleParMoscowMule() {
        setUp(always(new MoveEffect.Pickpocket(5, 1, 4)), List.of());
        giveMoscowMule();
        endTurn();
        assertEquals(GameState.STARTING_CHOOMIES - 5, state.choomies());
        assertFalse(state.effects().isEmpty()); // le Moscow Mule reste pour une vraie attaque
    }

    @Test
    void taxePrendUneClopeSinonDeuxCoups() {
        setUp(always(new MoveEffect.Tax(1, 5, 2)), List.of(new TerrainCard("A", FIRE, cardistry.items.Rarity.COMMON)));
        engine.submit(new Action.PlayCard(0));
        assertEquals(GameState.STARTING_CIGARETTES - 1, state.cigarettes());
        assertEquals(100, state.player().hp(), DELTA);
        assertTrue(events.contains(new GameEvent.CigarettesStolen(enemy, 1)));

        while (state.cigarettes() > 0) {
            engine.submit(new Action.Smoke());
        }
        double hp = state.player().hp();
        engine.submit(new Action.DrawCard());
        assertEquals(hp - 10, state.player().hp(), DELTA);
    }

    // --- Esquive, carte morte, soin ---

    @Test
    void esquiveLeProchainSort() {
        setUp(always(new MoveEffect.Gamble(1.0, new MoveEffect.Dodge(), MoveEffect.Hit.of(2, 2))),
                List.of(new SpellCard("Boule", FIRE, 10, cardistry.items.Rarity.COMMON), new SpellCard("Boule", FIRE, 10, cardistry.items.Rarity.COMMON)));
        endTurn();
        assertTrue(enemy.dodgeReady());
        engine.submit(new Action.PlayCard(0));
        assertEquals(100, enemy.hp(), DELTA);
        assertTrue(events.contains(new GameEvent.EnemyDodged(enemy)));
        assertTrue(enemy.dodgeReady()); // il s'est remis en garde à son tour
    }

    @Test
    void gambleRateDonneLAutreEffet() {
        setUp(always(new MoveEffect.Gamble(0.0, new MoveEffect.Dodge(), MoveEffect.Hit.of(2, 2))), List.of());
        endTurn();
        assertFalse(enemy.dodgeReady());
        assertEquals(96, state.player().hp(), DELTA);
    }

    @Test
    void carteMaudite() {
        setUp(always(new MoveEffect.CurseCard("Exercice 3b")),
                List.of(new TerrainCard("A", FIRE, cardistry.items.Rarity.COMMON), new TerrainCard("B", FIRE, cardistry.items.Rarity.COMMON)));
        endTurn();
        long dead = state.hand().stream().filter(c -> c instanceof DeadCard).count();
        assertEquals(1, dead);
        Card cursed = state.hand().stream().filter(c -> c instanceof DeadCard).findFirst().orElseThrow();
        assertEquals("Exercice 3b", cursed.name());
        assertEquals(ActionResult.REJECTED, engine.submit(new Action.PlayCard(state.hand().indexOf(cursed))));
    }

    @Test
    void soinPlafonneAuxPvMax() {
        setUp(always(new MoveEffect.HealSelf(12)), List.of());
        enemy.takeDamage(5);
        endTurn();
        assertEquals(100, enemy.hp(), DELTA);
        assertTrue(events.contains(new GameEvent.EnemyHealed(enemy, 5)));
    }

    @Test
    void renvoiDeDegatsSansJamaisTuer() {
        setUp(Enemy.builder("peneu", "Peneu", NEUTRAL, EnemyRank.ELITE).hp(500).reflect(0.2)
                .behavior(EnemyBehavior.always(NORMAL)), List.of(new SpellCard("Gros", FIRE, 100, cardistry.items.Rarity.COMMON),
                new SpellCard("Gros", FIRE, 100, cardistry.items.Rarity.COMMON)));
        engine.submit(new Action.PlayCard(0));
        assertEquals(400, enemy.hp(), DELTA);
        assertEquals(100 - 20 - 5, state.player().hp(), DELTA);
        state.player().takeDamage(70); // 5 PV
        engine.submit(new Action.PlayCard(0));
        // le renvoi (20) est limité à 4 : il laisse 1 PV ; l'attaque normale (5) tue ensuite
        assertTrue(events.contains(new GameEvent.DamageDealt(state.player(), 4, 0.2,
                GameEvent.DamageDealt.Source.REFLECTED)));
        assertTrue(state.isLost());
    }

    @Test
    void tourSauteGardeLIntent() {
        setUp(EnemyBehavior.every(2, SPECIAL, NORMAL), List.of());
        engine.start();
        engine.give(new cardistry.items.Item("pause", "Pause", "x", "", cardistry.items.Rarity.COMMON,
                java.util.Set.of(), ctx -> ctx.addEffect(TemporaryEffect.builder("pause", "Pause")
                        .skipsNextEnemyTurn("Cible passe son tour").charges(1).build())));
        engine.submit(new Action.UseItem(0));
        endTurn();
        assertEquals(100, state.player().hp(), DELTA);
        assertEquals(0, enemy.actions());
        assertSame(NORMAL, enemy.intent());
    }

    // --- Un dernier p'tit verre (Zo le StreMon) ---

    private static final EnemyMove PTIT_VERRE = EnemyMove.of("ptit-verre", "Un dernier p'tit verre", "P'tit verre",
            MoveEffect.Hit.of(5), new MoveEffect.Drink(1));

    /** Deux adversaires : le combat contre le premier n'est pas le dernier (Gueule de bois possible). */
    private void setUpDrinker() {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        enemy = Enemy.builder("zo", "Zo", NEUTRAL, EnemyRank.BOSS).hp(100)
                .behavior(EnemyBehavior.always(PTIT_VERRE)).build();
        Enemy next = Enemy.builder("suivant", "Suivant", NEUTRAL, EnemyRank.COMMON).hp(100)
                .behavior(EnemyBehavior.always(NORMAL)).build();
        state = new GameState(new Player(100), List.of(enemy, next),
                new TerrainCard("Forêt", NATURE, cardistry.items.Rarity.COMMON), List.of(), new Stats());
        engine = new CombatEngine(state, new CardPool(List.of(new TerrainCard("Pioche", FIRE, cardistry.items.Rarity.COMMON))),
                new Rng(seed), bus, new LootTable(List::of));
    }

    @Test
    void boissonNEstPasUneAttaqueMaisLeVerreEnEstUne() {
        assertFalse(new MoveEffect.Drink(1).isAttack());
        assertTrue(PTIT_VERRE.isAttack()); // à cause du coup de 5
    }

    @Test
    void ptitVerreDonneUneIvresse() {
        setUpDrinker();
        endTurn();
        assertEquals(1, state.drunkenness());
        assertEquals(DrunkTier.TIPSY, state.drunkTier());
        assertTrue(events.contains(new GameEvent.DrunkennessChanged(1, state.hangoverThreshold())));
    }

    @Test
    void ptitVerreFrappeDeCinqPuisFaitBoire() {
        setUpDrinker();
        events.clear();
        endTurn();
        assertEquals(95, state.player().hp(), DELTA);
        int hit = indexOf(GameEvent.EnemyAttacked.class);
        int drink = indexOf(GameEvent.DrunkennessChanged.class);
        assertTrue(hit >= 0 && drink > hit, "le coup avant l'Ivresse");
    }

    @Test
    void ptitVerreDegatsMultipliesParLeNiveau() {
        setUp(Enemy.builder("zo", "Zo", NEUTRAL, EnemyRank.BOSS).hp(100).scale(1.0, 1.25)
                .behavior(EnemyBehavior.always(PTIT_VERRE)), List.of());
        double expected = state.hitDamage(MoveEffect.Hit.of(5)); // 5 × 1,25 (arrondi des PV entiers)
        assertTrue(expected > 5);
        endTurn();
        assertEquals(100 - expected, state.player().hp(), DELTA);
        assertEquals(1, state.drunkenness());
    }

    @Test
    void moscowMuleAnnuleLeCoupPasLIvresse() {
        setUpDrinker();
        giveMoscowMule(); // le Moscow Mule est lui-même une boisson : Ivresse 1
        assertEquals(1, state.drunkenness());
        endTurn();
        assertEquals(100, state.player().hp(), DELTA);
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.AttackCancelled).count());
        assertEquals(2, state.drunkenness());
    }

    @Test
    void ptitVerreAuSeuilProgrammeUneGueuleDeBois() {
        setUpDrinker();
        state.setDrunkenness(state.hangoverThreshold() - 1);
        assertEquals(0, state.pendingIntensity(Curse.HANGOVER));
        endTurn();
        assertEquals(state.hangoverThreshold(), state.drunkenness());
        assertEquals(1, state.pendingIntensity(Curse.HANGOVER));
        assertTrue(events.contains(new GameEvent.CurseGained(Curse.HANGOVER)));
    }

    @Test
    void ptitVerreIntent() {
        setUpDrinker();
        assertEquals("5, +1 Ivresse pour toi", cardistry.ui.IntentText.effects(state, PTIT_VERRE));
    }

    // --- Vol de trinket (Nortin) ---

    @Test
    void volDeTrinketCompteCommeUneAttaque() {
        MoveEffect steal = new MoveEffect.StealTrinket(0.2, 8);
        assertTrue(steal.isAttack());
        assertTrue(EnemyMove.of("vol-trinket", "Vol de trinket", "Vol de trinket", steal).isAttack());
    }

    @Test
    void volDeTrinketIntentAvecChanceEtCoupSansTrinket() {
        MoveEffect steal = new MoveEffect.StealTrinket(0.2, 8);
        setUp(always(steal), List.of());
        assertEquals("20 % de te voler un trinket, sans trinket : 8",
                cardistry.ui.IntentText.effects(state, EnemyMove.of("vol-trinket", "Vol de trinket", "Vol de trinket", steal)));
    }

    @Test
    void journalDuVolDeTrinket() {
        Enemy nortin = cardistry.model.enemy.EnemyRegistry.create(cardistry.model.enemy.EnemyRegistry.bossId(1), 1);
        assertEquals("Nortin te vole Diplôme EPFL !", cardistry.ui.IntentText.journal(
                new GameEvent.TrinketStolen(nortin, ItemRegistry.trinket("diplome-epfl"))));
    }

    @Test
    void journalDuTrinketRendu() {
        assertEquals("Tu récupères Diplôme EPFL", cardistry.ui.IntentText.journal(
                new GameEvent.TrinketReturned(ItemRegistry.trinket("diplome-epfl"))));
    }

    // --- Vol de trinket : exécution ---

    private static cardistry.items.Trinket plainTrinket(String id, cardistry.items.Tag... tags) {
        return cardistry.items.Trinket.builder(id, id, cardistry.items.Rarity.COMMON).tags(tags).build();
    }

    /** Adversaire qui tente toujours un vol de trinket ({@code chance}, coup de 8 sans trinket). */
    private void setUpThief(double chance) {
        setUp(always(new MoveEffect.StealTrinket(chance, 8)), List.of());
    }

    private void equipPlain(int count) {
        for (int i = 0; i < count; i++) {
            engine.equip(plainTrinket("t" + i));
        }
    }

    private long stolenEvents() {
        return events.stream().filter(e -> e instanceof GameEvent.TrinketStolen).count();
    }

    @Test
    void volReussiPrendUnTrinketEtLAnnonce() {
        setUpThief(1.0);
        equipPlain(3);
        List<cardistry.items.Trinket> before = List.copyOf(state.trinkets());
        endTurn();
        assertEquals(2, state.trinkets().size());
        assertEquals(1, state.stolenTrinkets().size());
        cardistry.items.Trinket stolen = state.stolenTrinkets().get(0);
        assertTrue(before.contains(stolen));
        assertTrue(events.contains(new GameEvent.TrinketStolen(enemy, stolen)));
        assertEquals(100, state.player().hp(), DELTA);
    }

    @Test
    void volRateNeFaitRien() {
        setUpThief(0.0);
        equipPlain(3);
        endTurn();
        assertEquals(3, state.trinkets().size());
        assertTrue(state.stolenTrinkets().isEmpty());
        assertEquals(0, stolenEvents());
        assertEquals(100, state.player().hp(), DELTA);
    }

    @Test
    void volSansTrinketDonneUnCoup() {
        setUpThief(1.0);
        endTurn();
        assertEquals(92, state.player().hp(), DELTA);
        assertTrue(state.stolenTrinkets().isEmpty());
        assertEquals(0, stolenEvents());
    }

    @Test
    void volBloqueParMoscowMuleQuiEstConsomme() {
        setUpThief(1.0);
        equipPlain(3);
        giveMoscowMule();
        endTurn();
        assertEquals(3, state.trinkets().size());
        assertTrue(state.stolenTrinkets().isEmpty());
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.AttackCancelled).count());
        assertTrue(state.effects().isEmpty());
    }

    @Test
    void moscowMuleConsommeMemeSiLeVolAuraitRate() {
        setUpThief(0.0);
        equipPlain(3);
        giveMoscowMule();
        endTurn();
        assertTrue(state.effects().isEmpty());
        assertEquals(1, events.stream().filter(e -> e instanceof GameEvent.AttackCancelled).count());
    }

    @Test
    void volBloqueParLImmuniteDeFons() {
        setUpThief(1.0);
        equipPlain(3);
        boolean[] landed = new boolean[1];
        engine.give(new cardistry.items.Item("fons", "Fons", "", "", cardistry.items.Rarity.COMMON, java.util.Set.of(),
                ctx -> landed[0] = ctx.applyStatus(cardistry.core.status.StatusTarget.PLAYER,
                        cardistry.core.status.StatusType.FONS, 1, 2, new cardistry.core.status.StatusExtras(0.08, true))));
        engine.submit(new Action.UseItem(state.items().size() - 1));
        assertTrue(landed[0]);
        endTurn();
        assertEquals(3, state.trinkets().size());
        assertTrue(state.stolenTrinkets().isEmpty());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.AttackCancelled));
    }

    /** Diplôme EPFL + 5 trinkets, puis un vol réussi ; renvoie {@code true} si le Diplôme a été volé. */
    private boolean stealWithDiplomaAndFiveOthers(long engineSeed) {
        seed = engineSeed;
        events.clear();
        setUpThief(1.0);
        engine.equip(ItemRegistry.trinket("diplome-epfl"));
        equipPlain(5);
        assertEquals(6, state.trinkets().size());
        endTurn();
        return state.stolenTrinkets().stream().anyMatch(t -> t.id().equals("diplome-epfl"));
    }

    @Test
    void diplomeVoleAvecSixTrinketsSeulLeDiplomePart() {
        long s = 0;
        while (!stealWithDiplomaAndFiveOthers(s)) {
            assertTrue(++s < 500, "aucune graine ne vole le Diplôme");
        }
        // Le Diplôme libère sa propre place : 5 trinkets pour 5 emplacements, rien à jeter.
        assertEquals(1, state.stolenTrinkets().size());
        assertEquals(5, state.trinkets().size());
        assertEquals(5, state.trinketSlots());
        assertEquals(1, stolenEvents());
        assertFalse(events.stream().anyMatch(e -> e instanceof GameEvent.TrinketDiscarded));
    }

    @Test
    void autreTrinketVoleAvecLeDiplomeUnSeulPart() {
        long s = 0;
        while (stealWithDiplomaAndFiveOthers(s)) {
            assertTrue(++s < 500, "aucune graine ne vole un autre trinket que le Diplôme");
        }
        assertEquals(1, state.stolenTrinkets().size());
        assertEquals(5, state.trinkets().size());
        assertEquals(6, state.trinketSlots());
        assertEquals(1, stolenEvents());
    }

    @Test
    void volRecalculeLesBonusDeSet() {
        setUpThief(1.0);
        engine.equip(plainTrinket("a", cardistry.items.Tag.EPFL));
        engine.equip(plainTrinket("b", cardistry.items.Tag.EPFL));
        assertFalse(state.activeSetTiers().isEmpty());
        endTurn();
        assertTrue(state.activeSetTiers().isEmpty());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.SetTierDeactivated));
    }

    // --- Vol de trinket : rendu à la mort du voleur ---

    private static final MoveEffect SURE_STEAL = new MoveEffect.StealTrinket(1.0, 8);

    /**
     * Voleur de 100 PV suivi d'un 2e adversaire (pas le dernier combat : il y a une récompense) ;
     * main : un sort qui tue en un coup ; la récompense propose {@code loot}.
     */
    private void setUpThiefFight(EnemyBehavior behavior, List<cardistry.items.Collectible> loot) {
        EventBus bus = new EventBus();
        bus.subscribe(events::add);
        enemy = Enemy.builder("voleur", "Voleur", NEUTRAL, EnemyRank.COMMON).hp(100).behavior(behavior).build();
        Enemy next = Enemy.builder("suivant", "Suivant", NEUTRAL, EnemyRank.COMMON).hp(100)
                .behavior(always(MoveEffect.Hit.of(1))).build();
        state = new GameState(new Player(100), List.of(enemy, next), new TerrainCard("Forêt", NATURE, cardistry.items.Rarity.COMMON),
                List.of(new SpellCard("Fatal", FIRE, 1000, cardistry.items.Rarity.COMMON)), new Stats());
        engine = new CombatEngine(state, new CardPool(List.of(new TerrainCard("Pioche", FIRE, cardistry.items.Rarity.COMMON))), new Rng(seed), bus,
                new LootTable(() -> loot));
    }

    private void setUpThiefFight() {
        setUpThiefFight(always(SURE_STEAL), List.of());
    }

    /** Joue le sort fatal (premier de la main). */
    private void killThief() {
        engine.submit(new Action.PlayCard(0));
        assertFalse(enemy.isAlive());
    }

    private List<cardistry.items.Trinket> returnedEvents() {
        return events.stream().filter(e -> e instanceof GameEvent.TrinketReturned)
                .map(e -> ((GameEvent.TrinketReturned) e).trinket()).toList();
    }

    private int indexOf(Class<? extends GameEvent> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void leTrinketVoleRevientALaMortDuVoleurAvantLaMort() {
        setUpThiefFight();
        equipPlain(3);
        endTurn();
        cardistry.items.Trinket stolen = state.stolenTrinkets().get(0);

        killThief();

        assertEquals(3, state.trinkets().size());
        assertEquals(stolen, state.trinkets().get(2));
        assertTrue(state.stolenTrinkets().isEmpty());
        assertEquals(List.of(stolen), returnedEvents());
        assertTrue(indexOf(GameEvent.TrinketReturned.class) < indexOf(GameEvent.EnemyKilled.class),
                "rendu avant EnemyKilled");
    }

    @Test
    void plusieursVolsPlusieursRendusDansLOrdreDuVol() {
        setUpThiefFight();
        equipPlain(3);
        endTurn();
        endTurn();
        List<cardistry.items.Trinket> stolen = List.copyOf(state.stolenTrinkets());
        assertEquals(2, stolen.size());

        killThief();

        assertEquals(stolen, returnedEvents());
        assertEquals(stolen, state.trinkets().subList(1, 3));
    }

    @Test
    void mondeSnackRenduSoigneALaMortDuVoleur() {
        setUpThiefFight();
        engine.equip(ItemRegistry.trinket("monde-snack"));
        state.player().takeDamage(30);
        endTurn();
        assertEquals("monde-snack", state.stolenTrinkets().get(0).id());
        assertEquals(70, state.player().hp(), DELTA);

        killThief();

        assertEquals(80, state.player().hp(), DELTA);
    }

    @Test
    void unTrinketRenduRallumeLeSet() {
        setUpThiefFight();
        engine.equip(plainTrinket("a", cardistry.items.Tag.EPFL));
        engine.equip(plainTrinket("b", cardistry.items.Tag.EPFL));
        endTurn();
        assertTrue(state.activeSetTiers().isEmpty());
        events.clear();

        killThief();

        assertFalse(state.activeSetTiers().isEmpty());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.SetTierActivated));
    }

    @Test
    void unTrinketRenduNEstPasProposeEnRecompense() {
        cardistry.items.Trinket x = plainTrinket("x");
        cardistry.items.Trinket y = plainTrinket("y");
        setUpThiefFight(always(SURE_STEAL), List.of(x, y));
        engine.equip(x);
        endTurn();
        assertEquals(List.of(x), state.stolenTrinkets());

        killThief();

        assertTrue(engine.hasPendingReward());
        assertFalse(engine.pendingReward().contains(x));
        assertTrue(engine.pendingReward().contains(y));
    }

    @Test
    void surUneDefaiteLeTrinketResteVole() {
        EnemyMove steal = EnemyMove.of("vol", "Vol", "Vol", SURE_STEAL);
        EnemyMove kill = EnemyMove.of("coup", "Coup", "Coup", MoveEffect.Hit.of(1000));
        setUpThiefFight(self -> self.actions() == 0 ? steal : kill, List.of());
        equipPlain(3);
        endTurn();
        assertEquals(1, state.stolenTrinkets().size());

        engine.submit(new Action.RollCigarette());

        assertTrue(state.isLost());
        assertEquals(1, state.stolenTrinkets().size());
        assertTrue(returnedEvents().isEmpty());
    }

    @Test
    void sansVolRienNEstRenduALaMort() {
        setUpThiefFight(always(new MoveEffect.StealTrinket(0.0, 8)), List.of());
        equipPlain(3);
        List<cardistry.items.Trinket> before = List.copyOf(state.trinkets());
        endTurn();

        killThief();

        assertTrue(returnedEvents().isEmpty());
        assertEquals(before, state.trinkets());
    }
}
