package net.aste.aeroworks;

import net.aste.aeroworks.cloud.CloudDensity;
import net.aste.aeroworks.cloud.PsrdNoise;

/** Headless regressions for occupancy, animation and fog composition. */
public final class CloudFogTest {
    public static void main(String[] args) {
        noisePeriods();
        layerBounds();
        emptySpace();
        layerGaps();
        animationAndScroll();
        frameIndependentTransitions();
        existingFog();
        System.out.println("Cloud fog: 7 regression checks passed");
    }

    private static float[] layer(float offset, float height, float density, float noiseScale) {
        return new float[]{height, density, 30, 10, 30, 2, offset, noiseScale};
    }
    private static float sample(float[] layers, float y, float regionFade) {
        return CloudDensity.sample(layers, 1.25f, y, 2.5f, 0, 0, 0, regionFade);
    }
    private static void noisePeriods() {
        // Reference values from independent vector evaluation of the bundled GLSL.
        near(-.1991929f, PsrdNoise.sample(-2.25f, 1.5f, -.75f, 0), .00001f, "Shader reference, static gradients");
        near(-.007954901f, PsrdNoise.sample(-2.077f, 1.891f, -.679f, .31f), .00001f, "Shader reference, animated gradients");
        near(.68518454f, PsrdNoise.sample(-1.731f, 2.6729999f, -.537f, .93f), .00001f, "Shader reference, positive density");
        for (float alpha : new float[]{0, .75f, -2}) {
            float a = PsrdNoise.sample(-3.25f, 7.5f, 1.125f, alpha);
            near(a, PsrdNoise.sample(28.75f, 7.5f, 1.125f, alpha), .0001f, "X tiling");
            near(a, PsrdNoise.sample(-3.25f, 71.5f, 1.125f, alpha), .0001f, "Y tiling");
            near(a, PsrdNoise.sample(-3.25f, 7.5f, 33.125f, alpha), .0001f, "Z tiling");
            near(a, PsrdNoise.sample(-3.25f, 7.5f, 1.125f, alpha + (float)(2 * Math.PI)), .0001f, "Gradient rotation period");
        }
    }
    private static void layerBounds() {
        float[] layer = layer(4, 10, .8f, 0);
        require(sample(layer, 8, 0) > 0, "Inside layer");
        require(sample(layer, 3, 0) < -100, "Below layer");
        require(sample(layer, 14, 0) < -100, "Above layer");
        require(sample(layer, 4, 0) < 0, "Bottom fade");
        require(sample(layer, 13, 0) < sample(layer, 8, 0), "Top fade");
    }
    private static void emptySpace() {
        require(sample(new float[0], 8, 0) < -100, "Empty cloud type");
        require(sample(layer(4, 10, -.5f, 0), 8, 0) < 0, "Hole in a layer");
        require(sample(layer(4, 10, .8f, 0), 8, 1) < 0, "Outside region");
        near(0, CloudDensity.strength(-.2f, .15f), 0, "Clear space has no fog");
        near(1, CloudDensity.strength(.3f, .15f), 0, "Dense cloud has full fog");
    }
    private static void layerGaps() {
        float[] layers = new float[16];
        System.arraycopy(layer(0, 8, .8f, 0), 0, layers, 0, 8);
        System.arraycopy(layer(20, 8, .8f, 0), 0, layers, 8, 8);
        require(sample(layers, 4, 0) > 0 && sample(layers, 24, 0) > 0, "Both layers sampled");
        require(sample(layers, 12, 0) < -100, "No fog in gap between layers");
    }
    private static void animationAndScroll() {
        float[] layers = layer(0, 32, -.5f, 1);
        float a = CloudDensity.sample(layers, 1.25f, 8, 2.5f, 0, 0, 0, 0);
        float b = CloudDensity.sample(layers, 1.25f, 8, 2.5f, 10, 0, 15, 0);
        require(Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) > .0001f, "Wind and wiggle affect occupancy");
    }
    private static void frameIndependentTransitions() {
        float once = CloudDensity.smooth(0, 1, 20, 3);
        float many = 0;
        for (int i = 0; i < 120; i++) many = CloudDensity.smooth(many, 1, 20.0 / 120, 3);
        near(once, many, .00001f, "Smoothing independent of FPS");
        near(.4f, CloudDensity.smooth(.4f, 1, 0, 3), 0, "Paused time");
        near(0, CloudDensity.smooth(1, 0, 1, 0), 0, "Instant exit setting");
    }
    private static void existingFog() {
        near(24, CloudDensity.fogEnd(512, 24, 1), 0, "Full fog visibility");
        near(512, CloudDensity.fogEnd(512, 24, 0), 0, "Outside cloud unchanged");
        near(5, CloudDensity.fogEnd(5, 24, 1), 0, "Never weaken blindness or denser fog");
        near(24, CloudDensity.fogEnd(Float.MAX_VALUE, 24, 1), 0, "Stable interpolation at large distances");
    }
    private static void near(float expected, float actual, float tolerance, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) <= tolerance, message + ": " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
