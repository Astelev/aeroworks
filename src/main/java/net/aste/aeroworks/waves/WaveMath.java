package net.aste.aeroworks.waves;

/** The first wave profile. Coordinates and time are in blocks and seconds. */
public final class WaveMath {
    public static final double TAU = Math.PI * 2;
    private static final int TABLE_SIZE=4096;
    private static final double[] SIN=new double[TABLE_SIZE+1];
    static {for(int i=0;i<=TABLE_SIZE;i++)SIN[i]=Math.sin(TAU*i/TABLE_SIZE);}
    private WaveMath() {}
    public static double fastSin(double radians){
        double turns=radians/TAU;
        double p=(turns-Math.floor(turns))*TABLE_SIZE;
        int i=(int)p;double f=p-i;
        return SIN[i]+(SIN[i+1]-SIN[i])*f;
    }
    public static double phase(double x, double z, double seconds, double wavelength,
                               double speed, double directionRadians) {
        return Math.IEEEremainder(TAU * ((x * Math.cos(directionRadians)
                + z * Math.sin(directionRadians) - speed * seconds) / wavelength), TAU);
    }
    public static double height(double phase, double amplitude, double shoreWeight) {
        return Math.sin(phase) * amplitude * shoreWeight;
    }
    public static double smooth(double x) {
        x = Math.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
