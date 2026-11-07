package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Astral's Scry: a press sends a pulse at once and a hold another every
 * second, each front leaving where the caster stood at the held
 * sweeps' steady pace, fronts overlapping. The child steps run on every
 * living entity a front crosses, seen or behind walls, once for each
 * front. The caster's client is sent each live pulse each tick and lights
 * the walls each front hits in the goo type's colors. Scry is
 * {@code scry goo=astral reach=96} with an astral {@code ailment_overlay}
 * as its child.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 *
 * @param goo   the goo type whose colors tint the fronts and the walls they light
 * @param reach how far each pulse reaches, in blocks
 * @param where the filters an entity must pass; empty keeps any living entity
 * @param steps the child steps run once on each entity the front crosses
 */
public record ScryStep(ResourceKey<GooTypeDefinition> goo, double reach, List<EntityFilter> where,
                       List<Step> steps) implements HeldSweepStep {

    private static final String NAME = "scry";
    private static final String FIELD_GOO = "goo";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<ScryStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            GooTypes.ID_CODEC.fieldOf(FIELD_GOO).forGetter(ScryStep::goo),
            Codec.DOUBLE.fieldOf(FIELD_REACH).forGetter(ScryStep::reach),
            EntityFilter.CODEC.listOf().optionalFieldOf(FIELD_WHERE, List.of()).forGetter(ScryStep::where),
            Codec.lazyInitialized(() -> StepTypes.LIST_CODEC).fieldOf(FIELD_STEPS).forGetter(ScryStep::steps)
    ).apply(inst, ScryStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ScryStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ScryStep> type() {
        return TYPE;
    }

    @Override
    public HeldSweepPace pace() {
        return new HeldSweepPace(reach);
    }

    /**
     * Scry's fronts outline every mob they cross through walls, as the
     * glisten its child steps lay on them (operator ruling 2026-11-06).
     * decision scry-sphere-reveals-faces-and-glistens-mobs
     */
    @Override
    public boolean paintsMobs() {
        return true;
    }

    @Override
    public boolean tick(StepContext context) {
        int held = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::held)
                .orElse(ChannelAim.FIRST_TICK);
        HeldSweepPace pace = pace();
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        long now = caster.level().getGameTime();
        for (HeldSweepPulses.Pulse pulse : HeldSweepPulses.SERVER.advance(caster.getUUID(), caster.position(), held,
                now, pace)) {
            int age = pulse.ageAt(now);
            strikeCrossed(context, caster, pulse.origin(), pace.radiusAt(age - 1), pace.radiusAt(age));
            EntityVisuals.sendToSelf(caster, sweepOf(pulse, now));
        }
        return true;
    }

    /**
     * Runs the child steps on every entity one front crossed this tick, so
     * an entity between two fronts is crossed by each once.
     *
     * @param context  the step context
     * @param caster   the caster
     * @param origin   where the front's pulse began
     * @param previous the front's radius last tick
     * @param reached  its radius this tick
     */
    private void strikeCrossed(StepContext context, LivingEntity caster, Vec3 origin, double previous,
                               double reached) {
        if (reached <= previous) {
            return;
        }
        double scanned = reached + caster.position().distanceTo(origin);
        context.hostAs(EntityScanHost.class).forEachEntityWithin(SelectionShape.SPHERE, scanned, Set.copyOf(where),
                selected -> {
                    if (crossed(selected.target().position().distanceTo(origin), previous, reached)) {
                        new ProgramBehavior(steps).tick(selected);
                    }
                });
    }

    /**
     * Whether the front crossed a distance this tick: past last tick's radius, within this one's.
     *
     * @param distance the distance from the sphere's center
     * @param previous last tick's radius
     * @param radius   this tick's radius
     * @return true when this tick's front swept over the distance
     */
    static boolean crossed(double distance, double previous, double radius) {
        return distance > previous && distance <= radius;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.TARGET, HostCapability.ENTITY_SCAN);
    }

    @Override
    public Stream<Step> children() {
        return steps.stream();
    }

    @Override
    public boolean takesEntity(Entity entity) {
        return Step.takenThrough(where, steps, entity);
    }

    @Override
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }
}
