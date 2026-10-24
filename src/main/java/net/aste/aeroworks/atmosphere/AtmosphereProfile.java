package net.aste.aeroworks.atmosphere;

/** Relative air density; compressed barometric model with a smooth finite ceiling. */
public record AtmosphereProfile(double referenceHeight, double scaleHeight, double fadeStart, double ceiling) {
    public static final AtmosphereProfile DEFAULT = new AtmosphereProfile(64, 1024, 3072, 4096);

    public AtmosphereProfile {
        if (!Double.isFinite(referenceHeight) || !Double.isFinite(scaleHeight)
                || !Double.isFinite(fadeStart) || !Double.isFinite(ceiling)
                || scaleHeight <= 0 || referenceHeight > fadeStart || fadeStart >= ceiling) {
            throw new IllegalArgumentException("Require finite values, scale_height > 0 and reference_height <= fade_start_height < ceiling_height");
        }
    }

    public double density(double height) {
        if (Double.isNaN(height) || height >= ceiling) return 0;
        double density = Math.min(1.5, Math.exp((referenceHeight - height) / scaleHeight));
        if (height <= fadeStart) return density;
        double t = (height - fadeStart) / (ceiling - fadeStart);
        // Smoothstep's derivative is zero at both ends: no discontinuity at vacuum.
        return density * (1 - t * t * (3 - 2 * t));
    }
}
