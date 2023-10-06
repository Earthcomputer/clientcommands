package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

public final class Argument<T> {
    private final String name;
    private final T defaultValue;
    private final boolean repeatable;

    private Argument(String name, @Nullable T defaultValue, boolean repeatable) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.repeatable = repeatable;
    }

    public static <T> Argument<@Nullable T> of(String name) {
        return new Argument<>(name, null, false);
    }

    public static <T> Argument<T> ofDefaulted(String name, T defaultValue) {
        return new Argument<>(name, defaultValue, false);
    }

    public static <T> Argument<T> ofRepeatable(String name, T defaultValue) {
        return new Argument<>(name, defaultValue, true);
    }

    public static Argument<Boolean> ofFlag(String name) {
        return new Argument<>(name, false, false);
    }

    public String getName() {
        return name;
    }

    public String getFlag() {
        return "--" + name;
    }

    public static boolean isFlag(String argument) {
        return argument.startsWith("--");
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public boolean isRepeatable() {
        return repeatable;
    }

    public static <S> S getActualSource(ParseResults<S> parse) {
        return getActualSource(parse.getContext().build(parse.getReader().getString()));
    }

    @SuppressWarnings("unchecked")
    public static <S> S getActualSource(CommandContext<S> ctx) {
        if (ctx.getRootNode() == Objects.requireNonNull(MinecraftClient.getInstance().getNetworkHandler()).getCommandDispatcher().getRoot()) {
            // we're in the completion dispatcher, reparse using the real dispatcher to get the redirects
            return (S) getActualSource(Objects.requireNonNull(ClientCommandManager.getActiveDispatcher()).parse(
                ctx.getRange().get(ctx.getInput()),
                (FabricClientCommandSource) ctx.getSource()
            ));
        }

        S source = ctx.getSource();
        do {
            if (ctx.hasNodes()) {
                RedirectModifier<S> redirectModifier = ctx.getRedirectModifier();
                if (redirectModifier != null) {
                    try {
                        Collection<S> newSources = redirectModifier.apply(ctx);
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
}
