package net.earthcomputer.clientcommands.features;

import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class RenderSettings {

    private static final List<Pair<CEntitySelector, Boolean>> entityRenderSelectors = new ArrayList<>();
    private static final Set<UUID> disabledEntities = new HashSet<>();

    public static void clearEntityRenderSelectors() {
        if (Relogger.isRelogging) {
            var oldSelectors = new ArrayList<>(entityRenderSelectors);
            Relogger.relogSuccessTasks.add(() -> entityRenderSelectors.addAll(oldSelectors));
        }
        entityRenderSelectors.clear();
    }

    public static void addEntityRenderSelector(CEntitySelector selector, boolean shouldRender) {
        if (entityRenderSelectors.size() == 16)
            entityRenderSelectors.remove(0);
        entityRenderSelectors.add(new Pair<>(selector, shouldRender));
    }

    public static void preRenderEntities() {
        MinecraftClient client = MinecraftClient.getInstance();
        // prevent crash from other mods trying to load entity rendering without a world (usually a fake world and no client player)
        if (client.player == null) return;
        FabricClientCommandSource source = (FabricClientCommandSource) new ClientCommandSource(client.getNetworkHandler(), client);

        disabledEntities.clear();
        for (var filter : entityRenderSelectors) {
            try {
                List<UUID> entities = filter.getLeft().getEntities(source).stream().map(Entity::getUuid).collect(Collectors.toList());
                if (filter.getRight()) {
                    entities.forEach(disabledEntities::remove);
                } else {
                    disabledEntities.addAll(entities);
                }
            } catch (CommandSyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean shouldRenderEntity(Entity entity) {
        return !disabledEntities.contains(entity.getUuid());
    }

}
