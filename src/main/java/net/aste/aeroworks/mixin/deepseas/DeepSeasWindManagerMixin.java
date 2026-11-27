package net.aste.aeroworks.mixin.deepseas;
import com.maxenonyme.highseas.wind.WindSample;
import net.aste.aeroworks.compat.deepseas.DeepSeasWind;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.maxenonyme.highseas.wind.WindManager",remap=false)
public abstract class DeepSeasWindManagerMixin {
    @Inject(method="getWind(Lnet/minecraft/world/level/Level;DDD)Lcom/maxenonyme/highseas/wind/WindSample;",at=@At("HEAD"),cancellable=true)
    private static void aste_aeroworks$wind(Level level,double x,double y,double z,CallbackInfoReturnable<WindSample> cir){
        // Weather, altitude and gusts are already in AeroWorks' vector: never multiply twice.
        cir.setReturnValue(new WindSample(DeepSeasWind.at(level,x,y,z),1,1,1,1,1));
    }
}
