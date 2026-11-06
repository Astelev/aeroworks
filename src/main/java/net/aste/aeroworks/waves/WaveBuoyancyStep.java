package net.aste.aeroworks.waves;

/** One reusable context per physics thread. The native loop supplies its existing affine transforms. */
public final class WaveBuoyancyStep {
    private WaveShorePatch shore;
    private double amplitude,kx,kz,phase;
    private double ox,oz,xx,xz,yx,yz,zx,zz;
    private int transforms;
    private boolean observe;
    private int samples,weighted,changed;
    private double maximumOffset;
    public record Observation(int samples,int weighted,int changed,double maximumOffset){}
    public void observe(boolean enabled){observe=enabled;samples=weighted=changed=0;maximumOffset=0;}
    public boolean observing(){return observe;}
    public Observation observation(){return new Observation(samples,weighted,changed,maximumOffset);}
    public void begin(WaveParameters p,WaveShorePatch patch,double seconds,double gain){
        shore=p.enabled()&&p.buoyancy()&&p.amplitude()>0?patch:null;
        transforms=0;amplitude=p.amplitude()*gain;observe=false;
        kx=WaveMath.TAU*Math.cos(p.radians())/p.wavelength();
        kz=WaveMath.TAU*Math.sin(p.radians())/p.wavelength();
        phase=Math.IEEEremainder(-WaveMath.TAU*p.speed()*seconds/p.wavelength(),WaveMath.TAU);
    }
    public void transform(double x,double z){
        switch(transforms++){
            case 0->{ox=x;oz=z;}
            case 1->{xx=x-ox;xz=z-oz;}
            case 2->{yx=x-ox;yz=z-oz;}
            case 3->{zx=x-ox;zz=z-oz;}
            default->throw new IllegalStateException("Native buoyancy transform count changed");
        }
    }
    public double depth(double nativeDepth,double x,double y,double z,double waterBlockY){
        if(observe)samples++;
        if(shore==null||transforms!=4||amplitude==0)return nativeDepth;
        double wx=ox+xx*x+yx*y+zx*z,wz=oz+xz*x+yz*y+zz*z;
        double weight=shore.weight(wx,wz,waterBlockY);
        if(weight==0)return nativeDepth;
        double offset=amplitude*weight*WaveMath.fastSin(kx*wx+kz*wz+phase);
        if(observe){
            weighted++;maximumOffset=Math.max(maximumOffset,Math.abs(offset));
            if(Math.abs(Math.clamp(nativeDepth+offset,0,1)-Math.clamp(nativeDepth,0,1))>1e-8)changed++;
        }
        return nativeDepth+offset;
    }
    public void end(){shore=null;}
}
