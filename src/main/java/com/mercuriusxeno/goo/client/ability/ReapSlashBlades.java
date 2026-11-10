package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.ReapSlashPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Reap's slashes on the client, the Crissaegrim's flurry: each a leaf
 * crescent blade, its bright edge leading and its tips trailing, sweeping out
 * from the slasher's chest along its heading, tilted at its own angle, and
 * thinning away once it has flown its reach.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ReapSlashBlades {

    /** Ticks a blade takes to thin away once it has flown its reach. */
    static final int FADE_TICKS = 3;
    /** The radius of the blade's arc, in blocks. */
    private static final double BLADE_RADIUS = 0.9;
    /** How far the arc sweeps either side of its leading point, in radians. */
    private static final double ARC_HALF = Math.toRadians(70);
    private static final double ARC_SPAN = 2 * ARC_HALF;
    private static final double QUARTER_TURN = Math.PI / 2;
    /** The share of the edge's brightness the blade's back glows at. */
    private static final float BODY_SHARE = 0.5f;
    /** Below this squared length a heading runs straight up or down and has no level side. */
    private static final double VERTICAL_HEADING = 1e-6;
    /** The blade's depth at its middle, tapering to nothing at its tips, in blocks. */
    private static final double BLADE_DEPTH = 0.35;
    /** The quads along the arc. */
    private static final int SEGMENTS = 12;
    /** The share of its size a blade leaves the chest at, growing to whole over its first ticks. */
    private static final double LAUNCH_SIZE = 0.4;
    private static final double GROW_TICKS = 2;
    /** The pale green the leading edge glows. */
    private static final int EDGE_RGB = 0xE4FFC4;
    private static final int OPAQUE = 0xFF;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private static final List<Blade> LIVE = new ArrayList<>();

    private ReapSlashBlades() {
    }

    /**
     * One blade in flight.
     *
     * @param origin      where it launches
     * @param direction   its unit heading
     * @param reach       how far it flies
     * @param flightTicks the ticks it takes to fly its reach
     * @param launchTick  the game tick it launches
     * @param roll        its tilt about its heading, in radians
     */
    record Blade(Vec3 origin, Vec3 direction, double reach, int flightTicks, long launchTick, double roll) {

        double ageAt(double time) {
            return time - launchTick;
        }

        boolean overAt(long tick) {
            return tick - launchTick >= flightTicks + FADE_TICKS;
        }

        /**
         * Where the blade's leading point is an age into its flight, easing out to its reach.
         *
         * @param age ticks since it launched, with the partial tick
         * @return the leading point
         */
        Vec3 leadAt(double age) {
            double share = Math.clamp(age / flightTicks, 0, 1);
            return origin.add(direction.scale(reach * BurnoutGeometry.easeOutCubic((float) share)));
        }

        /**
         * How bright the blade is an age into its flight: whole as it flies,
         * thinning away after.
         *
         * @param age ticks since it launched, with the partial tick
         * @return its strength, 0 to 1
         */
        float strengthAt(double age) {
            return 1f - (float) Math.clamp((age - flightTicks) / FADE_TICKS, 0, 1);
        }
    }

    /**
     * Starts a blade on the client thread.
     *
     * @param payload the slash
     * @param context the network context
     */
    public static void handle(ReapSlashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LIVE.add(new Blade(payload.origin(), payload.direction(), payload.reach(), payload.flightTicks(),
                        mc.level.getGameTime() + payload.delayTicks(), Math.toRadians(payload.roll())));
            }
        });
    }

    /**
     * Draws every live blade after translucent blocks, dropping each that has thinned away.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            return;
        }
        long tick = mc.level.getGameTime();
        LIVE.removeIf(blade -> blade.overAt(tick));
        if (LIVE.isEmpty()) {
            return;
        }
        double time = tick + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE));
        int leaf = ClientGooTypes.color(GooTypes.LEAF);
        for (Blade blade : LIVE) {
            double age = blade.ageAt(time);
            if (age >= 0) {
                drawBlade(quads, blade, age, camera, leaf);
            }
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /**
     * Draws one blade as a crescent: an arc whose leading edge glows pale and
     * whose body fades to Leaf's green behind it, deepest at its middle and
     * tapering to a point at each tip.
     *
     * @param quads  the context the quads emit through
     * @param blade  the blade
     * @param age    ticks since it launched, with the partial tick
     * @param camera the camera's position
     * @param leaf   Leaf's green
     */
    private static void drawBlade(FlatQuadContext quads, Blade blade, double age, Vec3 camera, int leaf) {
        Vec3 forward = blade.direction();
        Vec3 side = sideOf(forward, blade.roll());
        double size = Math.min(1, LAUNCH_SIZE + age / GROW_TICKS * (1 - LAUNCH_SIZE));
        Vec3 lead = blade.leadAt(age).subtract(camera);
        int alpha = Mth.floor(blade.strengthAt(age) * OPAQUE);
        int edge = ARGB.color(alpha, EDGE_RGB);
        int body = ARGB.color(Mth.floor(alpha * BODY_SHARE), leaf);
        Vec3 lastEdge = null;
        Vec3 lastBack = null;
        for (int segment = 0; segment <= SEGMENTS; segment++) {
            double angle = -ARC_HALF + ARC_SPAN * segment / SEGMENTS;
            Vec3 onArc = lead.add(side.scale(BLADE_RADIUS * size * Math.sin(angle)))
                    .add(forward.scale(BLADE_RADIUS * size * (Math.cos(angle) - 1)));
            double depth = BLADE_DEPTH * size * Math.cos(angle / ARC_HALF * QUARTER_TURN);
            Vec3 back = onArc.subtract(forward.scale(depth));
            if (lastEdge != null) {
                emitQuad(quads, lastEdge, onArc, back, lastBack, edge, body);
            }
            lastEdge = onArc;
            lastBack = back;
        }
    }

    /**
     * The axis a blade's arc spreads along: level across its heading, tilted
     * about the heading by its roll.
     *
     * @param forward the blade's unit heading
     * @param roll    its tilt, in radians
     * @return the unit side axis
     */
    private static Vec3 sideOf(Vec3 forward, double roll) {
        Vec3 level = forward.cross(UP);
        Vec3 flat = level.lengthSqr() > VERTICAL_HEADING ? level.normalize() : EAST;
        Vec3 rise = flat.cross(forward);
        return flat.scale(Math.cos(roll)).add(rise.scale(Math.sin(roll)));
    }

    private static void emitQuad(FlatQuadContext quads, Vec3 edgeA, Vec3 edgeB, Vec3 backB, Vec3 backA, int edge,
                                 int body) {
        quads.vertex((float) edgeA.x, (float) edgeA.y, (float) edgeA.z, edge);
        quads.vertex((float) edgeB.x, (float) edgeB.y, (float) edgeB.z, edge);
        quads.vertex((float) backB.x, (float) backB.y, (float) backB.z, body);
        quads.vertex((float) backA.x, (float) backA.y, (float) backA.z, body);
    }
}
