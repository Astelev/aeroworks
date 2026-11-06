package net.aste.aeroworks.mixin.client;

import net.aste.aeroworks.client.waves.WaveShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.GlShader", remap=false)
public abstract class WaveShaderMixin {
    // Patch the final source, after Veil and any resource-loader replacements.
    // A changed upload contract must fail validation instead of silently losing the effect.
    @ModifyArg(method="<init>", at=@At(value="INVOKE", target=
            "Lnet/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds;safeShaderSource(ILjava/lang/CharSequence;)V"),
            index=1, require=1)
    private static CharSequence aste_aeroworks$waveShader(CharSequence source) {
        return WaveShader.compiledSource(source.toString());
    }
}
