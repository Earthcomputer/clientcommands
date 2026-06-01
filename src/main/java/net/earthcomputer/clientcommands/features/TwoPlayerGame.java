package net.earthcomputer.clientcommands.features;

import com.demonwav.mcdev.annotations.Translatable;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.c2c.C2CPacketHandler;
import net.earthcomputer.clientcommands.c2c.chess.ChessColor;
import net.earthcomputer.clientcommands.c2c.chess.ChessGame;
import net.earthcomputer.clientcommands.c2c.chess.ChessScreen;
import net.earthcomputer.clientcommands.c2c.packets.StartTwoPlayerGameC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.StopTwoPlayerGameC2CPacket;
import net.earthcomputer.clientcommands.command.ClientCommandHelper;
import net.earthcomputer.clientcommands.command.ConnectFourCommand;
import net.earthcomputer.clientcommands.command.TicTacToeCommand;
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.earthcomputer.clientcommands.util.CComponentUtil;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class TwoPlayerGame<T, S extends Screen> {
    public static final Map<Identifier, TwoPlayerGame<?, ?>> TYPE_BY_NAME = new LinkedHashMap<>();
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.playerNotFound"));
    private static final SimpleCommandExceptionType NO_GAME_WITH_PLAYER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.noGameWithPlayer"));
    private static final SimpleCommandExceptionType ALREADY_IN_GAME_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.alreadyInGame"));

    public static final TwoPlayerGame<TicTacToeCommand.TicTacToeGame, TicTacToeCommand.TicTacToeGameScreen> TIC_TAC_TOE_GAME_TYPE = register(new TwoPlayerGame<>("commands.ctictactoe.name", "ctictactoe", Identifier.fromNamespaceAndPath("clientcommands", "tictactoe"), (opponent, firstPlayer) -> new TicTacToeCommand.TicTacToeGame(opponent, firstPlayer ? TicTacToeCommand.TicTacToeGame.Mark.CROSS : TicTacToeCommand.TicTacToeGame.Mark.NOUGHT), TicTacToeCommand.TicTacToeGameScreen::new));
    public static final TwoPlayerGame<ConnectFourCommand.ConnectFourGame, ConnectFourCommand.ConnectFourGameScreen> CONNECT_FOUR_GAME_TYPE = register(new TwoPlayerGame<>("commands.cconnectfour.name", "cconnectfour", Identifier.fromNamespaceAndPath("clientcommands", "connectfour"), (opponent, firstPlayer) -> new ConnectFourCommand.ConnectFourGame(opponent, firstPlayer ? ConnectFourCommand.Piece.RED : ConnectFourCommand.Piece.YELLOW), ConnectFourCommand.ConnectFourGameScreen::new));
    public static final TwoPlayerGame<ChessGame, ChessScreen> CHESS_TYPE = register(new TwoPlayerGame<>("commands.cchess.name", "cchess", Identifier.fromNamespaceAndPath("clientcommands", "chess"), (opponent, firstPlayer) -> new ChessGame(opponent, firstPlayer ? ChessColor.WHITE : ChessColor.BLACK), ChessScreen::new));

    private final Component translation;
    private final String command;
    private final Identifier id;
    private final Set<UUID> pendingInvites;
    private final Map<UUID, T> activeGames;
    private final GameFactory<T> gameFactory;
    private final ScreenFactory<T, S> screenFactory;

    TwoPlayerGame(@Translatable String translationKey, String command, Identifier id, GameFactory<T> gameFactory, ScreenFactory<T, S> screenFactory) {
        this.translation = Component.translatable(translationKey);
        this.command = command;
        this.id = id;
        this.pendingInvites = Collections.newSetFromMap(CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).<UUID, Boolean>build().asMap());
        this.activeGames = new HashMap<>();
        this.gameFactory = gameFactory;
        this.screenFactory = screenFactory;
    }

    private static <T, S extends Screen> TwoPlayerGame<T, S> register(TwoPlayerGame<T, S> instance) {
        TYPE_BY_NAME.put(instance.id, instance);
        return instance;
    }

    @Nullable
    public static TwoPlayerGame<?, ?> getById(Identifier id) {
        return TYPE_BY_NAME.get(id);
    }

    public static TwoPlayerGame<?, ?> getByIdOrThrow(Identifier id) {
        TwoPlayerGame<?, ?> game = getById(id);
        if (game == null) {
            throw new IllegalStateException("Unknown game type " + id);
        }
        return game;
    }

    public static void onPlayerLeave(UUID opponentUUID) {
        for (TwoPlayerGame<?, ?> game : TYPE_BY_NAME.values()) {
            game.activeGames.remove(opponentUUID);
            game.pendingInvites.remove(opponentUUID);
        }
    }

    static {
        ClientConnectionEvents.DISCONNECT.register(() -> {
            for (TwoPlayerGame<?, ?> game : TYPE_BY_NAME.values()) {
                game.activeGames.clear();
                game.pendingInvites.clear();
            }
        });
    }

    public Component translate() {
        return this.translation;
    }

    public Identifier getId() {
        return this.id;
    }

    public Set<UUID> getPendingInvites() {
        return this.pendingInvites;
    }

    public Map<UUID, T> getActiveGames() {
        return this.activeGames;
    }

    @Contract("null -> null")
    @Nullable
    public T getActiveGame(@Nullable UUID opponent) {
        return this.activeGames.get(opponent);
    }

    public void removeActiveGame(UUID opponent) {
        this.activeGames.remove(opponent);
    }

    public void addNewGame(PlayerInfo opponent, boolean isFirstPlayer) {
        this.activeGames.put(opponent.getProfile().id(), this.gameFactory.create(opponent, isFirstPlayer));
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> createCommandTree() {
        final Minecraft mc = Minecraft.getInstance();
        final ClientPacketListener connection = mc.getConnection();
        assert connection != null;
        return literal(this.command)
            .then(literal("start")
                .then(argument("opponent", gameProfile(true))
                    .executes(ctx -> this.start(ctx.getSource(), getSingleProfileArgument(ctx, "opponent")))))
            .then(literal("stop")
                .then(argument("opponent", word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(this.getActiveGames().keySet().stream().flatMap(uuid -> Stream.ofNullable(connection.getPlayerInfo(uuid))).map(info -> info.getProfile().name()), builder))
                    .executes(ctx -> this.stop(ctx.getSource(), getString(ctx, "opponent")))))
            .then(literal("open")
                .then(argument("opponent", word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(this.getActiveGames().keySet().stream().flatMap(uuid -> Stream.ofNullable(connection.getPlayerInfo(uuid))).map(info -> info.getProfile().name()), builder))
                    .executes(ctx -> this.open(ctx.getSource(), getString(ctx, "opponent")))));
    }

    private int start(FabricClientCommandSource source, GameProfile player) throws CommandSyntaxException {
        PlayerInfo recipient = source.getClient().getConnection().getPlayerInfo(player.id());
        if (recipient == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        if (activeGames.containsKey(player.id())) {
            throw ALREADY_IN_GAME_EXCEPTION.create();
        }

        StartTwoPlayerGameC2CPacket packet = new StartTwoPlayerGameC2CPacket(Minecraft.getInstance().getGameProfile().name(), Minecraft.getInstance().getGameProfile().id(), false, this);
        C2CPacketHandler.getInstance().sendPacket(packet, recipient);
        this.pendingInvites.add(player.id());
        this.activeGames.remove(player.id());
        source.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.outgoing.invited", player.name(), translate()));
        return Command.SINGLE_SUCCESS;
    }

    private int stop(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        PlayerInfo opponent = source.getClient().getConnection().getPlayerInfo(name);
        if (opponent == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        if (this.activeGames.remove(opponent.getProfile().id()) == null) {
            throw NO_GAME_WITH_PLAYER_EXCEPTION.create();
        }

        StopTwoPlayerGameC2CPacket packet = new StopTwoPlayerGameC2CPacket(Minecraft.getInstance().getGameProfile().name(), Minecraft.getInstance().getGameProfile().id(), this);
        C2CPacketHandler.getInstance().sendPacket(packet, opponent);
        source.sendFeedback(Component.translatable("twoPlayerGame.stopped", translate(), name));
        return Command.SINGLE_SUCCESS;
    }

    private int open(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        PlayerInfo opponent = source.getClient().getConnection().getPlayerInfo(name);
        if (opponent == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        if (!openGame(opponent.getProfile().id())) {
            throw NO_GAME_WITH_PLAYER_EXCEPTION.create();
        }

        return Command.SINGLE_SUCCESS;
    }

    private boolean openGame(UUID opponentUuid) {
        final Minecraft mc = Minecraft.getInstance();
        T game = activeGames.get(opponentUuid);
        if (game != null) {
            mc.schedule(() -> mc.setScreen(this.screenFactory.createScreen(game)));
            return true;
        } else {
            return false;
        }
    }

    public static void onStartTwoPlayerGame(StartTwoPlayerGameC2CPacket packet) {
        final Minecraft mc = Minecraft.getInstance();
        String sender = packet.sender();
        TwoPlayerGame<?, ?> game = packet.game();
        PlayerInfo opponent = Minecraft.getInstance().getConnection().getPlayerInfo(sender);
        if (opponent == null) {
            return;
        }

        if (packet.accept() && game.getPendingInvites().remove(opponent.getProfile().id())) {
            packet.game().addNewGame(opponent, true);

            Component clickable = CComponentUtil.getCommandTextComponent("twoPlayerGame.clickToMakeYourMove", "/" + game.command + " open " + sender);
            ClientCommandHelper.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accepted", sender, game.translate()).append(" [").append(clickable).append("]"));
        } else {
            game.getActiveGames().remove(opponent.getProfile().id());
            MutableComponent clickable = Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accept").withStyle(style ->
                style
                    .withUnderlined(true)
                    .withColor(ChatFormatting.GREEN)
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accept.hover")))
                    .withClickEvent(CComponentUtil.callbackClickEvent(() -> {
                        if (!game.openGame(opponent.getProfile().id())) {
                            game.addNewGame(opponent, false);

                            StartTwoPlayerGameC2CPacket acceptPacket = new StartTwoPlayerGameC2CPacket(mc.getGameProfile().name(), mc.getGameProfile().id(), true, game);
                            try {
                                C2CPacketHandler.getInstance().sendPacket(acceptPacket, opponent);
                            } catch (CommandSyntaxException e) {
                                ClientCommandHelper.sendFeedback(Component.translationArg(e.getRawMessage()));
                            }

                            ClientCommandHelper.sendFeedback("c2cpacket.startTwoPlayerGameC2CPacket.outgoing.accept");
                        }
                    })));
            ClientCommandHelper.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming", sender, game.translate()).append(" [").append(clickable).append("]"));
        }
    }

    public static void onStopTwoPlayerGame(StopTwoPlayerGameC2CPacket packet) {
        if (packet.senderUUID() != null) {
            packet.game().removeActiveGame(packet.senderUUID());
            ClientCommandHelper.sendFeedback("c2cpacket.stopTwoPlayerGameC2CPacket.incoming", Component.nullToEmpty(packet.sender()), packet.game().translate());
        }
    }

    public void onWon(String sender, UUID senderUUID) {
        ClientCommandHelper.sendFeedback("twoPlayerGame.chat.won", translate(), sender);
        removeActiveGame(senderUUID);
    }

    public void onDraw(String sender, UUID senderUUID) {
        ClientCommandHelper.sendFeedback("twoPlayerGame.chat.draw", translate(), sender);
        removeActiveGame(senderUUID);
    }

    public void onLost(String sender, UUID senderUUID) {
        ClientCommandHelper.sendFeedback("twoPlayerGame.chat.lost", sender, translate());
        removeActiveGame(senderUUID);
    }

    public void onMove(String sender) {
        Component clickable = CComponentUtil.getCommandTextComponent("twoPlayerGame.clickToMakeYourMove", "/" + command + " open " + sender);
        ClientCommandHelper.sendFeedback(Component.translatable("twoPlayerGame.incoming", sender, translate()).append(" [").append(clickable).append("]"));
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
