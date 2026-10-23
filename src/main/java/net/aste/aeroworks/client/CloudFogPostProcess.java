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
import org.lwjgl.opengl.GL30;
import java.io.IOException;

/** Composites terrain/cloud fog before particles and weather; late hook is fallback only. */
public final class CloudFogPostProcess {
    private static PostChain chain;
    private static int width, height;
    private static int dhDepth = -1;
    private static final Matrix4f inverseDhProjection = new Matrix4f();
    private static boolean failed;
    private static boolean worldPassComplete;
    private static final Matrix4f worldProjection = new Matrix4f();
    private static float framePartialTick;
    public static void captureProjection(Matrix4f projection, float partialTick) {
        worldProjection.set(projection);
        framePartialTick = partialTick;
    }
    public static void finishWorld() {
        if (worldPassComplete) return;
        CloudFogEvents.update(Minecraft.getInstance().gameRenderer.getMainCamera(), framePartialTick);
        render(worldProjection, framePartialTick);
        worldPassComplete = true;
    }

    private CloudFogPostProcess() {}

    public static void beginFrame() { dhDepth = -1; worldPassComplete = false; }

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
        // This can be called with the fabulous particle target already bound.
        // Save state before chain allocation too: creating targets changes GL state.
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        int oldReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldDepthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int[] oldViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, oldViewport);
        try {
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

            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            chain.process(partialTick);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, oldReadFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, oldDrawFramebuffer);
            RenderSystem.viewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
            RenderSystem.depthFunc(oldDepthFunction);
            RenderSystem.setProjectionMatrix(oldProjection, sorting);
            RenderSystem.depthMask(depthMask);
            if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }
}
