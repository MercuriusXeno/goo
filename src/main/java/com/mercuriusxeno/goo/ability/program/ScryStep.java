package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.ScryPayload;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Glow's Scry: while held, it pings. Each ping is a sphere of light growing
 * from where the caster stood as it began, a number of blocks each tick, up
 * to its reach and then on past it while it fades, before the next ping
 * starts from wherever the caster stands then. The child steps run on every
 * living entity the front crosses within the reach, seen or behind walls.
 * The caster's client is sent the ping's origin and radius each tick, and
 * draws the sphere and the faces it reveals. Scry is
 * {@code scry growth=1 reach=96 fade=20} with a glow {@code ailment_overlay}
 * as its child (operator rulings 2026-10-09).
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 *
 * @param growth blocks the radius grows each tick of the hold
 * @param reach  how far the front reaches before it fades, in blocks
 * @param fade   how far past the reach the front travels while it fades, in blocks
 * @param where  the filters an entity must pass; empty keeps any living entity
 * @param steps  the child steps run once on each entity the front crosses
 */
public record ScryStep(double growth, double reach, double fade, List<EntityFilter> where, List<Step> steps)
        implements Step {

    private static final String NAME = "scry";
    private static final String FIELD_GROWTH = "growth";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_FADE = "fade";
    /** Where each caster's ping began, so a ping's front never moves with its caster. */
    private static final Map<UUID, Vec3> PING_ORIGINS = new ConcurrentHashMap<>();
    private static final String FIELD_WHERE = "where";
    private static final String FIELD_STEPS = "steps";

    /**
     * Codec for the step's params. The child list codec is read lazily
     * because {@link StepTypes} registers this type while building it.
     */
    public static final MapCodec<ScryStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_GROWTH).forGetter(ScryStep::growth),
            Codec.DOUBLE.fieldOf(FIELD_REACH).forGetter(ScryStep::reach),
            Codec.DOUBLE.optionalFieldOf(FIELD_FADE, 0.0).forGetter(ScryStep::fade),
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
        int pingTick = pingTick(held);
        LivingEntity caster = context.hostAs(TargetHost.class).target();
        Vec3 origin = pingTick == 1 ? caster.position() : PING_ORIGINS.getOrDefault(caster.getUUID(),
                caster.position());
        PING_ORIGINS.put(caster.getUUID(), origin);
        double radius = pingTick * growth;
        double previous = Math.min((pingTick - 1) * growth, reach);
        double reached = Math.min(radius, reach);
        double scanned = reached + caster.position().distanceTo(origin);
        context.hostAs(EntityScanHost.class).forEachEntityWithin(SelectionShape.SPHERE, scanned, Set.copyOf(where),
                selected -> {
                    if (crossed(selected.target().position().distanceTo(origin), previous, reached)) {
                        new ProgramBehavior(steps).tick(selected);
                    }
                });
        EntityVisuals.sendToSelf(caster, new ScryPayload(origin, (float) radius, (float) reach, (float) fade));
        return true;
    }

    /**
     * The tick of the current ping a tick of the hold falls on: pings run one
     * after another, each growing to its reach and on through its fade.
     *
     * @param heldTicks the hold's age, 1 on its first tick
     * @return the ping's tick, 1 on its first
     */
    int pingTick(int heldTicks) {
        int pingTicks = (int) Math.ceil((reach + fade) / growth);
        return Math.floorMod(heldTicks - 1, Math.max(1, pingTicks)) + 1;
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
