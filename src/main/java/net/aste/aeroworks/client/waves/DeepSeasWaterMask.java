package net.aste.aeroworks.client.waves;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.aste.aeroworks.Aeroworks;
import net.neoforged.fml.ModList;

/** Keeps Deep Seas' original hull-depth mask on the final Sodium shader path. */
public final class DeepSeasWaterMask {
    private DeepSeasWaterMask() {}
    private static final class Live {
        private static final NativeMask MASK=new NativeMask(ModList.get().isLoaded("create_submarine"));
    }
    public static String compiledSource(String source) { return Live.MASK.patch(source); }
    public static void bind(int program,Object pass) { Live.MASK.bind(program,pass); }
    public static String status() { return Live.MASK.status(); }

    // Optional classes stay behind reflection, including when this mod is absent or on a server.
    public static final class NativeMask {
        private final boolean installed;
        private Method patch, bind, translucent;
        private Field pixelPerfect;
        private boolean restored, failed, announced;
        public NativeMask(boolean installed) { this.installed=installed; }

        public String patch(String source) {
            if(!installed || failed || !terrainFragment(source)) return source;
            try {
                if(patch==null) patch=Class.forName("com.maxenonyme.createsubmarine.submarine.compat.sodium.WaterOcclusionPatch")
                        .getMethod("apply",String.class,boolean.class);
                // Use the mod's own algorithm and uniform names, not a second mask implementation.
                String result=(String)patch.invoke(null,source,false);
                restored=result.contains("SableWaterOcclusionEnabled") && result.contains("SableCloseSampler")
                        && result.contains("SableFarSampler");
                if(restored && !announced) {
                    announced=true;
                    Aeroworks.LOGGER.info("AeroWorks waves: original Deep Seas hull-depth mask present in final terrain fragment shader");
                }
                return result;
            } catch(ReflectiveOperationException e) { fail(e); return source; }
        }

        public void bind(int program,Object pass) {
            if(!installed || failed || program<=0) return;
            try {
                if(bind==null) {
                    Class<?> bridge=Class.forName("com.maxenonyme.createsubmarine.submarine.compat.sodium.SodiumWaterOcclusionBridge");
                    bind=bridge.getMethod("applyToProgram",int.class,boolean.class);
                    pixelPerfect=bridge.getField("PIXEL_PERFECT_ACTIVE");
                }
                if(translucent==null || !translucent.getDeclaringClass().isInstance(pass))
                    translucent=pass.getClass().getMethod("isTranslucent");
                // The original bridge binds the original Sable near/far depth buffers and switches
                // Deep Seas out of its block-meshing fallback when those buffers are available.
                bind.invoke(null,program,(boolean)translucent.invoke(pass));
            } catch(ReflectiveOperationException e) { fail(e); }
        }

        public String status() {
            if(!installed) return "absent";
            if(failed) return "integration error (see log)";
            boolean active=false;
            try { if(pixelPerfect!=null) active=pixelPerfect.getBoolean(null); }
            catch(IllegalAccessException ignored) {}
            return "shader="+restored+", pixel mask="+active;
        }
        private void fail(Exception e) {
            if(!failed) Aeroworks.LOGGER.error("Cannot restore original Deep Seas water mask",e);
            failed=true;
        }
        private static boolean terrainFragment(String source) {
            return source.contains("u_BlockTex") && source.contains("v_Material")
                    && source.contains("fragColor") && !source.contains("gl_Position");
        }
    }
}
