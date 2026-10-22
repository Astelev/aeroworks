package net.aste.aeroworks.client;

import com.mojang.blaze3d.shaders.FogShape;
import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.cloud.CloudDensity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = Aeroworks.MODID, value = Dist.CLIENT)
public final class CloudFogEvents {
    private static ClientLevel lastLevel;
    private static double lastTime = Double.NaN;
    private static Vec3 lastPos;
    private static float strength;

    private CloudFogEvents() {}

    public static float strength() {
        return Minecraft.getInstance().level == lastLevel ? strength : 0;
    }

    private static float update(Camera camera, float partialTick) {
        var level = Minecraft.getInstance().level;
        if (level == null || !CloudFogConfig.ENABLED.get() || camera.getFluidInCamera() != FogType.NONE
                || !SimpleCloudsRenderer.canRenderInDimension(level) || !SimpleCloudsConfig.CLIENT.renderClouds.get()) {
            reset();
            return 0;
        }
        double time = level.getGameTime() + partialTick;
        Vec3 pos = camera.getPosition();
        if (lastLevel == level && time == lastTime && pos.equals(lastPos)) return strength;
        var renderer = SimpleCloudsRenderer.getOptionalInstance().orElse(null);
        if (renderer == null || renderer.getMeshGenerator() == null || !renderer.getMeshGenerator().canRender()) {
            reset();
            return 0;
        }
        var manager = CloudManager.get(level);
        float target = 0;
        // AMBIENT deliberately removes clouds around the camera in its mesh shader.
        if (manager.getCloudMode() != CloudMode.AMBIENT) {
            var info = manager.getCloudTypeAtWorldPos((float) pos.x, (float) pos.z);
            var layers = info.getLeft().noiseConfig().packForShader();
            float density = CloudDensity.sample(layers, (float) (pos.x / 8),
                    (float) ((pos.y - manager.getCloudHeight()) / 8), (float) (pos.z / 8),
                    manager.getScrollX(partialTick), manager.getScrollY(partialTick), manager.getScrollZ(partialTick), info.getRight());
            target = CloudDensity.strength(density, CloudFogConfig.BOUNDARY_WIDTH.get().floatValue());
        }
        boolean reset = level != lastLevel || lastPos == null || pos.distanceToSqr(lastPos) > 128 * 128 || time < lastTime;
        strength = reset ? target : CloudDensity.smooth(strength, target, time - lastTime, CloudFogConfig.TRANSITION_TICKS.get());
        if (strength < .0001f) strength = 0;
        lastLevel = level;
        lastTime = time;
        lastPos = pos;
        return strength;
    }

    private static void reset() {
        lastLevel = null;
        lastPos = null;
        lastTime = Double.NaN;
        strength = 0;
    }

    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        float amount = update(event.getCamera(), (float) event.getPartialTick());
        if (amount == 0) return;
        float end = CloudDensity.fogEnd(event.getFarPlaneDistance(), CloudFogConfig.VISIBILITY.get().floatValue(), amount);
        event.setFarPlaneDistance(end);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance() * (1 - amount), end));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void color(ViewportEvent.ComputeFogColor event) {
        float amount = update(event.getCamera(), (float) event.getPartialTick());
        if (amount == 0) return;
        float[] color = SimpleCloudsRenderer.getInstance().getCloudColor((float) event.getPartialTick());
        event.setRed(event.getRed() + (color[0] - event.getRed()) * amount);
        event.setGreen(event.getGreen() + (color[1] - event.getGreen()) * amount);
        event.setBlue(event.getBlue() + (color[2] - event.getBlue()) * amount);
    }

    public static float cloudFogEnd(float original) {
        return CloudDensity.fogEnd(original, CloudFogConfig.VISIBILITY.get().floatValue(), strength());
    }

    public static float cloudFogStart(float original) {
        return original * (1 - strength());
    }
}
