package net.earthcomputer.clientcommands.command;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.c2c.CCNetworkHandler;
import net.earthcomputer.clientcommands.c2c.StringBuf;
import net.earthcomputer.clientcommands.c2c.packets.SnakeBodyC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.SnakeInviteC2CPacket;
import net.earthcomputer.clientcommands.c2c.packets.SnakeJoinC2CPacket;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.gameProfile;
import static dev.xpple.clientarguments.arguments.CGameProfileArgumentType.getCProfileArgument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SnakeCommand {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SimpleCommandExceptionType PLAYER_NOT_FOUND_EXCEPTION = new SimpleCommandExceptionType(Text.translatable("commands.cwe.playerNotFound"));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csnake")
            .executes(ctx -> snake(ctx.getSource(), null))
            .then(literal("invite")
                .then(argument("player", gameProfile())
                    .executes(ctx -> invite(ctx.getSource(), getCProfileArgument(ctx, "player")))
                )
            )
            .then(literal("join")
                .then(argument("player", gameProfile())
                    .executes(ctx -> joinGame(ctx.getSource(), getCProfileArgument(ctx, "player")))
                )
            )
        );
    }

    private static int joinGame(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.size() != 1) {
            throw PLAYER_NOT_FOUND_EXCEPTION.create();
        }
        assert source.getClient().getNetworkHandler() != null;
        final PlayerListEntry gameToJoin = CCNetworkHandler.getPlayerByName(profiles.iterator().next().getName())
            .orElseThrow(PLAYER_NOT_FOUND_EXCEPTION::create);
        return snake(source, gameToJoin);
    }

    private static int snake(FabricClientCommandSource source, @Nullable PlayerListEntry gameToJoin) throws CommandSyntaxException {
        final String otherSnake;
        if (gameToJoin != null) {
            otherSnake = gameToJoin.getProfile().getName();
            assert source.getClient().getNetworkHandler() != null;
            CCNetworkHandler.getInstance().sendPacket(new SnakeJoinC2CPacket(
                source.getClient().getNetworkHandler().getProfile().getName()
            ), gameToJoin);
        } else {
            otherSnake = null;
        }
        /*
            After executing a command, the current screen will be closed (the chat hud).
            And if you open a new screen in a command, that new screen will be closed
            instantly along with the chat hud. Slightly delaying the opening of the
            screen fixes this issue.
         */
        source.getClient().send(() -> source.getClient().setScreen(new SnakeGameScreen(otherSnake)));
        return Command.SINGLE_SUCCESS;
    }

    private static int invite(FabricClientCommandSource source, Collection<GameProfile> profiles) throws CommandSyntaxException {
        assert source.getClient().getNetworkHandler() != null;
        final String sender = source.getClient().getNetworkHandler().getProfile().getName();
        final Set<String> names = profiles.stream()
            .map(GameProfile::getName)
            .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        try {
            final long inviteCount = source.getClient().getNetworkHandler().getPlayerList().stream()
                .filter(entry -> names.contains(entry.getProfile().getName()))
                .peek(player -> {
                    final SnakeInviteC2CPacket packet = new SnakeInviteC2CPacket(sender);
                    try {
                        CCNetworkHandler.getInstance().sendPacket(packet, player);
                    } catch (CommandSyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    // TODO: Text.translatable
                    source.sendFeedback(
                        Text.literal("Invited ")
                            .append(Objects.requireNonNullElseGet(
                                player.getDisplayName(), () -> Text.literal(player.getProfile().getName())
                            ))
                            .append(" to a game of snake.")
                            .styled(style -> style.withColor(Formatting.GRAY))
                    );
                })
                .count();
            if (inviteCount == 0L) {
                source.sendFeedback(Text.literal("Couldn't find any players."));
            }
            snake(source, null);
            return (int)inviteCount;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof CommandSyntaxException syntaxException) {
                throw syntaxException;
            }
            throw e;
        }
    }

    public static class SnakeGameScreen extends Screen {

        private static final MinecraftClient client = MinecraftClient.getInstance();

        private static final Identifier GRID_TEXTURE = new Identifier("clientcommands:textures/snake_grid.png");

        private static final Random random = new Random();

        private static final int MAX_X = 16;
        private static final int MAX_Z = 16;

        private int tickCounter = 10;
        private Direction direction = Direction.EAST;
        private Direction lastMoved = Direction.EAST;
        private final LinkedList<Vec2i> snake = new LinkedList<>();
        private final Map<String, List<Vec2i>> otherSnakes = new HashMap<>();
        private final Queue<Direction> directionQueue = new ArrayDeque<>();
        private Vec2i apple;

        SnakeGameScreen(@Nullable String otherSnake) {
            super(Text.translatable("snakeGame.title"));
            if (otherSnake != null) {
                otherSnakes.put(otherSnake, List.of());
            }
            this.snake.add(new Vec2i(6, 8));
            this.snake.add(new Vec2i(5, 8));
            this.snake.add(new Vec2i(4, 8));
            do {
                this.apple = new Vec2i(random.nextInt(MAX_X + 1), random.nextInt(MAX_Z + 1));
            } while (this.snake.contains(this.apple));
        }

        @Override
        public void tick() {
            if (--this.tickCounter < 0) {
                this.tickCounter = 3;
                this.move();
            }
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            this.renderBackground(matrices);
            super.render(matrices, mouseX, mouseY, delta);
        }

        @Override
        public void renderBackground(MatrixStack matrices) {
            super.renderBackground(matrices);
            int startX = (this.width - 289) / 2;
            int startY = (this.height - 289) / 2;

            drawTextWithShadow(matrices, client.textRenderer, this.title, startX, startY - 10, 0xff_ffffff);
            MutableText score = Text.translatable("snakeGame.score", this.snake.size());
            drawCenteredText(matrices, client.textRenderer, score, this.width / 2, startY - 10, 0xff_ffffff);

            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.setShaderTexture(0, GRID_TEXTURE);
            drawTexture(matrices, startX, startY, 0, 0, 289, 289, 289, 289);
            int scaleX = MAX_X + 1;
            int scaleZ = MAX_Z + 1;
            DrawableHelper.fill(matrices, startX + this.apple.x() * scaleX, startY + this.apple.z() * scaleZ, startX + this.apple.x() * scaleX + scaleX, startY + this.apple.z() * scaleZ + scaleZ, 0xff_f52559);
            for (Vec2i vec : this.snake) {
                DrawableHelper.fill(matrices, startX + vec.x() * scaleX, startY + vec.z() * scaleZ, startX + vec.x() * scaleX + scaleX, startY + vec.z() * scaleZ + scaleZ, 0xff_1f2df6);
            }
            for (final Map.Entry<String, List<Vec2i>> otherSnake : otherSnakes.entrySet()) {
                for (final Vec2i vec : otherSnake.getValue()) {
                    DrawableHelper.fill(matrices, startX + vec.x() * scaleX, startY + vec.z() * scaleZ, startX + vec.x() * scaleX + scaleX, startY + vec.z() * scaleZ + scaleZ, 0xffffa500);
                }
                if (!otherSnake.getValue().isEmpty()) {
                    final Vec2i head = otherSnake.getValue().get(0);
                    DrawableHelper.drawCenteredText(
                        matrices, textRenderer,
                        otherSnake.getKey(),
                        startX + head.x() * scaleX + scaleX / 2,
                        startY + head.z() * scaleZ - scaleZ,
                        0xffffffff
                    );
                }
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (client.options.forwardKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_UP) {
                return enqueueMove(Direction.NORTH);
            } else if (client.options.leftKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_LEFT) {
                return enqueueMove(Direction.WEST);
            } else if (client.options.backKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_DOWN) {
                return enqueueMove(Direction.SOUTH);
            } else if (client.options.rightKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_RIGHT) {
                return enqueueMove(Direction.EAST);
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void move() {
            while (!directionQueue.isEmpty()) {
                if (setDirection(directionQueue.remove())) break;
            }
            Vec2i head = this.snake.getFirst();
            this.snake.addFirst(new Vec2i(head.x() + this.direction.getOffsetX(), head.z() + this.direction.getOffsetZ()));
            this.lastMoved = this.direction;
            if (this.checkGameOver()) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_OCELOT_DEATH, 1));
                this.close();
                return;
            }
            this.checkApple();
            syncPlayerData();
        }

        private boolean checkGameOver() {
            Vec2i head = this.snake.getFirst();
            if (head.x() < 0 || head.x() > MAX_X || head.z() < 0 || head.z() > MAX_Z) {
                return true;
            }
            ListIterator<Vec2i> it = this.snake.listIterator(1);
            while (it.hasNext()) {
                if (it.next().equals(head)) {
                    return true;
                }
            }
            return false;
        }

        private void checkApple() {
            Vec2i head = this.snake.getFirst();
            if (head.equals(this.apple)) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_GENERIC_EAT, 1));
                do {
                    this.apple = new Vec2i(random.nextInt(MAX_X + 1), random.nextInt(MAX_Z + 1));
                } while (this.snake.contains(this.apple));
            } else {
                this.snake.removeLast();
            }
        }

        private void syncPlayerData() {
            if (otherSnakes.isEmpty()) return;
            assert client.getNetworkHandler() != null;
            final SnakeBodyC2CPacket packet = new SnakeBodyC2CPacket(
                client.getNetworkHandler().getProfile().getName(), snake
            );
            for (final String otherSnake : otherSnakes.keySet()) {
                CCNetworkHandler.getPlayerByName(otherSnake).ifPresent(player -> {
                    try {
                        CCNetworkHandler.getInstance().sendPacket(packet, player);
                    } catch (CommandSyntaxException e) {
                        LOGGER.warn("Failed to sync snake data to " + otherSnake, e);
                    }
                });
            }
        }

        private boolean setDirection(Direction direction) {
            if (this.lastMoved == direction.getOpposite() || this.lastMoved == direction) {
                return false;
            }
            this.direction = direction;
            return true;
        }

        private boolean enqueueMove(Direction direction) {
            while (directionQueue.size() > 2) {
                directionQueue.remove();
            }
            directionQueue.add(direction);
            return true;
        }

        public Map<String, List<Vec2i>> getOtherSnakes() {
            return otherSnakes;
        }
    }

    public record Vec2i(int x, int z) {
        public Vec2i(StringBuf buf) {
            this(buf.readInt(), buf.readInt());
        }

        public static void write(StringBuf buf, Vec2i vec) {
            buf.writeInt(vec.x);
            buf.writeInt(vec.z);
        }
    }
}
