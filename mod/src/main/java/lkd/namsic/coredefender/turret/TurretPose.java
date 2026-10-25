package lkd.namsic.coredefender.turret;

import net.minecraft.world.phys.Vec3;

/** 렌더러의 로컬 화살 끝과 서버 발사 위치에 공통으로 사용하는 치수. */
public final class TurretPose {
    private TurretPose() {}
    public static final float ARROW_HEIGHT = .1F;
    public static final float ARROW_TIP = .65F;

    public static Vec3 muzzleOffset(float yaw, float pitch) {
        return Vec3.directionFromRotation(pitch, yaw).scale(ARROW_TIP)
                .add(Vec3.directionFromRotation(pitch - 90, yaw).scale(ARROW_HEIGHT));
    }
}
