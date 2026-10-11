package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The initializer of an Unmake drink, Pulser's square borrowed: exactly one
 * bright green square ring for each block picked, leaving the glove small and
 * flying to the block's near face over the pick's nine ticks, growing to the
 * face's own size as it lands at full brightness, the block standing as
 * itself the while and nothing of its stream showing; when the square lands
 * the block destabilises and its stream flows back. Drawn after the
 * translucent blocks with the glow lines Pulser's rings use.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkSquares {

    /** The square's colour: bright green. */
    static final int GREEN = 0x3AFF2A;
    /** The square's alpha, held at full brightness the whole flight, Pulser's peak. */
    static final int ALPHA = 230;
    /** The square's half side as it leaves the hand, in blocks, Pulser's own. */
    static final double HAND_RADIUS = SignalRings.HAND_RADIUS;
    /** The square's half side as it lands, the block face's own. */
    static final double FACE_RADIUS = DrinkBody.MOUTH;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DrinkSquares() {
    }

    /**
     * One square in flight.
     *
     * @param center where its middle is
     * @param axis   the unit direction it faces along, from the hand to the face
     * @param radius its half side, in blocks
     */
    record Flight(Vec3 center, Vec3 axis, double radius) {
    }

    /**
     * Draws every square in flight this frame.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        List<Flight> flights = flightsOf(mc, level, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (flights.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * SignalRings.WIDTH_SCALE;
        for (Flight flight : flights) {
            lines.emitPolyline(camera, SignalRings.RingShape.SQUARE.points(flight.center(), flight.axis(),
                    flight.radius()), ARGB.color(ALPHA, GREEN), width);
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    /**
     * @param mc          the client
     * @param level       the client level
     * @param partialTick the partial tick
     * @return every square in flight: one for each picked block whose square has not yet landed
     */
    private static List<Flight> flightsOf(Minecraft mc, ClientLevel level, float partialTick) {
        double now = level.getGameTime() + partialTick;
        List<Flight> flights = new ArrayList<>();
        for (ClientDrinks.Drink drink : ClientDrinks.CLIENT.live(now, id -> true)) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker == null) {
                continue;
            }
            Vec3 glove = DrinkRenderer.gloveOf(mc, drinker, partialTick);
            for (DrinkPayload.Streaming streaming : drink.streaming()) {
                if (now < streaming.start()) {
                    flights.add(flightOf(glove, nearFaceOf(streaming.pos(), glove),
                            shareOf(streaming.picked(), streaming.start(), now)));
                }
            }
        }
        return flights;
    }

    /**
     * @param pos   a picked block
     * @param glove the glove
     * @return the point of the block's near face the square lands on: the point of the block nearest the glove
     */
    static Vec3 nearFaceOf(BlockPos pos, Vec3 glove) {
        return new Vec3(Math.clamp(glove.x, pos.getX(), pos.getX() + 1.0),
                Math.clamp(glove.y, pos.getY(), pos.getY() + 1.0),
                Math.clamp(glove.z, pos.getZ(), pos.getZ() + 1.0));
    }

    /**
     * @param picked the game time the block was picked, the square leaving the hand
     * @param start  the game time the square lands
     * @param now    the game time, with the partial tick
     * @return how far the square has flown, 0 at the hand to 1 landed
     */
    static double shareOf(long picked, long start, double now) {
        return Math.clamp((now - picked) / Math.max(1, start - picked), 0, 1);
    }

    /**
     * @param share how far the square has flown
     * @return its half side there: small at the hand, the face's own as it lands
     */
    static double radiusAt(double share) {
        return HAND_RADIUS + share * (FACE_RADIUS - HAND_RADIUS);
    }

    /**
     * @param glove the glove
     * @param face  the point of the block's near face the square lands on
     * @param share how far the square has flown
     * @return the square's flight: on the line from the glove to the face, facing along it
     */
    static Flight flightOf(Vec3 glove, Vec3 face, double share) {
        Vec3 line = face.subtract(glove);
        return new Flight(glove.lerp(face, share), line.lengthSqr() > 0 ? line.normalize() : UP, radiusAt(share));
    }
}
