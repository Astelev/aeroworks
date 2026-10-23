package net.aste.aeroworks.client;

import dev.nonamecrackers2.simpleclouds.api.common.cloud.CloudMode;
import dev.nonamecrackers2.simpleclouds.client.renderer.SimpleCloudsRenderer;
import dev.nonamecrackers2.simpleclouds.common.config.SimpleCloudsConfig;
import dev.nonamecrackers2.simpleclouds.common.world.CloudManager;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.cloud.CloudDensity;
import net.aste.aeroworks.cloud.FogSampleClock;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.shaders.FogShape;
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
    private static final FogSampleClock sampleClock = new FogSampleClock();
    private static float target;
    private static Object packedNoise;
    private static float[] packedLayers;
    private static double packedTime;
    private static final CloudHeightBounds heightBounds = new CloudHeightBounds();

    private CloudFogEvents() {}

    public static float strength() {
        return Minecraft.getInstance().level == lastLevel ? strength : 0;
    }

    static float gray(float partialTick, float amount) {
        float[] cloud = SimpleCloudsRenderer.getInstance().getCloudColor(partialTick);
        return CloudDensity.gray(cloud[0], cloud[1], cloud[2], amount,
                CloudFogConfig.EDGE_FOG_BRIGHTNESS.get().floatValue(),
                CloudFogConfig.DENSE_FOG_BRIGHTNESS.get().floatValue());
    }

    static float update(Camera camera, float partialTick) {
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
        boolean reset = level != lastLevel || lastPos == null || pos.distanceToSqr(lastPos) > 128 * 128 || time < lastTime;
        if (reset) { sampleClock.reset(); packedNoise = null; heightBounds.reset(); }
        // Only metadata is consulted here: no region lookup, packing or noise below/above clouds.
        if (manager.getCloudMode() == CloudMode.AMBIENT || !heightBounds.contains(manager, time, pos.y)) {
            target = 0;
            sampleClock.reset(); // Entering a possible layer must sample immediately.
        } else if (sampleClock.shouldSample(time)) {
            target = 0;
            var info = manager.getCloudTypeAtWorldPos((float) pos.x, (float) pos.z);
            var noise = info.getLeft().noiseConfig();
            if (packedNoise != noise || time - packedTime >= 20) {
                packedLayers = noise.packForShader();
                packedNoise = noise;
                packedTime = time;
            }
            var layers = packedLayers;
            float density = CloudDensity.sample(layers, (float) (pos.x / 8),
                    (float) ((pos.y - manager.getCloudHeight()) / 8), (float) (pos.z / 8),
                    manager.getScrollX(partialTick), manager.getScrollY(partialTick), manager.getScrollZ(partialTick), info.getRight());
            target = CloudDensity.edgeStrength(density + CloudFogConfig.DENSITY_OFFSET.get().floatValue(),
                    CloudFogConfig.BOUNDARY_WIDTH.get().floatValue(),
                    CloudFogConfig.EDGE_DENSITY_MARGIN.get().floatValue());
            sampleClock.record(density, target);
        }
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
        sampleClock.reset();
        heightBounds.reset();
        packedNoise = null;
        packedLayers = null;
    }

    static void invalidate() { reset(); }

    @SubscribeEvent
    public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        CloudFogPostProcess.close();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void color(ViewportEvent.ComputeFogColor event) {
        float amount = update(event.getCamera(), (float) event.getPartialTick());
        if (amount <= 0) return;
        float gray = gray((float) event.getPartialTick(), amount);
        event.setRed(event.getRed() + (gray - event.getRed()) * amount);
        event.setGreen(event.getGreen() + (gray - event.getGreen()) * amount);
        event.setBlue(event.getBlue() + (gray - event.getBlue()) * amount);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        float amount = update(event.getCamera(), (float) event.getPartialTick());
        if (amount <= 0) return;
        float far = CloudFogConfig.VISIBILITY.get().floatValue() / Math.max(amount, .001f);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * .1f));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }
}
