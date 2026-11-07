package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A hunk of blocks lifted out of the world and carried as one entity: each
 * block keeps its offset from the hunk's origin cell, whose bottom center the
 * entity stands at, and draws there as it moves.
 *
 * A rising hunk climbs from its start to its target over its rise time,
 * eased so it starts and stops gently, and settles its blocks into the cells
 * it reached when the rise ends. Both sides run the rise from the spawn data,
 * so the client draws it without waiting on the server's position updates.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 *
 * A rising hunk is solid: it carries whatever stands on its top up with it
 * each tick, on both sides so a player's own client lifts them too, and
 * leaves them standing on the settled stone.
 * decision spire-carries-its-riders-up-with-the-rising-stone
 *
 * A lifted hunk hangs where a grab's hold puts it; let go or thrown, it flies
 * under gravity, and whatever it strikes, block or mob, it crushes on impact
 * before it breaks apart, dropping each of its blocks as the block itself.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public class BlockHunk extends Entity implements IEntityWithComplexSpawn {

    private static final String TAG_BLOCKS = "Blocks";
    private static final String TAG_RISING = "Rising";
    private static final String TAG_START = "Start";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_RISE_TICKS = "RiseTicks";
    private static final String TAG_AGE = "Age";
    private static final int DUST_PER_BLOCK = 6;
    private static final double DUST_SPREAD = 0.4;
    private static final double DUST_SPEED = 0.1;
    private static final double HALF = 0.5;
    /** Smoothstep's cubic weights: 3t^2 - 2t^3. */
    private static final double SMOOTHSTEP_SQUARE = 3;
    private static final double SMOOTHSTEP_CUBE = 2;
    /** A flying hunk's fall, in blocks a tick per tick, a falling block's own. */
    private static final double GRAVITY = 0.04;
    /** A flying hunk's air drag each tick, a falling block's own. */
    private static final double DRAG = 0.98;
    /** The least speed, in blocks a tick, at which an impact crushes what it strikes. */
    static final double CRUSH_SPEED = 0.3;
    /** How far past the face it strikes an impact crushes, in blocks. */
    private static final double CRUSH_DEPTH = 1;
    /** The hardest block an impact crushes: obsidian and harder stand. */
    private static final float CRUSHABLE_BELOW = 50f;
    /** The damage a struck mob takes for each block the hunk carries. */
    private static final float DAMAGE_PER_BLOCK = 1f;
    /** The most damage one impact deals a mob. */
    private static final float MOST_DAMAGE = 20f;
    /** The longest a let-go hunk flies before it breaks apart where it is, in ticks. */
    private static final int MOST_FLIGHT_TICKS = 200;
    /** How far a rider's feet may stand from the hunk's top and still ride it, in blocks. */
    static final double RIDER_FOOTING = 0.1;
    /** Shrinks the crush box off the faces the hunk only touches. */
    private static final double TOUCH_SLACK = 1e-3;

    /**
     * One block of a hunk.
     *
     * @param offset the block's cell, from the hunk's origin cell
     * @param state  the block
     */
    public record HunkBlock(BlockPos offset, BlockState state) {

        /** Codec a saved hunk stores its blocks by. */
        public static final Codec<HunkBlock> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(HunkBlock::offset),
                BlockState.CODEC.fieldOf("state").forGetter(HunkBlock::state)
        ).apply(inst, HunkBlock::new));
    }

    private List<HunkBlock> blocks = List.of();
    private boolean rising = true;
    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private int riseTicks = 1;
    private int age;
    /** Ticks the hunk has flown free since a hold last stood on it. */
    private int flightTicks;

    /**
     * The constructor the entity type builds a loaded or synced hunk with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public BlockHunk(EntityType<? extends BlockHunk> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Raises blocks from below their cells into them: the hunk appears a rise
     * below its target, climbs to it over the rise time and settles its blocks there.
     *
     * @param level     the server level
     * @param origin    the hunk's origin cell, where the block at offset zero settles
     * @param blocks    the blocks, each by its offset from the origin cell
     * @param rise      how many blocks below the target the hunk starts
     * @param riseTicks the ticks the rise takes
     * @return the rising hunk, added to the level
     */
    public static BlockHunk raise(ServerLevel level, BlockPos origin, List<HunkBlock> blocks, int rise,
                                  int riseTicks) {
        BlockHunk hunk = new BlockHunk(GooEntities.BLOCK_HUNK.get(), level);
        hunk.blocks = List.copyOf(blocks);
        hunk.target = Vec3.atBottomCenterOf(origin);
        hunk.start = hunk.target.subtract(0, rise, 0);
        hunk.riseTicks = Math.max(1, riseTicks);
        hunk.setPos(hunk.start);
        level.addFreshEntity(hunk);
        return hunk;
    }

    /**
     * Lifts blocks out of their cells as a hunk standing where they stood,
     * which collides with the world as it moves; the caller empties the cells.
     *
     * @param level  the server level
     * @param origin the hunk's origin cell
     * @param blocks the blocks, each by its offset from the origin cell
     * @return the lifted hunk, added to the level
     */
    public static BlockHunk lift(ServerLevel level, BlockPos origin, List<HunkBlock> blocks) {
        BlockHunk hunk = new BlockHunk(GooEntities.BLOCK_HUNK.get(), level);
        hunk.blocks = List.copyOf(blocks);
        hunk.becomeLifted();
        hunk.setPos(Vec3.atBottomCenterOf(origin));
        level.addFreshEntity(hunk);
        return hunk;
    }

    private void becomeLifted() {
        rising = false;
        noPhysics = false;
    }

    /**
     * The share of the rise run after some ticks, eased by smoothstep so the
     * hunk starts and stops gently.
     *
     * @param age       the ticks since the rise began
     * @param riseTicks the ticks the rise takes
     * @return the share, 0 at the start to 1 at the target
     */
    public static double risenShare(double age, int riseTicks) {
        double linear = Math.clamp(age / Math.max(1, riseTicks), 0, 1);
        return linear * linear * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * linear);
    }

    /**
     * The hunk's blocks, each by its offset from the origin cell.
     *
     * @return the blocks
     */
    public List<HunkBlock> blocks() {
        return blocks;
    }

    /**
     * The world corner a block of the hunk draws at, from the hunk's position.
     *
     * @param position the hunk's position, the bottom center of its origin cell
     * @param block    the block
     * @return the block's lowest corner
     */
    public static Vec3 cornerOf(Vec3 position, HunkBlock block) {
        return position.add(block.offset().getX() - HALF, block.offset().getY(), block.offset().getZ() - HALF);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // the spawn data carries the hunk whole; a rise runs from it and a flight follows the server's moves
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (rising) {
            rise();
            if (age >= riseTicks && level() instanceof ServerLevel server) {
                settle(server);
            }
        } else if (level() instanceof ServerLevel server) {
            if (hasData(GooAttachments.GRABBED)) {
                flightTicks = 0;
            } else {
                flightTicks++;
                fly(server);
            }
        }
    }

    /**
     * One tick of the rise: the riders on the hunk's top move up by as far as
     * the top climbs, before the hunk does, so they never stand inside it.
     */
    private void rise() {
        Vec3 next = start.lerp(target, risenShare(age, riseTicks));
        double climb = next.y - position().y;
        if (climb > 0) {
            carryRiders(climb);
        }
        setPos(next);
    }

    /**
     * Moves everything standing on the hunk's top up by a climb, feet kept on
     * the stone. A shulker's push, unlike a piston's, is not capped per tick,
     * so a tall spire's fastest climb carries its riders whole.
     *
     * @param climb how far the top climbs this tick, in blocks
     */
    private void carryRiders(double climb) {
        AABB box = getBoundingBox();
        AABB footing = new AABB(box.minX, box.maxY - RIDER_FOOTING, box.minZ, box.maxX, box.maxY + RIDER_FOOTING,
                box.maxZ);
        for (Entity rider : level().getEntities(this, footing, this::standsOnTop)) {
            rider.move(MoverType.SHULKER, new Vec3(0, climb, 0));
            rider.resetFallDistance();
            if (!(rider instanceof Player)) {
                // A player's own client lifts them; a velocity packet would only fight it.
                rider.hurtMarked = true;
            }
        }
    }

    /**
     * Whether an entity stands on the hunk's top: its feet at the top face,
     * over one of the blocks that make the top.
     *
     * @param entity the entity
     * @return true for a rider
     */
    private boolean standsOnTop(Entity entity) {
        if (entity.isSpectator() || entity instanceof BlockHunk) {
            return false;
        }
        AABB feet = entity.getBoundingBox();
        double top = getBoundingBox().maxY;
        return Math.abs(feet.minY - top) <= RIDER_FOOTING && overATopBlock(feet, top);
    }

    private boolean overATopBlock(AABB feet, double top) {
        for (HunkBlock block : blocks) {
            Vec3 corner = cornerOf(position(), block);
            if (Math.abs(corner.y + 1 - top) <= RIDER_FOOTING && overCell(feet, corner)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overCell(AABB feet, Vec3 corner) {
        boolean acrossX = feet.maxX > corner.x && feet.minX < corner.x + 1;
        return acrossX && feet.maxZ > corner.z && feet.minZ < corner.z + 1;
    }

    /**
     * Whether a hunk blocks movement: a rising one is solid, so nothing falls
     * through Spire's stone mid-rise, while a lifted one collides with nothing.
     *
     * @param rising whether the hunk rises
     * @return true where entities collide with it
     */
    public static boolean blocksMovement(boolean rising) {
        return rising;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity) {
        return blocksMovement(rising);
    }

    /**
     * Settles each block into the cell it reached, where that cell can still
     * be replaced, throwing dust off it, sets anything the stone would fill
     * standing on it, and ends the hunk.
     *
     * @param level the server level
     */
    private void settle(ServerLevel level) {
        List<BlockPos> settled = new ArrayList<>();
        for (HunkBlock block : blocks) {
            BlockPos cell = cellOf(block);
            if (level.getBlockState(cell).canBeReplaced()) {
                level.setBlockAndUpdate(cell, block.state());
                throwDust(level, cell, block.state());
                settled.add(cell);
            }
        }
        settled.forEach(cell -> level.getEntities(this, new AABB(cell), entity -> !entity.isSpectator())
                .forEach(entity -> standOnStone(level, entity)));
        discard();
    }

    /**
     * Sets an entity the settled stone fills standing on the stone above its feet.
     *
     * @param level  the server level
     * @param entity the entity
     */
    private static void standOnStone(ServerLevel level, Entity entity) {
        BlockPos cell = entity.blockPosition();
        while (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty() && cell.getY() < level.getMaxY()) {
            cell = cell.above();
        }
        entity.teleportTo(entity.getX(), cell.getY(), entity.getZ());
    }

    /**
     * One tick of a let-go or thrown hunk's flight: it falls, moves as far as
     * the world lets it, and strikes whatever stopped it or stands in its way.
     *
     * @param level the server level
     */
    private void fly(ServerLevel level) {
        if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0, -GRAVITY, 0));
        }
        Vec3 motion = getDeltaMovement();
        move(MoverType.SELF, motion);
        List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class, getBoundingBox(),
                LivingEntity::isAlive);
        if (horizontalCollision || verticalCollision || !struck.isEmpty() || flightTicks > MOST_FLIGHT_TICKS) {
            impact(level, motion, struck);
            return;
        }
        setDeltaMovement(getDeltaMovement().scale(DRAG));
    }

    /**
     * Strikes on impact: at crushing speed it breaks the blocks a block past
     * the face it struck and hurts every mob it hit, then it breaks apart.
     *
     * @param level  the server level
     * @param motion the motion the hunk struck with
     * @param struck the mobs it hit
     */
    private void impact(ServerLevel level, Vec3 motion, List<LivingEntity> struck) {
        if (motion.length() >= CRUSH_SPEED) {
            AABB crushed = getBoundingBox().expandTowards(motion.normalize().scale(CRUSH_DEPTH)).deflate(TOUCH_SLACK);
            for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(crushed.minX, crushed.minY, crushed.minZ),
                    BlockPos.containing(crushed.maxX, crushed.maxY, crushed.maxZ))) {
                if (crushable(level, cell)) {
                    level.destroyBlock(cell, true, this);
                }
            }
            DamageSource source = level.damageSources().fallingBlock(this);
            float damage = Math.min(MOST_DAMAGE, DAMAGE_PER_BLOCK * blocks.size());
            struck.forEach(mob -> mob.hurtServer(level, source, damage));
        }
        breakApart(level);
    }

    private static boolean crushable(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        float hardness = state.getDestroySpeed(level, cell);
        return !state.isAir() && hardness >= 0 && hardness < CRUSHABLE_BELOW;
    }

    /**
     * Breaks the hunk apart where it stands, each block dropping as the block
     * itself, as silk touch would drop it, and ends the hunk.
     *
     * @param level the server level
     */
    private void breakApart(ServerLevel level) {
        for (HunkBlock block : blocks) {
            BlockPos cell = cellOf(block);
            if (block.state().getBlock().asItem() != Items.AIR) {
                Block.popResource(level, cell, new ItemStack(block.state().getBlock()));
            }
            throwDust(level, cell, block.state());
        }
        discard();
    }

    private BlockPos cellOf(HunkBlock block) {
        return BlockPos.containing(cornerOf(position(), block).add(HALF, HALF, HALF));
    }

    private static void throwDust(ServerLevel level, BlockPos cell, BlockState state) {
        Vec3 center = Vec3.atCenterOf(cell);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z,
                DUST_PER_BLOCK, DUST_SPREAD, DUST_SPREAD, DUST_SPREAD, DUST_SPEED);
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        if (blocks == null || blocks.isEmpty()) {
            return super.makeBoundingBox(position);
        }
        AABB box = null;
        for (HunkBlock block : blocks) {
            Vec3 corner = cornerOf(position, block);
            AABB cell = new AABB(corner, corner.add(1, 1, 1));
            box = box == null ? cell : box.minmax(cell);
        }
        return box;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(blocks.size());
        for (HunkBlock block : blocks) {
            buffer.writeBlockPos(block.offset());
            buffer.writeVarInt(Block.getId(block.state()));
        }
        buffer.writeBoolean(rising);
        writePoint(buffer, start);
        writePoint(buffer, target);
        buffer.writeVarInt(riseTicks);
        buffer.writeVarInt(age);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<HunkBlock> read = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            read.add(new HunkBlock(buffer.readBlockPos(), Block.stateById(buffer.readVarInt())));
        }
        blocks = List.copyOf(read);
        if (!buffer.readBoolean()) {
            becomeLifted();
        }
        start = readPoint(buffer);
        target = readPoint(buffer);
        riseTicks = buffer.readVarInt();
        age = buffer.readVarInt();
        setPos(rising ? start.lerp(target, risenShare(age, riseTicks)) : position());
    }

    private static void writePoint(RegistryFriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x);
        buffer.writeDouble(point.y);
        buffer.writeDouble(point.z);
    }

    private static Vec3 readPoint(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        blocks = input.read(TAG_BLOCKS, HunkBlock.CODEC.listOf()).orElse(List.of());
        if (!input.getBooleanOr(TAG_RISING, true)) {
            becomeLifted();
        }
        start = input.read(TAG_START, Vec3.CODEC).orElse(position());
        target = input.read(TAG_TARGET, Vec3.CODEC).orElse(position());
        riseTicks = Math.max(1, input.getIntOr(TAG_RISE_TICKS, 1));
        age = input.getIntOr(TAG_AGE, 0);
        setPos(position());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_BLOCKS, HunkBlock.CODEC.listOf(), blocks);
        output.putBoolean(TAG_RISING, rising);
        output.store(TAG_START, Vec3.CODEC, start);
        output.store(TAG_TARGET, Vec3.CODEC, target);
        output.putInt(TAG_RISE_TICKS, riseTicks);
        output.putInt(TAG_AGE, age);
    }
}
