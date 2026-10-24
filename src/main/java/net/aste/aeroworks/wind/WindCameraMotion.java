package net.aste.aeroworks.wind;

/** Absolute render offsets in degrees; never accumulate into a player's actual aim. */
public final class WindCameraMotion {
    public static final class Result { public double pitch,yaw,roll; }
    private WindCameraMotion() {}
    public static void sample(long seed,double elapsed,double period,double strength,Result out) {
        double gain=Double.isFinite(strength) ? Math.max(0,Math.min(3,strength)):0;
        out.pitch=.45*gain*AlternatingWindPulse.sample(seed^0x5a0fL,elapsed,period);
        out.yaw=.25*gain*AlternatingWindPulse.sample(seed^0x91afL,elapsed+period*.31,period);
        out.roll=gain*AlternatingWindPulse.sample(seed^0x631cL,elapsed+period*.64,period);
    }
}
