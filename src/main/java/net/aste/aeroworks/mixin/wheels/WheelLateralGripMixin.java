package net.aste.aeroworks.mixin.wheels;

import net.aste.aeroworks.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Offroad 1.3.2: change only the coefficient of velocity.dot(sideD), after terrain contact is established. */
@Pseudo
@Mixin(targets="dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity",remap=false)
public abstract class WheelLateralGripMixin {
    @ModifyConstant(method="sable$physicsTick",constant=@Constant(doubleValue=-0.6),require=1,expect=1,allow=1)
    private double aste_aeroworks$lateralGrip(double original) {
        return original*Config.WHEEL_LATERAL_FRICTION_MULTIPLIER.get();
    }
}
