package net.aste.aeroworks;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.aste.aeroworks.network.WindVisualPayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public final class WindVisualPayloadTest {
    private static final ResourceLocation DIM=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
    private static void check(boolean value,String why) { if(!value) throw new AssertionError(why); }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY); }
    public static void main(String[] args) {
        roundtrip(new WindVisualPayload(DIM,null,List.of()));
        var shake=new WindVisualPayload.Shake(new UUID(12,34),78,5.4,.16,.75f);
        var flows=java.util.stream.IntStream.range(0,8).mapToObj(i->new WindVisualPayload.Flow(i*128,350,i*-128,30,.8f,i)).toList();
        roundtrip(new WindVisualPayload(DIM,shake,flows));
        boolean rejected=false;
        var in=buffer();
        try {
            in.writeResourceLocation(DIM);in.writeBoolean(false);in.writeVarInt(Integer.MAX_VALUE);
            try { WindVisualPayload.CODEC.decode(in); } catch(IllegalArgumentException expected) { rejected=true; }
            check(rejected,"Reject excess marker count before allocating or reading records");
        } finally { in.release(); }
        rejected=false;
        try { new WindVisualPayload.Flow(Double.NaN,3,4,30,.8f,1); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"Reject invalid marker coordinates");
        rejected=false;
        try { new WindVisualPayload.Shake(new UUID(1,2),3,0,0,1); }
        catch(IllegalArgumentException expected) { rejected=true; }
        check(rejected,"Reject invalid shake period");
        System.out.println("Wind visuals: occupied/empty payload roundtrips, bounded size/count and finite-state validation passed");
    }
    private static void roundtrip(WindVisualPayload original) {
        var buffer=buffer();
        try {
            WindVisualPayload.CODEC.encode(buffer,original);
            check(buffer.readableBytes()<512,"Bounded snapshot packet size");
            var copy=WindVisualPayload.CODEC.decode(buffer);
            check(copy.equals(original),"Preserve body identity, timing, envelope and marker geometry");
            check(buffer.readableBytes()==0,"Consume complete packet");
        } finally { buffer.release(); }
    }
}
