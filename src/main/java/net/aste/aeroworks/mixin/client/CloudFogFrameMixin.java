package net.aste.aeroworks.mixin.client;

import net.aste.aeroworks.client.CloudFogPostProcess;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CloudFogFrameMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void aste_aeroworks$beginCloudFogFrame(CallbackInfo ci) {
        CloudFogPostProcess.beginFrame();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void aste_aeroworks$closeCloudFog(CallbackInfo ci) {
        CloudFogPostProcess.close();
    }
}
