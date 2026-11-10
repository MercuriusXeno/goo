package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SculkCall;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for the Shriek blob's one cry: landed on a stone floor with a
 * cow near it and a zombie far off on the other side, both in earshot, the
 * zombie walks toward the blob and the cow stays where it grazed, and a
 * zombie arriving just after the cry is never called (decisions
 * shriek-blob-cries-once-and-calls-earshot,
 * shriek-is-a-sculk-call-that-draws-every-mob-it-reaches and
 * shriek-pulls-hostiles-only). The cry reaches any mob near it, so the
 * scene stands far from every other test, its chunks held loaded while it runs.
 */
@RegistersGameTests
public final class ShriekTests {
    private static final String SHRIEK_CALLS = "shriek_calls_the_zombie_and_leaves_the_cow";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SHRIEK_CALLS, ShriekTests::shriekCallsTheZombieAndLeavesTheCow);
    }

    private static final Identifier DEEP_SHRIEK = Identifier.parse("goo:deep_shriek");
    /** How far off its own bay the scene lies on each horizontal axis, past every other test. */
    private static final int FAR_OFF = 4096;
    /** The floor's reach from the blob along x, past the far zombie, and its half width along z. */
    private static final int FLOOR_LENGTH = 14;
    private static final int FLOOR_HALF_WIDTH = 3;
    /** The cow's and the zombies' distances from the blob, all inside deep_shriek.json's reach of 16. */
    private static final int NEAR = 4;
    private static final int FAR = 10;
    /** Ticks after the landing the late zombie spawns, once the shriek has sounded. */
    private static final int AFTER_THE_SHRIEK = 2;
    /** Ticks a called mob is given to walk after the shriek. */
    private static final int WALK_TICKS = 80;
    /** How much nearer the blob a called mob stands after its walk. */
    private static final double WALKED_NEARER = 2.5;
    private static final String SHOULD_SPAWN = "A %s should spawn";
    private static final String SHOULD_TICK = "The far chunks should tick their entities";
    private static final String ABILITY_REQUIRED = "%s must be loaded";
    private static final String SHOULD_COME = "The zombie %d blocks out should walk nearer the blob, stood %.2f off, stands %.2f off";
    private static final String SHOULD_HOLD = "The %s should hold where it stood, %.2f off the blob, it stands %.2f off";
    private static final String SHOULD_LEAVE_THE_COW = "The shriek should leave the cow uncalled";
    private static final String SHOULD_CALL_THE_ZOMBIE = "The shriek should call the zombie in earshot to the blob";
    private static final String SHOULD_LEAVE_THE_LATE_ZOMBIE = "The zombie arriving after the shriek should not be called";
    private static final String COW = "cow";
    private static final String LATE_ZOMBIE = "late zombie";

    private ShriekTests() {
    }

    /**
     * A zombie ten blocks west of the blob and a cow four blocks east stand
     * in earshot as the blob lands and shrieks: the zombie is called and
     * stands at least two and a half blocks nearer the blob eighty ticks
     * later, the cow is never called and holds where it stood, and a second
     * zombie spawned ten blocks east just after the shriek is never called
     * and holds where it spawned. The mobs spawn once the far chunks tick
     * their entities, since a mob added sooner stands frozen.
     *
     * @param helper the gametest helper
     */
    public static void shriekCallsTheZombieAndLeavesTheCow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = buildFloor(helper);
        Vec3 blob = Vec3.atBottomCenterOf(floor.above());
        BlockPos cowAt = floor.above().east(NEAR);
        BlockPos zombieAt = floor.above().west(FAR);
        BlockPos lateAt = floor.above().east(FAR);
        List<Mob> mobs = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(cowAt)
                        && level.isPositionEntityTicking(zombieAt) && level.isPositionEntityTicking(lateAt), SHOULD_TICK))
                .thenExecute(() -> {
                    mobs.add(stilled(spawn(helper, EntityType.COW, cowAt)));
                    mobs.add(spawn(helper, EntityType.ZOMBIE, zombieAt));
                    landShriek(helper, floor);
                })
                .thenIdle(AFTER_THE_SHRIEK)
                .thenExecute(() -> mobs.add(stilled(spawn(helper, EntityType.ZOMBIE, lateAt))))
                .thenIdle(WALK_TICKS)
                .thenExecute(() -> {
                    boolean cowCalled = SculkCall.isCalledTo(mobs.get(0), floor.above());
                    boolean zombieCalled = SculkCall.isCalledTo(mobs.get(1), floor.above());
                    boolean lateCalled = SculkCall.isCalledTo(mobs.get(2), floor.above());
                    double cowOff = across(mobs.get(0).position(), blob);
                    double zombieOff = across(mobs.get(1).position(), blob);
                    double lateOff = across(mobs.get(2).position(), blob);
                    release(helper, floor, mobs);
                    helper.assertFalse(cowCalled, SHOULD_LEAVE_THE_COW);
                    helper.assertTrue(zombieCalled, SHOULD_CALL_THE_ZOMBIE);
                    helper.assertFalse(lateCalled, SHOULD_LEAVE_THE_LATE_ZOMBIE);
                    helper.assertTrue(zombieOff <= FAR - WALKED_NEARER,
                            String.format(SHOULD_COME, FAR, (double) FAR, zombieOff));
                    helper.assertTrue(cowOff > NEAR - WALKED_NEARER, String.format(SHOULD_HOLD, COW, (double) NEAR, cowOff));
                    helper.assertTrue(lateOff > FAR - WALKED_NEARER,
                            String.format(SHOULD_HOLD, LATE_ZOMBIE, (double) FAR, lateOff));
                })
                .thenSucceed();
    }

    /**
     * @param at   a point
     * @param blob the blob
     * @return how far the point stands from the blob across the ground, height left out
     */
    private static double across(Vec3 at, Vec3 blob) {
        return Math.hypot(at.x - blob.x, at.z - blob.z);
    }

    /**
     * Takes a mob's idle strolls away, so only a call could move it: a mob
     * the shriek passes over then holds where it stood, and its holding
     * reads the shriek rather than a random wander.
     *
     * @param mob the mob
     * @return the same mob
     */
    private static Mob stilled(Mob mob) {
        mob.goalSelector.removeAllGoals(RandomStrollGoal.class::isInstance);
        return mob;
    }

    private static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = type.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(mob != null, String.format(SHOULD_SPAWN, type));
        mob.snapTo(Vec3.atBottomCenterOf(at));
        mob.setPersistenceRequired();
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    /**
     * Lays a stone floor in a far place held loaded: long along x past both
     * mobs, with a barrier rim a mob cannot wander off.
     *
     * @param helper the gametest helper
     * @return the floor block the blob lands on, the floor's middle
     */
    private static BlockPos buildFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(BlockPos.ZERO.above()).offset(FAR_OFF, 0, FAR_OFF);
        forceChunks(level, floor, true);
        for (BlockPos cell : floorCells(floor, 1)) {
            boolean rim = Math.abs(cell.getX() - floor.getX()) == FLOOR_LENGTH + 1
                    || Math.abs(cell.getZ() - floor.getZ()) == FLOOR_HALF_WIDTH + 1;
            level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(cell.above(), rim ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(cell.above(2), rim ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        return floor;
    }

    private static Iterable<BlockPos> floorCells(BlockPos floor, int rim) {
        return BlockPos.betweenClosed(floor.offset(-FLOOR_LENGTH - rim, 0, -FLOOR_HALF_WIDTH - rim),
                floor.offset(FLOOR_LENGTH + rim, 0, FLOOR_HALF_WIDTH + rim));
    }

    private static void forceChunks(ServerLevel level, BlockPos floor, boolean forced) {
        ChunkPos low = ChunkPos.containing(floor.offset(-FLOOR_LENGTH - 1, 0, -FLOOR_HALF_WIDTH - 1));
        ChunkPos high = ChunkPos.containing(floor.offset(FLOOR_LENGTH + 1, 0, FLOOR_HALF_WIDTH + 1));
        for (int x = low.x(); x <= high.x(); x++) {
            for (int z = low.z(); z <= high.z(); z++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

    /**
     * Clears the scene: the mobs, the blob, the floor and its rim, and lets the chunks unload.
     *
     * @param helper the gametest helper
     * @param floor  the floor block the blob landed on
     * @param mobs   the mobs the test spawned
     */
    private static void release(GameTestHelper helper, BlockPos floor, List<Mob> mobs) {
        ServerLevel level = helper.getLevel();
        mobs.forEach(Mob::discard);
        for (BlockPos cell : floorCells(floor, 1)) {
            for (int up = 0; up <= 2; up++) {
                level.setBlock(cell.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        forceChunks(level, floor, false);
    }

    /**
     * Lands a deep goo shriek blob on the top of a floor block.
         *
     * @param helper the gametest helper
     * @param floor  the floor block's absolute position
     */
    private static void landShriek(GameTestHelper helper, BlockPos floor) {
        AbilityDefinition shriek = AbilityRegistry.of(helper.getLevel()).getAbility(DEEP_SHRIEK);
        helper.assertTrue(shriek != null, String.format(ABILITY_REQUIRED, DEEP_SHRIEK));
        AbilityImpact.land(helper.getLevel(), floor, GooTypes.DEEP, Direction.UP, shriek);
    }
}
