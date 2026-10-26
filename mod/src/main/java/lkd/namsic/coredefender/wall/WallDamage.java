package lkd.namsic.coredefender.wall;

import net.minecraft.world.Difficulty;

public final class WallDamage {
    private WallDamage() {
    }

    public static int scale(double damage, Difficulty difficulty) {
        if (damage <= 0 || difficulty == Difficulty.PEACEFUL) {
            return 0;
        }

        double adjusted = switch (difficulty) {
            case EASY -> Math.min(damage / 2 + 1, damage);
            case HARD -> damage * 1.5;
            default -> damage;
        };
        return Math.max(1, (int) Math.round(adjusted));
    }
}
