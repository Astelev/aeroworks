package net.aste.aeroworks;

import net.aste.aeroworks.failure.FailurePersistence;
import net.aste.aeroworks.failure.FailureState;
import net.minecraft.nbt.CompoundTag;

public final class FailurePersistenceTest {
    private static void check(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
    public static void main(String[] args) {
        var tag=new CompoundTag();var original=new FailureState();
        FailurePersistence.write(tag,original);
        check(!tag.contains(FailurePersistence.KEY),"Healthy blocks add no nested NBT allocation/data");
        original.tryFail(1,0);FailurePersistence.write(tag,original);
        var loaded=new FailureState();FailurePersistence.read(tag.copy(),loaded);
        check(loaded.failed(),"Failure survives save/load and copied assembly NBT");
        loaded.repair(300);loaded.elapse(40);FailurePersistence.write(tag,loaded);
        FailurePersistence.read(tag,original);
        check(!original.failed()&&original.graceTicks()==260,"Repair and remaining protection survive NBT roundtrip");
        original.elapse(300);FailurePersistence.write(tag,original);
        check(!tag.contains(FailurePersistence.KEY),"Expired repaired state is removed from NBT");
        loaded.restore(true,0);FailurePersistence.read(new CompoundTag(),loaded);
        check(!loaded.failed()&&loaded.graceTicks()==0,"Absent key clears old client state");
        var bad=new CompoundTag();var inner=new CompoundTag();inner.putBoolean("Failed",true);inner.putInt("RepairGrace",Integer.MAX_VALUE);bad.put(FailurePersistence.KEY,inner);
        FailurePersistence.read(bad,loaded);check(loaded.failed()&&loaded.graceTicks()<=72000,"Untrusted excessive protection is bounded");
        System.out.println("Failure persistence: clean healthy NBT, save/load, assembly copy, repair sync, protection expiry and corrupt counters passed");
    }
}
