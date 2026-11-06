package net.aste.aeroworks.mixin.deepseas;
import com.maxenonyme.highseas.wind.WindSample;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.compat.deepseas.DeepSeasWind;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
@Pseudo
@Mixin(targets="com.maxenonyme.highseas.sail.SailWindSystem",remap=false)
public abstract class DeepSeasSailWindMixin {
    @WrapOperation(method="pull",at=@At(value="INVOKE",target="Lcom/maxenonyme/highseas/wind/WindManager;getWind(Lnet/minecraft/world/level/Level;DDD)Lcom/maxenonyme/highseas/wind/WindSample;"),require=1,allow=1)
    private static WindSample aste_aeroworks$bodyWind(Level level,double x,double y,double z,Operation<WindSample> original,
            @Local(argsOnly=true) ServerSubLevel body,@Local(argsOnly=true) ServerLevel server){
        return new WindSample(DeepSeasWind.forBody(server,body),1,1,1,1,1);
    }
}
