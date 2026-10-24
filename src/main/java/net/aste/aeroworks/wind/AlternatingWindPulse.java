package net.aste.aeroworks.wind;

/** A continuous signed pulse. Every period reverses sign; randomness cannot hold one direction. */
public final class AlternatingWindPulse {
    private AlternatingWindPulse() {}
    public static double sample(long seed, double seconds, double period) {
        if (!Double.isFinite(seconds) || !Double.isFinite(period) || period<=0) return 0;
        double clock=Math.max(0,seconds)/period;
        long halfCycle=(long)Math.floor(clock);
        double t=clock-halfCycle;
        double amplitude=.55+.45*ZonalWindModel.unitHash(seed^(halfCycle*0x9e3779b97f4a7c15L));
        // Squared sine reaches zero with zero slope at each reversal, avoiding force discontinuities.
        double pulse=Math.sin(Math.PI*t);
        return ((halfCycle&1)==0 ? 1:-1)*amplitude*pulse*pulse;
    }
}
