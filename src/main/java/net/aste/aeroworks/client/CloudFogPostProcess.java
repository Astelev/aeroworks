package net.aste.aeroworks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.mixin.client.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import java.io.IOException;

/** Runs after the complete world, before the hand depth-clear and HUD. */
public final class CloudFogPostProcess {
    private static PostChain chain;
    private static int width, height;
    private static int dhDepth = -1;
    private static final Matrix4f inverseDhProjection = new Matrix4f();
    private static boolean failed;
    private static final Matrix4f worldProjection = new Matrix4f();
    private static float framePartialTick;
    public static void captureProjection(Matrix4f projection, float partialTick) {
        worldProjection.set(projection);
        framePartialTick = partialTick;
    }
    public static void finishWorld() {
        CloudFogEvents.update(Minecraft.getInstance().gameRenderer.getMainCamera(), framePartialTick);
        render(worldProjection, framePartialTick);
    }

    private CloudFogPostProcess() {}

    public static void beginFrame() { dhDepth = -1; }

    public static void captureDhDepth(int depthTexture, Matrix4f projection) {
        dhDepth = depthTexture;
        inverseDhProjection.set(projection).invert();
    }

    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> close());
    }

    public static void close() {
        if (chain != null) chain.close();
        chain = null;
        failed = false;
        dhDepth = -1;
    }

    public static void render(Matrix4f projection, float partialTick) {
        float amount = CloudFogEvents.strength();
        if ((amount <= 0 && dhDepth < 0) || failed) return;
        Minecraft mc = Minecraft.getInstance();
        var main = mc.getMainRenderTarget();
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), main,
                        ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID, "shaders/post/cloud_fog.json"));
                chain.resize(main.width, main.height);
                width = main.width;
                height = main.height;
            } else if (width != main.width || height != main.height) {
                chain.resize(main.width, main.height);
                width = main.width;
                height = main.height;
            }
        } catch (IOException | RuntimeException error) {
            close();
            failed = true;
            Aeroworks.LOGGER.error("Could not create cloud fog post-process. Reload resources (F3+T) after fixing shader resources.", error);
            return;
        }

        var renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        if (renderer == null) return;
        float gray = CloudFogEvents.gray(partialTick, amount);
        Matrix4f inverseProjection = new Matrix4f(projection).invert();
        int depthTexture = dhDepth >= 0 ? dhDepth : main.getDepthTextureId();
        for (var pass : ((PostChainAccessor) chain).aste_aeroworks$passes()) {
            var effect = pass.getEffect();
            effect.setSampler("DhDepthSampler", () -> depthTexture);
            effect.setSampler("CloudsSampler", () -> renderer.getCloudTarget().getColorTextureId());
            effect.setSampler("AccumSampler", () -> renderer.getCloudTransparencyTarget().getColorTextureId());
            effect.setSampler("RevealSampler", () -> renderer.getCloudTransparencyTarget().getRevealageTextureId());
            effect.safeGetUniform("HasTransparency").set(renderer.getMeshGenerator().transparencyEnabled() ? 1 : 0);
            effect.safeGetUniform("InverseProjection").set(inverseProjection);
            effect.safeGetUniform("InverseDhProjection").set(inverseDhProjection);
            effect.safeGetUniform("HasDhDepth").set(dhDepth >= 0 ? 1 : 0);
            effect.safeGetUniform("Visibility").set(CloudFogConfig.VISIBILITY.get().floatValue());
            effect.safeGetUniform("Strength").set(amount);
            effect.safeGetUniform("FogColor").set(gray, gray, gray);
        }

        // PostPass changes GL state and projection. Restore it before rendering the hand/GUI.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        try {
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            chain.process(partialTick);
        } finally {
            main.bindWrite(true);
            RenderSystem.setProjectionMatrix(oldProjection, sorting);
            RenderSystem.depthMask(depthMask);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }
}
