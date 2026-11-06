package net.aste.aeroworks.waves;

import net.aste.aeroworks.Aeroworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WaveStatePayload(ResourceLocation dimension,WaveParameters parameters,String revision,String diagnostics) implements CustomPacketPayload {
    public static final Type<WaveStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID,"wave_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WaveStatePayload> CODEC=StreamCodec.of(WaveStatePayload::write,WaveStatePayload::read);
    public WaveStatePayload(ResourceLocation dimension,WaveParameters parameters){this(dimension,parameters,WaveNetwork.REVISION,"awaiting native buoyancy");}
    public WaveStatePayload {if(dimension==null||parameters==null||revision==null||revision.length()>64||diagnostics==null||diagnostics.length()>192)throw new IllegalArgumentException("Invalid wave state");}
    @Override public Type<WaveStatePayload> type(){return TYPE;}
    private static void write(RegistryFriendlyByteBuf b,WaveStatePayload v){
        b.writeResourceLocation(v.dimension);var p=v.parameters;
        b.writeBoolean(p.enabled());b.writeBoolean(p.buoyancy());
        b.writeDouble(p.amplitude());b.writeDouble(p.wavelength());b.writeDouble(p.speed());b.writeDouble(p.direction());b.writeDouble(p.shoreFade());
        b.writeUtf(v.revision,64);b.writeUtf(v.diagnostics,192);
    }
    private static WaveStatePayload read(RegistryFriendlyByteBuf b){
        var dim=b.readResourceLocation();
        var p=new WaveParameters(b.readBoolean(),b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());
        return new WaveStatePayload(dim,p,b.readUtf(64),b.readUtf(192));
    }
}
