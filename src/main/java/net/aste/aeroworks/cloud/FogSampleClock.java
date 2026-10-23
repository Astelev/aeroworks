package net.aste.aeroworks.cloud;

/** Limits expensive density queries, without delaying a fast camera at cloud edges. */
public final class FogSampleClock {
    private double time = Double.NaN, x, y, z;
    public void reset() { time = Double.NaN; }
    public boolean shouldSample(double now, double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        if (Double.isNaN(time) || now < time || now - time >= 1
                || dx * dx + dy * dy + dz * dz >= .25) {
            time = now; x = px; y = py; z = pz;
            return true;
        }
        return false;
    }
}
