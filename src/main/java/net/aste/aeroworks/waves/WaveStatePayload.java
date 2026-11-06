package net.aste.aeroworks.waves;

import net.aste.aeroworks.Aeroworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WaveStatePayload(ResourceLocation dimension,WaveParameters parameters) implements CustomPacketPayload {
    public static final Type<WaveStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID,"wave_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WaveStatePayload> CODEC=StreamCodec.of(WaveStatePayload::write,WaveStatePayload::read);
    public WaveStatePayload {if(dimension==null||parameters==null)throw new IllegalArgumentException("Missing wave state");}
    @Override public Type<WaveStatePayload> type(){return TYPE;}
    private static void write(RegistryFriendlyByteBuf b,WaveStatePayload v){
        b.writeResourceLocation(v.dimension);var p=v.parameters;
        b.writeBoolean(p.enabled());b.writeBoolean(p.buoyancy());
        b.writeDouble(p.amplitude());b.writeDouble(p.wavelength());b.writeDouble(p.speed());b.writeDouble(p.direction());b.writeDouble(p.shoreFade());
    }
    private static WaveStatePayload read(RegistryFriendlyByteBuf b){
        var dim=b.readResourceLocation();
        return new WaveStatePayload(dim,new WaveParameters(b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble()));
    }
}
