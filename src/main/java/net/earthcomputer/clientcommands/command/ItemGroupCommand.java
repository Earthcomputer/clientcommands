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
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
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
import static net.minecraft.commands.SharedSuggestionProvider.*;

public class ItemGroupCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.citemgroup.notFound", arg));
    private static final DynamicCommandExceptionType OUT_OF_BOUNDS_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.citemgroup.outOfBounds", arg));

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.citemgroup.saveFile.failed"));
    private static final DynamicCommandExceptionType ILLEGAL_CHARACTER_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.citemgroup.addGroup.illegalCharacter", arg));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.citemgroup.addGroup.alreadyExists", arg));

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

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(literal("citemgroup")
            .then(literal("modify")
                .then(argument("group", string())
                    .suggests((ctx, builder) -> suggest(groups.keySet(), builder))
                    .then(literal("add")
                        .then(argument("itemstack", itemStack(registryAccess))
                            .then(argument("count", integer(1))
                                .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "itemstack").createItemStack(getInteger(ctx, "count"), false))))
                            .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "itemstack").createItemStack(1, false)))))
                    .then(literal("remove")
                        .then(argument("index", integer(0))
                            .executes(ctx -> removeStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index")))))
                    .then(literal("set")
                        .then(argument("index", integer(0))
                            .then(argument("itemstack", itemStack(registryAccess))
                                .then(argument("count", integer(1))
                                    .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index"), getCItemStackArgument(ctx, "itemstack").createItemStack(getInteger(ctx, "count"), false))))
                                .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index"), getCItemStackArgument(ctx, "itemstack").createItemStack(1, false))))))
                    .then(literal("icon")
                        .then(argument("icon", itemStack(registryAccess))
                            .executes(ctx -> changeIcon(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "icon").createItemStack(1, false)))))
                    .then(literal("rename")
                        .then(argument("new", string())
                            .executes(ctx -> renameGroup(ctx.getSource(), getString(ctx, "group"), getString(ctx, "new")))))))
            .then(literal("add")
                .then(argument("group", string())
                    .then(argument("icon", itemStack(registryAccess))
                         .executes(ctx -> addGroup(ctx.getSource(), getString(ctx, "group"), getCItemStackArgument(ctx, "icon").createItemStack(1, false))))))
            .then(literal("remove")
                .then(argument("group", string())
                    .suggests((ctx, builder) -> suggest(groups.keySet(), builder))
                    .executes(ctx -> removeGroup(ctx.getSource(), getString(ctx, "group"))))));
    }

    private static int addGroup(FabricClientCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (groups.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        final ResourceLocation identifier = ResourceLocation.tryParse("clientcommands:" + name);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(name);
        }

        groups.put(name, new Group(icon, new ListTag()));
        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.addGroup.success", name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeGroup(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        groups.remove(name);

        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.removeGroup.success", name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int addStack(FabricClientCommandSource source, String name, ItemStack itemStack) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        ListTag items = group.items();
        items.add(itemStack.save(new CompoundTag()));

        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.addStack.success", itemStack.getItem().getDescription(), name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeStack(FabricClientCommandSource source, String name, int index) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        ListTag items = group.items();
        if (index < 0 || index >= items.size()) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.remove(index);

        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.removeStack.success", name, index));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int setStack(FabricClientCommandSource source, String name, int index, ItemStack itemStack) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        ListTag items = group.items();
        if ((index < 0) || (index >= items.size())) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.set(index, itemStack.save(new CompoundTag()));

        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.setStack.success", name, index, itemStack.getItem().getDescription()));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeIcon(FabricClientCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        ListTag items = group.items();
        ItemStack old = group.icon();

        groups.put(name, new Group(icon, items));

        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.changeIcon.success", name, old.getItem().getDescription(), icon.getItem().getDescription()));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int renameGroup(FabricClientCommandSource source, String name, String _new) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        ResourceLocation identifier = ResourceLocation.tryParse("clientcommands:" + _new);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(_new);
        }
        Group group = groups.remove(name);
        groups.put(_new, group);

        saveFile();
        source.sendFeedback(Component.translatable("commands.citemgroup.renameGroup.success", name, _new));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            CompoundTag compoundTag = new CompoundTag();
            groups.forEach((key, value) -> {
                CompoundTag group = new CompoundTag();
                group.put("icon", value.icon().save(new CompoundTag()));
                group.put("items", value.items());
                compoundTag.put(key, group);
            });
            rootTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            rootTag.put("Groups", compoundTag);
            Path newFile = File.createTempFile("groups", ".dat", configPath.toFile()).toPath();
            NbtIo.write(rootTag, newFile);
            Path backupFile = configPath.resolve("groups.dat_old");
            Path currentFile = configPath.resolve("groups.dat");
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        groups.clear();
        CompoundTag rootTag = NbtIo.read(configPath.resolve("groups.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        final int fileVersion = rootTag.getInt("DataVersion");
        CompoundTag compoundTag = rootTag.getCompound("Groups");
        DataFixer dataFixer = Minecraft.getInstance().getFixerUpper();
        if (fileVersion >= currentVersion) {
            compoundTag.getAllKeys().forEach(key -> {
                if (ResourceLocation.tryParse("clientcommands:" + key) == null) {
                    LOGGER.warn("Skipping item group with invalid name {}", key);
                    return;
                }

                CompoundTag group = compoundTag.getCompound(key);
                ItemStack icon = singleItemFromNbt(group.getCompound("icon"));
                ListTag items = group.getList("items", Tag.TAG_COMPOUND);
                groups.put(key, new Group(icon, items));
            });
        } else {
            compoundTag.getAllKeys().forEach(key -> {
                if (ResourceLocation.tryParse("clientcommands:" + key) == null) {
                    LOGGER.warn("Skipping item group with invalid name {}", key);
                    return;
                }

                CompoundTag group = compoundTag.getCompound(key);
                Dynamic<Tag> oldStackDynamic = new Dynamic<>(NbtOps.INSTANCE, group.getCompound("icon"));
                Dynamic<Tag> newStackDynamic = dataFixer.update(References.ITEM_STACK, oldStackDynamic, fileVersion, currentVersion);
                ItemStack icon = singleItemFromNbt((CompoundTag) newStackDynamic.getValue());

                ListTag updatedListTag = new ListTag();
                group.getList("items", Tag.TAG_COMPOUND).forEach(tag -> {
                    Dynamic<Tag> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<Tag> newTagDynamic = dataFixer.update(References.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                groups.put(key, new Group(icon, updatedListTag));
            });
        }
    }

    private static ItemStack singleItemFromNbt(CompoundTag nbt) {
        ItemStack stack = ItemStack.of(nbt);
        if (!stack.isEmpty()) {
            stack.setCount(1);
        }
        return stack;
    }

    private record Group(ItemStack icon, ListTag items) {
        void registerItemGroup(String key) {
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation("clientcommands", key), FabricItemGroup.builder()
                    .title(Component.literal(key))
                    .icon(() -> icon)
                    .displayItems((displayContext, entries) -> {
                        Set<ItemStack> existingStacks = ItemStackLinkedSet.createTypeAndTagSet();
                        for (int i = 0; i < items.size(); i++) {
                            ItemStack stack = singleItemFromNbt(items.getCompound(i));
                            if (stack.isEmpty()) {
                                continue;
                            }
                            stack.setCount(1);
                            if (existingStacks.add(stack)) {
                                entries.accept(stack);
                            }
                        }
                    })
                    .build());
        }
    }
}
