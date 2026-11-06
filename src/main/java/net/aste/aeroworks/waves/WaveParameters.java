package net.aste.aeroworks.waves;

/** Server-owned profile, shared by rendering and buoyancy. */
public record WaveParameters(boolean enabled,boolean buoyancy,double amplitude,double wavelength,
        double speed,double direction,double shoreFade) {
    public static final WaveParameters CALM=new WaveParameters(false,false,0,32,4,35,16);
    public WaveParameters {
        if(!Double.isFinite(amplitude)||amplitude<0||amplitude>2||!Double.isFinite(wavelength)||wavelength<8||wavelength>256
                ||!Double.isFinite(speed)||speed<0||speed>32||!Double.isFinite(direction)||Math.abs(direction)>360
                ||!Double.isFinite(shoreFade)||shoreFade<4||shoreFade>64) throw new IllegalArgumentException("Invalid wave profile");
    }
    public double radians(){return Math.toRadians(direction);}
}
