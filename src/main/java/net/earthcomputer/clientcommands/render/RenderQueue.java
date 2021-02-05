package net.earthcomputer.clientcommands.render;

import net.minecraft.client.util.math.MatrixStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class RenderQueue {

    private static final HashMap<String, List<Consumer<MatrixStack>>> queue = new HashMap<>();

    public static void add(InjectLoc injectLoc, Consumer<MatrixStack> runnable) {
        if (!queue.containsKey(injectLoc.getLocation())) {
            queue.put(injectLoc.getLocation(), new ArrayList<>());
        }

        List<Consumer<MatrixStack>> runnableList = queue.get(injectLoc.getLocation());
        runnableList.add(runnable);
    }

    public static void remove(InjectLoc injectLoc, Consumer<MatrixStack> runnable) {
        if (!queue.containsKey(injectLoc.getLocation())) {
            return;
        }

        List<Consumer<MatrixStack>> runnableList = queue.get(injectLoc.getLocation());
        runnableList.remove(runnable);
    }

    private static void onRender(float delta, long time, MatrixStack matrixStack, String location) {
        if (matrixStack == null || !queue.containsKey(location)) return;
        queue.get(location).forEach(r -> r.accept(matrixStack));
    }

    public enum InjectLoc {
        HAND("hand"),
        CAMERA("camera"),
        ;
        private final String location;

        InjectLoc(String location) {
            this.location = location;
        }

        public void onRender(float delta, long time, MatrixStack matrixStack) {
            RenderQueue.onRender(delta, time, matrixStack, this.location);
        }

        public String getLocation() {
            return location;
        }
    }
}