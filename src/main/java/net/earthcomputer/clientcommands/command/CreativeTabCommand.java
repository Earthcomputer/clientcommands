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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CItemArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static net.minecraft.commands.SharedSuggestionProvider.*;

public class CreativeTabCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.ccreativetab.notFound", arg));
    private static final DynamicCommandExceptionType OUT_OF_BOUNDS_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.ccreativetab.outOfBounds", arg));

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ccreativetab.saveFile.failed"));
    private static final DynamicCommandExceptionType ILLEGAL_CHARACTER_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.ccreativetab.addTab.illegalCharacter", arg));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.ccreativetab.addTab.alreadyExists", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final Map<String, Tab> tabs = new HashMap<>();

    public static void registerCreativeTabs() {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load groups file, hence /ccreativetab will not work!", e);
        }

        // FIXME: this is a hack because creative tabs must be registered on startup but item stacks normally can't be
        // parsed until the world is loaded. Use the default registries for now, as most things in item stacks aren't
        // in dynamic registries yet. Fix this once creative tabs can be registered dynamically.
        var holderLookupProvider = new RegistryAccess.ImmutableRegistryAccess(BuiltInRegistries.REGISTRY.stream().toList());
        tabs.forEach((key, tab) -> {
            try {
                tab.registerCreativeTab(holderLookupProvider, key);
            } catch (Throwable e) {
                LOGGER.error("Could not load tab {}", key, e);
            }
        });
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("ccreativetab")
            .then(literal("modify")
                .then(argument("tab", string())
                    .suggests((ctx, builder) -> suggest(tabs.keySet(), builder))
                    .then(literal("add")
                        .then(argument("itemstack", itemStack(context))
                            .then(argument("count", integer(1))
                                .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "tab"), getItemStackArgument(ctx, "itemstack").createItemStack(getInteger(ctx, "count"), false))))
                            .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "tab"), getItemStackArgument(ctx, "itemstack").createItemStack(1, false)))))
                    .then(literal("remove")
                        .then(argument("index", integer(0))
                            .executes(ctx -> removeStack(ctx.getSource(), getString(ctx, "tab"), getInteger(ctx, "index")))))
                    .then(literal("set")
                        .then(argument("index", integer(0))
                            .then(argument("itemstack", itemStack(context))
                                .then(argument("count", integer(1))
                                    .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "tab"), getInteger(ctx, "index"), getItemStackArgument(ctx, "itemstack").createItemStack(getInteger(ctx, "count"), false))))
                                .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "tab"), getInteger(ctx, "index"), getItemStackArgument(ctx, "itemstack").createItemStack(1, false))))))
                    .then(literal("icon")
                        .then(argument("icon", itemStack(context))
                            .executes(ctx -> changeIcon(ctx.getSource(), getString(ctx, "tab"), getItemStackArgument(ctx, "icon").createItemStack(1, false)))))
                    .then(literal("rename")
                        .then(argument("new", string())
                            .executes(ctx -> renameTab(ctx.getSource(), getString(ctx, "tab"), getString(ctx, "new")))))))
            .then(literal("add")
                .then(argument("tab", string())
                    .then(argument("icon", itemStack(context))
                         .executes(ctx -> addTab(ctx.getSource(), getString(ctx, "tab"), getItemStackArgument(ctx, "icon").createItemStack(1, false))))))
            .then(literal("remove")
                .then(argument("tab", string())
                    .suggests((ctx, builder) -> suggest(tabs.keySet(), builder))
                    .executes(ctx -> removeTab(ctx.getSource(), getString(ctx, "tab"))))));
    }

    private static int addTab(FabricClientCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (tabs.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        final ResourceLocation identifier = ResourceLocation.tryParse("clientcommands:" + name);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(name);
        }

        icon.setCount(1);

        tabs.put(name, new Tab((CompoundTag) icon.save(source.registryAccess()), new ListTag()));
        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.addTab.success", name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeTab(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (!tabs.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        tabs.remove(name);

        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.removeTab.success", name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int addStack(FabricClientCommandSource source, String name, ItemStack itemStack) throws CommandSyntaxException {
        if (!tabs.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Tab tab = tabs.get(name);
        ListTag items = tab.items();
        items.add(itemStack.save(source.registryAccess()));

        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.addStack.success", itemStack.getItem().getDescription(), name));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int removeStack(FabricClientCommandSource source, String name, int index) throws CommandSyntaxException {
        if (!tabs.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Tab tab = tabs.get(name);
        ListTag items = tab.items();
        if (index < 0 || index >= items.size()) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.remove(index);

        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.removeStack.success", name, index));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int setStack(FabricClientCommandSource source, String name, int index, ItemStack itemStack) throws CommandSyntaxException {
        if (!tabs.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Tab tab = tabs.get(name);
        ListTag items = tab.items();
        if ((index < 0) || (index >= items.size())) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.set(index, itemStack.save(source.registryAccess()));

        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.setStack.success", name, index, itemStack.getItem().getDescription()));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int changeIcon(FabricClientCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (!tabs.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        icon.setCount(1);

        Tab tab = tabs.get(name);
        ListTag items = tab.items();
        ItemStack old = ItemStack.parseOptional(source.registryAccess(), tab.icon());

        tabs.put(name, new Tab((CompoundTag) icon.save(source.registryAccess()), items));

        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.changeIcon.success", name, old.getItem().getDescription(), icon.getItem().getDescription()));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static int renameTab(FabricClientCommandSource source, String name, String _new) throws CommandSyntaxException {
        if (!tabs.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        ResourceLocation identifier = ResourceLocation.tryParse("clientcommands:" + _new);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(_new);
        }
        Tab tab = tabs.remove(name);
        tabs.put(_new, tab);

        saveFile();
        source.sendFeedback(Component.translatable("commands.ccreativetab.renameTab.success", name, _new));
        ClientCommandHelper.sendRequiresRestart();
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            CompoundTag compoundTag = new CompoundTag();
            tabs.forEach((key, value) -> {
                CompoundTag tab = new CompoundTag();
                tab.put("icon", value.icon());
                tab.put("items", value.items());
                compoundTag.put(key, tab);
            });
            rootTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            rootTag.put("CreativeTabs", compoundTag);
            Path newFile = File.createTempFile("creative_tabs", ".dat", configPath.toFile()).toPath();
            NbtIo.write(rootTag, newFile);
            Path backupFile = configPath.resolve("creative_tabs.dat_old");
            Path currentFile = configPath.resolve("creative_tabs.dat");
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        tabs.clear();
        CompoundTag rootTag = NbtIo.read(configPath.resolve("creative_tabs.dat"));
        if (rootTag == null) {
            try {
                Files.move(configPath.resolve("groups.dat"), configPath.resolve("creative_tabs.dat"));
            } catch (NoSuchFileException e) {
                return;
            }
            rootTag = NbtIo.read(configPath.resolve("creative_tabs.dat"));
            if (rootTag == null) {
                return;
            }
        }
        final int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        final int fileVersion = rootTag.getInt("DataVersion");
        CompoundTag compoundTag = rootTag.getCompound("CreativeTabs");
        if (compoundTag.isEmpty()) {
            compoundTag = rootTag.getCompound("Groups");
        }
        DataFixer dataFixer = Minecraft.getInstance().getFixerUpper();
        if (fileVersion >= currentVersion) {
            for (String key : compoundTag.getAllKeys()) {
                if (ResourceLocation.tryParse("clientcommands:" + key) == null) {
                    LOGGER.warn("Skipping creative tab with invalid name {}", key);
                    return;
                }

                CompoundTag tab = compoundTag.getCompound(key);
                CompoundTag icon = tab.getCompound("icon");
                ListTag items = tab.getList("items", Tag.TAG_COMPOUND);
                tabs.put(key, new Tab(icon, items));
            }
        } else {
            for (String key : compoundTag.getAllKeys()) {
                if (ResourceLocation.tryParse("clientcommands:" + key) == null) {
                    LOGGER.warn("Skipping creative tab with invalid name {}", key);
                    return;
                }

                CompoundTag tab = compoundTag.getCompound(key);
                Dynamic<Tag> oldStackDynamic = new Dynamic<>(NbtOps.INSTANCE, tab.getCompound("icon"));
                Dynamic<Tag> newStackDynamic = dataFixer.update(References.ITEM_STACK, oldStackDynamic, fileVersion, currentVersion);
                CompoundTag icon = (CompoundTag) newStackDynamic.getValue();

                ListTag updatedListTag = new ListTag();
                tab.getList("items", Tag.TAG_COMPOUND).forEach(tag -> {
                    Dynamic<Tag> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<Tag> newTagDynamic = dataFixer.update(References.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                tabs.put(key, new Tab(icon, updatedListTag));
            }
        }
    }

    private static ItemStack singleItemFromNbt(HolderLookup.Provider holderLookupProvider, CompoundTag nbt) {
        ItemStack stack = ItemStack.parseOptional(holderLookupProvider, nbt);
        if (!stack.isEmpty()) {
            stack.setCount(1);
        }
        return stack;
    }

    private record Tab(CompoundTag icon, ListTag items) {
        void registerCreativeTab(HolderLookup.Provider holderLookupProvider, String key) {
            Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("clientcommands", key), FabricItemGroup.builder()
                    .title(Component.literal(key))
                    .icon(() -> singleItemFromNbt(holderLookupProvider, icon))
                    .displayItems((displayContext, entries) -> {
                        Set<ItemStack> existingStacks = ItemStackLinkedSet.createTypeAndComponentsSet();
                        for (int i = 0; i < items.size(); i++) {
                            ItemStack stack = singleItemFromNbt(holderLookupProvider, items.getCompound(i));
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
