package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.StringReader;

public class CommandReader extends StringReader {
    public CommandReader(String command) {
        super(command);
    }

    public String readUnquotedString() {
        final int start = super.getCursor();
        while (super.canRead() && isAllowedInCommand(super.peek())) {
            super.skip();
        }
        return super.getString().substring(start, super.getCursor());
    }

    public static boolean isAllowedInCommand(final char c) {
        return c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z'
                || c >= 'a' && c <= 'z'
                || c == '_' || c == '-'
                || c == '.' || c == '+'
                || c == ':';
    }
}