package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class ClientEntitySelector {

    private final BiPredicate<Vec3, Entity> filter;
    private final BiConsumer<Vec3, List<Entity>> sorter;
    private final int limit;
    private final boolean senderOnly;
    private final Double originX;
    private final Double originY;
    private final Double originZ;

    public ClientEntitySelector(BiPredicate<Vec3, Entity> filter, BiConsumer<Vec3, List<Entity>> sorter, int limit, boolean senderOnly, Double originX, Double originY, Double originZ) {
        this.filter = filter;
        this.sorter = sorter;
        this.limit = limit;
        this.senderOnly = senderOnly;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public Entity getEntity(CommandSourceStack source) throws CommandSyntaxException {
        List<Entity> entities = getEntities(source);
        if (entities.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        }
        if (entities.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        }
        return entities.get(0);
    }

    public List<Entity> getEntities(CommandSourceStack source) {
        Vec3 origin = source.getPosition();
        origin = new Vec3(originX == null ? origin.x : originX, originY == null ? origin.y : originY, originZ == null ? origin.z : originZ);

        if (senderOnly) {
            return filter.test(origin, source.getEntity()) ? Collections.singletonList(source.getEntity()) : Collections.emptyList();
        }

        List<Entity> entities = new ArrayList<>();
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (filter.test(origin, entity)) {
                entities.add(entity);
            }
        }

        sorter.accept(origin, entities);

        return entities.size() <= limit ? entities : entities.subList(0, limit);
    }

    public int getLimit() {
        return limit;
    }
}
