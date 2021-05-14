package net.earthcomputer.clientcommands.command;

import com.google.common.collect.Iterables;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import net.earthcomputer.clientcommands.Page;
import net.earthcomputer.clientcommands.Paginator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.MinecraftClientGame;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class CHelpCommand {

    private static final SimpleCommandExceptionType FAILED_EXCEPTION = new SimpleCommandExceptionType(new TranslatableText("commands.help.failed"));

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        addClientSideCommand("chelp");

        dispatcher.register(literal("chelp")
            .executes(ctx -> {
                int cmdCount = 0;

                List<String> commandNames = new ArrayList<String>();
                for(CommandNode<ServerCommandSource> command : dispatcher.getRoot().getChildren()) {

                    String commandName = command.getName();

                    if(isClientSideCommand(commandName)) {
                        commandNames.add('/' + commandName);
                        cmdCount++;
                    }

                }

                MinecraftClient.getInstance().inGameHud.getChatHud().clear(false);

                Paginator<String> paginator = new Paginator<String>(commandNames, calculatePageSize());
                Page<String> page = paginator.getPage(1);

                for (int i = 0; i < page.items.size(); i++) {
                    sendFeedback(page.items.get(i));
                }

                sendFeedback(new LiteralText("")
                        .append(getCommandTextComponent(new TranslatableText("commands.chelp.paging.left"), String.format("/chelp %d", page.pageNumber - 1)))
                        .append(new TranslatableText("commands.chelp.paging.body", page.pageNumber, paginator.getPageCount()))
                        .append(getCommandTextComponent(new TranslatableText("commands.chelp.paging.right"), String.format("/chelp %d", page.pageNumber + 1))));

                return cmdCount;

            })
            .then(argument("page | command", greedyString())
                .executes(ctx -> {

                    String userInput = getString(ctx, "page | command");

                    if(isInteger(userInput)) {

                        int userPage = Integer.parseInt(userInput);
                        List<String> commandNames = new ArrayList<String>();

                        for(CommandNode<ServerCommandSource> command : dispatcher.getRoot().getChildren()) {
                            String commandName = command.getName();

                            if(isClientSideCommand(commandName)) {
                                commandNames.add('/' + commandName);
                            }
                        }

                        MinecraftClient.getInstance().inGameHud.getChatHud().clear(false);

                        Paginator<String> paginator = new Paginator<String>(commandNames, calculatePageSize());

                        if(!paginator.isValidPage(userPage)) {
                            sendFeedback(new TranslatableText("commands.chelp.paging.incorrect", userPage));
                            return 0;
                        }

                        Page<String> page = paginator.getPage(userPage);

                        for (int i = 0; i < page.items.size(); i++) {
                            sendFeedback(page.items.get(i));
                        }

                        sendFeedback(new LiteralText("")
                                .append(getCommandTextComponent(new TranslatableText("commands.chelp.paging.left"), String.format("/chelp %d", page.pageNumber - 1)))
                                .append(new TranslatableText("commands.chelp.paging.body", page.pageNumber, paginator.getPageCount()))
                                .append(getCommandTextComponent(new TranslatableText("commands.chelp.paging.right"), String.format("/chelp %d", page.pageNumber + 1))));
                        return page.items.size();
                    }
                    else
                    {
                        if (!isClientSideCommand(userInput))
                            throw FAILED_EXCEPTION.create();

                        ParseResults<ServerCommandSource> parseResults = dispatcher.parse(userInput, ctx.getSource());
                        if (parseResults.getContext().getNodes().isEmpty())
                            throw FAILED_EXCEPTION.create();

                        Map<CommandNode<ServerCommandSource>, String> usage = dispatcher.getSmartUsage(Iterables.getLast(parseResults.getContext().getNodes()).getNode(), ctx.getSource());
                        for (String u : usage.values()) {
                            sendFeedback(new LiteralText("/" + userInput + " " + u));
                        }

                        return usage.size();
                    }

                })));
    }

    public static int calculatePageSize(){
        int pageSize = 0;
        int guiScale = MinecraftClient.getInstance().options.guiScale;
        int visibleLines = MinecraftClient.getInstance().inGameHud.getChatHud().getVisibleLineCount();

        if(guiScale == 0) { // Auto gui scale
            guiScale = MinecraftClient.getInstance().getWindow().calculateScaleFactor(guiScale, true); // Calculate actual guiscale.
        }

        if(guiScale == 1 || guiScale == 2){
            pageSize = visibleLines - 1; // Leave space for page navigation.
        }else{
            pageSize = (visibleLines / 2) - 1; // If guiscale is larger than 2, then split pagesize in half and subtract 1 line for page navigation
        }

        return pageSize;
    }

    public static boolean isInteger(String input) {
        try {
            Integer.parseInt(input);
            return true;
        }catch(NumberFormatException e) {
            return false;
        }
    }

}
