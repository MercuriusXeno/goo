package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.TapHost;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Gametests for the convoke blob: landed on a floor, it pulls a mob from its
 * chunk to stand where it sits and goes, or with no mob in the chunk lingers
 * through pulse after pulse (decision convoke-blob-throbs-until-a-mob-arrives).
 * A convoke takes any mob in its chunk, and the tests beside one share its
 * chunk, so each test lays its scene in a chunk of its own far from every
 * other test, held loaded while it runs.
 */
public final class ConvokeTests {

    private static final Identifier ENDER_CONVOKE = Identifier.parse("goo:ender_convoke");
    /** How far off its own bay each test lays its chunk, past every other test, and apart from each other. */
    private static final int PULL_FAR_OFF = 4096;
    private static final int LINGER_FAR_OFF = 8192;
    private static final int TAP_FAR_OFF = 12288;
    private static final Identifier ENDER_CONVOKE_TAP = Identifier.parse("goo:ender_convoke_tap");
    private static final String SHOULD_SPAWN = "A cow should spawn";
    /** How far from the marker the cow stands, inside the marker's chunk. */
    private static final int COW_OFFSET = 3;
    private static final int CHUNK_MIDDLE = 8;
    /** ender_convoke.json's period between tries. */
    private static final int PERIOD = 20;
    private static final int PERIODS_WITHOUT_A_MOB = 3;
    private static final double ARRIVED_WITHIN = 0.1;
    private static final String ABILITY_REQUIRED = "goo:ender_convoke must be loaded";
    private static final String SHOULD_ARRIVE = "The cow should stand at the convoke spot %s, stands at %s";
    private static final String SHOULD_LEAVE = "The marker should go once a mob arrives";
    private static final String SHOULD_LINGER = "The marker should still stand after three pulses with no mob in its chunk";

    private ConvokeTests() {
    }

    /**
     * A cow standing elsewhere in the marker's chunk comes to stand in the
     * marker's cell, and the marker goes.
     *
     * @param helper the gametest helper
     */
    public static void convokePullsAChunkMob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = farFloor(helper, PULL_FAR_OFF);
        BlockPos marker = floor.above();
        Mob cow = spawnCow(helper, marker.east(COW_OFFSET));
        landConvoke(helper, floor);
        Vec3 cell = Vec3.atBottomCenterOf(marker);
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.position().distanceTo(cell) < ARRIVED_WITHIN,
                    String.format(SHOULD_ARRIVE, cell, cow.position()));
            helper.assertFalse(level.getBlockState(marker).is(GooBlocks.ABILITY_BLOCK.get()), SHOULD_LEAVE);
            release(helper, floor, cow);
        });
    }

    /**
     * An ender tap's drip at full chance lands on a floor: ender_convoke_tap.json's
     * program, its roll made certain, pulls a cow from the floor's chunk to
     * stand on the floor under the tap (decision convoke-drip-rolls-a-small-chance).
     *
     * @param helper the gametest helper
     */
    public static void convokeTapAtFullChance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = farFloor(helper, TAP_FAR_OFF);
        Mob cow = spawnCow(helper, floor.above().east(COW_OFFSET));
        AbilityDefinition tap = AbilityRegistry.of(level).getAbility(ENDER_CONVOKE_TAP);
        helper.assertTrue(tap != null, ABILITY_REQUIRED);
        BranchStep roll = (BranchStep) tap.behaviors().getFirst();
        BranchStep certain = new BranchStep(Expr.literal(1), roll.then(), roll.otherwise());
        // The far chunk's entities reach the level's entity scan some ticks after it is forced loaded.
        helper.runAfterDelay(PERIOD, () -> {
            new ProgramBehavior(List.of(certain)).tick(new TapHost(level, floor, Direction.UP));
            Vec3 underTap = Vec3.atBottomCenterOf(floor.above());
            boolean arrived = cow.position().distanceTo(underTap) < ARRIVED_WITHIN;
            Vec3 stands = cow.position();
            release(helper, floor, cow);
            helper.assertTrue(arrived, String.format(SHOULD_ARRIVE, underTap, stands));
            helper.succeed();
        });
    }

    /**
     * Spawns a cow with no will at a spot.
     *
     * @param helper the gametest helper
     * @param at     the cow's block, absolute
     * @return the cow
     */
    private static Mob spawnCow(GameTestHelper helper, BlockPos at) {
        Mob cow = EntityType.COW.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(cow != null, SHOULD_SPAWN);
        cow.setNoAi(true);
        cow.snapTo(Vec3.atBottomCenterOf(at));
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    /**
     * With no mob in its chunk, the marker still stands after three periods.
     *
     * @param helper the gametest helper
     */
    public static void convokeLingersWithoutAMob(GameTestHelper helper) {
        BlockPos floor = farFloor(helper, LINGER_FAR_OFF);
        landConvoke(helper, floor);
        helper.runAfterDelay(PERIODS_WITHOUT_A_MOB * PERIOD + 1, () -> {
            boolean stands = helper.getLevel().getBlockState(floor.above()).is(GooBlocks.ABILITY_BLOCK.get());
            helper.getLevel().removeBlock(floor.above(), false);
            release(helper, floor, null);
            helper.assertTrue(stands, SHOULD_LINGER);
            helper.succeed();
        });
    }

    /**
     * The floor a test lands its blob on: the middle of a chunk far off the
     * test's bay, at the bay's floor height, held loaded for the test.
     *
     * @param helper the gametest helper
     * @param farOff how far off the bay on each horizontal axis the chunk lies
     * @return the floor block's absolute position
     */
    private static BlockPos farFloor(GameTestHelper helper, int farOff) {
        BlockPos bay = helper.absolutePos(BlockPos.ZERO.above());
        ChunkPos chunk = ChunkPos.containing(bay.offset(farOff, 0, farOff));
        helper.getLevel().setChunkForced(chunk.x(), chunk.z(), true);
        BlockPos floor = new BlockPos(chunk.getMinBlockX() + CHUNK_MIDDLE, bay.getY(),
                chunk.getMinBlockZ() + CHUNK_MIDDLE);
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        return floor;
    }

    /**
     * Clears what a test laid in its far chunk and lets the chunk unload.
     *
     * @param helper the gametest helper
     * @param floor  the floor block
     * @param cow    the cow the test spawned, or null
     */
    private static void release(GameTestHelper helper, BlockPos floor, @Nullable Mob cow) {
        if (cow != null) {
            cow.discard();
        }
        helper.getLevel().removeBlock(floor, false);
        ChunkPos chunk = ChunkPos.containing(floor);
        helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
    }

    /**
     * Lands a convoke blob on the top of a floor block.
     *
     * @param helper the gametest helper
     * @param floor  the floor block's absolute position
     */
    private static void landConvoke(GameTestHelper helper, BlockPos floor) {
        AbilityDefinition convoke = AbilityRegistry.of(helper.getLevel()).getAbility(ENDER_CONVOKE);
        helper.assertTrue(convoke != null, ABILITY_REQUIRED);
        AbilityImpact.land(helper.getLevel(), floor, GooTypes.ENDER, Direction.UP, convoke);
    }
}
