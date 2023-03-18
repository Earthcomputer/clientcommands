package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SnakeCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csnake")
                .executes(ctx -> snake(ctx.getSource())));
    }

    private static int snake(FabricClientCommandSource source) {
        /*
            After executing a command, the current screen will be closed (the chat hud).
            And if you open a new screen in a command, that new screen will be closed
            instantly along with the chat hud. Slightly delaying the opening of the
            screen fixes this issue.
         */
        source.getClient().send(() -> source.getClient().setScreen(new SnakeGameScreen()));
        return Command.SINGLE_SUCCESS;
    }
}

class SnakeGameScreen extends Screen {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Identifier GRID_TEXTURE = new Identifier("clientcommands:textures/snake_grid.png");

    private static final Random random = new Random();

    private static final int MAX_X = 16;
    private static final int MAX_Y = 16;

    private int tickCounter = 10;
    private Direction direction = Direction.EAST;
    private Direction lastMoved = Direction.EAST;
    private final LinkedList<Vector2i> snake = new LinkedList<>();
    private Vector2i apple;

    SnakeGameScreen() {
        super(Text.translatable("snakeGame.title"));
        this.snake.add(new Vector2i(6, 8));
        this.snake.add(new Vector2i(5, 8));
        this.snake.add(new Vector2i(4, 8));
        do {
            this.apple = new Vector2i(random.nextInt(MAX_X + 1), random.nextInt(MAX_Y + 1));
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
        drawCenteredTextWithShadow(matrices, client.textRenderer, score, this.width / 2, startY - 10, 0xff_ffffff);

        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, GRID_TEXTURE);
        drawTexture(matrices, startX, startY, 0, 0, 289, 289, 289, 289);
        int scaleX = MAX_X + 1;
        int scaleY = MAX_Y + 1;
        DrawableHelper.fill(matrices, startX + this.apple.x() * scaleX, startY + this.apple.y() * scaleY, startX + this.apple.x() * scaleX + scaleX, startY + this.apple.y() * scaleY + scaleY, 0xff_f52559);
        for (Vector2i vec : this.snake) {
            DrawableHelper.fill(matrices, startX + vec.x() * scaleX, startY + vec.y() * scaleY, startX + vec.x() * scaleX + scaleX, startY + vec.y() * scaleY + scaleY, 0xff_1f2df6);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client.options.forwardKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_UP) {
            return this.setDirection(Direction.NORTH);
        } else if (client.options.leftKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_LEFT) {
            return this.setDirection(Direction.WEST);
        } else if (client.options.backKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_DOWN) {
            return this.setDirection(Direction.SOUTH);
        } else if (client.options.rightKey.matchesKey(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_RIGHT) {
            return this.setDirection(Direction.EAST);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void move() {
        Vector2i head = this.snake.getFirst();
        this.snake.addFirst(new Vector2i(head.x() + this.direction.getOffsetX(), head.y() + this.direction.getOffsetZ()));
        this.lastMoved = this.direction;
        if (this.checkGameOver()) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_OCELOT_DEATH, 1));
            this.close();
            return;
        }
        this.checkApple();
    }

    private boolean checkGameOver() {
        Vector2i head = this.snake.getFirst();
        if (head.x() < 0 || head.x() > MAX_X || head.y() < 0 || head.y() > MAX_Y) {
            return true;
        }
        ListIterator<Vector2i> it = this.snake.listIterator(1);
        while (it.hasNext()) {
            if (it.next().equals(head)) {
                return true;
            }
        }
        return false;
    }

    private void checkApple() {
        Vector2i head = this.snake.getFirst();
        if (head.equals(this.apple)) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_GENERIC_EAT, 1));
            do {
                this.apple = new Vector2i(random.nextInt(MAX_X + 1), random.nextInt(MAX_Y + 1));
            } while (this.snake.contains(this.apple));
        } else {
            this.snake.removeLast();
        }
    }

    private boolean setDirection(Direction direction) {
        if (this.lastMoved == direction.getOpposite()) {
            return false;
        }
        this.direction = direction;
        return true;
    }
}
