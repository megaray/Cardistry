package cardistry.ui.lanterna;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cardistry.core.CombatEngine;
import cardistry.core.GameState;
import cardistry.core.Rng;
import cardistry.core.event.EventBus;
import cardistry.items.ItemRegistry;
import cardistry.items.Rarity;
import cardistry.items.Trinket;
import cardistry.model.card.CardPool;
import org.junit.jupiter.api.Test;

/** Inventaire plein vu par l'interface : un trinket qui apporte sa place n'est jamais « plein ». */
class ItemScreensTest {

    private final GameState state = GameState.newGame();
    private final CombatEngine engine = new CombatEngine(state, new CardPool(), new Rng(1), new EventBus());

    private static Trinket plain(String id) {
        return Trinket.builder(id, id, Rarity.COMMON).build();
    }

    private void fillTrinkets() {
        for (int i = 0; state.trinkets().size() < state.trinketSlots(); i++) {
            engine.equip(plain("t" + i));
        }
    }

    @Test
    void barrePleineLeDiplomeNEstPasPlein() {
        fillTrinkets();

        assertFalse(ItemScreens.isFull(state, ItemRegistry.trinket("diplome-epfl")));
    }

    @Test
    void barrePleineUnTrinketNormalEstPlein() {
        fillTrinkets();

        assertTrue(ItemScreens.isFull(state, plain("x")));
    }

    @Test
    void objetsPleinsUnObjetEstPlein() {
        while (state.items().size() < state.itemSlots()) {
            engine.give(ItemRegistry.item("mate"));
        }

        assertTrue(ItemScreens.isFull(state, ItemRegistry.item("mate")));
    }
}
