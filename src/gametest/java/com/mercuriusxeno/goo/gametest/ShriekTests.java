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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the Shriek blob's one cry, landed on a stone floor through
 * the real landing path: the landing cell holds air after the cry, a zombie
 * in earshot is called and walks toward the landing while a cow beside it
 * stays uncalled, and a zombie arriving one tick after the cry is never
 * called (decisions shriek-blob-cries-once-and-calls-earshot,
 * shriek-is-a-sculk-call-that-draws-every-mob-it-reaches,
 * convoke-blob-throbs-until-a-mob-arrives and shriek-pulls-hostiles-only).
 * The cry reaches any mob near it, so each scene stands far from every
 * other test and from the other scene, its chunks held loaded while it runs.
 */
@RegistersGameTests
public final class ShriekTests {
    private static final String SHRIEK_CALLS = "shriek_calls_the_zombie_and_passes_the_cow";
    private static final String SHRIEK_CALLS_NONE_LATER = "shriek_calls_no_zombie_arriving_after_the_cry";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SHRIEK_CALLS, ShriekTests::shriekCallsTheZombieAndPassesTheCow);
        GooTestFunctions.registerTest(registrar, SHRIEK_CALLS_NONE_LATER,
                ShriekTests::shriekCallsNoZombieArrivingAfterTheCry);
    }

    private static final Identifier XENO_SHRIEK = Identifier.parse("goo:xeno_shriek");
    /** How far off its own bay the first scene lies on each horizontal axis, past every other test. */
    private static final int FAR_OFF = 4096;
    /** How far along z the second scene lies from the first, past the shriek's reach. */
    private static final int SCENE_APART = 64;
    /** The floor's reach from the landing along x, past the mobs, and its half width along z. */
    private static final int FLOOR_LENGTH = 12;
    private static final int FLOOR_HALF_WIDTH = 3;
    /** The mobs' distance from the landing, inside the shriek's reach. */
    private static final int FAR = 8;
    /** Ticks a called mob is given to walk after the shriek. */
    private static final int WALK_TICKS = 80;
    /** How much nearer the landing a called mob stands after its walk. */
    private static final double WALKED_NEARER = 2.5;
    /** Ticks a late zombie stands in earshot before it is read. */
    private static final int LATE_ZOMBIE_TICKS = 5;
    private static final String SHOULD_SPAWN = "A %s should spawn";
    private static final String SHOULD_TICK = "The far chunks should tick their entities";
    private static final String ABILITY_REQUIRED = "%s must be loaded";
    private static final String SHOULD_LEAVE_AIR = "The Shriek landing should place no block, holds %s";
    private static final String SHOULD_COME = "The zombie %d blocks out should walk nearer the landing, stood %.2f off, stands %.2f off";
    private static final String SHOULD_CALL_THE_ZOMBIE = "The shriek should call the zombie in earshot to the landing";
    private static final String SHOULD_PASS_THE_COW = "The shriek should pass the cow beside the zombie over";
    private static final String SHOULD_NOT_CALL_LATER = "A zombie arriving after the shriek should not be called";

    private ShriekTests() {
    }

    /**
     * A zombie eight blocks west of the landing and a cow beside it stand in
     * earshot as the blob lands and shrieks: the landing cell holds air, the
     * zombie is called and the cow is not, and eighty ticks later the zombie
     * stands at least two and a half blocks nearer the landing. The mobs
     * spawn once the far chunk ticks its entities, since a mob added sooner
     * stands frozen.
     *
     * @param helper the gametest helper
     */
    public static void shriekCallsTheZombieAndPassesTheCow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = buildFloor(helper, 0);
        BlockPos landing = floor.above();
        Vec3 blob = Vec3.atBottomCenterOf(landing);
        BlockPos zombieAt = landing.west(FAR);
        List<Mob> mobs = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(zombieAt), SHOULD_TICK))
                .thenExecute(() -> {
                    mobs.add(spawn(helper, EntityTypes.ZOMBIE, zombieAt));
                    mobs.add(spawn(helper, EntityTypes.COW, zombieAt.north()));
                    landShriek(helper, floor);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    boolean landingAir = level.getBlockState(landing).isAir();
                    String landingHolds = level.getBlockState(landing).toString();
                    boolean zombieCalled = SculkCall.isCalledTo(mobs.get(0), landing);
                    boolean cowCalled = SculkCall.isCalledTo(mobs.get(1), landing);
                    if (!landingAir || !zombieCalled || cowCalled) {
                        release(helper, floor, mobs);
                    }
                    helper.assertTrue(landingAir, String.format(SHOULD_LEAVE_AIR, landingHolds));
                    helper.assertTrue(zombieCalled, SHOULD_CALL_THE_ZOMBIE);
                    helper.assertFalse(cowCalled, SHOULD_PASS_THE_COW);
                })
                .thenIdle(WALK_TICKS)
                .thenExecute(() -> {
                    double zombieOff = across(mobs.get(0).position(), blob);
                    release(helper, floor, mobs);
                    helper.assertTrue(zombieOff <= FAR - WALKED_NEARER,
                            String.format(SHOULD_COME, FAR, (double) FAR, zombieOff));
                })
                .thenSucceed();
    }

    /**
     * A zombie spawned eight blocks west of the landing one tick after the
     * blob shrieks holds no walk to the landing a few ticks on, since the
     * shriek calls only the mobs in earshot as it cries.
     *
     * @param helper the gametest helper
     */
    public static void shriekCallsNoZombieArrivingAfterTheCry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = buildFloor(helper, SCENE_APART);
        BlockPos landing = floor.above();
        BlockPos zombieAt = landing.west(FAR);
        List<Mob> mobs = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.isPositionEntityTicking(zombieAt), SHOULD_TICK))
                .thenExecute(() -> landShriek(helper, floor))
                .thenIdle(1)
                .thenExecute(() -> mobs.add(spawn(helper, EntityTypes.ZOMBIE, zombieAt)))
                .thenIdle(LATE_ZOMBIE_TICKS)
                .thenExecute(() -> {
                    boolean lateZombieCalled = SculkCall.isCalledTo(mobs.get(0), landing);
                    release(helper, floor, mobs);
                    helper.assertFalse(lateZombieCalled, SHOULD_NOT_CALL_LATER);
                })
                .thenSucceed();
    }

    /**
     * @param at   a point
     * @param blob the landing
     * @return how far the point stands from the landing across the ground, height left out
     */
    private static double across(Vec3 at, Vec3 blob) {
        return Math.hypot(at.x - blob.x, at.z - blob.z);
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
     * Lays a stone floor in a far place held loaded: long along x past the
     * mobs, with a barrier rim a mob cannot wander off.
     *
     * @param helper the gametest helper
     * @param apart  how far along z this scene lies from the first
     * @return the floor block the blob lands on, the floor's middle
     */
    private static BlockPos buildFloor(GameTestHelper helper, int apart) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(BlockPos.ZERO.above()).offset(FAR_OFF, 0, FAR_OFF + apart);
        forceChunks(level, floor, true);
        for (BlockPos cell : floorCells(floor)) {
            boolean rim = Math.abs(cell.getX() - floor.getX()) == FLOOR_LENGTH + 1
                    || Math.abs(cell.getZ() - floor.getZ()) == FLOOR_HALF_WIDTH + 1;
            level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(cell.above(), rim ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(cell.above(2), rim ? Blocks.BARRIER.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        return floor;
    }

    private static Iterable<BlockPos> floorCells(BlockPos floor) {
        return BlockPos.betweenClosed(floor.offset(-FLOOR_LENGTH - 1, 0, -FLOOR_HALF_WIDTH - 1),
                floor.offset(FLOOR_LENGTH + 1, 0, FLOOR_HALF_WIDTH + 1));
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
     * Clears the scene: the mobs, the floor and its rim, and lets the chunks unload.
     *
     * @param helper the gametest helper
     * @param floor  the floor block the blob landed on
     * @param mobs   the mobs the test spawned
     */
    private static void release(GameTestHelper helper, BlockPos floor, List<Mob> mobs) {
        ServerLevel level = helper.getLevel();
        mobs.forEach(Mob::discard);
        for (BlockPos cell : floorCells(floor)) {
            for (int up = 0; up <= 2; up++) {
                level.setBlock(cell.above(up), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        forceChunks(level, floor, false);
    }

    /**
     * Lands a xeno goo shriek blob on the top of a floor block.
     *
     * @param helper the gametest helper
     * @param floor  the floor block's absolute position
     */
    private static void landShriek(GameTestHelper helper, BlockPos floor) {
        AbilityDefinition shriek = AbilityRegistry.of(helper.getLevel()).getAbility(XENO_SHRIEK);
        helper.assertTrue(shriek != null, String.format(ABILITY_REQUIRED, XENO_SHRIEK));
        AbilityImpact.land(helper.getLevel(), floor, GooTypes.XENO, Direction.UP, shriek);
    }
}
