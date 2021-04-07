package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.earthcomputer.clientcommands.interfaces.ISlot;
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

    private static final SimpleCommandExceptionType SAVE_FILE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.save.failed"));
    private static final SimpleCommandExceptionType LOAD_FILE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.load.failed"));

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.kit.notCreative"));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.kit.notFound", arg));

    private static final Path path = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

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
                .then(literal("kit")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(kits.keySet(), builder))
                                .then(literal("--override")
                                        .executes(ctx -> kit(ctx.getSource(), StringArgumentType.getString(ctx, "name"), true)))
                                .executes(ctx -> kit(ctx.getSource(), StringArgumentType.getString(ctx, "name"), false))))
                .then(literal("kits")
                        .executes(ctx -> kits(ctx.getSource()))));
    }

    private static int create(ServerCommandSource source, String name) throws CommandSyntaxException {
        loadFile();

        if (kits.put(name, client.player.inventory) == null) {
            saveFile();
            sendFeedback("commands.ckit.create.success", name);
        } else {
            saveFile();
            sendFeedback("commands.ckit.create.alreadyExists", name);
        }
        return 1;
    }

    private static int delete(ServerCommandSource source, String name) throws CommandSyntaxException {
        loadFile();

        if (kits.remove(name) == null) throw NOT_FOUND_EXCEPTION.create(name);
        saveFile();
        sendFeedback("commands.ckit.delete.success", name);
        return 1;
    }

    private static int edit(ServerCommandSource source, String name) throws CommandSyntaxException {
        loadFile();

        if (kits.put(name, client.player.inventory) == null) {
            saveFile();
            sendFeedback("commands.ckit.edit.notFoundSoCreatedNew", name);
        } else {
            saveFile();
            sendFeedback("commands.ckit.edit.success", name);
        }
        return 1;
    }

    private static int kit(ServerCommandSource source, String name, boolean override) throws CommandSyntaxException {
        if (!client.player.isCreative() || !client.player.abilities.creativeMode) throw NOT_CREATIVE_EXCEPTION.create();

        loadFile();

        PlayerInventory kit = kits.get(name);
        if (kit == null) throw NOT_FOUND_EXCEPTION.create(name);

        for (int i = 0; i < kit.main.size(); i++) {
            if (kit.main.get(i) == ItemStack.EMPTY && !override) continue;
            client.player.inventory.main.set(i, kit.main.get(i));
        }
        for (int i = 0; i < kit.armor.size(); i++) {
            if (kit.armor.get(i) == ItemStack.EMPTY && !override) continue;
            client.player.inventory.armor.set(i, kit.armor.get(i));
        }
        for (int i = 0; i < kit.offHand.size(); i++) {
            if (kit.offHand.get(i) == ItemStack.EMPTY && !override) continue;
            client.player.inventory.offHand.set(i, kit.offHand.get(i));
        }

        List<Slot> slots = client.player.playerScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory == client.player.inventory) {
                client.interactionManager.clickCreativeStack(kit.getStack(((ISlot) slots.get(i)).getIndex()), i);
            }
        }

        client.player.playerScreenHandler.sendContentUpdates();
        sendFeedback("commands.ckit.kit.success", name);
        return 1;
    }

    private static int kits(ServerCommandSource source) throws CommandSyntaxException {
        loadFile();

        sendFeedback("Available kits: " + String.join(", ", kits.keySet()));
        return 1;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag compoundTag = new CompoundTag();

            for (Map.Entry<String, PlayerInventory> entry : kits.entrySet()) {
                compoundTag.put(entry.getKey(), entry.getValue().serialize(new ListTag()));
            }

            File file = File.createTempFile("kits", ".dat", path.toFile());
            NbtIo.write(compoundTag, file);
            File file2 = new File(path.toFile(), "kits.dat_old");
            File file3 = new File(path.toFile(), "kits.dat");
            Util.backupAndReplace(file3, file, file2);
        } catch (Exception e) {
            throw SAVE_FILE_EXCEPTION.create();
        }
    }

    private static void loadFile() throws CommandSyntaxException {
        try {
            kits.clear();
            CompoundTag compoundTag = NbtIo.read(new File(path.toFile(), "kits.dat"));
            if (compoundTag == null) return;

            compoundTag.getKeys().forEach(key -> {
                PlayerInventory inventory = new PlayerInventory(client.player);
                inventory.deserialize(compoundTag.getList(key, 10));
                kits.put(key, inventory);
            });
        } catch (Exception e) {
            throw LOAD_FILE_EXCEPTION.create();
        }
    }
}