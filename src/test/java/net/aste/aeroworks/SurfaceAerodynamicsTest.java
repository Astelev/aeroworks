package net.aste.aeroworks;

import java.util.Random;
import net.aste.aeroworks.aerodynamics.PassiveImpulse;
import net.aste.aeroworks.aerodynamics.SurfaceAerodynamics;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SurfaceAerodynamicsTest {
    private static final double INF=Double.POSITIVE_INFINITY;
    private static void check(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static void near(double a,double b,double tolerance) {check(Math.abs(a-b)<=tolerance,"Mismatch "+a+" / "+b);}
    private static SurfaceAerodynamics.Result force(Vector3dc v,Vector3dc n,double density) {
        var result=new SurfaceAerodynamics.Result();
        SurfaceAerodynamics.calculate(v,n,density,.076,.2625,.0068882026,INF,INF,result);return result;
    }
    private static double energy(double mass,Matrix3d inertia,Vector3dc v,Vector3dc w) {
        return .5*(mass*v.lengthSquared()+w.dot(inertia.transform(w,new Vector3d())));
    }
    public static void main(String[] args) {
        oldRunaway(); quadratic(); passivity(); finiteSteps(); glideAndClimb(); edgeCases();
        System.out.println("Surface aerodynamics: former tilted-sail positive work, quadratic forces, lift orthogonality, drag dissipation, COM/rotation work, finite-step energy, glide/climb and edge cases passed");
    }
    private static void oldRunaway() {
        double angle=Math.toRadians(15),dt=1.0/60;
        var n=new Vector3d(-Math.sin(angle),-Math.cos(angle),0);
        var v=new Vector3d(20,0,0);
        // The old patch sends lift down the signed surface normal and feeds a drag impulse back into speed.
        double normal=v.dot(n);
        double d=Math.copySign(normal*normal/(1+normal*normal/(50*50)),normal)*.2625*dt;
        var normalImpulse=new Vector3d(n).mul(d);
        double square=new Vector3d(v).sub(normalImpulse).lengthSquared();
        var oldImpulse=new Vector3d(n).mul(-square/(1+square/(40*40))*.076*dt).sub(normalImpulse)
                .fma(-v.length()*.0068882026*dt,v);
        check(oldImpulse.dot(v)>0,"The reported tilted-sail energy source is reproducible with stock patch tuning");
        var result=force(v,n,1);
        near(result.lift.dot(v),0,1e-10);
        check(new Vector3d(result.lift).add(result.drag).dot(v)<0,"The corrected sail cannot act as a motor");
    }
    private static void quadratic() {
        var v=new Vector3d(17,-3,7);var n=new Vector3d(.3,-.8,.5);
        var a=force(v,n,1);var b=force(new Vector3d(v).mul(2),n,1);var thin=force(v,n,.5);
        near(b.lift.distance(new Vector3d(a.lift).mul(4)),0,1e-10);
        near(b.drag.distance(new Vector3d(a.drag).mul(4)),0,1e-10);
        near(thin.lift.distance(new Vector3d(a.lift).mul(.5)),0,1e-10);
        near(thin.drag.distance(new Vector3d(a.drag).mul(.5)),0,1e-10);
        var capped=new SurfaceAerodynamics.Result();
        SurfaceAerodynamics.calculate(v,n,1,.076,.2625,.0068882026,1,1,capped);
        check(capped.lift.length()<a.lift.length(),"Caps are an explicit optional non-quadratic mode");
        var flat=force(new Vector3d(20,0,0),new Vector3d(0,-1,0),1);
        check(flat.lift.y>0,"Preserve Sable's signed normal and cambered zero-incidence lift convention");
    }
    private static void passivity() {
        var random=new Random(7294);
        for(int i=0;i<10000;i++) {
            var v=new Vector3d(random.nextDouble()*200-100,random.nextDouble()*200-100,random.nextDouble()*200-100);
            var n=new Vector3d(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5);
            var f=force(v,n,random.nextDouble()*2);
            near(f.lift.dot(v),0,1e-7);
            check(f.drag.dot(v)<=1e-10,"Drag always dissipates local air-relative energy");
            near(new Vector3d(f.drag).cross(v).length(),0,1e-7);
            var rotated=force(new Vector3d(v).rotateY(.73),new Vector3d(n).rotateY(.73),1);
            var original=force(v,n,1);
            near(rotated.lift.distance(new Vector3d(original.lift).rotateY(.73)),0,1e-7);
            near(rotated.drag.distance(new Vector3d(original.drag).rotateY(.73)),0,1e-7);
        }
    }
    private static void finiteSteps() {
        var filter=new PassiveImpulse();var identity=new Matrix3d();
        var v=new Vector3d(20,0,0);var w=new Vector3d();var f=new SurfaceAerodynamics.Result();
        double initial=.5*v.lengthSquared();
        for(int i=0;i<20000;i++) {
            SurfaceAerodynamics.calculate(v,new Vector3d(0,-1,0),1,.076,0,0,INF,INF,f);
            var j=new Vector3d(f.lift).mul(.05);var torque=new Vector3d();
            filter.correct(1,identity,identity,v,w,j,torque);v.add(j);
            near(.5*v.lengthSquared(),initial,2e-7);
        }
        // Full 6D work identity, off-center surfaces and anisotropic inertia.
        var inertia=new Matrix3d().scaling(2,5,9);var inverse=new Matrix3d(inertia).invert();
        var random=new Random(27);
        for(int i=0;i<2000;i++) {
            var linear=new Vector3d(10+random.nextDouble()*20,-3,1);
            var spin=new Vector3d(1,-.5,2);var lever=new Vector3d(4,-2,7);
            var point=spin.cross(lever,new Vector3d()).add(linear);
            var surface=force(point,new Vector3d(.2,-1,.4),1);
            var j=new Vector3d(surface.lift).add(surface.drag).mul(i%2==0?.01:2);
            var torque=lever.cross(j,new Vector3d());
            near(linear.dot(j)+spin.dot(torque),point.dot(j),1e-7);
            check(point.dot(j)<=1e-7,"COM and rotational aerodynamic work must agree");
            double before=energy(3,inertia,linear,spin);
            filter.correct(3,inertia,inverse,linear,spin,j,torque);
            linear.fma(1.0/3,j);inverse.transform(torque,new Vector3d()).add(spin,spin);
            check(energy(3,inertia,linear,spin)<=before+1e-7,"No translational/rotational energy created by finite aerodynamic kicks");
        }
        var fast=new Vector3d(20,0,0);var brake=new Vector3d(-10000,0,0);var torque=new Vector3d();
        filter.correct(1,identity,identity,fast,new Vector3d(),brake,torque);
        near(fast.add(brake).length(),0,1e-10); // A huge drag step stops instead of reversing into runaway.
    }
    private static void glideAndClimb() {
        var filter=new PassiveImpulse();var identity=new Matrix3d();var zero=new Vector3d();
        for(boolean climbing:new boolean[]{false,true}) {
            var v=new Vector3d(30,climbing?10:0,0);double height=200,dt=.01;
            double initial=.5*v.lengthSquared()+9.81*height;boolean traded=false;
            for(int i=0;i<1000;i++) {
                var result=new SurfaceAerodynamics.Result();
                SurfaceAerodynamics.calculate(v,new Vector3d(0,-1,0),1,.012,0,.0005,INF,INF,result);
                var j=new Vector3d(result.lift).add(result.drag).mul(dt);var torque=new Vector3d();
                filter.correct(1,identity,identity,v,zero,j,torque);v.add(j);
                height+=v.y*dt-.5*9.81*dt*dt;v.y-=9.81*dt;
                check(.5*v.lengthSquared()+9.81*height<=initial+1e-7,"Engineless flight cannot gain mechanical energy");
                if(climbing && height>200.1 && v.length()<Math.sqrt(1000))traded=true;
            }
            if(climbing)check(traded,"A climb spends initial speed");
            else check(height<200,"A glider loses height instead of self-propelling");
        }
    }
    private static void edgeCases() {
        var zero=force(new Vector3d(),new Vector3d(0,-1,0),1);near(zero.lift.length()+zero.drag.length(),0,0);
        var vacuum=force(new Vector3d(20,0,0),new Vector3d(0,-1,0),0);near(vacuum.lift.length()+vacuum.drag.length(),0,0);
        var faceOn=force(new Vector3d(0,-20,0),new Vector3d(0,-1,0),1);near(faceOn.lift.length(),0,1e-12);
        var invalid=force(new Vector3d(Double.NaN,0,0),new Vector3d(0,-1,0),1);near(invalid.lift.length()+invalid.drag.length(),0,0);
    }
}
