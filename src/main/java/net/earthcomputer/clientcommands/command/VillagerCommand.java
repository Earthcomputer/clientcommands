package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.features.VillagerCracker;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
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
                .then(literal("timer")
                    .then(argument("value", blockPos())
                        .executes(ctx -> setTimerBlockPos(ctx.getSource(), getBlockPos(ctx, "value")))))
                .then(literal("target")
                    .then(argument("value", entity())
                        .executes(ctx -> setVillagerTarget(ctx.getSource(), getEntity(ctx, "value"))))));
    }

    private static int setTimerBlockPos(FabricClientCommandSource source, BlockPos pos) {
        VillagerCracker.timerBlockPos = pos;
        Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.timerSet", pos.getX(), pos.getY(), pos.getZ()));
        return Command.SINGLE_SUCCESS;
    }

    private static int setVillagerTarget(FabricClientCommandSource source, Entity target) throws CommandSyntaxException {
        if (!(target instanceof Villager villager)) {
            throw NOT_A_VILLAGER_EXCEPTION.create();
        }

        VillagerCracker.setTargetVillager(villager);
        Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.targetSet"));

        return Command.SINGLE_SUCCESS;
    }
}
