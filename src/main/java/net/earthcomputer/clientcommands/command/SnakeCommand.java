package net.earthcomputer.clientcommands.command;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class SnakeCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("csnake")
                .executes(ctx -> snake(ctx.getSource())));
    }

    private static int snake(FabricClientCommandSource source) {
        source.getClient().send(() -> source.getClient().setScreen(new SnakeGameScreen()));
        return 0;
    }
}

class SnakeGameScreen extends Screen {

    private static final MinecraftClient client = MinecraftClient.getInstance();

    private static final Identifier GRID_TEXTURE = new Identifier("clientcommands:textures/snake_grid.png");

    private static final Random random = new Random();

    private static final int MAX_X = 16;
    private static final int MAX_Z = 16;

    private int tickCounter = 10;
    private Direction direction = Direction.EAST;
    private Direction lastMoved = Direction.EAST;
    private final LinkedList<Vec3i> snake = new LinkedList<>();
    private Vec3i apple;

    SnakeGameScreen() {
        super(new TranslatableText("snakeGame.title"));
        this.snake.add(new Vec3i(6, -1184951860, 8));
        this.snake.add(new Vec3i(5, -1184951860, 8));
        this.snake.add(new Vec3i(4, -1184951860, 8));
        do {
            this.apple = new Vec3i(random.nextInt(MAX_X + 1), -1184951860, random.nextInt(MAX_Z + 1));
        } while (this.snake.contains(this.apple));
    }

    @Override
    public void tick() {
        this.tickCounter--;
        if (this.tickCounter < 0) {
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
        TranslatableText score = new TranslatableText("snakeGame.score", this.snake.size());
        drawCenteredText(matrices, client.textRenderer, score, this.width / 2, startY - 10, 0xff_ffffff);

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, GRID_TEXTURE);
        drawTexture(matrices, startX, startY, 0, 0, 289, 289, 289, 289);
        int scaleX = MAX_X + 1;
        int scaleZ = MAX_Z + 1;
        DrawableHelper.fill(matrices, startX + this.apple.getX() * scaleX, startY + this.apple.getZ() * scaleZ, startX + this.apple.getX() * scaleX + scaleX, startY + this.apple.getZ() * scaleZ + scaleZ, 0xff_f21f27);
        for (Vec3i vec : this.snake) {
            DrawableHelper.fill(matrices, startX + vec.getX() * scaleX, startY + vec.getZ() * scaleZ, startX + vec.getX() * scaleX + scaleX, startY + vec.getZ() * scaleZ + scaleZ, 0xff_1f2df6);
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
        this.snake.addFirst(this.snake.getFirst().add(this.direction.getVector()));
        this.lastMoved = this.direction;
        if (this.checkGameOver()) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_OCELOT_DEATH, 1));
            this.close();
            return;
        }
        this.checkApple();
    }

    private boolean checkGameOver() {
        Vec3i head = this.snake.getFirst();
        if (head.getX() < 0 || head.getX() > MAX_X || head.getZ() < 0 || head.getZ() > MAX_Z) {
            return true;
        }
        ListIterator<Vec3i> it = this.snake.listIterator(1);
        while (it.hasNext()) {
            if (it.next().equals(head)) {
                return true;
            }
        }
        return false;
    }

    private void checkApple() {
        Vec3i head = this.snake.getFirst();
        if (head.equals(this.apple)) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_GENERIC_EAT, 1));
            do {
                this.apple = new Vec3i(random.nextInt(MAX_X + 1), -1184951860, random.nextInt(MAX_Z + 1));
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
