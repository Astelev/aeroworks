package net.aste.aeroworks.mixin.client;

import net.aste.aeroworks.client.waves.WaveShader;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap=false)
public abstract class WaveShaderMixin {
    @Inject(method="getShaderSource", at=@At("RETURN"), cancellable=true, require=0)
    private static void aste_aeroworks$waveShader(ResourceLocation name, CallbackInfoReturnable<String> cir) {
        if (name.getNamespace().equals("sodium") && name.getPath().equals("blocks/block_layer_opaque.vsh"))
            cir.setReturnValue(WaveShader.patch(cir.getReturnValue()));
    }
}
