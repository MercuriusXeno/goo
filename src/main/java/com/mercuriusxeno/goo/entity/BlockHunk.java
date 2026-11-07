package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ISidedProxy;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
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
 * it reached when the rise ends. Its height is a function of the level's
 * game time from the tick it began, so both sides, and every rider, read
 * the same stone at the same tick; the client ignores the server's position
 * updates while it rises. Settled, it keeps drawing over its stone for
 * {@link #SETTLE_LINGER_TICKS} while the clients' chunks redraw the stone,
 * so nothing blinks out.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 *
 * A rising hunk is solid and carries whatever stands on its top up with it,
 * inside each rider's own tick (HunkRiders), so the rider's drawn position
 * glides with the stone, and leaves them standing on the settled stone.
 * decision spire-carries-its-riders-up-with-the-rising-stone
 *
 * A lifted hunk rises straight up out of the hole it tore until it clears
 * it, then travels to where a grab's hold puts it, colliding with the world
 * the whole way; let go or thrown, it flies under gravity, and whatever it
 * strikes, block or mob, it crushes on impact before it comes apart block by
 * block (HunkShatter), each crumbling to break particles and dropping as the
 * block itself.
 * The client interpolates its moves, and the player holding it draws it where
 * their own look puts it, so it glides with the look.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 */
public class BlockHunk extends Entity implements IEntityWithComplexSpawn {

    private static final String TAG_BLOCKS = "Blocks";
    private static final String TAG_RISING = "Rising";
    private static final String TAG_START = "Start";
    private static final String TAG_TARGET = "Target";
    private static final String TAG_RISE_TICKS = "RiseTicks";
    private static final String TAG_AGE = "Age";
    private static final String TAG_HOME = "Home";
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
    /** Ticks one block of a shattering hunk takes to crumble away. */
    public static final int CRUMBLE_TICKS = 5;
    /** Ticks between one block of a shattering hunk beginning to crumble and the next. */
    public static final int BLOCK_STAGGER_TICKS = 1;
    /** Ticks a settled hunk keeps drawing over its stone while the clients' chunks redraw it. */
    public static final int SETTLE_LINGER_TICKS = 5;
    /** How far a rider's feet may stand from the hunk's top and still ride it, in blocks. */
    static final double RIDER_FOOTING = 0.1;
    /** Shrinks the crush box off the faces the hunk only touches. */
    private static final double TOUCH_SLACK = 1e-3;
    /** The id a hunk no player holds names as its holder. */
    public static final int NO_HOLDER = -1;
    /** The game time a shattering hunk struck, or {@link #WHOLE} while it is whole. */
    private static final EntityDataAccessor<Long> SHATTER_START =
            SynchedEntityData.defineId(BlockHunk.class, EntityDataSerializers.LONG);
    /** The face a shattering hunk struck with, which orders its crumble. */
    private static final EntityDataAccessor<Direction> SHATTER_FACE =
            SynchedEntityData.defineId(BlockHunk.class, EntityDataSerializers.DIRECTION);
    /** The shatter time a whole hunk names. */
    public static final long WHOLE = -1;
    /** How fast a torn hunk rises out of its hole, in blocks a tick. */
    static final double RISE_OUT_SPEED = 0.3;
    /** How fast a risen hunk travels to the hold, in blocks a tick. */
    static final double PULL_SPEED = 1.2;
    /** Below this a rise has stalled against something above the hole. */
    private static final double STALLED = 1e-4;
    /** The player holding a lifted hunk, so their own client draws it on their look. */
    private static final EntityDataAccessor<Integer> HOLDER =
            SynchedEntityData.defineId(BlockHunk.class, EntityDataSerializers.INT);

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
    /** The game time the rise began on. */
    private long riseStartTime;
    /** The origin cell the hunk's blocks are at home in: where they were lifted from, or where they settle. */
    private BlockPos home = BlockPos.ZERO;
    private boolean settled;
    private final InterpolationHandler interpolation = new InterpolationHandler(this);
    /** Ticks the hunk has flown free since a hold last stood on it. */
    private int flightTicks;
    /** Whether a torn hunk has risen clear of its hole. */
    private boolean clearOfHole;
    /** The height a rising-out hunk stood at last tick, to tell a stalled rise. */
    private double lastRiseY = Double.NaN;
    /** By block index, whether a shattering hunk has dropped that block. */
    private boolean[] dropped = new boolean[0];

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
        hunk.riseStartTime = level.getGameTime();
        hunk.home = origin.immutable();
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
        hunk.home = origin.immutable();
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
     * The cell a block of the hunk picks its model's variant by, wherever the
     * hunk carries it: its home cell, the one it was lifted from or settles
     * into, so its texture holds still as it moves and matches the block the
     * world draws there.
     *
     * @param home  the hunk's home origin cell
     * @param block the block
     * @return the cell its variant seeds from
     */
    public static BlockPos variantCellOf(BlockPos home, HunkBlock block) {
        return home.offset(block.offset());
    }

    /**
     * The hunk's home origin cell.
     *
     * @return the cell its blocks were lifted from, or settle into
     */
    public BlockPos home() {
        return home;
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
        entityData.define(HOLDER, NO_HOLDER);
        entityData.define(SHATTER_START, WHOLE);
        entityData.define(SHATTER_FACE, Direction.DOWN);
    }

    /**
     * Marks the player holding the hunk this tick, so their client draws it on their look.
     *
     * @param holder the holding player
     */
    public void holdBy(Entity holder) {
        entityData.set(HOLDER, holder.getId());
    }

    /**
     * Where a held hunk moves this tick toward its hold: straight up out of
     * the hole it tore until its bottom clears the hole's rim or something
     * above stops it, then along the line to the hold at the pull's speed.
     *
     * @param hold where the hold puts the hunk
     * @return the point to move it to this tick
     */
    public Vec3 holdStep(Vec3 hold) {
        if (!clearOfHole) {
            double rimHeight = home.getY() + heightInBlocks();
            boolean stalled = !Double.isNaN(lastRiseY) && getY() - lastRiseY < STALLED;
            if (getY() < rimHeight && !stalled) {
                lastRiseY = getY();
                return position().add(0, Math.min(RISE_OUT_SPEED, rimHeight - getY()), 0);
            }
            clearOfHole = true;
        }
        Vec3 toward = hold.subtract(position());
        return toward.length() <= PULL_SPEED ? hold : position().add(toward.normalize().scale(PULL_SPEED));
    }

    private int heightInBlocks() {
        int low = blocks.stream().mapToInt(block -> block.offset().getY()).min().orElse(0);
        int high = blocks.stream().mapToInt(block -> block.offset().getY()).max().orElse(0);
        return high - low + 1;
    }

    /**
     * Whether the hunk has struck and is coming apart.
     *
     * @return true from the impact until its last block has crumbled
     */
    public boolean shattering() {
        return entityData.get(SHATTER_START) != WHOLE;
    }

    /**
     * @return the game time the hunk struck, or {@link #WHOLE}
     */
    public long shatterStart() {
        return entityData.get(SHATTER_START);
    }

    /**
     * @return the face the hunk struck with
     */
    public Direction shatterFace() {
        return entityData.get(SHATTER_FACE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SHATTER_START.equals(key) && shattering() && level().isClientSide()) {
            ISidedProxy.get().hunkShattered(this);
        }
    }

    /**
     * The id of the player holding the hunk.
     *
     * @return the holder's entity id, or {@link #NO_HOLDER}
     */
    public int holderId() {
        return entityData.get(HOLDER);
    }

    /**
     * Where a rising hunk stands at a moment of the level's clock.
     *
     * @param time the game time, a fraction of a tick past it allowed
     * @return the hunk's position
     */
    public Vec3 positionAt(double time) {
        return start.lerp(target, risenShare(time - riseStartTime, riseTicks));
    }

    /**
     * How high a rising hunk's top stands at a moment of the level's clock.
     *
     * @param time the game time
     * @return the top face's height
     */
    double topAt(double time) {
        return makeBoundingBox(positionAt(time)).maxY;
    }

    /**
     * Whether a rising hunk has reached its cells and settled its stone by a moment.
     *
     * @param time the game time
     * @return true from the tick the rise ends
     */
    public boolean settledBy(double time) {
        return time - riseStartTime >= riseTicks;
    }

    /**
     * Whether a settled hunk still lingers over its stone at a moment, drawing
     * it while the clients' chunks redraw it.
     *
     * @param time the game time
     * @return true from the settle until the hunk ends
     */
    public boolean lingers(double time) {
        return rising && settledBy(time);
    }

    /**
     * Whether the hunk still rises, unsettled, at a moment: the span it carries riders and blocks movement.
     *
     * @param time the game time
     * @return true while the stone climbs
     */
    boolean climbsAt(double time) {
        return rising && !settledBy(time);
    }

    @Override
    public void tick() {
        super.tick();
        if (rising) {
            riseTick(level().getGameTime());
        } else if (level() instanceof ServerLevel server) {
            liftedTick(server);
        } else if (interpolation.hasActiveInterpolation()) {
            interpolation.interpolate();
        }
    }

    private void riseTick(long now) {
        // The rise reads the clock, so any server position update the client took is dropped.
        interpolation.cancel();
        setPos(positionAt(now));
        if (climbsAt(now)) {
            HunkRiders.rises(this);
        }
        if (level() instanceof ServerLevel server) {
            settleWhenDue(server, now);
        }
    }

    private void liftedTick(ServerLevel server) {
        if (shattering()) {
            crumbleTick(server);
            return;
        }
        if (hasData(GooAttachments.GRABBED)) {
            flightTicks = 0;
            return;
        }
        entityData.set(HOLDER, NO_HOLDER);
        flightTicks++;
        fly(server);
    }

    private void settleWhenDue(ServerLevel server, long now) {
        if (!settled && settledBy(now)) {
            settle(server);
        }
        if (now - riseStartTime >= riseTicks + SETTLE_LINGER_TICKS) {
            discard();
        }
    }

    @Override
    public InterpolationHandler getInterpolation() {
        return interpolation;
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        HunkRiders.forget(this);
    }

    /**
     * Lifts a rider standing on the climbing top onto where the top climbs
     * this tick, inside the rider's own tick so its drawn position glides; the
     * stone's collision box stands there too, whichever of them ticks first.
     *
     * @param rider the entity
     * @return true where the entity rode the stone
     */
    boolean liftRider(Entity rider) {
        long now = level().getGameTime();
        if (!climbsAt(now)) {
            return false;
        }
        Vec3 position = positionAt(now);
        double top = topAt(now);
        AABB feet = rider.getBoundingBox();
        // From the ground the rise began on up to the top: a client that first sees the stone a tick or two late
        // finds it already past the rider's feet, and must still lift them rather than rise through them.
        boolean onTheStone = feet.minY >= topAt(riseStartTime) - RIDER_FOOTING && feet.minY <= top + RIDER_FOOTING;
        if (!onTheStone || !overATopBlock(feet, position, top)) {
            return false;
        }
        setBoundingBox(makeBoundingBox(position));
        if (top > feet.minY) {
            rider.setPos(rider.getX(), rider.getY() + top - feet.minY, rider.getZ());
        }
        Vec3 motion = rider.getDeltaMovement();
        rider.setDeltaMovement(motion.x, Math.max(0, motion.y), motion.z);
        rider.resetFallDistance();
        rider.setOnGround(true);
        return true;
    }

    private boolean overATopBlock(AABB feet, Vec3 position, double top) {
        for (HunkBlock block : blocks) {
            Vec3 corner = cornerOf(position, block);
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
        // A player's own client keeps them on the stone, a tick or so behind the server's copy, so the server's
        // stone would reject the moves that client reports and pull the player back through it.
        boolean playerOnTheServer = entity instanceof Player && !level().isClientSide();
        return !playerOnTheServer && blocksMovement(climbsAt(level().getGameTime()));
    }

    /**
     * Settles each block into the cell it reached, where that cell can still
     * be replaced, throwing dust off it, and sets anything the stone would fill
     * standing on it; the hunk lingers over the stone until its chunks redraw.
     *
     * @param level the server level
     */
    private void settle(ServerLevel level) {
        List<BlockPos> placed = new ArrayList<>();
        for (HunkBlock block : blocks) {
            BlockPos cell = cellOf(block);
            if (level.getBlockState(cell).canBeReplaced()) {
                level.setBlockAndUpdate(cell, block.state());
                throwDust(level, cell, block.state());
                placed.add(cell);
            }
        }
        placed.forEach(cell -> level.getEntities(this, new AABB(cell), entity -> !entity.isSpectator())
                .forEach(entity -> standOnStone(level, entity)));
        this.settled = true;
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
        setDeltaMovement(Vec3.ZERO);
        dropped = new boolean[blocks.size()];
        entityData.set(SHATTER_FACE, HunkShatter.leadingFace(motion));
        entityData.set(SHATTER_START, level.getGameTime());
    }

    private static boolean crushable(ServerLevel level, BlockPos cell) {
        BlockState state = level.getBlockState(cell);
        float hardness = state.getDestroySpeed(level, cell);
        return !state.isAir() && hardness >= 0 && hardness < CRUSHABLE_BELOW;
    }

    /**
     * One tick of a shattering hunk: each block whose crumble has ended drops
     * as the block itself, as silk touch would drop it, and the hunk ends once
     * its last block has.
     *
     * @param level the server level
     */
    private void crumbleTick(ServerLevel level) {
        long now = level.getGameTime();
        int[] turns = HunkShatter.turnsOf(blocks, shatterFace());
        boolean whole = false;
        for (int index = 0; index < blocks.size(); index++) {
            if (dropped[index]) {
                continue;
            }
            if (now < HunkShatter.crumbleEnd(shatterStart(), turns[index])) {
                whole = true;
                continue;
            }
            dropped[index] = true;
            HunkBlock block = blocks.get(index);
            if (block.state().getBlock().asItem() != Items.AIR) {
                Block.popResource(level, cellOf(block), new ItemStack(block.state().getBlock()));
            }
        }
        if (!whole) {
            discard();
        }
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
        buffer.writeLong(riseStartTime);
        buffer.writeBlockPos(home);
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
        riseStartTime = buffer.readLong();
        home = buffer.readBlockPos();
        if (rising) {
            setPos(positionAt(level().getGameTime()));
        }
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
        riseStartTime = level().getGameTime() - input.getIntOr(TAG_AGE, 0);
        home = input.read(TAG_HOME, BlockPos.CODEC).orElse(blockPosition());
        setPos(position());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_BLOCKS, HunkBlock.CODEC.listOf(), blocks);
        output.putBoolean(TAG_RISING, rising);
        output.store(TAG_START, Vec3.CODEC, start);
        output.store(TAG_TARGET, Vec3.CODEC, target);
        output.putInt(TAG_RISE_TICKS, riseTicks);
        output.putInt(TAG_AGE, (int) (level().getGameTime() - riseStartTime));
        output.store(TAG_HOME, BlockPos.CODEC, home);
    }
}
