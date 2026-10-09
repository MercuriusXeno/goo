package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.ScryPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Glow's Scry: a sphere of light grows from the channeling player, a number
 * of blocks each tick of the hold up to its reach, and the child steps run
 * on every living entity its front crosses this tick, seen or behind walls.
 * The caster's client is sent the radius, and draws the sphere and the faces
 * it reveals. Scry is {@code scry growth=1 reach=48} with a glow
 * {@code ailment_overlay} as its child.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 *
 * @param growth blocks the radius grows each tick of the hold
 * @param reach  the radius's cap in blocks
 * @param where  the filters an entity must pass; empty keeps any living entity
 * @param steps  the child steps run once on each entity the front crosses
 */
public record ScryStep(double growth, double reach, List<EntityFilter> where, List<Step> steps) implements Step {

    private static final String NAME = "scry";
    private static final String FIELD_GROWTH = "growth";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<ScryStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_GROWTH).forGetter(ScryStep::growth),
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
    public boolean tick(StepContext context) {
        int held = context.hostAs(ChannelHost.class).channelAim().map(ChannelAim::heldTicks)
                .orElse(ChannelAim.FIRST_TICK);
        double radius = radiusAt(held);
        double previous = radiusAt(held - 1);
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        Vec3 center = caster.position();
        context.hostAs(EntityScanHost.class).forEachEntityWithin(SelectionShape.SPHERE, radius, Set.copyOf(where),
                selected -> {
                    if (crossed(selected.target().position().distanceTo(center), previous, radius)) {
                        new ProgramBehavior(steps).tick(selected);
                    }
                });
        EntityVisuals.sendToSelf(caster, new ScryPayload((float) radius));
        return true;
    }

    /**
     * The sphere's radius at an age of the hold: growing each tick, capped at the reach.
     *
     * @param heldTicks the hold's age, 1 on its first tick
     * @return the radius in blocks, never below zero
     */
    double radiusAt(int heldTicks) {
        return Math.clamp(heldTicks * growth, 0, reach);
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
