package net.earthcomputer.clientcommands.util;

public final class Box {

	private int x;
	private int y;
	private int z;
	private int xSize;
	private int ySize;
	private int zSize;

	public Box(int x, int y, int z, int xSize, int ySize, int zSize) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.xSize = xSize;
		this.ySize = ySize;
		this.zSize = zSize;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getZ() {
		return z;
	}

	public void setZ(int z) {
		this.z = z;
	}

	public int getXSize() {
		return xSize;
	}

	public void setXSize(int xSize) {
		this.xSize = xSize;
	}

	public int getYSize() {
		return ySize;
	}

	public void setYSize(int ySize) {
		this.ySize = ySize;
	}

	public int getZSize() {
		return zSize;
	}

	public void setZSize(int zSize) {
		this.zSize = zSize;
	}
	
	public boolean isEmpty() {
		return xSize == 0 || ySize == 0 || zSize == 0;
	}

	@Override
	public int hashCode() {
		int hash = 0;
		hash = 31 * hash + x;
		hash = 31 * hash + y;
		hash = 31 * hash + z;
		hash = 31 * hash + xSize;
		hash = 31 * hash + ySize;
		hash = 31 * hash + zSize;
		return hash;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Box && equals((Box) other);
	}

	public boolean equals(Box other) {
		return x == other.x
				&& y == other.y
				&& z == other.z
				&& xSize == other.xSize
				&& ySize == other.ySize
				&& zSize == other.zSize;
	}

	@Override
	public String toString() {
		return "(" + x + ", " + y + ", " + z + ", " + xSize + ", " + ySize + ", " + zSize + ")";
	}

}
