package net.aste.aeroworks.aerodynamics;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.Config;
import net.neoforged.fml.ModList;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Per-body scratch: two ordinary/nested call sites share one passive impulse and one energy check. */
public final class AerodynamicStep {
    private final SurfaceAerodynamics.Result force=new SurfaceAerodynamics.Result();
    private final PassiveImpulse passivity=new PassiveImpulse();
    private final Vector3d impulse=new Vector3d(),torque=new Vector3d();
    private final Vector3d normal=new Vector3d(),point=new Vector3d(),worldPoint=new Vector3d();
    private final Vector3d lever=new Vector3d(),worldLever=new Vector3d(),velocity=new Vector3d(),temporary=new Vector3d();
    private final Vector3d airVelocity=new Vector3d(),spin=new Vector3d();
    private boolean enabled,prepared;
    private double liftTuning,normalTuning,baseTuning,liftCap,normalCap;
    public void begin() {
        enabled=Config.CONSERVATIVE_AERODYNAMICS.get();prepared=false;impulse.zero();torque.zero();
    }
    public boolean enabled() {return enabled;}
    public void add(BlockSubLevelLiftProvider provider,BlockSubLevelLiftProvider.LiftProviderContext context,
            ServerSubLevel body,Pose3d nested,double timeStep,Vector3dc relativeVelocity,Vector3dc angularVelocity,
            BlockSubLevelLiftProvider.LiftProviderGroup group) {
        if(!Double.isFinite(timeStep)||timeStep<=0)return;
        var pose=body.logicalPose();
        if(!prepared) {
            airVelocity.set(relativeVelocity);pose.transformNormalInverse(airVelocity);
            spin.set(angularVelocity);pose.transformNormalInverse(spin);
            boolean patch=ModList.get().isLoaded("ldfix");
            liftTuning=patch?LiftPatchTuning.lift():1;
            normalTuning=patch?LiftPatchTuning.normalDrag():1;
            baseTuning=patch?LiftPatchTuning.baseDrag():1;
            boolean caps=patch && Config.AERODYNAMIC_SOFTCAPS.get();
            liftCap=caps?LiftPatchTuning.liftCap():Double.POSITIVE_INFINITY;
            normalCap=caps?LiftPatchTuning.normalCap():Double.POSITIVE_INFINITY;
            prepared=true;
        }
        var pos=context.pos();var direction=context.dir();
        point.set(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        normal.set(direction.x,direction.y,direction.z);
        if(nested!=null) {nested.transformPosition(point);nested.transformNormal(normal);}
        // Velocity and torque use the same lever from the actual center of mass, not the plot origin.
        lever.set(point).sub(body.getMassTracker().getCenterOfMass());
        pose.transformNormal(lever,worldLever);
        angularVelocity.cross(worldLever,velocity).add(relativeVelocity);
        pose.transformNormalInverse(velocity);
        pose.transformPosition(point,worldPoint);
        double density=DimensionPhysicsData.getAirPressure(body.getLevel(),worldPoint);
        SurfaceAerodynamics.calculate(velocity,normal,density,provider.sable$getLiftScalar()*liftTuning,
                provider.sable$getParallelDragScalar()*normalTuning,provider.sable$getDirectionlessDragScalar()*baseTuning,
                liftCap,normalCap,force);
        force.lift.mul(timeStep);force.drag.mul(timeStep);
        temporary.set(force.lift).add(force.drag);
        impulse.add(temporary);
        lever.cross(temporary,temporary);torque.add(temporary);
        if(group!=null) {
            double lift=force.lift.length(),drag=force.drag.length();
            group.totalLift().add(force.lift);group.totalDrag().add(force.drag);
            group.liftCenter().fma(lift,point);group.dragCenter().fma(drag,point);
            group.totalLiftStrength+=lift;group.totalDragStrength+=drag;
        }
    }
    public void finish(ServerSubLevel body,Vector3d linearImpulse,Vector3d angularImpulse) {
        if(!enabled||!prepared)return;
        var mass=body.getMassTracker();
        passivity.correct(mass.getMass(),mass.getInertiaTensor(),mass.getInverseInertiaTensor(),airVelocity,spin,impulse,torque);
        linearImpulse.add(impulse);angularImpulse.add(torque);
    }
}
