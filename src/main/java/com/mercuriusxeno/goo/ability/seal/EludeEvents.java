package com.mercuriusxeno.goo.ability.seal;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Runs the Elude a player holds: each hit a mob or a projectile would land
 * on them is cancelled and moves them to a random safe spot within its
 * radius, turned to a random heading, an afterimage left where they stood
 * and where they land. Every other harm lands, and so does a hit with no
 * safe spot found. While it stands the player wears the Elude shimmer.
 * Decision elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class EludeEvents {

    /** Ticks between one shimmer refresh and the next. */
    public static final int SHIMMER_REFRESH_TICKS = 20;
    /** Ticks each refresh keeps the shimmer on, outlasting the refresh period. */
    public static final int SHIMMER_TICKS = 40;
    private static final int SHIMMER_CLEARED = 0;
    /** Ticks each blink's afterimage grows and fades over, the blink's own. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;
    /** The fewest blocks across the ground an escape carries the player, so it leaves the hitter's reach. */
    static final int MIN_ESCAPE = 2;
    /** Columns an escape tries before it gives up and the hit lands. */
    static final int ESCAPE_TRIES = 32;
    private static final float FULL_TURN_DEGREES = 360f;
    private static final float HALF_TURN_DEGREES = 180f;

    private EludeEvents() {
    }

    /**
     * Keeps the shimmer on a player holding Elude.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                && standing(player) != null) {
            keepShimmer(player, level.getGameTime());
        }
    }

    /**
     * Refreshes the shimmer of a player whose Elude stands, once each refresh period.
     *
     * @param player   the player
     * @param gameTime the game time
     */
    static void keepShimmer(ServerPlayer player, long gameTime) {
        if (gameTime % SHIMMER_REFRESH_TICKS == 0) {
            sendShimmer(player, SHIMMER_TICKS);
        }
    }

    /**
     * Clears the shimmer the tick Elude's held effect ends, so its fade
     * starts at the end rather than when the last refresh lapses.
     * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
     *
     * @param player the player whose Elude ended
     */
    public static void clearShimmer(ServerPlayer player) {
        sendShimmer(player, SHIMMER_CLEARED);
    }

    private static void sendShimmer(ServerPlayer player, int ticks) {
        EntityVisuals.sendToWatchers(player, new AilmentPayload(player.getId(), AilmentKind.ELUDE, ticks));
    }

    /**
     * Cancels a mob's or a projectile's hit on a player holding Elude and
     * moves them to a random safe spot instead, where one is found.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            Elude held = standing(player);
            if (held != null && escapes(event.getSource())) {
                RandomSource random = player.getRandom();
                safeSpotNear(player.blockPosition(), Mth.floor(held.distance()), random,
                        spot -> StandingColumn.at(level, spot).safe())
                        .ifPresent(spot -> {
                            event.setCanceled(true);
                            blinkTo(level, player, Vec3.atBottomCenterOf(spot), randomHeading(random));
                        });
            }
        }
    }

    /**
     * Whether Elude turns a hit aside: a hit a mob deals, or one a projectile
     * lands whoever shot it.
     *
     * @param source the hit's source
     * @return true for a hit the player escapes
     */
    static boolean escapes(DamageSource source) {
        return source.getEntity() instanceof Mob || source.getDirectEntity() instanceof Projectile;
    }

    /**
     * A random spot within the radius a player can escape to: each try draws
     * a column at least the minimum escape across the ground and takes the
     * safe height in it nearest the player's feet.
     *
     * @param stood  the block the player's feet fill
     * @param radius the escape's radius in blocks
     * @param random the draws
     * @param safe   whether a player's feet can stand in a block
     * @return the block their feet land in, empty where every try found no safe height
     */
    static Optional<BlockPos> safeSpotNear(BlockPos stood, int radius, RandomSource random,
            Predicate<BlockPos> safe) {
        int span = radius + radius + 1;
        int reach = radius * radius;
        for (int tried = 0; tried < ESCAPE_TRIES; tried++) {
            int dx = random.nextInt(span) - radius;
            int dz = random.nextInt(span) - radius;
            int across = dx * dx + dz * dz;
            if (across >= MIN_ESCAPE * MIN_ESCAPE && across <= reach) {
                Optional<BlockPos> spot = nearestSafeHeight(stood.offset(dx, 0, dz),
                        Mth.floor(Math.sqrt(reach - across)), safe);
                if (spot.isPresent()) {
                    return spot;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The safe height in a column nearest a level, upward first on a tie.
     *
     * @param level the column's block at the player's feet height
     * @param rise  how far above and below that block the column reaches
     * @param safe  whether a player's feet can stand in a block
     * @return the safe block, empty where the column holds none
     */
    private static Optional<BlockPos> nearestSafeHeight(BlockPos level, int rise, Predicate<BlockPos> safe) {
        for (int dy = 0; dy <= rise; dy++) {
            if (safe.test(level.above(dy))) {
                return Optional.of(level.above(dy));
            }
            if (dy > 0 && safe.test(level.below(dy))) {
                return Optional.of(level.below(dy));
            }
        }
        return Optional.empty();
    }

    /**
     * A random heading across a full turn, in degrees from -180 to 180.
     *
     * @param random the draws
     * @return the heading
     */
    static float randomHeading(RandomSource random) {
        return random.nextFloat() * FULL_TURN_DEGREES - HALF_TURN_DEGREES;
    }

    /**
     * Blinks a player to a spot turned to a heading, with the shared blink
     * effect at both ends.
     * Decision afterimage-is-one-shared-effect.
     *
     * @param level   the player's level
     * @param player  the player
     * @param feet    where their feet land
     * @param heading the yaw they face after it
     */
    static void blinkTo(ServerLevel level, ServerPlayer player, Vec3 feet, float heading) {
        Vec3 stood = player.position();
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), heading, player.getXRot(), true);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        for (Vec3 end : new Vec3[] {stood, feet}) {
            EntityVisuals.sendToWatchers(player,
                    new AfterimagePayload(player.getId(), end, GooTypes.ENDER, AFTERIMAGE_LIFE_TICKS));
            level.playSound(null, end.x, end.y, end.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    /**
     * The Elude a player holds now.
     *
     * @param player the player
     * @return their Elude, or null where none stands
     */
    public static @Nullable Elude standing(ServerPlayer player) {
        Elude held = player.hasData(GooAttachments.ELUDE)
                ? player.getData(GooAttachments.ELUDE) : null;
        return held != null && held.standsAt(player.level().getGameTime()) ? held : null;
    }
}
