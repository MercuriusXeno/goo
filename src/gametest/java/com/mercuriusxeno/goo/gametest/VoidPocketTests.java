package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.gate.EndGateOpening;
import com.mercuriusxeno.goo.ability.gate.EndGates;
import com.mercuriusxeno.goo.ability.gate.VoidPocketOpening;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametest for Void's pocket: thrown at a stone wall, it opens a pair whose
 * mirror lies on the thrower's room floor in goo:pocket. The thrower steps
 * through, builds a block in the room, comes back through the same pair, and
 * the block stands in the room after the pair closes and when the next cast
 * opens the same room; a thrower left inside at closing is carried out
 * (decision void-opens-a-pocket-dimension).
 */
@RegistersGameTests
public final class VoidPocketTests {
    private static final String VOID_POCKET_ROUND_TRIP = "void_pocket_round_trip";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VOID_POCKET_ROUND_TRIP, VoidPocketTests::voidPocketRoundTrip);
    }

    private static final Identifier VOID_POCKET = Identifier.parse("goo:void_pocket");
    /** The wall block the blob strikes on its south face, the gate's centre. */
    private static final BlockPos WALL_CENTER = new BlockPos(4, 2, 4);
    /** Where in the room, from the floor's centre, the traveller builds: clear of the 3 by 3 mirror. */
    private static final BlockPos BUILT_FROM_FLOOR = new BlockPos(3, 1, 3);
    private static final String ABILITY_REQUIRED = "goo:void_pocket must be loaded";
    private static final String POCKET_REQUIRED = "The server should hold the pocket dimension";
    private static final String SHOULD_BE_GOO_OWNED = "The pocket dimension should register under goo, registers as %s";
    private static final String SHOULD_OPEN = "The pocket gate should stand open as a pair over the wall";
    private static final String SHOULD_MIRROR_IN_POCKET = "The mirror should lie in the pocket, lies in %s";
    private static final String SHOULD_ARRIVE = "The player should stand in %s at %s, stands in %s at %s";
    private static final String SHOULD_KEEP_BLOCK = "The block built in the room should stand at %s, %s stands there";
    private static final String SHOULD_REOPEN_SAME_ROOM = "The next cast should open the same room at %s, opened %s";

    private VoidPocketTests() {
    }

    /**
     * A Void pocket thrown at a stone wall opens a pair into the thrower's
     * room; the thrower carried through stands in goo:pocket, builds a block,
     * and carried back stands beside the wall gate. Once the pair closes, the
     * block still stands; the next cast opens the same room, and a thrower
     * left inside when that pair closes stands beside the wall gate again.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void voidPocketRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevel pocket = level.getServer().getLevel(VoidPocketOpening.POCKET);
        helper.assertTrue(pocket != null, POCKET_REQUIRED);
        helper.assertTrue(Goo.MODID.equals(VoidPocketOpening.POCKET.identifier().getNamespace()),
                String.format(SHOULD_BE_GOO_OWNED, VoidPocketOpening.POCKET.identifier()));
        BlockPos center = helper.absolutePos(WALL_CENTER);
        List<BlockPos> wall = List.of(center, center.above(), center.below(), center.east(), center.west(),
                center.above().east(), center.above().west(), center.below().east(), center.below().west());
        wall.forEach(cell -> level.setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState()));
        wall.forEach(cell -> level.setBlockAndUpdate(cell.south(), Blocks.AIR.defaultBlockState()));
        ServerPlayer traveller = helper.makeMockServerPlayerInLevel();

        EndGates.Pair first = cast(helper, level, center, traveller);
        traveller.snapTo(center.south().getCenter());
        travel(helper, traveller, level, center.south());
        assertStandsAt(helper, traveller, pocket, first.far().arrival());
        BlockPos built = first.far().center().offset(BUILT_FROM_FLOOR);
        pocket.setBlockAndUpdate(built, Blocks.GLOWSTONE.defaultBlockState());
        travel(helper, traveller, pocket, first.far().center().above());
        assertStandsAt(helper, traveller, level, first.near().arrival());
        EndGateOpening.closeExpired(level.getServer(), first.closesAt());
        helper.assertTrue(pocket.getBlockState(built).is(Blocks.GLOWSTONE),
                String.format(SHOULD_KEEP_BLOCK, built, pocket.getBlockState(built)));

        EndGates.Pair second = cast(helper, level, center, traveller);
        helper.assertTrue(second.far().center().equals(first.far().center()),
                String.format(SHOULD_REOPEN_SAME_ROOM, first.far().center(), second.far().center()));
        travel(helper, traveller, level, center.south());
        assertStandsAt(helper, traveller, pocket, second.far().arrival());
        EndGateOpening.closeExpired(level.getServer(), second.closesAt());
        assertStandsAt(helper, traveller, level, second.near().arrival());
        helper.assertTrue(pocket.getBlockState(built).is(Blocks.GLOWSTONE),
                String.format(SHOULD_KEEP_BLOCK, built, pocket.getBlockState(built)));
        helper.succeed();
    }

    private static EndGates.Pair cast(GameTestHelper helper, ServerLevel level, BlockPos center,
                                      ServerPlayer thrower) {
        AbilityDefinition pocketGate = AbilityRegistry.of(level).getAbility(VOID_POCKET);
        helper.assertTrue(pocketGate != null, ABILITY_REQUIRED);
        AbilityImpact.land(level, center, GooTypes.VOID, Direction.SOUTH, pocketGate, null, 0, thrower.getUUID());
        EndGates.Pair pair = EndGates.get(level).pairs().stream()
                .filter(open -> open.near().center().equals(center)).findFirst().orElse(null);
        helper.assertTrue(pair != null, SHOULD_OPEN);
        helper.assertTrue(pair.far().dimension() == VoidPocketOpening.POCKET,
                String.format(SHOULD_MIRROR_IN_POCKET, pair.far().dimension()));
        return pair;
    }

    private static void travel(GameTestHelper helper, ServerPlayer traveller, ServerLevel from, BlockPos gateCell) {
        TeleportTransition through = GooBlocks.END_GATE.get().getPortalDestination(from, traveller, gateCell);
        helper.assertTrue(through != null, SHOULD_OPEN);
        traveller.teleport(through);
    }

    private static void assertStandsAt(GameTestHelper helper, ServerPlayer traveller, ServerLevel level,
                                       Vec3 arrival) {
        helper.assertTrue(traveller.level() == level && traveller.position().equals(arrival),
                String.format(SHOULD_ARRIVE, level.dimension().identifier(), arrival,
                        traveller.level().dimension().identifier(), traveller.position()));
    }
}
