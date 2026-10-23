package net.aste.aeroworks.mixin.wind;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.wind.WindFields;
import net.aste.aeroworks.wind.StepWind;
import net.aste.aeroworks.wind.WindSample;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adjust the two callers, never overwrite Sable's or Lift Patch's aerodynamic formulas. */
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelWindMixin {
    @Unique private final StepWind aste_aeroworks$wind = new StepWind();

    @Inject(method = "prePhysicsTick", at = @At("HEAD"))
    private void aste_aeroworks$beginWindStep(CallbackInfo ci) {
        aste_aeroworks$wind.begin();
    }

    @Redirect(method = "prePhysicsTick", at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;sable$contributeLiftAndDrag(Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderContext;Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3d;DLorg/joml/Vector3dc;Lorg/joml/Vector3dc;Lorg/joml/Vector3d;Lorg/joml/Vector3d;Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider$LiftProviderGroup;)V"),
            require = 2, expect = 2, allow = 2)
    private void aste_aeroworks$relativeAirVelocity(BlockSubLevelLiftProvider provider,
            BlockSubLevelLiftProvider.LiftProviderContext context, ServerSubLevel subLevel,
            Pose3d localPose, double timeStep, Vector3dc linearVelocity, Vector3dc angularVelocity,
            Vector3d linearImpulse, Vector3d angularImpulse,
            BlockSubLevelLiftProvider.LiftProviderGroup group) {
        if (!aste_aeroworks$wind.ready()) {
            // One field query per body/substep, shared by ordinary and nested surfaces.
            var position = subLevel.logicalPose().position();
            var level = subLevel.getLevel();
            WindFields.sample(new WindSample(level, position.x(), position.y(), position.z(),
                    level.getGameTime() / 20.0), aste_aeroworks$wind.sampleBuffer());
            aste_aeroworks$wind.prepare(linearVelocity);
        }
        provider.sable$contributeLiftAndDrag(context, subLevel, localPose, timeStep,
                aste_aeroworks$wind.velocity(linearVelocity), angularVelocity,
                linearImpulse, angularImpulse, group);
    }
}
