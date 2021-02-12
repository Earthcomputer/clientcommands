package net.earthcomputer.clientcommands.features;

import net.earthcomputer.clientcommands.TempRules;
import net.earthcomputer.clientcommands.render.RenderQueue;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.earthcomputer.clientcommands.features.PlayerRandCracker.throwItemsUntil;

public class SpeedManipulation {
    public static double speed = 0.1;
    public static void onSpeedManipEnabled() {
        TaskManager.addTask("speedManip", new SimpleTask() {
            @Override
            public boolean condition() {
                return TempRules.getSpeedManipulation() && MinecraftClient.getInstance().player != null;
            }

            @Override
            protected void onTick() {
                MinecraftClient MC = MinecraftClient.getInstance();
                if (speed >= 0) {
                    Vec3d v = MC.player.getVelocity();
                    MC.player.setVelocity(v.x * 1.5, v.y, v.z * 1.5);
                    v = MC.player.getVelocity();
                    double currentSpeed = Math.sqrt(Math.pow(v.x, 2) + Math.pow(v.z, 2));
                    double maxSpeed = speed;
                    if (currentSpeed > maxSpeed)
                        MC.player.setVelocity(v.x / currentSpeed * maxSpeed, v.y, v.z / currentSpeed * maxSpeed);
                } else {
                    Vec3d v = MC.player.getVelocity();
                    MC.player.setVelocity(v.x * -1.5, v.y, v.z * -1.5);
                    v = MC.player.getVelocity();
                    double currentSpeed = -(Math.sqrt(Math.pow(v.x, 2) + Math.pow(v.z, 2)));
                    double maxSpeed = speed;
                    if (currentSpeed < maxSpeed)
                        MC.player.setVelocity(v.x / currentSpeed * maxSpeed, v.y, v.z / currentSpeed * maxSpeed);
                }
                
            }
        });
    }
    public static void setSpeed(double SPEED) {
        speed = SPEED/10.0;
    }



}

