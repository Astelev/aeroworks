package net.aste.aeroworks.wind;

import org.joml.Vector3d;
import java.util.Objects;

/** Central sampling boundary; the default field reads server-published weather snapshots only. */
public final class WindFields {
    private static final WindField DEFAULT = (sample, result) -> {
        var config = WindSettings.current();
        if (config.dynamic()) WindWorlds.sample(sample, config.field(), result, null);
        else result.set(config.constantX(), 0, config.constantZ());
    };
    private static volatile WindField provider = DEFAULT;
    private WindFields() {}
    public static void setProvider(WindField field) { provider = Objects.requireNonNull(field); }
    public static void resetProvider() { provider = DEFAULT; }
    static boolean usesDefault() { return provider == DEFAULT; }
    public static Vector3d sample(WindSample sample, Vector3d result) {
        return sampleForStep(sample,result,null);
    }
    public static Vector3d sampleForStep(WindSample sample, Vector3d result, Vector3d torque) {
        result.zero();
        if(torque!=null) torque.zero();
        var config=WindSettings.current();
        var field=provider;
        if (config.enabled()) {
            if(field==DEFAULT && config.dynamic()) WindWorlds.sample(sample,config.field(),result,torque);
            else field.sample(sample,result);
        }
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z)) result.zero();
        if(torque!=null && (!Double.isFinite(torque.x)||!Double.isFinite(torque.y)||!Double.isFinite(torque.z))) torque.zero();
        return result;
    }
}
