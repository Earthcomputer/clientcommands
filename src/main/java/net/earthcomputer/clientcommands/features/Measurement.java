package net.earthcomputer.clientcommands.features;

import com.seedfinding.latticg.reversal.DynamicProgram;
import com.seedfinding.latticg.reversal.calltype.CallType;
import com.seedfinding.latticg.reversal.calltype.java.JavaCalls;
import com.seedfinding.latticg.util.Range;
import net.minecraft.util.RandomSource;


public abstract class Measurement {
    public abstract void apply(DynamicProgram program);

    public abstract void apply(RandomSource random);

    public static Measurement nextFloat(float value) {
        return new FloatMeasurement(value);
    }

    public static Measurement nextFloat(float value, float range) {
        return new FloatRangedMeasurement(value, range);
    }

    public static Measurement skip() {
        return skip(1);
    }

    public static Measurement skip(int count) {
        return new SkipMeasurement(count);
    }

    public static Measurement nextInt(int bound, int value) {
        return new IntMeasurement(bound, value);
    }

    static class SkipMeasurement extends Measurement {
        int count;
        SkipMeasurement(int count) {
            this.count = count;
        }

        @Override
        public void apply(DynamicProgram program) {
            program.skip(count);
        }

        @Override
        public void apply(RandomSource random) {
            for(var i = 0; i < count; i++) {
                random.nextFloat();
            }
        }
    }

    static class FloatMeasurement extends Measurement {
        float value;

        static CallType<Range<Float>> callType =  JavaCalls.nextFloat().ranged(0.04f);

        FloatMeasurement(float value) {
            this.value = value;
        }

        CallType<Range<Float>> getCallType() {
            return callType;
        }

        float getRange() {
            return 0.02f;
        }

        @Override
        public void apply(DynamicProgram program) {
            program.add(getCallType(), Range.of(value-getRange(), value+getRange()));
        }

        @Override
        public void apply(RandomSource random) {
            random.nextFloat();
        }
    }

    static class FloatRangedMeasurement extends FloatMeasurement {
        float range;

        FloatRangedMeasurement(float value, float range) {
            super(value);
            this.range = range;
        }

        @Override
        CallType<Range<Float>> getCallType() {
            return JavaCalls.nextFloat().ranged(this.range * 2f);
        }

        @Override
        public float getRange() {
            return range;
        }
    }

    static class IntMeasurement extends Measurement {
        CallType<Integer> callType;
        int bound;
        int value;
        IntMeasurement(int bound, int value) {
            this.value = value;
            this.bound = bound;
            callType = JavaCalls.nextInt(bound);
        }

        @Override
        public void apply(DynamicProgram program) {
            program.add(callType, this.value);
        }

        @Override
        public void apply(RandomSource random) {
            random.nextInt(bound);
        }
    }
}
