package net.aste.aeroworks;

import net.aste.aeroworks.atmosphere.AtmosphereProfile;

public final class AtmosphereTest {
    public static void main(String[] args) {
        reference(); highAltitude(); monotonic(); smoothEdges(); customProfile(); vacuum(); invalidSettings();
        System.out.println("Atmosphere: 7 density/profile checks passed");
    }
    private static void reference() {
        var p = AtmosphereProfile.DEFAULT;
        near(1, p.density(64), 1e-12, "Reference pressure");
        near(Math.exp(-1), p.density(1088), 1e-12, "One scale height");
        near(1.5, p.density(-10000), 1e-12, "Underground density capped like Sable");
    }
    private static void highAltitude() {
        var p = AtmosphereProfile.DEFAULT;
        require(p.density(300) > .7, "No old Y=300 atmosphere cutoff");
        require(p.density(1024) > .3, "Wings retain pressure at Y=1024");
        require(p.density(2048) > .1, "Thin atmosphere extends past build height");
    }
    private static void monotonic() {
        var p = AtmosphereProfile.DEFAULT;
        double last = p.density(-1024);
        for (int y = -1023; y <= 5000; y++) {
            double d = p.density(y);
            require(Double.isFinite(d) && d >= 0 && d <= last, "Density finite, nonnegative and nonincreasing at " + y);
            last = d;
        }
    }
    private static void smoothEdges() {
        var p = AtmosphereProfile.DEFAULT;
        double e = .01;
        double left = (p.density(p.fadeStart()) - p.density(p.fadeStart() - e)) / e;
        double right = (p.density(p.fadeStart() + e) - p.density(p.fadeStart())) / e;
        near(left, right, 1e-8, "Fade start has matching first derivatives");
        near(0, (p.density(p.ceiling()) - p.density(p.ceiling() - e)) / e, 1e-8, "Vacuum boundary has zero slope");
        near(p.density(300 - e), p.density(300 + e), .0001, "Continuous across old boundary");
    }
    private static void customProfile() {
        var low = new AtmosphereProfile(100, 100, 500, 700);
        var high = new AtmosphereProfile(100, 200, 1000, 1400);
        require(high.density(400) > low.density(400), "Scale height controls thinning");
        require(low.density(650) > 0 && low.density(700) == 0, "Custom vacuum height");
        require(high.density(700) > 0, "Raising the ceiling retains air above old ceiling");
    }
    private static void vacuum() {
        var p = AtmosphereProfile.DEFAULT;
        near(0, p.density(4096), 0, "Exact vacuum at ceiling");
        near(0, p.density(100000), 0, "No negative pressure above ceiling");
        near(0, p.density(Double.POSITIVE_INFINITY), 0, "Infinite altitude safe");
        near(0, p.density(Double.NaN), 0, "Invalid altitude cannot poison forces");
    }
    private static void invalidSettings() {
        for (double[] values : new double[][]{{64,0,3000,4000}, {64,-1,3000,4000},
                {64,1024,4000,3000}, {64,1024,3000,3000}, {4000,1024,3000,5000},
                {Double.NaN,1024,3000,4000}, {64,Double.POSITIVE_INFINITY,3000,4000}}) {
            boolean rejected = false;
            try { new AtmosphereProfile(values[0],values[1],values[2],values[3]); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "Invalid atmosphere settings rejected");
        }
    }
    private static void near(double expected, double actual, double tolerance, String label) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance, label + ": " + actual);
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
