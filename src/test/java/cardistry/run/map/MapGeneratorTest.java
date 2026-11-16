package cardistry.run.map;

import static cardistry.run.map.RunMap.COLUMNS;
import static cardistry.run.map.RunMap.FLOORS;
import static cardistry.run.map.RunMap.REST_FLOOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.Rng;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cartes générées : structure, contraintes de types, déterminisme, proportions. */
class MapGeneratorTest {

    private static RunMap map(long seed, int level) {
        return MapGenerator.generate(new Rng(seed).fork("carte-" + level), level);
    }

    private static String describe(RunMap map) {
        StringBuilder text = new StringBuilder();
        for (MapNode node : map.nodes()) {
            text.append(node.floor()).append(node.column()).append(node.type().symbol()).append(node.next()).append(';');
        }
        return text.toString();
    }

    private static List<MapNode> parents(RunMap map, MapNode node) {
        if (node.floor() == 1) {
            return List.of();
        }
        return map.floor(node.floor() - 1).stream().filter(p -> p.next().contains(node.column())).toList();
    }

    @Test
    void memeSeedMemeCarte() {
        assertEquals(describe(map(42, 1)), describe(map(42, 1)));
        assertNotEquals(describe(map(42, 1)), describe(map(42, 2)));
        assertNotEquals(describe(map(42, 1)), describe(map(43, 1)));
    }

    @Test
    void sousFluxIndependantDuRestant() {
        Rng rng = new Rng(42);
        rng.nextInt(100); // tirages ailleurs : la carte ne change pas
        assertEquals(describe(map(42, 1)), describe(MapGenerator.generate(rng.fork("carte-1"), 1)));
    }

    @Test
    void structureSurMilleCartes() {
        for (long seed = 0; seed < 1000; seed++) {
            RunMap map = map(seed, 1 + (int) (seed % 3));
            assertEquals(1, map.floor(FLOORS).size());
            MapNode boss = map.boss();
            assertEquals(NodeType.BOSS, boss.type());
            assertEquals(MapGenerator.BOSS_COLUMN, boss.column());
            assertTrue(map.floor(1).size() >= 2, "au moins deux départs");
            for (MapNode node : map.nodes()) {
                assertTrue(node.column() >= 0 && node.column() < COLUMNS);
                if (node.floor() < FLOORS) {
                    assertFalse(node.next().isEmpty(), "impasse : " + node);
                }
                if (node.floor() > 1) {
                    assertFalse(parents(map, node).isEmpty(), "nœud inaccessible : " + node);
                }
                if (node.floor() < REST_FLOOR) {
                    for (int next : node.next()) {
                        assertTrue(Math.abs(next - node.column()) <= 1, "saut de colonne : " + node);
                    }
                }
                if (node.floor() == REST_FLOOR) {
                    assertEquals(List.of(MapGenerator.BOSS_COLUMN), node.next());
                }
            }
            for (int floor = 1; floor < REST_FLOOR; floor++) {
                for (MapNode a : map.floor(floor)) {
                    for (MapNode b : map.floor(floor)) {
                        for (int ta : a.next()) {
                            for (int tb : b.next()) {
                                boolean cross = (a.column() < b.column() && ta > tb) || (a.column() > b.column() && ta < tb);
                                assertFalse(cross, "chemins croisés, seed " + seed);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void typesParEtage() {
        for (long seed = 0; seed < 1000; seed++) {
            RunMap map = map(seed, 1);
            boolean shop = false;
            for (MapNode node : map.nodes()) {
                switch (node.floor()) {
                    case 1 -> assertEquals(NodeType.COMBAT, node.type());
                    case REST_FLOOR -> assertEquals(NodeType.REST, node.type());
                    case FLOORS -> assertEquals(NodeType.BOSS, node.type());
                    case 2, 3 -> assertTrue(node.type() != NodeType.ELITE && node.type() != NodeType.REST, node.toString());
                    case 6 -> assertNotEquals(NodeType.REST, node.type());
                    default -> {
                    }
                }
                shop |= node.type() == NodeType.SHOP;
                for (MapNode parent : parents(map, node)) {
                    assertFalse(parent.type().noRepeat() && parent.type() == node.type(),
                            "deux " + node.type() + " de suite, seed " + seed);
                }
            }
            assertTrue(shop, "pas de shop, seed " + seed);
            assertTrue(hasPathWithoutElite(map), "élite obligatoire, seed " + seed);
        }
    }

    private static boolean hasPathWithoutElite(RunMap map) {
        Set<MapNode> reachable = new HashSet<>(map.floor(1));
        for (int floor = 1; floor < FLOORS; floor++) {
            Set<MapNode> next = new HashSet<>();
            for (MapNode node : reachable) {
                if (node.type() != NodeType.ELITE) {
                    next.addAll(map.choicesAfter(node));
                }
            }
            reachable = next;
        }
        return reachable.contains(map.boss());
    }

    @Test
    void proportionsDesEtages4a6() {
        Map<NodeType, Integer> counts = new EnumMap<>(NodeType.class);
        int total = 0;
        for (long seed = 0; seed < 2000; seed++) {
            RunMap map = map(seed, 2);
            for (int floor = 4; floor <= 6; floor++) {
                for (MapNode node : map.floor(floor)) {
                    counts.merge(node.type(), 1, Integer::sum);
                    total++;
                }
            }
        }
        double combat = counts.getOrDefault(NodeType.COMBAT, 0) / (double) total;
        double elite = counts.getOrDefault(NodeType.ELITE, 0) / (double) total;
        double event = counts.getOrDefault(NodeType.EVENT, 0) / (double) total;
        double altar = counts.getOrDefault(NodeType.ALTAR, 0) / (double) total;
        // Poids 45 / 15 / 22 / 22 (autel), un peu déformés par les contraintes (pas deux élites de suite…).
        assertTrue(combat > 0.32 && combat < 0.50, "combat " + combat);
        assertTrue(elite > 0.08 && elite < 0.18, "élite " + elite);
        assertTrue(event > 0.14 && event < 0.26, "événement " + event);
        assertTrue(altar > 0.10 && altar < 0.22, "autel " + altar);
    }
}
