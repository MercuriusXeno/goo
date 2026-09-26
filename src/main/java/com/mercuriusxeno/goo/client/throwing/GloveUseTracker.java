package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.radial.GloveRadialScreen;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side tracker for the glove press: counts the held use key
 * through {@link GloveInputGate}, throwing on a short release and opening
 * the radial at the threshold. Auto-registered via EventBusSubscriber.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveUseTracker {
    private static final GloveInputGate PRESS = new GloveInputGate();
    private static InteractionHand pressHand = InteractionHand.MAIN_HAND;

    /** How often (in ticks) to re-check whether the selected goo type is in inventory. */
    private static final int AVAILABILITY_CHECK_INTERVAL = 10;
    private static int availabilityTick;

    /**
     * True when the glove's selected goo type exists in the player's inventory.
     * Updated every {@link #AVAILABILITY_CHECK_INTERVAL} ticks to avoid per-frame
     * inventory scans. Used by the renderer and target highlighter to suppress
     * visuals when the player has depleted their goo without opening the radial.
     */
    private static boolean selectedTypeAvailable;

    private GloveUseTracker() {}


    /**
     * Whether the player's glove has goo of the selected type in inventory.
     *
     * @return true if selectedTypeAvailable
     */
    public static boolean isSelectedTypeAvailable() {
        return selectedTypeAvailable;
    }

    /**
     * Tracks glove hold duration each client tick, opening radial on threshold.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            resetState();
            return;
        }

        trackGloveHold(mc, player);
        tickAvailabilityCheck(player);
        BlobFlightManager.tick();
        GloveThrowSender.tick();
        ThrowFreezeState.tick();
    }

    /** Clears hold and availability state when no player is present. */
    private static void resetState() {
        PRESS.cancel();
        selectedTypeAvailable = false;
        ThrowFreezeState.clear();
    }

    /**
     * Starts a glove press: the glove's use reached the client with no
     * block or entity interaction taking the click.
     *
     * @param hand the hand holding the glove
     */
    public static void pressGlove(InteractionHand hand) {
        if (!PRESS.isArmed()) {
            pressHand = hand;
        }
        PRESS.arm();
    }

    /**
     * Cancels the offhand's turn at a right click while a main-hand glove
     * press is live, which the glove's PASS would otherwise hand it.
     *
     * @param event the use-key interaction for one hand
     */
    @SubscribeEvent
    public static void onUseKeyInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem() && event.getHand() == InteractionHand.OFF_HAND
                && PRESS.isArmed() && pressHand == InteractionHand.MAIN_HAND) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /**
     * Advances the glove press off the held use key; a screen or a hand
     * that no longer holds the glove drops it.
     *
     * @param mc     the client
     * @param player the local player
     */
    private static void trackGloveHold(Minecraft mc, LocalPlayer player) {
        if (mc.screen != null || !(player.getItemInHand(pressHand).getItem() instanceof GooGloveItem)) {
            PRESS.cancel();
            return;
        }
        PRESS.tick(mc.options.keyUse.isDown(), new GloveInputGate.PressActions() {
            @Override
            public boolean sendThrow() {
                return GloveThrowSender.sendThrow(player);
            }

            @Override
            public void swing() {
                player.swing(pressHand);
            }

            @Override
            public void openRadial() {
                GloveRadialScreen.open();
            }
        });
    }

    /**
     * Periodically re-checks whether the selected goo type is in inventory.
     * @param player the local player whose inventory is checked for goo availability
     */
    private static void tickAvailabilityCheck(LocalPlayer player) {
        availabilityTick++;
        if (availabilityTick >= AVAILABILITY_CHECK_INTERVAL) {
            availabilityTick = 0;
            selectedTypeAvailable = checkSelectedTypeAvailable(player);
        }
    }

    /**
     * Returns true if the player holds a glove with a selected type they have in inventory.
     *
     * @param player the interacting player
     * @return true if the player has at least 1 mB of the selected goo type
     */
    private static boolean checkSelectedTypeAvailable(LocalPlayer player) {
        ResourceKey<GooTypeDefinition> type = readSelectedType(player);
        return type != null && GooSourceScanner.hasEnough(player, type, 1);
    }

    /**
     * Reads the selected goo type from whichever hand holds a glove.
     *
     * @param player the interacting player
     * @return the selected goo type, or null if no glove is held or no type is selected
     */
    private static @Nullable ResourceKey<GooTypeDefinition> readSelectedType(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(main);
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof GooGloveItem) {
            return GooGloveItem.getSelectedType(off);
        }
        return null;
    }
}
