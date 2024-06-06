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
    
    LegacyRandomSource random = new LegacyRandomSource(0);

    static List<MerchantOffer> nextOffers = new ArrayList<>();
    static List<Integer> nextOffersWithBooks = new ArrayList<>();

    int errorCount = 0;
    long lastAmbient = 0;
    boolean lastAmbientCracked = false;
    boolean justAmbient = false;
    long lastAmethyst = 0;
    static long amethystInterval = 0;
    int ticksToWait = 0;
    boolean synced = false;

    static long lastBruteForce = 0;
    static long bruteForceResult = 0;

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

            errorCount = 0;
            if(ticks == 0) {
                if((tickCounter - lastBruteForce > 500 || tickCounter > bruteForceResult + 10) && CCrackVillager.findingOffers) {
                    lastBruteForce = tickCounter;
                    bruteForce();
                }
                synced = true;
                if(CCrackVillager.findingOffers) {
                    Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.synced"), false);
                } else {
                    Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.syncedNotCracking"), false);
                }
            } else {
                Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("commands.ccrackvillager.maintain"), false);
            }
            Minecraft.getInstance().gui.overlayMessageTime = 20;
        } else {
            ticksToWait = 10;
            errorCount++;
            Minecraft.getInstance().gui.overlayMessageTime = 0;
            if(errorCount > 2) {
                errorCount = 0;
                CCrackVillager.cracked = false;
                lastAmbientCracked = false;
                lastBruteForce = 0;
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
            tickCounter = forSync.tickCounter;
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

        if(nextInt(1000) < lastAmbient++ && CCrackVillager.cracked) {
            nextFloat();
            nextFloat();
            lastAmbient = -80;
            lastAmbientCracked = true;
            justAmbient = true;
        } else {
            justAmbient = false;
        }
        nextInt(100);

        if(CCrackVillager.cracked && !sim && CCrackVillager.findingOffers && synced) {
            var player = Minecraft.getInstance().player;
            var villager = CCrackVillager.targetVillager.get();
            if(player == null || player.distanceTo(villager) > 5) return;
            var simulate = clone();
            for(var i = 0; i < CCrackVillager.interval; i++) {
                simulate.onTick(true);
            }
            var offers = simulate.predictOffers();
            if(offers == null) return;
            for(var offer : offers) {
                if(CCrackVillager.goalOffers.stream().anyMatch(goalOffer -> goalOffer.test(offer))) {
                    assert Minecraft.getInstance().gameMode != null;
                    printNextTrades();
                    Minecraft.getInstance().gameMode.interact(player, villager, InteractionHand.MAIN_HAND);
                    CCrackVillager.findingOffers = false;
                    break;
                }
            }
        }
    }

    void bruteForce() {
        var player = Minecraft.getInstance().player;
        var villager = CCrackVillager.targetVillager.get();
        if(player == null || player.distanceTo(villager) > 5) return;
        var simulate = clone();

        for(var i = 0; i < CCrackVillager.interval; i++) {
            simulate.onTick(true);
        }
        for(var step = 0; step < 600; step++) {
            simulate.onTick(true);
            var sim2 = simulate.clone();

            var offers = sim2.predictOffers();
            if(offers == null) return;
            for(var offer : offers) {
                for(var goal : CCrackVillager.goalOffers) {
                    if(goal.test(offer)) {
                        bruteForceResult = sim2.tickCounter;
                        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.match", step));
                        return;
                    }
                }
            }
        }
        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.noMatch"));
    }

    void randomCalled() {
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

    public void printNextTrades() {
        nextOffers.clear();
        nextOffersWithBooks.clear();
        for(var i = 0; i < 15; i++) {
            var sim = clone();
            for(var tick = 0; tick < i; tick++) {
                sim.onTick(true);
            }
            var offers = sim.predictOffers();
            var bookIndex = -1;
            for(var index = 0; index < 2; index++) {
                var offer = offers.get(index);
                nextOffers.add(offer);
                if(offer.getResult().is(Items.ENCHANTED_BOOK)) {
                    bookIndex = index;
                }
            }
            nextOffersWithBooks.add(bookIndex);
        }

    }

    EnchantmentInstance getEnchantment(ItemStack stack) {
        var enchantmentsForCrafting = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if(enchantmentsForCrafting.isEmpty()) return null;
        var holder = enchantmentsForCrafting.keySet().iterator().next();
        return new EnchantmentInstance(holder.value(), EnchantmentHelper.getEnchantmentsForCrafting(stack).getLevel(holder.value()));
    }

    public void syncOffer(ClientboundMerchantOffersPacket packet) {
        var offers = packet.getOffers();
        var hasBook = -1;
        for(var i = 0; i < 2; i++) {
            if(offers.get(i).getResult().is(Items.ENCHANTED_BOOK)) hasBook = i;
        }
        if(hasBook != -1) {
            for(var i = 0; i < nextOffers.size(); i+=2) {
                var i2 = nextOffersWithBooks.get(i/2);
                if(i2 != -1) {
                    if(i2 == hasBook && checkItem(offers.get(hasBook).getResult(), nextOffers.get(i+i2).getResult())) {
                        CCrackVillager.interval = i/2;
                        Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.syncWithLag", CCrackVillager.interval));
                        return;
                    }
                }
            }
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.maintainFail"));
        } else {
            for(var i = 0; i < nextOffers.size(); i+=2) {
                var same = true;
                for(var j = 0; j < 2; j++) {
                    var offer = nextOffers.get(i+j);
                    var offer2 = offers.get(j);
                    if(checkItem(offer.getResult(), offer2.getResult())
                            && checkItem(offer.getCostA(), offer2.getCostA())
                            && checkItem(offer.getCostB(), offer2.getCostB()))
                        continue;
                    same = false;
                }
                if(same) {
                    CCrackVillager.interval = i/2;
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.syncWithLag", CCrackVillager.interval));
                    return;
                }
            }
            Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("commands.ccrackvillager.maintainFail"));
        }
    }

    boolean checkItem(ItemStack stack1, ItemStack stack2){
        if(stack1.getItem() == stack2.getItem() && stack1.getCount() == stack2.getCount()) {
            var enchantment1 = getEnchantment(stack1);
            var enchantment2 = getEnchantment(stack2);
            if((enchantment1 == null) == (enchantment2 == null)) {
                if(enchantment1 == null) return true;
                return enchantment1.enchantment.getDescriptionId().equals(enchantment2.enchantment.getDescriptionId())
                        && enchantment1.level == enchantment2.level;
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
        result.tickCounter = tickCounter;
        
        return result;
    }
}