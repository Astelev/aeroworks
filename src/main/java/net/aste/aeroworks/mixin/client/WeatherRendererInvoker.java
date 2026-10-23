package net.aste.aeroworks.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface WeatherRendererInvoker {
    @Invoker("renderSnowAndRain")
    void aste_aeroworks$renderWeather(LightTexture light, float partialTick, double x, double y, double z);
}
