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
 * at the caster's feet, spreading out to the reach its charge resolved with
 * a burst of snowflakes riding the edge, then fading.
 * nova-ring-grows-with-the-hold
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class NovaRings {

    /** The client's novas. */
    public static final NovaRings CLIENT = new NovaRings();

    /** How far above the feet the ring lies, clear of the floor. */
    private static final double FEET_LIFT = 0.1;
    /** The block-local offset from the ring's corner to its center. */
    private static final double HALF_BLOCK = 0.5;
    /** Snowflakes per block of the ring's reach. */
    private static final int SNOWFLAKES_PER_BLOCK = 12;
    private static final double TWO_PI = 2 * Math.PI;
    /** How far below the edge's speed a snowflake may launch, as a share of it. */
    private static final float SNOWFLAKE_SPEED_SPREAD = 0.5f;

    /**
     * One nova playing.
     *
     * @param center    the caster's feet
     * @param reach     the reach the ring spreads to
     * @param startTick the game time it began
     */
    record Ring(Vec3 center, float reach, long startTick) {

        float progress(float gameTime) {
            return Math.clamp((gameTime - startTick) / FrostExplosionVisual.DURATION_TICKS, 0f, 1f);
        }

        boolean isOver(long now) {
            return now - startTick >= FrostExplosionVisual.DURATION_TICKS;
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
     * Starts a nova at the caster's feet and scatters its snowflakes.
     *
     * @param level  the client level
     * @param center the caster's feet
     * @param reach  the reach the ring spreads to
     */
    public void pulse(ClientLevel level, Vec3 center, float reach) {
        live.add(new Ring(center, reach, level.getGameTime()));
        int snowflakes = Math.max(1, Math.round(reach * SNOWFLAKES_PER_BLOCK));
        for (int i = 0; i < snowflakes; i++) {
            Vec3 velocity = FrostExplosionVisual.snowflakeVelocity(Direction.UP,
                    TWO_PI * (i + level.getRandom().nextFloat()) / snowflakes,
                    reach * FrostExplosionVisual.SNOWFLAKE_SPEED * (1f - SNOWFLAKE_SPEED_SPREAD * level.getRandom().nextFloat()));
            level.addParticle(ParticleTypes.SNOWFLAKE, center.x, center.y + FEET_LIFT, center.z,
                    velocity.x, velocity.y, velocity.z);
        }
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
            FrostExplosionVisual.drawRing(frame, ring.corner(), Direction.UP, 0f, ring.reach(),
                    ring.progress(frame.gameTime()));
        }
    }
}
