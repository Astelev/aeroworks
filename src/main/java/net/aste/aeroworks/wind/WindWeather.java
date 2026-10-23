package net.aste.aeroworks.wind;

/** Server-thread weather metadata. Cloud heading is motion TOWARDS +X/+Z, in radians. */
public record WindWeather(long cloudType, double rain, double storm, double cloudHeading,
        double surfaceTurn, double aloftTurn) {
    public static final WindWeather CLEAR = new WindWeather(0, 0, 0, 0, 0, 0);

    public static WindWeather of(long key, double rain, double storm, double cloudHeading, long seed,
            double deviationDegrees) {
        double ground = ZonalWindModel.unitHash(seed ^ key) * Math.PI * 2;
        double upper = (ZonalWindModel.unitHash(seed ^ key ^ 0x687493badL) - .5)
                * Math.toRadians(deviationDegrees);
        return new WindWeather(key, ZonalWindModel.clamp(rain), ZonalWindModel.clamp(storm),
                cloudHeading, ground, upper);
    }
}
