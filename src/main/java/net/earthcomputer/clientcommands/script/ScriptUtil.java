package net.earthcomputer.clientcommands.script;

import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptUtil {

    public static Object fromNbt(Tag tag) {
        if (tag instanceof CompoundTag) {
            return fromNbtCompound((CompoundTag) tag);
        } else if (tag instanceof AbstractListTag) {
            return fromNbtList((AbstractListTag<?>) tag);
        } else if (tag instanceof StringTag) {
            return tag.asString();
        } else if (tag instanceof LongTag) {
            return ((LongTag) tag).getLong();
        } else if (tag instanceof AbstractNumberTag) {
            return ((AbstractNumberTag) tag).getDouble();
        } else {
            throw new IllegalStateException("Unknown tag type " + tag.getType());
        }
    }

    public static Object fromNbtCompound(CompoundTag tag) {
        Map<String, Object> map = new HashMap<>(tag.getSize());
        tag.getKeys().forEach(key -> map.put(key, fromNbt(tag.getTag(key))));
        return map;
    }

    public static Object fromNbtList(AbstractListTag<?> tag) {
        List<Object> list = new ArrayList<>(tag.size());
        tag.forEach(val -> list.add(fromNbt(val)));
        return list;
    }

    public static String simplifyIdentifier(Identifier id) {
        if (id == null)
            return "null";
        if ("minecraft".equals(id.getNamespace()))
            return id.getPath();
        else
            return id.toString();
    }

    public static String asString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof JSObject) {
            return (String) AbstractJSObject.getDefaultValue((JSObject) obj, String.class);
        } else {
            return String.valueOf(obj);
        }
    }

}
