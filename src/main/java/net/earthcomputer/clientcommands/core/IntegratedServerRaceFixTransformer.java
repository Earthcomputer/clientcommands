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
		if (!"net.minecraft.network.NetHandlerPlayServer".equals(transformedName)) {
			return basicClass;
		}
		if (basicClass == null) {
			return null;
		}

		ClassReader reader = new ClassReader(basicClass);
		ClassNode clazz = new ClassNode();
		reader.accept(clazz, 0);

		for (MethodNode method : clazz.methods) {
			if ("(Lhh;)V".equals(method.desc) || "(Lnet/minecraft/util/text/ITextComponent;)V".equals(method.desc)) {
				if ("a".equals(method.name) || "func_147231_a".equals(method.name)
						|| "onDisconnect".equals(method.name)) {
					transformOnDisconnect(method);
					break;
				}
			}
		}

		ClassWriter writer = new ClassWriter(0);
		clazz.accept(writer);
		return writer.toByteArray();
	}

	private void transformOnDisconnect(MethodNode method) {
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
				MethodInsnNode methodInsn = (MethodInsnNode) insn;
				if ("net/minecraft/server/MinecraftServer".equals(methodInsn.owner) || "chb".equals(methodInsn.owner)
						|| "net/minecraft/server/integrated/IntegratedServer".equals(methodInsn.owner)) {
					if ("()V".equals(methodInsn.desc)) {
						if ("x".equals(methodInsn.name) || "func_71263_m".equals(methodInsn.name)
								|| "initiateShutdown".equals(methodInsn.name)) {
							method.instructions.set(insn, insn = new InsnNode(Opcodes.POP));
							break;
						}
					}
				}
			}
		}
	}

}
