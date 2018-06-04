package net.earthcomputer.clientcommands.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;

public class RenderManagerTransformer implements IClassTransformer {

	static {
		ClientCommandsLoadingPlugin.EXPECTED_TASKS.add("transformRenderManager");
		ClientCommandsLoadingPlugin.EXPECTED_TASKS.add("transformShouldRender");
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (basicClass == null)
			return null;

		if (!"net.minecraft.client.renderer.entity.RenderManager".equals(transformedName))
			return basicClass;

		ClassReader reader = new ClassReader(basicClass);
		ClassNode clazz = new ClassNode();
		reader.accept(clazz, 0);

		transformRenderManager(clazz);

		ClassWriter writer = new ClassWriter(0);
		clazz.accept(writer);
		return writer.toByteArray();
	}

	private static void transformRenderManager(ClassNode clazz) {
		ClientCommandsLoadingPlugin.EXPECTED_TASKS.remove("transformRenderManager");
		for (MethodNode method : clazz.methods) {
			if ("shouldRender".equals(method.name) || "func_178635_a".equals(method.name) || "a".equals(method.name)) {
				if ("(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;DDD)Z"
						.equals(method.desc) || "(Lvg;Lbxy;DDD)Z".equals(method.desc)
						|| "(Lve;Lbxw;DDD)Z".equals(method.desc)) {
					transformShouldRender(method);
					break;
				}
			}
		}
	}

	private static void transformShouldRender(MethodNode method) {
		ClientCommandsLoadingPlugin.EXPECTED_TASKS.remove("transformShouldRender");

		// @formatter:off
		/*
		 * Add:
		 * 
		 * if (RenderSettings.isEntityRenderingDisabled(entityIn.getClass()))
		 *    return false;
		 */
		// @formatter:on

		InsnList insns = new InsnList();
		insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
		insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;",
				false));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/earthcomputer/clientcommands/render/RenderSettings",
				"isEntityRenderingDisabled", "(Ljava/lang/Class;)Z", false));
		LabelNode label = new LabelNode();
		insns.add(new JumpInsnNode(Opcodes.IFEQ, label));
		insns.add(new InsnNode(Opcodes.ICONST_0));
		insns.add(new InsnNode(Opcodes.IRETURN));
		insns.add(label);
		insns.add(new FrameNode(Opcodes.F_SAME, 0, new Object[0], 0, new Object[0]));

		method.instructions.insertBefore(method.instructions.getFirst(), insns);
	}

}
