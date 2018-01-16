package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public class CommandFindBlock extends ClientCommandBase {

	// The maximum block search radius, in blocks
	private static final int MAX_RADIUS = 16 * 8;

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if (args.length < 1) {
			throw new WrongUsageException(getUsage(sender));
		}

		Block block = getBlockByText(sender, args[0]);
		int radius = args.length < 2 ? MAX_RADIUS : parseInt(args[1], 0, MAX_RADIUS);
		String radiusType = args.length < 3 ? "cartesian" : args[2];

		// compile a list of block positions within the radius which might match the
		// query
		List<BlockPos> candidates;

		switch (radiusType) {
		case "cartesian":
		case "square":
			candidates = findBlockCandidatesInSquareArea(sender, block, radius, radiusType);
			break;
		case "taxicab":
			candidates = findBlockCandidatesInTaxicabArea(sender, block, radius);
			break;
		default:
			throw new CommandException("Unknown radius type " + radiusType);
		}

		if (candidates.isEmpty()) {
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "No such block found"));
			return;
		}

		// find the closest matched block based on the radius type
		BlockPos closestBlock;
		switch (radiusType) {
		case "cartesian":
			closestBlock = candidates.stream().filter(it -> it.distanceSq(sender.getPosition()) <= radius * radius)
					.min(Comparator.comparingDouble(it -> it.distanceSq(sender.getPosition()))).orElse(null);
			break;
		case "square":
			closestBlock = candidates.stream()
					.filter(it -> Math.max(
							Math.max(Math.abs(sender.getPosition().getX() - it.getX()),
									Math.abs(sender.getPosition().getZ() - it.getZ())),
							Math.abs(sender.getPosition().getY() - it.getY())) <= radius)
					.min(Comparator.comparingInt(it -> Math.max(
							Math.max(Math.abs(sender.getPosition().getX() - it.getX()),
									Math.abs(sender.getPosition().getZ() - it.getZ())),
							Math.abs(sender.getPosition().getY() - it.getY()))))
					.orElse(null);
			break;
		case "taxicab":
			closestBlock = candidates.stream()
					.filter(it -> Math.abs(sender.getPosition().getX() - it.getX())
							+ Math.abs(sender.getPosition().getY() - it.getY())
							+ Math.abs(sender.getPosition().getZ() - it.getZ()) <= radius)
					.min(Comparator.comparingInt(it -> Math.abs(sender.getPosition().getX() - it.getX())
							+ Math.abs(sender.getPosition().getY() - it.getY())
							+ Math.abs(sender.getPosition().getZ() - it.getZ())))
					.orElse(null);
			break;
		default:
			throw new AssertionError();
		}

		// output the block
		if (closestBlock == null) {
			sender.sendMessage(new TextComponentString(TextFormatting.RED + "No such block found"));
		} else {
			float distance;
			switch (radiusType) {
			case "cartesian":
				distance = MathHelper.sqrt(closestBlock.distanceSq(sender.getPosition()));
				break;
			case "square":
				distance = Math.max(
						Math.max(Math.abs(sender.getPosition().getX() - closestBlock.getX()),
								Math.abs(sender.getPosition().getZ() - closestBlock.getZ())),
						Math.abs(sender.getPosition().getY() - closestBlock.getY()));
				break;
			case "taxicab":
				distance = Math.abs(sender.getPosition().getX() - closestBlock.getX())
						+ Math.abs(sender.getPosition().getY() - closestBlock.getY())
						+ Math.abs(sender.getPosition().getZ() - closestBlock.getZ());
				break;
			default:
				throw new AssertionError();
			}
			sender.sendMessage(
					new TextComponentString("Closest match is at ").appendSibling(getCoordsTextComponent(closestBlock))
							.appendSibling(new TextComponentString(String.format(", %.2f blocks away", distance))));
		}
	}

	private List<BlockPos> findBlockCandidatesInSquareArea(ICommandSender sender, Block block, int radius,
			String radiusType) {
		World world = Minecraft.getMinecraft().world;
		BlockPos senderPos = sender.getPosition();
		ChunkPos chunkPos = new ChunkPos(senderPos);

		List<BlockPos> blockCandidates = new ArrayList<>();

		// search in each chunk with an increasing radius, until we increase the radius
		// past an already found block
		int chunkRadius = (radius >> 4) + 1;
		for (int r = 0; r < chunkRadius; r++) {
			for (int chunkX = chunkPos.x - r; chunkX <= chunkPos.x + r; chunkX++) {
				for (int chunkZ = chunkPos.z - r; chunkZ <= chunkPos.z
						+ r; chunkZ += chunkX == chunkPos.x - r || chunkX == chunkPos.x + r ? 1 : r + r) {
					Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
					if (searchChunkForBlockCandidates(chunk, senderPos.getY(), block, blockCandidates)) {
						// update new, potentially shortened, radius
						int dx = chunkPos.x - chunkX;
						int dz = chunkPos.z - chunkZ;
						int newChunkRadius;
						if ("cartesian".equals(radiusType)) {
							newChunkRadius = MathHelper.ceil(MathHelper.sqrt(dx * dx + dz * dz) + MathHelper.SQRT_2);
						} else {
							newChunkRadius = Math.max(Math.abs(chunkPos.x - chunkX), Math.abs(chunkPos.z - chunkZ)) + 1;
						}
						if (newChunkRadius < chunkRadius) {
							chunkRadius = newChunkRadius;
						}
					}
				}
			}
		}

		return blockCandidates;
	}

	private List<BlockPos> findBlockCandidatesInTaxicabArea(ICommandSender sender, Block block, int radius) {
		World world = Minecraft.getMinecraft().world;
		BlockPos senderPos = sender.getPosition();
		ChunkPos chunkPos = new ChunkPos(senderPos);

		List<BlockPos> blockCandidates = new ArrayList<>();

		// search in each chunk with an increasing radius, until we increase the radius
		// past an already found block
		int chunkRadius = (radius >> 4) + 1;
		for (int r = 0; r < chunkRadius; r++) {
			for (int chunkX = chunkPos.x - r; chunkX <= chunkPos.x + r; chunkX++) {
				int chunkZ = chunkPos.z - (r - Math.abs(chunkPos.x - chunkX));
				for (int i = 0; i < 2; i++) {
					Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
					if (searchChunkForBlockCandidates(chunk, senderPos.getY(), block, blockCandidates)) {
						// update new, potentially shortened, radius
						int newChunkRadius = Math.abs(chunkPos.x - chunkX) + Math.abs(chunkPos.z - chunkZ) + 1;
						if (newChunkRadius < chunkRadius) {
							chunkRadius = newChunkRadius;
						}
					}

					chunkZ = chunkPos.z + (r - Math.abs(chunkPos.x - chunkX));
				}
			}
		}

		return blockCandidates;
	}

	private boolean searchChunkForBlockCandidates(Chunk chunk, int senderY, Block block,
			List<BlockPos> blockCandidates) {
		boolean found = false;
		int maxY = chunk.getTopFilledSegment() + 15;

		// search every column for the block
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				// search the column nearest to the sender first, and stop if we find the block
				int maxDy = Math.max(senderY, maxY - senderY);
				for (int dy = 0; dy <= maxDy; dy = dy > 0 ? -dy : -dy + 1) {
					if (senderY + dy < 0 || senderY + dy >= 256) {
						continue;
					}
					if (chunk.getBlockState(x, senderY + dy, z).getBlock() == block) {
						blockCandidates.add(new BlockPos((chunk.x << 4) + x, senderY + dy, (chunk.z << 4) + z));
						found = true;
						break;
					}
				}
			}
		}

		return found;
	}

	@Override
	public String getName() {
		return "cfindblock";
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "/cfindblock <block> [radius] [radiustype]";
	}

	@Override
	public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, Block.REGISTRY.getKeys());
		}
		if (args.length == 3) {
			return getListOfStringsMatchingLastWord(args, "cartesian", "taxicab", "square");
		}
		return Collections.emptyList();
	}

}
