package net.earthcomputer.clientcommands.command;

import io.netty.buffer.Unpooled;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CommandBook extends ClientCommandBase {
    @Override
    public String getName() {
        return "cbook";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commands.cbook.usage";
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0)
            throw new WrongUsageException(getUsage(sender));

        if (!(sender instanceof EntityPlayerSP))
            throw new CommandException("commands.cbook.noPlayer");

        int limit = args.length > 1 ? parseInt(args[1], 1, 50) : 50;
        Random rand = args.length > 2 ? new Random(parseLong(args[2])) : new Random();

        EntityPlayerSP player = (EntityPlayerSP) sender;
        ItemStack heldItem = player.getHeldItemMainhand();
        if (heldItem.getItem() != Items.WRITABLE_BOOK) {
            throw new CommandException("commands.cbook.noBook");
        }

        IntStream characterGenerator;

        switch (args[0]) {
            case "fill":
                characterGenerator = IntStream.generate(() -> 0x10ffff);
                break;
            case "random":
                characterGenerator = rand.ints(0x80, 0x10ffff - 0x800).map(i -> i < 0xd800 ? i : i + 0x800);
                break;
            case "ascii":
                characterGenerator = rand.ints(0x20, 0x7f);
            default:
                throw new CommandException(getUsage(sender));
        }

        String joinedPages = characterGenerator.limit(50 * 210).mapToObj(i -> String.valueOf((char) i)).collect(Collectors.joining());

        NBTTagList pages = new NBTTagList();

        for (int page = 0; page < limit; page++) {
            pages.appendTag(new NBTTagString(joinedPages.substring(page * 210, (page + 1) * 210)));
        }

        if (heldItem.hasTagCompound()) {
            heldItem.getTagCompound().setTag("pages", pages);
        } else {
            heldItem.setTagInfo("pages", pages);
        }
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeItemStack(heldItem);
        player.connection.sendPacket(new CPacketCustomPayload("MC|BEdit", buf));

        sender.sendMessage(new TextComponentTranslation("commands.cbook.success"));
    }
}
