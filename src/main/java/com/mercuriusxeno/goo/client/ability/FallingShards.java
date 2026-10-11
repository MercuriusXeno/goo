package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * The glass shards falling from crystal taps on this client: each a slim
 * glass sliver, point down, dropping under gravity from the spigot to the
 * point it struck, turning slowly as it falls, and shattering there with a
 * tinkle of glass. A shard is smaller than the channel's flechettes.
 * decision shards-drip-falls-as-a-glass-shard
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class FallingShards {

    /** The client's falling shards. */
    public static final FallingShards CLIENT = new FallingShards();

    /** Blocks per tick squared a shard falls by, an item's gravity. */
    static final double GRAVITY = 0.08;
    /** The sliver's length tip to base, smaller than a flechette. */
    private static final float LENGTH = 0.45f;
    /** The sliver's base half-width. */
    private static final float HALF_WIDTH = 0.06f;
    /** Radians the sliver turns about the vertical each tick it falls. */
    private static final float TURN_PER_TICK = 0.35f;
    /** The glass's color: a pale, faintly blue clear glass. */
    private static final int GLASS_COLOR = 0xC8DDF4FF;
    private static final int BASE_CORNERS = 3;
    private static final double THIRD_TURN = 2 * Math.PI / BASE_CORNERS;
    private static final int SHATTER_BITS = 6;
    private static final float SHATTER_VOLUME = 0.5f;
    private static final float SHATTER_PITCH = 1.7f;
    private static final double SHATTER_SPEED = 0.08;
    /** Half, the share in the fall's distance formula and the centering of a random spread. */
    private static final double HALF = 0.5;

    /**
     * One shard falling.
     *
     * @param from      the spigot it fell from
     * @param to        the point it strikes
     * @param startTick the game time it left the spigot
     */
    record Fall(Vec3 from, Vec3 to, long startTick) {

        /** @return the ticks the fall takes under gravity, at least one */
        double fallTicks() {
            return Math.max(1, Math.sqrt(Math.max(0, from.y - to.y) / (HALF * GRAVITY)));
        }

        /**
         * Where the shard's tip is at a moment of its fall.
         *
         * @param gameTime the game time with its partial tick
         * @return the tip, at the strike point once the fall is done
         */
        Vec3 tipAt(double gameTime) {
            double ticks = Math.clamp(gameTime - startTick, 0, fallTicks());
            return new Vec3(from.x, Math.max(to.y, from.y - HALF * GRAVITY * ticks * ticks), from.z);
        }

        boolean landedBy(long now) {
            return now - startTick >= fallTicks();
        }
    }

    private final List<Fall> live = new ArrayList<>();

    private FallingShards() {
    }

    /**
     * Starts a shard falling from a spigot to the point it strikes.
     *
     * @param level the client level
     * @param from  the spigot
     * @param to    the strike point
     */
    public void drop(ClientLevel level, Vec3 from, Vec3 to) {
        live.add(new Fall(from, to, level.getGameTime()));
    }

    /**
     * Shatters every shard whose fall is done and drops it.
     *
     * @param level the client level
     */
    void shatterLanded(ClientLevel level) {
        long now = level.getGameTime();
        live.removeIf(fall -> {
            if (!fall.landedBy(now)) {
                return false;
            }
            shatter(level, fall.to());
            return true;
        });
    }

    private static void shatter(ClientLevel level, Vec3 at) {
        level.playLocalSound(at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, SHATTER_VOLUME,
                SHATTER_PITCH, false);
        BlockParticleOption glass = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
        for (int bit = 0; bit < SHATTER_BITS; bit++) {
            level.addParticle(glass, at.x, at.y, at.z, (level.getRandom().nextDouble() - HALF) * SHATTER_SPEED,
                    level.getRandom().nextDouble() * SHATTER_SPEED, (level.getRandom().nextDouble() - HALF) * SHATTER_SPEED);
        }
    }

    /** Drops every shard, as a disconnect does. */
    public void clear() {
        live.clear();
    }

    /**
     * Client tick: shatters the shards that have landed.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            CLIENT.clear();
        } else {
            CLIENT.shatterLanded(level);
        }
    }

    /**
     * Draws the falling shards after translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CLIENT.live.isEmpty()) {
            return;
        }
        double gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.CRYSTAL_SHARD_TYPE));
        for (Fall fall : CLIENT.live) {
            emitSliver(quads, fall.tipAt(gameTime).subtract(camera), (float) ((gameTime - fall.startTick()) * TURN_PER_TICK));
        }
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
    }

    /**
     * Emits one sliver: a three-sided pyramid whose apex is the tip, point
     * down, its base a turned triangle above it.
     *
     * @param quads the context the faces emit through
     * @param tip   the tip, camera-relative
     * @param turn  the sliver's turn about the vertical, in radians
     */
    private static void emitSliver(FlatQuadContext quads, Vec3 tip, float turn) {
        Vector3f apex = new Vector3f((float) tip.x, (float) tip.y, (float) tip.z);
        Vector3f[] ring = new Vector3f[BASE_CORNERS];
        for (int corner = 0; corner < BASE_CORNERS; corner++) {
            double angle = turn + corner * THIRD_TURN;
            ring[corner] = new Vector3f(apex.x + (float) Math.cos(angle) * HALF_WIDTH, apex.y + LENGTH,
                    apex.z + (float) Math.sin(angle) * HALF_WIDTH);
        }
        for (int side = 0; side < BASE_CORNERS; side++) {
            Vector3f start = ring[side];
            Vector3f end = ring[(side + 1) % BASE_CORNERS];
            Vector3f normal = new Vector3f(end).sub(start).cross(new Vector3f(apex).sub(start)).normalize();
            ConeGeometry.emitTriangle(corner -> {
                Vector3f at = switch (corner) {
                    case ConeGeometry.BASE_START -> start;
                    case ConeGeometry.BASE_END -> end;
                    default -> apex;
                };
                quads.vertex(at.x, at.y, at.z, GLASS_COLOR, normal.x, normal.y, normal.z);
            });
        }
    }
}
