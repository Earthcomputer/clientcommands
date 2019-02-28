package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.network.packet.BookUpdateC2SPacket;
import net.minecraft.util.Hand;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;

public class BookCommand {

    private static final SimpleCommandExceptionType NO_BOOK = new SimpleCommandExceptionType(new LiteralMessage("You are not holding a book"));

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        addClientSideCommand("cbook");

        dispatcher.register(literal("cbook")
            .then(literal("fill")
                .executes(ctx -> fillBook(ctx.getSource(), fill(), DEFAULT_LIMIT))
                .then(argument("limit", integer(0, MAX_LIMIT))
                    .executes(ctx -> fillBook(ctx.getSource(), fill(), getInteger(ctx, "limit")))))
            .then(literal("random")
                .executes(ctx -> fillBook(ctx.getSource(), random(new Random()), DEFAULT_LIMIT))
                .then(argument("limit", integer(0, MAX_LIMIT))
                    .executes(ctx -> fillBook(ctx.getSource(), random(new Random()), getInteger(ctx, "limit")))
                    .then(argument("seed", string())
                        .executes(ctx -> fillBook(ctx.getSource(), random(new Random(parseLong(getString(ctx, "seed")))), getInteger(ctx, "limit"))))))
            .then(literal("ascii")
                .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random()), DEFAULT_LIMIT))
                .then(argument("limit", integer(0, MAX_LIMIT))
                    .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random()), getInteger(ctx, "limit")))
                    .then(argument("seed", string())
                        .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random(parseLong(getString(ctx, "seed")))), getInteger(ctx, "limit")))))));
    }

    private static IntStream fill() {
        return IntStream.generate(() -> 0x10ffff);
    }

    private static IntStream random(Random rand) {
        return rand.ints(0x80, 0x10ffff - 0x800).map(i -> i < 0xd800 ? i : i + 0x800);
    }

    private static IntStream ascii(Random rand) {
        return rand.ints(0x20, 0x7f);
    }

    private static int fillBook(CommandSource source, IntStream characterGenerator, int limit) throws CommandSyntaxException {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        ItemStack heldItem = player.getMainHandStack();
        Hand hand = Hand.MAIN;
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            heldItem = player.getOffHandStack();
            hand = Hand.OFF;
            if (heldItem.getItem() != Items.WRITABLE_BOOK) {
                throw NO_BOOK.create();
            }
        }

        String joinedPages = characterGenerator.limit(MAX_LIMIT * 210).mapToObj(i -> String.valueOf((char) i)).collect(Collectors.joining());

        ListTag pages = new ListTag();

        for (int page = 0; page < limit; page++) {
            pages.add(new StringTag(joinedPages.substring(page * 210, (page + 1) * 210)));
        }

        if (heldItem.hasTag()) {
            heldItem.getTag().put("pages", pages);
        } else {
            heldItem.setChildTag("pages", pages);
        }
        player.networkHandler.sendPacket(new BookUpdateC2SPacket(heldItem, false, hand));

        sendFeedback("commands.cbook.success");

        return 0;
    }

}
