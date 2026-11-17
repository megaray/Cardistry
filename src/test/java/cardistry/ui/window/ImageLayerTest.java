package cardistry.ui.window;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageLayerTest {

    @Test
    void hideFromRetireCeQuiDebordeSurLePanneau() {
        ImageLayer layer = new ImageLayer();
        layer.begin();
        layer.add(new ImageLayer.Placement(0, 0, 4, 4, "haut.png", null, 1f)); // lignes 0-3
        layer.add(new ImageLayer.Placement(0, 10, 4, 4, "deborde.png", null, 1f)); // lignes 10-13
        layer.add(new ImageLayer.Fill(0, 0, 7, 14 * 12, Color.RED)); // jusqu'à la ligne 12 exclue
        layer.add(new ImageLayer.Fill(0, 0, 7, 14 * 13, Color.RED)); // déborde
        layer.add(new ImageLayer.Stamp(0, 14 * 11, 14, "bas.png", null, 1f)); // centré ligne 11 : reste
        layer.add(new ImageLayer.Stamp(0, 14 * 12, 14, "bas.png", null, 1f)); // déborde
        layer.hideFrom(12);
        layer.commit();
        assertEquals(List.of("haut.png"), layer.snapshot().stream().map(ImageLayer.Placement::path).toList());
        assertEquals(2, layer.marks().size());
    }
}
