package net.earthcomputer.clientcommands.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.lwjgl.util.Rectangle;

import net.earthcomputer.clientcommands.network.NetUtils;
import net.earthcomputer.clientcommands.util.Box;
import net.minecraft.util.math.BlockPos;

public abstract class CommandAreaExtension extends ClientCommandBase {

	private static final int CUBE_SIDE = 32; // cbrt(32768)
	private static final int CUBE_VOLUME = CUBE_SIDE * CUBE_SIDE * CUBE_SIDE;

	protected void areaExecute(BlockPos from, BlockPos to, Object[] extraArgs) {
		// https://stackoverflow.com/questions/37644269/dividing-a-cuboid-of-n-into-smaller-cuboids-of-m-volume

		int minX = Math.min(from.getX(), to.getX());
		int minY = Math.min(from.getY(), to.getY());
		int minZ = Math.min(from.getZ(), to.getZ());
		int maxX = Math.max(from.getX(), to.getX());
		int maxY = Math.max(from.getY(), to.getY());
		int maxZ = Math.max(from.getZ(), to.getZ());
		from = new BlockPos(minX, minY, minZ);
		to = new BlockPos(maxX, maxY, maxZ);

		int xSize = to.getX() - from.getX() + 1;
		int ySize = to.getY() - from.getY() + 1;
		int zSize = to.getZ() - from.getZ() + 1;

		// Cubes
		int cubeXCount = xSize / CUBE_SIDE;
		int cubeYCount = ySize / CUBE_SIDE;
		int cubeZCount = zSize / CUBE_SIDE;
		int remainingXSize = xSize % CUBE_SIDE;
		int remainingYSize = ySize % CUBE_SIDE;
		int remainingZSize = zSize % CUBE_SIDE;
		int remainingXStart = xSize - remainingXSize;
		int remainingYStart = ySize - remainingYSize;
		int remainingZStart = zSize - remainingZSize;

		for (int cubeY = 0; cubeY < cubeYCount; cubeY++) {
			for (int cubeX = 0; cubeX < cubeXCount; cubeX++) {
				for (int cubeZ = 0; cubeZ < cubeZCount; cubeZ++) {
					BlockPos cubeCorner = from.add(cubeX * CUBE_SIDE, cubeY * CUBE_SIDE, cubeZ * CUBE_SIDE);
					NetUtils.sendChatMessage(makeCommand(
							cubeCorner,
							cubeCorner.add(CUBE_SIDE - 1, CUBE_SIDE - 1, CUBE_SIDE - 1),
							extraArgs));
				}
			}
		}

		// One of the axes is going to contain the "full square" with the corner piece
		// One of the other axes will be "long" in that it contains the remaining beam

		// From now on, a "solution" is all the boxes, minus the cubes from before

		// The best solution so far
		List<Box> bestSolution;
		// The boxes making up the full square
		List<Box> fullSquareSolution;
		// The boxes making up the long side
		List<Box> longSolution;
		// The boxes making up the short side
		List<Box> shortSolution;
		// All the boxes in the current solution
		List<Box> curSolution;

		// Y is full square:
		{
			fullSquareSolution = toBoxY(solve2D(remainingYSize, xSize, zSize), remainingYStart, remainingYSize);
			// X is long:
			{
				longSolution = toBoxX(solve2D(remainingXSize, zSize, remainingYStart), remainingXStart, remainingXSize);
				shortSolution = toBoxZ(solve2D(remainingZSize, remainingXStart, remainingYStart), remainingZStart,
						remainingZSize);
				curSolution = new ArrayList<>();
				curSolution.addAll(fullSquareSolution);
				curSolution.addAll(longSolution);
				curSolution.addAll(shortSolution);
				bestSolution = curSolution;
			}
			// Z is long:
			{
				longSolution = toBoxZ(solve2D(remainingZSize, xSize, remainingYStart), remainingZStart, remainingZSize);
				shortSolution = toBoxX(solve2D(remainingXSize, remainingZStart, remainingYStart), remainingXStart,
						remainingXSize);
				curSolution = new ArrayList<>();
				curSolution.addAll(fullSquareSolution);
				curSolution.addAll(longSolution);
				curSolution.addAll(shortSolution);
				if (curSolution.size() < bestSolution.size())
					bestSolution = curSolution;
			}
		}

		// X is full square:
		{
			fullSquareSolution = toBoxX(solve2D(remainingXSize, zSize, ySize), remainingXStart, remainingXSize);
			// Y is long:
			{
				longSolution = toBoxY(solve2D(remainingYSize, remainingXStart, zSize), remainingYStart, remainingYSize);
				shortSolution = toBoxZ(solve2D(remainingZSize, remainingXStart, remainingYStart), remainingZStart,
						remainingZSize);
				curSolution = new ArrayList<>();
				curSolution.addAll(longSolution);
				curSolution.addAll(fullSquareSolution);
				curSolution.addAll(shortSolution);
				if (curSolution.size() < bestSolution.size())
					bestSolution = curSolution;
			}
			// Z is long:
			{
				longSolution = toBoxZ(solve2D(remainingZSize, remainingXStart, ySize), remainingZStart, remainingZSize);
				shortSolution = toBoxY(solve2D(remainingYSize, remainingXStart, remainingZStart), remainingYStart,
						remainingYSize);
				curSolution = new ArrayList<>();
				curSolution.addAll(shortSolution);
				curSolution.addAll(fullSquareSolution);
				curSolution.addAll(longSolution);
				if (curSolution.size() < bestSolution.size())
					bestSolution = curSolution;
			}
		}

		// Z is full square:
		{
			fullSquareSolution = toBoxZ(solve2D(remainingZSize, xSize, ySize), remainingZStart, remainingZSize);
			// X is long:
			{
				longSolution = toBoxX(solve2D(remainingXSize, remainingZStart, ySize), remainingXStart, remainingXSize);
				shortSolution = toBoxY(solve2D(remainingYSize, remainingXStart, remainingZStart), remainingYStart,
						remainingYSize);
				curSolution = new ArrayList<>();
				curSolution.addAll(shortSolution);
				curSolution.addAll(fullSquareSolution);
				curSolution.addAll(longSolution);
				if (curSolution.size() < bestSolution.size())
					bestSolution = curSolution;
			}
			// Y is long:
			{
				longSolution = toBoxY(solve2D(remainingYSize, xSize, remainingZStart), remainingYStart, remainingYSize);
				shortSolution = toBoxX(solve2D(remainingXSize, remainingZStart, remainingYStart), remainingXStart,
						remainingXSize);
				curSolution = new ArrayList<>();
				curSolution.addAll(longSolution);
				curSolution.addAll(fullSquareSolution);
				curSolution.addAll(shortSolution);
				if (curSolution.size() < bestSolution.size())
					bestSolution = curSolution;
			}
		}

		for (Box box : bestSolution) {
			BlockPos corner = from.add(box.getX(), box.getY(), box.getZ());
			NetUtils.sendChatMessage(makeCommand(
					corner,
					corner.add(box.getXSize() - 1, box.getYSize() - 1, box.getZSize() - 1),
					extraArgs));
		}
	}

	private List<Rectangle> solve2D(int depth, int width, int height) {
		if (depth == 0)
			return Collections.emptyList();
		int maxArea = CUBE_VOLUME / depth;
		int rectWidth = (int) Math.sqrt(maxArea);
		int rectHeight = maxArea / rectWidth;
		if (rectWidth >= width) {
			if (rectHeight >= height) {
				return Collections.singletonList(new Rectangle(0, 0, width, height));
			} else if (rectWidth < rectHeight) {
				int tmp = rectWidth;
				rectWidth = rectHeight;
				rectHeight = tmp;
			}
		} else if (rectHeight < rectWidth) {
			int tmp = rectWidth;
			rectWidth = rectHeight;
			rectHeight = tmp;
		}
		List<Rectangle> rects = new ArrayList<>();

		// Full rectangles
		int rectXCount = width / rectWidth;
		int rectYCount = height / rectHeight;
		int remainingWidth = width % rectWidth;
		int remainingHeight = height % rectHeight;
		int remainingXStart = width - remainingWidth;
		int remainingYStart = height - remainingHeight;

		for (int rectY = 0; rectY < rectYCount; rectY++) {
			for (int rectX = 0; rectX < rectXCount; rectX++) {
				rects.add(new Rectangle(rectX * rectWidth, rectY * rectHeight, rectWidth, rectHeight));
			}
		}

		// Partial rectangles
		int rectWidthBottom = remainingHeight == 0 ? Integer.MAX_VALUE : maxArea / remainingHeight;
		int rectHeightRight = remainingWidth == 0 ? Integer.MAX_VALUE : maxArea / remainingWidth;
		if (remainingYStart % rectHeightRight == 0 ||
				remainingYStart * rectWidthBottom + width * rectHeightRight < height * rectWidthBottom
						+ remainingXStart * rectHeightRight) {
			for (int y = 0; y < remainingYStart; y += rectHeightRight) {
				rects.add(new Rectangle(remainingXStart, y, remainingWidth,
						Math.min(rectHeightRight, remainingYStart - y)));
			}
			for (int x = 0; x < width; x += rectWidthBottom) {
				rects.add(new Rectangle(x, remainingYStart, Math.min(rectWidthBottom, width - x), remainingHeight));
			}
		} else {
			for (int y = 0; y < remainingYStart; y += rectHeightRight) {
				rects.add(new Rectangle(remainingXStart, y, remainingWidth, Math.min(rectHeightRight, height - y)));
			}
			for (int x = 0; x < remainingWidth; x += rectWidthBottom) {
				rects.add(new Rectangle(x, remainingYStart, Math.min(rectWidthBottom, remainingXStart - x),
						remainingHeight));
			}
		}

		return rects;
	}

	private static List<Box> toBoxX(List<Rectangle> rects, int remainingXStart, int remainingXSize) {
		return rects.stream()
				.map(rect -> new Box(
						remainingXStart,
						rect.getY(),
						rect.getX(),
						remainingXSize,
						rect.getHeight(),
						rect.getWidth()))
				.filter(box -> !box.isEmpty())
				.collect(Collectors.toList());
	}

	private static List<Box> toBoxY(List<Rectangle> rects, int remainingYStart, int remainingYSize) {
		return rects.stream()
				.map(rect -> new Box(
						rect.getX(),
						remainingYStart,
						rect.getY(),
						rect.getWidth(),
						remainingYSize,
						rect.getHeight()))
				.filter(box -> !box.isEmpty())
				.collect(Collectors.toList());
	}

	private static List<Box> toBoxZ(List<Rectangle> rects, int remainingZStart, int remainingZSize) {
		return rects.stream()
				.map(rect -> new Box(
						rect.getX(),
						rect.getY(),
						remainingZStart,
						rect.getWidth(),
						rect.getHeight(),
						remainingZSize))
				.filter(box -> !box.isEmpty())
				.collect(Collectors.toList());
	}

	protected abstract String makeCommand(BlockPos from, BlockPos to, Object[] extraArgs);

}
