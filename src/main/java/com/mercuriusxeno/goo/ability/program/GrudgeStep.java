package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.GooHitCredit;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.GrudgeRingPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Pulses a crimson ring out from the caster's middle, Nova's ring turned to a
 * curse: every living thing within the radius, save the caster and the
 * caster's own minions, takes damage equal to the health the caster is
 * missing at release, and wears a brief withered mark of smoke and crimson.
 * The missing health reads the caster's real health, so the filler hearts
 * Undead and Stoneskin lay over missing ones leave it high. The radius is
 * evaluated on the host, so the release reads the charge its hold reached:
 * {@code grudge radius="2 + 6 * charge"}.
 * grudge-is-a-charged-crimson-nova-dealing-your-missing-health
 * nether-keeps-its-name-and-takes-inversion-as-its-theme
 * nova-ring-grows-with-the-hold
 * heal-and-decay-never-harm-their-caster
 *
 * @param radius the ring's reach in blocks, evaluated on the host
 */
public record GrudgeStep(Expr radius) implements Step {

    private static final String NAME = "grudge";
    private static final String FIELD_RADIUS = "radius";
    private static final Set<EntityFilter> AROUND_THE_CASTER =
            Set.of(EntityFilter.LIVING, EntityFilter.NOT_TARGET, EntityFilter.NOT_OWN_MINION);

    /** The mark's crimson, a deep blood red. */
    static final int MARK_CRIMSON = 0xFF8B0A1E;
    /** Smoke puffs and crimson puffs the mark lays over a struck body. */
    private static final int MARK_SMOKE = 10;
    private static final int MARK_CRIMSON_PUFFS = 8;
    /** How fast the mark's puffs drift off the body. */
    private static final double MARK_DRIFT = 0.02;
    private static final double HALF = 0.5;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<GrudgeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(GrudgeStep::radius)
    ).apply(inst, GrudgeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<GrudgeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<GrudgeStep> type() {
        return TYPE;
    }

    /**
     * The damage Grudge's ring deals: the health the caster is missing, their
     * maximum health less their current health, never below none.
     *
     * @param maxHealth the caster's maximum health
     * @param health    the caster's current health
     * @return the damage each thing the ring reaches takes
     */
    static float missingHealthDamage(float maxHealth, float health) {
        return Math.max(0f, maxHealth - health);
    }

    @Override
    public boolean tick(StepContext context) {
        float reach = radius.evaluateFloat(context);
        FrostHost pulse = context.hostAs(FrostHost.class);
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        float hurt = missingHealthDamage(caster.getMaxHealth(), caster.getHealth());
        ServerLevel level = pulse.level();
        Vec3 center = pulse.frostCenter();
        List<LivingEntity> struck = new ArrayList<>();
        pulse.forEachEntityWithin(SelectionShape.SPHERE, reach, AROUND_THE_CASTER,
                selected -> struck.add(selected.target()));
        for (LivingEntity living : struck) {
            if (hurt > 0f) {
                // direct-goo-hit-credits-its-caster
                // goo-hit-keeps-its-kind-and-names-the-player-behind-it
                living.hurtServer(level, GooHitCredit.credit(level.damageSources().wither(), living, caster), hurt);
            }
            markWithered(level, living);
        }
        EntityVisuals.sendToWatchersOf(level, center, new GrudgeRingPayload(center, reach));
        return true;
    }

    /**
     * Lays the brief withered mark over a struck body, drawing alone: smoke
     * and crimson puffs drifting off it, no wither effect.
     *
     * @param level  the server level
     * @param living the struck thing
     */
    private static void markWithered(ServerLevel level, LivingEntity living) {
        double x = living.getX();
        double y = living.getY() + living.getBbHeight() * HALF;
        double z = living.getZ();
        double across = living.getBbWidth() * HALF;
        double up = living.getBbHeight() * HALF;
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, MARK_SMOKE, across, up, across, MARK_DRIFT);
        level.sendParticles(ColorParticleOption.create(GooParticles.GOO_FOG.get(), MARK_CRIMSON), x, y, z,
                MARK_CRIMSON_PUFFS, across, up, across, MARK_DRIFT);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.FROST, HostCapability.TARGET);
    }
}
