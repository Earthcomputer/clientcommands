package net.earthcomputer.clientcommands.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.command.CommandSource.suggestMatching;
import static net.minecraft.server.command.CommandManager.*;

public class NameHistory {

    private static final SimpleCommandExceptionType IO_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.cnamehistory.ioException"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("cnamehistory");

        LiteralCommandNode<ServerCommandSource> cnamehistory = dispatcher.register(literal("cnamehistory"));
        dispatcher.register(literal("cnamehistory")
                .then(argument("player", string())
                        .suggests(((context, builder) -> suggestMatching(MinecraftClient.getInstance().world.getPlayers().stream().map(abstractPlayer -> abstractPlayer.getName().getString()), builder)))
                        .executes(ctx -> getHistory(ctx.getSource(), getString(ctx, "player")))));
    }

    private static int getHistory(ServerCommandSource source, String player) throws CommandSyntaxException {
        String uuid;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + player);
            URLConnection request = url.openConnection();
            request.connect();
            uuid = (new JsonParser().parse(new InputStreamReader(request.getInputStream()))).getAsJsonObject().get("id").getAsString();
        } catch (Exception e) {
            throw IO_EXCEPTION.create();
        }
        JsonArray names;
        try {
            URL url = new URL("https://api.mojang.com/user/profiles/" + uuid + "/names");
            URLConnection request = url.openConnection();
            request.connect();
            names = (new JsonParser().parse(new InputStreamReader(request.getInputStream()))).getAsJsonArray();
        } catch (Exception e) {
            throw IO_EXCEPTION.create();
        }

        List<String> stringNames = new ArrayList<>();
        names.forEach(name -> stringNames.add(name.getAsJsonObject().get("name").getAsString()));
        sendFeedback(new TranslatableText("commands.cnamehistory.success", player, String.join(", ", stringNames)));
        return 0;
    }
}
