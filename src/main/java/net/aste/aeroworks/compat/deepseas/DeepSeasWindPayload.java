package net.aste.aeroworks.compat.deepseas;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.wind.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-owned wind only; clients never submit weather or propulsion values. */
public record DeepSeasWindPayload(ResourceLocation dimension, double time, boolean enabled, boolean dynamic,
        long seed, double phase, double rate, WindParameters parameters, double reference,
        double fallbackX, double fallbackZ, Map<Long,WindWeatherGrid.Cell> weather, List<Body> bodies) implements CustomPacketPayload {
    public static final int MAX_BODIES=128;
    public static final Type<DeepSeasWindPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Aeroworks.MODID,"deep_seas_wind"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DeepSeasWindPayload> CODEC=StreamCodec.of(DeepSeasWindPayload::write,DeepSeasWindPayload::read);
    public record Body(UUID id,double x,double z) {
        public Body {if(id==null||!finite(x,z))throw new IllegalArgumentException("Invalid body wind");}
    }
    public DeepSeasWindPayload {
        if(dimension==null||parameters==null||!finite(time,phase,rate,reference,fallbackX,fallbackZ)||reference<=0||rate<0||bodies.size()>MAX_BODIES)
            throw new IllegalArgumentException("Invalid wind snapshot");
        if(weather.size()>WindWeatherGrid.MAX_CELLS)throw new IllegalArgumentException("Too many weather cells");
        for(var entry:weather.entrySet())if(entry.getKey()!=entry.getValue().key())throw new IllegalArgumentException("Weather key mismatch");
        weather=Map.copyOf(weather);bodies=List.copyOf(bodies);
    }
    public void ambient(double elapsed,double x,double y,double z,org.joml.Vector3d out) {
        out.zero();if(!enabled)return;
        if(!dynamic){out.set(fallbackX,0,fallbackZ);return;}
        var result=new ZonalWindModel.Result();
        double dt=Math.max(0,Math.min(2,elapsed));
        ZonalWindModel.sample(parameters,seed,phase+dt*rate,x,y,z,WindWeatherGrid.lookup(weather,x,z),time+dt,result);
        out.set(result.x,0,result.z);
    }
    static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
    @Override public Type<DeepSeasWindPayload> type(){return TYPE;}
    private static void write(RegistryFriendlyByteBuf b,DeepSeasWindPayload v){
        b.writeResourceLocation(v.dimension);b.writeDouble(v.time);b.writeBoolean(v.enabled);b.writeBoolean(v.dynamic);
        b.writeLong(v.seed);b.writeDouble(v.phase);b.writeDouble(v.rate);b.writeDouble(v.reference);
        var p=v.parameters;
        for(double n:new double[]{p.zoneSize(),p.surfaceHeight(),p.maximumHeight(),p.surfaceClear(),p.surfaceRain(),p.surfaceStorm(),p.aloftClear(),p.aloftRain(),p.aloftStorm(),p.fallbackPeriod(),p.weatherPeriodMultiplier(),p.transitionSeconds(),p.cloudDeviationDegrees()})b.writeDouble(n);
        b.writeDouble(v.fallbackX);b.writeDouble(v.fallbackZ);
        b.writeVarInt(v.weather.size());
        for(var cell:v.weather.values()) {
            b.writeInt(cell.x());b.writeInt(cell.z());var blend=cell.weather();
            writeWeather(b,blend.from());writeWeather(b,blend.to());b.writeDouble(blend.started());b.writeDouble(blend.duration());
        }
        b.writeVarInt(v.bodies.size());
        for(var body:v.bodies){b.writeUUID(body.id);b.writeDouble(body.x);b.writeDouble(body.z);}
    }
    private static void writeWeather(RegistryFriendlyByteBuf b,WindWeather w){
        b.writeLong(w.cloudType());b.writeDouble(w.rain());b.writeDouble(w.storm());b.writeDouble(w.cloudHeading());b.writeDouble(w.surfaceTurn());b.writeDouble(w.aloftTurn());
    }
    private static WindWeather readWeather(RegistryFriendlyByteBuf b){
        return new WindWeather(b.readLong(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());
    }
    private static DeepSeasWindPayload read(RegistryFriendlyByteBuf b){
        var dim=b.readResourceLocation();double time=b.readDouble();boolean enabled=b.readBoolean(),dynamic=b.readBoolean();
        long seed=b.readLong();double phase=b.readDouble(),rate=b.readDouble(),reference=b.readDouble();
        var p=new WindParameters(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());
        double x=b.readDouble(),z=b.readDouble();int cellCount=b.readVarInt();
        if(cellCount<0||cellCount>WindWeatherGrid.MAX_CELLS)throw new IllegalArgumentException("Too many weather cells");
        var cells=new HashMap<Long,WindWeatherGrid.Cell>();
        for(int i=0;i<cellCount;i++) {
            int cx=b.readInt(),cz=b.readInt();var from=readWeather(b);var to=readWeather(b);
            var cell=new WindWeatherGrid.Cell(cx,cz,new WindWeatherBlend(from,to,b.readDouble(),b.readDouble()));
            if(cells.put(cell.key(),cell)!=null)throw new IllegalArgumentException("Duplicate weather cell");
        }
        int count=b.readVarInt();
        if(count<0||count>MAX_BODIES)throw new IllegalArgumentException("Too many body winds");
        var bodies=new ArrayList<Body>(count);for(int i=0;i<count;i++)bodies.add(new Body(b.readUUID(),b.readDouble(),b.readDouble()));
        return new DeepSeasWindPayload(dim,time,enabled,dynamic,seed,phase,rate,p,reference,x,z,cells,bodies);
    }
}
