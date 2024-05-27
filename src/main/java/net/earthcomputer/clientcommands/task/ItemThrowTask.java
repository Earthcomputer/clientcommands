package net.earthcomputer.clientcommands.task;

import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.event.MoreClientEntityEvents;
import net.earthcomputer.clientcommands.features.PlayerRandCracker;
import net.earthcomputer.clientcommands.features.SuggestionsHook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Set;

public class ItemThrowTask extends SimpleTask {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<Object> MUTEX_KEYS = Set.of(ItemThrowTask.class);

    public static final int FLAG_URGENT = 1;
    public static final int FLAG_WAIT_FOR_ITEMS = 2;

    private static WeakReference<ItemThrowTask> currentThrowTask = null;

    static {
        MoreClientEntityEvents.POST_ADD.register(ItemThrowTask::handleItemSpawn);
    }

    private final int totalItemsToThrow;
    private final int flags;

    private int confirmedItemThrows;
    private int sentItemThrows;
    private float itemThrowsAllowedThisTick;
    private boolean waitingFence = false;
    private boolean failed = false;

    public ItemThrowTask(int itemsToThrow) {
        this(itemsToThrow, 0);
    }

    public ItemThrowTask(int itemsToThrow, int flags) {
        this.totalItemsToThrow = itemsToThrow;
        this.flags = flags;
    }

    @Override
    public boolean condition() {
        return waitingFence || sentItemThrows != totalItemsToThrow || sentItemThrows > confirmedItemThrows;
    }

    @Override
    protected void onTick() {
        itemThrowsAllowedThisTick += Configs.itemThrowsPerTick;

        while (((flags & FLAG_URGENT) != 0 || itemThrowsAllowedThisTick >= 1) && sentItemThrows < totalItemsToThrow) {
            itemThrowsAllowedThisTick--;
            if (!PlayerRandCracker.throwItem()) {
                if ((flags & FLAG_WAIT_FOR_ITEMS) != 0) {
                    return;
                }
                failed = true;
                _break();
                onFailedToThrowItem();
                return;
            }
        }

        if (!waitingFence && sentItemThrows == totalItemsToThrow && confirmedItemThrows < sentItemThrows) {
            waitingFence = true;
            SuggestionsHook.fence().thenAccept(v -> {
                if (sentItemThrows > confirmedItemThrows) {
                    LOGGER.info("Server rejected {} item throws. Rethrowing them.", sentItemThrows - confirmedItemThrows);
                    while (sentItemThrows > confirmedItemThrows) {
                        PlayerRandCracker.unthrowItem();
                        sentItemThrows--;
                    }
                }
                waitingFence = false;
            });
        }
    }

    @Override
    public void initialize() {
        currentThrowTask = new WeakReference<>(this);
    }

    @Override
    public void onCompleted() {
        if (!failed) {
            onSuccess();
        }
        currentThrowTask = null;
    }

    @Override
    public Set<Object> getMutexKeys() {
        return MUTEX_KEYS;
    }

    protected void onFailedToThrowItem() {
    }

    protected void onSuccess() {
    }

    protected void onItemSpawn(ClientboundAddEntityPacket packet) {
    }

    private static void handleItemSpawn(ClientboundAddEntityPacket packet) {
        if (packet.getType() != EntityType.ITEM) {
            return;
        }

        ItemThrowTask task = currentThrowTask == null ? null : currentThrowTask.get();
        if (task == null) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (player.getEyePosition().distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) > 1) {
            return;
        }

        task.confirmedItemThrows++;
        task.onItemSpawn(packet);
    }

    @Override
    public String toString() {
        return "ItemThrowTask[totalItemsToThrow=" + totalItemsToThrow + ",flags=" + flags + "]";
    }
}
