package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the living entity a thrown goo struck: it
 * hands the target to the effect steps, which act on it (decision
 * step-tick-holds-effect); reads answer its health and its distance
 * from the thrower, and world actions anchor at the target. A goo lands
 * in one tick and nothing ticks an entity afterwards, so this host has no
 * {@link HostCapability#TICKING} and a program with a waiting step refuses
 * at load (decision host-agnostic-runtime).
 *
 * <p>A standing field's strike runs on a host naming no thrower and naming
 * the player who placed the field, whom its hits credit and nothing else
 * reads, so the placer's own field strikes them, and the distance, the
 * teleport, the lead and the rest read the strike as they did before it
 * credited anyone.
 * standing-goo-thing-credits-whoever-placed-it
 * crediting-a-goo-hit-changes-nothing-else
 *
 * @param level   the server level
 * @param target  the struck entity
 * @param thrower the entity that threw the goo, or null when unknown
 * @param share   the share of the hit the target takes, one for a direct hit
 * @param placer  the player who placed the standing field striking the target, or null for none
 */
public record EntityHost(ServerLevel level, LivingEntity target, @Nullable Entity thrower, double share,
                         @Nullable Entity placer)
        implements TargetHost, ExplodeHost, EntityScanHost {

    private static final double BODY_CENTER = 0.5;
    /** The share a direct hit takes: all of it. */
    public static final double WHOLE_HIT = 1;

    /**
     * The host of a direct hit, which takes the whole of it.
     *
     * @param level   the server level
     * @param target  the struck entity
     * @param thrower the entity that threw, or null
     */
    public EntityHost(ServerLevel level, LivingEntity target, @Nullable Entity thrower) {
        this(level, target, thrower, WHOLE_HIT);
    }

    /**
     * The host of a thrown hit the target takes a share of.
     *
     * @param level   the server level
     * @param target  the struck entity
     * @param thrower the entity that threw, or null
     * @param share   the share of the hit the target takes
     */
    public EntityHost(ServerLevel level, LivingEntity target, @Nullable Entity thrower, double share) {
        this(level, target, thrower, share, null);
    }

    /**
     * The host of a standing field's strike, which no one threw and whose hits credit the field's placer.
     *
     * @param level  the server level
     * @param target the struck entity
     * @param placer the player who placed the field, or null for none
     * @return the host
     */
    public static EntityHost struckByField(ServerLevel level, LivingEntity target, @Nullable Entity placer) {
        return new EntityHost(level, target, null, WHOLE_HIT, placer);
    }

    @Override
    public @Nullable Entity creditedTo() {
        return thrower != null ? thrower : placer;
    }

    @Override
    public HostKind kind() {
        return HostKind.ENTITY;
    }

    @Override
    public LivingEntity target() {
        return target;
    }

    @Override
    public @Nullable Entity thrower() {
        return thrower;
    }

    @Override
    public OptionalDouble read(String name) {
        return switch (name) {
            case HostVariables.HEALTH -> OptionalDouble.of(target.getHealth());
            case HostVariables.MAX_HEALTH -> OptionalDouble.of(target.getMaxHealth());
            case HostVariables.DISTANCE -> OptionalDouble.of(distanceFromThrower());
            case HostVariables.UNDEAD -> flag(target.isInvertedHealAndHarm());
            default -> readOther(name);
        };
    }

    /**
     * Reads the variables past the target's health and kind: whether it
     * sprints, the share of the hit it takes, and its counters.
     *
     * @param name the variable name
     * @return the value, or empty for a name bound to none
     */
    private OptionalDouble readOther(String name) {
        return switch (name) {
            case HostVariables.SPRINTING -> flag(isSprintingPlayer());
            case HostVariables.SHARE -> OptionalDouble.of(share);
            default -> readCounter(name);
        };
    }

    /**
     * Reads a counter the target keeps, named in an expression by its id;
     * a name that is no counter id is unbound.
     *
     * @param name the variable name
     * @return the counter's value, or empty for a name that is no counter id
     */
    private OptionalDouble readCounter(String name) {
        if (!HostVariables.isCounter(name)) {
            return OptionalDouble.empty();
        }
        Identifier id = Identifier.tryParse(name);
        return id == null ? OptionalDouble.empty() : OptionalDouble.of(counters().read(id));
    }

    /**
     * Returns the counters the target keeps, empty for a target never counted.
     *
     * @return the counters
     */
    public EntityCounters counters() {
        return target.getData(GooAttachments.ENTITY_COUNTERS);
    }

    /**
     * Reads a target predicate as the variable value an expression weighs by.
     *
     * @param holds whether the predicate holds
     * @return one when it holds, zero otherwise
     */
    private static OptionalDouble flag(boolean holds) {
        return OptionalDouble.of(holds ? 1 : 0);
    }

    /**
     * Tests whether the target is a player sprinting; only a player's
     * sprint doubles the Shatter cloud's shred rate.
     *
     * @return true for a sprinting player
     */
    private boolean isSprintingPlayer() {
        return target instanceof Player player && player.isSprinting();
    }

    /**
     * Measures the target's distance from the thrower.
     *
     * @return the distance in blocks, zero with no thrower
     */
    private double distanceFromThrower() {
        return thrower == null ? 0 : target.position().distanceTo(thrower.position());
    }

    @Override
    public BlockPos position() {
        return target.blockPosition();
    }

    /**
     * Explodes at the entity with vanilla's look whatever the step wrote, the
     * look an entity's explosion always showed: Blast alone takes the written look.
     * Each hit names the thrower, or a standing field's placer.
     * blast-alone-takes-the-new-explosion-look
     * direct-goo-hit-credits-its-caster
     * goo-hit-keeps-its-kind-and-names-the-player-behind-it
     */
    @Override
    public void explode(float power, double reach, boolean gathers, ExplosionMode mode, ExplosionLook look) {
        GooExplosion.detonate(level, target.position(), power, new GooExplosion.Cut(reach, gathers), mode,
                GooExplosion.Look.vanilla(), creditedTo());
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, target.position(), shape, radius, filters, target, thrower);
    }

    /**
     * Hands the body each living thing in the volume every filter keeps, the
     * thrower standing as the caster {@code not_own_minion} spares the minions of.
     * drain-skips-your-rallied-and-charmed-mobs
     */
    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        EntityScan.forEachLivingWithin(level, target.position(), shape, radius, filters, target, thrower,
                living -> body.accept(new EntityHost(level, living, thrower, WHOLE_HIT, placer)));
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        if (level.getEntity(entityId) instanceof LivingEntity living && living.isAlive()) {
            body.accept(new EntityHost(level, living, thrower, WHOLE_HIT, placer));
        }
    }

    @Override
    public void spawnParticles(ParticleBurst burst) {
        SimpleParticles.resolve(burst.particle()).ifPresent(particle -> level.sendParticles(particle,
                target.getX(), target.getY(BODY_CENTER) + burst.lift(), target.getZ(),
                burst.count(), burst.spreadAcross(), burst.spreadAlong(), burst.spreadAcross(), burst.speed()));
    }

    @Override
    public void playSound(SoundCue cue) {
        // goo-sound-at-the-listener-reaches-both-ears: a player's body centre sits under their eye
        SoundPlays.playAtHeadOf(level, target, new Vec3(target.getX(), target.getY(BODY_CENTER), target.getZ()), cue);
    }


}
