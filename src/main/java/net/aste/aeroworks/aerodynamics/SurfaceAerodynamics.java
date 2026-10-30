package net.aste.aeroworks.aerodynamics;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Cambered lift, attached-flow redirection of two-sided pressure, and dissipative drag. */
public final class SurfaceAerodynamics {
    public static final class Result {
        public final Vector3d lift=new Vector3d(),drag=new Vector3d();
        public double normalDamping,baseDamping;
        public void clear() {lift.zero();drag.zero();normalDamping=baseDamping=0;}
    }
    private SurfaceAerodynamics() {}
    public static void calculate(Vector3dc velocity,Vector3dc normal,double density,double liftCoefficient,
            double normalDragCoefficient,double baseDragCoefficient,double liftCap,double normalCap,Result out) {
        calculate(velocity,normal,density,liftCoefficient,normalDragCoefficient,baseDragCoefficient,
                liftCap,normalCap,12,25,.1,out);
    }
    public static void calculate(Vector3dc velocity,Vector3dc normal,double density,double liftCoefficient,
            double normalDragCoefficient,double baseDragCoefficient,double liftCap,double normalCap,
            double attachedAngleDegrees,double separatedAngleDegrees,double attachedDragFraction,Result out) {
        out.clear();
        double speed=velocity.length(),normalLength=normal.length();
        if(!Double.isFinite(speed)||!Double.isFinite(normalLength)||speed<1e-9||normalLength<1e-9
                ||!Double.isFinite(density)||density<=0)return;
        double ux=velocity.x()/speed,uy=velocity.y()/speed,uz=velocity.z()/speed;
        double nx=normal.x()/normalLength,ny=normal.y()/normalLength,nz=normal.z()/normalLength;
        double incidence=Math.max(-1,Math.min(1,ux*nx+uy*ny+uz*nz));
        double px=nx-incidence*ux,py=ny-incidence*uy,pz=nz-incidence*uz;
        double projection=Math.sqrt(px*px+py*py+pz*pz);
        if(projection>1e-9 && liftCoefficient>0) {
            // Keep the Sable normal convention (-normal is positive lift), projected off the airflow.
            double magnitude=density*liftCoefficient*squaredSpeed(speed*projection,liftCap);
            out.lift.set(px,py,pz).mul(-magnitude/projection);
        }
        // Keep all transverse pressure, including control authority of zero-lift symmetric sails.
        double normalSpeed=speed*incidence;
        double normalSquared=squaredSpeed(Math.abs(normalSpeed),normalCap);
        out.normalDamping=Math.abs(normalSpeed)>1e-9?density*Math.max(0,normalDragCoefficient)*normalSquared/Math.abs(normalSpeed):0;
        double fraction=pressureDragFraction(Math.abs(incidence),attachedAngleDegrees,separatedAngleDegrees,attachedDragFraction);
        double redirected=-out.normalDamping*normalSpeed*(1-fraction);
        out.lift.add(px*redirected,py*redirected,pz*redirected);
        out.normalDamping*=fraction;
        out.baseDamping=density*Math.max(0,baseDragCoefficient)*speed;
        out.drag.set(nx,ny,nz).mul(-out.normalDamping*normalSpeed).fma(-out.baseDamping,velocity);
        if(!out.lift.isFinite()||!out.drag.isFinite()||!Double.isFinite(out.normalDamping)||!Double.isFinite(out.baseDamping))out.clear();
    }
    private static double pressureDragFraction(double incidence,double attachedDegrees,double separatedDegrees,double minimum) {
        if(!Double.isFinite(attachedDegrees)||!Double.isFinite(separatedDegrees)||!Double.isFinite(minimum))return 1;
        // This angle is measured from the plane, not from its normal. Sign and face are immaterial.
        double start=Math.sin(Math.toRadians(Math.max(0,Math.min(89.9,attachedDegrees))));
        double end=Math.sin(Math.toRadians(Math.max(0,Math.min(90,separatedDegrees))));
        end=Math.max(start+1e-6,end);
        double t=Math.max(0,Math.min(1,(incidence-start)/(end-start)));
        double smooth=t*t*(3-2*t);
        double low=Math.max(0,Math.min(1,minimum));
        return low+(1-low)*smooth;
    }
    private static double squaredSpeed(double speed,double cap) {
        if(Double.isInfinite(cap))return speed*speed;
        if(cap<=0||!Double.isFinite(cap))return 0;
        double ratio=speed/cap;
        return cap*cap*(ratio*ratio/(1+ratio*ratio));
    }
}
