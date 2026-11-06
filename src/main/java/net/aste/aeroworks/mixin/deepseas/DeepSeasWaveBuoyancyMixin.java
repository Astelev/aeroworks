package net.aste.aeroworks.mixin.deepseas;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.UUID;
import net.aste.aeroworks.waves.ServerWaves;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo
@Mixin(targets="com.maxenonyme.highseas.BoatBuoyancySystem",remap=false)
public abstract class DeepSeasWaveBuoyancyMixin {
    @Inject(method="scan",at=@At("RETURN"),require=1)
    private static void aste_aeroworks$track(ServerLevel level,SubLevel body,UUID id,CallbackInfoReturnable<Object> cir){
        ServerWaves.track(level,body,cir.getReturnValue()!=null);
    }
    @Inject(method="apply",at=@At("HEAD"),require=1)
    private static void aste_aeroworks$begin(CallbackInfo ci,@Local(argsOnly=true) ServerSubLevel body){ServerWaves.begin(body);}
    @WrapOperation(method="apply",at=@At(value="INVOKE",target="Ldev/ryanhcode/sable/companion/math/Pose3dc;transformPosition(Lorg/joml/Vector3d;)Lorg/joml/Vector3d;"),require=4,allow=4)
    private static Vector3d aste_aeroworks$pose(Pose3dc pose,Vector3d point,Operation<Vector3d> original){
        var world=original.call(pose,point);ServerWaves.step().transform(world.x,world.z);return world;
    }
    @ModifyArg(method="apply",at=@At(value="INVOKE",target="Lnet/minecraft/util/Mth;clamp(DDD)D"),index=0,require=1,allow=1)
    private static double aste_aeroworks$waveDepth(double depth,@Local(index=26) double x,@Local(index=28) double y,
            @Local(index=30) double z,@Local(index=19) double relativeSurface,@Local(index=11) double originY){
        // Native surfaceNear = top block Y+1; native relativeSurface includes its +0.5 cell-centre term.
        return ServerWaves.step().depth(depth,x,y,z,relativeSurface+originY-1.5);
    }
    @Inject(method="apply",at=@At("RETURN"),require=1)
    private static void aste_aeroworks$end(CallbackInfo ci){ServerWaves.end();}
}
