package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import static dev.xpple.clientarguments.arguments.CBlockPosArgument.*;
import static dev.xpple.clientarguments.arguments.CEntityArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class VillagerCommand {
    private static final SimpleCommandExceptionType NOT_A_VILLAGER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvillager.notAVillager"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
            literal("cvillager")
                .then(literal("clock")
                    .then(argument("pos", blockPos())
                        .executes(ctx -> setClockBlockPos(ctx.getSource(), getBlockPos(ctx, "pos")))))
                .then(literal("target")
                    .then(argument("entity", entity())
                        .executes(ctx -> setVillagerTarget(ctx.getSource(), getEntity(ctx, "entity"))))));
    }

    private static int setClockBlockPos(FabricClientCommandSource source, BlockPos pos) {
        VillagerCracker.clockBlockPos = pos;
        source.getPlayer().sendSystemMessage(Component.translatable("commands.cvillager.clockSet", pos.getX(), pos.getY(), pos.getZ()));
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerTarget(FabricClientCommandSource source, Entity target) throws CommandSyntaxException {
        if (!(target instanceof Villager villager)) {
            throw NOT_A_VILLAGER_EXCEPTION.create();
        }

        VillagerCracker.setTargetVillager(villager);
        source.getPlayer().sendSystemMessage(Component.translatable("commands.cvillager.targetSet"));

        return Command.SINGLE_SUCCESS;
    }
}
