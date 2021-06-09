package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Hand;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.LongArgumentType.*;
import static net.earthcomputer.clientcommands.command.ClientCommandManager.*;
import static net.minecraft.server.command.CommandManager.*;

public class BookCommand {

    private static final SimpleCommandExceptionType NO_BOOK = new SimpleCommandExceptionType(new TranslatableText("commands.cbook.commandException"));

    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 50;

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        if (MultiConnectAPI.instance().getProtocolVersion() >= Protocols.V1_15) {
            return; // chunk savestate fixed in 1.15
        }

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
                                .then(argument("seed", longArg())
                                        .executes(ctx -> fillBook(ctx.getSource(), random(new Random(getLong(ctx, "seed"))), getInteger(ctx, "limit"))))))
                .then(literal("ascii")
                        .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random()), DEFAULT_LIMIT))
                        .then(argument("limit", integer(0, MAX_LIMIT))
                                .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random()), getInteger(ctx, "limit")))
                                .then(argument("seed", longArg())
                                        .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random(getLong(ctx, "seed"))), getInteger(ctx, "limit")))))));
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
        Hand hand = Hand.MAIN_HAND;
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            heldItem = player.getOffHandStack();
            hand = Hand.OFF_HAND;
            if (heldItem.getItem() != Items.WRITABLE_BOOK) {
                throw NO_BOOK.create();
            }
        }
        int slot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : 40;

        String joinedPages = characterGenerator.limit(MAX_LIMIT * 210).mapToObj(i -> String.valueOf((char) i)).collect(Collectors.joining());

        NbtList pages = new NbtList();

        for (int page = 0; page < limit; page++) {
            pages.add(NbtString.of(joinedPages.substring(page * 210, (page + 1) * 210)));
        }

        heldItem.getOrCreateTag().put("pages", pages);
        player.networkHandler.sendPacket(new BookUpdateC2SPacket(heldItem, false, slot));

        sendFeedback("commands.cbook.success");

        return 0;
    }

}
