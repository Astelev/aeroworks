package net.aste.aeroworks.compat.deepseas;

import org.joml.Vector3d;

/** Same horizontal, area-weighted signed normals as High Seas' vane. */
public final class SailHeadingMath {
    private SailHeadingMath(){}
    public static void add(Vector3d sum,double nx,double ny,double nz,double area,int supportSign){
        double length=Math.hypot(nx,nz);
        if(!Double.isFinite(length)||length<1e-9||!Double.isFinite(area)||area<=0)return;
        double weight=area*(supportSign==0?1:-Math.signum(supportSign))/length;
        sum.add(nx*weight,0,nz*weight);
    }
    public static boolean normalize(Vector3d direction){
        double length=Math.hypot(direction.x,direction.z);direction.y=0;
        if(!Double.isFinite(length)||length<1e-9){direction.zero();return false;}
        direction.div(length);return true;
    }
    /** Add lateral leeway without reversing the designated bow in a headwind. */
    public static Vector3d drift(Vector3d forward,double wx,double wz,double fraction,Vector3d out){
        out.set(forward);if(!normalize(out))return out;
        double speed=Math.hypot(wx,wz);
        if(!Double.isFinite(speed)||speed<1e-9)return out;
        double dot=out.x*wx/speed+out.z*wz/speed;
        double sideX=wx/speed-out.x*dot,sideZ=wz/speed-out.z*dot;
        out.add(sideX*Math.clamp(fraction,0,.5),0,sideZ*Math.clamp(fraction,0,.5));
        normalize(out);return out;
    }
    public static double alignment(double fx,double fz,double wx,double wz){
        double fl=Math.hypot(fx,fz),wl=Math.hypot(wx,wz);
        if(!Double.isFinite(fl)||!Double.isFinite(wl)||fl<1e-9||wl<1e-9)return Double.NaN;
        return Math.clamp((fx/fl)*(wx/wl)+(fz/fl)*(wz/wl),-1,1);
    }
}
