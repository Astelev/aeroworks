package net.aste.aeroworks;

import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class FlightBundleTargetsTest {
    private static ClassNode nested(JarFile jar, String module, String path) throws Exception {
        var entry = jar.stream().filter(e -> e.getName().contains(module) && e.getName().endsWith(".jar")).findFirst().orElseThrow();
        try (var zip = new ZipInputStream(jar.getInputStream(entry))) {
            for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                if (!e.getName().equals(path + ".class")) continue;
                var node = new ClassNode();
                new ClassReader(zip.readAllBytes()).accept(node, 0);
                return node;
            }
        }
        throw new AssertionError(path);
    }
    private static MethodNode method(ClassNode node, String name, String descriptor) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError(node.name + "." + name + descriptor));
    }
    public static void main(String[] args) throws Exception {
        try (var jar = new JarFile(args[0])) {
            var wheel = nested(jar, ".offroad.", "dev/ryanhcode/offroad/content/blocks/wheel_mount/WheelMountBlockEntity");
            var tick = method(wheel, "sable$physicsTick",
                    "(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V");
            int fma = 0;
            for (var i : tick.instructions)
                if (i instanceof MethodInsnNode call && call.owner.equals("org/joml/Vector3d") && call.name.equals("fma")
                        && call.desc.equals("(DLorg/joml/Vector3dc;)Lorg/joml/Vector3d;")) fma++;
            if (fma != 2) throw new AssertionError("Wheel impulse call sites changed: " + fma);
            for (String local : new String[]{"localVelocity", "normalD", "sideD", "brakeStrength", "springForce"})
                if (tick.localVariables == null || tick.localVariables.stream().noneMatch(v -> v.name.equals(local)))
                    throw new AssertionError("Wheel local capture changed: " + local);
            method(wheel, "applyAllBatchedForces", "(Lnet/minecraft/server/level/ServerLevel;D)V");
            method(wheel, "applyBatchedForces", "()V");
            for (String field : new String[]{"queuedWheelMounts", "queuedForcePos", "forceTotal", "touchingFriction"})
                if (wheel.fields.stream().noneMatch(f -> f.name.equals(field))) throw new AssertionError(field);
        }
        System.out.println("Offroad 1.3.3: longitudinal/lateral friction and static brake injection contracts verified");
    }
}
