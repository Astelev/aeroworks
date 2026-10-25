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
        oldRunaway(); quadratic(); passivity(); symmetricControls(); zeroDrag(); finiteSteps(); glideAndClimb(); edgeCases();
        System.out.println("Surface aerodynamics: former runaway, two-sided controls, zero-drag coasting, soft caps, lift orthogonality, normal pressure dissipation, midpoint energy and glide/climb passed");
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
            check(f.normalDamping>=0 && f.baseDamping>=0,"No negative physical damping coefficients");
            var rotated=force(new Vector3d(v).rotateY(.73),new Vector3d(n).rotateY(.73),1);
            var original=force(v,n,1);
            near(rotated.lift.distance(new Vector3d(original.lift).rotateY(.73)),0,1e-7);
            near(rotated.drag.distance(new Vector3d(original.drag).rotateY(.73)),0,1e-7);
        }
    }
    private static void submit(PassiveImpulse integrator,Vector3dc lever,Vector3dc v,Vector3dc spin,Vector3dc n,
            double lift,double normal,double base,double liftCap,double normalCap,double dt,double mass,Matrix3d inertia,
            Vector3d impulse,Vector3d torque) {
        var point=spin.cross(lever,new Vector3d()).add(v);
        var force=new SurfaceAerodynamics.Result();
        SurfaceAerodynamics.calculate(point,n,1,lift,normal,base,liftCap,normalCap,force);
        integrator.begin();
        integrator.addSurface(lever,point,n,force.lift,force.normalDamping,force.baseDamping,dt);
        integrator.integrate(mass,inertia,v,spin,impulse,torque);
    }
    private static void symmetricControls() {
        // Simulated SymmetricSailBlock: liftScalar=0, parallelDragScalar=1.75 (verified in source).
        var v=new Vector3d(30,0,0);var r=new Vector3d(-10,0,0);var inertia=new Matrix3d().scaling(100,200,300);
        var integrator=new PassiveImpulse();var j=new Vector3d();var torque=new Vector3d();double previous=0,previousPitch=0;
        for(int sign:new int[]{-1,1}) {
            double angle=sign*Math.toRadians(15);var n=new Vector3d(Math.sin(angle),0,Math.cos(angle));
            var f=new SurfaceAerodynamics.Result();
            SurfaceAerodynamics.calculate(v,n,1,0,1.75*.35,0,40,50,f);
            check(f.lift.lengthSquared()==0 && Math.abs(f.drag.z)>1,"A zero-lift symmetric sail must retain transverse pressure");
            var flipped=new SurfaceAerodynamics.Result();
            SurfaceAerodynamics.calculate(v,new Vector3d(n).negate(),1,0,1.75*.35,0,40,50,flipped);
            near(flipped.drag.distance(f.drag),0,1e-12);
            submit(integrator,r,v,new Vector3d(),n,0,1.75*.35,0,40,50,.02,10,inertia,j,torque);
            check(Math.abs(torque.y)>1e-4,"Deflected tail must create yaw torque even with zero general drag");
            if(previous!=0)check(previous*torque.y<0,"Opposite deflection must reverse yaw");
            previous=torque.y;
            var pitchNormal=new Vector3d(Math.sin(angle),Math.cos(angle),0);
            submit(integrator,r,v,new Vector3d(),pitchNormal,0,1.75*.35,0,40,50,.02,10,inertia,j,torque);
            check(Math.abs(torque.z)>1e-4,"Deflected elevator must create pitch torque");
            if(previousPitch!=0)check(previousPitch*torque.z<0,"Opposite elevator deflection must reverse pitch");
            previousPitch=torque.z;
            submit(integrator,r,v,new Vector3d(),n,0,0,0,40,50,.02,10,inertia,j,torque);
            near(j.length()+torque.length(),0,0);
        }
        double previousRoll=0;
        for(int sign:new int[]{-1,1}) {
            integrator.begin();
            for(int side:new int[]{-1,1}) {
                double angle=sign*side*Math.toRadians(15);
                var normal=new Vector3d(Math.sin(angle),Math.cos(angle),0);
                var force=new SurfaceAerodynamics.Result();
                SurfaceAerodynamics.calculate(v,normal,1,0,1.75*.35,0,40,50,force);
                integrator.addSurface(new Vector3d(0,0,side*5),v,normal,force.lift,force.normalDamping,force.baseDamping,.02);
            }
            integrator.integrate(10,inertia,v,new Vector3d(),j,torque);
            check(Math.abs(torque.x)>1e-4,"Differential ailerons create roll");
            if(previousRoll!=0)check(previousRoll*torque.x<0,"Opposite aileron deflection reverses roll");
            previousRoll=torque.x;
        }
        var horizontal=force(new Vector3d(200,0,0),new Vector3d(0,-1,0),1);
        var limited=new SurfaceAerodynamics.Result();
        SurfaceAerodynamics.calculate(new Vector3d(200,0,0),new Vector3d(0,-1,0),1,.076,0,0,40,50,limited);
        check(limited.lift.length()<.076*40*40 && limited.lift.length()<horizontal.lift.length()/10,"Restore lift soft cap at flight speed");
    }
    private static void zeroDrag() {
        var integrator=new PassiveImpulse();var inertia=new Matrix3d().scaling(2,5,9);
        var v=new Vector3d(70,-2,5);var spin=new Vector3d(1,-2,3);var j=new Vector3d();var torque=new Vector3d();
        // Zero both Lift Patch drag coefficients: a symmetric sail must not alter ANY motion.
        submit(integrator,new Vector3d(-20,3,7),v,spin,new Vector3d(.2,1,.3),0,0,0,40,50,.5,3,inertia,j,torque);
        near(j.length()+torque.length(),0,0);
        // Cambered lift with no drag must rotate velocity without reducing its magnitude.
        var identity=new Matrix3d();v.set(30,0,0);spin.zero();
        for(int i=0;i<20000;i++) {
            submit(integrator,new Vector3d(),v,spin,new Vector3d(v.y,-v.x,0),.076,0,0,40,50,.05,1,identity,j,torque);
            v.add(j);near(v.length(),30,2e-8);near(torque.length(),0,0);
        }
    }
    private static void finiteSteps() {
        var integrator=new PassiveImpulse();var inertia=new Matrix3d().set(3,.2,.1,.2,5,.3,.1,.3,9);
        var inverse=new Matrix3d(inertia).invert();var random=new Random(27);
        for(boolean drag:new boolean[]{false,true})for(int i=0;i<2000;i++) {
            var v=new Vector3d(10+random.nextDouble()*20,-3,1);var spin=new Vector3d(1,-.5,2);
            var j=new Vector3d();var torque=new Vector3d();double before=energy(3,inertia,v,spin);
            submit(integrator,new Vector3d(4,-2,7),v,spin,new Vector3d(.2,-1,.4),.076,drag?.2625:0,drag?.0068:0,
                    40,50,i%2==0?.01:2,3,inertia,j,torque);
            v.fma(1.0/3,j);inverse.transform(torque,new Vector3d()).add(spin,spin);
            double after=energy(3,inertia,v,spin);
            check(after<=before+1e-7,"Coupled surface impulse cannot create energy");
            if(!drag)near(after,before,1e-7);
        }
        // Wing + two symmetric controls share one coupled body solve (not independent damping passes).
        var v=new Vector3d(25,0,0);var spin=new Vector3d();var j=new Vector3d();var torque=new Vector3d();
        integrator.begin();
        for(var lever:new Vector3d[]{new Vector3d(0,0,5),new Vector3d(0,0,-5),new Vector3d(-10,0,0)}) {
            var f=force(v,new Vector3d(.1,-1,0),1);
            integrator.addSurface(lever,v,new Vector3d(.1,-1,0),f.lift,0,0,.05);
        }
        double before=energy(3,inertia,v,spin);
        integrator.integrate(3,inertia,v,spin,j,torque);v.fma(1.0/3,j);inverse.transform(torque,spin);
        near(energy(3,inertia,v,spin),before,1e-7);
    }
    private static void glideAndClimb() {
        var filter=new PassiveImpulse();var identity=new Matrix3d();var zero=new Vector3d();
        for(boolean climbing:new boolean[]{false,true}) {
            var v=new Vector3d(30,climbing?10:0,0);double height=200,dt=.01;
            double initial=.5*v.lengthSquared()+9.81*height;boolean traded=false;
            for(int i=0;i<1000;i++) {
                var j=new Vector3d();var torque=new Vector3d();
                submit(filter,zero,v,zero,new Vector3d(0,-1,0),.012,0,.0005,40,50,dt,1,identity,j,torque);
                v.add(j);
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
