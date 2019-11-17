package net.earthcomputer.clientcommands.script;

import jdk.nashorn.api.scripting.ClassFilter;

import java.util.HashSet;
import java.util.Set;

public class ScriptClassFilter implements ClassFilter {

    private static final Set<String> ALLOWED_CLASSES = new HashSet<>();
    private static void allow(String clazz) {
        ALLOWED_CLASSES.add(clazz);
    }
    static {
        allow("java.util.List");
        allow("java.util.ArrayList");
        allow("java.util.Map");
        allow("java.util.HashMap");
        allow("java.util.Set");
        allow("java.util.HashSet");
        allow(ScriptBlockInfo.class.getName());
        allow(ScriptEntity.class.getName());
        allow(ScriptInventory.class.getName());
        allow(ScriptLivingEntity.class.getName());
        allow(ScriptPlayer.class.getName());
        allow(ScriptThread.class.getName());
        allow(ScriptWorld.class.getName());
    }

    @Override
    public boolean exposeToScripts(String s) {
        return ALLOWED_CLASSES.contains(s);
    }
}
