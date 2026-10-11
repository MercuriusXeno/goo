package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for Quantum's portable hole: thrown at a stone wall, it phases a
 * three by three hole a player walks through, and on its clock the wall
 * steps back into phase, pushing a cow left in the hole clear
 * (decision portable-hole-phases-blocks-for-a-while).
 */
public final class PortableHoleTests {

    private static final Identifier QUANTUM_HOLE = Identifier.parse("goo:quantum_hole");
    /** The wall block the blob strikes on its north face, the hole's centre. */
    private static final BlockPos STRUCK = new BlockPos(2, 2, 3);
    /** Where the player starts, north of the wall. */
    private static final Vec3 START = new Vec3(2.5, 1, 1.5);
    /** How far the player walks south each tick. */
    private static final double WALK_STEP = 0.25;
    /** The z, relative, the player stops walking at: clear of the wall's south side. */
    private static final double WALK_END_Z = 4.6;
    /** The hole's lifetime in quantum_hole.json. */
    private static final int HOLE_LIFETIME = 200;
    /** Ticks given to the walk. */
    private static final int WALK_TICKS = 20;
    /** Ticks past the lifetime the restore is checked at. */
    private static final int RESTORE_SLACK = 5;

    private PortableHoleTests() {
    }

    /**
     * A hole thrown at a stone wall phases its three by three cells; a
     * survival player walks through to the far side; after the hole's
     * lifetime the wall is stone again and a cow left inside is pushed clear.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void holeWalksThroughAndRestores(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> wall = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                BlockPos cell = helper.absolutePos(new BlockPos(x, y, STRUCK.getZ()));
                level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
                wall.add(cell);
            }
        }
        AbilityDefinition hole = AbilityRegistry.of(level).getAbility(QUANTUM_HOLE);
        helper.assertTrue(hole != null, "goo:quantum_hole must be loaded");

        AbilityImpact.land(level, helper.absolutePos(STRUCK), GooTypes.QUANTUM, Direction.NORTH, hole);

        for (BlockPos cell : wall) {
            helper.assertTrue(level.getBlockState(cell).is(GooBlocks.PHASED_BLOCK.get()),
                    "Every wall cell should be out of phase, " + cell + " is " + level.getBlockState(cell));
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.connection.markClientLoaded();
        player.snapTo(helper.absoluteVec(START));
        double walkEnd = helper.absoluteVec(new Vec3(0, 0, WALK_END_Z)).z;
        helper.onEachTick(() -> {
            if (player.getZ() < walkEnd) {
                Vec3 step = new Vec3(0, 0, WALK_STEP);
                player.move(MoverType.PLAYER, step);
                player.setKnownMovement(step);
            }
        });
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, STRUCK.below());
        helper.runAfterDelay(WALK_TICKS, () -> helper.assertTrue(player.getZ() >= walkEnd,
                "The player should walk through the phased wall, stands at z " + player.getZ()));
        helper.runAfterDelay(HOLE_LIFETIME + RESTORE_SLACK, () -> {
            for (BlockPos cell : wall) {
                helper.assertTrue(level.getBlockState(cell).is(Blocks.STONE),
                        "The wall should step back into phase as stone, " + cell + " is " + level.getBlockState(cell));
            }
            helper.assertTrue(level.noCollision(cow, cow.getBoundingBox()),
                    "The cow left in the hole should be pushed clear of the restored wall, stands at " + cow.position());
            level.getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }
}
