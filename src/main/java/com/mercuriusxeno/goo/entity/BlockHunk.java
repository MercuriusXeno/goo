package com.mercuriusxeno.goo.entity;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import java.util.ArrayList;
import java.util.List;

/**
 * A hunk of blocks lifted out of the world and carried as one entity: each
 * block keeps its offset from the hunk's origin cell, whose bottom center the
 * entity stands at, and draws there as it moves. A rising hunk climbs from
 * its start to its target over its rise time, eased so it starts and stops
 * gently, and settles its blocks into the cells it reached when the rise ends.
 * Both sides run the rise from the spawn data, so the client draws it without
 * waiting on the server's position updates.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 */
public class BlockHunk extends Entity implements IEntityWithComplexSpawn {

    private static final String TAG_BLOCKS = "Blocks";
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
    private Vec3 start = Vec3.ZERO;
    private Vec3 target = Vec3.ZERO;
    private int riseTicks = 1;
    private int age;

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
        // the spawn data carries the hunk whole, and both sides run its rise from it
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        setPos(start.lerp(target, risenShare(age, riseTicks)));
        if (age >= riseTicks && level() instanceof ServerLevel server) {
            settle(server);
        }
    }

    /**
     * Settles each block into the cell it reached, where that cell can still
     * be replaced, throwing dust off it, and ends the hunk.
     *
     * @param level the server level
     */
    private void settle(ServerLevel level) {
        for (HunkBlock block : blocks) {
            BlockPos cell = BlockPos.containing(cornerOf(position(), block).add(HALF, HALF, HALF));
            if (level.getBlockState(cell).canBeReplaced()) {
                level.setBlockAndUpdate(cell, block.state());
                Vec3 center = Vec3.atCenterOf(cell);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block.state()), center.x, center.y,
                        center.z, DUST_PER_BLOCK, DUST_SPREAD, DUST_SPREAD, DUST_SPREAD, DUST_SPEED);
            }
        }
        discard();
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
        start = readPoint(buffer);
        target = readPoint(buffer);
        riseTicks = buffer.readVarInt();
        age = buffer.readVarInt();
        setPos(start.lerp(target, risenShare(age, riseTicks)));
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
        start = input.read(TAG_START, Vec3.CODEC).orElse(position());
        target = input.read(TAG_TARGET, Vec3.CODEC).orElse(position());
        riseTicks = Math.max(1, input.getIntOr(TAG_RISE_TICKS, 1));
        age = input.getIntOr(TAG_AGE, 0);
        setPos(position());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_BLOCKS, HunkBlock.CODEC.listOf(), blocks);
        output.store(TAG_START, Vec3.CODEC, start);
        output.store(TAG_TARGET, Vec3.CODEC, target);
        output.putInt(TAG_RISE_TICKS, riseTicks);
        output.putInt(TAG_AGE, age);
    }
}
