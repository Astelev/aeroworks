package net.aste.aeroworks.waves;

/** Per-step coefficients: never repeat trig/config preparation for each buoyancy sample. */
public final class WaveSpectrum {
    private WaveParameters p;private WaveWindGrid wind;private double seconds;
    private double kx,kz,phase,ax,az,ap,bx,bz,bp,warpAmplitude;
    public void begin(WaveParameters p,WaveWindGrid wind,double seconds){
        this.p=p;this.wind=wind;this.seconds=seconds;
        double k=WaveMath.TAU/p.wavelength(),angle=p.radians();
        kx=k*Math.cos(angle);kz=k*Math.sin(angle);phase=Math.IEEEremainder(-k*p.speed()*seconds,WaveMath.TAU);
        var d=p.detail();warpAmplitude=p.wavelength()*d.crestDistortion();double ak=k/d.chopLength(),bk=ak*1.37;
        ax=ak*Math.cos(angle+.7);az=ak*Math.sin(angle+.7);ap=Math.IEEEremainder(-ak*p.speed()*d.chopSpeed()*seconds,WaveMath.TAU);
        bx=bk*Math.cos(angle-1.1);bz=bk*Math.sin(angle-1.1);bp=Math.IEEEremainder(-bk*p.speed()*d.chopSpeed()*.83*seconds,WaveMath.TAU);
    }
    public double amplitude(){return p.amplitude();}
    public double height(double x,double z,double depth){
        var d=p.detail();double mainPhase=kx*x+kz*z+phase;
        if(!d.enabled())return p.amplitude()*WaveMath.fastSin(mainPhase);
        double ratio=wind.ratio(x,z,seconds),energy=Math.sqrt(ratio)*WaveMath.smooth(depth/d.effectiveDepth());
        if(energy==0)return 0;
        double n=d.noiseStrength()==0&&warpAmplitude==0?0:WaveShape.noise(x/d.noiseScale(),z/d.noiseScale(),seconds/d.noisePeriod(),wind.seed());
        double warpX=0,warpZ=0;
        if(warpAmplitude>0){
            warpX=n*warpAmplitude;
            warpZ=WaveShape.noise(x/d.noiseScale(),z/d.noiseScale(),seconds/d.noisePeriod(),wind.seed()^0x68bc21eb)*warpAmplitude;
            mainPhase+=kx*warpX+kz*warpZ;
        }
        double gate=WaveMath.smooth((ratio-d.chopThreshold())/(1-d.chopThreshold())),chop=0;
        double main=WaveShape.crest(WaveMath.fastSin(mainPhase+n*d.noiseStrength()*.6),gate)*(1+n*d.noiseStrength());
        if(gate>0&&d.chopFraction()>0){
            double jitter=WaveShape.noise(x/(d.noiseScale()*.35),z/(d.noiseScale()*.35),seconds/(d.noisePeriod()*.23),wind.seed()^0x5bd1e995);
            chop=(.7*WaveMath.fastSin(ax*x+az*z+ap+ax*warpX+az*warpZ+1.8*jitter)+.3*WaveMath.fastSin(bx*x+bz*z+bp+bx*warpX+bz*warpZ-1.2*jitter))
                    *(.55+.45*jitter)*d.chopFraction()*gate;
        }
        return p.amplitude()*energy*(main+chop);
    }
}
