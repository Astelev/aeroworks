package net.aste.aeroworks.mixin.client;

import net.aste.aeroworks.client.waves.WaveRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer", remap=false)
public abstract class WaveRendererMixin {
    @Inject(method="begin", at=@At("TAIL"), require=0)
    private void aste_aeroworks$wavesBegin(CallbackInfo ci) { WaveRenderer.begin(); }
    @Inject(method="end", at=@At("HEAD"), require=0)
    private void aste_aeroworks$wavesEnd(CallbackInfo ci) { WaveRenderer.end(); }
}
