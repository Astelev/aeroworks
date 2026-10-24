package net.aste.aeroworks.failure;

/** Time-based hazard, not a chance per physics step. Units: RPM, blocks/second, world Y, seconds. */
public record FailureRisk(double baseChancePerMinute,double referenceRPM,double rpmWeight,
        double referenceSpeed,double speedWeight,double altitudeStart,double altitudeScale,
        double altitudeWeight,double turbulenceMultiplier,double maximumMultiplier) {
    public static final FailureRisk DEFAULT=new FailureRisk(.3,64,1,40,1,128,256,1,8,256);
    public FailureRisk {
        for(double v:new double[]{baseChancePerMinute,referenceRPM,rpmWeight,referenceSpeed,speedWeight,
                altitudeStart,altitudeScale,altitudeWeight,turbulenceMultiplier,maximumMultiplier}) {
            if(!Double.isFinite(v))throw new IllegalArgumentException("Failure parameters must be finite");
        }
        if(baseChancePerMinute<0||baseChancePerMinute>1||referenceRPM<=0||rpmWeight<0||referenceSpeed<=0
                ||speedWeight<0||altitudeScale<=0||altitudeWeight<0||turbulenceMultiplier<1||maximumMultiplier<1) {
            throw new IllegalArgumentException("Invalid failure probability, references or risk multipliers");
        }
    }
    public double multiplier(double rpm,double speed,double height,boolean turbulence) {
        if(!Double.isFinite(rpm)||Math.abs(rpm)<.001||!Double.isFinite(speed)||!Double.isFinite(height))return 0;
        double rotation=Math.max(.05,1+rpmWeight*(Math.abs(rpm)/referenceRPM-1));
        double movement=1+speedWeight*Math.abs(speed)/referenceSpeed;
        double altitude=1+altitudeWeight*Math.max(0,height-altitudeStart)/altitudeScale;
        return Math.min(maximumMultiplier,rotation*movement*altitude*(turbulence?turbulenceMultiplier:1));
    }
    public double probability(double rpm,double speed,double height,boolean turbulence,double seconds) {
        if(!Double.isFinite(seconds)||seconds<=0||baseChancePerMinute==0)return 0;
        double risk=multiplier(rpm,speed,height,turbulence);
        return risk<=0?0:-Math.expm1(Math.log1p(-baseChancePerMinute)*seconds/60*risk);
    }
    public double eventProbability(double chance,double rpm,double speed,double height,boolean turbulence) {
        double risk=multiplier(rpm,speed,height,turbulence);
        return chance<=0||risk<=0?0:-Math.expm1(Math.log1p(-chance)*risk);
    }
}
