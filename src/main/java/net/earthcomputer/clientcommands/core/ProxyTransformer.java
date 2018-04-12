package net.earthcomputer.clientcommands.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

public class ProxyTransformer implements IClassTransformer {

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (basicClass == null) {
			return null;
		}

		String internalName = transformedName.replace('.', '/');

		ClassReader reader = new ClassReader(basicClass);
		ClassNode node = new ClassNode();
		reader.accept(node, 0);

		FieldNode proxyField = null;
		if (node.fields != null) {
			for (FieldNode field : node.fields) {
				if (field.visibleAnnotations != null) {
					for (AnnotationNode ann : field.visibleAnnotations) {
						if ("Lnet/earthcomputer/clientcommands/util/Proxy;".equals(ann.desc)) {
							if (proxyField != null) {
								throw new AssertionError("There should not be more than one proxy field");
							}
							proxyField = field;
							break;
						}
					}
				}
			}
		}
		if (proxyField == null) {
			return basicClass;
		}

		Type proxyType = Type.getType(proxyField.desc);
		if (proxyType.getSort() != Type.OBJECT) {
			throw new AssertionError("The proxy type should be an object");
		}

		Class<?> proxyTypeClass;
		try {
			proxyTypeClass = Launch.classLoader.loadClass(proxyType.getInternalName().replace('/', '.'));
		} catch (ClassNotFoundException e) {
			throw new AssertionError("Proxy class doesn't exist");
		}
		for (Method method : proxyTypeClass.getMethods()) {
			if (!Modifier.isStatic(method.getModifiers()) && !Modifier.isFinal(method.getModifiers())
					&& (Modifier.isPublic(method.getModifiers()) || Modifier.isProtected(method.getModifiers()))) {
				String methodName = method.getName();
				String methodDesc = Type.getMethodDescriptor(method);
				if (isPresent(node, methodName, methodDesc)) {
					continue;
				}

				MethodNode delegateMethod = new MethodNode(
						method.getModifiers() & ~(Modifier.ABSTRACT | Modifier.NATIVE), methodName, methodDesc, null,
						Arrays.stream(method.getExceptionTypes()).map(Type::getInternalName).toArray(String[]::new));

				InsnList insns = new InsnList();
				int varIndex = 0;
				if ((proxyField.access & Opcodes.ACC_STATIC) == 0) {
					insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
					varIndex = 1;
				}
				insns.add(new FieldInsnNode(
						(proxyField.access & Opcodes.ACC_STATIC) != 0 ? Opcodes.GETSTATIC : Opcodes.GETFIELD,
						internalName, proxyField.name, proxyField.desc));
				for (Class<?> paramType : method.getParameterTypes()) {
					int opcode;
					int slots = 1;
					if (!paramType.isPrimitive()) {
						opcode = Opcodes.ALOAD;
					} else if (paramType == Long.TYPE) {
						opcode = Opcodes.LLOAD;
						slots = 2;
					} else if (paramType == Float.TYPE) {
						opcode = Opcodes.FLOAD;
					} else if (paramType == Double.TYPE) {
						opcode = Opcodes.DLOAD;
						slots = 2;
					} else {
						opcode = Opcodes.ILOAD;
					}
					insns.add(new VarInsnNode(opcode, varIndex));
					varIndex += slots;
				}
				boolean itf = proxyTypeClass.isInterface();
				insns.add(new MethodInsnNode(itf ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL,
						proxyType.getInternalName(), methodName, methodDesc, itf));
				Type returnType = Type.getReturnType(methodDesc);
				int opcode;
				switch (returnType.getSort()) {
				case Type.ARRAY:
				case Type.OBJECT:
					opcode = Opcodes.ARETURN;
					break;
				case Type.VOID:
					opcode = Opcodes.RETURN;
					break;
				case Type.LONG:
					opcode = Opcodes.LRETURN;
					break;
				case Type.FLOAT:
					opcode = Opcodes.FRETURN;
					break;
				case Type.DOUBLE:
					opcode = Opcodes.DRETURN;
					break;
				default:
					opcode = Opcodes.IRETURN;
					break;
				}
				insns.add(new InsnNode(opcode));

				delegateMethod.instructions = insns;
				node.methods.add(delegateMethod);
			}
		}

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		node.accept(writer);
		return writer.toByteArray();
	}

	private boolean isPresent(ClassNode clazz, String methodName, String methodDesc) {
		if (clazz.methods != null) {
			for (MethodNode method : clazz.methods) {
				if (methodName.equals(method.name) && methodDesc.equals(method.desc)) {
					return true;
				}
			}
		}
		return false;
	}

}
