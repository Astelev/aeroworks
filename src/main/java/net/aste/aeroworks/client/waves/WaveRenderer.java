package net.aste.aeroworks.client.waves;

import com.mojang.blaze3d.platform.GlStateManager;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Arrays;
import net.aste.aeroworks.Aeroworks;
import net.aste.aeroworks.waves.ShoreField;
import net.aste.aeroworks.waves.WaveMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.lwjgl.opengl.GL33C;

/** Native Sodium water vertices, with Deep Seas' original fragment clipping intact. */
@EventBusSubscriber(modid=Aeroworks.MODID, value=Dist.CLIENT)
public final class WaveRenderer {
    private static final int SIZE=129, STEP=4, UNIT=7, BUDGET=512;
    private static final ArrayDeque<Binding> BINDINGS=new ArrayDeque<>();
    private static final BlockPos.MutableBlockPos POS=new BlockPos.MutableBlockPos();
    private static ClientLevel level;
    private static float[] heights, pending;
    private static int cursor, buildX, buildZ, originX, originZ, texture;
    private static int nextX, nextZ;
    private static boolean ready, upload;
    private static float partial;
    private static long nextRefresh;
    private static Object iris;
    private static Method irisActive;
    private static boolean irisChecked, irisWarned;
    private record Binding(int texture, int sampler) {}
    private WaveRenderer() {}

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) { partial=(float)event.getPartialTick(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(level!=mc.level) {
            level=mc.level; heights=pending=null; ready=upload=false; nextRefresh=0;
        }
        if(level==null || mc.player==null || mc.isPaused() || !WaveConfig.ENABLED.get()
                || !ModList.get().isLoaded("sodium") || shaderPack()) return;
        if(heights==null) {
            if(level.getGameTime()<nextRefresh) return;
            buildX=Math.floorDiv(mc.player.getBlockX(),32)*32-(SIZE-1)*STEP/2;
            buildZ=Math.floorDiv(mc.player.getBlockZ(),32)*32-(SIZE-1)*STEP/2;
            heights=new float[SIZE*SIZE];
            Arrays.fill(heights,ShoreField.DRY); cursor=0;
        }
        for(int budget=0; budget<BUDGET && cursor<heights.length;budget++,cursor++) {
            int x=buildX+(cursor%SIZE)*STEP, z=buildZ+(cursor/SIZE)*STEP;
            float h=column(x,z);
            // Test the surrounding cell too: a single wet sample must not animate a thin stream.
            if(h!=ShoreField.DRY) for(int dx=-2;dx<=2;dx+=4) for(int dz=-2;dz<=2;dz+=4)
                if(column(x+dx,z+dz)!=h) h=ShoreField.DRY;
            heights[cursor]=h;
        }
        if(cursor==heights.length) {
            pending=ShoreField.build(heights,SIZE,STEP,WaveConfig.SHORE_FADE.get().floatValue());
            nextX=buildX; nextZ=buildZ; upload=true; heights=null;
            nextRefresh=level.getGameTime()+8;
        }
    }

    private static float column(int x,int z) {
        // Never request a chunk or wait for terrain generation.
        var chunk=level.getChunkSource().getChunk(x>>4,z>>4,ChunkStatus.FULL,false);
        if(chunk==null) return ShoreField.DRY;
        int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x&15,z&15);
        if(y<level.getMinBuildHeight()+2) return ShoreField.DRY;
        var state=chunk.getBlockState(POS.set(x,y,z));
        if(!state.isAir() && !state.getFluidState().isEmpty()) {
            var fluid=state.getFluidState();
            if(!fluid.is(FluidTags.WATER) || !fluid.isSource() || !state.getCollisionShape(level,POS).isEmpty())
                return ShoreField.DRY;
            for(int d=1;d<=2;d++) if(!chunk.getFluidState(POS.set(x,y-d,z)).is(FluidTags.WATER))
                return ShoreField.DRY;
            return y+8f/9f-.001f;
        }
        return ShoreField.DRY;
    }

    private static boolean shaderPack() {
        if(!irisChecked) {
            irisChecked=true;
            try {
                Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris=api.getMethod("getInstance").invoke(null);
                irisActive=api.getMethod("isShaderPackInUse");
            } catch(ReflectiveOperationException ignored) { irisActive=null; }
        }
        if(irisActive==null) return false;
        try {
            boolean active=(boolean)irisActive.invoke(iris);
            if(active && !irisWarned) {
                irisWarned=true;
                Aeroworks.LOGGER.info("AeroWorks visual waves disabled while an Iris shader pack is active");
            }
            return active;
        } catch(ReflectiveOperationException e) { return true; }
    }

    public static void begin() {
        int program=GL33C.glGetInteger(GL33C.GL_CURRENT_PROGRAM);
        int enabled=GL33C.glGetUniformLocation(program,"aste_WaveEnabled");
        BINDINGS.push(new Binding(-1,-1));
        if(enabled<0) return; // Alternate/Iris shader: leave it completely alone.
        GL33C.glUniform1f(enabled,0);
        var mc=Minecraft.getInstance();
        if(mc.level!=level || level==null || !WaveConfig.ENABLED.get() || shaderPack() || (!ready && !upload)) return;
        int active=GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL33C.GL_TEXTURE0+UNIT);
        int previous=GL33C.glGetInteger(GL33C.GL_TEXTURE_BINDING_2D);
        int sampler=GL33C.glGetInteger(GL33C.GL_SAMPLER_BINDING);
        BINDINGS.pop(); BINDINGS.push(new Binding(previous,sampler));
        if(texture==0) texture=GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        GL33C.glBindSampler(UNIT,0);
        if(upload) {
            GL33C.glTexImage2D(GL33C.GL_TEXTURE_2D,0,GL33C.GL_RG32F,SIZE,SIZE,0,GL33C.GL_RG,GL33C.GL_FLOAT,pending);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_MIN_FILTER,GL33C.GL_LINEAR);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_MAG_FILTER,GL33C.GL_LINEAR);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_WRAP_S,GL33C.GL_CLAMP_TO_EDGE);
            GL33C.glTexParameteri(GL33C.GL_TEXTURE_2D,GL33C.GL_TEXTURE_WRAP_T,GL33C.GL_CLAMP_TO_EDGE);
            originX=nextX; originZ=nextZ; upload=false; ready=true; pending=null;
        }
        GlStateManager._activeTexture(active);
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        double direction=Math.toRadians(WaveConfig.DIRECTION.get()), length=WaveConfig.WAVELENGTH.get();
        double speed=WaveConfig.SPEED.get(), seconds=(level.getGameTime()+partial)/20.0;
        GL33C.glUniform1i(loc(program,"aste_WaveShore"),UNIT);
        GL33C.glUniform3f(loc(program,"aste_WaveGrid"),(float)(camera.x-originX),(float)(camera.z-originZ),(float)camera.y);
        GL33C.glUniform4f(loc(program,"aste_WaveProfile"),(float)(WaveMath.TAU*Math.cos(direction)/length),
                (float)(WaveMath.TAU*Math.sin(direction)/length),
                (float)WaveMath.phase(camera.x,camera.z,seconds,length,speed,direction),WaveConfig.AMPLITUDE.get().floatValue());
        sprite(program,"aste_WaveStill","water_still");
        sprite(program,"aste_WaveFlow","water_flow");
        sprite(program,"aste_WaveOverlay","water_overlay");
        GL33C.glUniform1f(enabled,1);
    }

    public static void end() {
        if(BINDINGS.isEmpty()) return;
        var binding=BINDINGS.pop();
        if(binding.texture<0) return;
        int active=GL33C.glGetInteger(GL33C.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL33C.GL_TEXTURE0+UNIT);
        GlStateManager._bindTexture(binding.texture);
        GL33C.glBindSampler(UNIT,binding.sampler);
        GlStateManager._activeTexture(active);
    }
    public static void close() {
        if(texture!=0) GlStateManager._deleteTexture(texture);
        texture=0; ready=upload=false; pending=heights=null; BINDINGS.clear();
    }
    private static int loc(int program,String name) { return GL33C.glGetUniformLocation(program,name); }
    private static void sprite(int program,String uniform,String name) {
        var s=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(ResourceLocation.fromNamespaceAndPath("minecraft","block/"+name));
        GL33C.glUniform4f(loc(program,uniform),s.getU0(),s.getV0(),s.getU1(),s.getV1());
    }
}
