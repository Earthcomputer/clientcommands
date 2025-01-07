package net.earthcomputer.clientcommands.features;

import com.demonwav.mcdev.annotations.Translatable;
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
import net.earthcomputer.clientcommands.event.ClientConnectionEvents;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
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
import java.util.UUID;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static dev.xpple.clientarguments.arguments.CGameProfileArgument.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class TwoPlayerGame<T, S extends Screen> {
    public static final Map<ResourceLocation, TwoPlayerGame<?, ?>> TYPE_BY_NAME = new LinkedHashMap<>();
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.playerNotFound"));
    private static final SimpleCommandExceptionType NO_GAME_WITH_PLAYER_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("twoPlayerGame.noGameWithPlayer"));

    public static final TwoPlayerGame<TicTacToeCommand.TicTacToeGame, TicTacToeCommand.TicTacToeGameScreen> TIC_TAC_TOE_GAME_TYPE = register(new TwoPlayerGame<>("commands.ctictactoe.name", "ctictactoe", ResourceLocation.fromNamespaceAndPath("clientcommands", "tictactoe"), (opponent, firstPlayer) -> new TicTacToeCommand.TicTacToeGame(opponent, firstPlayer ? TicTacToeCommand.TicTacToeGame.Mark.CROSS : TicTacToeCommand.TicTacToeGame.Mark.NOUGHT), TicTacToeCommand.TicTacToeGameScreen::new));
    public static final TwoPlayerGame<ConnectFourCommand.ConnectFourGame, ConnectFourCommand.ConnectFourGameScreen > CONNECT_FOUR_GAME_TYPE = register(new TwoPlayerGame<>("commands.cconnectfour.name", "cconnectfour", ResourceLocation.fromNamespaceAndPath("clientcommands", "connectfour"), (opponent, firstPlayer) -> new ConnectFourCommand.ConnectFourGame(opponent, firstPlayer ? ConnectFourCommand.Piece.RED : ConnectFourCommand.Piece.YELLOW), ConnectFourCommand.ConnectFourGameScreen::new));

    private final Component translation;
    private final String command;
    private final ResourceLocation id;
    private final Set<UUID> pendingInvites;
    private final Map<UUID, T> activeGames;
    private final GameFactory<T> gameFactory;
    private final ScreenFactory<T, S> screenFactory;

    TwoPlayerGame(@Translatable String translationKey, String command, ResourceLocation id, GameFactory<T> gameFactory, ScreenFactory<T, S> screenFactory) {
        this.translation = Component.translatable(translationKey);
        this.command = command;
        this.id = id;
        this.pendingInvites = Collections.newSetFromMap(CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).<UUID, Boolean>build().asMap());
        this.activeGames = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(15)).<UUID, T>build().asMap();
        this.gameFactory = gameFactory;
        this.screenFactory = screenFactory;
    }

    private static <T, S extends Screen> TwoPlayerGame<T, S> register(TwoPlayerGame<T, S> instance) {
        TYPE_BY_NAME.put(instance.id, instance);
        return instance;
    }

    @Nullable
    public static TwoPlayerGame<?, ?> getById(ResourceLocation id) {
        return TYPE_BY_NAME.get(id);
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

    public ResourceLocation getId() {
        return this.id;
    }

    public Set<UUID> getPendingInvites() {
        return this.pendingInvites;
    }

    public Map<UUID, T> getActiveGames() {
        return this.activeGames;
    }

    @Nullable
    public T getActiveGame(UUID opponent) {
        return this.activeGames.get(opponent);
    }

    public void removeActiveGame(UUID opponent) {
        this.activeGames.remove(opponent);
    }

    public void addNewGame(PlayerInfo opponent, boolean isFirstPlayer) {
        this.activeGames.put(opponent.getProfile().getId(), this.gameFactory.create(opponent, isFirstPlayer));
    }

    public LiteralArgumentBuilder<FabricClientCommandSource> createCommandTree() {
        final Minecraft mc = Minecraft.getInstance();
        final ClientPacketListener connection = mc.getConnection();
        assert connection != null;
        return literal(this.command)
            .then(literal("start")
                .then(argument("opponent", gameProfile(true))
                    .executes(ctx -> this.start(ctx.getSource(), getSingleProfileArgument(ctx, "opponent")))))
            .then(literal("open")
                .then(argument("opponent", word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(this.getActiveGames().keySet().stream().flatMap(uuid -> Stream.ofNullable(connection.getPlayerInfo(uuid))).map(info -> info.getProfile().getName()), builder))
                    .executes(ctx -> this.open(ctx.getSource(), getString(ctx, "opponent")))));
    }

    public int start(FabricClientCommandSource source, GameProfile player) throws CommandSyntaxException {
        PlayerInfo recipient = source.getClient().getConnection().getPlayerInfo(player.getId());
        if (recipient == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }

        StartTwoPlayerGameC2CPacket packet = new StartTwoPlayerGameC2CPacket(player.getName(), player.getId(), false, this);
        C2CPacketHandler.getInstance().sendPacket(packet, recipient);
        this.pendingInvites.add(player.getId());
        this.activeGames.remove(player.getId());
        source.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.outgoing.invited", player.getName(), translate()));
        return Command.SINGLE_SUCCESS;
    }

    public int open(FabricClientCommandSource source, String name) throws CommandSyntaxException {
        PlayerInfo opponent = source.getClient().getConnection().getPlayerInfo(name);
        if (opponent == null) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        if (!openGame(opponent.getProfile().getId())) {
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

        if (packet.accept() && game.getPendingInvites().remove(opponent.getProfile().getId())) {
            packet.game().addNewGame(opponent, true);

            MutableComponent clickable = Component.translatable("twoPlayerGame.clickToMakeYourMove");
            clickable.withStyle(style -> style
                .withUnderlined(true)
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + game.command + " open " + sender))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + game.command + " open " + sender))));
            ClientCommandHelper.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accepted", sender, game.translate()).append(" [").append(clickable).append("]"));
        } else {
            game.getActiveGames().remove(opponent.getProfile().getId());
            MutableComponent clickable = Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accept").withStyle(style ->
                style
                    .withUnderlined(true)
                    .withColor(ChatFormatting.GREEN)
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming.accept.hover")))
                    .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, ClientCommandHelper.registerCode(() -> {
                        if (!game.openGame(opponent.getProfile().getId())) {
                            game.addNewGame(opponent, false);

                            StartTwoPlayerGameC2CPacket acceptPacket = new StartTwoPlayerGameC2CPacket(mc.getGameProfile().getName(), mc.getGameProfile().getId(), true, game);
                            try {
                                C2CPacketHandler.getInstance().sendPacket(acceptPacket, opponent);
                            } catch (CommandSyntaxException e) {
                                ClientCommandHelper.sendFeedback(Component.translationArg(e.getRawMessage()));
                            }

                            ClientCommandHelper.sendFeedback("c2cpacket.startTwoPlayerGameC2CPacket.outgoing.accept");
                        }
                    }))));
            ClientCommandHelper.sendFeedback(Component.translatable("c2cpacket.startTwoPlayerGameC2CPacket.incoming", sender, game.translate()).append(" [").append(clickable).append("]"));
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
        MutableComponent clickable = Component.translatable("twoPlayerGame.clickToMakeYourMove");
        clickable.withStyle(style -> style
            .withColor(ChatFormatting.GREEN)
            .withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + command + " open " + sender))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/" + command + " open " + sender))));
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
