package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.serialization.Dynamic;
import net.fabricmc.fabric.api.util.NbtType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.AbstractInventoryScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.command.CommandSource;
import net.minecraft.datafixer.TypeReferences;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
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

    private static final Logger LOGGER = LogManager.getLogger("clientcommands");

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.saveFile.failed"));

    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.create.alreadyExists", arg));

    private static final SimpleCommandExceptionType NOT_CREATIVE_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.ckit.load.notCreative"));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> new TranslatableText("commands.ckit.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Map<String, NbtList> kits = new HashMap<>();

    static {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.info("Could not load kits file, hence /ckit will not work!");
        }
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("ckit");

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
                        .executes(ctx -> list(ctx.getSource())))
                .then(literal("preview")
                        .then(argument("name", StringArgumentType.string())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(kits.keySet(), builder))
                                .executes(ctx -> preview(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))));
    }

    private static int create(ServerCommandSource source, String name) throws CommandSyntaxException {
        if (kits.containsKey(name)) {
            throw ALREADY_EXISTS_EXCEPTION.create(name);
        }
        kits.put(name, client.player.getInventory().writeNbt(new NbtList()));
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
        kits.put(name, client.player.getInventory().writeNbt(new NbtList()));
        saveFile();
        sendFeedback("commands.ckit.edit.success", name);
        return 0;
    }

    private static int load(ServerCommandSource source, String name, boolean override) throws CommandSyntaxException {
        if (!client.player.getAbilities().creativeMode) {
            throw NOT_CREATIVE_EXCEPTION.create();
        }

        NbtList kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        PlayerInventory tempInv = new PlayerInventory(client.player);
        tempInv.readNbt(kit);
        List<Slot> slots = client.player.playerScreenHandler.slots;
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i).inventory == client.player.getInventory()) {
                ItemStack itemStack = tempInv.getStack(slots.get(i).getIndex());
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
        if (kits.isEmpty()) {
            sendFeedback("commands.ckit.list.empty");
        } else {
            String list = String.join(", ", kits.keySet());
            sendFeedback("commands.ckit.list", list);
        }
        return kits.size();
    }

    private static int preview(ServerCommandSource source, String name) throws CommandSyntaxException {
        NbtList kit = kits.get(name);
        if (kit == null) {
            throw NOT_FOUND_EXCEPTION.create(name);
        }

        PlayerInventory tempInv = new PlayerInventory(client.player);
        tempInv.readNbt(kit);
        /*
            After executing a command, the current screen will be closed (the chat hud).
            And if you open a new screen in a command, that new screen will be closed
            instantly along with the chat hud. Slightly delaying the opening of the
            screen fixes this issue.
         */
        client.send(() -> client.openScreen(new PreviewScreen(new PlayerScreenHandler(tempInv, true, client.player), tempInv, name)));
        return 0;
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            NbtCompound rootTag = new NbtCompound();
            NbtCompound compoundTag = new NbtCompound();
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
        NbtCompound rootTag = NbtIo.read(new File(configPath.toFile(), "kits.dat"));
        if (rootTag == null) {
            return;
        }
        final int currentVersion = SharedConstants.getGameVersion().getWorldVersion();
        final int fileVersion = rootTag.getInt("DataVersion");
        NbtCompound compoundTag = rootTag.getCompound("Kits");
        if (fileVersion >= currentVersion) {
            compoundTag.getKeys().forEach(key -> kits.put(key, compoundTag.getList(key, NbtType.COMPOUND)));
        } else {
            compoundTag.getKeys().forEach(key -> {
                NbtList updatedListTag = new NbtList();
                compoundTag.getList(key, NbtType.COMPOUND).forEach(tag -> {
                    Dynamic<NbtElement> oldTagDynamic = new Dynamic<>(NbtOps.INSTANCE, tag);
                    Dynamic<NbtElement> newTagDynamic = client.getDataFixer().update(TypeReferences.ITEM_STACK, oldTagDynamic, fileVersion, currentVersion);
                    updatedListTag.add(newTagDynamic.getValue());
                });
                kits.put(key, updatedListTag);
            });
        }
    }
}

class PreviewScreen extends AbstractInventoryScreen<PlayerScreenHandler> {

    public PreviewScreen(PlayerScreenHandler playerScreenHandler, PlayerInventory inventory, String name) {
        super(playerScreenHandler, inventory, new LiteralText(name).styled(style -> style.withColor(Formatting.RED)));
        this.passEvents = true;
        this.titleX = 80;
    }

    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        this.textRenderer.draw(matrices, this.title, (float) this.titleX, (float) this.titleY, 0x404040);
    }

    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.drawStatusEffects = false;
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);

        this.drawMouseoverTooltip(matrices, mouseX, mouseY);
    }

    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        this.drawTexture(matrices, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
