package cardistry.model.enemy;

import static cardistry.model.enemy.EnemyRegistry.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Rng;
import cardistry.model.Element;
import cardistry.model.Enemy;
import cardistry.model.enemy.MoveEffect.Hit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Valeurs de chaque adversaire (niveau 1 et scaling) et enchaînement de ses attaques. */
class EnemyRegistryTest {

    private static final double DELTA = 1e-9;

    /** Les {@code count} prochaines attaques (choisies puis notées comme exécutées). */
    private static List<EnemyMove> moves(Enemy enemy, int count) {
        List<EnemyMove> moves = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            EnemyMove move = enemy.chooseIntent();
            moves.add(move);
            enemy.recordAction(move);
        }
        return moves;
    }

    private static double damage(EnemyMove move) {
        return ((Hit) move.effects().get(0)).damage();
    }

    private static List<String> ids(List<EnemyMove> moves) {
        return moves.stream().map(EnemyMove::id).toList();
    }

    @Test
    void chaqueAdversaireExisteAuxTroisNiveaux() {
        for (String id : EnemyRegistry.ids()) {
            for (int level = 1; level <= 3; level++) {
                Enemy enemy = create(id, level);
                assertEquals(id, enemy.id());
                assertEquals(enemy.maxHp(), enemy.hp(), DELTA);
                assertTrue(enemy.chooseIntent() != null);
            }
        }
    }

    @Test
    void scalingParNiveau() {
        assertEquals(28, create(CADDIE, 1).maxHp(), DELTA);
        assertEquals(Math.round(28 * 1.4), create(CADDIE, 2).maxHp(), DELTA);
        assertEquals(Math.round(28 * 1.9), create(CADDIE, 3).maxHp(), DELTA);
        assertEquals(1.0, create(CADDIE, 1).damageScale(), DELTA);
        assertEquals(1.25, create(CADDIE, 2).damageScale(), DELTA);
        assertEquals(1.5, create(CADDIE, 3).damageScale(), DELTA);
    }

    /** FOR, INT, ESP, DEF, RES, CHA de {@code enemy}. */
    private static List<Integer> stats(Enemy enemy) {
        return cardistry.core.stats.StatType.characterStats().stream().map(t -> (int) enemy.stat(t)).toList();
    }

    @Test
    void statsAuNiveau1() {
        assertEquals(List.of(1, 0, 0, 2, 2, 0), stats(create(CADDIE, 1)));
        assertEquals(List.of(0, 0, 0, 0, 1, 3), stats(create(CHOOMAH, 1)));
        assertEquals(List.of(0, 0, 0, 0, 0, 4), stats(create(CHOOMAH_PRESSE, 1)));
        assertEquals(List.of(2, 0, 0, 1, 1, 1), stats(create(CHOOMAH_COSTAUD, 1)));
        assertEquals(List.of(1, 2, 0, 0, 3, 1), stats(create(CAME, 1)));
        assertEquals(List.of(0, 1, 1, 1, 2, 2), stats(create(PARESSEUX, 1)));
        assertEquals(List.of(0, 0, 0, 0, 0, 0), stats(create(CLARENCE, 1)));
        assertEquals(List.of(0, 0, 0, 1, 4, 0), stats(create(SEAU, 1)));
        assertEquals(List.of(0, 1, 3, 0, 2, 0), stats(create(GOUROU, 1)));
        assertEquals(List.of(0, 4, 0, 1, 1, 0), stats(create(NG, 1)));
        assertEquals(List.of(1, 2, 0, 2, 1, 1), stats(create(HYPERCUBE, 1)));
        assertEquals(List.of(1, 0, 0, 0, 1, 3), stats(create(GOBELIN, 1)));
        assertEquals(List.of(2, 4, 2, 1, 3, 1), stats(create(VAFI, 1)));
        assertEquals(List.of(1, 0, 0, 4, 5, 0), stats(create(PENEU, 1)));
        assertEquals(List.of(4, 0, 1, 2, 0, 0), stats(create(TREX, 1)));
        assertEquals(List.of(1, 3, 0, 0, 0, 2), stats(create(ETUDIANT, 1)));
        assertEquals(List.of(2, 3, 2, 1, 3, 2), stats(create(ENGEANCE, 1)));
    }

    @Test
    void plusUnForEtUneDefParNiveauSaufLesBoss() {
        assertEquals(List.of(2, 0, 0, 3, 2, 0), stats(create(CADDIE, 2)));
        assertEquals(List.of(3, 0, 0, 4, 2, 0), stats(create(CADDIE, 3)));
        assertEquals(List.of(3, 1, 1, 2, 3, 1), stats(create(bossId(1), 1)));
        assertEquals(List.of(4, 4, 2, 3, 4, 2), stats(create(bossId(2), 2)));
        assertEquals(List.of(5, 3, 3, 4, 5, 3), stats(create(bossId(3), 3)));
    }

    @Test
    void chaqueAdversaireADesStats() {
        for (String id : EnemyRegistry.ids()) {
            assertEquals(6, baseStats(id).length, id);
        }
    }

    @Test
    void bossPvFixesDegatsSelonLeNiveau() {
        // Niveaux 1 et 2 (Nortin, Zo le StreMon) : voir les tests nortin… et zo… ci-dessous.
        assertEquals(280, create(bossId(3), 3).maxHp(), DELTA);
        assertEquals(1.5, create(bossId(3), 3).damageScale(), DELTA);
        Enemy boss = create(bossId(3), 3);
        assertEquals(EnemyRank.BOSS, boss.rank());
        assertEquals(List.of("coup", "coup", "coup", "grosse-attaque", "coup"), ids(moves(boss, 5)));
    }

    @Test
    void nortinEstLeBossDuNiveauUn() {
        Enemy nortin = create(bossId(1), 1);
        assertEquals("Nortin", nortin.name());
        assertEquals(Element.NEUTRAL, nortin.element());
        assertEquals(EnemyRank.BOSS, nortin.rank());
        assertEquals(160, nortin.maxHp(), DELTA);
        assertEquals("nortin", nortin.spriteId());
    }

    @Test
    void leBossDuNiveauTroisGardeLeSpriteCommun() {
        assertEquals("boss", create(bossId(3), 1).spriteId());
    }

    // --- Zo le StreMon (boss du niveau 2) ---

    @Test
    void zoEstLeBossDuNiveauDeux() {
        Enemy zo = create(bossId(2), 2);
        assertEquals("Zo le StreMon", zo.name());
        assertEquals(Element.ALGEBRAIC, zo.element());
        assertEquals(EnemyRank.BOSS, zo.rank());
        assertEquals(250, zo.maxHp(), DELTA); // PV fixes, comme tous les boss
        assertEquals(1.25, zo.damageScale(), DELTA);
        assertEquals("zo", zo.spriteId());
    }

    @Test
    void zoRepeteUnCycleDeSixTours() {
        List<String> cycle = List.of("coup-de-teub", "coup-de-teub", "ptit-verre", "coup-de-teub", "petite-clope",
                "capoeira");
        List<String> twice = new ArrayList<>(cycle);
        twice.addAll(cycle);
        assertEquals(twice, ids(moves(create(bossId(2), 2), 12)));
    }

    @Test
    void zoChiffresDeSesAttaques() {
        List<EnemyMove> moves = moves(create(bossId(2), 2), 6);
        assertEquals(List.of(Hit.of(8)), moves.get(0).effects());                              // Coup de teub
        assertEquals(List.of(Hit.of(5), new MoveEffect.Drink(1)), moves.get(2).effects());     // P'tit verre
        assertEquals(List.of(new MoveEffect.HealSelf(10)), moves.get(4).effects());            // P'tite clope
        assertEquals(List.of(Hit.of(12)), moves.get(5).effects());                             // Capoeira
    }

    @Test
    void nortinRepeteUnCycleDeQuatreTours() {
        assertEquals(List.of("bitchslap", "dodge", "vol-trinket", "kicknuts",
                        "bitchslap", "dodge", "vol-trinket", "kicknuts"),
                ids(moves(create(bossId(1), 1), 8)));
    }

    @Test
    void nortinDegatsDeSesCoups() {
        List<EnemyMove> moves = moves(create(bossId(1), 1), 4);
        assertEquals(8, damage(moves.get(0)), DELTA);  // Bitch slap
        assertEquals(new MoveEffect.StealTrinket(0.2, 8), moves.get(2).effects().get(0)); // Vol de trinket
        assertEquals(16, damage(moves.get(3)), DELTA); // Hit in the nuts
    }

    @Test
    void nortinEsquiveSeptantepourcentSinonBitchSlap() {
        EnemyMove dodge = moves(create(bossId(1), 1), 2).get(1);
        assertEquals(new MoveEffect.Gamble(0.7, new MoveEffect.Dodge(), Hit.of(8)), dodge.effects().get(0));
    }

    @Test
    void elementsEtRangs() {
        assertEquals(Element.NEUTRAL, create(CADDIE, 1).element());
        assertEquals(Element.NATURE, create(CHOOMAH, 1).element());
        assertEquals(Element.FIRE, create(CAME, 1).element());
        assertEquals(Element.NATURE, create(PARESSEUX, 1).element());
        assertEquals(Element.NEUTRAL, create(CLARENCE, 1).element());
        assertEquals(Element.WATER, create(SEAU, 1).element());
        assertEquals(Element.FIRE, create(VAFI, 1).element());
        assertEquals(Element.NEUTRAL, create(PENEU, 1).element());
        assertEquals(Element.NATURE, create(TREX, 1).element());
        assertEquals(Element.WATER, create(ETUDIANT, 1).element());
        for (String id : ELITES) {
            assertEquals(EnemyRank.ELITE, create(id, 1).rank());
        }
        for (String id : List.of(CADDIE, CHOOMAH, CHOOMAH_PRESSE, CHOOMAH_COSTAUD, CAME, PARESSEUX, CLARENCE, SEAU)) {
            assertEquals(EnemyRank.COMMON, create(id, 1).rank());
        }
    }

    @Test
    void caddieChargeTousLesTroisTours() {
        List<EnemyMove> moves = moves(create(CADDIE, 1), 6);
        assertEquals(List.of(6.0, 6.0, 12.0, 6.0, 6.0, 12.0), moves.stream().map(EnemyRegistryTest::damage).toList());
        assertEquals("Charge de parking", moves.get(2).name());
    }

    @Test
    void choomahCrieEtVole() {
        Enemy choomah = create(CHOOMAH, 1);
        assertEquals(Enemy.Voice.SCREAM, choomah.voice());
        assertEquals(22, choomah.maxHp(), DELTA);
        List<EnemyMove> moves = moves(choomah, 3);
        assertEquals(List.of("coup", "coup", "vol-a-la-tire"), ids(moves));
        assertEquals(4, damage(moves.get(0)), DELTA);
        assertEquals(new MoveEffect.Pickpocket(5, 1, 4), moves.get(2).effects().get(0));
        for (EnemyMove move : moves) {
            assertTrue(move.name().matches("[OAUoau]+"), move.name());
            assertTrue(move.description().matches("[OAUoau]+"), move.description());
        }
        assertEquals(CHOOMAH_PRESSE, create(CHOOMAH_PRESSE, 1).spriteId());
        assertEquals(CHOOMAH_COSTAUD, create(CHOOMAH_COSTAUD, 1).spriteId());
    }

    @Test
    void variantesDeChoomah() {
        Enemy presse = create(CHOOMAH_PRESSE, 1);
        assertEquals(18, presse.maxHp(), DELTA);
        assertEquals(List.of("coup", "vol-a-la-tire", "coup", "vol-a-la-tire"), ids(moves(presse, 4)));
        Enemy costaud = create(CHOOMAH_COSTAUD, 1);
        assertEquals(30, costaud.maxHp(), DELTA);
        assertEquals(6, damage(moves(costaud, 1).get(0)), DELTA);
    }

    @Test
    void cameTaxeUnTourSurDeux() {
        Enemy came = create(CAME, 1);
        assertEquals(25, came.maxHp(), DELTA);
        List<EnemyMove> moves = moves(came, 4);
        assertEquals(List.of("coup", "taxe", "coup", "taxe"), ids(moves));
        assertEquals(new MoveEffect.Tax(1, 5, 2), moves.get(1).effects().get(0));
    }

    @Test
    void paresseuxFaitPeuDeDegatsEtSeBalance() {
        Enemy sloth = create(PARESSEUX, 1);
        assertEquals(24, sloth.maxHp(), DELTA);
        List<EnemyMove> moves = moves(sloth, 2);
        assertEquals(3, damage(moves.get(0)), DELTA);
        assertEquals("Badoing, badoing !", moves.get(1).name());
        assertEquals(new MoveEffect.Gamble(0.5, new MoveEffect.Dodge(), Hit.of(2, 2)), moves.get(1).effects().get(0));
    }

    @Test
    void clarenceNAttaqueJamais() {
        Enemy clarence = create(CLARENCE, 1);
        assertEquals(1, clarence.maxHp(), DELTA);
        assertEquals(75, clarence.choomiesReward());
        EnemyMove move = moves(clarence, 1).get(0);
        assertEquals("Clarence te regarde.", move.description());
        assertTrue(move.effects().isEmpty());
    }

    @Test
    void seauSeRemplitPuisDeborde() {
        List<EnemyMove> moves = moves(create(SEAU, 1), 7);
        assertEquals(List.of(2.0, 3.0, 4.0, 5.0, 14.0, 2.0, 3.0), moves.stream().map(EnemyRegistryTest::damage).toList());
        assertEquals("Débordement", moves.get(4).name());
    }

    @Test
    void vafiSerieDAlgebreEtPausePetou() {
        Enemy vafi = create(VAFI, 1);
        assertEquals(55, vafi.maxHp(), DELTA);
        List<EnemyMove> moves = moves(vafi, 3);
        assertEquals(List.of("correction", "correction", "serie-d-algebre"), ids(moves));
        assertEquals("Série d'algèbre", moves.get(2).name());
        assertEquals(new MoveEffect.CurseCard("Exercice 3b"), moves.get(2).effects().get(0));

        vafi.takeDamage(30); // sous 50 %
        List<EnemyMove> low = moves(vafi, 3);
        assertEquals("pause-petou", low.get(0).id());
        assertEquals(new MoveEffect.HealSelf(12), low.get(0).effects().get(0));
        assertEquals("pause-petou", low.get(1).id());
        assertNotEquals("pause-petou", low.get(2).id()); // deux fois au plus
    }

    @Test
    void peneuRebondEtCrevaisonQuiMonte() {
        Enemy peneu = create(PENEU, 1);
        assertEquals(90, peneu.maxHp(), DELTA);
        assertEquals(0.2, peneu.reflectRatio(), DELTA);
        assertEquals(List.of(6.0, 8.0, 10.0), moves(peneu, 3).stream().map(EnemyRegistryTest::damage).toList());
    }

    @Test
    void trexRateSouvent() {
        Enemy trex = create(TREX, 1);
        assertEquals(65, trex.maxHp(), DELTA);
        assertEquals(new Hit(13, 1, 0, 0.30, 8), moves(trex, 1).get(0).effects().get(0));
    }

    @Test
    void etudiantCraqueSousLaMoitie() {
        Enemy student = create(ETUDIANT, 1);
        assertEquals(60, student.maxHp(), DELTA);
        assertEquals(new Hit(6, 1, 3, 0, 0), moves(student, 1).get(0).effects().get(0));
        student.takeDamage(31);
        EnemyMove crisis = moves(student, 1).get(0);
        assertEquals("Crise de nerfs", crisis.name());
        assertEquals(2, ((Hit) crisis.effects().get(0)).hits());
    }

    @Test
    void tirageDesCommunsSansRepetitionEtClarenceRare() {
        Rng rng = new Rng(11);
        int clarence = 0;
        int draws = 20_000;
        String previous = CADDIE;
        for (int i = 0; i < draws; i++) {
            String id = pickCommon(rng, previous);
            if (id.equals(CLARENCE)) {
                clarence++;
            } else if (CHOOMAH_VARIANTS.contains(previous)) {
                assertTrue(!CHOOMAH_VARIANTS.contains(id), "deux Choomah de suite");
            } else {
                assertNotEquals(previous, id);
            }
            previous = id;
        }
        double ratio = (double) clarence / draws;
        assertTrue(ratio > 0.02 && ratio < 0.04, "Clarence : " + ratio);
    }

    @Test
    void tirageDesElites() {
        Rng rng = new Rng(5);
        for (int i = 0; i < 200; i++) {
            String id = pickElite(rng, VAFI);
            assertTrue(ELITES.contains(id));
            assertNotEquals(VAFI, id);
        }
        assertInstanceOf(String.class, pickElite(rng, null));
    }
}
