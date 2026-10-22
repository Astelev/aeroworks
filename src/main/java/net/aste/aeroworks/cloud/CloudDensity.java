package net.aste.aeroworks.cloud;

/** Uses Simple Clouds' packed layer layout and shader units (one unit = eight blocks). */
public final class CloudDensity {
    private CloudDensity() {}

    public static float sample(float[] layers, float x, float y, float z,
                               float scrollX, float scrollY, float scrollZ, float outerFade) {
        float sum = 0;
        boolean valid = false;
        for (int i = 0; i + 7 < layers.length; i += 8) {
            float height = layers[i], heightOffset = layers[i + 6];
            if (y < heightOffset || y > heightOffset + height - 1) continue;
            float value = PsrdNoise.sample((x + scrollX) / layers[i + 2],
                    (y + scrollY) / layers[i + 3], (z + scrollZ) / layers[i + 4],
                    (scrollX + scrollY + scrollZ) / 5) * layers[i + 7] + layers[i + 1];
            float delta = y - heightOffset;
            value -= 1 - clamp(delta / layers[i + 5]);
            value -= 1 - clamp((height - delta) / layers[i + 5]);
            if (layers.length == 8 || value > -10) {
                sum += value;
                valid = true;
            }
        }
        return valid ? sum - 5 * (float) Math.pow(clamp(outerFade), 10) : -10000;
    }

    public static float strength(float density, float boundaryWidth) {
        float t = clamp(density / boundaryWidth);
        return t * t * (3 - 2 * t);
    }

    public static float smooth(float previous, float target, double elapsedTicks, double transitionTicks) {
        if (transitionTicks <= 0) return target;
        return previous + (target - previous) * (float) (1 - Math.exp(-Math.max(0, elapsedTicks) / transitionTicks));
    }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
}
