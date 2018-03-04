package net.earthcomputer.clientcommands.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.launchwrapper.IClassTransformer;

public class IntegratedServerRaceFixTransformer implements IClassTransformer {

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (!"net.minecraft.client.Minecraft".equals(transformedName)) {
			return basicClass;
		}
		if (basicClass == null) {
			return null;
		}
		
		ClassReader reader = new ClassReader(basicClass);
		ClassNode clazz = new ClassNode();
		reader.accept(clazz, 0);

		for (MethodNode method : clazz.methods) {
			if ("(Lbrz;Ljava/lang/String;)V".equals(method.desc)
					|| "(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V".equals(method.desc)) {
				if ("a".equals(method.name) || "func_71353_a".equals(method.name) || "loadWorld".equals(method.name)) {
					transformLoadWorld(method);
					break;
				}
			}
		}

		ClassWriter writer = new ClassWriter(0);
		clazz.accept(writer);
		return writer.toByteArray();
	}

	private void transformLoadWorld(MethodNode method) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
				MethodInsnNode methodInsn = (MethodInsnNode) insn;
				if ("net/minecraft/server/MinecraftServer".equals(methodInsn.owner) || "chb".equals(methodInsn.owner)
						|| "net/minecraft/server/integrated/IntegratedServer".equals(methodInsn.owner)) {
					if ("()V".equals(methodInsn.desc)) {
						if ("x".equals(methodInsn.name) || "func_71263_m".equals(methodInsn.name)
								|| "initiateShutdown".equals(methodInsn.name)) {
							method.instructions.set(insn, new InsnNode(Opcodes.POP));
							break;
						}
					}
				}
			}
		}
	}

}
