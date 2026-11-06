package net.aste.aeroworks.client.waves;

import com.mojang.blaze3d.platform.GlStateManager;
import java.util.ArrayDeque;
import net.aste.aeroworks.waves.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL33C;

/** Unit 6 is separate from shore data (7) and Deep Seas' native hull textures (8/9). */
final class WaveDetailRenderer {
    private static final int UNIT=6,SIZE=WaveWindGrid.CLIENT_SIZE;
    private record Binding(int texture,int sampler){}
    private static final ArrayDeque<Binding> bindings=new ArrayDeque<>();
    private static int texture,originX,originZ;
    private static double epoch;
    private static WaveWindGrid uploaded;
    private WaveDetailRenderer(){}
    static void enter(){bindings.push(new Binding(-1,-1));}
    static void bind(int program,ClientLevel level,Vec3 camera,WaveParameters p,double seconds){
        var d=p.detail();GL33C.glUniform1f(loc(program,"aste_WaveDetailed"),d.enabled()?1:0);
        if(!d.enabled())return;
        GL33C.glUniform1f(loc(program,"aste_WaveWarp"),(float)(p.wavelength()*d.crestDistortion()));
        var grid=WaveClientState.wind(level);
        int active=GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL33C.GL_TEXTURE0+UNIT);
        bindings.pop();bindings.push(new Binding(GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D),GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING)));
        if(texture==0)texture=GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);GL33C.glBindSampler(UNIT,0);
        if(grid!=uploaded){
            epoch=seconds;
            int minX=(int)Math.floor(camera.x/64)-5,minZ=(int)Math.floor(camera.z/64)-5;
            if(!grid.cells().isEmpty()){
                minX=grid.cells().values().stream().mapToInt(WaveWindGrid.Cell::x).min().orElse(minX);
                minZ=grid.cells().values().stream().mapToInt(WaveWindGrid.Cell::z).min().orElse(minZ);
            }
            originX=minX*64;originZ=minZ*64;float[] data=new float[SIZE*SIZE*4];
            for(var c:grid.cells().values()){
                int x=c.x()-minX,z=c.z()-minZ;if(x<0||z<0||x>=SIZE||z>=SIZE)continue;
                int i=(z*SIZE+x)*4;data[i]=c.from();data[i+1]=c.to();data[i+2]=(float)(c.started()-epoch);data[i+3]=1;
            }
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D,0,GL33C.GL_RGBA32F,SIZE,SIZE,0,GL33C.GL_RGBA,GL33C.GL_FLOAT,data);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_MIN_FILTER,GL33C.GL_NEAREST);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_MAG_FILTER,GL33C.GL_NEAREST);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_WRAP_S,GL33C.GL_CLAMP_TO_EDGE);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_WRAP_T,GL33C.GL_CLAMP_TO_EDGE);
            uploaded=grid;
        }
        GlStateManager._activeTexture(active);
        GL33C.glUniform1i(loc(program,"aste_WaveWind"),UNIT);
        GL33C.glUniform3f(loc(program,"aste_WaveWindGrid"),(float)(camera.x-originX),(float)(camera.z-originZ),(float)(seconds-epoch));
        GL33C.glUniform4f(loc(program,"aste_WaveDetail"),(float)d.fullDepth(),(float)d.noiseStrength(),(float)d.chopThreshold(),(float)d.chopFraction());
        band(program,"aste_WaveChopA",camera,seconds,p.wavelength()*d.chopLength(),p.speed()*d.chopSpeed(),p.radians()+.7);
        band(program,"aste_WaveChopB",camera,seconds,p.wavelength()*d.chopLength()/1.37,p.speed()*d.chopSpeed()*.83,p.radians()-1.1);
        noise(program,"A",camera,seconds,d.noiseScale(),d.noisePeriod());
        noise(program,"B",camera,seconds,d.noiseScale()*.35,d.noisePeriod()*.23);
        GL33C.glUniform1ui(loc(program,"aste_WaveSeed"),grid.seed());
    }
    private static void band(int program,String name,Vec3 camera,double time,double length,double speed,double angle){
        GL33C.glUniform4f(loc(program,name),(float)(WaveMath.TAU*Math.cos(angle)/length),(float)(WaveMath.TAU*Math.sin(angle)/length),
                (float)WaveMath.phase(camera.x,camera.z,time,length,speed,angle),0);
    }
    private static void noise(int program,String band,Vec3 camera,double time,double scale,double period){
        double x=camera.x/scale,z=camera.z/scale,t=time/period;
        int ix=(int)Math.floor(x),iz=(int)Math.floor(z),it=(int)Math.floor(t);
        GL33C.glUniform4f(loc(program,"aste_WaveNoise"+band),(float)(x-ix),(float)(z-iz),(float)(t-it),(float)(1/scale));
        GL33C.glUniform3i(loc(program,"aste_WaveNoiseOrigin"+band),ix,iz,it);
    }
    static void end(){
        if(bindings.isEmpty())return;var old=bindings.pop();if(old.texture<0)return;
        int active=GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);GlStateManager._activeTexture(GL33C.GL_TEXTURE0+UNIT);
        GlStateManager._bindTexture(old.texture);GL33C.glBindSampler(UNIT,old.sampler);GlStateManager._activeTexture(active);
    }
    static void close(){if(texture!=0)GlStateManager._deleteTexture(texture);texture=0;uploaded=null;bindings.clear();}
    private static int loc(int program,String name){return GL33C.glGetUniformLocation(program,name);}
}
