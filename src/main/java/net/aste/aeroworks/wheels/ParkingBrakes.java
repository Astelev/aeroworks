package net.aste.aeroworks.wheels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/** Scoped to Offroad's existing batch: contacts only, no world/block scans or retained body references. */
public final class ParkingBrakes {
    public interface Access {State aste_aeroworks$parkingBrake();}
    public static final class State {
        public final ParkingBrakeSolver.Contact contact=new ParkingBrakeSolver.Contact();
        public ServerSubLevel body;
        public boolean prepared;
    }
    private ParkingBrakes() {}
    public static void prepare(Collection<?> wheels) {
        var bodies=new IdentityHashMap<ServerSubLevel,ArrayList<ParkingBrakeSolver.Contact>>();
        for(Object wheel:wheels)if(wheel instanceof Access access) {
            var state=access.aste_aeroworks$parkingBrake();
            if(state.body==null||state.prepared)continue;
            state.prepared=true;
            bodies.computeIfAbsent(state.body,b->new ArrayList<>()).add(state.contact);
        }
        for(var entry:bodies.entrySet()) {
            var mass=entry.getKey().getMassTracker();
            if(!ParkingBrakeSolver.solve(mass.getMass(),mass.getInertiaTensor(),entry.getValue()))
                for(var c:entry.getValue())c.impulse=c.fallback;
        }
    }
}
