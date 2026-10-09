package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
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
 * @param level         the server level
 * @param player        the invoking player
 * @param brewDuration  the drunk brew's duration in ticks, empty for a glove invocation
 * @param channelAim    the held channel's aim this tick, empty outside a channel
 */
public record PlayerHost(ServerLevel level, ServerPlayer player, OptionalInt brewDuration,
                         Optional<ChannelAim> channelAim)
        implements TargetHost, ExplodeHost, EntityScanHost, ChannelHost, SiphonHost {

    /** Blocks past the interaction range a channel still breaks at, vanilla's own slack for a block break. */
    private static final double REACH_SLACK = 1.0;

    /**
     * The host of a glove invocation, which carries no brew duration.
     *
     * @param level  the server level
     * @param player the invoking player
     */
    public PlayerHost(ServerLevel level, ServerPlayer player) {
        this(level, player, OptionalInt.empty(), Optional.empty());
    }

    /**
     * The host of a drunk brew, carrying its duration.
     *
     * @param level        the server level
     * @param player       the drinking player
     * @param brewDuration the brew's duration in ticks
     */
    public PlayerHost(ServerLevel level, ServerPlayer player, OptionalInt brewDuration) {
        this(level, player, brewDuration, Optional.empty());
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
        return new PlayerHost(level, player, OptionalInt.empty(), Optional.of(aim));
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
        return asEntity().read(name);
    }

    @Override
    public BlockPos position() {
        return player.blockPosition();
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

    @Override
    public void holdDrink() {
        drinks().hold(player);
    }

    /**
     * The standing blocks in the cone from the eye toward the cursor, nearest
     * first; none outside a held channel
     * (decision unmake-waves-dissolve-by-crucible-cost).
     */
    @Override
    public List<BlockPos> siphonCone(double radius) {
        return channelAim().map(aim -> {
            Vec3 line = aim.aimPoint().subtract(eye());
            if (line.lengthSqr() == 0) {
                return List.<BlockPos>of();
            }
            Vec3 reach = eye().add(line.normalize().scale(SiphonRule.RANGE));
            return CalcifyStep.blocksInCone(eye(), reach, SiphonRule.coneDegrees(radius)).stream()
                    .filter(pos -> !level.getBlockState(pos).isAir())
                    .toList();
        }).orElse(List.of());
    }

    /**
     * The goo a block holds, for one the drink can take: not one already
     * liquifying, and not one no hand can break.
     */
    @Override
    public @Nullable GooValue siphonValue(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Drinks.Drink drink = drinks().of(player);
        if (state.is(GooBlocks.MELTING_BLOCK.get()) || state.getDestroySpeed(level, pos) < 0
                || drink != null && drink.siphoning(pos)) {
            return null;
        }
        return ValuedBlocks.valueAt(level, pos);
    }

    @Override
    public boolean burnUnstable(int amount) {
        if (!GooSourceScanner.hasEnough(player, GooTypes.UNSTABLE, amount)) {
            return false;
        }
        GooSourceScanner.deplete(player, GooTypes.UNSTABLE, amount);
        return true;
    }

    @Override
    public void siphon(BlockPos pos, GooContents goo, int ticks) {
        Drinks.Drink drink = drinks().of(player);
        if (drink == null) {
            return;
        }
        long now = level.getGameTime();
        BlockMelts.siphon(level, pos, now + ticks);
        drink.start(pos, new Drinks.Siphon(goo, now, now + ticks));
    }

    private Drinks drinks() {
        return GooServerState.of(level.getServer()).drinks();
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
