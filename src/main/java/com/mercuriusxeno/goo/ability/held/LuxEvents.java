package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Keeps a player's Lux working while it stands: night vision topped up with
 * its particles off, and the glow glisten on the living mob under the
 * crosshair within the gaze's reach, the gaze stopping at blocks. Once Lux
 * no longer stands it ends, taking the night vision it kept up with it.
 * decision lux-night-vision-without-particles
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LuxEvents {

    /** How far the gaze reaches, in blocks (operator ruling 2026-10-09). */
    static final double GAZE_REACH = 32;
    /** Night vision Lux grants at a time, in ticks; it is topped up before it runs low. */
    static final int NIGHT_VISION_TICKS = 400;
    /** The duration left under which night vision is topped up, above vanilla's flicker at 200. */
    static final int TOP_UP_BELOW = 220;
    /** Ticks between glisten sends to the mob under the crosshair. */
    static final int GAZE_EVERY = 5;
    /** How long a gaze glisten lasts, a little past the next send so it never blinks. */
    static final int GAZE_GLISTEN_TICKS = 10;

    private LuxEvents() {
    }

    /**
     * Keeps Lux working each tick it stands, and ends it the tick it no longer does.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.LUX)) {
            return;
        }
        long now = player.level().getGameTime();
        if (!player.getData(GooAttachments.LUX).standsAt(now)) {
            end(player);
            return;
        }
        topUpNightVision(player);
        if (now % GAZE_EVERY == 0) {
            glistenTheGaze(player);
        }
    }

    private static void topUpNightVision(ServerPlayer player) {
        MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
        if (needsTopUp(vision == null ? 0 : vision.getDuration())) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, false, false, true));
        }
    }

    private static void glistenTheGaze(ServerPlayer player) {
        LivingEntity gazed = gazedAt(player);
        if (gazed != null) {
            EntityVisuals.sendToWatchers(gazed, new AilmentPayload(gazed.getId(), AilmentKind.GLOW, GAZE_GLISTEN_TICKS));
        }
    }

    /**
     * Ends a player's Lux: clears it and takes away the night vision it kept up.
     *
     * @param player the player
     */
    public static void end(ServerPlayer player) {
        player.removeData(GooAttachments.LUX);
        MobEffectInstance vision = player.getEffect(MobEffects.NIGHT_VISION);
        if (vision != null && vision.getDuration() <= NIGHT_VISION_TICKS) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /**
     * Whether night vision with a duration left needs topping up.
     *
     * @param ticksLeft the duration left, zero for none
     * @return true when it would soon flicker or has run out
     */
    static boolean needsTopUp(int ticksLeft) {
        return ticksLeft < TOP_UP_BELOW;
    }

    /**
     * The living mob under the player's crosshair within the gaze's reach,
     * the gaze stopping at the first block in its way.
     *
     * @param player the player
     * @return the mob, or null for none
     */
    private static @Nullable LivingEntity gazedAt(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getLookAngle().scale(GAZE_REACH));
        Vec3 stop = player.level().clip(new ClipContext(eye, reach, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        AABB swept = player.getBoundingBox().expandTowards(stop.subtract(eye)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, stop, swept,
                candidate -> candidate instanceof LivingEntity living && living.isAlive(), eye.distanceToSqr(stop));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
