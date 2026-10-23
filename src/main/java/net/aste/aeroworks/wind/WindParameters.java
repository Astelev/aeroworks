package net.aste.aeroworks.wind;

/** All speeds are blocks/second; heights and zone size are world blocks. */
public record WindParameters(double zoneSize, double surfaceHeight, double maximumHeight,
        double surfaceClear, double surfaceRain, double surfaceStorm,
        double aloftClear, double aloftRain, double aloftStorm,
        double fallbackPeriod, double weatherPeriodMultiplier, double transitionSeconds,
        double cloudDeviationDegrees) {
    public static final WindParameters DEFAULT = new WindParameters(512, 64, 250,
            2.5, 4, 6, 8, 12, 18, 360, 1, 15, 15);
    public WindParameters {
        for (double value : new double[]{zoneSize, surfaceHeight, maximumHeight, surfaceClear, surfaceRain,
                surfaceStorm, aloftClear, aloftRain, aloftStorm, fallbackPeriod, weatherPeriodMultiplier,
                transitionSeconds, cloudDeviationDegrees}) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Wind parameters must be finite");
        }
        if (zoneSize < 16 || maximumHeight <= surfaceHeight || surfaceClear < 0
                || surfaceRain < surfaceClear || surfaceStorm < surfaceRain || aloftClear < surfaceClear
                || aloftRain < Math.max(aloftClear, surfaceRain) || aloftStorm < Math.max(aloftRain, surfaceStorm)
                || fallbackPeriod <= 0 || weatherPeriodMultiplier <= 0 || transitionSeconds <= 0
                || cloudDeviationDegrees < 0 || cloudDeviationDegrees > 45) {
            throw new IllegalArgumentException("Require ordered nonnegative wind speeds, maximum_height > surface_height and positive timing/zone parameters");
        }
    }
}
