package net.aste.aeroworks.aerodynamics;

import org.joml.Matrix3dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Removes only finite-step numerical energy from the combined aerodynamic impulse, including rotation. */
public final class PassiveImpulse {
    private final Vector3d nextVelocity=new Vector3d(),nextSpin=new Vector3d(),temporary=new Vector3d();
    public void correct(double mass,Matrix3dc inertia,Matrix3dc inverseInertia,Vector3dc airVelocity,
            Vector3dc spin,Vector3d impulse,Vector3d torque) {
        if(!Double.isFinite(mass)||mass<=0||!airVelocity.isFinite()||!spin.isFinite()
                ||!impulse.isFinite()||!torque.isFinite()) {impulse.zero();torque.zero();return;}
        double initial=.5*(mass*airVelocity.lengthSquared()+spin.dot(inertia.transform(spin,temporary)));
        double work=impulse.dot(airVelocity)+torque.dot(spin);
        nextVelocity.set(impulse).div(mass).add(airVelocity);
        inverseInertia.transform(torque,nextSpin).add(spin);
        double proposed=.5*(mass*nextVelocity.lengthSquared()+nextSpin.dot(inertia.transform(nextSpin,temporary)));
        double target=Math.max(0,initial+Math.min(0,work));
        if(!Double.isFinite(initial)||!Double.isFinite(proposed)||!Double.isFinite(target)||initial<0||proposed<0) {
            impulse.zero();torque.zero();return;
        }
        if(proposed>target && proposed>0) {
            double scale=Math.sqrt(target/proposed);
            impulse.set(nextVelocity).mul(scale).sub(airVelocity).mul(mass);
            temporary.set(nextSpin).mul(scale).sub(spin);
            inertia.transform(temporary,torque);
        }
    }
}
