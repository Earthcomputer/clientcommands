package net.earthcomputer.clientcommands.features;

import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.packets.StartTwoPlayerGameC2CPacket;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.ConnectFourCommand;
import net.earthcomputer.clientcommands.command.TicTacToeCommand;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TwoPlayerGameType<T, S extends Screen> {
    public static final Map<ResourceLocation, TwoPlayerGameType<?, ?>> TYPE_BY_NAME = new LinkedHashMap<>();
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.playerNotFound"));
    private static final SimpleCommandExceptionType NO_GAME_WITH_PLAYER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.noGameWithPlayer"));

    public static final TwoPlayerGameType<TicTacToeCommand.TicTacToeGame, TicTacToeCommand.TicTacToeGameScreen> TIC_TAC_TOE_GAME_TYPE = register(new TwoPlayerGameType<>("commands.ctictactoe.name", "ctictactoe", ResourceLocation.fromNamespaceAndPath("clientcommands", "tictactoe"), (opponent, firstPlayer) -> new TicTacToeCommand.TicTacToeGame(opponent, firstPlayer ? TicTacToeCommand.TicTacToeGame.Mark.CROSS : TicTacToeCommand.TicTacToeGame.Mark.NOUGHT), TicTacToeCommand.TicTacToeGameScreen::new));
    public static final TwoPlayerGameType<ConnectFourCommand.ConnectFourGame, ConnectFourCommand.ConnectFourGameScreen > FOUR_IN_A_ROW_GAME_TYPE = register(new TwoPlayerGameType<>("commands.cconnectfour.name", "cconnectfour", ResourceLocation.fromNamespaceAndPath("clientcommands", "connectfour"), (opponent, firstPlayer) -> new ConnectFourCommand.ConnectFourGame(opponent, firstPlayer ? ConnectFourCommand.Piece.RED : ConnectFourCommand.Piece.YELLOW), ConnectFourCommand.ConnectFourGameScreen::new));

    private static <T, S extends Screen> TwoPlayerGameType<T, S> register(TwoPlayerGameType<T, S> instance) {
        TYPE_BY_NAME.put(instance.id, instance);
        return instance;
    }

    @Nullable
    public static TwoPlayerGameType<?, ?> getById(ResourceLocation id) {
        return TYPE_BY_NAME.get(id);
    }

    private final String translationKey;
    private final String command;
    private final ResourceLocation id;
    private final Set<String> pendingInvites;
    private final Map<String, T> activeGames;
    private final GameFactory<T> gameFactory;
    private final ScreenFactory<T, S> screenFactory;

    TwoPlayerGameType(String translationKey, String command, ResourceLocation id, GameFactory<T> gameFactory, ScreenFactory<T, S> screenFactory) {
        this.translationKey = translationKey;
        this.command = command;
        this.id = id;
        this.pendingInvites = Collections.newSetFromMap(CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).<String, Boolean>build().asMap());
        this.activeGames = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).<String, T>build().asMap();
        this.gameFactory = gameFactory;
        this.screenFactory = screenFactory;
    }

    public Component translate() {
        return Component.translatable(this.translationKey);
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public Set<String> getPendingInvites() {
        return this.pendingInvites;
    }

    public Map<String, T> getActiveGames() {
        return this.activeGames;
    }

    @Nullable
    public T getActiveGame(String opponent) {
        return this.activeGames.get(opponent);
    }

    public void addNewGame(PlayerInfo opponent, boolean isFirstPlayer) {
        this.activeGames.put(opponent.getProfile().getName(), this.gameFactory.create(opponent, isFirstPlayer));
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> createCommandTree() {
        return literal(this.command)
            .then(literal("start")
                .then(argument("opponent", gameProfile(true))
                    .executes(ctx -> this.start(ctx.getSource(), getSingleProfileArgument(ctx, "opponent")))))
            .then(literal("open")
                .then(argument("opponent", word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(this.getActiveGames().keySet(), builder))
                    .executes(ctx -> this.open(ctx.getSource(), getString(ctx, "opponent")))));
    }

    public int start(FabricClientCommandSource source, GameProfile player) throws CommandSyntaxException {
        PlayerInfo recipient = source.getClient().getConnection().getPlayerInfo(player.getId());
        if (recipient == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        StartTwoPlayerGameC2CPacket packet = new StartTwoPlayerGameC2CPacket(source.getClient().getConnection().getLocalGameProfile().getName(), false, this);
        C2CPacketHandler.getInstance().sendPacket(packet, recipient);
        this.pendingInvites.add(recipient.getProfile().getName());
        source.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.outgoing.invited", recipient.getProfile().getName(), translate()));
        return Command.SINGLE_SUCCESS;
    }

    public int open(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        T game = this.activeGames.get(name);
        if (game == null) {
            throw NO_GAME_WITH_PLAYER_EXCEPTION.create();
        }

        source.getClient().tell(() -> source.getClient().setScreen(this.screenFactory.createScreen(game)));
        return Command.SINGLE_SUCCESS;
    }

    public static void onStartTwoPlayerGame(StartTwoPlayerGameC2CPacket packet) {
        String sender = packet.sender();
        TwoPlayerGameType<?, ?> game = packet.game();
        PlayerInfo opponent = Minecraft.getInstance().getConnection().getPlayerInfo(sender);
        if (opponent == null) {
            return;
        }

        if (packet.accept() && game.getPendingInvites().remove(sender)) {
            packet.game().addNewGame(opponent, true);

            MutableComponent component = Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accepted", sender, game.translate());
            component.withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + game.command + " open " + sender))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + game.command + " open " + sender))));
            Minecraft.getInstance().gui.getChat().addMessage(component);
            return;
        }

        MutableComponent component = Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming", sender, game.translate());
        component
            .append(" [")
            .append(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accept").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, ClientCommandHelper.registerCode(() -> {
                    game.addNewGame(opponent, false);

                    StartTwoPlayerGameC2CPacket acceptPacket = new StartTwoPlayerGameC2CPacket(Minecraft.getInstance().getConnection().getLocalGameProfile().getName(), true, game);
                    try {
                        C2CPacketHandler.getInstance().sendPacket(acceptPacket, opponent);
                    } catch (CommandSyntaxException e) {
                        Minecraft.getInstance().gui.getChat().addMessage(Component.translationArg(e.getRawMessage()));
                    }

                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.outgoing.accept"));
                })))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accept.hover")))))
            .append("]");
        Minecraft.getInstance().gui.getChat().addMessage(component);
    }

    @FunctionalInterface
    public interface GameFactory<T> {
        T create(PlayerInfo opponent, boolean isFirstPlayer);
    }

    @FunctionalInterface
    public interface ScreenFactory<T, S extends Screen> {
        S createScreen(T t);
    }
}
