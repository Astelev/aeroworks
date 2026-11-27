package net.aste.aeroworks.mixin.atmospheric;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.compat.atmospheric.AtmosphericWind;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.enzonic.aeronauticswinds.physics.SailTurbulencePhysics",remap=false)
public abstract class AtmosphericSailTurbulenceMixin {
    @Inject(method="computeTurbulence",at=@At("HEAD"),cancellable=true,require=1)
    private static void aeroworks$ownCloudEffects(CallbackInfoReturnable<Double> ci,@Local(argsOnly=true) ServerSubLevel body) {
        if(AtmosphericWind.managesAircraft(body))ci.setReturnValue(0d);
    }
}
