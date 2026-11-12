package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Undead invert healing and harming, as everywhere else in Minecraft: goo's
 * heal harms an undead target by the amount it would heal a living one, and
 * Decay's harm heals one by the amount it would take. The
 * one reading of undead is {@link LivingEntity#isInvertedHealAndHarm}, which
 * vanilla's undead answer true and, through LivingEntityUndeadMixin, so does
 * a player under nether's Undead.
 * heal-harms-an-undead-target
 * undead-player-counts-as-undead-for-heal-and-decay
 */
public final class UndeadInversion {

    /**
     * Half of the twenty ticks of immunity vanilla's hurt leaves: a hit of the
     * same size lands again once they pass, so an i-frame-respecting harm lands
     * twice a second.
     */
    private static final long HARM_PACE_TICKS = 10L;
    /** The game tick each undead target's next paced heal comes due, kept while the target stands, never saved. */
    private static final Map<Entity, Long> NEXT_PACED_HEAL = new WeakHashMap<>();

    private UndeadInversion() {
    }

    /**
     * Whether a target takes healing as harm and harm as healing.
     *
     * @param target the entity healed or harmed
     * @return true for an undead target
     */
    public static boolean inverts(LivingEntity target) {
        return target.isInvertedHealAndHarm();
    }

    /**
     * Heals a living target, and harms an undead one by the same amount,
     * save the caster: an undead caster's own heal leaves it as it stands.
     *
     * @param target the entity healed
     * @param amount the health the heal restores
     * @param caster the entity whose heal it is, or null when unknown
     */
    public static void heal(LivingEntity target, float amount, @Nullable Entity caster) {
        if (inverts(target)) {
            harmUnlessCaster(target, amount, caster);
        } else {
            target.heal(amount);
        }
    }

    /**
     * Harms an undead target by a heal's amount, unless it is the caster.
     *
     * @param target the undead entity
     * @param amount the health the heal would have restored
     * @param caster the entity whose heal it is, or null when unknown
     */
    private static void harmUnlessCaster(LivingEntity target, float amount, @Nullable Entity caster) {
        if (target == caster) {
            return;
        }
        harmUndead(target, amount, caster);
    }

    /**
     * Whether an inverting harm lands nothing on its target: a living
     * caster takes no harm from its own ability.
     *
     * @param target the entity the harm reaches
     * @param caster the entity whose harm it is, or null when unknown
     * @return true when the harm should leave the target as it stands
     */
    public static boolean sparesCaster(LivingEntity target, @Nullable Entity caster) {
        return target == caster && !inverts(target);
    }

    /**
     * Heals an undead target by a harm's amount. A harm respecting i-frames
     * lands at most once each half of vanilla's immunity window, and a heal
     * meets no i-frames, so a heal standing in for such a harm waits out
     * that same half window from the last; the heal leaves no immunity and
     * no hurt flash behind.
     * decay-and-hive-heal-an-undead-target
     * decay-gnats-degrade-each-block-once
     *
     * @param target     the undead entity
     * @param amount     the health the harm would have taken
     * @param atHarmPace whether the harm respected i-frames, so the heal keeps its pace
     */
    public static void healUndead(LivingEntity target, float amount, boolean atHarmPace) {
        long now = target.level().getGameTime();
        if (atHarmPace) {
            if (now < NEXT_PACED_HEAL.getOrDefault(target, now)) {
                return;
            }
            NEXT_PACED_HEAL.put(target, now + HARM_PACE_TICKS);
        }
        target.heal(amount);
    }

    /**
     * Harms an undead target by a heal's amount, landing every time it is
     * called: a heal streamed each tick harms each tick, so the hurt passes
     * the target's damage immunity and leaves that immunity as it found it,
     * and it keeps the target's motion, since a heal shoves nothing.
     *
     * @param target the undead entity
     * @param amount the health the heal would have restored
     * @param caster the entity whose heal it is, or null when unknown
     */
    private static void harmUndead(LivingEntity target, float amount, @Nullable Entity caster) {
        int immunity = target.invulnerableTime;
        Vec3 motion = target.getDeltaMovement();
        target.invulnerableTime = 0;
        target.hurtServer((ServerLevel) target.level(), magicFrom(target, caster), amount);
        target.invulnerableTime = immunity;
        target.setDeltaMovement(motion);
        target.hurtMarked = false;
    }

    /**
     * The magic a heal harms with: the caster's indirect magic, as vanilla's
     * instant health harming a zombie is, so the harmed mob turns on the
     * caster; plain magic for a caster harming itself or for none.
     *
     * @param target the undead entity
     * @param caster the entity whose heal it is, or null when unknown
     * @return the damage source
     */
    private static DamageSource magicFrom(LivingEntity target, @Nullable Entity caster) {
        if (caster == null || caster == target) {
            return target.damageSources().magic();
        }
        return target.damageSources().indirectMagic(caster, caster);
    }
}
