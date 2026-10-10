package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While right click holds a glow ability and glow goo is on hand, the goo in
 * the local player's glove lights up: it draws full bright, and a golden
 * shell of light, added onto what lies behind it, breathes around it. The
 * glove's renderer draws the shell in the hand's own pass, so the glove
 * never hides it. It fades in as the hold starts and out as it ends, so the
 * holder sees the channel running even while no wisp appears.
 * decision radiant-wisps-where-light-is-low
 * operator rulings 2026-10-10: a light glow around the hand marks a held glow channel;
 * the first glow, drawn in the world at the hand, sat behind the glove and never showed
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GloveGlow {

    /** Ticks the glow takes to fade fully in, and to fade fully out. */
    static final int FADE_TICKS = 5;
    /** The inner shell's half-size over the goo's, and its alpha at full strength. */
    static final float INNER_SCALE = 1.45f;
    private static final int INNER_ALPHA = 120;
    /** The outer shell's half-size over the goo's, and its alpha at full strength. */
    static final float OUTER_SCALE = 2.3f;
    private static final int OUTER_ALPHA = 45;
    private static final int INNER_RGB = 0xFFF0A8;
    private static final int OUTER_RGB = 0xFFD84A;
    /** How much the shells breathe, as a share of their size, and how fast, in radians a tick. */
    private static final float BREATH = 0.1f;
    private static final float BREATH_PACE = 0.2f;

    /** Ticks the glow has faded in so far, zero to {@link #FADE_TICKS}, now and the tick before. */
    private static int faded;
    private static int fadedBefore;

    private GloveGlow() {
    }

    /**
     * Fades the glow one tick toward shown while a glow hold runs, and toward hidden otherwise.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        fadedBefore = faded;
        faded = nextFade(faded, player != null && holdsGlow(player));
    }

    /**
     * The fade a tick moves to: one step toward full while the hold runs, one toward none otherwise.
     *
     * @param faded   ticks faded in now
     * @param holding whether a glow hold runs
     * @return ticks faded in after the tick
     */
    static int nextFade(int faded, boolean holding) {
        return Mth.clamp(holding ? faded + 1 : faded - 1, 0, FADE_TICKS);
    }

    /**
     * Whether right click holds a glow selection with glow goo on hand.
     *
     * @param player the local player
     * @return true while a glow hold runs
     */
    private static boolean holdsGlow(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return selection != null && selection.getGooType() == GooTypes.GLOW && GloveUseTracker.runsHeld(player);
    }

    /**
     * How strongly a glove glows this frame: the local player's held glove
     * by its fade, any other glove not at all.
     *
     * @param stack the glove being drawn
     * @return the strength, zero to one
     */
    public static float strengthFor(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (faded == 0 && fadedBefore == 0 || player == null || !inHand(stack, player)) {
            return 0f;
        }
        return Mth.lerp(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), fadedBefore, faded) / FADE_TICKS;
    }

    /**
     * Whether a glove being drawn is the one in either of the player's hands.
     *
     * @param stack  the glove being drawn
     * @param player the local player
     * @return true when either hand holds it
     */
    private static boolean inHand(ItemStack stack, LocalPlayer player) {
        return heldBy(stack, player.getMainHandItem()) || heldBy(stack, player.getOffhandItem());
    }

    /**
     * Whether a glove being drawn is the one in a hand: the same stack, or
     * one alike in item and components, should the draw hold a copy.
     *
     * @param stack the glove being drawn
     * @param held  the stack in the hand
     * @return true when they match
     */
    private static boolean heldBy(ItemStack stack, ItemStack held) {
        return stack == held || ItemStack.isSameItemSameComponents(stack, held);
    }

    /**
     * Emits the glow's two shells about the pose's origin, the goo's center.
     *
     * @param pose     the pose at the goo's center
     * @param consumer the vertex consumer, position and color
     * @param gooHalf  the goo's half-size
     * @param strength how strongly it glows, zero to one
     * @param time     the game clock in ticks, which the breath follows
     */
    public static void emitShells(PoseStack.Pose pose, VertexConsumer consumer, float gooHalf, float strength,
                                  float time) {
        float breath = 1f + BREATH * Mth.sin(time * BREATH_PACE);
        WispRenderer.emitCube(pose, consumer, gooHalf * OUTER_SCALE * breath,
                ARGB.color(Math.round(OUTER_ALPHA * strength), OUTER_RGB));
        WispRenderer.emitCube(pose, consumer, gooHalf * INNER_SCALE * breath,
                ARGB.color(Math.round(INNER_ALPHA * strength), INNER_RGB));
    }
}
