package net.aste.aeroworks.waves;

/** Server-owned profile, shared by rendering and buoyancy. */
public record WaveParameters(boolean enabled,boolean buoyancy,double amplitude,double wavelength,
        double speed,double direction,double shoreFade,WaveDetail detail) {
    public WaveParameters(boolean enabled,boolean buoyancy,double amplitude,double wavelength,double speed,double direction,double shoreFade){
        this(enabled,buoyancy,amplitude,wavelength,speed,direction,shoreFade,WaveDetail.OFF);
    }
    public static final WaveParameters CALM=new WaveParameters(false,false,0,32,4,35,16);
    public WaveParameters {
        if(detail==null||!Double.isFinite(amplitude)||amplitude<0||amplitude>2||!Double.isFinite(wavelength)||wavelength<8||wavelength>256
                ||!Double.isFinite(speed)||speed<0||speed>32||!Double.isFinite(direction)||Math.abs(direction)>360
                ||!Double.isFinite(shoreFade)||shoreFade<4||shoreFade>64) throw new IllegalArgumentException("Invalid wave profile");
    }
    public double maximumDisplacement(){return amplitude*(detail.enabled()?Math.sqrt(1.5)*(1+detail.noiseStrength()+detail.chopFraction()):1);}
    public double radians(){return Math.toRadians(direction);}
}
