package net.aste.aeroworks.failure;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.aste.aeroworks.Aeroworks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Main-server-thread cache, shared by all failure checks in a level during one game tick. */
@EventBusSubscriber(modid=Aeroworks.MODID)
public final class FailureOccupancy {
    private record Snapshot(long tick,Set<UUID> bodies) {}
    private static final Map<ServerLevel,Snapshot> CACHE=new WeakHashMap<>();
    private FailureOccupancy() {}
    public static boolean hasPlayer(ServerLevel level,ServerSubLevel body) {
        if(body.isRemoved())return false;
        long now=level.getGameTime();
        var snapshot=CACHE.get(level);
        if(snapshot==null || snapshot.tick()!=now) {
            var occupied=new HashSet<UUID>();
            for(var player:level.players()) {
                if(player.isSpectator() || !player.isAlive())continue;
                var tracked=Sable.HELPER.getTrackingOrVehicleSubLevel(player);
                if(tracked==null && player.isSleeping()) {
                    var bed=player.getSleepingPos();
                    if(bed.isPresent())tracked=Sable.HELPER.getContaining(level,bed.get());
                }
                if(tracked!=null && !tracked.isRemoved())occupied.add(tracked.getUniqueId());
            }
            snapshot=new Snapshot(now,Set.copyOf(occupied));
            CACHE.put(level,snapshot);
        }
        return snapshot.bodies().contains(body.getUniqueId());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if(event.getLevel() instanceof ServerLevel level)CACHE.remove(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { CACHE.clear(); }
}
