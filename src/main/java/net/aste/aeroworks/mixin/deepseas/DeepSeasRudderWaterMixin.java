package net.aste.aeroworks.mixin.deepseas;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.aste.aeroworks.compat.NativeWaterSurface;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the native medium decision; do not issue a second fluid/world query. */
@Pseudo
@Mixin(targets="com.maxenonyme.highseas.block.RudderBlock",remap=false)
public abstract class DeepSeasRudderWaterMixin implements NativeWaterSurface {
    @Unique private static final ThreadLocal<Boolean> aeroworks$water=ThreadLocal.withInitial(()->false);
    @Inject(method="sable$contributeLiftAndDrag",at=@At("HEAD"),require=1)
    private void aeroworks$beginMedium(CallbackInfo ci){aeroworks$water.set(false);}
    @WrapOperation(method="sable$contributeLiftAndDrag",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/level/material/FluidState;is(Lnet/minecraft/tags/TagKey;)Z"),require=1,allow=1)
    private boolean aeroworks$rememberMedium(FluidState state,TagKey<Fluid> tag,Operation<Boolean> original){
        boolean wet=original.call(state,tag);aeroworks$water.set(wet);return wet;
    }
    @Override public boolean aeroworks$usedWater(){return aeroworks$water.get();}
}
