package lkd.namsic.coredefender.wave;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

public final class WaveProjectiles {
    private static final String HOSTILE = "cd_wave_projectile";
    private static final String DAMAGE_SCALE = "cd_projectile_damage_scale";

    private WaveProjectiles() {
    }

    public static void rememberOwner(Projectile projectile) {
        Entity owner = projectile.getOwner();
        if (!projectile.getPersistentData().getBoolean(HOSTILE) && WaveMobs.isWaveMob(owner)) {
            projectile.getPersistentData().putBoolean(HOSTILE, true);
            projectile.getPersistentData().putDouble(DAMAGE_SCALE, WaveMobs.damageScale(owner));
        }
    }

    public static boolean isHostile(Projectile projectile) {
        rememberOwner(projectile);
        return projectile.getPersistentData().getBoolean(HOSTILE);
    }

    public static double damageScale(Projectile projectile) {
        return isHostile(projectile) ? projectile.getPersistentData().getDouble(DAMAGE_SCALE) : 1;
    }
}
