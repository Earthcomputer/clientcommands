package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

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
        source.getClient().tell(() -> source.getClient().setScreen(new SnakeGameScreen()));
        return Command.SINGLE_SUCCESS;
    }
}

class SnakeGameScreen extends Screen {

    private static final Minecraft client = Minecraft.getInstance();

    private static final ResourceLocation GRID_TEXTURE = new ResourceLocation("clientcommands:textures/snake_grid.png");

    private static final Random random = new Random();

    private static final int MAX_X = 16;
    private static final int MAX_Y = 16;

    private int tickCounter = 10;
    private Direction direction = Direction.EAST;
    private Direction lastMoved = Direction.EAST;
    private final LinkedList<Vector2i> snake = new LinkedList<>();
    private Vector2i apple;

    SnakeGameScreen() {
        super(Component.translatable("snakeGame.title"));
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int startX = (this.width - 289) / 2;
        int startY = (this.height - 289) / 2;

        context.drawString(client.font, this.title, startX, startY - 10, 0xff_ffffff);
        MutableComponent score = Component.translatable("snakeGame.score", this.snake.size());
        context.drawCenteredString(client.font, score, this.width / 2, startY - 10, 0xff_ffffff);

        context.blit(GRID_TEXTURE, startX, startY, 0, 0, 289, 289, 289, 289);
        int scaleX = MAX_X + 1;
        int scaleY = MAX_Y + 1;
        context.fill(startX + this.apple.x() * scaleX, startY + this.apple.y() * scaleY, startX + this.apple.x() * scaleX + scaleX, startY + this.apple.y() * scaleY + scaleY, 0xff_f52559);
        for (Vector2i vec : this.snake) {
            context.fill(startX + vec.x() * scaleX, startY + vec.y() * scaleY, startX + vec.x() * scaleX + scaleX, startY + vec.y() * scaleY + scaleY, 0xff_1f2df6);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client.options.keyUp.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_UP) {
            return this.setDirection(Direction.NORTH);
        } else if (client.options.keyLeft.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_LEFT) {
            return this.setDirection(Direction.WEST);
        } else if (client.options.keyDown.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_DOWN) {
            return this.setDirection(Direction.SOUTH);
        } else if (client.options.keyRight.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_RIGHT) {
            return this.setDirection(Direction.EAST);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void move() {
        Vector2i head = this.snake.getFirst();
        this.snake.addFirst(new Vector2i(head.x() + this.direction.getStepX(), head.y() + this.direction.getStepZ()));
        this.lastMoved = this.direction;
        if (this.checkGameOver()) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.OCELOT_DEATH, 1));
            this.onClose();
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
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EAT, 1));
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
