package net.aste.aeroworks.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/** A draw's camera/fog state, captured where vanilla intended to draw it. */
final class DeferredEffectState {
    private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
    private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix());
    private final com.mojang.blaze3d.vertex.VertexSorting sorting = RenderSystem.getVertexSorting();
    private final float fogStart = RenderSystem.getShaderFogStart();
    private final float fogEnd = RenderSystem.getShaderFogEnd();
    private final FogShape fogShape = RenderSystem.getShaderFogShape();
    private final float[] fogColor = RenderSystem.getShaderFogColor().clone();
    private final float[] shaderColor = RenderSystem.getShaderColor().clone();
    private final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
    private final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
    private final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
    private final int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
    private final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
    private final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
    private final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int[] viewport = new int[4];

    DeferredEffectState() {
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
    }

    private void apply() {
        RenderSystem.getModelViewStack().set(modelView);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogShape(fogShape);
        RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
        RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        RenderSystem.depthMask(depthMask);
        RenderSystem.depthFunc(depthFunc);
        if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    void draw(Runnable draw) {
        var previous = new DeferredEffectState();
        var stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        try {
            apply();
            // Fabulous auxiliary targets have already been composited. Draw
            // queued effects onto the finished main scene, against its depth.
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            draw.run();
        } finally {
            stack.popMatrix();
            previous.apply();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previous.readFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous.drawFbo);
            RenderSystem.viewport(previous.viewport[0], previous.viewport[1], previous.viewport[2], previous.viewport[3]);
        }
    }
}
