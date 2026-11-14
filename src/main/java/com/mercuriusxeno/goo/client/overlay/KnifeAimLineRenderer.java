package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws Knives' aim while right click holds an armed press: a faint dashed line per knife the current charge slings, each
 * leaving the blob floating ahead of the hand and arcing the way its knife
 * flies until it would strike a block.
 * knives-aims-a-spread-fan-not-a-blob-line
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 * aim-line-draws-quieter-so-the-dart-reads
 */
final class KnifeAimLineRenderer {

    /** The knife lines' bloom: faint, since a press draws one per knife and they overlay near the hand. */
    static final DashedGlowLines.GlowPalette KNIFE_LINE_GLOW = new DashedGlowLines.GlowPalette(2, 20, 0.75f, 0.6f);

    private KnifeAimLineRenderer() {
    }

    /**
     * Whether the knife lines show: the selected delivery slings a fan and
     * right click holds an armed press.
     *
     * @param delivery the selected delivery
     * @param armed    whether right click holds an armed press
     * @return true while a fan-slinging press is held
     */
    static boolean showsLines(@Nullable Delivery delivery, boolean armed) {
        return armed && delivery != null && delivery.slingsAFan();
    }

    /**
     * Draws the knife lines for this frame.
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param delivery     the selected delivery, one slinging a fan
     * @param rgb          the goo's highlight color
     * @param partialTick  the frame's partial tick
     */
    static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera,
                       Delivery delivery, int rgb, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        KnifeAimLines.Strike blocks = (from, to) -> {
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
        };
        int heldTicks = GloveUseTracker.heldTicks();
        KnifeAimLines.Press press = new KnifeAimLines.Press(player.getEyePosition(partialTick),
                player.getViewYRot(partialTick), player.getViewXRot(partialTick),
                KnifeRainSling.gloveOnTheRight(player), heldTicks, delivery.chargeShare(heldTicks),
                GloveUseTracker.scatterSeed());
        List<Vec3[]> lines = KnifeAimLines.linesOf(press, delivery.charge(), blocks).stream()
                .map(line -> line.toArray(Vec3[]::new)).toList();
        DashedGlowLines.render(poseStack, bufferSource, camera, lines, rgb, partialTick, KNIFE_LINE_GLOW);
    }
}
