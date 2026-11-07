package net.aste.aeroworks;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Every config callback must reject other specs before reading or migrating any values. */
public final class ConfigRoutingTest {
    public static void main(String[] args) throws Exception {
        for (String type : new String[]{"failure/FailureSettings", "wind/WindSettings",
                "atmosphere/AtmosphereSettings", "atmosphere/PropellerSettings",
                "aerodynamics/AerodynamicSettings", "waves/WavePhysicsConfig"}) {
            var node = new ClassNode();
            try (var in = ConfigRoutingTest.class.getResourceAsStream("/net/aste/aeroworks/" + type + ".class")) {
                new ClassReader(in).accept(node, 0);
            }
            int checked = 0;
            for (var method : node.methods) {
                if (!method.name.equals("reload") && !method.name.equals("update")
                        && !(type.startsWith("aerodynamics/") && method.name.equals("loading"))) continue;
                var code = new java.util.ArrayList<AbstractInsnNode>();
                for (var insn : method.instructions) if (insn.getOpcode() >= 0) code.add(insn);
                String specOwner = type.startsWith("waves/")
                        ? "net/aste/aeroworks/waves/WavePhysicsConfig" : "net/aste/aeroworks/Config";
                boolean guard = code.size() > 6 && code.get(0).getOpcode() == Opcodes.ALOAD
                        && code.get(1) instanceof MethodInsnNode c && c.name.equals("getConfig")
                        && code.get(2) instanceof MethodInsnNode s && s.name.equals("getSpec")
                        && code.get(3) instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC
                        && f.owner.equals(specOwner) && f.name.equals("SPEC");
                if (!guard || !(code.get(4) instanceof JumpInsnNode branch))
                    throw new AssertionError(type + " reads config before checking its exact spec");
                // javac emits either 'if equal continue; return' or 'if different return; update'.
                AbstractInsnNode mismatch;
                if (branch.getOpcode() == Opcodes.IF_ACMPEQ) mismatch = code.get(5);
                else if (branch.getOpcode() == Opcodes.IF_ACMPNE) {
                    mismatch = branch.label;
                    while (mismatch.getOpcode() < 0) mismatch = mismatch.getNext();
                } else throw new AssertionError(type + " does not compare spec identities");
                if (mismatch.getOpcode() != Opcodes.RETURN)
                    throw new AssertionError(type + " does not immediately ignore another config file");
                checked++;
            }
            if (checked != 1) throw new AssertionError("Missing config reader for " + type);
        }
        System.out.println("Config routing: all six readers reject unrelated specs before any config access/migration");
    }
}
