package com.mercuriusxeno.goo.ability.program;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Undead invert healing and harming, as everywhere else in Minecraft: goo's
 * heal harms an undead target by the amount it would heal a living one. The
 * one reading of undead is {@link LivingEntity#isInvertedHealAndHarm}, which
 * vanilla's undead answer true and, through LivingEntityUndeadMixin, so does
 * a player under nether's Undead.
 * heal-harms-an-undead-target
 * undead-player-counts-as-undead-for-heal-and-decay
 */
public final class UndeadInversion {

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
     * Heals a living target, and harms an undead one by the same amount.
     *
     * @param target the entity healed
     * @param amount the health the heal restores
     * @param caster the entity whose heal it is, or null when unknown
     */
    public static void heal(LivingEntity target, float amount, @Nullable Entity caster) {
        if (inverts(target)) {
            harmUndead(target, amount, caster);
        } else {
            target.heal(amount);
        }
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
