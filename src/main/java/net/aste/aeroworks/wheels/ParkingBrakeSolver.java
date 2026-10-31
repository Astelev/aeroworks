package net.aste.aeroworks.wheels;

import java.util.List;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;

/** Coupled, bounded contact impulses: no per-wheel whole-body velocity reset. */
public final class ParkingBrakeSolver {
    public static final class Contact {
        public final Vector3d axis=new Vector3d(),lever=new Vector3d(),angularAxis=new Vector3d(),angularResponse=new Vector3d();
        public double speed,limit,impulse,fallback;
    }
    private ParkingBrakeSolver() {}
    public static boolean solve(double mass,Matrix3dc inertia,List<Contact> contacts) {
        if(!Double.isFinite(mass)||mass<=0)return false;
        var inverse=new Matrix3d(inertia);
        if(!Double.isFinite(inverse.determinant())||inverse.determinant()<=1e-12)return false;
        inverse.invert();
        for(var c:contacts) {
            if(!c.axis.isFinite()||!c.lever.isFinite()||!Double.isFinite(c.speed)||!Double.isFinite(c.limit)||c.limit<0)return false;
            c.lever.cross(c.axis,c.angularAxis);
            inverse.transform(c.angularAxis,c.angularResponse);
            double effective=1/mass+c.angularAxis.dot(c.angularResponse);
            if(!Double.isFinite(effective)||effective<=0)return false;
        }
        var linear=new Vector3d();var angular=new Vector3d();
        for(var c:contacts)c.impulse=0;
        for(int iteration=0;iteration<16;iteration++)for(var c:contacts) {
            double speed=c.speed+c.axis.dot(linear)+c.angularAxis.dot(angular);
            double inverseMass=1/mass+c.angularAxis.dot(c.angularResponse);
            double next=Math.max(-c.limit,Math.min(c.limit,c.impulse-speed/inverseMass));
            double delta=next-c.impulse;c.impulse=next;
            linear.fma(delta/mass,c.axis);angular.fma(delta,c.angularResponse);
        }
        return linear.isFinite()&&angular.isFinite();
    }
}
