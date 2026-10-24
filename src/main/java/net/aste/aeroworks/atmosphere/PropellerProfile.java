package net.aste.aeroworks.atmosphere;

/** Additional propeller efficiency. Starts with zero slope and multiplies existing air pressure. */
public record PropellerProfile(double startHeight,double scaleHeight) {
    public static final PropellerProfile DEFAULT=new PropellerProfile(128,256);
    public PropellerProfile {
        if(!Double.isFinite(startHeight)||!Double.isFinite(scaleHeight)||scaleHeight<=0) {
            throw new IllegalArgumentException("Require finite propeller heights and scale_height > 0");
        }
    }
    public double efficiency(double height) {
        if(Double.isNaN(height)) return 0;
        if(height<=startHeight) return 1;
        double t=(height-startHeight)/scaleHeight;
        return Math.exp(-t*t);
    }
}
