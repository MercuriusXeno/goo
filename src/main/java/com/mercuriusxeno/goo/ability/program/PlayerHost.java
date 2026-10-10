package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pulse.ExtenderEvents;
import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the player invoking a self ability: the player
 * is the target, the thrower and the anchor at once, so a step reading the
 * thrower's look reads the player's own (decision self-delivery-runs-on-player).
 * Every read and world action runs as the struck entity host would run it
 * on the player, and like that host it has no driver for later ticks.
 *
 * A drunk brew runs its ability on this host too, carrying the brew's
 * duration (decision brew-grants-the-self-ability-for-an-hour).
 *
 * A held channel runs its ability on this host each tick of the hold,
 * carrying that tick's aim (decision flatten-disc-cursor-breaks-above-the-plane).
 *
 * A blink runs on this host carrying the face plane its press pinned
 * (decision blink-lands-safely-costed-by-distance).
 *
 * @param level         the server level
 * @param player        the invoking player
 * @param brewDuration  the drunk brew's duration in ticks, empty for a glove invocation
 * @param channelAim    the held channel's aim this tick, empty outside a channel
 * @param charge        the share of a full charge a charged ability's hold reached, 0 outside a charge
 * @param blinkPin      the face plane a blink's press pinned, empty for free aim
 */
public record PlayerHost(ServerLevel level, ServerPlayer player, OptionalInt brewDuration,
                         Optional<ChannelAim> channelAim, float charge, Optional<ChannelAim.FacePlane> blinkPin)
        implements TargetHost, ExplodeHost, EntityScanHost, ChannelHost, EffectExtendHost, FrostHost {

    /** The share of the player's height Nova emanates from. */
    private static final double HALF_HEIGHT = 0.5;

    /** The charge a host outside a charged release carries. */
    private static final float NO_CHARGE = 0f;

    /** Blocks past the interaction range a channel still breaks at, vanilla's own slack for a block break. */
    private static final double REACH_SLACK = 1.0;
    /** Log: how many devices a Pulser tick found to toggle among its cone's cells. */
    private static final String LOG_TOGGLES = "Pulser toggles {} devices among {} cells";

    /**
     * The host of a glove invocation, which carries no brew duration.
     *
     * @param level  the server level
     * @param player the invoking player
     */
    public PlayerHost(ServerLevel level, ServerPlayer player) {
        this(level, player, OptionalInt.empty(), Optional.empty(), NO_CHARGE, Optional.empty());
    }

    /**
     * The host of a glove invocation of a blink, carrying the face plane its press pinned.
     *
     * @param level    the server level
     * @param player   the invoking player
     * @param blinkPin the pinned face plane, empty for free aim
     * @return the host carrying the pin
     */
    public static PlayerHost blinking(ServerLevel level, ServerPlayer player,
            Optional<ChannelAim.FacePlane> blinkPin) {
        return new PlayerHost(level, player, OptionalInt.empty(), Optional.empty(), NO_CHARGE, blinkPin);
    }

    /**
     * The host of a drunk brew, carrying its duration.
     *
     * @param level        the server level
     * @param player       the drinking player
     * @param brewDuration the brew's duration in ticks
     */
    public PlayerHost(ServerLevel level, ServerPlayer player, OptionalInt brewDuration) {
        this(level, player, brewDuration, Optional.empty(), NO_CHARGE, Optional.empty());
    }

    /**
     * The host of one tick of a held channel.
     *
     * @param level  the server level
     * @param player the channeling player
     * @param aim    the hold's aim this tick
     * @return the host carrying the aim
     */
    public static PlayerHost channeling(ServerLevel level, ServerPlayer player, ChannelAim aim) {
        return new PlayerHost(level, player, OptionalInt.empty(), Optional.of(aim), NO_CHARGE, Optional.empty());
    }

    /**
     * The host of a charged ability's release, carrying the share of a full
     * charge its hold reached (decision nova-ring-grows-with-the-hold).
     *
     * @param level  the server level
     * @param player the releasing player
     * @param charge the share of a full charge, 0 to 1
     * @return the host carrying the charge
     */
    public static PlayerHost charged(ServerLevel level, ServerPlayer player, float charge) {
        return new PlayerHost(level, player, OptionalInt.empty(), Optional.empty(), charge, Optional.empty());
    }

    @Override
    public HostKind kind() {
        return HostKind.PLAYER;
    }

    @Override
    public LivingEntity target() {
        return player;
    }

    @Override
    public Entity thrower() {
        return player;
    }

    /**
     * Sets the player moving and clears the fall it has built up, so a
     * propelled player is not killed by the landing.
     *
     * @param motion the velocity to set, in blocks per tick
     */
    @Override
    public void push(Vec3 motion) {
        TargetHost.super.push(motion);
        player.resetFallDistance();
    }

    /**
     * The struck entity host over the player, which every read and world action runs through.
     *
     * @return the entity host with the player as target and thrower
     */
    private EntityHost asEntity() {
        return new EntityHost(level, player, player);
    }

    @Override
    public OptionalDouble read(String name) {
        if (HostVariables.CHARGE.equals(name)) {
            return OptionalDouble.of(charge);
        }
        return asEntity().read(name);
    }

    @Override
    public BlockPos position() {
        return player.blockPosition();
    }

    /** The player's middle, where Nova emanates from (decision nova-ring-grows-with-the-hold). */
    @Override
    public Vec3 frostCenter() {
        return player.position().add(0, player.getBbHeight() * HALF_HEIGHT, 0);
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        asEntity().explode(power, mode);
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return asEntity().anyEntityWithin(shape, radius, filters);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        asEntity().forEachEntityWithin(shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        asEntity().forEntity(entityId, body);
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        asEntity().pullEntitiesWithin(radius, speed);
    }

    @Override
    public void spawnParticles(ParticleBurst burst) {
        asEntity().spawnParticles(burst);
    }

    @Override
    public void playSound(SoundCue cue) {
        asEntity().playSound(cue);
    }

    @Override
    public Vec3 eye() {
        return player.getEyePosition();
    }



    @Override
    public boolean reaches(BlockPos pos) {
        return player.isWithinBlockInteractionRange(pos, REACH_SLACK);
    }

    @Override
    public Entity breaker() {
        return player;
    }

    /**
     * Toggles the device once in the player's stream hold, its door read by
     * its lower half so both halves count as one
     * (decision signal-wave-toggles-each-device-once).
     */
    @Override
    public void toggleOnceThisHold(BlockPos pos) {
        ZapDevice.handDevice(level, pos)
                .filter(device -> GooServerState.of(level.getServer()).streamHolds().touchOnce(player.getUUID(), device))
                .ifPresent(device -> ZapDevice.toggleByHand(level, device));
    }

    /**
     * Lengthens the player's timed effects by the drunk brew's duration; a
     * glove invocation lengthens nothing (decision extender-multiplies-the-next-self-duration).
     */
    @Override
    public void extendTimedEffects() {
        brewDuration.ifPresent(duration -> ExtenderEvents.extendStanding(player, duration));
    }

    /**
     * Toggles each device standing in the cells once, a door's two halves
     * counting as one device (decision pulser-toggles-rapidly-while-held).
     */
    @Override
    public void toggleEachDevice(List<BlockPos> cells) {
        List<BlockPos> devices = cells.stream().map(pos -> ZapDevice.handDevice(level, pos)).flatMap(Optional::stream)
                .distinct().toList();
        Goo.LOGGER.debug(LOG_TOGGLES, devices.size(), cells.size());
        devices.forEach(device -> ZapDevice.toggleByHand(level, device));
    }

    @Override
    public void forEachLivingIn(List<BlockPos> cells, Set<EntityFilter> filters, Consumer<TargetHost> body) {
        if (cells.isEmpty()) {
            return;
        }
        List<AABB> boxes = cells.stream().map(AABB::new).toList();
        AABB bounds = boxes.stream().reduce(AABB::minmax).orElseThrow();
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, bounds, living -> living != player
                && living.isAlive() && boxes.stream().anyMatch(living.getBoundingBox()::intersects)
                && EntityScan.passes(living, filters, player))) {
            body.accept(new EntityHost(level, living, player));
        }
    }
}
