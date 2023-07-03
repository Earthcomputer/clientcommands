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
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.*;

public class KitCommand {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ckit.saveFile.failed"));

    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.ckit.create.alreadyExists", arg));

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.ckit.load.notCreative"));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Text.translatable("commands.ckit.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final Map<String, NbtList> kits = new HashMap<>();

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
                                .suggests((ctx, builder) -> suggestMatching(kits.keySet(), builder))
                                .executes(ctx -> delete(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("edit")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggestMatching(kits.keySet(), builder))
                                .executes(ctx -> edit(ctx.getSource(), getString(ctx, "name")))))
                .then(literal("load")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggestMatching(kits.keySet(), builder))
                                .then(literal("--override")
                                        .executes(ctx -> load(ctx.getSource(), getString(ctx, "name"), true)))
                                .executes(ctx -> load(ctx.getSource(), getString(ctx, "name"), false))))
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource())))
                .then(literal("preview")
                        .then(argument("name", string())
                                .suggests((ctx, builder) -> suggestMatching(kits.keySet(), builder))
                                .executes(ctx -> preview(ctx.getSource(), getString(ctx, "name"))))));
    }

    private static int create(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (kits.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }
        kits.put(name, source.getPlayer().getInventory().writeNbt(new NbtList()));
        saveFile();
        source.sendFeedback(Text.translatable("commands.ckit.create.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int delete(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (kits.remove(name) == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        saveFile();
        source.sendFeedback(Text.translatable("commands.ckit.delete.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int edit(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        if (!kits.containsKey(name)) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }
        kits.put(name, source.getPlayer().getInventory().writeNbt(new NbtList()));
        saveFile();
        source.sendFeedback(Text.translatable("commands.ckit.edit.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int load(FabricClientCommandSource source, String name, boolean override) throws CommandSyntaxException {
        if (!source.getPlayer().getAbilities().creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        NbtList kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        PlayerInventory tempInv = new PlayerInventory(source.getPlayer());
        tempInv.readNbt(kit);
        List<Slot> slots = source.getPlayer().playerScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory == source.getPlayer().getInventory()) {
                ItemStack itemStack = tempInv.getStack(slots.get(i).getIndex());
                if (!itemStack.isEmpty() || override) {
                    source.getClient().interactionManager.clickCreativeStack(itemStack, i);
                }
            }
        }

        source.getPlayer().playerScreenHandler.sendContentUpdates();
        source.sendFeedback(Text.translatable("commands.ckit.load.success", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(FabricClientCommandSource source) {
        if (kits.isEmpty()) {
            source.sendFeedback(Text.translatable("commands.ckit.list.empty"));
        } else {
            String list = String.join(", ", kits.keySet());
            source.sendFeedback(Text.translatable("commands.ckit.list", list));
        }
        return kits.size();
    }

    private static int preview(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        NbtList kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        PlayerInventory tempInv = new PlayerInventory(source.getPlayer());
        tempInv.readNbt(kit);
        /*
            After executing a command, the current screen will be closed (the chat hud).
            And if you open a new screen in a command, that new screen will be closed
            instantly along with the chat hud. Slightly delaying the opening of the
            screen fixes this issue.
         */
        source.getClient().send(() -> source.getClient().setScreen(new PreviewScreen(new PlayerScreenHandler(tempInv, true, source.getPlayer()), tempInv, name)));
        return Command.SINGLE_SUCCESS;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            NbtCompound rootTag = new NbtCompound();
            NbtCompound compoundTag = new NbtCompound();
            kits.forEach(compoundTag::put);
            rootTag.putInt("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
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
        NbtCompound rootTag = NbtIo.read(new File(configPath.toFile(), "kits.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getGameVersion().getSaveVersion().getId();
        final int fileVersion = rootTag.getInt("DataVersion");
        NbtCompound compoundTag = rootTag.getCompound("Kits");
        DataFixer dataFixer = MinecraftClient.getInstance().getDataFixer();
        if (fileVersion >= currentVersion) {
            compoundTag.getKeys().forEach(key -> kits.put(key, compoundTag.getList(key, NbtElement.COMPOUND_TYPE)));
        } else {
            compoundTag.getKeys().forEach(key -> {
                NbtList updatedListTag = new NbtList();
                compoundTag.getList(key, NbtElement.COMPOUND_TYPE).forEach(tag -> {
                    Dynamic<NbtElement> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<NbtElement> newTagDynamic = dataFixer.update(TypeReferences.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                kits.put(key, updatedListTag);
            });
        }
    }
}

class PreviewScreen extends AbstractInventoryScreen<PlayerScreenHandler> {

    public PreviewScreen(PlayerScreenHandler playerScreenHandler, PlayerInventory inventory, String name) {
        super(playerScreenHandler, inventory, Text.literal(name).styled(style -> style.withColor(Formatting.RED)));
        this.titleX = 80;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0x404040, false);
    }

    @Override
    public void render(DrawContext DrawContext, int mouseX, int mouseY, float delta) {
        this.renderBackground(DrawContext);
        super.render(DrawContext, mouseX, mouseY, delta);

        this.drawMouseoverTooltip(DrawContext, mouseX, mouseY);
    }

    @Override
    protected void drawStatusEffects(DrawContext context, int mouseX, int mouseY) {
        // nop
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(BACKGROUND_TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
