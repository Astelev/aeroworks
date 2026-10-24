package net.aste.aeroworks.wind;

import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class WindKinematics {
    private WindKinematics() {}

    /** Do not mutate Sable's shared body velocity: buoyancy and other wings still need it. */
    public static Vector3dc relativeVelocity(Vector3dc bodyVelocity, Vector3dc wind) {
        if (wind.lengthSquared() == 0.0) return bodyVelocity;
        return new Vector3d(bodyVelocity).sub(wind);
    }

    public static Vector3d worldPosition(Vector3dc center, Pose3dc localPose, Pose3dc worldPose, Vector3d result) {
        result.set(center);
        if (localPose != null) localPose.transformPosition(result);
        worldPose.transformPosition(result);
        return result;
    }
}
