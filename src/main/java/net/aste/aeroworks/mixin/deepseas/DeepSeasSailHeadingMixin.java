package net.aste.aeroworks.mixin.deepseas;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.aste.aeroworks.Config;
import net.aste.aeroworks.compat.deepseas.*;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="com.maxenonyme.highseas.sail.SailWindSystem",remap=false)
public abstract class DeepSeasSailHeadingMixin {
    @Shadow private static boolean afloat(ServerLevel level,SubLevel body){throw new AssertionError();}
    @Inject(method="keel",at=@At("HEAD"),cancellable=true,require=1)
    private static void aeroworks$sailBow(SubLevel body,long tick,Vector3dc velocity,CallbackInfoReturnable<Vector3d> cir){
        if(!(body instanceof ServerSubLevel root)||!afloat(root.getLevel(),root))return;
        var heading=DeepSeasSailHeading.forRoot(root,tick).vector();
        if(!SailHeadingMath.normalize(heading)){cir.setReturnValue(null);return;}
        var wind=DeepSeasWind.forBody(root.getLevel(),root);
        cir.setReturnValue(SailHeadingMath.drift(heading,wind.x,wind.z,Config.DEEP_SEAS_SAIL_DRIFT.get(),new Vector3d()));
    }
}
