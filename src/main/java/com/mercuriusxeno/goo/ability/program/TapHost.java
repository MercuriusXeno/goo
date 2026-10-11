package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.pulse.ZapDevice;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.ShardFallPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the block a tap's drip lands on: world actions
 * anchor at the struck face's center, and a placed block goes into the
 * block beyond that face. A tap has no will and no target, and a drip lands
 * in one tick with nothing ticking it afterwards, so this host implements
 * neither {@link TargetHost} nor {@link TickingHost}
 * (decision tap-ability-tagged-program). It scans the entities around the
 * struck face, so a drip acts on what stands where it lands.
 * vitality-drip-heals-below
 *
 * @param level   the server level
 * @param tapPos  the tap the drip fell from
 * @param landing the block the drip landed on
 * @param face    the landing block's face the drip struck
 */
public record TapHost(ServerLevel level, BlockPos tapPos, BlockPos landing, Direction face)
        implements ExplodeHost, AnchoredWorldHost, PlaceBlockHost, EntityScanHost, DripHost, ConvokeHost,
        DeviceToggleHost, MobSpawnHost, FrostHost, TickBlockHost, ShardFallHost {

    private static final double HALF = 0.5;
    /** Half the width of the column a shard falls down, narrower than a block. */
    private static final double SHARD_COLUMN_HALF_WIDTH = 0.4;

    /**
     * A tap host whose tap stands right beyond the struck face, as a tap
     * dripping straight onto the block below it does.
     *
     * @param level   the server level
     * @param landing the block the drip landed on
     * @param face    the landing block's face the drip struck
     */
    public TapHost(ServerLevel level, BlockPos landing, Direction face) {
        this(level, landing.relative(face), landing, face);
    }

    /**
     * The center of a block's face, where a tap host anchors its world actions.
     *
     * @param landing the block
     * @param face    the face
     * @return the face center
     */
    public static Vec3 faceCenter(BlockPos landing, Direction face) {
        return Vec3.atCenterOf(landing).add(face.getStepX() * HALF, face.getStepY() * HALF, face.getStepZ() * HALF);
    }

    @Override
    public Vec3 anchor() {
        return faceCenter(landing, face);
    }

    /**
     * The cell beyond the struck face, where a drip conjures its mob.
     * spawn-drip-rolls-a-fresh-spawn
     *
     * @return the cell
     */
    @Override
    public BlockPos spawnCell() {
        return landing.relative(face);
    }

    @Override
    public Vec3 morphFrom() {
        return anchor();
    }

    @Override
    public Vec3 frostCenter() {
        return anchor();
    }

    @Override
    public Direction.Axis burstAxis() {
        return face.getAxis();
    }

    @Override
    public HostKind kind() {
        return HostKind.TAP;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return landing;
    }

    @Override
    public int countDrip() {
        return GooServerState.of(level.getServer()).tapDripCounts().countDrip(level.dimension(), landing);
    }

    @Override
    public void resetDrips() {
        GooServerState.of(level.getServer()).tapDripCounts().reset(level.dimension(), landing);
    }

    /**
     * Grows a pointed dripstone tip under the landing block, or under the
     * stalactite already hanging from it, when that cell stands open; the
     * tip it extends thickens through its own shape update.
     */
    @Override
    public void growStalactite() {
        BlockPos below = landing.below();
        while (level.getBlockState(below).is(Blocks.POINTED_DRIPSTONE)) {
            below = below.below();
        }
        if (level.getBlockState(below).isAir()) {
            level.setBlock(below, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN), Block.UPDATE_ALL);
        }
    }

    /**
     * The block the drip landed on, which Tick's tap ticks faster.
     * tick-drip-splashes-a-small-tick-effect
     */
    @Override
    public Optional<BlockPos> tickedBlock() {
        return Optional.of(landing);
    }

    @Override
    public void tickBlock(BlockPos pos, int times) {
        BlockTicking.tickBlockEntity(level, pos, times);
    }

    /**
     * Toggles the device below the tap: the landing block when it is one, a
     * closed trapdoor or door the drip struck, else the device standing on
     * the struck face, a lever or button the drip fell through
     * (decision pulser-drip-toggles-the-block-below).
     */
    @Override
    public void toggleDevice() {
        ZapDevice.handDevice(level, landing)
                .or(() -> ZapDevice.handDevice(level, landing.relative(face)))
                .ifPresent(device -> ZapDevice.toggleByHand(level, device));
    }

    @Override
    public long gameTime() {
        return level.getGameTime();
    }

    /**
     * Pulls a mob from the landing's chunk to stand in the cell beyond the
     * struck face, under the tap.
     * decision convoke-drip-rolls-a-small-chance
     */
    @Override
    public boolean convokeFromChunk() {
        return ChunkConvoke.convoke(level, Vec3.atBottomCenterOf(landing.relative(face)));
    }

    /**
     * Drops a glass shard from the tap's spigot down the column to the
     * landing, striking the highest living mob standing in it.
     * decision shards-drip-falls-as-a-glass-shard
     */
    @Override
    public OptionalInt fallShard() {
        Vec3 spigot = Vec3.atBottomCenterOf(tapPos);
        Vec3 floor = anchor();
        AABB column = new AABB(spigot.x - SHARD_COLUMN_HALF_WIDTH, floor.y, spigot.z - SHARD_COLUMN_HALF_WIDTH,
                spigot.x + SHARD_COLUMN_HALF_WIDTH, spigot.y, spigot.z + SHARD_COLUMN_HALF_WIDTH);
        Optional<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class, column, LivingEntity::isAlive)
                .stream().max(Comparator.comparingDouble(mob -> mob.getBoundingBox().maxY));
        Vec3 hit = struck.map(mob -> new Vec3(spigot.x, Math.min(spigot.y, mob.getBoundingBox().maxY), spigot.z))
                .orElse(floor);
        EntityVisuals.sendToWatchersOf(level, spigot, new ShardFallPayload(spigot, hit));
        return struck.map(mob -> OptionalInt.of(mob.getId())).orElseGet(OptionalInt::empty);
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        GooExplosion.detonate(level, anchor(), power, mode, GooExplosion.Look.vanilla());
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, anchor(), shape, radius, filters, null);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        BlockAnchoredActions.forEachEntityWithin(level, anchor(), shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        BlockAnchoredActions.forEntity(level, entityId, body);
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, anchor(), radius, speed, null);
    }

    @Override
    public void liftEntitiesInColumn(double radius, double height, double speed) {
        EntityLift.liftInColumn(level, anchor(), radius, height, speed);
    }

    @Override
    public void rideShaftAbove(int cap, double rise, double sink) {
        EntityLift.rideShaft(level, BlockPos.containing(anchor()).above(), cap, rise, sink);
    }

    /**
     * Writes the block into the cell beyond the struck face when that cell
     * can be replaced, so a drip never overwrites the tap it fell from or
     * any other standing block.
     */
    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        BlockAnchoredActions.placeBeyondFace(level, landing, face, block, state);
    }

}
