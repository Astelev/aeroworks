package net.aste.aeroworks.waves;

import net.aste.aeroworks.Aeroworks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WaveStatePayload(ResourceLocation dimension,WaveParameters parameters,String revision,String diagnostics,WaveWindGrid wind) implements CustomPacketPayload {
    public static final Type<WaveStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID,"wave_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WaveStatePayload> CODEC=StreamCodec.of(WaveStatePayload::write,WaveStatePayload::read);
    public WaveStatePayload(ResourceLocation dimension,WaveParameters parameters,String revision,String diagnostics){this(dimension,parameters,revision,diagnostics,WaveWindGrid.EMPTY);}
    public WaveStatePayload(ResourceLocation dimension,WaveParameters parameters){this(dimension,parameters,WaveNetwork.REVISION,"awaiting native buoyancy");}
    public WaveStatePayload {if(wind==null||dimension==null||parameters==null||revision==null||revision.length()>64||diagnostics==null||diagnostics.length()>192)throw new IllegalArgumentException("Invalid wave state");}
    @Override public Type<WaveStatePayload> type(){return TYPE;}
    private static void write(RegistryFriendlyByteBuf b,WaveStatePayload v){
        b.writeResourceLocation(v.dimension);var p=v.parameters;
        b.writeBoolean(p.enabled());b.writeBoolean(p.buoyancy());
        b.writeDouble(p.amplitude());b.writeDouble(p.wavelength());b.writeDouble(p.speed());b.writeDouble(p.direction());b.writeDouble(p.shoreFade());
        var d=p.detail();b.writeBoolean(d.enabled());
        for(double n:new double[]{d.fullDepth(),d.noiseStrength(),d.noiseScale(),d.noisePeriod(),d.chopThreshold(),d.chopFraction(),d.chopLength(),d.chopSpeed()})b.writeDouble(n);
        b.writeInt(v.wind.seed());b.writeVarInt(v.wind.cells().size());
        for(var c:v.wind.cells().values()){b.writeInt(c.x());b.writeInt(c.z());b.writeFloat(c.from());b.writeFloat(c.to());b.writeDouble(c.started());}
        b.writeUtf(v.revision,64);b.writeUtf(v.diagnostics,192);
    }
    private static WaveStatePayload read(RegistryFriendlyByteBuf b){
        var dim=b.readResourceLocation();
        boolean enabled=b.readBoolean(),buoyancy=b.readBoolean();
        double amp=b.readDouble(),length=b.readDouble(),speed=b.readDouble(),direction=b.readDouble(),fade=b.readDouble();
        var d=new WaveDetail(b.readBoolean(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());
        var p=new WaveParameters(enabled,buoyancy,amp,length,speed,direction,fade,d);
        int seed=b.readInt(),count=b.readVarInt();
        if(count<0||count>WaveWindGrid.MAX_CELLS)throw new IllegalArgumentException("Too many sea wind cells");
        var cells=new java.util.HashMap<Long,WaveWindGrid.Cell>();
        for(int i=0;i<count;i++){
            var c=new WaveWindGrid.Cell(b.readInt(),b.readInt(),b.readFloat(),b.readFloat(),b.readDouble());
            if(cells.put(c.key(),c)!=null)throw new IllegalArgumentException("Duplicate sea wind cell");
        }
        return new WaveStatePayload(dim,p,b.readUtf(64),b.readUtf(192),new WaveWindGrid(seed,cells));
    }
}
