package net.aste.aeroworks.mixin.waves;

import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.aste.aeroworks.waves.ServerWaves;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=ServerLevelPlot.class,remap=false)
public abstract class WaveGeometryMixin {
    @Inject(method="onBlockChange",at=@At("TAIL"),require=1)
    private void aste_aeroworks$geometry(BlockPos pos,BlockState state,CallbackInfo ci){
        ServerWaves.changed(((ServerLevelPlot)(Object)this).getSubLevel());
    }
}
