package net.earthcomputer.clientcommands;

public final class Ptr<T> {

	private T val;

	public Ptr(T val) {
		this.val = val;
	}

	public T get() {
		return val;
	}

	public void set(T val) {
		this.val = val;
	}

}
