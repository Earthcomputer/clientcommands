package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.command.ClientEntitySelector;
import net.earthcomputer.clientcommands.command.FakeCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RenderSettings {

    private static final List<Pair<ClientEntitySelector, Boolean>> entityRenderSelectors = new ArrayList<>();
    private static final Set<UUID> disabledEntities = new HashSet<>();

    public static void clearEntityRenderSelectors() {
        if (Relogger.isRelogging) {
            var oldSelectors = new ArrayList<>(entityRenderSelectors);
            Relogger.relogSuccessTasks.add(() -> entityRenderSelectors.addAll(oldSelectors));
        }
        entityRenderSelectors.clear();
    }

    public static void addEntityRenderSelector(ClientEntitySelector selector, boolean shouldRender) {
        if (entityRenderSelectors.size() == 16)
            entityRenderSelectors.remove(0);
        entityRenderSelectors.add(new Pair<>(selector, shouldRender));
    }

    public static void preRenderEntities() {
        ServerCommandSource source = new FakeCommandSource(MinecraftClient.getInstance().player);

        disabledEntities.clear();
        for (var filter : entityRenderSelectors) {
            List<UUID> entities = filter.getLeft().getEntities(source).stream().map(Entity::getUuid).collect(Collectors.toList());
            if (filter.getRight()) {
                entities.forEach(disabledEntities::remove);
            } else {
                disabledEntities.addAll(entities);
            }
        }
    }

    public static boolean shouldRenderEntity(Entity entity) {
        return !disabledEntities.contains(entity.getUuid());
    }

}
