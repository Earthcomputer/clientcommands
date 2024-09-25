package net.earthcomputer.clientcommands.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.lang.reflect.Field;
import java.util.Map;

public final class BrigadierRemover<S> {

    private static final Field CHILDREN_FIELD, LITERALS_FIELD, ARGUMENTS_FIELD;
    static {
        try {
            CHILDREN_FIELD = CommandNode.class.getDeclaredField("children");
            LITERALS_FIELD = CommandNode.class.getDeclaredField("literals");
            ARGUMENTS_FIELD = CommandNode.class.getDeclaredField("arguments");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        CHILDREN_FIELD.setAccessible(true);
        LITERALS_FIELD.setAccessible(true);
        ARGUMENTS_FIELD.setAccessible(true);
    }

    private static final BrigadierRemover<?> NULL = new BrigadierRemover<>(null, null);

    private final CommandNode<S> parentNode;
    private final CommandNode<S> thisNode;

    private BrigadierRemover(CommandNode<S> parentNode, CommandNode<S> thisNode) {
        this.parentNode = parentNode;
        this.thisNode = thisNode;
    }

    public static <S> BrigadierRemover<S> of(CommandDispatcher<S> dispatcher) {
        return new BrigadierRemover<>(null, dispatcher.getRoot());
    }

    @SuppressWarnings("unchecked")
    public BrigadierRemover<S> get(String child) {
        if (thisNode == null) {
            return (BrigadierRemover<S>) NULL;
        }
        return new BrigadierRemover<>(thisNode, thisNode.getChild(child));
    }

    @SuppressWarnings("unchecked")
    public boolean remove() {
        if (thisNode == null || parentNode == null) {
            return false;
        }

        Map<String, CommandNode<S>> parentChildren;
        Map<String, LiteralCommandNode<S>> parentLiterals;
        Map<String, ArgumentCommandNode<S, ?>> parentArguments;
        try {
            parentChildren = (Map<String, CommandNode<S>>) CHILDREN_FIELD.get(parentNode);
            parentLiterals = thisNode instanceof LiteralCommandNode ? (Map<String, LiteralCommandNode<S>>) LITERALS_FIELD.get(parentNode) : null;
            parentArguments = thisNode instanceof ArgumentCommandNode ? (Map<String, ArgumentCommandNode<S, ?>>) ARGUMENTS_FIELD.get(parentNode) : null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }

        parentChildren.remove(thisNode.getName());
        if (thisNode instanceof LiteralCommandNode) {
            parentLiterals.remove(thisNode.getName());
        } else if (thisNode instanceof ArgumentCommandNode) {
            parentArguments.remove(thisNode.getName());
        }

        return true;
    }

}
