package top.ellan.mahjong.rules.riichi;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileModelTest {
    @Test
    void doraCyclesMatchRiichiRules() {
        assertEquals(TileKind.M1, TileKind.M9.dora());
        assertEquals(TileKind.EAST, TileKind.NORTH.dora());
        assertEquals(TileKind.WHITE_DRAGON, TileKind.RED_DRAGON.dora());
    }

    @Test
    void redIsAnAttributeOfSuitedFive() {
        Tile red = Tile.parse("0p");
        assertEquals(TileKind.P5, red.kind());
        assertTrue(red.red());
        assertThrows(IllegalArgumentException.class, () -> Tile.red(TileKind.EAST));
    }
}
