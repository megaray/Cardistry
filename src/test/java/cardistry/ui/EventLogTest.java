package cardistry.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.event.GameEvent;
import cardistry.model.Enemy;
import cardistry.model.card.CardPool;
import cardistry.model.enemy.EnemyRegistry;
import cardistry.utils.Log;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventLogTest {

    @Test
    void lesChampsSontResumes() {
        assertEquals("CardPlayed(card=Boule de feu)",
                EventLog.describe(new GameEvent.CardPlayed(CardPool.bouleDeFeu())));
        assertEquals("ChoomiesChanged(choomies=12, delta=-3)",
                EventLog.describe(new GameEvent.ChoomiesChanged(12, -3)));
        assertEquals("CardRewardOffered(options=[Boule de feu, Petite taffe])", EventLog.describe(
                new GameEvent.CardRewardOffered(List.of(CardPool.bouleDeFeu(), CardPool.petiteTaffe()))));
    }

    @Test
    void lAdversaireEstNommeAvecSesPv() {
        String id = EnemyRegistry.ids().iterator().next();
        Enemy enemy = EnemyRegistry.create(id, 1);
        assertEquals("EnemyKilled(enemy=" + enemy.name() + " (" + enemy.hp() + " PV))",
                EventLog.describe(new GameEvent.EnemyKilled(enemy)));
    }

    @Test
    void seulementEnDebug() {
        Log.Level before = Log.level();
        Log.clearMemory();
        try {
            Log.setLevel(Log.Level.INFO);
            new EventLog().accept(new GameEvent.ChoomiesChanged(1, 1));
            assertTrue(Log.recent().isEmpty());
            Log.setLevel(Log.Level.DEBUG);
            new EventLog().accept(new GameEvent.ChoomiesChanged(1, 1));
            assertEquals(Log.Level.DEBUG, Log.recent().get(0).level());
        } finally {
            Log.setLevel(before);
            Log.clearMemory();
        }
    }
}
