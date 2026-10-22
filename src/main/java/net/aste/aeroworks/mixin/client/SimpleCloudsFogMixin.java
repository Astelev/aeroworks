package net.aste.aeroworks.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.aste.aeroworks.client.CloudFogEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the cloud meshes in the same fog as terrain, including screen-space mode. */
@Mixin(value = SimpleCloudsRenderer.class, remap = false)
public abstract class SimpleCloudsFogMixin {
    @Inject(method = "getFogStart", at = @At("RETURN"), cancellable = true)
    private void aeroworks$cloudFogStart(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(CloudFogEvents.cloudFogStart(cir.getReturnValue()));
    }
    @Inject(method = "getFogEnd", at = @At("RETURN"), cancellable = true)
    private void aeroworks$cloudFogEnd(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(CloudFogEvents.cloudFogEnd(cir.getReturnValue()));
    }
}
