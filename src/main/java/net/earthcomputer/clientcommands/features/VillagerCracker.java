package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.interfaces.IVillager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class VillagerCracker {
    // This value was computed by brute forcing all seeds
    public static final float MAX_ERROR = 0x1.4p-24f;

    @Nullable
    private static UUID villagerUuid = null;
    @Nullable
    private static WeakReference<Villager> cachedVillager = null;

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
        if (Minecraft.getInstance().level != null) {
            for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                if (entity.getUUID() == villagerUuid && entity instanceof Villager villager) {
                    cachedVillager = new WeakReference<>(villager);
                    return villager;
                }
            }
        }
        return null;
    }

    public static void setTargetVillager(@Nullable Villager villager) {
        Villager oldVillager = getVillager();
        if (oldVillager != null) {
            ((IVillager) oldVillager).clientcommands_setCrackedRandom(null);
        }

        VillagerCracker.cachedVillager = new WeakReference<>(villager);
        VillagerCracker.villagerUuid = villager == null ? null : villager.getUUID();
    }

    public static void onSoundEventPlayed(ClientboundSoundPacket packet) {
        Villager targetVillager = getVillager();
        if (targetVillager == null) {
            return;
        }

        if (packet.getSound().value().getLocation().toString().equals("minecraft:entity.villager.ambient")) {
            ((IVillager) targetVillager).clientcommands_onAmbientSoundPlayed(packet.getPitch());
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
