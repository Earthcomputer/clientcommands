package net.earthcomputer.clientcommands.script;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import jdk.nashorn.api.scripting.AbstractJSObject;
import jdk.nashorn.api.scripting.JSObject;
import jdk.nashorn.api.scripting.ScriptUtils;
import net.earthcomputer.clientcommands.command.ClientCommandManager;
import net.earthcomputer.clientcommands.command.ClientEntitySelector;
import net.earthcomputer.clientcommands.command.FakeCommandSource;
import net.earthcomputer.clientcommands.command.arguments.ClientEntityArgumentType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

class ScriptBuiltins {

    static void addBuiltinVariables(ScriptEngine engine) {
        engine.put("player", new ScriptPlayer());
        engine.put("world", new ScriptWorld());
        engine.put("$", (Function<String, Object>) command -> {
            StringReader reader = new StringReader(command);
            if (command.startsWith("@")) {
                try {
                    ClientEntitySelector selector = ClientEntityArgumentType.entities().parse(reader);
                    if (reader.getRemainingLength() != 0)
                        throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().createWithContext(reader);
                    List<Entity> entities = selector.getEntities(new FakeCommandSource(MinecraftClient.getInstance().player));
                    List<Object> ret = new ArrayList<>(entities.size());
                    for (Entity entity : entities)
                        ret.add(ScriptEntity.create(entity));
                    return ret;
                } catch (CommandSyntaxException e) {
                    throw new IllegalArgumentException("Invalid selector syntax", e);
                }
            }
            String commandName = reader.readUnquotedString();
            reader.setCursor(0);
            if (!ClientCommandManager.isClientSideCommand(commandName)) {
                ClientCommandManager.sendError(new TranslatableText("commands.client.notClient"));
                return 1;
            }
            return ClientCommandManager.executeCommand(reader, command);
        });
        engine.put("print", (Consumer<String>) message -> MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(new LiteralText(message)));
        engine.put("chat", (Consumer<String>) message -> MinecraftClient.getInstance().player.sendChatMessage(message));
        engine.put("tick", (Runnable) ScriptManager::passTick);

        engine.put("Thread", new AbstractJSObject() {
            @Override
            public Object newObject(Object... args) {
                if (args.length < 1 || args.length > 2)
                    throw new UnsupportedOperationException("new Thread() called with " + args.length + " arguments, but expected 1 or 2");
                JSObject action = ScriptUtil.asFunction(args[0]);
                boolean daemon = args.length < 2 || ScriptUtil.asBoolean(args[1]);
                return ScriptManager.createThread(() -> {
                        action.call(null);
                        return null;
                    }, daemon).handle;
            }

            @Override
            public Object getMember(String name) {
                return "current".equals(name) ? ScriptManager.currentThread().handle : super.getMember(name);
            }

            @Override
            public boolean hasMember(String name) {
                return "current".equals(name) || super.hasMember(name);
            }

            @Override
            public Set<String> keySet() {
                return Collections.singleton("current");
            }

            @Override
            public boolean isInstance(Object instance) {
                return instance instanceof ScriptThread;
            }
        });
        engine.put("BlockState", new AbstractJSObject() {
            @Override
            public Object getMember(String name) {
                return "defaultState".equals(name) ? (Function<String, ScriptBlockState>) ScriptBlockState::defaultState : null;
            }

            @Override
            public boolean hasMember(String name) {
                return "defaultState".equals(name) || super.hasMember(name);
            }

            @Override
            public Set<String> keySet() {
                return Collections.singleton("defaultState");
            }

            @Override
            public boolean isInstance(Object instance) {
                return instance instanceof ScriptBlockState;
            }
        });
        engine.put("ItemStack", new AbstractJSObject() {
            @Override
            public Object getMember(String name) {
                return "of".equals(name) ? (Function<Object, ScriptItemStack>) obj -> {
                    Tag itemNbt = ScriptUtil.toNbt(obj);
                    if (!(itemNbt instanceof CompoundTag)) {
                        Identifier itemId = new Identifier(ScriptUtil.asString(obj));
                        if (!Registry.ITEM.containsId(itemId))
                            throw new IllegalArgumentException("Cannot convert " + obj + " to item");
                        return new ScriptItemStack(new ItemStack(Registry.ITEM.get(itemId)));
                    }
                    ItemStack stack = ItemStack.fromTag((CompoundTag) itemNbt);
                    return new ScriptItemStack(stack);
                } : null;
            }

            @Override
            public boolean hasMember(String name) {
                return "of".equals(name) || super.hasMember(name);
            }

            @Override
            public Set<String> keySet() {
                return Collections.singleton("of");
            }

            @Override
            public boolean isInstance(Object instance) {
                return instance instanceof ScriptItemStack;
            }
        });
    }

}
