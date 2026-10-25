package net.aste.aeroworks.aerodynamics;

import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Passive cambered surface in still air: lift dot velocity = 0, drag dot velocity <= 0. */
public final class SurfaceAerodynamics {
    public static final class Result {
        public final Vector3d lift=new Vector3d(),drag=new Vector3d();
        public void clear() {lift.zero();drag.zero();}
    }
    private SurfaceAerodynamics() {}
    public static void calculate(Vector3dc velocity,Vector3dc normal,double density,double liftCoefficient,
            double normalDragCoefficient,double baseDragCoefficient,double liftCap,double normalCap,Result out) {
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
        double drag=density*(Math.max(0,baseDragCoefficient)*speed*speed
                +Math.max(0,normalDragCoefficient)*squaredSpeed(speed*Math.abs(incidence),normalCap));
        out.drag.set(ux,uy,uz).mul(-drag);
        if(!out.lift.isFinite()||!out.drag.isFinite())out.clear();
    }
    private static double squaredSpeed(double speed,double cap) {
        if(Double.isInfinite(cap))return speed*speed;
        if(cap<=0||!Double.isFinite(cap))return 0;
        double ratio=speed/cap;
        return cap*cap*(ratio*ratio/(1+ratio*ratio));
    }
}
