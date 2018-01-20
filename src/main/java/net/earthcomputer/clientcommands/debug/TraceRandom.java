package net.earthcomputer.clientcommands.debug;

import java.util.Arrays;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.earthcomputer.clientcommands.ClientCommandsMod;

public class TraceRandom extends Random {

	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LogManager.getLogger(ClientCommandsMod.MODID);

	private int nextCalls = 0;
	private String name;

	public TraceRandom(String name) {
		super(0);
		this.name = name;
	}

	private int nextCalls() {
		int calls = nextCalls;
		nextCalls = 0;
		return calls;
	}

	private void logNextCalls(String methodName, Object returned) {
		LOGGER.info("{}.{}: {} next calls, {} returned", name, methodName, nextCalls(), returned);
	}

	@Override
	protected int next(int bits) {
		nextCalls++;
		return super.next(bits);
	}

	@Override
	public boolean nextBoolean() {
		boolean b = super.nextBoolean();
		logNextCalls("nextBoolean()", b);
		return b;
	}

	@Override
	public void nextBytes(byte[] arr) {
		super.nextBytes(arr);
		logNextCalls("nextBytes(byte[" + arr.length + "])", Arrays.toString(arr));
	}

	@Override
	public double nextDouble() {
		double d = super.nextDouble();
		logNextCalls("nextDouble()", d);
		return d;
	}

	@Override
	public float nextFloat() {
		float f = super.nextFloat();
		logNextCalls("nextFloat()", f);
		return f;
	}

	@Override
	public synchronized double nextGaussian() {
		double d = super.nextGaussian();
		logNextCalls("nextGaussian()", d);
		return d;
	}

	@Override
	public int nextInt() {
		int i = super.nextInt();
		logNextCalls("nextInt()", i);
		return i;
	}

	@Override
	public int nextInt(int bound) {
		int i = super.nextInt(bound);
		logNextCalls("nextInt(" + bound + ")", i);
		return i;
	}

	@Override
	public long nextLong() {
		long l = super.nextLong();
		logNextCalls("nextLong()", l);
		return l;
	}

	@Override
	public synchronized void setSeed(long seed) {
		super.setSeed(seed);
		LOGGER.info("{}: Set the seed to {}", name, seed);
	}

}
