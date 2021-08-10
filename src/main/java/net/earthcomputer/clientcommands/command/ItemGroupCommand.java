package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Dynamic;
import net.earthcomputer.clientcommands.interfaces.IItemGroup;
import net.earthcomputer.clientcommands.mixin.CreativeInventoryScreenAccessor;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.*;
import static net.minecraft.command.argument.ItemStackArgumentType.*;
import static net.minecraft.server.command.CommandManager.*;

public class ItemGroupCommand {

    private static final Logger LOGGER = LogManager.getLogger("clientcommands");

    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.citemgroup.notFound", arg));
    private static final DynamicCommandExceptionType OUT_OF_BOUNDS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.citemgroup.outOfBounds", arg));

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.citemgroup.saveFile.failed"));
    private static final DynamicCommandExceptionType ILLEGAL_CHARACTER_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.citemgroup.addGroup.illegalCharacter", arg));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.citemgroup.addGroup.alreadyExists", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Map<String, Group> groups = new HashMap<>();

    static {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load groups file, hence /citemgroup will not work!", e);
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("citemgroup");

        dispatcher.register(literal("citemgroup")
            .then(literal("modify")
                .then(argument("group", string())
                    .suggests((ctx, builder) -> suggestMatching(groups.keySet(), builder))
                    .then(literal("add")
                        .then(argument("itemstack", itemStack())
                            .then(argument("count", integer(1))
                                .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "group"), getItemStackArgument(ctx, "itemstack").createStack(getInteger(ctx, "count"), false))))
                            .executes(ctx -> addStack(ctx.getSource(), getString(ctx, "group"), getItemStackArgument(ctx, "itemstack").createStack(1, false)))))
                    .then(literal("remove")
                        .then(argument("index", integer(0))
                            .executes(ctx -> removeStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index")))))
                    .then(literal("set")
                        .then(argument("index", integer(0))
                            .then(argument("itemstack", itemStack())
                                .then(argument("count", integer(1))
                                    .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index"), getItemStackArgument(ctx, "itemstack").createStack(getInteger(ctx, "count"), false))))
                                .executes(ctx -> setStack(ctx.getSource(), getString(ctx, "group"), getInteger(ctx, "index"), getItemStackArgument(ctx, "itemstack").createStack(1, false))))))
                    .then(literal("icon")
                        .then(argument("icon", itemStack())
                            .executes(ctx -> changeIcon(ctx.getSource(), getString(ctx, "group"), getItemStackArgument(ctx, "icon").createStack(1, false)))))
                    .then(literal("rename")
                        .then(argument("new", string())
                            .executes(ctx -> renameGroup(ctx.getSource(), getString(ctx, "group"), getString(ctx, "new")))))))
            .then(literal("add")
                .then(argument("group", string())
                    .then(argument("icon", itemStack())
                         .executes(ctx -> addGroup(ctx.getSource(), getString(ctx, "group"), getItemStackArgument(ctx, "icon").createStack(1, false))))))
            .then(literal("remove")
                .then(argument("group", string())
                    .suggests((ctx, builder) -> suggestMatching(groups.keySet(), builder))
                    .executes(ctx -> removeGroup(ctx.getSource(), getString(ctx, "group"))))));
    }

    private static int addGroup(ServerCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (groups.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }

        final Identifier identifier = Identifier.tryParse("clientcommands:" + name);
        if (identifier == null) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(name);
        }
        ItemGroup itemGroup = FabricItemGroupBuilder.create(identifier)
                .icon(() -> icon)
                .build();

        groups.put(name, new Group(itemGroup, icon, new NbtList()));
        saveFile();
        sendFeedback("commands.citemgroup.addGroup.success", name);
        return 0;
    }

    private static final Field FABRIC_CURRENT_PAGE_FIELD;
    static {
        try {
            //noinspection JavaReflectionMemberAccess
            FABRIC_CURRENT_PAGE_FIELD = CreativeInventoryScreen.class.getDeclaredField("fabric_currentPage");
            FABRIC_CURRENT_PAGE_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int removeGroup(ServerCommandSource source, String name) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        groups.remove(name);

        reloadGroups();
        CreativeInventoryScreenAccessor.setSelectedTab(0);
        try {
            FABRIC_CURRENT_PAGE_FIELD.set(null, 0);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        saveFile();
        sendFeedback("commands.citemgroup.removeGroup.success", name);
        return 0;
    }

    private static int addStack(ServerCommandSource source, String name, ItemStack itemStack) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.getItems();
        items.add(itemStack.writeNbt(new NbtCompound()));

        reloadGroups();
        saveFile();
        sendFeedback("commands.citemgroup.addStack.success", itemStack.getItem().getName(), name);
        return 0;
    }

    private static int removeStack(ServerCommandSource source, String name, int index) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.getItems();
        if (index < 0 || index >= items.size()) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.remove(index);

        reloadGroups();
        saveFile();
        sendFeedback("commands.citemgroup.removeStack.success", name, index);
        return 0;
    }

    private static int setStack(ServerCommandSource source, String name, int index, ItemStack itemStack) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        NbtList items = group.getItems();
        if ((index < 0) || (index >= items.size())) {
            throw OUT_OF_BOUNDS_EXCEPTION.create(index);
        }
        items.set(index, itemStack.writeNbt(new NbtCompound()));

        reloadGroups();
        saveFile();
        sendFeedback("commands.citemgroup.setStack.success", name, index, itemStack.getItem().getName());
        return 0;
    }

    private static int changeIcon(ServerCommandSource source, String name, ItemStack icon) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.get(name);
        ItemGroup itemGroup = group.getItemGroup();
        NbtList items = group.getItems();
        ItemStack old = itemGroup.getIcon();
        itemGroup = FabricItemGroupBuilder.create(
                new Identifier("clientcommands", name))
                .icon(() -> icon)
                .build();

        groups.put(name, new Group(itemGroup, icon, items));

        reloadGroups();
        saveFile();
        sendFeedback("commands.citemgroup.changeIcon.success", name, old.getItem().getName(), icon.getItem().getName());
        return 0;
    }

    private static int renameGroup(ServerCommandSource source, String name, String _new) throws CommandSyntaxException {
        if (!groups.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Group group = groups.remove(name);
        ItemGroup itemGroup = group.getItemGroup();
        NbtList items = group.getItems();
        ItemStack icon = itemGroup.getIcon();
        try {
            itemGroup = FabricItemGroupBuilder.create(
                    new Identifier("clientcommands", _new))
                    .icon(() -> icon)
                    .build();

            groups.put(_new, new Group(itemGroup, icon, items));
        } catch (InvalidIdentifierException e) {
            throw ILLEGAL_CHARACTER_EXCEPTION.create(_new);
        }

        reloadGroups();
        saveFile();
        sendFeedback("commands.citemgroup.renameGroup.success", name, _new);
        return 0;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            NbtCompound rootTag = new NbtCompound();
            NbtCompound compoundTag = new NbtCompound();
            groups.forEach((key, value) -> {
                NbtCompound group = new NbtCompound();
                group.put("icon", value.getIcon().writeNbt(new NbtCompound()));
                group.put("items", value.getItems());
                compoundTag.put(key, group);
            });
            rootTag.putInt("DataVersion", SharedConstants.getGameVersion().getWorldVersion());
            rootTag.put("Groups", compoundTag);
            File newFile = File.createTempFile("groups", ".dat", configPath.toFile());
            NbtIo.write(rootTag, newFile);
            File backupFile = new File(configPath.toFile(), "groups.dat_old");
            File currentFile = new File(configPath.toFile(), "groups.dat");
            Util.backupAndReplace(currentFile, newFile, backupFile);
        } catch (IOException e) {
            e.printStackTrace();
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        groups.clear();
        NbtCompound rootTag = NbtIo.read(new File(configPath.toFile(), "groups.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getGameVersion().getWorldVersion();
        final int fileVersion = rootTag.getInt("DataVersion");
        NbtCompound compoundTag = rootTag.getCompound("Groups");
        if (fileVersion >= currentVersion) {
            compoundTag.getKeys().forEach(key -> {
                NbtCompound group = compoundTag.getCompound(key);
                ItemStack icon = ItemStack.fromNbt(group.getCompound("icon"));
                NbtList items = group.getList("items", NbtElement.COMPOUND_TYPE);
                ItemGroup itemGroup = FabricItemGroupBuilder.create(
                        new Identifier("clientcommands", key))
                        .icon(() -> icon)
                        .appendItems(stacks -> {
                            for (int i = 0; i < items.size(); i++) {
                                stacks.add(ItemStack.fromNbt(items.getCompound(i)));
                            }
                        })
                        .build();
                groups.put(key, new Group(itemGroup, icon, items));
            });
        } else {
            compoundTag.getKeys().forEach(key -> {
                NbtCompound group = compoundTag.getCompound(key);
                Dynamic<NbtElement> oldStackDynamic = new Dynamic<>(NbtOps.INSTANCE, group.getCompound("icon"));
                Dynamic<NbtElement> newStackDynamic = client.getDataFixer().update(TypeReferences.ITEM_STACK, oldStackDynamic, fileVersion, currentVersion);
                ItemStack icon = ItemStack.fromNbt((NbtCompound) newStackDynamic.getValue());

                NbtList updatedListTag = new NbtList();
                group.getList("items", NbtElement.COMPOUND_TYPE).forEach(tag -> {
                    Dynamic<NbtElement> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<NbtElement> newTagDynamic = client.getDataFixer().update(TypeReferences.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                ItemGroup itemGroup = FabricItemGroupBuilder.create(
                        new Identifier("clientcommands", key))
                        .icon(() -> icon)
                        .appendItems(stacks -> {
                            for (int i = 0; i < updatedListTag.size(); i++) {
                                stacks.add(ItemStack.fromNbt(updatedListTag.getCompound(i)));
                            }
                        })
                        .build();
                groups.put(key, new Group(itemGroup, icon, updatedListTag));
            });
        }
    }

    private static void reloadGroups() {
        ((IItemGroup) ItemGroup.BUILDING_BLOCKS).shrink();

        for (String key : groups.keySet()) {
            Group group = groups.get(key);
            group.setItemGroup(FabricItemGroupBuilder.create(
                    new Identifier("clientcommands", key))
                    .icon(group::getIcon)
                    .appendItems(stacks -> {
                        for (int i = 0; i < group.getItems().size(); i++) {
                            stacks.add(ItemStack.fromNbt(group.getItems().getCompound(i)));
                        }
                    })
                    .build());
        }
    }
}

class Group {

    private ItemGroup itemGroup;
    private final ItemStack icon;
    private final NbtList items;

    public Group(ItemGroup itemGroup, ItemStack icon, NbtList items) {
        this.itemGroup = itemGroup;
        this.icon = icon;
        this.items = items;
    }

    public ItemGroup getItemGroup() {
        return this.itemGroup;
    }

    public ItemStack getIcon() {
        return this.icon;
    }

    public NbtList getItems() {
        return this.items;
    }

    public void setItemGroup(ItemGroup itemGroup) {
        this.itemGroup = itemGroup;
    }
}
