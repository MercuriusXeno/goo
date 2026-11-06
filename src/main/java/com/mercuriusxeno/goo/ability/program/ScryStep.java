package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Astral's Scry: while held, it pulses at the held sweeps' shared pace, each
 * pulse a sphere of light easing out from where the caster stood as it
 * began to its reach. The child steps run on every living entity the front
 * crosses, seen or behind walls. The caster's client is sent each tick of
 * the pulse and draws the sphere and the thin shell of faces at its rim in
 * the goo type's colors. Scry is {@code scry goo=astral reach=96} with an
 * astral {@code ailment_overlay} as its child.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 *
 * @param goo   the goo type whose colors tint the sphere and the shell
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

    @Override
    public boolean tick(StepContext context) {
        int held = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::held)
                .orElse(ChannelAim.FIRST_TICK);
        HeldSweepPace pace = pace();
        int pulseTick = pace.pulseTick(held);
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        Vec3 origin = HeldSweepOrigins.originOf(caster, pulseTick);
        double previous = pace.radiusAt(pulseTick - 1);
        double reached = pace.radiusAt(pulseTick);
        double scanned = reached + caster.position().distanceTo(origin);
        context.hostAs(EntityScanHost.class).forEachEntityWithin(SelectionShape.SPHERE, scanned, Set.copyOf(where),
                selected -> {
                    if (crossed(selected.target().position().distanceTo(origin), previous, reached)) {
                        new ProgramBehavior(steps).tick(selected);
                    }
                });
        EntityVisuals.sendToSelf(caster, sweepAt(origin, pulseTick));
        return true;
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
    public Stream<HostedStep> hostedChildren(HostKind host) {
        return steps.stream().map(child -> new HostedStep(child, HostKind.ENTITY));
    }
}
