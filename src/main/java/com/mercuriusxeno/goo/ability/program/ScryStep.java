package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Astral's Scry, an instant cast: a press sends one ring, which leaves where
 * the caster stood and crosses its reach in two seconds with no key held, and
 * a press while a ring still travels sends another beside it. The child
 * steps run on every living entity a front crosses, seen or behind walls,
 * once for each front. The caster's client is sent each live ring each tick and lights
 * the walls each front hits in the goo type's colors, and each pulse pings
 * as it leaves, an amethyst resonance (operator ruling 2026-11-06). Scry is
 * {@code scry goo=astral reach=32} with an astral {@code ailment_overlay}
 * as its child.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 * decision scry-reach-drops-to-thirty-two
 * decision scry-is-instant-and-no-longer-channeled
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
    /** The ping each pulse sounds as it leaves: the amethyst block's resonance, pitched up to a sonar ping. */
    private static final float PING_VOLUME = 1f;
    private static final float PING_PITCH = 1.6f;
    /** Ticks each front takes to reach Scry's reach: two seconds. */
    private static final int CROSSING_TICKS = 40;

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

    /**
     * Scry's front crosses its reach in two seconds, slow enough to see the
     * echo come back off the cavern.
     * decision scry-ring-crosses-its-reach-in-two-seconds
     */
    @Override
    public HeldSweepPace pace() {
        return HeldSweepPace.crossing(reach, CROSSING_TICKS);
    }

    /**
     * Scry's fronts outline every mob they cross through walls, beside the
     * astral outline its child steps lay on them (operator ruling 2026-11-06).
     * decision scry-sphere-reveals-faces-and-glistens-mobs
     */
    @Override
    public boolean paintsMobs() {
        return true;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        ScryRings.SERVER.cast(new Caster(this, caster, caster.level(), context.hostAs(EntityScanHost.class)), pace());
        return true;
    }

    /**
     * The caster of a ring Scry sent: the ring's ping, its strike on the
     * entities its front crosses, and its radius sent to the caster's client.
     *
     * @param scry   the step that cast the ring
     * @param caster the caster
     * @param castIn the level the ring was cast in
     * @param scan   the entity scan around the caster
     */
    private record Caster(ScryStep scry, LivingEntity caster, Level castIn, EntityScanHost scan)
            implements ScryRings.RingCaster {

        @Override
        public UUID id() {
            return caster.getUUID();
        }

        @Override
        public Vec3 position() {
            return caster.position();
        }

        @Override
        public long gameTime() {
            return caster.level().getGameTime();
        }

        @Override
        public boolean gone() {
            return caster.isRemoved() || caster.level() != castIn;
        }

        @Override
        public void ping() {
            caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, PING_VOLUME, PING_PITCH);
        }

        @Override
        public void strike(HeldSweepPulses.Pulse pulse, double previous, double reached) {
            strikeCrossed(new Strike(scan, Set.copyOf(scry.where()), caster.position(), pulse.origin()), previous,
                    reached, selected -> new ProgramBehavior(scry.steps()).tick(selected));
        }

        @Override
        public void send(HeldSweepPulses.Pulse pulse, long gameTime) {
            scry.sendSweeps(List.of(pulse), gameTime, payload -> EntityVisuals.sendToSelf(caster, payload));
        }
    }

    /**
     * Where one front strikes from: the scan around the caster, the filters
     * an entity must pass, where the caster stands and where the front's
     * pulse began.
     *
     * @param scan     the entity scan around the caster
     * @param filters  the filters an entity must pass
     * @param casterAt where the caster stands
     * @param origin   where the front's pulse began
     */
    public record Strike(EntityScanHost scan, Set<EntityFilter> filters, Vec3 casterAt, Vec3 origin) {
    }

    /**
     * Hands every entity one front crossed this tick to the strike, so an
     * entity between two fronts is crossed by each once. The scan reaches
     * the front's radius past the caster's distance from the pulse's
     * origin, since it scans around the caster.
     *
     * @param strike   where the front strikes from
     * @param previous the front's radius last tick
     * @param reached  its radius this tick
     * @param struck   runs the child steps on an entity crossed
     */
    public static void strikeCrossed(Strike strike, double previous, double reached, Consumer<TargetHost> struck) {
        if (reached <= previous) {
            return;
        }
        double scanned = reached + strike.casterAt().distanceTo(strike.origin());
        HeldSweepSections.profiled(HeldSweepSections.SCRY_STRIKE, () -> strike.scan().forEachEntityWithin(
                SelectionShape.SPHERE, scanned, strike.filters(), selected -> {
                    if (crossed(selected.target().position().distanceTo(strike.origin()), previous, reached)) {
                        struck.accept(selected);
                    }
                }));
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
        return Set.of(HostCapability.TARGET, HostCapability.ENTITY_SCAN);
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
