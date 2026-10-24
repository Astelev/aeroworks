package net.aste.aeroworks.mixin.failure;

import dev.ryanhcode.sable.api.block.propeller.BlockEntityPropeller;
import net.aste.aeroworks.failure.FailureSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remove thrust immediately, even while a large rotor is still coasting to a stop. */
@Mixin(value=BlockEntityPropeller.class,remap=false)
public interface PropellerFailureMixin {
    @Inject(method="getScaledThrust",at=@At("HEAD"),cancellable=true,require=1)
    private void aste_aeroworks$failedThrust(CallbackInfoReturnable<Double> cir) {
        if(FailureSystem.stopped(this))cir.setReturnValue(0d);
    }
}
