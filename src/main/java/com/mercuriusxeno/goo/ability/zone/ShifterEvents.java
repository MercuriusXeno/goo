package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Runs the shifter a player holds: each hit they would take is
 * cancelled and blinks them the distance along their look through the blink
 * resolver, an afterimage left where they stood and where they land; a fall
 * out of the world returns them to the last solid ground they stood on; only
 * /kill passes. While it stands the player remembers that ground and wears
 * the shifter shimmer.
 * Decision shifter-blinks-along-the-cursor-on-hit.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ShifterEvents {

    /** Ticks between one shimmer refresh and the next. */
    static final int SHIMMER_REFRESH_TICKS = 20;
    /** Ticks each refresh keeps the shimmer on, outlasting the refresh period. */
    static final int SHIMMER_TICKS = 40;
    /** Ticks each blink's afterimage grows and fades over, the blink's own. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;

    private ShifterEvents() {
    }

    /**
     * Remembers the solid ground a player holding shifter stands on,
     * and keeps their shimmer on.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            Shifter held = standing(player);
            if (held != null) {
                rememberGround(level, player, held);
                if (level.getGameTime() % SHIMMER_REFRESH_TICKS == 0) {
                    EntityVisuals.sendToWatchers(player,
                            new AilmentPayload(player.getId(), AilmentKind.SHIFTER, SHIMMER_TICKS));
                }
            }
        }
    }

    /**
     * Cancels a hit on a player holding shifter and moves them instead.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            Shifter held = standing(player);
            if (held != null && escapes(event.getSource(), held)) {
                event.setCanceled(true);
                escapeTo(player, held, event.getSource()).ifPresent(feet -> blinkTo(level, player, feet));
            }
        }
    }

    /**
     * Whether shifter turns a hit aside: every hit but /kill, and a fall
     * out of the world only once the player has stood on solid ground to
     * return to.
     *
     * @param source the hit's source
     * @param held   the player's shifter
     * @return true for a hit the player escapes
     */
    static boolean escapes(DamageSource source, Shifter held) {
        return !source.is(DamageTypes.GENERIC_KILL)
                && (!source.is(DamageTypes.FELL_OUT_OF_WORLD) || held.safeGround().isPresent());
    }

    /**
     * Where a player escapes a hit to: their safe ground from a fall out of
     * the world, the blink along their look from any other hit.
     *
     * @param player the player
     * @param held   their shifter
     * @param source the hit's source
     * @return where their feet land, empty where the blink finds nowhere they fit
     */
    private static Optional<Vec3> escapeTo(ServerPlayer player, Shifter held, DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD) ? held.safeGround()
                : TeleportStep.landingAlongLook(player, player.position(), player.getLookAngle(), held.distance(),
                        Optional.empty()).map(BlinkLanding::feet);
    }

    /**
     * Blinks a player to a spot with the shared blink effect at both ends.
     * Decision afterimage-is-one-shared-effect.
     *
     * @param level  the player's level
     * @param player the player
     * @param feet   where their feet land
     */
    private static void blinkTo(ServerLevel level, ServerPlayer player, Vec3 feet) {
        Vec3 stood = player.position();
        player.teleportTo(feet.x, feet.y, feet.z);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        for (Vec3 end : new Vec3[] {stood, feet}) {
            EntityVisuals.sendToWatchers(player,
                    new AfterimagePayload(player.getId(), end, GooTypes.ENDER, AFTERIMAGE_LIFE_TICKS));
            level.playSound(null, end.x, end.y, end.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
        }
    }

    /**
     * Remembers where the player stands when it is solid ground: the block
     * under their feet has a sturdy top and their body fits where they stand.
     *
     * @param level  the player's level
     * @param player the player
     * @param held   their shifter
     */
    private static void rememberGround(ServerLevel level, ServerPlayer player, Shifter held) {
        BlockPos under = BlockPos.containing(player.position()).below();
        boolean solid = level.getBlockState(under).isFaceSturdy(level, under, Direction.UP)
                && level.noCollision(player, player.getBoundingBox());
        if (solid && !held.safeGround().map(player.position()::equals).orElse(false)) {
            player.setData(GooAttachments.SHIFTER, held.standingOn(player.position()));
        }
    }

    /**
     * The shifter a player holds now.
     *
     * @param player the player
     * @return their shifter, or null where none stands
     */
    public static @Nullable Shifter standing(ServerPlayer player) {
        Shifter held = player.hasData(GooAttachments.SHIFTER)
                ? player.getData(GooAttachments.SHIFTER) : null;
        return held != null && held.standsAt(player.level().getGameTime()) ? held : null;
    }
}
