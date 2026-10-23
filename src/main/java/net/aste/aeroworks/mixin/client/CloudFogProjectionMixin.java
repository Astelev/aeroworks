package net.aste.aeroworks.mixin.client;

import net.aste.aeroworks.client.CloudFogPostProcess;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class CloudFogProjectionMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void aste_aeroworks$projection(DeltaTracker tracker, boolean outline, Camera camera,
            GameRenderer renderer, LightTexture light, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        CloudFogPostProcess.captureProjection(projection, tracker.getGameTimeDeltaPartialTick(true));
    }
    // NeoForge renders solid and translucent particles separately (or together
    // in Fabulous). Run once before the first particle pass, after opaque terrain.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V"))
    private void aste_aeroworks$beforeParticles(CallbackInfo ci) {
        CloudFogPostProcess.finishWorld();
    }
}
