package net.aste.aeroworks.waves;

/** One reusable context per physics thread. The native loop supplies its existing affine transforms. */
public final class WaveBuoyancyStep {
    private WaveShorePatch shore;
    private double amplitude,kx,kz,phase;
    private double ox,oz,xx,xz,yx,yz,zx,zz;
    private int transforms;
    public void begin(WaveParameters p,WaveShorePatch patch,double seconds,double gain){
        shore=p.enabled()&&p.buoyancy()&&p.amplitude()>0?patch:null;
        transforms=0;amplitude=p.amplitude()*gain;
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
        if(shore==null||transforms!=4||amplitude==0)return nativeDepth;
        double wx=ox+xx*x+yx*y+zx*z,wz=oz+xz*x+yz*y+zz*z;
        double weight=shore.weight(wx,wz,waterBlockY);
        if(weight==0)return nativeDepth;
        return nativeDepth+amplitude*weight*WaveMath.fastSin(kx*wx+kz*wz+phase);
    }
    public void end(){shore=null;}
}
