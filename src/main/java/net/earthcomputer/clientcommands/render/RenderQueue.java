package net.earthcomputer.clientcommands.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RenderQueue {
    private static int tickCounter = 0;
    private static final List<AddQueueEntry> addQueue = new ArrayList<>();
    private static final List<RemoveQueueEntry> removeQueue = new ArrayList<>();
    private static final EnumMap<Layer, Map<Object, Shape>> queue = new EnumMap<>(Layer.class);

    private static final RenderStateDataKey<EnumMap<Layer, List<Shape.RenderState>>> RENDER_STATES_KEY = RenderStateDataKey.create(() -> "clientcommands render queue");

    private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("clientcommands", "pipeline/lines_no_depth"))
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
            .build()
    );
    public static final RenderType LINES_NO_DEPTH_LAYER = RenderType.create(
        "clientcommands_no_depth",
        RenderSetup.builder(LINES_NO_DEPTH_PIPELINE)
            .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
            .createRenderSetup()
    );

    static {
        ClientTickEvents.START_CLIENT_TICK.register(RenderQueue::tick);

        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            EnumMap<Layer, List<Shape.RenderState>> renderStates = new EnumMap<>(Layer.class);
            queue.forEach((layer, shapes) -> {
                List<Shape.RenderState> states = new ArrayList<>();
                shapes.values().forEach(shape -> states.add(shape.extract(context)));
                renderStates.put(layer, states);
            });
            context.levelState().setData(RENDER_STATES_KEY, renderStates);
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(context -> render(Layer.ON_TOP, LINES_NO_DEPTH_LAYER, context));
    }

    public static void register() {
        // load class
    }

    public static void add(Layer layer, Object key, Shape shape, int life) {
        addQueue.add(new AddQueueEntry(layer, key, shape, life));
    }

    public static void addCuboid(Layer layer, Object key, Vec3 from, Vec3 to, int color, int life) {
        add(layer, key, new Cuboid(from, to, color), life);
    }

    public static void addCuboid(Layer layer, Object key, AABB cuboid, int color, int life) {
        add(layer, key, new Cuboid(cuboid, color), life);
    }

    public static void addLine(Layer layer, Object key, Vec3 from, Vec3 to, int color, int life) {
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

    private static void tick(Minecraft mc) {
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

    private static void render(Layer layer, RenderType renderType, LevelRenderContext context) {
        EnumMap<Layer, List<Shape.RenderState>> renderStates = context.levelState().getData(RENDER_STATES_KEY);
        if (renderStates == null) {
            return;
        }

        List<Shape.RenderState> states = renderStates.get(layer);
        if (states == null) {
            return;
        }

        for (Shape.RenderState state : states) {
            state.render(context, renderType);
        }
    }

    public static void submitCustomGeometry(
        SubmitNodeCollector collector,
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer renderer
    ) {
        if (renderType == LINES_NO_DEPTH_LAYER && collector.order(0) instanceof SubmitNodeCollection collection) {
            collection.afterTerrain.submit(new CustomFeatureRenderer.Submit(poseStack.last().copy(), renderType, renderer));
        } else {
            collector.submitCustomGeometry(poseStack, renderType, renderer);
        }
    }

    public enum Layer {
        ON_TOP
    }

    private record AddQueueEntry(Layer layer, Object key, Shape shape, int life) {}

    private record RemoveQueueEntry(Layer layer, Object key) {}
}
