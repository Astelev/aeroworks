package net.aste.aeroworks.wind;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.common.cloud.CloudType;
import dev.nonamecrackers2.simpleclouds.common.cloud.region.CloudRegion;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.minecraft.server.level.ServerLevel;

/** Server-only metadata adapter. Never invoked from the physics sampler. */
final class SimpleCloudsWind {
    private SimpleCloudsWind() {}

    static double period(CloudManager<?> manager, WindParameters p) {
        var config = manager.getCloudGenerator().getSpawnConfig().get();
        if (config.isEmpty()) return p.fallbackPeriod();
        var interval = config.getSpawnInterval();
        double ticks = ((double) interval.getMinValue() + interval.getMaxValue()) * .5;
        // CloudGenerator.tick decrements by max(1, ceil(cloudSpeed)) per game tick.
        double rate = Math.max(1, Math.ceil(manager.getCloudSpeed()));
        return Math.max(10, ticks / (20 * rate) * p.weatherPeriodMultiplier());
    }

    static WindWeather read(ServerLevel level, CloudManager<?> manager, double x, double z,
            long seed, WindParameters p) {
        CloudRegion region = null;
        CloudType type;
        double fade;
        if (manager.getCloudMode() != CloudMode.SINGLE && manager.isCloudGeneratorActive()) {
            var found = CloudRegion.calculateAt(manager.getClouds(), (float) (x / 8), (float) (z / 8));
            region = found.getLeft();
            type = region == null ? null : manager.getCloudTypeForId(region.getCloudTypeId());
            fade = 1 - found.getRight();
        } else {
            var found = manager.getCloudTypeAtWorldPos((float) x, (float) z);
            type = found.getLeft();
            fade = found.getRight();
        }
        // Noise samples x+scrollX/z+scrollZ: its visible motion is OPPOSITE the scroll delta.
        double dx = -(manager.getScrollX() - manager.getScrollX(0));
        double dz = -(manager.getScrollZ() - manager.getScrollZ(0));
        if (region != null) {
            double rx = region.getPosX() - region.getPosX(0);
            double rz = region.getPosZ() - region.getPosZ(0);
            if (rx * rx + rz * rz > 1e-10) { dx = rx; dz = rz; }
            else {
                var direction = region.getMovementDirection();
                if (direction.x * direction.x + direction.y * direction.y > 1e-10) {
                    dx = direction.x; dz = direction.y;
                }
            }
        }
        double heading = dx * dx + dz * dz > 1e-10 ? Math.atan2(dz, dx)
                : ZonalWindModel.unitHash(seed ^ 0x538babaL) * Math.PI * 2;
        long key = type == null ? 0 : ZonalWindModel.typeKey(type.id().toString());
        double rain = 0, storm = 0;
        if (type != null) {
            // Same horizontal storm fringe as CloudManager.getRainLevel; no camera-height attenuation.
            double coverage = ZonalWindModel.clamp((.7 - fade) / .1);
            if (type.weatherType().includesRain()) rain = coverage;
            if (type.weatherType().includesThunder()) {
                storm = coverage * (.5 + .5 * ZonalWindModel.clamp(type.storminess()));
            }
        }
        if (manager.shouldUseVanillaWeather() && (type == null
                || (!type.weatherType().includesRain() && !type.weatherType().includesThunder()))) {
            rain = level.getRainLevel(1);
            storm = level.getThunderLevel(1);
            // Vanilla weather changes must turn wind even with an unchanged/empty SC cloud type.
            key ^= storm > .5 ? 0x736f726dL : rain > .5 ? 0x7261696eL : 0;
        }
        return WindWeather.of(key, rain, storm, heading, seed, p.cloudDeviationDegrees());
    }
}
