package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.gate.AstralGateOpening;
import com.mercuriusxeno.goo.ability.gate.EndGateOpening;
import com.mercuriusxeno.goo.ability.gate.EndGates;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

/**
 * Gametests for Astral's gate: thrown at a stone wall by the overworld's
 * night, it opens a pair whose mirror stands in goo:lunar, and a player
 * stepping through stands in goo:lunar and comes back through the same pair
 * (decision astral-visits-lunar-and-solar-dimensions).
 */
public final class AstralGateTests {

    private static final Identifier ASTRAL_GATE = Identifier.parse("goo:astral_gate");
    /** The wall block the blob strikes on its south face, the gate's centre. */
    private static final BlockPos WALL_CENTER = new BlockPos(4, 2, 4);
    private static final String ABILITY_REQUIRED = "goo:astral_gate must be loaded";
    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String SOLAR_REQUIRED = "The server should hold goo:solar";
    private static final String SHOULD_OPEN = "The gate should stand open as a pair over the wall";
    private static final String SHOULD_MIRROR_IN_LUNAR = "By night the mirror should lie in goo:lunar, lies in %s";
    private static final String SHOULD_ARRIVE = "The player should stand in %s at %s, stands in %s at %s";
    private static final String SHOULD_CLEAR = "The lunar mirror should be gone once the pair closes";

    private AstralGateTests() {
    }

    /**
     * By the overworld's night an Astral gate thrown at a stone wall opens a
     * pair whose mirror lies on goo:lunar's arrival floor; a player carried
     * through the wall gate stands in goo:lunar beside the mirror, and
     * carried through the mirror stands back beside the wall gate.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void astralGateRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel lunar = level.getServer().getLevel(AstralGateOpening.LUNAR);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        helper.assertTrue(level.getServer().getLevel(AstralGateOpening.SOLAR) != null, SOLAR_REQUIRED);
        BlockPos center = helper.absolutePos(WALL_CENTER);
        List<BlockPos> wall = List.of(center, center.above(), center.below(), center.east(), center.west(),
                center.above().east(), center.above().west(), center.below().east(), center.below().west());
        wall.forEach(cell -> level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState()));
        wall.forEach(cell -> level.setBlockAndUpdate(cell.south(), Blocks.AIR.defaultBlockState()));
        AbilityDefinition gate = AbilityRegistry.of(level).getAbility(ASTRAL_GATE);
        helper.assertTrue(gate != null, ABILITY_REQUIRED);

        AbilityImpact.land(level, center, GooTypes.ASTRAL, Direction.SOUTH, gate);

        Optional<EndGates.Pair> pair = EndGates.get(level).pairs().stream()
                .filter(open -> open.near().center().equals(center)).findFirst();
        helper.assertTrue(pair.isPresent(), SHOULD_OPEN);
        helper.assertTrue(pair.get().far().dimension() == AstralGateOpening.LUNAR,
                String.format(SHOULD_MIRROR_IN_LUNAR, pair.get().far().dimension()));

        ServerPlayer traveller = helper.makeMockServerPlayerInLevel();
        traveller.snapTo(center.south().getCenter());
        TeleportTransition there = GooBlocks.END_GATE.get().getPortalDestination(level, traveller, center.south());
        helper.assertTrue(there != null, SHOULD_OPEN);
        traveller.teleport(there);
        assertStandsAt(helper, traveller, lunar, pair.get().far().arrival());

        BlockPos mirrorCell = pair.get().far().center().above();
        TeleportTransition back = GooBlocks.END_GATE.get().getPortalDestination(lunar, traveller, mirrorCell);
        helper.assertTrue(back != null, SHOULD_OPEN);
        traveller.teleport(back);
        assertStandsAt(helper, traveller, level, pair.get().near().arrival());

        EndGateOpening.closeExpired(level.getServer(), pair.get().closesAt());
        helper.assertTrue(!lunar.getBlockState(mirrorCell).is(GooBlocks.END_GATE.get()), SHOULD_CLEAR);
        helper.succeed();
    }

    private static void assertStandsAt(GameTestHelper helper, ServerPlayer traveller, ServerLevel level,
                                       Vec3 arrival) {
        helper.assertTrue(traveller.level() == level && traveller.position().equals(arrival),
                String.format(SHOULD_ARRIVE, level.dimension().identifier(), arrival,
                        traveller.level().dimension().identifier(), traveller.position()));
    }
}
