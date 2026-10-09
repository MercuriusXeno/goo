package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * The frost novas playing on this client: each is frost's fog ring laid flat
 * about the nova's center, the caster's middle or a tap's landing, spreading out to the reach its charge resolved with
 * a burst of snowflakes riding the edge, then fading.
 * nova-ring-grows-with-the-hold
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NovaRings {

    /** The client's novas. */
    public static final NovaRings CLIENT = new NovaRings();

    /** Ticks a nova's ring takes to spread to its reach: fast, a burst rather than a drift. */
    static final int SPREAD_TICKS = 6;
    /** Ticks its fog takes to fade once it has spread. */
    static final int FADE_TICKS = 10;
    /** Ticks a nova plays. */
    static final int DURATION_TICKS = SPREAD_TICKS + FADE_TICKS;

    /** How far above its center the ring lies, clear of a floor it pulses on. */
    private static final double FEET_LIFT = 0.1;
    /** The block-local offset from the ring's corner to its center. */
    private static final double HALF_BLOCK = 0.5;
    /** Snowflakes per block of the ring's reach. */
    private static final int SNOWFLAKES_PER_BLOCK = 12;
    private static final double TWO_PI = 2 * Math.PI;
    /** How much faster than frost's burnout the snowflakes burst, to ride the fast ring's edge. */
    private static final float SNOWFLAKE_BURST = 2.5f;
    /** How far below the edge's speed a snowflake may launch, as a share of it. */
    private static final float SNOWFLAKE_SPEED_SPREAD = 0.5f;

    /**
     * One nova playing.
     *
     * @param center    the nova's center
     * @param reach     the reach the ring spreads to
     * @param startTick the game time it began
     */
    record Ring(Vec3 center, float reach, long startTick) {

        float progress(float gameTime) {
            return Math.clamp((gameTime - startTick) / DURATION_TICKS, 0f, 1f);
        }

        boolean isOver(long now) {
            return now - startTick >= DURATION_TICKS;
        }

        /** @return the point the ring's block-local coordinates measure from */
        Vec3 corner() {
            return center.add(-HALF_BLOCK, FEET_LIFT - HALF_BLOCK, -HALF_BLOCK);
        }
    }

    private final List<Ring> live = new ArrayList<>();

    private NovaRings() {
    }

    /**
     * Starts a nova about its center and scatters its snowflakes.
     *
     * @param level  the client level
     * @param center the nova's center
     * @param reach  the reach the ring spreads to
     */
    public void pulse(ClientLevel level, Vec3 center, float reach) {
        live.add(new Ring(center, reach, level.getGameTime()));
        int snowflakes = Math.max(1, Math.round(reach * SNOWFLAKES_PER_BLOCK));
        for (int i = 0; i < snowflakes; i++) {
            Vec3 velocity = FrostExplosionVisual.snowflakeVelocity(Direction.UP,
                    TWO_PI * (i + level.getRandom().nextFloat()) / snowflakes,
                    reach * FrostExplosionVisual.SNOWFLAKE_SPEED * SNOWFLAKE_BURST * (1f - SNOWFLAKE_SPEED_SPREAD * level.getRandom().nextFloat()));
            level.addParticle(ParticleTypes.SNOWFLAKE, center.x, center.y + FEET_LIFT, center.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    /**
     * How far a nova's ring has spread toward its reach: an ease-out over SPREAD_TICKS.
     *
     * @param progress the nova's progress in [0, 1]
     * @return the spread in [0, 1]
     */
    static float spread(float progress) {
        return BurnoutGeometry.easeOutCubic(Math.min(1f, progress * DURATION_TICKS / SPREAD_TICKS));
    }

    /**
     * How much of a nova's fog is left: whole while the ring spreads, fading over FADE_TICKS.
     *
     * @param progress the nova's progress in [0, 1]
     * @return the fog's opacity in [0, 1]
     */
    static float fog(float progress) {
        return BurnoutGeometry.fadeAfter(progress, (float) SPREAD_TICKS / DURATION_TICKS);
    }

    /**
     * Drops every nova that has played out and answers the rest.
     *
     * @param now the game time
     * @return the novas still playing
     */
    List<Ring> live(long now) {
        live.removeIf(ring -> ring.isOver(now));
        return List.copyOf(live);
    }

    /** Drops every nova, as a disconnect does. */
    public void clear() {
        live.clear();
    }

    /**
     * Draws the live novas after translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<Ring> rings = CLIENT.live(mc.level.getGameTime());
        if (rings.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        BurnoutFrame frame = new BurnoutFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera().position(), mc.level.getGameTime() + partialTick);
        for (Ring ring : rings) {
            float progress = ring.progress(frame.gameTime());
            FrostExplosionVisual.drawDisc(frame, ring.corner(), Direction.UP, 0f, ring.reach() * spread(progress),
                    progress, fog(progress));
        }
    }
}
