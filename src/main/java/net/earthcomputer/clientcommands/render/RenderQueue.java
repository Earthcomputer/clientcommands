package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.Function;

public class RenderQueue {
    private static int tickCounter = 0;
    private static final List<AddQueueEntry> addQueue = new ArrayList<>();
    private static final List<RemoveQueueEntry> removeQueue = new ArrayList<>();
    private static final EnumMap<Layer, Map<Object, Shape>> queue = new EnumMap<>(Layer.class);

    public static void add(Layer layer, Object key, Shape shape, int life) {
        addQueue.add(new AddQueueEntry(layer, key, shape, life));
    }

    public static void addCuboid(Layer layer, Object key, Vec3d from, Vec3d to, int color, int life) {
        add(layer, key, new Cuboid(from, to, color), life);
    }

    public static void addCuboid(Layer layer, Object key, Box cuboid, int color, int life) {
        add(layer, key, new Cuboid(cuboid, color), life);
    }

    public static void addLine(Layer layer, Object key, Vec3d from, Vec3d to, int color, int life) {
        add(layer, key, new Line(from, to, color), life);
    }

    public static void remove(Layer layer, Object key) {
        removeQueue.add(new RemoveQueueEntry(layer, key));
    }

    private static void doAdd(AddQueueEntry entry) {
        Map<Object, Shape> shapes = queue.computeIfAbsent(entry.layer(), k -> new LinkedHashMap<>());
        Shape oldShape = shapes.get(entry.key());
        if (oldShape != null) {
            entry.shape().prevPos = oldShape.prevPos;
        } else {
            entry.shape().prevPos = entry.shape().getPos();
        }
        entry.shape().deathTime = tickCounter + entry.life();
        shapes.put(entry.key(), entry.shape());
    }

    public static void tick() {
        for (RemoveQueueEntry entry : removeQueue) {
            Map<Object, Shape> shapes = queue.get(entry.layer());
            if (shapes != null) {
                shapes.remove(entry.key());
            }
        }
        removeQueue.clear();

        queue.values().forEach(shapes -> shapes.values().forEach(shape -> shape.prevPos = shape.getPos()));
        tickCounter++;
        for (AddQueueEntry entry : addQueue) {
            doAdd(entry);
        }
        addQueue.clear();
        for (Map<Object, Shape> shapes : queue.values()) {
            Iterator<Shape> itr = shapes.values().iterator();
            while (itr.hasNext()) {
                Shape shape = itr.next();
                if (tickCounter == shape.deathTime) {
                    itr.remove();
                }
                shape.tick();
            }
        }
    }

    public static void render(Layer layer, VertexConsumer vertexConsumer, MatrixStack matrixStack, float delta) {
        if (!queue.containsKey(layer)) {
            return;
        }
        queue.get(layer).values().forEach(shape -> shape.render(matrixStack, vertexConsumer, delta));
    }

    public enum Layer {
        ON_TOP
    }

    private record AddQueueEntry(Layer layer, Object key, Shape shape, int life) {}

    private record RemoveQueueEntry(Layer layer, Object key) {}

    public static RenderLayer noDepthLayer() {
        return NO_DEPTH_LAYER.apply(Unit.INSTANCE);
    }

    private static final Function<Unit, RenderLayer> NO_DEPTH_LAYER = Util.memoize($ -> {
        RenderLayer.MultiPhaseParameters multiPhaseParameters = RenderLayer.MultiPhaseParameters.builder()
            .program(RenderLayer.LINES_PROGRAM)
            .transparency(new RenderLayer.Transparency("translucent_transparency", () -> {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }, () -> {
                RenderSystem.disableBlend();
                RenderSystem.defaultBlendFunc();
            }))
            .writeMaskState(RenderLayer.COLOR_MASK)
            .cull(RenderLayer.DISABLE_CULLING)
            .depthTest(RenderLayer.ALWAYS_DEPTH_TEST)
            .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
            .lineWidth(new RenderLayer.LineWidth(OptionalDouble.of(2)))
            .build(true);
        return RenderLayer.of("clientcommands_no_depth", VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.LINES, 256, true, true, multiPhaseParameters);
    });
}
