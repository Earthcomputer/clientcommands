package net.earthcomputer.clientcommands.c2c;

import java.nio.charset.StandardCharsets;

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

    public void writeString(String string) {
        this.buffer.append(string).append('\0');
    }

    public void writeInt(int integer) {
        this.buffer.append(integer).append('\0');
    }
}
