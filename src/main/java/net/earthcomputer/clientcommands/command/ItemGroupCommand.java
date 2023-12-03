package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemStackSet;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CItemStackArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.*;

public class ItemGroupCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.citemgroup.notFound", arg));
    private static final DynamicCommandExceptionType OUT_OF_BOUNDS_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.citemgroup.outOfBounds", arg));

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.citemgroup.saveFile.failed"));
    private static final DynamicCommandExceptionType ILLEGAL_CHARACTER_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.citemgroup.addGroup.illegalCharacter", arg));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.citemgroup.addGroup.alreadyExists", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final Map<String, Group> groups = new HashMap<>();

    public static void registerItemGroups() {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load groups file, hence /citemgroup will not work!", e);
        }
        groups.forEach((key, group) -> group.registerItemGroup(key));
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        dispatcher.register(literal("citemgroup")
            .then(literal("modify")
                .then(argument("group", string())
                    .suggests((ctx, builder) -> suggestMatching(groups.keySet(), builder))
                    .then(literal("add")
                        .then(argument("itemstack", itemStack(registryAccess))
                            .then(argument("count", integer(1))
                                .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "itemstack").createStack(getInteger(ctx, "count"), false))))
                            .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "itemstack").createStack(1, false)))))
                    .then(literal("remove")
                        .then(argument("index", integer(0))
                            .executes(ctx -> removeStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index")))))
                    .then(literal("set")
                        .then(argument("index", integer(0))
                            .then(argument("itemstack", itemStack(registryAccess))
                                .then(argument("count", integer(1))
                                    .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index"), getCItemStackArgument(ctx, "itemstack").createStack(getInteger(ctx, "count"), false))))
                                .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index"), getCItemStackArgument(ctx, "itemstack").createStack(1, false))))))
                    .then(literal("icon")
                        .then(argument("icon", itemStack(registryAccess))
                            .executes(ctx -> changeIcon(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "icon").createStack(1, false)))))
                    .then(literal("rename")
                        .then(argument("new", string())
                            .executes(ctx -> renameGroup(ctx.getSource(), getString(ctx, "group"), getString(ctx, "new")))))))
            .then(literal("add")
                .then(argument("group", string())
                    .then(argument("icon", itemStack(registryAccess))
                         .executes(ctx -> addGroup(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "icon").createStack(1, false))))))
            .then(literal("remove")
                .then(argument("group", string())
                    .suggests((ctx, builder) -> suggestMatching(groups.keySet(), builder))
                    .executes(ctx -> removeGroup(ctx.getSource(), getString(ctx, "group"))))));
    }

    private static int addGroup(FabricClientCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (groups.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        final Identifier identifier = Identifier.tryParse("clientcommands:" + name);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(name);
        }

        groups.put(name, new Group(icon, new NbtList()));
        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.addGroup.success", name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeGroup(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        groups.remove(name);

        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.removeGroup.success", name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int addStack(FabricClientCommandSource source, String name, ItemStack itemStack) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.items();
        items.add(itemStack.writeNbt(new NbtCompound()));

        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.addStack.success", itemStack.getItem().getName(), name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeStack(FabricClientCommandSource source, String name, int index) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.items();
        if (index < 0 || index >= items.size()) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.remove(index);

        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.removeStack.success", name, index));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int setStack(FabricClientCommandSource source, String name, int index, ItemStack itemStack) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.items();
        if ((index < 0) || (index >= items.size())) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.set(index, itemStack.writeNbt(new NbtCompound()));

        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.setStack.success", name, index, itemStack.getItem().getName()));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeIcon(FabricClientCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.items();
        ItemStack old = group.icon();

        groups.put(name, new Group(icon, items));

        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.changeIcon.success", name, old.getItem().getName(), icon.getItem().getName()));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int renameGroup(FabricClientCommandSource source, String name, String _new) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Identifier identifier = Identifier.tryParse("clientcommands:" + _new);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(_new);
        }
        Group group = groups.remove(name);
        groups.put(_new, group);

        saveFile();
        source.sendFeedback(Text.translatable("commands.citemgroup.renameGroup.success", name, _new));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            NbtCompound rootTag = new NbtCompound();
            NbtCompound compoundTag = new NbtCompound();
            groups.forEach((key, value) -> {
                NbtCompound group = new NbtCompound();
                group.put("icon", value.icon().writeNbt(new NbtCompound()));
                group.put("items", value.items());
                compoundTag.put(key, group);
            });
            rootTag.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
            rootTag.put("Groups", compoundTag);
            Path newFile = File.createTempFile("groups", ".dat", configPath.toFile()).toPath();
            NbtIo.write(rootTag, newFile);
            Path backupFile = configPath.resolve("groups.dat_old");
            Path currentFile = configPath.resolve("groups.dat");
            Util.backupAndReplace(currentFile, newFile, backupFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        groups.clear();
        NbtCompound rootTag = NbtIo.read(configPath.resolve("groups.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getGameVersion().getSaveVersion().getId();
        final int fileVersion = rootTag.getInt("DataVersion");
        NbtCompound compoundTag = rootTag.getCompound("Groups");
        DataFixer dataFixer = MinecraftClient.getInstance().getDataFixer();
        if (fileVersion >= currentVersion) {
            compoundTag.getKeys().forEach(key -> {
                if (Identifier.tryParse("clientcommands:" + key) == null) {
                    LOGGER.warn("Skipping item group with invalid name {}", key);
                    return;
                }

                NbtCompound group = compoundTag.getCompound(key);
                ItemStack icon = singleItemFromNbt(group.getCompound("icon"));
                NbtList items = group.getList("items", NbtElement.COMPOUND_TYPE);
                groups.put(key, new Group(icon, items));
            });
        } else {
            compoundTag.getKeys().forEach(key -> {
                if (Identifier.tryParse("clientcommands:" + key) == null) {
                    LOGGER.warn("Skipping item group with invalid name {}", key);
                    return;
                }

                NbtCompound group = compoundTag.getCompound(key);
                Dynamic<NbtElement> oldStackDynamic = new Dynamic<>(NbtOps.INSTANCE, group.getCompound("icon"));
                Dynamic<NbtElement> newStackDynamic = dataFixer.update(TypeReferences.ITEM_STACK, oldStackDynamic, fileVersion, currentVersion);
                ItemStack icon = singleItemFromNbt((NbtCompound) newStackDynamic.getValue());

                NbtList updatedListTag = new NbtList();
                group.getList("items", NbtElement.COMPOUND_TYPE).forEach(tag -> {
                    Dynamic<NbtElement> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<NbtElement> newTagDynamic = dataFixer.update(TypeReferences.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                groups.put(key, new Group(icon, updatedListTag));
            });
        }
    }

    private static ItemStack singleItemFromNbt(NbtCompound nbt) {
        ItemStack stack = ItemStack.fromNbt(nbt);
        if (!stack.isEmpty()) {
            stack.setCount(1);
        }
        return stack;
    }

    private record Group(ItemStack icon, NbtList items) {
        void registerItemGroup(String key) {
            Registry.register(Registries.ITEM_GROUP, new Identifier("clientcommands", key), FabricItemGroup.builder()
                    .displayName(Text.literal(key))
                    .icon(() -> icon)
                    .entries((displayContext, entries) -> {
                        Set<ItemStack> existingStacks = ItemStackSet.create();
                        for (int i = 0; i < items.size(); i++) {
                            ItemStack stack = singleItemFromNbt(items.getCompound(i));
                            if (stack.isEmpty()) {
                                continue;
                            }
                            stack.setCount(1);
                            if (existingStacks.add(stack)) {
                                entries.add(stack);
                            }
                        }
                    })
                    .build());
        }
    }
}
