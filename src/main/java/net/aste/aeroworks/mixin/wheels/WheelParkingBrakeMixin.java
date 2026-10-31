package net.aste.aeroworks.mixin.wheels;

import java.util.Collection;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.api.physics.force.ForceTotal;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.Config;
import net.aste.aeroworks.wheels.ParkingBrakes;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity",remap=false)
public abstract class WheelParkingBrakeMixin implements ParkingBrakes.Access {
    @Shadow @Final private static Collection<?> queuedWheelMounts;
    @Shadow private Vector3d queuedForcePos;
    @Shadow @Final private ForceTotal forceTotal;
    @Shadow private double touchingFriction;
    @Unique private final ParkingBrakes.State aste_aeroworks$brake=new ParkingBrakes.State();
    @Unique private final Vector3d aste_aeroworks$brakeImpulse=new Vector3d();
    public ParkingBrakes.State aste_aeroworks$parkingBrake() {return aste_aeroworks$brake;}

    @Inject(method="sable$physicsTick(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V",
            at=@At("HEAD"),remap=false,require=0,expect=0,allow=1)
    private void aste_aeroworks$resetBrake(CallbackInfo ci) {
        aste_aeroworks$brake.body=null;aste_aeroworks$brake.prepared=false;
    }
    @ModifyArg(method="sable$physicsTick(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V",
            at=@At(value="INVOKE",target="Lorg/joml/Vector3d;fma(DLorg/joml/Vector3dc;)Lorg/joml/Vector3d;",ordinal=0,remap=false),
            index=0,remap=false,require=0,expect=0,allow=1)
    private double aste_aeroworks$holdBrake(double original,@Local(argsOnly=true) ServerSubLevel body,
            @Local(name="localVelocity") Vector3d velocity,@Local(name="normalD") Vector3dc axis,
            @Local(name="brakeStrength") double brake,@Local(name="springForce") double support) {
        if(!Config.WHEEL_PARKING_BRAKE.get()||brake<.999||support<=0||!Double.isFinite(support))return original;
        double speed=velocity.dot(axis);
        if(!Double.isFinite(speed)||Math.abs(speed)>Config.WHEEL_PARKING_SPEED.get())return original;
        var state=aste_aeroworks$brake;var c=state.contact;
        c.axis.set(axis).normalize();c.lever.set(queuedForcePos).sub(body.getMassTracker().getCenterOfMass());
        c.speed=speed;c.limit=support*Math.max(0,touchingFriction)*Config.WHEEL_STATIC_FRICTION.get();
        c.fallback=original;c.impulse=original;state.body=body;
        // The shared batch replaces this longitudinal impulse, retaining the untouched lateral/suspension forces.
        return 0;
    }
    @Inject(method="applyAllBatchedForces(Lnet/minecraft/server/level/ServerLevel;D)V",at=@At("HEAD"),
            remap=false,require=0,expect=0,allow=1)
    private static void aste_aeroworks$solveBrakes(CallbackInfo ci) {ParkingBrakes.prepare(queuedWheelMounts);}
    @Inject(method="applyBatchedForces",at=@At("HEAD"),remap=false,require=0,expect=0,allow=1)
    private void aste_aeroworks$applyBrake(CallbackInfo ci) {
        var state=aste_aeroworks$brake;
        if(state.body!=null) {
            double impulse=state.prepared?state.contact.impulse:state.contact.fallback;
            aste_aeroworks$brakeImpulse.set(state.contact.axis).mul(impulse);
            forceTotal.applyImpulseAtPoint(state.body,queuedForcePos,aste_aeroworks$brakeImpulse);
            state.body=null;
        }
    }
}
