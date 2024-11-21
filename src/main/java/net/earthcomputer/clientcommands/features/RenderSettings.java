package net.earthcomputer.clientcommands.features;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.clientarguments.arguments.CEntitySelector;
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
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

public class RenderSettings {
    private static final List<Tuple<CEntitySelector, Boolean>> entityRenderSelectors = new ArrayList<>();
    private static final Set<UUID> disabledEntities = new HashSet<>();

    static {
        ClientConnectionEvents.DISCONNECT.register(RenderSettings::clearEntityRenderSelectors);
    }

    private static void clearEntityRenderSelectors() {
        if (Relogger.isRelogging) {
            var oldSelectors = new ArrayList<>(entityRenderSelectors);
            Relogger.relogSuccessTasks.add(() -> entityRenderSelectors.addAll(oldSelectors));
        }
        entityRenderSelectors.clear();
    }

    public static void addEntityRenderSelector(CEntitySelector selector, boolean shouldRender) {
        if (entityRenderSelectors.size() == 16) {
            entityRenderSelectors.removeFirst();
        }
        entityRenderSelectors.add(new Tuple<>(selector, shouldRender));
    }

    public static void preRenderEntities() {
        Minecraft minecraft = Minecraft.getInstance();
        // prevent crash from other mods trying to load entity rendering without a level (usually a fake level and no client player)
        if (minecraft.player == null) {
            return;
        }
        FabricClientCommandSource source = (FabricClientCommandSource) new ClientSuggestionProvider(minecraft.getConnection(), minecraft);

        disabledEntities.clear();
        for (var filter : entityRenderSelectors) {
            try {
                List<UUID> entities = filter.getA().findEntities(source).stream().map(Entity::getUUID).toList();
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
