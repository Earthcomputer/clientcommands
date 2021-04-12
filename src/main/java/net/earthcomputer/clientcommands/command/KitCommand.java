package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.serialization.Dynamic;
import net.earthcomputer.clientcommands.interfaces.ISlot;
import net.fabricmc.fabric.api.util.NbtType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class KitCommand {

    private static final Logger logger = LogManager.getLogger("clientcommands");

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.saveFile.failed"));

    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.create.alreadyExists", arg));

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.load.notCreative"));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Map<String, ListTag> kits = new HashMap<>();

    static {
        try {
            loadFile();
        } catch (IOException e) {
            logger.info("Could not load kits file, hence /ckit will not work!");
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ckit");

        LiteralCommandNode<ServerCommandSource> ckit = dispatcher.register(literal("ckit"));
        dispatcher.register(literal("ckit")
                .then(literal("create")
                        .then(argument("name", StringArgumentType.string())
                                .executes(ctx -> create(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("delete")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(kits.keySet(), builder))
                                .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("edit")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(kits.keySet(), builder))
                                .executes(ctx -> edit(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                .then(literal("load")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(kits.keySet(), builder))
                                .then(literal("--override")
                                        .executes(ctx -> load(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true)))
                                .executes(ctx -> load(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))))
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource()))));
    }

    private static int create(ServerCommandSource source, String name) throws CommandSyntaxException {
        if (kits.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }
        kits.put(name, client.player.inventory.serialize(new ListTag()));
        saveFile();
        sendFeedback("commands.ckit.create.success", name);
        return 0;
    }

    private static int delete(ServerCommandSource source, String name) throws CommandSyntaxException {
        if (kits.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        saveFile();
        sendFeedback("commands.ckit.delete.success", name);
        return 0;
    }

    private static int edit(ServerCommandSource source, String name) throws CommandSyntaxException {
        if (!kits.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        kits.put(name, client.player.inventory.serialize(new ListTag()));
        saveFile();
        sendFeedback("commands.ckit.edit.success", name);
        return 0;
    }

    private static int load(ServerCommandSource source, String name, boolean override) throws CommandSyntaxException {
        if (!client.player.abilities.creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        ListTag kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        PlayerInventory tempInv = new PlayerInventory(client.player);
        tempInv.deserialize(kit);
        List<Slot> slots = client.player.playerScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory == client.player.inventory) {
                ItemStack itemStack = tempInv.getStack(((ISlot) slots.get(i)).getIndex());
                if (!itemStack.isEmpty() || override) {
                    client.interactionManager.clickCreativeStack(itemStack, i);
                }
            }
        }

        client.player.playerScreenHandler.sendContentUpdates();
        sendFeedback("commands.ckit.load.success", name);
        return 0;
    }

    private static int list(ServerCommandSource source) {
        String list = String.join(", ", kits.keySet());
        sendFeedback(list.equals("") ? "No available kits" : "Available kits: " + list);
        return kits.size();
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            CompoundTag compoundTag = new CompoundTag();
            kits.forEach(compoundTag::put);
            rootTag.putInt("DataVersion", SharedConstants.getGameVersion().getWorldVersion());
            rootTag.put("Kits", compoundTag);
            File newFile = File.createTempFile("kits", ".dat", configPath.toFile());
            NbtIo.write(rootTag, newFile);
            File backupFile = new File(configPath.toFile(), "kits.dat_old");
            File currentFile = new File(configPath.toFile(), "kits.dat");
            Util.backupAndReplace(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        kits.clear();
        CompoundTag rootTag = NbtIo.read(new File(configPath.toFile(), "kits.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getGameVersion().getWorldVersion();
        final int fileVersion = rootTag.getInt("DataVersion");
        CompoundTag compoundTag = rootTag.getCompound("Kits");
        if (fileVersion >= currentVersion) {
            compoundTag.getKeys().forEach(key -> kits.put(key, compoundTag.getList(key, NbtType.COMPOUND)));
        } else {
            compoundTag.getKeys().forEach(key -> {
                ListTag updatedListTag = new ListTag();
                compoundTag.getList(key, NbtType.COMPOUND).forEach(tag -> {
                    Dynamic<Tag> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<Tag> newTagDynamic = client.getDataFixer().update(TypeReferences.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                kits.put(key, updatedListTag);
            });
        }
    }
}
