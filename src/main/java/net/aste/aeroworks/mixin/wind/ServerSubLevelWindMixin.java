package net.aste.aeroworks.mixin.wind;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.aste.aeroworks.aerodynamics.AerodynamicStep;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.aste.aeroworks.Config;
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

/** Shared body-step wind plus passive aerodynamics; the original provider remains an explicit fallback. */
@Mixin(value = ServerSubLevel.class, remap = false)
public abstract class ServerSubLevelWindMixin {
    @Unique private final StepWind aste_aeroworks$wind = new StepWind();
    @Unique private final AerodynamicStep aste_aeroworks$aerodynamics = new AerodynamicStep();

    @Inject(method = "prePhysicsTick", at = @At("HEAD"))
    private void aste_aeroworks$beginWindStep(CallbackInfo ci) {
        aste_aeroworks$wind.begin();
        aste_aeroworks$aerodynamics.begin();
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
            WindFields.sampleForStep(new WindSample(level, position.x(), position.y(), position.z(),
                    level.getGameTime() / 20.0, subLevel.getRuntimeId()), aste_aeroworks$wind.sampleBuffer(), aste_aeroworks$wind.torqueBuffer());
            double coefficient=Config.SAIL_PRESSURE_COEFFICIENT.get();
            aste_aeroworks$wind.prepare(linearVelocity, coefficient);
            // Once per body, in world coordinates, through Sable's existing impulse accumulator.
            if(coefficient>0 && aste_aeroworks$wind.torqueBuffer().lengthSquared()>0) {
                double pressure=DimensionPhysicsData.getAirPressure(level,position);
                if(Double.isFinite(pressure) && pressure>0) {
                    angularImpulse.fma(timeStep*coefficient*pressure,aste_aeroworks$wind.torqueBuffer());
                }
            }
        }
        if(aste_aeroworks$aerodynamics.enabled()) {
            aste_aeroworks$aerodynamics.add(provider,context,subLevel,localPose,aste_aeroworks$wind.aerodynamicTimeStep(timeStep),
                    aste_aeroworks$wind.velocity(linearVelocity),angularVelocity,group);
            return;
        }
        provider.sable$contributeLiftAndDrag(context, subLevel, localPose, aste_aeroworks$wind.aerodynamicTimeStep(timeStep),
                aste_aeroworks$wind.velocity(linearVelocity), angularVelocity,
                linearImpulse, angularImpulse, group);
    }
    @Redirect(method="prePhysicsTick",at=@At(value="INVOKE",target=
            "Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;applyLinearAndAngularImpulse(Lorg/joml/Vector3dc;Lorg/joml/Vector3dc;Z)V"),
            require=1,expect=1,allow=1)
    private void aste_aeroworks$finishAerodynamics(RigidBodyHandle handle,Vector3dc linear,Vector3dc angular,boolean wakeUp) {
        // Sable's two local accumulators are mutable Vector3d values, confirmed by the bytecode contract check.
        aste_aeroworks$aerodynamics.finish((ServerSubLevel)(Object)this,(Vector3d)linear,(Vector3d)angular);
        handle.applyLinearAndAngularImpulse(linear,angular,wakeUp);
    }

}
