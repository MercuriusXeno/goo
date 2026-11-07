package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws Knives' spread fan while right click holds an armed press, in place
 * of the blob line: the cone the release would sling at the current charge,
 * rooted at the hand the knives leave, in the goo's highlight color at the
 * aim line's quiet alpha.
 * knives-aims-a-spread-fan-not-a-blob-line
 * aim-line-draws-quieter-so-the-dart-reads
 */
final class SpreadFanRenderer {

    /** Blocks across the ground the fan reaches when nothing is aimed at. */
    private static final double FREE_REACH = 12;

    private SpreadFanRenderer() {
    }

    /**
     * Whether the fan shows: the selected delivery slings a fan and right
     * click holds an armed press.
     *
     * @param delivery the selected delivery
     * @param armed    whether right click holds an armed press
     * @return true while a fan-slinging press is held
     */
    static boolean showsFan(@Nullable Delivery delivery, boolean armed) {
        return armed && delivery != null && delivery.slingsAFan();
    }

    /**
     * Draws the fan for this frame.
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param delivery     the selected delivery, one slinging a fan
     * @param aimEnd       where the aim ends, or null when nothing is aimed at
     * @param rgb          the goo's highlight color
     * @param partialTick  the frame's partial tick
     */
    static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera,
                       Delivery delivery, @Nullable Vec3 aimEnd, int rgb, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        float yaw = player.getViewYRot(partialTick);
        Vec3 hand = KnifeRain.handPoint(camera.position(), yaw, player.getViewVector(partialTick),
                KnifeRainSling.gloveOnTheRight(player));
        SpreadFan.Fan fan = SpreadFan.of(hand, yaw, player.getViewXRot(partialTick), delivery.charge(),
                delivery.chargeShare(GloveUseTracker.heldTicks()), reachOf(hand, aimEnd, player.getY()));
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        int color = ArcRenderer.computeGlowPassColor(rgb, 0, ArcRenderer.AIM_LINE_GLOW);
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        for (List<Vec3> line : List.of(fan.leftEdge(), fan.center(), fan.rightEdge())) {
            ctx.emitPolyline(camera.position(), line.toArray(Vec3[]::new), color, width);
        }
        for (List<Vec3> rib : fan.ribs()) {
            ctx.emitPolyline(camera.position(), rib.toArray(Vec3[]::new), color, width);
        }
        bufferSource.endLastBatch();
    }

    /**
     * Where the fan stops: at the aimed point's height and distance, or at
     * the player's feet a free reach ahead when nothing is aimed at.
     *
     * @param hand   the point the knives leave from
     * @param aimEnd where the aim ends, or null
     * @param feetY  the player's feet height
     * @return the reach
     */
    private static SpreadFan.FanReach reachOf(Vec3 hand, @Nullable Vec3 aimEnd, double feetY) {
        if (aimEnd == null) {
            return new SpreadFan.FanReach(feetY, FREE_REACH);
        }
        return new SpreadFan.FanReach(aimEnd.y, Math.hypot(aimEnd.x - hand.x, aimEnd.z - hand.z));
    }
}
