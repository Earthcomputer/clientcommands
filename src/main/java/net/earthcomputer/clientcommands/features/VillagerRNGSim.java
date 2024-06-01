package net.earthcomputer.clientcommands.features;

import com.google.common.collect.Lists;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;

public class VillagerRNGSim {
    public static VillagerRNGSim INSTANCE = new VillagerRNGSim(); 
    
    LegacyRandomSource random = new LegacyRandomSource(0);

    int errorCount = 0;
    long lastAmbient = 0;
    boolean lastAmbientCracked = false;
    boolean justAmbient = false;
    long lastAmethyst = 0;
    int ticksToWait = 0;


    public void onAmethyst(ClientboundSoundPacket packet) {
        var lastChimeIntensity1_2 = (packet.getVolume() - 0.1f);
        var nextFloat = (packet.getPitch() - 0.5f) / lastChimeIntensity1_2;

        var forSync = clone();
        var ticks = 0;
        var predicted = forSync.random.nextFloat();
        while(Math.abs(nextFloat - predicted) > 0.0001 && ticks++ < 30) {
            predicted = forSync.random.nextFloat();
        }

        if(ticks < 30) {
            setSeed(forSync.getSeed());
            justAmbient = forSync.justAmbient;
            lastAmbient = forSync.lastAmbient;
            ticksToWait = forSync.ticksToWait;
            lastAmethyst = forSync.lastAmethyst;
            errorCount = 0;
            if(ticks == 0) {
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
            random.nextFloat();
            random.nextFloat();
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
        if(random.nextInt(1000) < lastAmbient++ && CCrackVillager.cracked) {
            random.nextFloat();
            random.nextFloat();
            lastAmbient = -80;
            lastAmbientCracked = true;
            justAmbient = true;
        } else {
            justAmbient = false;
        }
        random.nextInt(100);

        if(CCrackVillager.cracked && !sim && CCrackVillager.targetEnchantment != null) {
            var player = Minecraft.getInstance().player;
            var villager = CCrackVillager.targetVillager.get();
            if(player == null || player.distanceTo(villager) > 5) return;
            var simulate = clone();
            //simulate.onTick(true);
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
            MerchantOffer offer = itemList.remove(this.random.nextInt(itemList.size())).getOffer(villager, this.random);
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