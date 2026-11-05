package net.aste.aeroworks.compat.deepseas;

import java.util.*;
import java.util.function.BiConsumer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.*;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.Config;
import net.aste.aeroworks.wind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.joml.Vector3d;

/** No High Seas or client classes here: an optional, server-authoritative adapter. */
@EventBusSubscriber(modid=Aeroworks.MODID)
public final class DeepSeasWind {
    private static final Map<ServerLevel,Cache> CACHE=new IdentityHashMap<>();
    private static BiConsumer<DeepSeasWindPayload,net.neoforged.neoforge.network.handling.IPayloadContext> receiver=(v,c)->{};
    private static Level clientLevel;
    private static DeepSeasWindPayload client;
    private static Map<UUID,DeepSeasWindPayload.Body> clientBodies=Map.of();
    private static long received;
    private static final class Cache {long tick=Long.MIN_VALUE;final Map<UUID,Vec3> bodies=new HashMap<>();}
    private DeepSeasWind(){}
    public static void register(RegisterPayloadHandlersEvent event){
        event.registrar("1").playToClient(DeepSeasWindPayload.TYPE,DeepSeasWindPayload.CODEC,(v,c)->receiver.accept(v,c));
    }
    public static void installClient(BiConsumer<DeepSeasWindPayload,net.neoforged.neoforge.network.handling.IPayloadContext> handler){receiver=handler;}
    public static void receive(Level level,DeepSeasWindPayload payload){
        if(level==null||!level.dimension().location().equals(payload.dimension()))return;
        clientLevel=level;client=payload;received=level.getGameTime();
        var map=new HashMap<UUID,DeepSeasWindPayload.Body>();for(var body:payload.bodies())map.put(body.id(),body);clientBodies=Map.copyOf(map);
    }
    public static Vec3 at(Level level,double x,double y,double z){
        if(level==null||!DeepSeasWindPayload.finite(x,y,z))return Vec3.ZERO;
        var body=containing(level,x,y,z);
        if(level instanceof ServerLevel server){
            var wind=body instanceof ServerSubLevel serverBody?bodyWind(server,serverBody):ambient(server,x,y,z);
            return nativeUnits(wind,Config.DEEP_SEAS_WIND_REFERENCE.get());
        }
        var packet=client;
        if(level!=clientLevel||packet==null||level.getGameTime()<received||level.getGameTime()-received>40)return Vec3.ZERO;
        var state=body==null?null:clientBodies.get(body.getUniqueId());
        if(state!=null)return nativeUnits(new Vec3(state.x(),0,state.z()),packet.reference());
        var out=new Vector3d();packet.ambient((level.getGameTime()-received)/20.0,x,y,z,out);
        return nativeUnits(new Vec3(out.x,0,out.z),packet.reference());
    }
    /** The physics hook already knows the owning body; no per-panel spatial search or weather work. */
    public static Vec3 forBody(ServerLevel level,ServerSubLevel body){
        return nativeUnits(bodyWind(level,body),Config.DEEP_SEAS_WIND_REFERENCE.get());
    }
    public static Vec3 nativeUnits(Vec3 metresPerSecond,double reference){
        if(!Double.isFinite(reference)||reference<=0||!DeepSeasWindPayload.finite(metresPerSecond.x,metresPerSecond.z))return Vec3.ZERO;
        return new Vec3(metresPerSecond.x/reference,0,metresPerSecond.z/reference);
    }
    private static Vec3 bodyWind(ServerLevel level,ServerSubLevel body){
        if(body.isRemoved())return Vec3.ZERO;
        var cache=CACHE.computeIfAbsent(level,k->new Cache());long tick=level.getGameTime();
        if(cache.tick!=tick){cache.bodies.clear();cache.tick=tick;}
        return cache.bodies.computeIfAbsent(body.getUniqueId(),id->{
            var p=body.logicalPose().position();var v=new Vector3d();
            WindFields.sample(new WindSample(level,p.x(),p.y(),p.z(),tick/20.0,body.getRuntimeId()),v);
            return new Vec3(v.x,0,v.z);
        });
    }
    private static Vec3 ambient(ServerLevel level,double x,double y,double z){
        var v=new Vector3d();WindFields.sample(new WindSample(level,x,y,z,level.getGameTime()/20.0),v);return new Vec3(v.x,0,v.z);
    }
    private static SubLevel containing(Level level,double x,double y,double z){
        var container=SubLevelContainer.getContainer(level);if(container==null)return null;
        SubLevel nearest=null;double best=Double.POSITIVE_INFINITY;
        for(var body:container.queryIntersecting(new BoundingBox3d(x-.01,y-.01,z-.01,x+.01,y+.01,z+.01))){
            if(body.isRemoved())continue;var p=body.logicalPose().position();double d=(p.x()-x)*(p.x()-x)+(p.y()-y)*(p.y()-y)+(p.z()-z)*(p.z()-z);
            if(d<best){best=d;nearest=body;}
        }
        return nearest;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||!ModList.get().isLoaded("create_high_seas")||level.getGameTime()%5!=0||level.players().isEmpty())return;
        var settings=WindSettings.current();var clock=WindWorlds.clock(level);var container=SubLevelContainer.getContainer(level);
        for(var player:level.players()){
            var pos=dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level,player.position());
            var nearby=new ArrayList<ServerSubLevel>();
            double range=Math.min(2048,level.getServer().getPlayerList().getViewDistance()*16+128);
            if(container!=null)for(var body:container.getAllSubLevels()){
                if(body.isRemoved()||(body.getPlot().getLiftProviders().isEmpty()&&body.getPlot().getContraptions().isEmpty()))continue;
                var p=body.logicalPose().position();if((p.x()-pos.x)*(p.x()-pos.x)+(p.z()-pos.z)*(p.z()-pos.z)<=range*range)nearby.add(body);
            }
            nearby.sort(Comparator.comparingDouble(b->{var p=b.logicalPose().position();return (p.x()-pos.x)*(p.x()-pos.x)+(p.z()-pos.z)*(p.z()-pos.z);}));
            var bodies=new ArrayList<DeepSeasWindPayload.Body>();
            for(int i=0;i<Math.min(nearby.size(),DeepSeasWindPayload.MAX_BODIES);i++){
                var body=nearby.get(i);var v=bodyWind(level,body);bodies.add(new DeepSeasWindPayload.Body(body.getUniqueId(),v.x,v.z));
            }
            var fallback=ambient(level,pos.x,pos.y,pos.z);
            PacketDistributor.sendToPlayer(player,new DeepSeasWindPayload(level.dimension().location(),level.getGameTime()/20.0,
                settings.enabled(),settings.dynamic()&&WindFields.usesDefault(),clock.seed(),clock.phase(),clock.rate(),settings.field(),
                Config.DEEP_SEAS_WIND_REFERENCE.get(),fallback.x,fallback.z,bodies));
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){
        if(e.getLevel() instanceof ServerLevel server)CACHE.remove(server);
        if(e.getLevel()==clientLevel){clientLevel=null;client=null;clientBodies=Map.of();}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){CACHE.clear();}
}
