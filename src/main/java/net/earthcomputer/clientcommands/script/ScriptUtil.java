package net.earthcomputer.clientcommands.script;

import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import jdk.nashorn.api.scripting.ScriptUtils;
import net.minecraft.nbt.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Array;
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
        tag.getKeys().forEach(key -> map.put(key, fromNbt(tag.get(key))));
        return map;
    }

    public static Object fromNbtList(AbstractListTag<?> tag) {
        List<Object> list = new ArrayList<>(tag.size());
        tag.forEach(val -> list.add(fromNbt(val)));
        return list;
    }

    public static Tag toNbt(Object obj) {
        obj = normalizeNbtType(obj);
        if (obj instanceof Map) {
            //noinspection unchecked
            return mapToNbtCompound((Map<Object, Object>) obj);
        } else if (obj instanceof List) {
            //noinspection unchecked
            return listToNbtList((List<Object>) obj);
        } else if (obj instanceof String) {
            return StringTag.of((String) obj);
        } else if (obj instanceof Byte) {
            return ByteTag.of((Byte) obj);
        } else if (obj instanceof Short) {
            return ShortTag.of((Short) obj);
        } else if (obj instanceof Integer) {
            return IntTag.of((Integer) obj);
        } else if (obj instanceof Long) {
            return LongTag.of((Long) obj);
        } else if (obj instanceof Float) {
            return FloatTag.of((Float) obj);
        } else if (obj instanceof Double) {
            return DoubleTag.of((Double) obj);
        } else {
            throw new IllegalStateException("Don't know how to convert object of type " + obj.getClass() + " to NBT");
        }
    }

    private static Object normalizeNbtType(Object input) {
        if (input instanceof Map || input instanceof List || input instanceof Number || input instanceof String)
            return input;
        if (input.getClass().isArray()) {
            int len = Array.getLength(input);
            List<Object> ret = new ArrayList<>(len);
            for (int i = 0; i < len; i++)
                ret.add(Array.get(input, i));
            return ret;
        }
        if (input instanceof JSObject) {
            JSObject jsObj = (JSObject) input;
            if (jsObj.isArray()) {
                int len = ScriptUtil.asNumber(jsObj.getMember("length")).intValue();
                List<Object> ret = new ArrayList<>(len);
                for (int i = 0; i < len; i++)
                    ret.add(jsObj.getSlot(i));
                return ret;
            }
            if (jsObj.getClassName().equals("Object")) {
                Map<String, Object> properties = new HashMap<>();
                for (String key : jsObj.keySet())
                    properties.put(key, jsObj.getMember(key));
                return properties;
            }
            if (jsObj.getClassName().equals("Number")) {
                return jsObj.toNumber();
            }
        }
        return asString(input);
    }

    private static CompoundTag mapToNbtCompound(Map<Object, Object> map) {
        CompoundTag compound = new CompoundTag();
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            compound.put(asString(entry.getKey()), toNbt(entry.getValue()));
        }
        return compound;
    }

    private static byte getListType(List<Object> list) {
        byte type = 0;
        for (Object element : list) {
            element = normalizeNbtType(element);
            if (element instanceof Map) {
                return 10; // compound
            } else if (element instanceof List) {
                @SuppressWarnings("unchecked") byte sublistType = getListType((List<Object>)element);
                byte newType;
                if (sublistType == 1) // byte
                    newType = 7; // byte array
                else if (sublistType == 2 || sublistType == 3)
                    newType = 11; // int array
                else if (sublistType == 4)
                    newType = 12; // long array
                else
                    return 9; // list
                type = (byte) Math.max(type, newType);
            } else if (element instanceof Number) {
                byte newType;
                if (element instanceof Byte)
                    newType = 1; // byte
                else if (element instanceof Short)
                    newType = 2; // short
                else if (element instanceof Integer)
                    newType = 3; // int
                else if (element instanceof Long)
                    newType = 4; // long
                else if (element instanceof Float)
                    newType = 5; // float
                else
                    newType = 6; // double
                if (newType == 5) { // float
                    if (type <= 2) // byte, short
                        type = 5; // float
                    else
                        type = 6; // double
                } else if (newType == 6) // double
                    type = 6; // double
                else
                    type = (byte) Math.max(type, newType);
            } else if (element instanceof String) {
                return 8; // string
            }
        }
        return type;
    }

    private static AbstractListTag listToNbtList(List<Object> list) {
        byte type = getListType(list);
        AbstractListTag<?> listTag;
        if (type == 1) // byte
            listTag = new ByteArrayTag(new byte[list.size()]);
        else if (type == 2 || type == 3) // short, int
            listTag = new IntArrayTag(new int[list.size()]);
        else if (type == 4) // long
            listTag = new LongArrayTag(new long[list.size()]);
        else
            listTag = new ListTag();

        int index = 0;
        for (Object element : list) {
            element = normalizeNbtType(element);
            Tag elementTag = toNbt(element);
            if (type <= 4) { // integral number
                if (!(elementTag instanceof AbstractNumberTag))
                    throw new IllegalStateException();
                AbstractNumberTag num = (AbstractNumberTag) elementTag;
                if (type == 1) // byte
                    ((ByteArrayTag) listTag).getByteArray()[index] = num.getByte();
                else if (type == 2 || type == 3) // short, int
                    ((IntArrayTag) listTag).getIntArray()[index] = num.getInt();
                else if (type == 4) // long
                    ((LongArrayTag) listTag).getLongArray()[index] = num.getLong();
            } else if (type == 7 || type == 11 || type == 12) { // integral arrays
                if (!(elementTag instanceof AbstractListTag))
                    throw new IllegalStateException();
                AbstractListTag<?> converted;
                if (type == 7) { // byte array
                    if (elementTag instanceof ByteArrayTag) {
                        converted = (ByteArrayTag) elementTag;
                    } else if (elementTag instanceof IntArrayTag) {
                        int[] from = ((IntArrayTag) elementTag).getIntArray();
                        byte[] to = new byte[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (byte) from[i];
                        converted = new ByteArrayTag(to);
                    } else {
                        long[] from = ((LongArrayTag) elementTag).getLongArray();
                        byte[] to = new byte[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (byte) from[i];
                        converted = new ByteArrayTag(to);
                    }
                } else if (type == 11) { // int array
                    if (elementTag instanceof ByteArrayTag) {
                        byte[] from = ((ByteArrayTag) elementTag).getByteArray();
                        int[] to = new int[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new IntArrayTag(to);
                    } else if (elementTag instanceof IntArrayTag) {
                        converted = (IntArrayTag) elementTag;
                    } else {
                        long[] from = ((LongArrayTag) elementTag).getLongArray();
                        int[] to = new int[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = (int) from[i];
                        converted = new IntArrayTag(to);
                    }
                } else { // long array
                    if (elementTag instanceof ByteArrayTag) {
                        byte[] from = ((ByteArrayTag) elementTag).getByteArray();
                        long[] to = new long[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new LongArrayTag(to);
                    } else if (elementTag instanceof IntArrayTag) {
                        int[] from = ((IntArrayTag) elementTag).getIntArray();
                        long[] to = new long[from.length];
                        for (int i = 0; i < from.length; i++)
                            to[i] = from[i];
                        converted = new LongArrayTag(to);
                    } else {
                        converted = (LongArrayTag) elementTag;
                    }
                }
                ((ListTag) listTag).add(converted);
            } else {
                ((ListTag) listTag).add(elementTag);
            }
            index++;
        }
        return listTag;
    }

    public static String simplifyIdentifier(Identifier id) {
        if (id == null)
            return "null";
        if ("minecraft".equals(id.getNamespace()))
            return id.getPath();
        else
            return id.toString();
    }

    public static Object safeWrap(Object obj) {
        try {
            return ScriptUtils.wrap(obj);
        } catch (NoSuchMethodError e) {
            // hackfix for older Java 8 builds
            return ScriptObjectMirror.wrap(obj, getGlobalContext());
        } catch (IllegalArgumentException e) {
            return obj;
        }
    }

    public static boolean asBoolean(Object obj) {
        if (obj == null) return false;
        obj = safeWrap(obj);
        return (Boolean) ScriptUtils.convert(obj, Boolean.class);
    }

    public static Number asNumber(Object obj) {
        if (obj == null) return 0;
        obj = safeWrap(obj);
        if (obj instanceof JSObject) {
            return (Number) AbstractJSObject.getDefaultValue((JSObject) obj, Number.class);
        } else {
            return (Number) ScriptUtils.convert(obj, Number.class);
        }
    }

    public static String asString(Object obj) {
        if (obj == null) return null;
        obj = safeWrap(obj);
        if (obj instanceof JSObject) {
            return (String) AbstractJSObject.getDefaultValue((JSObject) obj, String.class);
        } else {
            return String.valueOf(obj);
        }
    }

    public static boolean isFunction(Object obj) {
        try {
            asFunction(obj);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static JSObject asFunction(Object obj) {
        obj = safeWrap(obj);
        if (obj instanceof JSObject && ((JSObject) obj).isFunction())
            return (JSObject) obj;
        throw new IllegalArgumentException("Cannot interpret " + obj + " as a function");
    }

    public static JSObject asObject(Object obj) {
        obj = safeWrap(obj);
        if (obj instanceof JSObject)
            return (JSObject) obj;
        throw new IllegalArgumentException("Cannot interpret " + obj + " as an object");
    }

    // unsafe function, should only be used for compatibility with older Java releases
    private static Object getGlobalContext() {
        try {
            return Class.forName("jdk.nashorn.internal.runtime.Context")
                    .getMethod("getGlobal")
                    .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to get global context", e);
        }
    }

    static Direction getDirectionFromString(String side) {
        if (side == null) {
            return null;
        }

        for (Direction dir : Direction.values()) {
            if (dir.name().equalsIgnoreCase(side)) {
                return dir;
            }
        }

        return null;
    }
}
