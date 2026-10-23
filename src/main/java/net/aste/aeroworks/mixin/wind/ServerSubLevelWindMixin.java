package net.aste.aeroworks.mixin.wind;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.Config;
import net.aste.aeroworks.wind.WindFields;
import net.aste.aeroworks.wind.WindKinematics;
import net.aste.aeroworks.wind.WindSample;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Adjust the two callers, never overwrite Sable's or Lift Patch's aerodynamic formulas. */
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelWindMixin {
    @ModifyArgs(method = "prePhysicsTick", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;sable$contributeLiftAndDrag(Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderContext;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3d;DLorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;Lorg/joml/Vector3d;Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderGroup;)V"),
            require = 2, expect = 2, allow = 2)
    private void aste_aeroworks$relativeAirVelocity(Args args) {
        if (!Config.WIND_ENABLED.get()) return;
        BlockSubLevelLiftProvider.LiftProviderContext context = args.get(0);
        ServerSubLevel subLevel = args.get(1);
        Pose3d localPose = args.get(2);
        var pos = context.pos();
        var world = WindKinematics.worldPosition(new Vector3d(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5),
                localPose, subLevel.logicalPose(), new Vector3d());
        var level = subLevel.getLevel();
        var wind = WindFields.sample(new WindSample(level, world.x, world.y, world.z, level.getGameTime() / 20.0), new Vector3d());
        Vector3dc original = args.get(4);
        args.set(4, WindKinematics.relativeVelocity(original, wind));
    }
}
