package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Hex's Leech through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Leech cell there, and a husk walking
 * a lane of Leech goo covers less ground than a like husk walking bare stone
 * over the same ticks.
 * leech-goo-slows-what-it-catches-and-the-spirits-damage-it
 */
@RegistersGameTests
public final class LeechTests {

    private static final String LEECH_LAYS_AND_SLOWS = "leech_drip_lays_a_cell_and_the_goo_slows_a_walker";
    private static final Identifier LEECH = Identifier.parse("goo:hex_leech");
    /** The goo lane's westmost stone, where its walker starts; the lane runs east across the bay. */
    private static final BlockPos GOO_LANE = new BlockPos(0, 1, 1);
    /** The bare lane's westmost stone, three blocks south of the goo lane, out of its walker's reach. */
    private static final BlockPos BARE_LANE = GOO_LANE.south(3);
    /** The lane's eastmost offset from its start, the bay's far wall short of one block. */
    private static final int LANE_EAST = 5;
    /** The ticks both husks walk before the ground each covered is read, short of the bare husk's whole lane. */
    private static final int WALK_TICKS = 20;
    /**
     * How far two husks walking alike lanes may part by chance: unslowed, the
     * husk stepping up onto one-layer goo and the husk on stone each covered
     * 1.957 blocks in WALK_TICKS, yet one run read the goo walker a hair
     * behind, so the goo walker must trail by more than this to count slowed.
     */
    private static final double WALK_JITTER = 0.1;
    private static final String ABILITY_REQUIRED = "The ability registry must hold hex_leech";
    private static final String LAYERS_WRONG = "The cell over the lane's stone should hold one layer of Leech,"
            + " stands %s";
    private static final String NOT_SLOWED = "The husk on Leech goo should cover less ground in %d ticks than the"
            + " husk on bare stone: %.3f on goo, %.3f on stone";

    private LeechTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LEECH_LAYS_AND_SLOWS,
                LeechTests::leechDripLaysACellAndTheGooSlowsAWalker);
    }

    /**
     * Leech drips land one layer on each stone of a lane east of its start,
     * the first of them read at one layer; a husk walks that lane while a
     * like husk walks a bare stone lane toward a like target, and after the
     * same ticks the husk in the goo has covered the less ground.
     *
     * @param helper the gametest helper
     */
    public static void leechDripLaysACellAndTheGooSlowsAWalker(GameTestHelper helper) {
        layLane(helper, GOO_LANE);
        layLane(helper, BARE_LANE);
        AbilityDefinition leech = AbilityRegistry.of(helper.getLevel()).getAbility(LEECH);
        helper.assertTrue(leech != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.HEX);
        KnownRecipes.teachRequires(caster, leech);
        for (int east = 1; east <= LANE_EAST; east++) {
            LandedDrips.landOn(helper, caster, leech, GOO_LANE.east(east), 1);
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState first = helper.getBlockState(GOO_LANE.east(1).above());
        boolean oneLayer = first.is(GooBlocks.HEX_LEECH.get()) && first.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, first));
        Mob onGoo = walkTheLane(helper, GOO_LANE);
        Mob onStone = walkTheLane(helper, BARE_LANE);
        double gooStart = onGoo.getX();
        double stoneStart = onStone.getX();
        helper.runAfterDelay(WALK_TICKS, () -> {
            double onGooCovered = onGoo.getX() - gooStart;
            double onStoneCovered = onStone.getX() - stoneStart;
            helper.assertTrue(onGooCovered + WALK_JITTER < onStoneCovered,
                    String.format(NOT_SLOWED, WALK_TICKS, onGooCovered, onStoneCovered));
            helper.succeed();
        });
    }

    /**
     * Lays a lane of stone on bedrock from its start east across the bay,
     * open air over it.
     *
     * @param helper the gametest helper
     * @param start  the lane's westmost stone
     */
    private static void layLane(GameTestHelper helper, BlockPos start) {
        for (int east = 0; east <= LANE_EAST; east++) {
            BlockPos stone = start.east(east);
            helper.setBlock(stone.below(), Blocks.BEDROCK);
            helper.setBlock(stone, Blocks.STONE);
            helper.setBlock(stone.above(), Blocks.AIR);
        }
    }

    /**
     * Spawns a husk on a lane's westmost stone, where daylight does not burn
     * it, and walks it toward the lane's eastmost block.
     *
     * @param helper the gametest helper
     * @param start  the lane's westmost stone
     * @return the walking husk
     */
    private static Mob walkTheLane(GameTestHelper helper, BlockPos start) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, start.above());
        helper.walkTo(husk, start.east(LANE_EAST).above(), 1f);
        return husk;
    }
}
