package net.aste.aeroworks.mixin.client;

import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.aste.aeroworks.client.CloudFogPostProcess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Simple Clouds has already copied DH depth here, then added cloud depth to it.
 * No DH API classes are linked; this pipeline only loads when DH is active.
 */
@Pseudo
@Mixin(targets = "dev.nonamecrackers2.simpleclouds.client.dh.pipeline.DhSupportPipeline", remap = false)
public abstract class CloudFogDhMixin {
    // Composite once, after Sodium has finished terrain, using both projections.
    @Redirect(method = "afterDistantHorizonsRender", at = @At(value = "INVOKE",
            target = "Ldev/nonamecrackers2/simpleclouds/client/renderer/SimpleCloudsRenderer;doFinalCompositePass(Lorg/joml/Matrix4f;FLorg/joml/Matrix4f;)V"))
    private void aste_aeroworks$deferComposite(SimpleCloudsRenderer renderer, Matrix4f view,
            float partialTick, Matrix4f projection) {
    }

    @Inject(method = "afterDistantHorizonsRender", at = @At("RETURN"))
    private void aste_aeroworks$captureDhDepth(Minecraft mc, SimpleCloudsRenderer renderer,
            Matrix4f modelView, Matrix4f projection, float partialTick, double x, double y, double z,
            Frustum frustum, int framebuffer, CallbackInfo ci) {
        CloudFogPostProcess.captureDhDepth(renderer.getCloudTarget().getDepthTextureId(), projection);
    }
}
