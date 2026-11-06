package net.aste.aeroworks.waves;

/** Deterministic CPU/GLSL value-noise and wave spectrum. No random state or world queries. */
public final class WaveShape {
    private WaveShape(){}
    private static double hash(int x,int z,int t,int seed){
        int h=x*0x632be5ab^z*0x85157af5^t*0x9e3779b9^seed;
        h=(h^(h>>>16))*0x7feb352d;h=(h^(h>>>15))*0x846ca68b;h^=h>>>16;
        return (h>>>8)*(1.0/16777215.0)*2-1;
    }
    public static double noise(double x,double z,double t,int seed){
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z),it=(int)Math.floor(t);
        double fx=WaveMath.smooth(x-ix),fz=WaveMath.smooth(z-iz),ft=WaveMath.smooth(t-it);
        double a=mix(mix(hash(ix,iz,it,seed),hash(ix+1,iz,it,seed),fx),mix(hash(ix,iz+1,it,seed),hash(ix+1,iz+1,it,seed),fx),fz);
        double b=mix(mix(hash(ix,iz,it+1,seed),hash(ix+1,iz,it+1,seed),fx),mix(hash(ix,iz+1,it+1,seed),hash(ix+1,iz+1,it+1,seed),fx),fz);
        return mix(a,b,ft);
    }
    private static double mix(double a,double b,double f){return a+(b-a)*f;}
    public static double height(WaveParameters p,WaveWindGrid wind,double x,double z,double seconds,double depth){
        var spectrum=new WaveSpectrum();spectrum.begin(p,wind,seconds);
        return spectrum.height(x,z,depth);
    }
}
