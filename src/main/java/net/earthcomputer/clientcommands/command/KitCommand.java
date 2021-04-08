package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.earthcomputer.clientcommands.interfaces.ISlot;
import net.fabricmc.fabric.api.util.NbtType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Util;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.addClientSideCommand;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.sendFeedback;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KitCommand {

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.saveFile.failed"));
    private static final SimpleCommandExceptionType LOAD_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.loadFile.failed"));

    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.create.alreadyExists", arg));

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.load.notCreative"));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Map<String, PlayerInventory> kits = new HashMap<>();

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
        loadFile();

        if (kits.get(name) != null) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }
        kits.put(name, client.player.inventory);
        saveFile();
        sendFeedback("commands.ckit.create.success", name);
        return 0;
    }

    private static int delete(ServerCommandSource source, String name) throws CommandSyntaxException {
        loadFile();

        if (kits.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        saveFile();
        sendFeedback("commands.ckit.delete.success", name);
        return 0;
    }

    private static int edit(ServerCommandSource source, String name) throws CommandSyntaxException {
        loadFile();

        if (kits.get(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        kits.put(name, client.player.inventory);
        saveFile();
        sendFeedback("commands.ckit.edit.success", name);
        return 0;
    }

    private static int load(ServerCommandSource source, String name, boolean override) throws CommandSyntaxException {
        if (!client.player.isCreative() || !client.player.abilities.creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        loadFile();

        PlayerInventory kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        List<Slot> slots = client.player.playerScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory == client.player.inventory) {
                ItemStack itemStack = kit.getStack(((ISlot) slots.get(i)).getIndex());
                if (!itemStack.isEmpty() || override) { // same as if (!(itemStack.isEmpty() && !override))
                    client.interactionManager.clickCreativeStack(itemStack, i);
                }
            }
        }

        client.player.playerScreenHandler.sendContentUpdates();
        sendFeedback("commands.ckit.load.success", name);
        return 0;
    }

    private static int list(ServerCommandSource source) throws CommandSyntaxException {
        loadFile();

        String list = String.join(", ", kits.keySet());
        sendFeedback(list.equals("") ? "No available kits" : "Available kits: " + list);
        return kits.size();
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag compoundTag = new CompoundTag();

            kits.forEach((key, value) -> compoundTag.put(key, value.serialize(new ListTag())));

            File newFile = File.createTempFile("kits", ".dat", configPath.toFile());
            NbtIo.write(compoundTag, newFile);
            File backupFile = new File(configPath.toFile(), "kits.dat_old");
            File currentFile = new File(configPath.toFile(), "kits.dat");
            Util.backupAndReplace(currentFile, newFile, backupFile);
        } catch (Exception e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws CommandSyntaxException {
        try {
            kits.clear();
            CompoundTag compoundTag = NbtIo.read(new File(configPath.toFile(), "kits.dat"));
            if (compoundTag == null) {
                return;
            }
            compoundTag.getKeys().forEach(key -> {
                PlayerInventory inventory = new PlayerInventory(client.player);
                inventory.deserialize(compoundTag.getList(key, NbtType.COMPOUND));
                kits.put(key, inventory);
            });
        } catch (Exception e) {
            throw LOAD_FAILED_EXCEPTION.create();
        }
    }
}
