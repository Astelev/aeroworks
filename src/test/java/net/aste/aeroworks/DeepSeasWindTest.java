package net.aste.aeroworks;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.aste.aeroworks.compat.deepseas.*;
import net.aste.aeroworks.wind.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
public final class DeepSeasWindTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        var v=DeepSeasWind.nativeUnits(new Vec3(9,100,-18),9);
        check(v.equals(new Vec3(1,0,-2)),"Preserve heading/sign and convert magnitude once; ships use horizontal wind");
        check(DeepSeasWind.nativeUnits(Vec3.ZERO,9).equals(Vec3.ZERO),"Disabled wind stays calm");
        check(DeepSeasWind.nativeUnits(new Vec3(Double.NaN,0,1),9).equals(Vec3.ZERO),"Reject nonfinite providers");
        var dim=ResourceLocation.fromNamespaceAndPath("minecraft","overworld");
        var bodies=java.util.stream.IntStream.range(0,DeepSeasWindPayload.MAX_BODIES).mapToObj(i->new DeepSeasWindPayload.Body(new UUID(1,i),i,-i)).toList();
        var p=new DeepSeasWindPayload(dim,30,true,true,123,4,.03,WindParameters.DEFAULT,9,2,3,bodies);
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{DeepSeasWindPayload.CODEC.encode(b,p);check(b.readableBytes()<5000,"Bounded packet");check(p.equals(DeepSeasWindPayload.CODEC.decode(b)),"Roundtrip server settings, clock and body identities");check(b.readableBytes()==0,"Complete packet");}finally{b.release();}
        var result=new ZonalWindModel.Result();var out=new Vector3d();
        p.ambient(.25,123,250,-321,out);
        ZonalWindModel.sample(p.parameters(),p.seed(),p.phase()+.25*p.rate(),123,250,-321,WindWeatherBlend.CLEAR,p.time()+.25,result);
        check(Math.abs(out.x-result.x)<1e-10&&Math.abs(out.z-result.z)<1e-10,"Ambient client evaluates same spatial model/clock as server");
        new DeepSeasWindPayload(dim,0,false,true,0,0,0,WindParameters.DEFAULT,9,10,20,List.of()).ambient(0,0,0,0,out);
        check(out.lengthSquared()==0,"Server disabled wins over client config");
        new DeepSeasWindPayload(dim,0,true,false,0,0,0,WindParameters.DEFAULT,9,-5,7,List.of()).ambient(0,99,99,99,out);
        check(out.equals(new Vector3d(-5,0,7)),"Constant/custom provider fallback uses server values");
        boolean rejected=false;try{new DeepSeasWindPayload(dim,0,true,true,0,0,0,WindParameters.DEFAULT,0,0,0,List.of());}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Reject invalid unit reference");
        System.out.println("Deep Seas wind: units/heading/calm, bounded payload, server config and model parity passed");
    }
}
