package net.aste.aeroworks.mixin.waves;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.aste.aeroworks.waves.ServerWaves;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ServerSubLevel.class,remap=false)
public abstract class SolidWaveBuoyancyMixin {
    @Inject(method="prePhysicsTick",at=@At("TAIL"),require=1)
    private void aste_aeroworks$solidWaves(SubLevelPhysicsSystem physics,RigidBodyHandle handle,double dt,CallbackInfo ci){
        ServerWaves.solidStep((ServerSubLevel)(Object)this,handle,dt);
    }
}
