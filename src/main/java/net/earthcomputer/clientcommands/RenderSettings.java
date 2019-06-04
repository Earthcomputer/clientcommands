package net.earthcomputer.clientcommands;

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

public class RenderSettings {

    private static List<Pair<ClientEntitySelector, Boolean>> entityRenderSelectors = new ArrayList<>();
    private static Set<Entity> disabledEntities = new HashSet<>();

    public static void clearEntityRenderSelectors() {
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
        for (Pair<ClientEntitySelector, Boolean> filter : entityRenderSelectors) {
            if (filter.getRight()) {
                disabledEntities.removeAll(filter.getLeft().getEntities(source));
            } else {
                disabledEntities.addAll(filter.getLeft().getEntities(source));
            }
        }
    }

    public static boolean shouldRenderEntity(Entity entity) {
        return !disabledEntities.contains(entity);
    }

}
