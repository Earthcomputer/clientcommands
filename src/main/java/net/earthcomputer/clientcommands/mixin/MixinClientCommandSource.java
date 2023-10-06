package net.earthcomputer.clientcommands.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.suggestion.Suggestion;
import net.earthcomputer.clientcommands.command.Argument;
import net.earthcomputer.clientcommands.interfaces.IClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(ClientCommandSource.class)
public class MixinClientCommandSource implements IClientCommandSource {
    @Shadow
    @Final
    private ClientPlayNetworkHandler networkHandler;
    @Shadow
    @Final
    private MinecraftClient client;

    @Unique
    private ImmutableMap<Argument<?>, Object> arguments = ImmutableMap.of();

    @SuppressWarnings("unchecked")
    @Override
    public <T> T clientcommands_getArg(Argument<T> arg) {
        return (T) this.arguments.getOrDefault(arg, arg.getDefaultValue());
    }

    @Override
    public <T> IClientCommandSource clientcommands_withArg(Argument<T> arg, T value) {
        MixinClientCommandSource source = (MixinClientCommandSource) (Object) new ClientCommandSource(this.networkHandler, this.client);
        source.arguments = ImmutableMap.<Argument<?>, Object>builderWithExpectedSize(this.arguments.size() + 1).putAll(this.arguments).put(arg, value).build();
        return source;
    }

    @Override
    @Nullable
    public List<Suggestion> clientcommands_filterSuggestions(List<Suggestion> suggestions) {
        if (arguments.isEmpty()) {
            return null;
        } else {
            return suggestions.stream().filter(suggestion -> {
                String text = suggestion.getText();
                return !Argument.isFlag(text) || arguments.keySet().stream().noneMatch(arg -> !arg.isRepeatable() && arg.getFlag().equals(text));
            }).toList();
        }
    }
}
