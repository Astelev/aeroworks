package net.aste.aeroworks.wind;

/** Phenomenological effects; no vortex/fluid solver. Distances in blocks, time in seconds. */
public record WindEffectsParameters(boolean mountainEnabled, double diameter, double spacing,
        double driftRadius, double driftPeriod, double minimumMountainHeight, double minimumRelief,
        double columnHeight, double verticalSpeed, boolean turbulenceEnabled, double chancePer100Blocks,
        double minimumDistance, double maximumDistance, double cooldownDistance, double gustSpeed,
        double rockingTorquePerSail, double gustPeriod, double fadeDistance,
        double stormDirectionMultiplier, double stormChanceMultiplier) {
    public static final WindEffectsParameters DEFAULT = new WindEffectsParameters(true,100,128,15,120,120,20,
            500,4,true,.3,100,300,150,3.5,.15,.4,10,3,2);
    public WindEffectsParameters {
        for (double v : new double[]{diameter,spacing,driftRadius,driftPeriod,minimumMountainHeight,minimumRelief,
                columnHeight,verticalSpeed,chancePer100Blocks,minimumDistance,maximumDistance,cooldownDistance,
                gustSpeed,rockingTorquePerSail,gustPeriod,fadeDistance,stormDirectionMultiplier,stormChanceMultiplier}) {
            if (!Double.isFinite(v)) throw new IllegalArgumentException("Wind effects must be finite");
        }
        if (diameter<=0 || spacing<16 || driftRadius<0 || driftRadius>spacing/2 || driftPeriod<=0
                || minimumRelief<0 || columnHeight<60 || verticalSpeed<0 || chancePer100Blocks<0 || chancePer100Blocks>=1
                || minimumDistance<=0 || maximumDistance<minimumDistance || cooldownDistance<0 || gustSpeed<0
                || rockingTorquePerSail<0 || gustPeriod<=0 || fadeDistance<=0
                || stormDirectionMultiplier<1 || stormChanceMultiplier<1)
            throw new IllegalArgumentException("Invalid flow/turbulence sizes, speeds, chances or timing");
    }
}
