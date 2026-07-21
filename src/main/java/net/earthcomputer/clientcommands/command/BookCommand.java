package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.earthcomputer.clientcommands.util.MultiVersionCompat;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.arguments.LongArgumentType.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.*;

public class BookCommand {
    private static final SimpleCommandExceptionType NO_BOOK = new SimpleCommandExceptionType(Component.translatable("commands.cbook.commandException"));
    private static final SimpleCommandExceptionType TITLE_TOO_LONG = new SimpleCommandExceptionType(Component.translatable("commands.cbook.titleTooLong", WrittenBookContent.TITLE_MAX_LENGTH));

    private static final int DEFAULT_LIMIT = 50;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cbook")
            .then(literal("sign")
                .executes(ctx -> signBook(ctx.getSource(), ""))
                .then(argument("title", greedyString())
                    .executes(ctx -> signBook(ctx.getSource(), getString(ctx, "title")))))
            .then(literal("fill")
                .requires(_ -> MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_15)
                .executes(ctx -> fillBook(ctx.getSource(), fill(), DEFAULT_LIMIT))
                .then(argument("limit", integer(0, getMaxLimit()))
                    .executes(ctx -> fillBook(ctx.getSource(), fill(), getInteger(ctx, "limit")))))
            .then(literal("random")
                .requires(_ -> MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_15)
                .executes(ctx -> fillBook(ctx.getSource(), random(new Random()), DEFAULT_LIMIT))
                .then(argument("limit", integer(0, getMaxLimit()))
                    .executes(ctx -> fillBook(ctx.getSource(), random(new Random()), getInteger(ctx, "limit")))
                    .then(argument("seed", longArg())
                        .executes(ctx -> fillBook(ctx.getSource(), random(new Random(getLong(ctx, "seed"))), getInteger(ctx, "limit"))))))
            .then(literal("ascii")
                .requires(_ -> MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_15)
                .executes(ctx -> fillBook(ctx.getSource(), ascii(new Random()), DEFAULT_LIMIT))
                .then(argument("limit", integer(0, getMaxLimit()))
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
        LocalPlayer player = source.getPlayer();

        HeldBook book = getHeldWritableBook(player);

        String joinedPages = characterGenerator.limit((long) getMaxLimit() * 210)
            .mapToObj(i -> String.valueOf((char) i))
            .collect(Collectors.joining());

        List<String> pages = new ArrayList<>(limit);
        List<Filterable<String>> filterablePages = new ArrayList<>();

        for (int pageIndex = 0; pageIndex < limit; pageIndex++) {
            String page = joinedPages.substring(pageIndex * 210, (pageIndex + 1) * 210);
            pages.add(page);
            filterablePages.add(Filterable.passThrough(page));
        }

        book.stack().set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(filterablePages));
        player.connection.send(new ServerboundEditBookPacket(book.slot(), pages, Optional.empty()));

        source.sendFeedback(Component.translatable("commands.cbook.success"));

        return Command.SINGLE_SUCCESS;
    }

    private static int signBook(FabricClientCommandSource source, String title) throws CommandSyntaxException {
        LocalPlayer player = source.getPlayer();

        if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
            throw TITLE_TOO_LONG.create();
        }

        HeldBook book = getHeldWritableBook(player);
        WritableBookContent content = book.stack().get(DataComponents.WRITABLE_BOOK_CONTENT);
        List<String> pages = content == null ? List.of() : content.getPages(false).toList();

        player.connection.send(new ServerboundEditBookPacket(book.slot(), pages, Optional.of(title)));
        source.sendFeedback(Component.translatable("commands.cbook.success"));

        return Command.SINGLE_SUCCESS;
    }

    private static HeldBook getHeldWritableBook(LocalPlayer player) throws CommandSyntaxException {
        ItemStack heldItem = player.getMainHandItem();
        InteractionHand hand = InteractionHand.MAIN_HAND;
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            heldItem = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
            if (heldItem.getItem() != Items.WRITABLE_BOOK) {
                throw NO_BOOK.create();
            }
        }
        int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().getSelectedSlot() : Inventory.SLOT_OFFHAND;
        return new HeldBook(heldItem, slot);
    }

    private static int getMaxLimit() {
        return MultiVersionCompat.INSTANCE.getProtocolVersion() < MultiVersionCompat.V1_14 ? 50 : 100;
    }

    private record HeldBook(ItemStack stack, int slot) {
    }
}
