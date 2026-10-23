package net.aste.aeroworks;

import net.aste.aeroworks.cloud.CloudDensity;
import net.aste.aeroworks.cloud.FogSampleClock;
import net.aste.aeroworks.cloud.FrameEffectQueue;
import java.util.ArrayList;
import java.util.List;
import net.aste.aeroworks.cloud.PsrdNoise;

/** Headless regressions for occupancy, animation and fog composition. */
public final class CloudFogTest {
    public static void main(String[] args) {
        sampleBudget();
        heightRange();
        noisePeriods();
        layerBounds();
        emptySpace();
        layerGaps();
        animationAndScroll();
        frameIndependentTransitions();
        sensitivity();
        earlierEdge();
        grayColor();
        deferredLayerOrder();
        oncePerFrame();
        abandonedFrame();
        failedFogStillDrawsEffects();
        System.out.println("Cloud fog: 15 regression checks passed");
    }

    private static void sampleBudget() {
        var clock = new FogSampleClock();
        int samples = 0;
        for (int frame = 0; frame < 1200; frame++) {
            double time = frame / 6.0; // Ten seconds at 120 FPS.
            if (clock.shouldSample(time)) { samples++; clock.record(-1, 0); }
            require(!clock.shouldSample(time), "Repeated viewport events reuse density");
        }
        require(samples <= 6, "Stable clear space uses at most six samples in ten seconds");
        clock.reset();
        require(clock.shouldSample(0), "Initial query");
        clock.record(-.5f, 0);
        require(!clock.shouldSample(19), "Initial plateau waits one second");
        require(clock.shouldSample(20), "Bounded initial delay");
        clock.record(-.05f, .3f);
        require(!clock.shouldSample(23.99), "Sharp change still obeys five-Hz cap");
        require(clock.shouldSample(24), "Sharp density change schedules a fast follow-up");
        clock.record(-.049f, .301f);
        require(!clock.shouldSample(28), "Stable result backs off");
        require(clock.shouldSample(32), "Backoff remains bounded");
        clock.record(.5f, 1);
        require(clock.shouldSample(36), "Entering dense fog gets one fast confirmation");
        clock.record(1, 1);
        require(!clock.shouldSample(40), "Fully fogged camera does not keep sampling at five Hz");
        require(clock.shouldSample(56), "Dense cloud checks again after one second");
        clock.record(1, 1);
        require(!clock.shouldSample(95), "Stable dense fog backs off to two seconds");
        require(clock.shouldSample(96), "Dense cloud still checks for exit");
        clock.record(-.5f, 0);
        require(clock.shouldSample(100), "Sharp exit also schedules a fast follow-up");
        require(clock.shouldSample(0), "Time rollback refreshes");
        clock.reset();
        require(clock.shouldSample(0), "World/reload reset refreshes");
    }
    private static void heightRange() {
        require(!CloudDensity.withinHeightRange(127.99, 192, -8, 32), "Skip below lowest negative layer offset");
        require(CloudDensity.withinHeightRange(128, 192, -8, 32), "Lower layer boundary included");
        require(CloudDensity.withinHeightRange(192, 192, -8, 32), "Cloud base is not a universal lower bound");
        require(CloudDensity.withinHeightRange(448, 192, -8, 32), "Upper bound conservative and inclusive");
        require(!CloudDensity.withinHeightRange(448.01, 192, -8, 32), "Skip above all possible layers");
        require(!CloudDensity.withinHeightRange(200, 192, Integer.MAX_VALUE, Integer.MIN_VALUE), "All empty layers skip noise");
        require(CloudDensity.withinHeightRange(200, 192, Integer.MIN_VALUE, Integer.MAX_VALUE), "Unknown types never hide clouds");
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
    private static void sensitivity() {
        near(0, CloudDensity.strength(-.05f, .1f), 0, "Default threshold keeps a clear cloud fringe");
        near(.5f, CloudDensity.strength(-.05f + .1f, .1f), .0001f, "Density offset activates the cloud fringe");
        require(CloudDensity.strength(.03f, .05f) > CloudDensity.strength(.03f, .15f), "Smaller boundary width responds earlier");
    }
    private static void earlierEdge() {
        near(0, CloudDensity.edgeStrength(-.15f, .15f, .15f), 0, "Outside expanded edge");
        require(CloudDensity.edgeStrength(-.075f, .15f, .15f) > 0, "Fog starts in thin cloud fringe");
        near(1, CloudDensity.edgeStrength(.15f, .15f, .15f), 0, "Original full-density threshold unchanged");
        near(1, CloudDensity.edgeStrength(1, .15f, .15f), 0, "Dense cloud strength unchanged");
        near(CloudDensity.strength(.05f, .15f), CloudDensity.edgeStrength(.05f, .15f, 0), .00001f, "Zero margin restores old edge");
        near(0, CloudDensity.edgeStrength(-10000, .15f, .15f), 0, "No fog outside vertical layers");
    }
    private static void grayColor() {
        near(.9f, CloudDensity.gray(1,1,1,0,.9f,.65f), .00001f, "Light gray fringe");
        near(.65f, CloudDensity.gray(1,1,1,1,.9f,.65f), .00001f, "Darker dense fog");
        require(CloudDensity.gray(1,1,1,.8f,.9f,.65f) < CloudDensity.gray(1,1,1,.2f,.9f,.65f), "More fog shades gray darker");
        near(.13f, CloudDensity.gray(.2f,.2f,.2f,1,.9f,.65f), .00001f, "Night lighting retained");
    }
    private static void deferredLayerOrder() {
        var queue = new FrameEffectQueue();
        var draws = new ArrayList<String>();
        queue.begin();
        draws.add("opaque terrain");
        queue.submit(true, () -> draws.add("solid particles"));
        draws.add("late terrain and sublevels");
        queue.submit(true, () -> draws.add("translucent particles"));
        queue.submit(true, () -> draws.add("rain and snow"));
        queue.finish(() -> draws.add("completed world fog"));
        require(draws.equals(List.of("opaque terrain", "late terrain and sublevels", "completed world fog",
                "solid particles", "translucent particles", "rain and snow")), "Fog must include late terrain and precede all effects");
    }
    private static void oncePerFrame() {
        var queue = new FrameEffectQueue();
        int[] draws = new int[3];
        queue.begin();
        queue.submit(false, () -> draws[0]++);
        queue.submit(true, () -> draws[1]++);
        queue.finish(() -> draws[2]++);
        queue.finish(() -> draws[2]++);
        require(draws[0] == 1 && draws[1] == 1 && draws[2] == 1, "No missing or duplicated draws");
        queue.submit(true, () -> draws[1]++);
        require(draws[1] == 2, "An effect submitted after the flush still draws immediately");
    }
    private static void abandonedFrame() {
        var queue = new FrameEffectQueue();
        int[] draws = new int[1];
        queue.begin();
        queue.submit(true, () -> draws[0]++);
        queue.begin();
        queue.submit(true, () -> draws[0] += 10);
        queue.finish(() -> {});
        require(draws[0] == 10, "Abandoned frame cannot replay stale camera effects");
    }
    private static void failedFogStillDrawsEffects() {
        var queue = new FrameEffectQueue();
        int[] draws = new int[1];
        queue.begin();
        queue.submit(true, () -> draws[0]++);
        try { queue.finish(() -> { throw new IllegalStateException("test"); }); }
        catch (IllegalStateException expected) { }
        require(draws[0] == 1, "Effects still flush if world fog fails");
    }
    private static void near(float expected, float actual, float tolerance, String message) {
        require(Float.isFinite(actual) && Math.abs(expected - actual) <= tolerance, message + ": " + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
