package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
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
 * Spikes goo's strike as this client sees it: chrome spikes, drawn with the
 * original spike geometry, shoot up out of the goo under the struck thing
 * into its body, hold, and retract into the goo on the retract frame
 * metal_spikes.json names, sounding the retract shink as they go; the goo
 * lies flat between strikes. Client-only: the server's strike already
 * landed its damage and its shink out.
 * urchin-rests-bristled-and-spears
 * urchin-spines-return-to-the-original-spikes
 * urchin-spikes-shink-out-and-shink-back
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SpikesRise {

    /** Ticks the spikes take to shoot up into the struck thing. */
    static final int SHOOT_TICKS = 2;
    /** Ticks the spikes take to sink back into the goo. */
    static final int RETRACT_TICKS = 6;
    /** How far from the struck thing's center the ring of spikes around the middle one stands, as a share of its width. */
    private static final float RING_SHARE = 0.35f;
    /** The spikes rising around the middle one, evenly round the ring. */
    private static final int RING_SPIKES = 4;
    /** The struck thing's body center, as a share of its height over its feet. */
    private static final float BODY_CENTER = 0.5f;
    /** Ticks the chrome band takes to sweep down a spike once. */
    private static final float SWEEP_TICKS = 20f;
    private static final double TWO_PI = 2 * Math.PI;
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
     * Draws one strike's spikes at their share of their reach, where any
     * stand out and the cell and the struck thing both still stand.
     *
     * @param level the client's level
     * @param rise  the strike
     * @param share how far out its spikes stand, {@link #extension}
     * @param frame what this frame draws with
     */
    private static void drawRise(ClientLevel level, Rise rise, float share, Frame frame) {
        Entity struck = level.getEntity(rise.payload.struckId());
        BlockState cell = level.getBlockState(rise.payload.cell());
        if (share <= 0f || struck == null || !(cell.getBlock() instanceof VolumeCellBlock)) {
            return;
        }
        double gooTop = rise.payload.cell().getY() + (double) VolumeCellBlock.layersOf(cell) / VolumeCellBlock.MAX_LAYERS;
        Vec3 feet = struck.getPosition(frame.partial());
        Vector3f aim = relative(feet.add(0, struck.getBbHeight() * BODY_CENTER, 0), frame.camera());
        float ring = struck.getBbWidth() * RING_SHARE;
        emitSpike(frame, relative(new Vec3(feet.x, gooTop, feet.z), frame.camera()), aim, share);
        for (int spike = 0; spike < RING_SPIKES; spike++) {
            double angle = TWO_PI * spike / RING_SPIKES;
            Vec3 base = new Vec3(feet.x + ring * Math.cos(angle), gooTop, feet.z + ring * Math.sin(angle));
            emitSpike(frame, relative(base, frame.camera()), aim, share);
        }
    }

    /**
     * Emits one spike from its base on the goo toward the struck thing's
     * body center, out its share of the way there and past it.
     *
     * @param frame what this frame draws with
     * @param base  the spike's base on the goo, camera-relative
     * @param aim   the struck thing's body center, camera-relative
     * @param share how far out the spike stands
     */
    private static void emitSpike(Frame frame, Vector3f base, Vector3f aim, float share) {
        float reach = aim.distance(base) + SpikesSpear.OVERSHOOT;
        SpikesSpear.emit(frame.pose(), frame.chrome(), base, aim, reach * share, frame.sweep());
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
