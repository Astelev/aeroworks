package net.aste.aeroworks;

import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.compat.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class WaterSurfaceDispatchTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static class Provider implements BlockSubLevelLiftProvider {
        int calls;double dt;Vector3dc velocity,spin;
        @Override public Direction sable$getNormal(BlockState state){return Direction.NORTH;}
        @Override public void sable$contributeLiftAndDrag(LiftProviderContext c,ServerSubLevel b,Pose3d p,
                double dt,Vector3dc v,Vector3dc w,Vector3d j,Vector3d torque,LiftProviderGroup group){
            calls++;this.dt=dt;velocity=v;spin=w;
        }
    }
    private static final class Rudder extends Provider implements NativeWaterSurface {
        boolean wet,used;
        @Override public void sable$contributeLiftAndDrag(LiftProviderContext c,ServerSubLevel b,Pose3d p,
                double dt,Vector3dc v,Vector3dc w,Vector3d j,Vector3d torque,LiftProviderGroup group){
            super.sable$contributeLiftAndDrag(c,b,p,dt,v,w,j,torque,group);
            used=wet;if(wet){j.add(1,0,0);torque.add(0,2,0);}
        }
        @Override public boolean aeroworks$usedWater(){return used;}
    }
    public static void main(String[] args){
        var rudder=new Rudder();var velocity=new Vector3d(12,0,3);var spin=new Vector3d(0,.2,0);
        var j=new Vector3d();var torque=new Vector3d();rudder.wet=true;
        check(WaterSurfaceDispatch.contribute(rudder,null,null,null,.05,velocity,spin,j,torque,null,true),"Submerged rudder is handled natively, without air fallback");
        check(rudder.calls==1&&rudder.dt==.05&&rudder.velocity==velocity&&rudder.spin==spin,"Original body velocity, angular velocity and timestep reach water handler unchanged");
        check(j.x==1&&torque.y==2,"Native linear impulse and steering torque retained");
        rudder.wet=false;j.zero();torque.zero();
        check(!WaterSurfaceDispatch.contribute(rudder,null,null,null,.05,velocity,spin,j,torque,null,true),"Dry rudder returns to the existing conservative air model");
        check(!rudder.used&&j.lengthSquared()==0&&torque.lengthSquared()==0,"Wet flag cannot leak into the next dry call");
        check(WaterSurfaceDispatch.contribute(rudder,null,null,null,.05,velocity,spin,j,torque,null,false),"Native-only mode handles dry return once");
        check(rudder.calls==3,"No duplicated native calls");
        var ordinary=new Provider();
        check(!WaterSurfaceDispatch.contribute(ordinary,null,null,null,.05,velocity,spin,j,torque,null,true)&&ordinary.calls==0,"Ordinary sails keep their existing air path");
        System.out.println("Water rudder routing: raw motion/timestep, native force/torque, dry-air fallback, flag reset and no duplicate or ordinary-sail calls passed");
    }
}
