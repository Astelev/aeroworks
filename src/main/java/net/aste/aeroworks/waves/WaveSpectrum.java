package net.aste.aeroworks.waves;

/** Per-step coefficients: never repeat trig/config preparation for each buoyancy sample. */
public final class WaveSpectrum {
    private WaveParameters p;private WaveWindGrid wind;private double seconds;
    private double kx,kz,phase,ax,az,ap,bx,bz,bp;
    public void begin(WaveParameters p,WaveWindGrid wind,double seconds){
        this.p=p;this.wind=wind;this.seconds=seconds;
        double k=WaveMath.TAU/p.wavelength(),angle=p.radians();
        kx=k*Math.cos(angle);kz=k*Math.sin(angle);phase=Math.IEEEremainder(-k*p.speed()*seconds,WaveMath.TAU);
        var d=p.detail();double ak=k/d.chopLength(),bk=ak*1.37;
        ax=ak*Math.cos(angle+.7);az=ak*Math.sin(angle+.7);ap=Math.IEEEremainder(-ak*p.speed()*d.chopSpeed()*seconds,WaveMath.TAU);
        bx=bk*Math.cos(angle-1.1);bz=bk*Math.sin(angle-1.1);bp=Math.IEEEremainder(-bk*p.speed()*d.chopSpeed()*.83*seconds,WaveMath.TAU);
    }
    public double amplitude(){return p.amplitude();}
    public double height(double x,double z,double depth){
        var d=p.detail();double mainPhase=kx*x+kz*z+phase;
        if(!d.enabled())return p.amplitude()*WaveMath.fastSin(mainPhase);
        double ratio=wind.ratio(x,z,seconds),energy=Math.sqrt(ratio)*WaveMath.smooth(depth/d.fullDepth());
        if(energy==0)return 0;
        double n=d.noiseStrength()==0?0:WaveShape.noise(x/d.noiseScale(),z/d.noiseScale(),seconds/d.noisePeriod(),wind.seed());
        double main=WaveMath.fastSin(mainPhase+n*d.noiseStrength()*.6)*(1+n*d.noiseStrength());
        double gate=WaveMath.smooth((ratio-d.chopThreshold())/(1-d.chopThreshold())),chop=0;
        if(gate>0&&d.chopFraction()>0){
            double jitter=WaveShape.noise(x/(d.noiseScale()*.35),z/(d.noiseScale()*.35),seconds/(d.noisePeriod()*.23),wind.seed()^0x5bd1e995);
            chop=(.7*WaveMath.fastSin(ax*x+az*z+ap+1.8*jitter)+.3*WaveMath.fastSin(bx*x+bz*z+bp-1.2*jitter))
                    *(.55+.45*jitter)*d.chopFraction()*gate;
        }
        return p.amplitude()*energy*(main+chop);
    }
}
