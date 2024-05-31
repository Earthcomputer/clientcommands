package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class VillagerCracker {
    @Nullable
    private static UUID villagerUuid = null;
    @Nullable
    private static WeakReference<Villager> cachedVillager = null;

    @Nullable
    public static BlockPos clockBlockPos = null;

    @Nullable
    public static Villager getVillager() {
        if (villagerUuid == null) {
            cachedVillager = null;
            return null;
        }
        if (cachedVillager != null) {
            Villager villager = cachedVillager.get();
            if (villager != null && !villager.isRemoved()) {
                return villager;
            }
        }
        for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
            if (entity.getUUID() == villagerUuid && entity instanceof Villager villager) {
                cachedVillager = new WeakReference<>(villager);
                return villager;
            }
        }
        return null;
    }

    public static void setTargetVillager(Villager villager) {
        VillagerCracker.cachedVillager = new WeakReference<>(villager);
        VillagerCracker.villagerUuid = villager.getUUID();
    }

    public static void onSoundEventPlayed(ClientboundSoundPacket packet) {
        Villager targetVillager = getVillager();
        if (targetVillager == null) {
            return;
        }

        if (packet.getSound().value().getLocation().getPath().startsWith("item.armor.equip_")) {
            long seed = packet.getSeed();
            long[] possible = CrackVillagerRngGen.getSeeds(seed).toArray();
            if (possible.length == 0) {
                ClientCommandHelper.sendError(Component.translatable("commands.cvillager.crackFailed"));
            } else {
                ((IVillager) targetVillager).clientcommands_setCrackedRandom(RandomSource.create(possible[0] ^ 0x5deece66dL));
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("commands.cvillager.crackSuccess", Long.toHexString(possible[0])));
            }
        }

        if (packet.getSound().value().getLocation().getPath().equals("entity.villager.ambient")) {
            ((IVillager) targetVillager).clientcommands_onAmbientSoundPlayed();
        }
    }

    public static void onServerTick() {
        Villager targetVillager = getVillager();
        if (targetVillager == null) {
            return;
        }

        ((IVillager) targetVillager).clientcommands_onServerTick();
    }
}
