package net.aste.aeroworks.wind;

/** Immutable transition, published to physics; retargeting preserves the current weather value. */
public record WindWeatherBlend(WindWeather from, WindWeather to, double started, double duration) {
    public static final WindWeatherBlend CLEAR = new WindWeatherBlend(WindWeather.CLEAR, WindWeather.CLEAR, 0, 1);
    public double fraction(double time) {
        // Exponential response composes across retargets. Restarted smoothstep
        // would become ~80 seconds of lag when a 15-second target drifts every second.
        return -Math.expm1(-3 * Math.max(0, time - started) / duration);
    }
    public double rain(double t) { return mix(from.rain(), to.rain(), t); }
    public double storm(double t) { return mix(from.storm(), to.storm(), t); }
    public double heading(double t) { return ZonalWindModel.angleMix(from.cloudHeading(), to.cloudHeading(), t); }
    public double surfaceTurn(double t) { return ZonalWindModel.angleMix(from.surfaceTurn(), to.surfaceTurn(), t); }
    public double aloftTurn(double t) { return ZonalWindModel.angleMix(from.aloftTurn(), to.aloftTurn(), t); }
    public WindWeather current(double time) {
        double t = fraction(time);
        return new WindWeather(to.cloudType(), rain(t), storm(t), heading(t), surfaceTurn(t), aloftTurn(t));
    }
    public WindWeatherBlend retarget(WindWeather next, double time, double seconds) {
        if (next.equals(to)) return this;
        return new WindWeatherBlend(current(time), next, time, seconds);
    }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
