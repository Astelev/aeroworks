package net.aste.aeroworks.wind;

import net.aste.aeroworks.Config;
import org.joml.Vector3d;
import java.util.Objects;

/** Central sampling boundary: future biome/height/cloud fields replace only the provider. */
public final class WindFields {
    private static final WindField CONSTANT = (sample, result) -> result.set(
            Config.WIND_X.get(), Config.WIND_Y.get(), Config.WIND_Z.get());
    private static volatile WindField provider = CONSTANT;

    private WindFields() {}

    public static void setProvider(WindField field) { provider = Objects.requireNonNull(field); }
    public static void resetProvider() { provider = CONSTANT; }

    public static Vector3d sample(WindSample sample, Vector3d result) {
        result.zero();
        if (Config.WIND_ENABLED.get()) provider.sample(sample, result);
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z)) result.zero();
        return result;
    }
}
