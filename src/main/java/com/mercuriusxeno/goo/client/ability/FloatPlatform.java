package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Float's platform: under every floating mob in sight, flat mint squares,
 * Pulser's square rings laid level, leave the mob's feet one after another,
 * dropping a short way and widening as they fade, like an emitter pushing the
 * mob up. The whole platform fades over the float's last ticks.
 * float-blob-levitates-the-mob
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FloatPlatform {

    /** Squares in flight at once, evenly staggered along the drop. */
    static final int SQUARES = 4;
    /** Game ticks a square takes from the feet to the bottom of its drop. */
    static final double FLIGHT_TICKS = 20.0;
    /** How far below the feet a square has dropped when it vanishes, in blocks. */
    static final double DROP = 0.6;
    /** A square's half side as it leaves the feet, as a share of the mob's half width. */
    static final double START_REACH = 0.8;
    /** A square's half side as it vanishes, as a share of the mob's half width. */
    static final double END_REACH = 1.6;
    /** How many times the window's line width a square draws at. */
    static final float WIDTH_SCALE = 2f;
    /** Typhoon's mint, its wheel color. */
    private static final int MINT_RGB = 0xD5F5E3;
    private static final float PEAK_ALPHA = 220f;
    private static final double HALF = 0.5;
    private static final double SIGHT_RANGE = 48.0;

    private FloatPlatform() {
    }

    /**
     * Draws each floating mob's platform after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float gameTime = mc.level.getGameTime() + partialTick;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !living.isInvisible()
                    && living.hasData(GooAttachments.FLOATING)) {
                drawPlatform(lines, living, camera, width, partialTick, gameTime);
            }
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static void drawPlatform(LineContext lines, LivingEntity living, Vec3 camera, float width,
                                     float partialTick, float gameTime) {
        float ticksLeft = living.getData(GooAttachments.FLOATING).expiresAt() - gameTime;
        Vec3 feet = living.getPosition(partialTick);
        if (ticksLeft > 0f && feet.distanceToSqr(camera) <= SIGHT_RANGE * SIGHT_RANGE) {
            drawSquares(lines, feet, living.getBbWidth() * HALF, camera, width, gameTime, ticksLeft);
        }
    }

    private static void drawSquares(LineContext lines, Vec3 feet, double halfWidth, Vec3 camera, float width,
                                     float gameTime, float ticksLeft) {
        for (int square = 0; square < SQUARES; square++) {
            double share = dropShare(gameTime, square);
            int alpha = squareAlpha(share, ticksLeft);
            if (alpha > 0) {
                lines.emitPolyline(camera, SignalRings.RingShape.SQUARE.points(squareCenter(feet, share), Vec3.Y_AXIS,
                        halfSide(share, halfWidth)), ARGB.color(alpha, MINT_RGB), width);
            }
        }
    }

    /**
     * A square's alpha: Pulser's fade in and out along the drop, scaled down
     * over the float's last ticks.
     *
     * @param share     the share of the drop
     * @param ticksLeft the game ticks until the float ends
     * @return the alpha, 0 to 255
     */
    static int squareAlpha(double share, float ticksLeft) {
        return Math.round(PEAK_ALPHA * MobAilments.strength(ticksLeft) * SignalRings.opacity(share));
    }

    /**
     * A square's center along its drop, straight below the feet.
     *
     * @param feet  the mob's feet
     * @param share the share of the drop
     * @return the center
     */
    static Vec3 squareCenter(Vec3 feet, double share) {
        return feet.subtract(0, share * DROP, 0);
    }

    /**
     * How far along its drop a square is: the squares share one clock, each a
     * fixed share of the drop behind the one before.
     *
     * @param gameTime the game time including the partial tick
     * @param square   the square's index, 0 to {@link #SQUARES} less one
     * @return the share of the drop, from 0 at the feet to just under 1 at the bottom
     */
    static double dropShare(double gameTime, int square) {
        double share = gameTime / FLIGHT_TICKS + (double) square / SQUARES;
        return share - Math.floor(share);
    }

    /**
     * A square's half side along its drop, widening from just inside the
     * mob's footprint to well past it.
     *
     * @param share     the share of the drop
     * @param halfWidth the mob's half width, in blocks
     * @return the half side, in blocks
     */
    static double halfSide(double share, double halfWidth) {
        return halfWidth * (START_REACH + share * (END_REACH - START_REACH));
    }
}
