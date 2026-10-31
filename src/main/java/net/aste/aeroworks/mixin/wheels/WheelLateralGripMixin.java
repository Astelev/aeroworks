package net.aste.aeroworks.mixin.wheels;

import net.aste.aeroworks.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Offroad 1.3.2 bytecode: the second queuedForce.fma is lateral; the first is rolling/braking/drive. */
@Pseudo
@Mixin(targets="dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity",remap=false)
public abstract class WheelLateralGripMixin {
    @ModifyArg(method="sable$physicsTick(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V",
            at=@At(value="INVOKE",target="Lorg/joml/Vector3d;fma(DLorg/joml/Vector3dc;)Lorg/joml/Vector3d;",ordinal=1,remap=false),
            index=0,remap=false,require=0,expect=0,allow=1)
    private double aste_aeroworks$lateralGrip(double original) {
        return original*Config.WHEEL_LATERAL_FRICTION_MULTIPLIER.get();
    }
}
