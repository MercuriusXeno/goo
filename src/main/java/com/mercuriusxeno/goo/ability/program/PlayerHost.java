package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.ChunkWatchers;
import com.mercuriusxeno.goo.network.UnmakePayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
        implements TargetHost, ExplodeHost, EntityScanHost, ChannelHost, UnmakeHost {

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

    /**
     * The blocks a stream's channel holds: every standing block whose center
     * lies in the cone and which the eye sees, so a wall's face is held and
     * what stands behind it is not; none outside a held channel
     * (decision unmake-waves-dissolve-by-crucible-cost).
     */
    @Override
    public List<BlockPos> unmadeBlocks() {
        return channelAim().map(aim -> CalcifyStep.blocksInCone(eye(), aim.aimPoint(), aim.coneDegrees()).stream()
                .filter(pos -> !level.getBlockState(pos).isAir() && eyeSees(pos))
                .toList()).orElse(List.of());
    }

    /**
     * Whether the sight line from the eye to a block's center first meets that block.
     *
     * @param pos the block
     * @return true when nothing stands between the eye and the block
     */
    private boolean eyeSees(BlockPos pos) {
        BlockHitResult hit = level.clip(new ClipContext(eye(), Vec3.atCenterOf(pos), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    @Override
    public @Nullable GooValue unmadeValue(BlockPos pos) {
        return ValuedBlocks.valueAt(level, pos);
    }

    /**
     * Counts this tick of the stream's hold on the block, a block the stream
     * left starting over (decision unmake-waves-dissolve-by-crucible-cost).
     */
    @Override
    public int countUnmakeWork(BlockPos pos) {
        return GooServerState.of(level.getServer()).streamHolds()
                .advanceBlock(player.getUUID(), pos, level.getServer().getTickCount());
    }

    @Override
    public void showUnmaking(BlockPos pos, float fraction) {
        ChunkWatchers.send(level, pos, new UnmakePayload(pos, fraction));
    }

    @Override
    public void unmake(BlockPos pos, GooContents yield) {
        level.removeBlock(pos, false);
        GooStacks.dropAll(yield, level, pos);
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
