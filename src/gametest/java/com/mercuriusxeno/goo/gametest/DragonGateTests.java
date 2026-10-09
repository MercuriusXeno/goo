package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.gate.DragonGateOpening;
import com.mercuriusxeno.goo.ability.gate.DragonGates;
import com.mercuriusxeno.goo.ability.gate.GateFootprint;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import java.util.List;
import java.util.Optional;

/**
 * Gametests for Dragon Gate: thrown at a stone wall, it lays a three by
 * three gate over the wall and a mirror on the End's platform, and on the
 * pair's clock both change back to the blocks they covered
 * (decision dragon-gate-banishes-blocks-and-opens-a-portal).
 */
public final class DragonGateTests {

    private static final Identifier ENDER_DRAGON_GATE = Identifier.parse("goo:ender_dragon_gate");
    /** The wall block the blob strikes on its south face, the gate's centre. */
    private static final BlockPos WALL_CENTER = new BlockPos(4, 2, 4);
    private static final String ABILITY_REQUIRED = "goo:ender_dragon_gate must be loaded";
    private static final String SHOULD_OPEN = "The gate should stand open as a pair over the wall";
    private static final String SHOULD_COVER = "Every cell of the wall's patch should be a gate cell, %s is not";
    private static final String SHOULD_MIRROR = "The End's platform should hold the mirror gate";
    private static final String SHOULD_CARRY_THERE = "The wall gate should carry a traveller to %s in the End, carries %s";
    private static final String SHOULD_CARRY_BACK = "The mirror should carry a traveller back to %s, carries %s";
    private static final String SHOULD_RESTORE ="Every cell of the wall's patch should be stone again, %s is not";
    private static final String SHOULD_RESTORE_PLATFORM = "The End's platform should be obsidian again";

    private DragonGateTests() {
    }

    /**
     * A Dragon Gate thrown at a stone wall lays the gate over the wall's
     * three by three patch and the mirror on the End's platform, each gate
     * carrying a traveller to stand beside the other; closing the pair on its
     * clock turns the wall back to stone and the platform back to obsidian.
     *
     * @param helper the gametest helper
     */
    public static void dragonGateLaysATemporaryPortal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> wall = GateFootprint.patch(helper.absolutePos(WALL_CENTER), Direction.SOUTH);
        wall.forEach(cell -> level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState()));
        AbilityDefinition gate = AbilityRegistry.of(level).getAbility(ENDER_DRAGON_GATE);
        helper.assertTrue(gate != null, ABILITY_REQUIRED);

        AbilityImpact.land(level, helper.absolutePos(WALL_CENTER), GooTypes.ENDER, Direction.SOUTH, gate);

        Optional<DragonGates.Pair> pair = DragonGates.get(level).pairs().stream()
                .filter(open -> open.near().center().equals(helper.absolutePos(WALL_CENTER))).findFirst();
        helper.assertTrue(pair.isPresent(), SHOULD_OPEN);
        for (BlockPos cell : wall) {
            helper.assertTrue(level.getBlockState(cell).is(GooBlocks.DRAGON_GATE.get()), String.format(SHOULD_COVER, cell));
        }
        ServerLevel end = level.getServer().getLevel(Level.END);
        BlockPos platform = pair.get().far().center();
        helper.assertTrue(end != null && end.getBlockState(platform).is(GooBlocks.DRAGON_GATE.get()), SHOULD_MIRROR);
        Mob traveller = helper.spawnWithNoFreeWill(EntityType.COW, WALL_CENTER.south());
        TeleportTransition there = GooBlocks.DRAGON_GATE.get().getPortalDestination(level, traveller,
                helper.absolutePos(WALL_CENTER));
        helper.assertTrue(there != null && there.newLevel() == end && there.position().equals(pair.get().far().arrival()),
                String.format(SHOULD_CARRY_THERE, pair.get().far().arrival(), there));
        TeleportTransition back = GooBlocks.DRAGON_GATE.get().getPortalDestination(end, traveller, platform);
        helper.assertTrue(back != null && back.newLevel() == level && back.position().equals(pair.get().near().arrival()),
                String.format(SHOULD_CARRY_BACK, pair.get().near().arrival(), back));
        traveller.discard();

        DragonGateOpening.closeExpired(level.getServer(), pair.get().closesAt());

        for (BlockPos cell : wall) {
            helper.assertTrue(level.getBlockState(cell).is(Blocks.STONE), String.format(SHOULD_RESTORE, cell));
        }
        helper.assertTrue(end.getBlockState(platform).is(Blocks.OBSIDIAN), SHOULD_RESTORE_PLATFORM);
        helper.succeed();
    }
}
