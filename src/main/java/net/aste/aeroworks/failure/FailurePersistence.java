package net.aste.aeroworks.failure;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class FailurePersistence {
    public static final String KEY="AeroworksFailure";
    private FailurePersistence() {}
    public static void write(CompoundTag tag,FailureState state) {
        // Most kinetic blocks are healthy: do not create a nested tag for every ordinary machine.
        if(!state.failed() && state.graceTicks()==0) { tag.remove(KEY);return; }
        var data=new CompoundTag();
        data.putBoolean("Failed",state.failed());data.putInt("RepairGrace",state.graceTicks());
        tag.put(KEY,data);
    }
    public static void read(CompoundTag tag,FailureState state) {
        if(!tag.contains(KEY,Tag.TAG_COMPOUND)) { state.restore(false,0);return; }
        var data=tag.getCompound(KEY);
        state.restore(data.getBoolean("Failed"),data.getInt("RepairGrace"));
    }
}
