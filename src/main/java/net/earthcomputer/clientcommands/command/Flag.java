package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Flag<T> {
    private final Class<T> type;
    private final String name;
    @Nullable
    private final Character shortName;
    private final T defaultValue;
    private final boolean repeatable;

    private Flag(Class<T> type, String name, @Nullable Character shortName, T defaultValue, boolean repeatable) {
        this.type = type;
        this.name = name;
        this.shortName = shortName;
        this.defaultValue = defaultValue;
        this.repeatable = repeatable;
    }

    public static <T> Flag.Builder<T> of(Class<T> type, String name) {
        return new Builder<>(type, name);
    }

    public static Flag.Builder<Boolean> ofFlag(String name) {
        return new Builder<>(Boolean.class, name).withDefaultValue(false);
    }

    public String getName() {
        return name;
    }

    @Nullable
    public String getShortFlag() {
        return shortName == null ? null : "-" + shortName;
    }

    public String getFlag() {
        return "--" + name;
    }

    public static boolean isFlag(String argument) {
        return (argument.startsWith("-") && argument.length() == 2) || argument.startsWith("--");
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public void addToCommand(CommandDispatcher<FabricClientCommandSource> dispatcher, LiteralCommandNode<FabricClientCommandSource> commandNode, Function<CommandContext<FabricClientCommandSource>, T> value) {
        dispatcher.register(commandNode.createBuilder()
            .then(ClientCommandManager.literal(getFlag())
                .redirect(commandNode, ctx -> ClientCommandHelper.withFlag(ctx.getSource(), this, value.apply(ctx)))));
        if (shortName != null) {
            dispatcher.register(commandNode.createBuilder()
                .then(ClientCommandManager.literal(getShortFlag())
                    .redirect(commandNode, ctx -> ClientCommandHelper.withFlag(ctx.getSource(), this, value.apply(ctx)))));
        }
    }

    public void addToCommandWithArg(CommandDispatcher<FabricClientCommandSource> dispatcher, LiteralCommandNode<FabricClientCommandSource> commandNode, ArgumentType<T> argument) {
        dispatcher.register(commandNode.createBuilder()
            .then(ClientCommandManager.literal(getFlag())
                .then(ClientCommandManager.argument(this.name, argument)
                    .redirect(commandNode, ctx -> ClientCommandHelper.withFlag(ctx.getSource(), this, ctx.getArgument(this.name, this.type))))));
        if (shortName != null) {
            dispatcher.register(commandNode.createBuilder()
                .then(ClientCommandManager.literal(getShortFlag())
                    .then(ClientCommandManager.argument(this.name, argument)
                        .redirect(commandNode, ctx -> ClientCommandHelper.withFlag(ctx.getSource(), this, ctx.getArgument(this.name, this.type))))));
        }
    }

    public static <S> S getActualSource(ParseResults<S> parse) {
        return getActualSource(parse.getContext().build(parse.getReader().getString()));
    }

    @SuppressWarnings("unchecked")
    public static <S> S getActualSource(CommandContext<S> ctx) {
        if (ctx.getRootNode() == Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).getCommandDispatcher().getRoot()) {
            // we're in the completion dispatcher, reparse using the real dispatcher to get the redirects
            return (S) getActualSource(Objects.requireNonNull(ClientCommandManager.getActiveDispatcher()).parse(
                StringRange.encompassing(ctx.getRange(), ctx.getLastChild().getRange()).get(ctx.getInput()),
                (FabricClientCommandSource) ctx.getSource()
            ));
        }

        S source = ctx.getSource();
        do {
            if (ctx.hasNodes()) {
                RedirectModifier<S> redirectModifier = ctx.getRedirectModifier();
                if (redirectModifier != null) {
                    try {
                        Collection<S> newSources = redirectModifier.apply(ctx.copyFor(source));
                        if (newSources.size() != 1) {
                            return source;
                        }
                        source = newSources.iterator().next();
                    } catch (CommandSyntaxException e) {
                        return source;
                    }
                }
            }
            ctx = ctx.getChild();
        } while (ctx != null);

        return source;
    }

    public static final class Builder<T> {
        private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]*");

        private final Class<T> type;
        private final String name;
        @Nullable
        private Character shortName;
        private boolean hasSetDefaultValue = false;
        private T defaultValue;
        private boolean repeatable;

        private Builder(Class<T> type, String name) {
            if (!NAME_PATTERN.matcher(name).matches()) {
                throw new IllegalArgumentException("Flag name does not match the pattern " + NAME_PATTERN.pattern());
            }

            this.type = type;
            this.name = name;
            this.shortName = name.charAt(0);
        }

        public Builder<T> withNoShortName() {
            this.shortName = null;
            return this;
        }

        public Builder<T> withShortName(char shortName) {
            this.shortName = shortName;
            return this;
        }

        public Builder<T> withDefaultValue(T defaultValue) {
            this.hasSetDefaultValue = true;
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder<T> repeatable() {
            this.repeatable = true;
            return this;
        }

        public Flag<T> build() {
            if (!hasSetDefaultValue) {
                throw new IllegalStateException("Default value for flag not set");
            }
            return new Flag<>(type, name, shortName, defaultValue, repeatable);
        }
    }
}
