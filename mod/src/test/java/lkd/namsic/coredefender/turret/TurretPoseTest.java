package lkd.namsic.coredefender.turret;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TurretPoseTest {
    @Test void projectileOriginMatchesRenderedArrowAcrossAimDirections() {
        for (int yaw = -180; yaw <= 180; yaw += 15) {
            for (int pitch = -90; pitch <= 90; pitch += 15) {
                var rendered = new Matrix4f().rotateY((float) Math.toRadians(-yaw))
                        .rotateX((float) Math.toRadians(pitch))
                        .transformPosition(new Vector3f(0, TurretPose.ARROW_HEIGHT, TurretPose.ARROW_TIP));
                var actual = TurretPose.muzzleOffset(yaw, pitch);
                assertEquals(rendered.x, actual.x, .0002);
                assertEquals(rendered.y, actual.y, .0002);
                assertEquals(rendered.z, actual.z, .0002);
            }
        }
    }
}
