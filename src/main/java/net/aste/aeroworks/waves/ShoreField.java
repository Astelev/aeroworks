package net.aste.aeroworks.waves;

import java.util.Arrays;

/** Conservative distance from dry/shallow/unloaded cells or a different water level. */
public final class ShoreField {
    public static final float DRY = -100000;
    private ShoreField() {}
    public static float[] build(float[] heights, int size, float spacing, float fade) {
        if (heights.length != size * size) throw new IllegalArgumentException("Grid size");
        float[] distance = new float[heights.length];
        Arrays.fill(distance, size * spacing);
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int i = z * size + x;
            if (x == 0 || z == 0 || x == size - 1 || z == size - 1 || heights[i] == DRY
                    || heights[i] != heights[i - 1] || heights[i] != heights[i + 1]
                    || heights[i] != heights[i - size] || heights[i] != heights[i + size]) distance[i] = 0;
        }
        // Two chamfer passes. The safety margin keeps interpolation away from the shoreline.
        for (int z = 1; z < size; z++) for (int x = 1; x < size - 1; x++) {
            int i = z * size + x;
            distance[i] = Math.min(distance[i], Math.min(distance[i - 1] + spacing,
                    Math.min(distance[i - size] + spacing, Math.min(distance[i - size - 1], distance[i - size + 1]) + spacing * 1.414214f)));
        }
        for (int z = size - 2; z >= 0; z--) for (int x = size - 2; x > 0; x--) {
            int i = z * size + x;
            distance[i] = Math.min(distance[i], Math.min(distance[i + 1] + spacing,
                    Math.min(distance[i + size] + spacing, Math.min(distance[i + size - 1], distance[i + size + 1]) + spacing * 1.414214f)));
        }
        float[] result = new float[size * size * 2];
        for (int i = 0; i < heights.length; i++) {
            result[i * 2] = heights[i];
            result[i * 2 + 1] = heights[i] == DRY ? 0 : (float) WaveMath.smooth((distance[i] - spacing * 2) / fade);
        }
        return result;
    }
}
