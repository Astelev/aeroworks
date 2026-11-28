package net.aste.aeroworks.compat;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/** Preserve medium-specific providers before applying air-only wind, tuning or integration. */
public final class WaterSurfaceDispatch {
    private WaterSurfaceDispatch(){}
    public static boolean contribute(BlockSubLevelLiftProvider provider,
            BlockSubLevelLiftProvider.LiftProviderContext context,ServerSubLevel body,Pose3d nested,
            double timeStep,Vector3dc linearVelocity,Vector3dc angularVelocity,
            Vector3d linearImpulse,Vector3d angularImpulse,BlockSubLevelLiftProvider.LiftProviderGroup group,
            boolean conservativeAir){
        if(!(provider instanceof NativeWaterSurface water))return false;
        provider.sable$contributeLiftAndDrag(context,body,nested,timeStep,linearVelocity,angularVelocity,
                linearImpulse,angularImpulse,group);
        // A dry specialised provider returned without a force. Preserve the existing air fallback,
        // but never add air force on top of its water force or invoke the native provider twice.
        return water.aeroworks$usedWater()||!conservativeAir;
    }
}
