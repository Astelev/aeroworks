package net.aste.aeroworks.wind;

/** Seeded, continuous space/time field. No world access, noise, zone objects or per-sample allocations. */
public final class ZonalWindModel {
    private ZonalWindModel() {}
    public static final class Result { public double x, z; }

    public static void sample(WindParameters p, long seed, double phase, double x, double y, double z,
            WindWeatherBlend weather, double time, Result out) {
        double t = weather.fraction(time);
        double rain = weather.rain(t), storm = weather.storm(t);
        double surface = mix(mix(p.surfaceClear(), p.surfaceRain(), rain), p.surfaceStorm(), storm);
        double aloft = mix(mix(p.aloftClear(), p.aloftRain(), rain), p.aloftStorm(), storm);
        double gx = x / p.zoneSize(), gz = z / p.zoneSize();
        double surfaceAngle = grid(seed, gx, gz, phase) * Math.PI * 2 + weather.surfaceTurn(t);
        double upperAngle = weather.heading(t) + weather.aloftTurn(t)
                + (grid(seed ^ 0xaabbcd133L, gx, gz, phase) * 2 - 1) * Math.toRadians(p.cloudDeviationDegrees()) * .5;
        double height = smooth((y - p.surfaceHeight()) / (p.maximumHeight() - p.surfaceHeight()));
        // Interpolate heading and magnitude separately; opposing layers do not cancel into calm air.
        // These headings are continuous/unwrapped; choosing a shortest arc here
        // would flip abruptly when the two layers cross an antipodal direction.
        double heading = mix(surfaceAngle, upperAngle, height);
        double gust = 1 + (grid(seed ^ 0x6a937b84L, gx, gz, phase * 1.7) * 2 - 1) * .15;
        double speed = mix(surface, aloft, height) * gust;
        out.x = Math.cos(heading) * speed;
        out.z = Math.sin(heading) * speed;
    }

    private static double grid(long seed, double x, double z, double phase) {
        long ix = (long) Math.floor(x), iz = (long) Math.floor(z);
        double fx = smooth(x - ix), fz = smooth(z - iz);
        double a = mix(node(seed, ix, iz, phase), node(seed, ix + 1, iz, phase), fx);
        double b = mix(node(seed, ix, iz + 1, phase), node(seed, ix + 1, iz + 1, phase), fx);
        return mix(a, b, fz);
    }
    private static double node(long seed, long x, long z, double phase) {
        long key = seed ^ (x * 0x632be59bd9b4e019L) ^ (z * 0x9e3779b97f4a7c15L);
        double clock = phase + unitHash(key ^ 0x19ac332L);
        long epoch = (long) Math.floor(clock);
        return mix(unitHash(key ^ (epoch * 0xd1b54a32d192ed03L)),
                unitHash(key ^ ((epoch + 1) * 0xd1b54a32d192ed03L)), smooth(clock - epoch));
    }
    public static double unitHash(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return ((value ^ (value >>> 31)) >>> 11) * 0x1.0p-53;
    }
    public static long typeKey(String id) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) hash = (hash ^ id.charAt(i)) * 0x100000001b3L;
        return hash;
    }
    public static double angleMix(double a, double b, double t) {
        double delta = Math.IEEEremainder(b - a, Math.PI * 2);
        return a + delta * t;
    }
    public static double clamp(double t) { return Math.max(0, Math.min(1, t)); }
    public static double smooth(double t) { t = clamp(t); return t * t * (3 - 2 * t); }
    private static double mix(double a, double b, double t) { return a + (b - a) * t; }
}
