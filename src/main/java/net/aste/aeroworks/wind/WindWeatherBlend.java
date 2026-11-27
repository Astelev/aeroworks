package net.aste.aeroworks.wind;

/** Immutable transition; all public queries accept world time in seconds, never a normalized fraction. */
public record WindWeatherBlend(WindWeather from, WindWeather to, double started, double duration) {
    public static final WindWeatherBlend CLEAR = new WindWeatherBlend(WindWeather.CLEAR, WindWeather.CLEAR, 0, 1);
    public double fraction(double time) {
        // Exponential response composes across retargets. Restarted smoothstep
        // would become ~80 seconds of lag when a 15-second target drifts every second.
        return -Math.expm1(-3 * Math.max(0, time - started) / duration);
    }
    public double rain(double time) { return mix(from.rain(), to.rain(), fraction(time)); }
    public double storm(double time) { return mix(from.storm(), to.storm(), fraction(time)); }
    public double heading(double time) { return ZonalWindModel.angleMix(from.cloudHeading(), to.cloudHeading(), fraction(time)); }
    public double surfaceTurn(double time) { return ZonalWindModel.angleMix(from.surfaceTurn(), to.surfaceTurn(), fraction(time)); }
    public double aloftTurn(double time) { return ZonalWindModel.angleMix(from.aloftTurn(), to.aloftTurn(), fraction(time)); }
    public WindWeather current(double time) {
        return new WindWeather(to.cloudType(), rain(time), storm(time), heading(time), surfaceTurn(time), aloftTurn(time));
    }
    public WindWeatherBlend retarget(WindWeather next, double time, double seconds) {
        if (next.equals(to)) return this;
        return new WindWeatherBlend(current(time), next, time, seconds);
    }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
