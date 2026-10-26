package lkd.namsic.coredefender.wall;

import net.minecraft.world.Difficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WallDamageTest {
    @Test
    void usesVanillaDifficultyScalingAndRoundsOnlyTheFinalDamage() {
        assertEquals(0, WallDamage.scale(3, Difficulty.PEACEFUL));
        assertEquals(3, WallDamage.scale(3, Difficulty.EASY));
        assertEquals(3, WallDamage.scale(3, Difficulty.NORMAL));
        assertEquals(5, WallDamage.scale(3, Difficulty.HARD));
        assertEquals(5, WallDamage.scale(8, Difficulty.EASY));
        assertEquals(8, WallDamage.scale(8, Difficulty.NORMAL));
        assertEquals(12, WallDamage.scale(8, Difficulty.HARD));
        assertEquals(2, WallDamage.scale(2.4, Difficulty.EASY));
        assertEquals(4, WallDamage.scale(2.4, Difficulty.HARD));
        assertEquals(1, WallDamage.scale(1, Difficulty.EASY));
        assertEquals(0, WallDamage.scale(0, Difficulty.HARD));
    }
}
