package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.SpikesRisePayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * Spikes goo's strike as this client sees it: one rigid chrome triangular
 * pyramid shoots up out of the goo under the struck thing and fully through
 * it, its tip standing out over the thing's head, holds, and sinks back into
 * the goo on the retract frame metal_spikes.json names, sounding the retract
 * shink as it goes. The pyramid keeps its size and moves, rather than
 * stretching. Client-only: the server's strike already landed its damage and
 * its shink out.
 * urchin-rests-bristled-and-spears
 * urchin-spikes-shink-out-and-shink-back
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SpikesRise {

    /** Ticks the spikes take to shoot up into the struck thing. */
    static final int SHOOT_TICKS = 2;
    /** Ticks the spikes take to sink back into the goo. */
    static final int RETRACT_TICKS = 6;
    /** How far the impaling pyramid's tip stands out over the struck thing's head, in blocks. */
    static final float OVER_THE_HEAD = 0.4f;
    /** The impaling pyramid's base radius, as a share of the struck thing's width. */
    private static final float BASE_SHARE = 0.4f;
    /** The thinnest the impaling pyramid's base stands, in blocks, so a small thing is still run through. */
    private static final float LEAST_BASE_RADIUS = 0.16f;
    /** Ticks the chrome band takes to sweep down a spike once. */
    private static final float SWEEP_TICKS = 20f;
    /** The smoothstep's cubic coefficients, 3t^2 - 2t^3. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    private static final List<Rise> RISES = new ArrayList<>();

    private SpikesRise() {
    }

    /**
     * Starts a strike's spikes rising under the thing it struck.
     *
     * @param payload the strike
     * @param context the payload context
     */
    public static void handle(SpikesRisePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                RISES.add(new Rise(payload, level.getGameTime()));
            }
        });
    }

    /**
     * How far out the spikes stand through a strike, as a share of their
     * reach: easing up over the shoot, held until the retract frame, then
     * easing back into the goo.
     *
     * @param age          the strike's age in ticks, with the partial tick
     * @param retractFrame the age the spikes start retracting
     * @return the share in [0, 1], zero before the strike and once retracted
     */
    static float extension(float age, int retractFrame) {
        if (age <= 0f || age >= retractFrame + RETRACT_TICKS) {
            return 0f;
        }
        if (age < SHOOT_TICKS) {
            return BurnoutGeometry.easeOutCubic(age / SHOOT_TICKS);
        }
        if (age < retractFrame) {
            return 1f;
        }
        float retracted = (age - retractFrame) / RETRACT_TICKS;
        return 1f - retracted * retracted * (SMOOTH_SQUARE - SMOOTH_CUBE * retracted);
    }

    /**
     * Sounds each strike's retract shink on its retract frame and drops the
     * strikes whose spikes have retracted.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            RISES.clear();
            return;
        }
        long now = level.getGameTime();
        for (Rise rise : RISES) {
            if (!rise.shinkedBack && now - rise.startedAt >= rise.payload.retractFrame()) {
                rise.shinkedBack = true;
                Entity struck = level.getEntity(rise.payload.struckId());
                Vec3 at = struck != null ? struck.position() : Vec3.atCenterOf(rise.payload.cell());
                SoundPlays.playLocally(level, at, rise.payload.retractShink());
            }
        }
        RISES.removeIf(rise -> now - rise.startedAt >= rise.payload.retractFrame() + RETRACT_TICKS);
    }

    /**
     * Draws every strike's spikes rising out of the goo under the thing it
     * struck, in Metal's chrome, while the cell and the thing both stand.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || RISES.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Frame frame = new Frame(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.METAL_CHROME_TYPE),
                mc.gameRenderer.getMainCamera().position(), partial,
                ColorBytes.toByte(Mth.frac((float) (now / SWEEP_TICKS))));
        for (Rise rise : RISES) {
            drawRise(level, rise, extension((float) (now - rise.startedAt), rise.payload.retractFrame()), frame);
        }
        buffers.endBatch(GooRenderTypes.METAL_CHROME_TYPE);
    }

    /**
     * Draws one strike's pyramid risen its share of the way, where any of it
     * stands out of the goo and the cell and the struck thing both still stand.
     *
     * @param level the client's level
     * @param rise  the strike
     * @param share how far the pyramid has risen, {@link #extension}
     * @param frame what this frame draws with
     */
    private static void drawRise(ClientLevel level, Rise rise, float share, Frame frame) {
        Entity struck = level.getEntity(rise.payload.struckId());
        BlockState cell = level.getBlockState(rise.payload.cell());
        if (share <= 0f || struck == null || !SpikesBristle.isSpikes(cell)) {
            return;
        }
        double gooTop = SpikesBristle.gooTop(rise.payload.cell(), cell);
        Vec3 feet = struck.getPosition(frame.partial());
        float height = impaleHeight(gooTop, feet.y + struck.getBbHeight());
        Vec3 base = new Vec3(feet.x, gooTop - (1f - share) * height, feet.z);
        float radius = Math.max(LEAST_BASE_RADIUS, struck.getBbWidth() * BASE_SHARE);
        SpikesBristle.emitPyramid(frame.pose(), frame.chrome(), relative(base, frame.camera()), height, radius,
                frame.sweep());
    }

    /**
     * How tall the impaling pyramid stands: from the goo's surface through
     * the struck thing to past the top of its head.
     *
     * @param gooTop  the goo surface's height
     * @param headTop the top of the struck thing's body
     * @return the pyramid's height in blocks
     */
    static float impaleHeight(double gooTop, double headTop) {
        return (float) (Math.max(0.0, headTop - gooTop) + OVER_THE_HEAD);
    }

    private static Vector3f relative(Vec3 point, Vec3 camera) {
        return new Vector3f((float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z));
    }

    /**
     * What one frame draws every strike's spikes with.
     *
     * @param pose    the pose, camera-relative
     * @param chrome  Metal's chrome vertex consumer
     * @param camera  the camera's position
     * @param partial the partial tick
     * @param sweep   the chrome band's place, a byte
     */
    private record Frame(PoseStack.Pose pose, VertexConsumer chrome, Vec3 camera, float partial, int sweep) {
    }

    /** One strike's spikes this client draws, from the tick its payload arrived. */
    private static final class Rise {
        private final SpikesRisePayload payload;
        private final long startedAt;
        private boolean shinkedBack;

        Rise(SpikesRisePayload payload, long startedAt) {
            this.payload = payload;
            this.startedAt = startedAt;
        }
    }
}
