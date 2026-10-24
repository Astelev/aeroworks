package net.aste.aeroworks.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.aste.aeroworks.Aeroworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded server -> client presentation state, never used to apply physics. */
public record WindVisualPayload(ResourceLocation dimension, Shake shake, List<Flow> flows) implements CustomPacketPayload {
    public static final int MAX_FLOWS=8;
    public static final Type<WindVisualPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID,"wind_visuals"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WindVisualPayload> CODEC=StreamCodec.of(WindVisualPayload::write,WindVisualPayload::read);
    public record Shake(UUID body,long seed,double elapsed,double period,float strength) {
        public Shake {
            if(!Double.isFinite(elapsed)||elapsed<0||!Double.isFinite(period)||period<.08
                    ||!Float.isFinite(strength)||strength<0||strength>1) throw new IllegalArgumentException("Invalid shake");
        }
    }
    public record Flow(double x,double y,double z,float radius,float strength,long seed) {
        public Flow {
            if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(radius)||radius<=0||radius>256
                    ||!Float.isFinite(strength)||strength<0||strength>1) throw new IllegalArgumentException("Invalid flow marker");
        }
    }
    public WindVisualPayload {
        if(flows.size()>MAX_FLOWS) throw new IllegalArgumentException("Too many flow markers");
        flows=List.copyOf(flows);
    }
    @Override public Type<WindVisualPayload> type() { return TYPE; }
    private static void write(RegistryFriendlyByteBuf out,WindVisualPayload value) {
        out.writeResourceLocation(value.dimension());
        out.writeBoolean(value.shake()!=null);
        if(value.shake()!=null) {
            var s=value.shake();out.writeUUID(s.body());out.writeLong(s.seed());
            out.writeDouble(s.elapsed());out.writeDouble(s.period());out.writeFloat(s.strength());
        }
        out.writeVarInt(value.flows().size());
        for(var f:value.flows()) {
            out.writeDouble(f.x());out.writeDouble(f.y());out.writeDouble(f.z());
            out.writeFloat(f.radius());out.writeFloat(f.strength());out.writeLong(f.seed());
        }
    }
    private static WindVisualPayload read(RegistryFriendlyByteBuf in) {
        var dimension=in.readResourceLocation();
        var shake=in.readBoolean() ? new Shake(in.readUUID(),in.readLong(),in.readDouble(),in.readDouble(),in.readFloat()):null;
        int count=in.readVarInt();
        if(count<0||count>MAX_FLOWS) throw new IllegalArgumentException("Too many flow markers");
        var flows=new ArrayList<Flow>(count);
        for(int i=0;i<count;i++) flows.add(new Flow(in.readDouble(),in.readDouble(),in.readDouble(),in.readFloat(),in.readFloat(),in.readLong()));
        return new WindVisualPayload(dimension,shake,flows);
    }
}
