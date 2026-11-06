package net.aste.aeroworks;

import java.util.*;
import java.nio.file.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.aste.aeroworks.waves.*;
import net.aste.aeroworks.wind.WindParameters;
import org.joml.Vector3d;
import dev.ryanhcode.sable.companion.math.Pose3d;

public final class WaveSpectrumTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    private static void near(double a,double b,double e,String why){check(Math.abs(a-b)<=e,why+": "+a+" != "+b);}
    private static WaveWindGrid wind(float from,float to,double start,int cx,int cz){
        var cells=new HashMap<Long,WaveWindGrid.Cell>();
        for(int z=cz-5;z<=cz+5;z++)for(int x=cx-5;x<=cx+5;x++){
            var cell=new WaveWindGrid.Cell(x,z,from,to,start);cells.put(cell.key(),cell);
        }
        return new WaveWindGrid(0x12345678,cells);
    }
    private static WaveParameters profile(WaveDetail d){return new WaveParameters(true,true,.5,32,4,35,16,d);}
    public static void main(String[] args)throws Exception{
        var d=WaveDetail.DEFAULT;var p=profile(d);var grid=wind(1,1,0,0,0);
        var baseline=new WaveDetail(true,16,0,48,24,.65,0,.25,1.7);
        double x=7.17,z=-4.1,t=11;
        double calm=WaveShape.height(profile(baseline),wind(.16f,.16f,0,0,0),x,z,t,32);
        var moderate=wind(.64f,.64f,0,0,0);
        double strong=WaveShape.height(profile(baseline),moderate,x,z,t,32);
        near(strong,calm*2,1e-9,"Wave energy follows actual/storm wind ratio");
        double shallow=WaveShape.height(profile(baseline),moderate,x,z,t,4);
        near(shallow,strong*WaveMath.smooth(.25),1e-9,"Depth attenuates main swell");
        near(WaveShape.height(p,wind(0,0,0,0,0),x,z,t,32),0,0,"No local wind energy");
        near(WaveWindStates.stormSpeed(WindParameters.DEFAULT,64),6,0,"Sea uses configured surface storm wind");
        near(WaveWindStates.stormSpeed(WindParameters.DEFAULT,250),18,0,"High lakes use configured storm wind at their height");
        var noChop=new WaveDetail(true,16,.2,48,24,.65,0,.25,1.7);
        for(float ratio:new float[]{.1f,.64f,.65f}){
            var w=wind(ratio,ratio,0,0,0);
            near(WaveShape.height(p,w,x,z,t,32),WaveShape.height(profile(noChop),w,x,z,t,32),1e-9,"No chop below configured storm fraction");
        }
        check(Math.abs(WaveShape.height(p,grid,x,z,t,32)-WaveShape.height(profile(noChop),grid,x,z,t,32))>1e-5,"Strong wind adds separate short waves");
        var crossing=wind(.2f,1,10,0,0);
        near(crossing.ratio(x,z,10),.2f,1e-8,"Wind transition starts continuously");
        near(crossing.ratio(x,z,12),1,1e-8,"Wind transition reaches target");
        double old=crossing.ratio(x,z,11);var retarget=wind((float)old,.3f,11,0,0);
        near(retarget.ratio(x,z,11),old,1e-7,"Retargeting wind has no jump");
        for(int i=0;i<5000;i++){
            double xx=(i*17.37)%400-200,zz=(i*-7.11)%200,tt=i*.37;
            double n=WaveShape.noise(xx/48,zz/48,tt/24,123);
            check(n>=-1&&n<=1,"Bounded continuous noise");
            double a=WaveShape.height(p,grid,xx,zz,tt,16);
            check(Math.abs(a)<=p.maximumDisplacement()+1e-9,"Conservative force culling bound includes noise and chop");
            near(a,WaveShape.height(p,grid,xx,zz,tt,16),0,"Noise is deterministic");
            near(a,WaveShape.height(p,grid,xx,zz,tt+1e-5,16),2e-4,"Continuous temporal noise and sine phase");
        }
        float[] h=new float[65*65],depths=new float[h.length];Arrays.fill(h,63);Arrays.fill(depths,12);
        var patch=new WaveShorePatch(-128,-128,65,h,depths,16);
        var pose=new Pose3d();pose.position().set(3,0,5);
        var air=new WaveBuoyancyStep();air.begin(p,patch,t,1,grid);
        for(var v:new Vector3d[]{new Vector3d(),new Vector3d(1,0,0),new Vector3d(0,1,0),new Vector3d(0,0,1)}){pose.transformPosition(v);air.transform(v.x,v.z);}
        near(air.depth(.5,4,64,2,63)-.5,WaveShape.height(p,grid,7,7,t,12)*patch.weight(7,7,63),1e-9,"Air cells use shared noisy depth/wind spectrum");
        var body=new BlockWaveStep();var block=new BlockWaveSamples(new double[]{4,64,2,1},false,1);
        body.calculate(block,patch,p,pose,new Vector3d(4,64,2),t,1,.05,grid);
        double dy=WaveShape.height(p,grid,7,7,t,12)*patch.weight(7,7,63);
        near(body.impulse.y,BlockWaveMath.delta(64,.5,64,dy,1)*.05,1e-9,"Solid blocks use the same spectrum as air cells");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        var packet=new WaveStatePayload(ResourceLocation.fromNamespaceAndPath("minecraft","overworld"),p,WaveNetwork.REVISION,"test",grid);
        try{WaveStatePayload.CODEC.encode(buf,packet);check(buf.readableBytes()<4096,"121 coarse sea cells stay within 4KiB/second per player");
            check(WaveStatePayload.CODEC.decode(buf).equals(packet)&&buf.readableBytes()==0,"Full detail/wind state roundtrip");
        }finally{buf.release();}
        double mean=0;
        for(int i=0;i<10000;i++)mean+=WaveShape.crest(Math.sin(i*WaveMath.TAU/10000),1);
        near(mean/10000,0,1e-9,"Sharp storm profile has no artificial mean water-level shift");
        near(WaveShape.crest(1,1),1,1e-12,"Storm crest remains amplitude-bounded");
        double eps=.01;
        double sharpCurvature=(1-WaveShape.crest(Math.sin(Math.PI/2+eps),1))/(eps*eps);
        check(sharpCurvature>.7,"Storm crest is sharper than a sine");
        near(WaveShape.crest(.4,0),.4,0,"Calm profile unchanged");
        // Track actual crest locations along the transverse direction, rather than testing amplitude noise.
        var warped=new WaveDetail(true,16,0,48,24,.65,0,.25,1.7,.22);
        var straight=new WaveDetail(true,16,0,48,24,.65,0,.25,1.7,0);
        var steady=wind(.5f,.5f,0,0,0);double angle=p.radians(),c=Math.cos(angle),sn=Math.sin(angle);
        double minCrest=Double.POSITIVE_INFINITY,maxCrest=Double.NEGATIVE_INFINITY;
        for(int cross=-96;cross<=96;cross+=12){
            double bestU=0,bestHeight=-100;
            for(double u=-2;u<=18;u+=.125){
                double xx=u*c-cross*sn,zz=u*sn+cross*c;
                double hh=WaveShape.height(profile(warped),steady,xx,zz,0,32);
                if(hh>bestHeight){bestHeight=hh;bestU=u;}
            }
            minCrest=Math.min(minCrest,bestU);maxCrest=Math.max(maxCrest,bestU);
            double xx=8*c-cross*sn,zz=8*sn+cross*c;
            near(WaveShape.height(profile(straight),steady,xx,zz,0,32),.5*Math.sqrt(.5),1e-6,"Zero distortion restores straight carrier crests");
        }
        check(maxCrest-minCrest>2,"Crests must bend visibly by several blocks without amplitude noise or short waves");
        // Spatial and temporal lattice boundaries must remain continuous even at high distortion.
        var extreme=new WaveDetail(true,16,.2,48,24,.65,.35,.25,1.7,.35);
        for(double boundary:new double[]{-96,-48,0,48,96}){
            double a=WaveShape.height(profile(extreme),grid,boundary-1e-5,7,24-1e-5,32);
            double b=WaveShape.height(profile(extreme),grid,boundary+1e-5,7,24+1e-5,32);
            near(a,b,2e-4,"No seams at spatial/time noise-cell boundaries");
        }
        // CPU reference fixtures consumed by real OpenGL tests, including large/negative coordinates.
        var rows=new ArrayList<String>();var random=new Random(431);
        for(int i=0;i<64;i++){
            double cx=i%2==0?0:-12000000.25,cz=i%3==0?19000000.75:0;
            double px=random.nextDouble()*64-32,pz=random.nextDouble()*64-32,time=10000+random.nextDouble()*100;
            float ratio=(float)(.2+random.nextDouble()*1.2);double depth=3+random.nextDouble()*25;
            var w=wind(ratio,ratio,0,(int)Math.floor(cx/64),(int)Math.floor(cz/64));
            double expected=WaveShape.height(p,w,cx+px,cz+pz,time,depth);
            rows.add(String.format(Locale.ROOT,"%.12f %.12f %.12f %.12f %.12f %.12f %.12f %.12f",cx,cz,px,pz,time,(double)ratio,depth,expected));
        }
        Files.createDirectories(Path.of("build/wave-test"));Files.write(Path.of("build/wave-test/spectrum.txt"),rows);
        System.out.println("Wave spectrum: relative storm threshold, depth, smooth retargeting, bounded noise, shared air/solid forces, packet and GPU fixtures passed");
    }
}
