package net.aste.aeroworks;

import java.util.ArrayList;
import java.util.Random;
import net.aste.aeroworks.wheels.ParkingBrakeSolver;
import org.joml.Matrix3d;
import org.joml.Vector3d;

public final class ParkingBrakeTest {
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
    private static double energy(double mass,Matrix3d inertia,Vector3d v,Vector3d w) {
        return .5*(mass*v.lengthSquared()+w.dot(inertia.transform(w,new Vector3d())));
    }
    private static ParkingBrakeSolver.Contact contact(Vector3d lever,Vector3d axis,Vector3d v,Vector3d w,double limit) {
        var c=new ParkingBrakeSolver.Contact();c.lever.set(lever);c.axis.set(axis).normalize();
        c.speed=w.cross(lever,new Vector3d()).add(v).dot(c.axis);c.limit=limit;return c;
    }
    public static void main(String[] args) {
        var inertia=new Matrix3d().scaling(200,300,500);var v=new Vector3d(.1,0,0);var w=new Vector3d(0,.02,0);
        var cs=new ArrayList<ParkingBrakeSolver.Contact>();
        for(int side:new int[]{-1,1})cs.add(contact(new Vector3d(0,0,side*3),new Vector3d(1,0,0),v,w,100));
        check(ParkingBrakeSolver.solve(100,inertia,cs),"Brake solver failed");
        var inverse=new Matrix3d(inertia).invert();
        for(var c:cs) {v.fma(c.impulse/100,c.axis);w.add(inverse.transform(new Vector3d(c.angularAxis).mul(c.impulse)));}
        check(v.length()+w.length()<1e-8,"Two brakes must stop creep and unequal wheel yaw together");
        var random=new Random(123);
        for(int sample=0;sample<2000;sample++) {
            v.set(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5);
            w.set(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5);
            var beforeV=new Vector3d(v);var beforeW=new Vector3d(w);
            cs.clear();
            for(int i=0;i<2+sample%7;i++)cs.add(contact(new Vector3d(random.nextDouble()*8-4,-1,random.nextDouble()*8-4),
                    new Vector3d(1,0,.2*(random.nextDouble()-.5)),v,w,sample%2==0?.01:100));
            double before=energy(100,inertia,v,w);
            check(ParkingBrakeSolver.solve(100,inertia,cs),"Finite contacts must solve");
            for(var c:cs) {
                check(Math.abs(c.impulse)<=c.limit+1e-12,"Friction limit exceeded");
                v.fma(c.impulse/100,c.axis);w.add(inverse.transform(new Vector3d(c.angularAxis).mul(c.impulse)));
            }
            check(energy(100,inertia,v,w)<=before+1e-8,"Parking brakes cannot create kinetic energy");
            if(sample%2==0)check(v.distance(beforeV)+w.distance(beforeW)>0,"Weak friction must still apply a bounded impulse");
        }
        cs.clear();cs.add(contact(new Vector3d(),new Vector3d(1,0,0),new Vector3d(0,1,2),new Vector3d(),100));
        ParkingBrakeSolver.solve(100,inertia,cs);check(cs.get(0).impulse==0,"Rolling brake must not damp vertical/lateral motion");
        cs.get(0).speed=.2;cs.get(0).limit=0;ParkingBrakeSolver.solve(100,inertia,cs);
        check(cs.get(0).impulse==0,"No support/grip means no holding force");
        lateralGrip();
        System.out.println("Parking brakes: coupled creep/yaw stop, 2..8 wheels, friction bounds, no added energy and longitudinal-only impulses passed");
    }
    private static void lateralGrip() {
        var inertia=new Matrix3d().scaling(200,300,500);var inverse=new Matrix3d(inertia).invert();
        var v=new Vector3d(8,0,.1);var w=new Vector3d(0,.02,0);
        var cs=new ArrayList<ParkingBrakeSolver.Contact>();
        for(int side:new int[]{-1,1})cs.add(contact(new Vector3d(side*3,0,0),new Vector3d(0,0,1),v,w,100));
        ParkingBrakeSolver.solve(100,inertia,cs);
        for(var c:cs) {v.fma(c.impulse/100,c.axis);w.add(inverse.transform(new Vector3d(c.angularAxis).mul(c.impulse)));}
        check(Math.abs(v.x-8)<1e-12,"Lateral grip must allow free forward rolling without brakes");
        check(Math.abs(v.z)+w.length()<1e-8,"Lateral creep and yaw must stop together in grip");
        // Weakly loaded/slippery wheels cannot pin the aircraft to the ground.
        v.set(0,0,.4);w.zero();cs.clear();
        cs.add(contact(new Vector3d(),new Vector3d(0,0,1),v,w,.01));
        ParkingBrakeSolver.solve(100,inertia,cs);v.fma(cs.get(0).impulse/100,cs.get(0).axis);
        check(v.z>.39 && Math.abs(cs.get(0).impulse)<=.01,"Exceeding available friction must retain slip");
        // Longitudinal parking and lateral grip share one body solve, not two competing velocity resets.
        v.set(.1,0,.1);w.set(0,.02,0);cs.clear();
        for(int side:new int[]{-1,1})for(var axis:new Vector3d[]{new Vector3d(1,0,0),new Vector3d(0,0,1)})
            cs.add(contact(new Vector3d(side*3,0,side*3),axis,v,w,100));
        ParkingBrakeSolver.solve(100,inertia,cs);
        for(var c:cs) {v.fma(c.impulse/100,c.axis);w.add(inverse.transform(new Vector3d(c.angularAxis).mul(c.impulse)));}
        check(v.length()+w.length()<1e-5,"Combined parked grip must stop longitudinal/lateral/yaw motion");
        System.out.println("Lateral static grip: free forward rolling, stopped lateral creep/yaw, friction breakaway and combined parking passed");
    }
}
