package net.earthcomputer.clientcommands;

import net.minecraft.container.Property;

import java.util.function.IntConsumer;

public class DelegatingProperty extends Property {

    private final Property delegate;
    private final IntConsumer changeListener;

    public DelegatingProperty(Property delegate, IntConsumer changeListener) {
        this.delegate = delegate;
        this.changeListener = changeListener;
    }

    @Override
    public int get() {
        return delegate.get();
    }

    @Override
    public void set(int var1) {
        delegate.set(var1);
        changeListener.accept(var1);
    }
}
