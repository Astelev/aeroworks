package net.aste.aeroworks.waves;

/** Relative to the configured main wave and the local configured storm wind. */
public record WaveDetail(boolean enabled,double fullDepth,double noiseStrength,double noiseScale,double noisePeriod,
        double chopThreshold,double chopFraction,double chopLength,double chopSpeed,double crestDistortion,double depthRangeMultiplier) {
    public WaveDetail(boolean enabled,double fullDepth,double noiseStrength,double noiseScale,double noisePeriod,
            double chopThreshold,double chopFraction,double chopLength,double chopSpeed,double crestDistortion){
        this(enabled,fullDepth,noiseStrength,noiseScale,noisePeriod,chopThreshold,chopFraction,chopLength,chopSpeed,crestDistortion,enabled?3:1);
    }
    public WaveDetail(boolean enabled,double fullDepth,double noiseStrength,double noiseScale,double noisePeriod,
            double chopThreshold,double chopFraction,double chopLength,double chopSpeed){
        this(enabled,fullDepth,noiseStrength,noiseScale,noisePeriod,chopThreshold,chopFraction,chopLength,chopSpeed,enabled?.22:0);
    }
    public static final WaveDetail DEFAULT=new WaveDetail(true,16,.2,48,24,.65,.35,.25,1.7);
    public static final WaveDetail OFF=new WaveDetail(false,16,0,48,24,.65,0,.25,1.7);
    public double effectiveDepth(){return Math.min(128,fullDepth*depthRangeMultiplier);}
    public WaveDetail {
        for(double d:new double[]{fullDepth,noiseStrength,noiseScale,noisePeriod,chopThreshold,chopFraction,chopLength,chopSpeed,crestDistortion,depthRangeMultiplier})
            if(!Double.isFinite(d))throw new IllegalArgumentException("Non-finite wave detail");
        if(fullDepth<3||fullDepth>128||noiseStrength<0||noiseStrength>.5||noiseScale<8||noiseScale>512
                ||noisePeriod<2||noisePeriod>300||chopThreshold<0||chopThreshold>=1||chopFraction<0||chopFraction>.75
                ||chopLength<.125||chopLength>.5||chopSpeed<.25||chopSpeed>4||crestDistortion<0||crestDistortion>.35||depthRangeMultiplier<1||depthRangeMultiplier>8)throw new IllegalArgumentException("Invalid wave detail");
    }
}
