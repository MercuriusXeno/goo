package com.mercuriusxeno.goo.ability.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Runs the teleportitis Zone leaves on a mob: each tick a player stands
 * within the curse's radius, the mob warps to a random spot the way chorus
 * fruit throws its eater, an afterimage left where it stood and where it
 * lands. A second Zone exiles the cursed mob from existence. The cursed
 * mob wears the zone shimmer, refreshed while the curse stands.
 * Decision zone-curses-with-ender-shimmer.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ZoneEvents {

    /** Rolls a warp tries before giving up for the tick, as chorus fruit does. */
    static final int WARP_TRIES = 16;
    /** Ticks between one shimmer refresh and the next. */
    static final int SHIMMER_REFRESH_TICKS = 20;
    /** Ticks each refresh keeps the shimmer on, outlasting the refresh period. */
    static final int SHIMMER_TICKS = 40;
    /** Ticks each warp's afterimage grows and fades over, the blink's own. */
    static final int AFTERIMAGE_LIFE_TICKS = 12;
    private static final double HALF = 0.5;

    private ZoneEvents() {
    }

    /**
     * Warps or exiles each cursed mob a player stands near, and keeps its
     * shimmer on.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity mob && mob.level() instanceof ServerLevel level) {
            ZoneCurse curse = curseOf(mob);
            if (curse != null) {
                runCurse(level, mob, curse);
            }
        }
    }

    /**
     * Keeps a cursed mob's shimmer on and repels it from a player within its radius.
     *
     * @param level the mob's level
     * @param mob   the cursed mob
     * @param curse its curse
     */
    private static void runCurse(ServerLevel level, LivingEntity mob, ZoneCurse curse) {
        if (level.getGameTime() % SHIMMER_REFRESH_TICKS == 0) {
            EntityVisuals.sendToWatchers(mob, new AilmentPayload(mob.getId(), AilmentKind.ZONE, SHIMMER_TICKS));
        }
        Player near = level.getNearestPlayer(mob, curse.radius());
        if (near != null) {
            repel(level, mob, curse, near);
        }
    }

    /**
     * Warps a cursed mob away from the player it neared.
     *
     * @param level  the mob's level
     * @param mob    the cursed mob
     * @param curse  its curse
     * @param player the player it neared
     */
    static void repel(ServerLevel level, LivingEntity mob, ZoneCurse curse, Player player) {
        Vec3 stood = mob.position();
        if (warpAway(level, mob, curse, player)) {
            leaveAfterimage(level, mob, stood);
            leaveAfterimage(level, mob, mob.position());
        }
    }

    /**
     * Exiles a cursed mob from existence: its afterimage stays where it stood
     * and the mob is gone.
     *
     * @param level the mob's level
     * @param mob   the cursed mob
     */
    public static void exile(ServerLevel level, LivingEntity mob) {
        leaveAfterimage(level, mob, mob.position());
        mob.discard();
    }

    /**
     * Throws the mob to a random spot the way chorus fruit does, refusing a
     * roll that lands within the curse's radius of the player.
     *
     * @param level  the mob's level
     * @param mob    the cursed mob
     * @param curse  its curse
     * @param player the player it neared
     * @return true once the mob stands somewhere else
     */
    private static boolean warpAway(ServerLevel level, LivingEntity mob, ZoneCurse curse, Player player) {
        RandomSource random = mob.getRandom();
        for (int tryIndex = 0; tryIndex < WARP_TRIES; tryIndex++) {
            Vec3 roll = rollSpot(level, mob.position(), curse.range(), random);
            if (roll.distanceTo(player.position()) > curse.radius()
                    && mob.randomTeleport(roll.x, roll.y, roll.z, true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Rolls a warp's target: up to half the range either way on each
     * horizontal axis and up to a quarter of it up or down, kept inside the
     * level's height.
     *
     * @param level  the level
     * @param from   where the mob stands
     * @param range  the full width of the horizontal roll
     * @param random the roll's source
     * @return the spot the warp tries
     */
    static Vec3 rollSpot(ServerLevel level, Vec3 from, double range, RandomSource random) {
        double y = Mth.clamp(from.y + (random.nextDouble() - HALF) * range * HALF, level.getMinY(),
                level.getMinY() + level.getLogicalHeight() - 1);
        return new Vec3(from.x + (random.nextDouble() - HALF) * range, y,
                from.z + (random.nextDouble() - HALF) * range);
    }

    /**
     * Leaves the shared blink effect at a spot: the mob's afterimage and the
     * enderman's teleport sound.
     * Decision afterimage-is-one-shared-effect.
     *
     * @param level the mob's level
     * @param mob   the mob whose model the afterimage takes
     * @param at    where the afterimage stands
     */
    private static void leaveAfterimage(ServerLevel level, LivingEntity mob, Vec3 at) {
        EntityVisuals.sendToWatchers(mob, new AfterimagePayload(mob.getId(), at, GooTypes.ENDER, AFTERIMAGE_LIFE_TICKS));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1f, 1f);
    }

    /**
     * The curse a living entity carries, for a reader outside the tick.
     *
     * @param entity the entity
     * @return its curse, or null where none stands
     */
    public static @Nullable ZoneCurse curseOf(LivingEntity entity) {
        ZoneCurse curse = entity.hasData(GooAttachments.ZONE_CURSE) ? entity.getData(GooAttachments.ZONE_CURSE) : null;
        return curse != null && curse.stands() ? curse : null;
    }
}
