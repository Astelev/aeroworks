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

    // Fallback only: the ordinary pass already ran before particles/weather.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            shift = At.Shift.AFTER))
    private void aste_aeroworks$finishWorld(CallbackInfo ci) {
        CloudFogPostProcess.finishWorld();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void aste_aeroworks$closeCloudFog(CallbackInfo ci) {
        CloudFogPostProcess.close();
    }
}
