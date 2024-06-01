package net.earthcomputer.clientcommands.features;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;

public class VillagerRNGSim {
    public static VillagerRNGSim INSTANCE = new VillagerRNGSim();
    static VillagerRNGSim TenTicksBefore = new VillagerRNGSim();
    static boolean tenTicksBeforeSet = false;
    static int tenTickIndex = 0;
    static int[] tenTickRandomCalls = new int[10];
    static long[] tenTickSeeds = new long[10];
    static long[] tenTickLastAmbient = new long[10];
    static long[] tenTickLastAmethyst = new long[10];
    
    LegacyRandomSource random = new LegacyRandomSource(0);

    int errorCount = 0;
    long lastAmbient = 0;
    boolean lastAmbientCracked = false;
    boolean justAmbient = false;
    long lastAmethyst = 0;
    static long amethystInterval = 0;
    int ticksToWait = 0;
    boolean synced = false;

    long tickCounter = 0;

    public void onAmethyst(ClientboundSoundPacket packet) {
        var lastChimeIntensity1_2 = (packet.getVolume() - 0.1f);
        var nextFloat = (packet.getPitch() - 0.5f) / lastChimeIntensity1_2;

        var forSync = clone();
        var ticks = 0;
        var predicted = forSync.nextFloat();
        while(Math.abs(nextFloat - predicted) > 0.0001 && ticks++ < 30) {
            predicted = forSync.nextFloat();
        }

        assert Minecraft.getInstance().level != null;
        amethystInterval = lastAmethyst;
        lastAmethyst = tickCounter;
        amethystInterval = lastAmethyst - amethystInterval;

        synced = false;
        if(ticks < 30) {
            setSeed(forSync.getSeed());
            justAmbient = forSync.justAmbient;
            lastAmbient = forSync.lastAmbient;
            ticksToWait = forSync.ticksToWait;
            lastAmethyst = forSync.lastAmethyst;
            tenTickLastAmethyst[tenTickIndex] = lastAmethyst;

            errorCount = 0;
            if(ticks == 0) {
                synced = true;
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.synced"), false);
            } else {
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.maintain"), false);
            }
            Minecraft.getInstance().gui.overlayMessageTime = 20;
        } else {
            ticksToWait = 10;
            errorCount++;
            Minecraft.getInstance().gui.overlayMessageTime = 0;
            if(errorCount > 4) {
                errorCount = 0;
                CCrackVillager.cracked = false;
                lastAmbientCracked = false;
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.maintainFail"), false);
            }
        }

    }

    public void onAmbient() {
        if(!lastAmbientCracked) {
            if(!CCrackVillager.cracked) return;

            lastAmbient = -80;
            nextFloat();
            nextFloat();
            justAmbient = true;
        }

        if(justAmbient) return;

        var forSync = clone();
        var ticks = 0;
        while(!forSync.justAmbient) {
            ticks++;
            forSync.onTick(true);
        }

        if(ticks < 30) {
            setSeed(forSync.getSeed());
            justAmbient = forSync.justAmbient;
            lastAmbient = forSync.lastAmbient;
            ticksToWait = forSync.ticksToWait;
            lastAmethyst = forSync.lastAmethyst;;
        } else {
            ticksToWait = 10;
        }
    }

    public long getSeed() {
        return random.seed.get();
    }

    public void setSeed(long seed) {
        random.setSeed(seed ^ 25214903917L);
    }

    public void onTick() {
        onTick(false);
    }

    public void onTick(boolean sim) {
        if(ticksToWait-- > 0) {
            return;
        }
        tickCounter++;

        if(sim && tickCounter - lastAmethyst >= amethystInterval) {
            nextFloat();
        }

        if(CCrackVillager.cracked) {
            tenTickIndex++;
            tenTickIndex %= 10;
            tenTickRandomCalls[tenTickIndex] = 0;
            tenTickSeeds[tenTickIndex] = getSeed();
        }

        if(nextInt(1000) < lastAmbient++ && CCrackVillager.cracked) {
            nextFloat();
            nextFloat();
            lastAmbient = -80;
            lastAmbientCracked = true;
            justAmbient = true;
        } else {
            justAmbient = false;
        }
        if(CCrackVillager.cracked) {
            tenTickLastAmbient[tenTickIndex] = lastAmbient;
        }
        nextInt(100);

        if(CCrackVillager.cracked && !sim && CCrackVillager.targetEnchantment != null && synced) {
            var player = Minecraft.getInstance().player;
            var villager = CCrackVillager.targetVillager.get();
            if(player == null || player.distanceTo(villager) > 5) return;
            var simulate = clone();
            var connection = Minecraft.getInstance().getConnection();
            if(connection != null) {
                var info = connection.getPlayerInfo(player.getScoreboardName());
                if(info != null) {
                    CCrackVillager.interval = info.getLatency() / 50;
                }
            }
            for(var i = 0; i < CCrackVillager.interval; i++) {
                simulate.onTick(true);
            }
            var offers = simulate.predictOffers();
            if(offers == null) return;
            for(var offer : offers) {
                if(CCrackVillager.targetEnchantment.test(offer.getResult())) {
                    assert Minecraft.getInstance().gameMode != null;
                    Minecraft.getInstance().gameMode.interact(player, villager, InteractionHand.MAIN_HAND);
                    var chat = Minecraft.getInstance().gui.getChat();
                    chat.addMessage(Component.literal("I found it !"));
                    CCrackVillager.targetEnchantment = null;
                    break;
                }
            }
        }
    }

    void randomCalled() {
        if(this == INSTANCE) {
            tenTickRandomCalls[tenTickIndex]++;
        }
    }

    float nextFloat() {
        randomCalled();
        return random.nextFloat();
    }

    int nextInt(int bound) {
        randomCalled();
        return random.nextInt(bound);
    }

    public List<MerchantOffer> predictOffers() {
        var villager = CCrackVillager.targetVillager.get();
        if(villager == null) return null;
        var map = VillagerTrades.TRADES.get(villager.getVillagerData().getProfession());
        if(map == null || map.isEmpty()) return null;
        var items = map.get(villager.getVillagerData().getLevel());
        if(items == null) return null;
        var itemList = Lists.newArrayList(items);
        List<MerchantOffer> offers = new ArrayList<>();
        var i = 0;
        while (i < 2) {
            MerchantOffer offer = itemList.remove(nextInt(itemList.size())).getOffer(villager, this.random);
            if (offer == null) continue;
            offers.add(offer);
            ++i;
        }
        return offers;
    }

    public void onOfferTrades() {
        var chat = Minecraft.getInstance().gui.getChat();
        chat.addMessage(Component.literal("client pre-trade: " + Long.toHexString(getSeed())));

        for(var offer : predictOffers()) {

            var first = offer.getCostA();
            var second = offer.getCostB();
            var result = offer.getResult();
            var offerString = "%dx %s".formatted(first.getCount(), first.getHoverName().getString());
            if(!second.isEmpty()) {
                offerString += " + %dx %s".formatted(second.getCount(), second.getDisplayName().getString());
            }
            offerString += " => %dx %s".formatted(result.getCount(), result.getHoverName().getString());
            if(result.is(Items.ENCHANTED_BOOK)) {
                var enchantments = EnchantmentHelper.getEnchantmentsForCrafting(result);
                var enchantment = enchantments.keySet().iterator().next().value();
                offerString += " with %s".formatted(enchantment.getFullname(EnchantmentHelper.getItemEnchantmentLevel(enchantment, result)).getString());
            }
            chat.addMessage(Component.literal(offerString));
        }

        chat.addMessage(Component.literal("client post-trade: " + Long.toHexString(getSeed())));

    }

    public void syncOffers(ClientboundMerchantOffersPacket packet) {
        if(CCrackVillager.cracked) {
            var offers = packet.getOffers();
            var tick = 0;
            while (tick < 10) {
                var player = Minecraft.getInstance().player;
                var villager = CCrackVillager.targetVillager.get();
                if(player == null || player.distanceTo(villager) > 5) return;
                var simulate = clone();
                var index = (tenTickIndex-tick+10) % 10;
                simulate.setSeed(tenTickSeeds[index]);
                var predictedOffers = simulate.predictOffers();
                if(predictedOffers == null) return;
                var match = true;
                for(var i = 0; i < predictedOffers.size(); i++) {
                    if(!checkItem(offers.get(i).getCostA(), predictedOffers.get(i).getCostA())) {
                        match = false;
                        break;
                    }
                }
                if(match) {
                    break;
                }
                tick++;
            }
            if(tick > 0 && tick < 10) {
                CCrackVillager.interval = tick;
                var chat = Minecraft.getInstance().gui.getChat();
                chat.addMessage(Component.translatable("commands.ccrackvillager.syncWithLag", tick));
            }
        }
    }

    EnchantmentInstance getEnchantment(ItemStack stack) {

        var enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if(enchantmentsForCrafting.isEmpty()) return null;
        var holder = enchantmentsForCrafting.keySet().iterator().next();
        return new EnchantmentInstance(holder.value(), EnchantmentHelper.getItemEnchantmentLevel(holder.value(), stack));
    }

    boolean checkItem(ItemStack stack1, ItemStack stack2){
        if(stack1.getItem() == stack2.getItem() && stack1.getCount() == stack2.getCount()) {
            var enchantment1 = getEnchantment(stack1);
            var enchantment2 = getEnchantment(stack2);
            if((enchantment1 == null) == (enchantment2 == null)) {
                return enchantment1 == null
                        || (enchantment1.enchantment == enchantment2.enchantment && enchantment1.level == enchantment2.level);
            }
            return false;
        }
        return false;

    }

    @Override
    public VillagerRNGSim clone() {
        var result = new VillagerRNGSim();
        result.setSeed(getSeed());
        result.lastAmbient = lastAmbient;
        result.lastAmethyst = lastAmethyst;
        result.lastAmbientCracked = lastAmbientCracked;
        result.justAmbient = justAmbient;
        
        return result;
    }
}