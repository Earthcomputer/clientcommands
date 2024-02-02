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
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public class KitCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ckit.saveFile.failed"));

    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.ckit.create.alreadyExists", arg));

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.ckit.load.notCreative"));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.ckit.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final Map<String, ListTag> kits = new HashMap<>();

    static {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load kits file, hence /ckit will not work!", e);
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("ckit")
                .then(literal("create")
                        .then(argument("name", string())
                                .executes(ctx -> create(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("delete")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggest(kits.keySet(), builder))
                                .executes(ctx -> delete(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("edit")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggest(kits.keySet(), builder))
                                .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("load")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggest(kits.keySet(), builder))
                                .then(literal("--override")
                                        .executes(ctx -> load(ctx.getSource(), getString(ctx, "name"), true)))
                                .executes(ctx -> load(ctx.getSource(), getString(ctx, "name"), false))))
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(literal("preview")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggest(kits.keySet(), builder))
                                .executes(ctx -> preview(ctx.getSource(), getString(ctx, "name"))))));
    }

    private static int create(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (kits.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }
        kits.put(name, source.getPlayer().getInventory().save(new ListTag()));
        saveFile();
        source.sendFeedback(Component.translatable("commands.ckit.create.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (kits.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        saveFile();
        source.sendFeedback(Component.translatable("commands.ckit.delete.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int edit(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (!kits.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        kits.put(name, source.getPlayer().getInventory().save(new ListTag()));
        saveFile();
        source.sendFeedback(Component.translatable("commands.ckit.edit.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int load(FabricClientCommandSource source, String name, boolean override) throws CommandSyntaxException {
        if (!source.getPlayer().getAbilities().instabuild) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        ListTag kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Inventory tempInv = new Inventory(source.getPlayer());
        tempInv.load(kit);
        List<Slot> slots = source.getPlayer().inventoryMenu.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).container == source.getPlayer().getInventory()) {
                ItemStack itemStack = tempInv.getItem(slots.get(i).getContainerSlot());
                if (!itemStack.isEmpty() || override) {
                    source.getClient().gameMode.handleCreativeModeItemAdd(itemStack, i);
                }
            }
        }

        source.getPlayer().inventoryMenu.broadcastChanges();
        source.sendFeedback(Component.translatable("commands.ckit.load.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        if (kits.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.ckit.list.empty"));
        } else {
            String list = String.join(", ", kits.keySet());
            source.sendFeedback(Component.translatable("commands.ckit.list", list));
        }
        return kits.size();
    }

    private static int preview(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        ListTag kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        Inventory tempInv = new Inventory(source.getPlayer());
        tempInv.load(kit);
        /*
            After executing a command, the current screen will be closed (the chat hud).
            And if you open a new screen in a command, that new screen will be closed
            instantly along with the chat hud. Slightly delaying the opening of the
            screen fixes this issue.
         */
        source.getClient().tell(() -> source.getClient().setScreen(new PreviewScreen(new InventoryMenu(tempInv, true, source.getPlayer()), tempInv, name)));
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            CompoundTag compoundTag = new CompoundTag();
            kits.forEach(compoundTag::put);
            rootTag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            rootTag.put("Kits", compoundTag);
            Path newFile = File.createTempFile("kits", ".dat", configPath.toFile()).toPath();
            NbtIo.write(rootTag, newFile);
            Path backupFile = configPath.resolve("kits.dat_old");
            Path currentFile = configPath.resolve("kits.dat");;
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        kits.clear();
        CompoundTag rootTag = NbtIo.read(configPath.resolve("kits.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        final int fileVersion = rootTag.getInt("DataVersion");
        CompoundTag compoundTag = rootTag.getCompound("Kits");
        DataFixer dataFixer = Minecraft.getInstance().getFixerUpper();
        if (fileVersion >= currentVersion) {
            compoundTag.getAllKeys().forEach(key -> kits.put(key, compoundTag.getList(key, Tag.TAG_COMPOUND)));
        } else {
            compoundTag.getAllKeys().forEach(key -> {
                ListTag updatedListTag = new ListTag();
                compoundTag.getList(key, Tag.TAG_COMPOUND).forEach(tag -> {
                    Dynamic<Tag> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<Tag> newTagDynamic = dataFixer.update(References.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                kits.put(key, updatedListTag);
            });
        }
    }
}

class PreviewScreen extends EffectRenderingInventoryScreen<InventoryMenu> {

    public PreviewScreen(InventoryMenu playerScreenHandler, Inventory inventory, String name) {
        super(playerScreenHandler, inventory, Component.literal(name).withStyle(style -> style.withColor(ChatFormatting.RED)));
        this.titleLabelX = 80;
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        context.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(drawContext, mouseX, mouseY, delta);
        super.render(drawContext, mouseX, mouseY, delta);

        this.renderTooltip(drawContext, mouseX, mouseY);
    }

    @Override
    protected void renderEffects(GuiGraphics context, int mouseX, int mouseY) {
        // nop
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        context.blit(INVENTORY_LOCATION, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
