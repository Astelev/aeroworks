package net.aste.aeroworks.client.rain;

/** Conservative near-camera segment test, including slanted rain columns. */
public final class RainRaySchedule {
    private RainRaySchedule(){}
    public static boolean near(double sx,double sz,double ex,double ez,double cx,double cz){
        double dx=ex-sx,dz=ez-sz,d=dx*dx+dz*dz;
        double t=d==0?0:Math.clamp(((cx-sx)*dx+(cz-sz)*dz)/d,0,1);
        double x=sx+t*dx-cx,z=sz+t*dz-cz;return x*x+z*z<=64;
    }
    public static boolean refresh(int tick,int last,int interval,boolean hasHit,boolean changed,boolean near){
        return !hasHit||changed||near||interval<=1||tick-last>=interval;
    }
}
