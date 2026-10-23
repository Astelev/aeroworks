package net.aste.aeroworks.wind;

import org.joml.Vector3d;
import java.util.Objects;

/** Central sampling boundary; the default field reads server-published weather snapshots only. */
public final class WindFields {
    private static final WindField DEFAULT = (sample, result) -> {
        var config = WindSettings.current();
        if (config.dynamic()) WindWorlds.sample(sample, config.field(), result);
        else result.set(config.constantX(), 0, config.constantZ());
    };
    private static volatile WindField provider = DEFAULT;
    private WindFields() {}
    public static void setProvider(WindField field) { provider = Objects.requireNonNull(field); }
    public static void resetProvider() { provider = DEFAULT; }
    static boolean usesDefault() { return provider == DEFAULT; }
    public static Vector3d sample(WindSample sample, Vector3d result) {
        result.zero();
        if (WindSettings.current().enabled()) provider.sample(sample, result);
        result.y = 0; // This version models horizontal wind only.
        if (!Double.isFinite(result.x) || !Double.isFinite(result.z)) result.zero();
        return result;
    }
}
