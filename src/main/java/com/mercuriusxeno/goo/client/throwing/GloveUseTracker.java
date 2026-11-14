package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.PalmBlobChargePayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side tracker for the glove press: resolves the use key through
 * {@link GloveInputGate}, previewing the ability's area while the key is
 * held and throwing when it comes up, or streaming from the press for a
 * stream, the glove arm posed by the ability, or swung for a blob thrown.
 * Auto-registered via EventBusSubscriber.
 * decision right-click-held-previews-release-throws
 * decision use-key-throw-swings-no-more
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveUseTracker {
    private static final GloveInputGate PRESS = new GloveInputGate();
    private static InteractionHand pressHand = InteractionHand.MAIN_HAND;
    /**
     * The face the cursor rested on when the live press began, which a
     * channel holds to for the press, or null where it rested on none
     * (decision flatten-disc-cursor-breaks-above-the-plane).
     */
    private static ChannelAim.@Nullable FacePlane pressPlane;

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
     * Advances the glove press, the goo availability check and the throw state each client tick.
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
        GooFlightManager.tick();
    }

    /** Clears hold and availability state when no player is present. */
    private static void resetState() {
        PRESS.cancel();
        selectedTypeAvailable = false;
    }

    /**
     * Starts a glove press: the glove's use reached the client with no
     * block or entity interaction taking the click.
     *
     * @param hand the hand holding the glove
     */
    public static void pressGlove(InteractionHand hand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && SpireCast.selected(player)) {
            // spire-rips-walls-and-platforms: a footprint cast holds its own presses, never a throw
            SpireCast.press(player, hand);
            return;
        }
        if (!PRESS.isArmed()) {
            pressHand = hand;
            pressPlane = crosshairPlane(Minecraft.getInstance());
        }
        PRESS.arm();
    }

    /**
     * The face the crosshair rests on.
     *
     * @param mc the client
     * @return the face plane, or null where the crosshair rests on no block
     */
    private static ChannelAim.@Nullable FacePlane crosshairPlane(Minecraft mc) {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? new ChannelAim.FacePlane(hit.getBlockPos(), hit.getDirection()) : null;
    }

    /**
     * The face the cursor rested on when the live press began.
     *
     * @return the face a channel holds to, or null where it rested on none
     */
    public static ChannelAim.@Nullable FacePlane pressPlane() {
        return pressPlane;
    }

    /**
     * Cancels the use key's turn, for either hand, while a glove press is
     * live, so a held channel uses no block and swings no hand; with no
     * press live the click passes to vanilla untouched.
     *
     * @param event the use-key interaction for one hand
     */
    @SubscribeEvent
    public static void onUseKeyInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        cancelUseKey(event, PRESS.holdsTheUseKey());
    }

    /**
     * Applies the cancel verdict to one use-key interaction.
     * decision diagnose-and-correct-block-use-during-a-held-channel
     * decision use-animation-only-when-goo-throws
     * decision use-key-throw-swings-no-more
     *
     * @param event the use-key interaction for one hand
     * @param armed whether a glove press is live
     */
    static void cancelUseKey(InputEvent.InteractionKeyMappingTriggered event, boolean armed) {
        if (cancelsUseKey(event.isUseItem(), armed)) {
            // The glove's use answers PASS, so vanilla never enters its using state and startUseItem
            // fires again every few ticks the use key stays down, using the aimed block before the
            // glove each time; cancelling the event stops startUseItem whole, for both hands.
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    /**
     * Whether a use-key interaction is cancelled.
     *
     * @param useItem whether the interaction is the use key's
     * @param armed   whether a glove press is live
     * @return true to cancel it
     */
    static boolean cancelsUseKey(boolean useItem, boolean armed) {
        return useItem && armed;
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
            if (PRESS.isArmed()) {
                GlovePoses.drop(player);
            }
            PRESS.cancel();
            return;
        }
        PRESS.tick(mc.options.keyUse.isDown(), pressActions(player));
        liftThePalmBlob(mc, player);
    }

    /**
     * Keeps the local player's palm blob lifted for a tick the press holds a
     * cast, and tells the server of a tick a charge holds, which sends
     * nothing else until it lets go, so the players watching see the lift.
     * every-ability-casts-from-the-lifted-palm-blob
     *
     * @param mc     the client
     * @param player the local player
     */
    private static void liftThePalmBlob(Minecraft mc, LocalPlayer player) {
        if (mc.level == null || !selectedTypeAvailable || !LiftedPalmBlob.pressLifts(PRESS)) {
            return;
        }
        LiftedPalmBlob.held(player.getId(), player.getEyePosition(), mc.level.getGameTime());
        if (PRESS.isPreviewing()) {
            player.connection.send(new ServerboundCustomPayloadPacket(PalmBlobChargePayload.INSTANCE));
        }
    }

    /**
     * What a glove press resolves to for the local player.
     *
     * @param player the local player
     * @return the throw, arm pose, stream and stream check the press runs
     */
    private static GloveInputGate.PressActions pressActions(LocalPlayer player) {
        return new LocalPress(player);
    }

    /**
     * The local player's glove press: its throw, arm pose and stream.
     *
     * @param player the local player
     */
    private record LocalPress(LocalPlayer player) implements GloveInputGate.PressActions {

        @Override
        public boolean sendThrow(int heldTicks) {
            return GloveThrowSender.sendThrow(player, heldTicks);
        }

        @Override
        public void holdPose() {
            GlovePoses.hold(player);
        }

        @Override
        public void releasePose() {
            GlovePoses.release(player, pressHand);
        }

        @Override
        public void dropPose() {
            GlovePoses.drop(player);
        }

        @Override
        public void hold() {
            GloveThrowSender.sendHold(player);
        }

        @Override
        public boolean runsWhileHeld() {
            return selectedRunsWhileHeld(player);
        }
    }

    /**
     * Whether the held glove's selection runs on every held tick, a stream or a channel.
     *
     * @param player the local player
     * @return true for a held selection
     */
    private static boolean selectedRunsWhileHeld(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return selection != null && GloveInputGate.runsFromPress(GloveThrowSender.selectedDelivery(selection.abilityId()),
                GloveThrowSender.selectedBadge(selection.abilityId()));
    }

    /**
     * Whether the held glove shows its ability's area: while a press previews
     * its throw, or while a stream runs.
     *
     * @return true while right click holds a live press
     */
    public static boolean showsArea() {
        return PRESS.isArmed();
    }

    /**
     * Whether a held ability's visual runs: right click holds it and the
     * selected goo is still on hand, as the stream tick it plays beside is
     * sent only then, so the fog, breeze, vortex and cursor stop with the goo.
     *
     * @param player the local player
     * @return true while the held ability runs
     */
    public static boolean runsHeld(LocalPlayer player) {
        return heldVisualRuns(showsArea(), checkSelectedTypeAvailable(player));
    }

    /**
     * Whether a held ability's visual runs, read from the press and the goo on hand.
     *
     * @param armed   whether right click holds a live press
     * @param gooLeft whether any of the selected goo is on hand
     * @return true only while both hold
     */
    static boolean heldVisualRuns(boolean armed, boolean gooLeft) {
        return armed && gooLeft;
    }

    /**
     * Whether a charge stands: a press that is not channeled previews with
     * the use key held.
     * abilities-not-channeled-charge-and-cancel-on-left-click
     *
     * @return true while a charge stands
     */
    public static boolean charging() {
        return PRESS.isPreviewing();
    }

    /**
     * Cancels the local player's charge standing, sending nothing.
     * abilities-not-channeled-charge-and-cancel-on-left-click
     *
     * @param player the local player
     */
    public static void cancelCharge(LocalPlayer player) {
        PRESS.cancelCharge(pressActions(player));
    }

    /**
     * The ticks the live press has held its preview, which a charged
     * ability's ghost reads (decision nova-ring-grows-with-the-hold).
     *
     * @return the held ticks, 0 while no press previews
     */
    public static int heldTicks() {
        return PRESS.heldTicks();
    }

    /**
     * The ticks the live press has run a held channel or stream, the first
     * counted 1, which Gouge's wind-up shake reads
     * (decision terrakinesis-shakes-the-blocks-before-the-tear).
     *
     * @return the ticks, 0 while no channel runs
     */
    public static int channelTicks() {
        return PRESS.channelTicks();
    }

    /**
     * The scatter the live press slings its knives by, which its aim lines
     * draw and its release sends.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @return the press's scatter seed
     */
    public static long scatterSeed() {
        return PRESS.scatterSeed();
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
