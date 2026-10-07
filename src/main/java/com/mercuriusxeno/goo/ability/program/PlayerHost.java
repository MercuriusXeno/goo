package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
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
        implements TargetHost, ExplodeHost, EntityScanHost, ChannelHost {

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
    public boolean blockIn(BlockPos pos, TagKey<Block> tag) {
        return level.getBlockState(pos).is(tag);
    }

    @Override
    public boolean airAt(BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    @Override
    public boolean reaches(BlockPos pos) {
        return player.isWithinBlockInteractionRange(pos, REACH_SLACK);
    }

    /**
     * Breaks a block as the channeling player and drops its loot as a
     * pickaxe would harvest it, so stone yields cobblestone where the glove
     * in hand would yield nothing.
     *
     * @param pos the block
     */
    @Override
    public void breakBlock(BlockPos pos) {
        ItemStack harvestTool = new ItemStack(Items.IRON_PICKAXE);
        Block.dropResources(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), player, harvestTool);
        level.destroyBlock(pos, false, player);
    }
}
