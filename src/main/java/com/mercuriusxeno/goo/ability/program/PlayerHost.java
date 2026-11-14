package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.ability.zone.ConjuredDoors;
import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
import com.mercuriusxeno.goo.block.ability.SlickLayPace;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the player invoking a self ability: the player
 * is the target, the thrower and the anchor at once, so a step reading the
 * thrower's look reads the player's own (decision self-delivery-runs-on-player).
 * Every read and world action runs as the struck entity host would run it
 * on the player, and like that host it has no driver for later ticks.
 *
 * A held channel runs its ability on this host each tick of the hold,
 * carrying that tick's aim (decision flatten-disc-cursor-breaks-above-the-plane).
 *
 * @param level         the server level
 * @param player        the invoking player
 * @param channelAim    the held channel's aim this tick, empty outside a channel
 * @param charge        the share of a full charge a charged ability's hold reached, 0 outside a charge
 * @param placementPrice what each thing a held stream lays pays as it lands, free outside a stream draining on
 *                       placement
 */
public record PlayerHost(ServerLevel level, ServerPlayer player, Optional<ChannelAim> channelAim, float charge,
                         PlacementPrice placementPrice)
        implements TargetHost, ExplodeHost, EntityScanHost, ChannelHost, FrostHost, ZoneHost {

    /** The share of the player's height Nova emanates from. */
    private static final double HALF_HEIGHT = 0.5;

    /** The charge a host outside a charged release carries. */
    private static final float NO_CHARGE = 0f;

    /** Blocks past the interaction range a channel still breaks at, vanilla's own slack for a block break. */
    public static final double REACH_SLACK = 1.0;
    /** Log: how many devices a Tick pulse found to toggle among its cone's cells. */
    private static final String LOG_TOGGLES = "Tick toggles {} devices and pulses {} receivers among {} cells";

    /**
     * The host of a glove invocation.
     *
     * @param level  the server level
     * @param player the invoking player
     */
    public PlayerHost(ServerLevel level, ServerPlayer player) {
        this(level, player, Optional.empty(), NO_CHARGE, PlacementPrice.FREE);
    }

    /**
     * The host of one tick of a held channel paying by the tick.
     *
     * @param level  the server level
     * @param player the channeling player
     * @param aim    the hold's aim this tick
     * @return the host carrying the aim
     */
    public static PlayerHost channeling(ServerLevel level, ServerPlayer player, ChannelAim aim) {
        return channeling(level, player, aim, PlacementPrice.FREE);
    }

    /**
     * The host of one tick of a held stream, carrying what each thing it lays pays as it lands.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param level  the server level
     * @param player the streaming player
     * @param aim    the hold's aim this tick
     * @param price  what each placement pays, free for a stream paying by the tick
     * @return the host carrying the aim and the price
     */
    public static PlayerHost channeling(ServerLevel level, ServerPlayer player, ChannelAim aim, PlacementPrice price) {
        return new PlayerHost(level, player, Optional.of(aim), NO_CHARGE, price);
    }

    /**
     * The host a stream's drip lands through once it strikes, whether or not
     * its caster still holds the stream, carrying the price what it lays pays.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param level  the level the drip struck in
     * @param caster the player who threw the drip
     * @param price  what each placement pays
     * @return the host carrying the price and no aim
     */
    public static PlayerHost landing(ServerLevel level, ServerPlayer caster, PlacementPrice price) {
        return new PlayerHost(level, caster, Optional.empty(), NO_CHARGE, price);
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
        return new PlayerHost(level, player, Optional.empty(), charge, PlacementPrice.FREE);
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
    public void explode(float power, double reach, boolean gathers, ExplosionMode mode) {
        asEntity().explode(power, reach, gathers, mode);
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
    public void liftEntitiesInColumn(double radius, double height, double speed) {
        asEntity().liftEntitiesInColumn(radius, height, speed);
    }

    @Override
    public void rideShaftAbove(int cap, double rise, double sink) {
        asEntity().rideShaftAbove(cap, rise, sink);
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
    public boolean gloveOnTheRight() {
        return KnifeRainSling.gloveOnTheRight(player);
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
     * Toggles each hand device standing in the cells once, a door's two
     * halves counting as one device, and gives each repeater and redstone
     * receiver there one Zap pulse.
     * pulser-toggles-rapidly-while-held
     * tick-pulses-anything-a-signal-runs-while-held
     */
    @Override
    public void toggleEachDevice(List<BlockPos> cells) {
        List<BlockPos> devices = cells.stream().map(pos -> ZapDevice.handDevice(level, pos)).flatMap(Optional::stream)
                .distinct().toList();
        List<BlockPos> receivers = cells.stream().filter(pos -> ZapDevice.pulsedByTick(level.getBlockState(pos)))
                .map(BlockPos::immutable).distinct().toList();
        Goo.LOGGER.debug(LOG_TOGGLES, devices.size(), receivers.size(), cells.size());
        devices.forEach(device -> ZapDevice.toggleByHand(level, device));
        receivers.forEach(receiver -> ZapDevice.pulseInPlace(level, receiver));
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

    @Override
    public boolean standsInPocket() {
        return level.dimension() == ZoneRooms.POCKET;
    }

    /** Carries the player into their Zone room (decision
     * outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform). */
    @Override
    public void showCharge(int held, int charge) {
        ZoneRooms.showCharge(player, held, charge);
    }

    /** Conjures the door before the player (decision
     * channeling-to-the-pocket-conjures-a-door-that-envelops-the-player). */
    @Override
    public void conjureDoor(int held, int charge) {
        ConjuredDoors.conjure(player, held, charge);
    }

    @Override
    public void enterPocket(int cost) {
        if (GooSourceScanner.hasEnough(player, GooTypes.VOID, cost) && ZoneRooms.enter(player)) {
            GooSourceScanner.deplete(player, GooTypes.VOID, cost);
        }
    }

    /** Grows the player's Zone room on all sides at once (decision
     * outsider-room-grows-fractionally-on-all-sides-at-once). */
    @Override
    public void feedPlatform(int ringTicks, int base, int perDistance) {
        int feed = ZoneRooms.feedOf(player, ringTicks, base, perDistance);
        if (GooSourceScanner.hasEnough(player, GooTypes.VOID, feed)) {
            GooSourceScanner.deplete(player, GooTypes.VOID, feed);
            ZoneRooms.feedPlatform(player, feed, base, perDistance);
        }
    }

    @Override
    public HoldMarks holdMarks() {
        return GooServerState.of(level.getServer()).streamHolds().marks(player.getUUID());
    }

    @Override
    public GradualBreak gradualBreak() {
        return holdMarks().gradualBreak();
    }

    @Override
    public double channelerTravelSpeed() {
        return player.getKnownMovement().horizontalDistance();
    }

    @Override
    public double channelerUnadjustedSpeed() {
        return SlickLayPace.unadjustedSpeed(player.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    @Override
    public boolean channelerStandsOnSlick() {
        return level.getBlockState(player.blockPosition()).is(GooBlocks.KINETIC_SLICK.get());
    }
}
