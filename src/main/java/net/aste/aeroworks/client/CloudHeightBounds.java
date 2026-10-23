package net.aste.aeroworks.client;

import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.aste.aeroworks.cloud.CloudDensity;

/** Conservative union of every cloud type, cached independently of the camera's region. */
final class CloudHeightBounds {
    private double checked = Double.NaN;
    private int min, max;

    void reset() { checked = Double.NaN; }

    boolean contains(CloudManager<?> manager, double time, double worldY) {
        if (Double.isNaN(checked) || time < checked || time - checked >= 20) {
            min = Integer.MAX_VALUE;
            max = Integer.MIN_VALUE;
            var types = manager.getIndexedCloudTypes();
            for (var type : types) {
                var noise = type.noiseConfig();
                if (noise.layerCount() == 0) continue;
                min = Math.min(min, noise.getStartHeight());
                max = Math.max(max, noise.getEndHeight());
            }
            // An unavailable type index must not hide clouds (e.g. while syncing).
            if (types.length == 0) { min = Integer.MIN_VALUE; max = Integer.MAX_VALUE; }
            checked = time;
        }
        return CloudDensity.withinHeightRange(worldY, manager.getCloudHeight(), min, max);
    }
}
