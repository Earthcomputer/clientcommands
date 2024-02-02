package net.earthcomputer.clientcommands.features;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RenderSettings {

    private static final List<Tuple<CEntitySelector, Boolean>> entityRenderSelectors = new ArrayList<>();
    private static final Set<UUID> disabledEntities = new HashSet<>();

    public static void clearEntityRenderSelectors() {
        if (Relogger.isRelogging) {
            var oldSelectors = new ArrayList<>(entityRenderSelectors);
            Relogger.relogSuccessTasks.add(() -> entityRenderSelectors.addAll(oldSelectors));
        }
        entityRenderSelectors.clear();
    }

    public static void addEntityRenderSelector(CEntitySelector selector, boolean shouldRender) {
        if (entityRenderSelectors.size() == 16) {
            entityRenderSelectors.remove(0);
        }
        entityRenderSelectors.add(new Tuple<>(selector, shouldRender));
    }

    public static void preRenderEntities() {
        Minecraft client = Minecraft.getInstance();
        // prevent crash from other mods trying to load entity rendering without a world (usually a fake world and no client player)
        if (client.player == null) {
            return;
        }
        FabricClientCommandSource source = (FabricClientCommandSource) new ClientSuggestionProvider(client.getConnection(), client);

        disabledEntities.clear();
        for (var filter : entityRenderSelectors) {
            try {
                List<UUID> entities = filter.getA().getEntities(source).stream().map(Entity::getUUID).collect(Collectors.toList());
                if (filter.getB()) {
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
        return !disabledEntities.contains(entity.getUUID());
    }

}
