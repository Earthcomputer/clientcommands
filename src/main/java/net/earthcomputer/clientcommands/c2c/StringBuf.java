package net.earthcomputer.clientcommands.c2c;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntFunction;

public class StringBuf {

    private final StringBuilder buffer;
    private int cursor = 0;

    public StringBuf(String string) {
        this.buffer = new StringBuilder(string);
    }

    public StringBuf() {
        this("");
    }

    public byte[] bytes() {
        return this.buffer.toString().getBytes(StandardCharsets.UTF_8);
    }

    public int getRemainingLength() {
        return this.buffer.length() - this.cursor;
    }

    public String readString() {
        int start = this.cursor;
        while (this.buffer.charAt(this.cursor) != '\0') {
            this.cursor++;
        }
        return this.buffer.substring(start, this.cursor++);
    }

    public int readInt() {
        return Integer.parseInt(this.readString());
    }

    public <E, C extends Collection<E>> C readCollection(IntFunction<C> collectionCreator, Function<StringBuf, E> elementReader) {
        final int count = readInt();
        final C result = collectionCreator.apply(count);
        for (int i = 0; i < count; i++) {
            result.add(elementReader.apply(this));
        }
        return result;
    }

    public void writeString(String string) {
        this.buffer.append(string).append('\0');
    }

    public void writeInt(int integer) {
        this.buffer.append(integer).append('\0');
    }

    public <E> void writeCollection(Collection<E> collection, BiConsumer<StringBuf, E> elementWriter) {
        writeInt(collection.size());
        for (final E element : collection) {
            elementWriter.accept(this, element);
        }
    }
}
