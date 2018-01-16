package net.earthcomputer.clientcommands.core;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

public class NetHandlerTransformer implements IClassTransformer {

	private static final String OBJECT_DESC = "Ljava/lang/Object;";
	private static final String MINECRAFT_NAME = "net/minecraft/client/Minecraft";
	private static final String MINECRAFT_DESC = "L" + MINECRAFT_NAME + ";";
	private static final String NETWORK_MANAGER_NAME = "net/minecraft/network/NetworkManager";
	private static final String NETWORK_MANAGER_DESC = "L" + NETWORK_MANAGER_NAME + ";";
	private static final String PACKET_NAME = "net/minecraft/network/Packet";
	private static final String PACKET_MCP_DESC = "L" + PACKET_NAME + ";";
	private static final String PACKET_NOTCH_DESC = "Lht;";
	private static final String CHANNEL_NAME = "io/netty/channel/Channel";
	private static final String CHANNEL_DESC = "L" + CHANNEL_NAME + ";";
	private static final String GENERIC_FUTURE_LISTENER_DESC = "Lio/netty/util/concurrent/GenericFutureListener;";
	private static final String CHANNEL_HANDLER_CONTEXT_DESC = "Lio/netty/channel/ChannelHandlerContext;";

	private static final String GETMINECRAFT_MCP_NAME = "getMinecraft";
	private static final String GETMINECRAFT_SRG_NAME = "func_71410_x";
	private static final String ISCALLINGFROMMINECRAFTTHREAD_MCP_NAME = "isCallingFromMinecraftThread";
	private static final String ISCALLINGFROMMINECRAFTTHREAD_SRG_NAME = "func_152345_ab";

	private static final String CHANNEL_MCP_NAME = "channel";
	private static final String CHANNEL_SRG_NAME = "field_150746_k";
	private static final String SENDPACKET_MCP_NAME = "sendPacket";
	private static final String SENDPACKET_SRG_NAME = "func_179290_a";
	private static final String SENDPACKET_NOTCH_NAME = "a";
	private static final String SENDPACKET_MCP_DESC = "(" + PACKET_MCP_DESC + ")V";
	private static final String SENDPACKET_NOTCH_DESC = "(" + PACKET_NOTCH_DESC + ")V";
	private static final String SENDPACKET1_MCP_NAME = "sendPacket";
	private static final String SENDPACKET1_SRG_NAME = "func_179288_a";
	private static final String SENDPACKET1_NOTCH_NAME = "a";
	private static final String SENDPACKET1_MCP_DESC = "(" + PACKET_MCP_DESC + GENERIC_FUTURE_LISTENER_DESC + "["
			+ GENERIC_FUTURE_LISTENER_DESC + ")V";
	private static final String SENDPACKET1_NOTCH_DESC = "(" + PACKET_NOTCH_DESC + GENERIC_FUTURE_LISTENER_DESC + "["
			+ GENERIC_FUTURE_LISTENER_DESC + ")V";
	private static final String CHANNELREAD0_NAME = "channelRead0";
	private static final String CHANNELREAD0_DESC = "(" + CHANNEL_HANDLER_CONTEXT_DESC + OBJECT_DESC + ")V";

	private static final String EVENT_MANAGER_NAME = "net/earthcomputer/clientcommands/EventManager";
	private static final String EVENT_MANAGER_PRE_METHODS_DESC = "(" + NETWORK_MANAGER_DESC + PACKET_MCP_DESC + ")"
			+ PACKET_MCP_DESC;
	private static final String EVENT_MANAGER_POST_METHODS_DESC = "(" + NETWORK_MANAGER_DESC + PACKET_MCP_DESC + ")V";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		if (basicClass == null) {
			return null;
		}

		if ("net.minecraft.network.NetworkManager".equals(transformedName)) {
			ClassReader reader = new ClassReader(basicClass);
			ClassNode clazz = new ClassNode();
			reader.accept(clazz, ClassReader.SKIP_FRAMES);
			transformNetworkManager(clazz);
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			clazz.accept(writer);
			return writer.toByteArray();
		}

		if ("net.minecraft.network.PacketThreadUtil$1".equals(transformedName)) {
			ClassReader reader = new ClassReader(basicClass);
			ClassNode clazz = new ClassNode();
			reader.accept(clazz, ClassReader.SKIP_FRAMES);
			transformPacketThreadUtil_1(clazz);
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
			clazz.accept(writer);
			return writer.toByteArray();
		}

		return basicClass;
	}

	private void transformNetworkManager(ClassNode clazz) {
		for (MethodNode method : clazz.methods) {
			if (method.name.equals(SENDPACKET_MCP_NAME) || method.name.equals(SENDPACKET_SRG_NAME)
					|| method.name.equals(SENDPACKET_NOTCH_NAME)) {
				if (method.desc.equals(SENDPACKET_MCP_DESC) || method.desc.equals(SENDPACKET_NOTCH_DESC)) {
					transformSendPacketMethod(method);
				}
			}
			if (method.name.equals(SENDPACKET1_MCP_NAME) || method.name.equals(SENDPACKET1_SRG_NAME)
					|| method.name.equals(SENDPACKET1_NOTCH_NAME)) {
				if (method.desc.equals(SENDPACKET1_MCP_DESC) || method.desc.equals(SENDPACKET1_NOTCH_DESC)) {
					transformSendPacketMethod(method);
				}
			}
			if (method.name.equals(CHANNELREAD0_NAME)) {
				if (method.desc.equals(CHANNELREAD0_DESC)) {
					transformChannelRead0(method);
				}
			}
		}
	}

	private void transformSendPacketMethod(MethodNode method) {
		// @formatter:off
		/*
		 * Add:
		 * 
		 * packet = EventManager.firePacketOutboundPre(this, packet);
		 * if (packet == null)
		 * {
		 *    return;
		 * }
		 */
		// @formatter:on
		InsnList insns = new InsnList();
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_MANAGER_NAME, "firePacketOutboundPre",
				EVENT_MANAGER_PRE_METHODS_DESC, false));
		insns.add(new VarInsnNode(Opcodes.ASTORE, 1));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
		LabelNode l = new LabelNode();
		insns.add(new JumpInsnNode(Opcodes.IFNONNULL, l));
		insns.add(new InsnNode(Opcodes.RETURN));
		insns.add(l);
		method.instructions.insertBefore(method.instructions.getFirst(), insns);

		AbstractInsnNode returnInsn;
		for (returnInsn = method.instructions.getLast(); returnInsn
				.getOpcode() != Opcodes.RETURN; returnInsn = returnInsn.getPrevious())
			;

		// @formatter:off
		/*
		 * Add:
		 * 
		 * EventManager.firePacketOutboundPost(this, packet);
		 */
		// @formatter:on
		insns = new InsnList();
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_MANAGER_NAME, "firePacketOutboundPost",
				EVENT_MANAGER_POST_METHODS_DESC, false));
		method.instructions.insertBefore(returnInsn, insns);
	}

	private void transformChannelRead0(MethodNode method) {
		boolean isDevEnv = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");

		// @formatter:off
		/*
		 * Add:
		 * 
		 * if (this.channel.isOpen())
		 * {
		 *    if (Minecraft.getMinecraft().isCallingFromMinecraftThread())
		 *    {
		 *       packet = EventManager.firePacketInboundPre(this, (Packet) packet);
		 *       if (packet == null)
		 *       {
		 *          return;
		 *       }
		 *    }
		 * }
		 */
		// @formatter:on
		InsnList insns = new InsnList();
		LabelNode endLabel = new LabelNode();
		addChannelOpenAndMinecraftThreadTest(insns, endLabel, isDevEnv);
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
		insns.add(new TypeInsnNode(Opcodes.CHECKCAST, PACKET_NAME));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_MANAGER_NAME, "fireInboundPacketPre",
				EVENT_MANAGER_PRE_METHODS_DESC, false));
		insns.add(new VarInsnNode(Opcodes.ASTORE, 2));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
		insns.add(new JumpInsnNode(Opcodes.IFNONNULL, endLabel));
		insns.add(new InsnNode(Opcodes.RETURN));
		insns.add(endLabel);
		method.instructions.insertBefore(method.instructions.getFirst(), insns);

		AbstractInsnNode returnInsn;
		for (returnInsn = method.instructions.getLast(); returnInsn
				.getOpcode() != Opcodes.RETURN; returnInsn = returnInsn.getPrevious())
			;

		// @formatter:off
		/*
		 * Add:
		 * 
		 * if (this.channel.isOpen())
		 * {
		 *    if (Minecraft.getMinecraft().isCallingFromMinecraftThread())
		 *    {
		 *       EventManager.firePacketInboundPost(this, (Packet) packet);
		 *    }
		 * }
		 */
		// @formatter:on
		insns = new InsnList();
		endLabel = new LabelNode();
		addChannelOpenAndMinecraftThreadTest(insns, endLabel, isDevEnv);
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 2));
		insns.add(new TypeInsnNode(Opcodes.CHECKCAST, PACKET_NAME));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_MANAGER_NAME, "fireInboundPacketPost",
				EVENT_MANAGER_POST_METHODS_DESC, false));
		insns.add(endLabel);
		method.instructions.insertBefore(returnInsn, insns);
	}

	private void addChannelOpenAndMinecraftThreadTest(InsnList insns, LabelNode endLabel, boolean isDevEnv) {
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new FieldInsnNode(Opcodes.GETFIELD, NETWORK_MANAGER_NAME,
				isDevEnv ? CHANNEL_MCP_NAME : CHANNEL_SRG_NAME, CHANNEL_DESC));
		insns.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, CHANNEL_NAME, "isOpen", "()Z", true));
		insns.add(new JumpInsnNode(Opcodes.IFEQ, endLabel));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MINECRAFT_NAME,
				isDevEnv ? GETMINECRAFT_MCP_NAME : GETMINECRAFT_SRG_NAME, "()" + MINECRAFT_DESC, false));
		insns.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MINECRAFT_NAME,
				isDevEnv ? ISCALLINGFROMMINECRAFTTHREAD_MCP_NAME : ISCALLINGFROMMINECRAFTTHREAD_SRG_NAME, "()Z",
				false));
		insns.add(new JumpInsnNode(Opcodes.IFEQ, endLabel));
	}

	private void transformPacketThreadUtil_1(ClassNode clazz) {
		FieldNode packetField = null;
		for (FieldNode field : clazz.fields) {
			if (field.desc.equals(PACKET_MCP_DESC) || field.desc.equals(PACKET_NOTCH_DESC)) {
				packetField = field;
				break;
			}
		}
		packetField.access &= ~Opcodes.ACC_FINAL;

		for (MethodNode method : clazz.methods) {
			if (method.name.equals("run")) {
				if (method.desc.equals("()V")) {
					transformPacketThreadUtilRun(clazz.name, method, packetField);
				}
			}
		}
	}

	private void transformPacketThreadUtilRun(String className, MethodNode method, FieldNode packetField) {
		// @formatter:off
		/*
		 * Add:
		 * 
		 * this.val$packet = EventManager.firePacketInboundPre(null, this.val$packet);
		 * if (this.val$packet == null)
		 * {
		 *    return;
		 * }
		 */
		// @formatter:on
		InsnList insns = new InsnList();
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new InsnNode(Opcodes.ACONST_NULL));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new FieldInsnNode(Opcodes.GETFIELD, className, packetField.name, packetField.desc));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_MANAGER_NAME, "firePacketInboundPre",
				EVENT_MANAGER_PRE_METHODS_DESC, false));
		insns.add(new FieldInsnNode(Opcodes.PUTFIELD, className, packetField.name, packetField.desc));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new FieldInsnNode(Opcodes.GETFIELD, className, packetField.name, packetField.desc));
		LabelNode l = new LabelNode();
		insns.add(new JumpInsnNode(Opcodes.IFNONNULL, l));
		insns.add(new InsnNode(Opcodes.RETURN));
		insns.add(l);
		method.instructions.insertBefore(method.instructions.getFirst(), insns);

		AbstractInsnNode returnInsn;
		for (returnInsn = method.instructions.getLast(); returnInsn
				.getOpcode() != Opcodes.RETURN; returnInsn = returnInsn.getPrevious())
			;

		// @formatter:off
		/*
		 * Add:
		 * 
		 * EventManager.firePacketInboundPost(null, this.val$packet);
		 */
		// @formatter:on
		insns = new InsnList();
		insns.add(new InsnNode(Opcodes.ACONST_NULL));
		insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
		insns.add(new FieldInsnNode(Opcodes.GETFIELD, className, packetField.name, packetField.desc));
		insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, EVENT_MANAGER_NAME, "firePacketInboundPost",
				EVENT_MANAGER_POST_METHODS_DESC, false));
		method.instructions.insertBefore(returnInsn, insns);
	}

}
