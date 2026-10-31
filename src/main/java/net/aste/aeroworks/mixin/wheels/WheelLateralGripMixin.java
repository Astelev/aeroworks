package net.aste.aeroworks.mixin.wheels;

import net.aste.aeroworks.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Optional source-contract hook: incompatible Offroad bytecode must never prevent the pack from starting. */
@Pseudo
@Mixin(targets="dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity",remap=false)
public abstract class WheelLateralGripMixin {
    @ModifyConstant(method="sable$physicsTick",constant=@Constant(doubleValue=-0.6),require=0,expect=0,allow=1)
    private double aste_aeroworks$lateralGrip(double original) {
        return original*Config.WHEEL_LATERAL_FRICTION_MULTIPLIER.get();
    }
}
