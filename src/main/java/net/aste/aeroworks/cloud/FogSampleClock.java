package net.aste.aeroworks.cloud;

/** Adaptive density queries in game ticks. Camera motion never bypasses the rate limit. */
public final class FogSampleClock {
    private double time = Double.NaN;
    private double interval = 20;
    private float density, strength;
    private boolean hasResult;

    public void reset() {
        time = Double.NaN;
        interval = 20;
        hasResult = false;
    }

    public boolean shouldSample(double now) {
        if (Double.isNaN(time) || now < time || now - time >= interval) {
            time = now;
            return true;
        }
        return false;
    }

    /** Raw density detects approaching edges even when visible fog is clamped to 0 or 1. */
    public void record(float nextDensity, float nextStrength) {
        float change = hasResult ? Math.abs(nextDensity - density) : 0;
        float fogChange = hasResult ? Math.abs(nextStrength - strength) : 0;
        boolean plateau = nextStrength <= .001f || nextStrength >= .999f;
        if (hasResult && strength >= .999f && nextStrength >= .999f) {
            // Large fluctuations deep inside the cloud do not change visibility.
            interval = change <= .02f ? Math.min(40, interval * 2) : 20;
            interval = Math.max(20, interval);
        } else if (hasResult && (change >= .1f || fogChange >= .2f)) {
            interval = 4; // A sharp change gets a prompt follow-up, even at full fog.
        } else if (hasResult && change <= .02f && fogChange <= .03f) {
            interval = Math.min(40, interval * 2);
        } else {
            interval = plateau ? 20 : 8;
        }
        density = nextDensity;
        strength = nextStrength;
        hasResult = true;
    }
}
