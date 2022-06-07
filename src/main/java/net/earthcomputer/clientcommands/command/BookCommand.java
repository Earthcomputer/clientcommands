package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.multiconnect.api.MultiConnectAPI;
import net.earthcomputer.multiconnect.api.Protocols;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.c2s.play.BookUpdateC2SPacket;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.LongArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.*;

public class BookCommand {
    private static final SimpleCommandExceptionType NO_BOOK = new SimpleCommandExceptionType(new TranslatableText("commands.cbook.commandException"));

    private static final int MAX_LIMIT = WrittenBookItem.field_30933;
    private static final int DEFAULT_LIMIT = 50;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (MultiConnectAPI.instance().getProtocolVersion() >= Protocols.V1_15) {
            return; // chunk savestate fixed in 1.15
        }

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

    private static int fillBook(FabricClientCommandSource source, IntStream characterGenerator, int limit) throws CommandSyntaxException {
        ClientPlayerEntity player = source.getPlayer();
        assert player != null;

        ItemStack heldItem = player.getMainHandStack();
        Hand hand = Hand.MAIN_HAND;
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            heldItem = player.getOffHandStack();
            hand = Hand.OFF_HAND;
            if (heldItem.getItem() != Items.WRITABLE_BOOK) {
                throw NO_BOOK.create();
            }
        }
        int slot = hand == Hand.MAIN_HAND ? player.getInventory().selectedSlot : PlayerInventory.OFF_HAND_SLOT;

        String joinedPages = characterGenerator.limit(MAX_LIMIT * 210).mapToObj(i -> String.valueOf((char) i)).collect(Collectors.joining());

        List<String> pages = new ArrayList<>(limit);
        NbtList pagesNbt = new NbtList();

        for (int pageIndex = 0; pageIndex < limit; pageIndex++) {
            String page = joinedPages.substring(pageIndex * 210, (pageIndex + 1) * 210);
            pages.add(page);
            pagesNbt.add(NbtString.of(page));
        }

        heldItem.setSubNbt("pages", pagesNbt);
        player.networkHandler.sendPacket(new BookUpdateC2SPacket(slot, pages, Optional.empty()));

        source.sendFeedback(new TranslatableText("commands.cbook.success"));

        return 0;
    }
}
