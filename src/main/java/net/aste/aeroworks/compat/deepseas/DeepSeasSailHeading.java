package net.aste.aeroworks.compat.deepseas;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;
import java.util.*;

/** Main-thread metadata, once per connected ship per server tick. Optional API stays in Native. */
public final class DeepSeasSailHeading {
    public record Heading(double x,double z){
        public Vector3d vector(){return new Vector3d(x,0,z);}
    }
    private static final Heading NONE=new Heading(0,0);
    private static final class World {long tick=Long.MIN_VALUE;final Map<UUID,Heading> headings=new HashMap<>();}
    private static final Map<ServerLevel,World> WORLDS=new IdentityHashMap<>();
    private DeepSeasSailHeading(){}
    public static Heading forBody(ServerLevel level,ServerSubLevel body){
        long tick=level.getServer().getTickCount();
        var root=Native.root(level,body,tick);
        return root instanceof ServerSubLevel server?forRoot(server,tick):NONE;
    }
    public static Heading forRoot(ServerSubLevel root,long tick){
        var world=WORLDS.computeIfAbsent(root.getLevel(),v->new World());
        if(world.tick!=tick){world.headings.clear();world.tick=tick;}
        return world.headings.computeIfAbsent(root.getUniqueId(),id->Native.average(root,tick));
    }
    public static void clear(ServerLevel level){WORLDS.remove(level);}
    public static void clear(){WORLDS.clear();}
    private static final class Native {
        static SubLevel root(ServerLevel level,ServerSubLevel body,long tick){
            var container=dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if(container==null)return null;
            return com.maxenonyme.highseas.sail.BoatClassifier.rootOf(level,container.getAllSubLevels(),body,tick);
        }
        static Heading average(ServerSubLevel root,long tick){
            Collection<SubLevel> members;
            try{members=dev.ryanhcode.sable.api.SubLevelHelper.getConnectedChain(root);}
            catch(RuntimeException e){members=List.of(root);}
            var sum=new Vector3d();var normal=new Vector3d();
            for(var member:members){
                if(member.isRemoved())continue;
                var rotation=member.logicalPose().orientation();
                for(var sail:com.maxenonyme.highseas.sail.SailWindRegistry.getSails(member,tick)){
                    var n=sail.localNormal();normal.set(n.x,n.y,n.z);rotation.transform(normal);
                    SailHeadingMath.add(sum,normal.x,normal.y,normal.z,sail.area(),sail.supportSign());
                }
            }
            return SailHeadingMath.normalize(sum)?new Heading(sum.x,sum.z):NONE;
        }
    }
}
