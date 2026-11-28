package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Durable's Coat through the real paths: its cell resists an explosion and a pick at least as obsidian does at any
 * fill, one layer of it laid by drips on a block's exposed faces keeps that block through a TNT blast that breaks the
 * same block bare, and its drips hold on a block's side and underside once the pour settles.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
@RegistersGameTests
public final class CoatTests {

    private static final String RESISTS_AS_OBSIDIAN = "coat_cell_resists_as_obsidian_does";
    private static final String KEEPS_A_BLOCK_THROUGH_TNT = "one_layer_of_coat_keeps_a_block_through_tnt";
    private static final String HOLDS_ON_A_SIDE_AND_UNDERSIDE = "coat_holds_on_a_side_and_an_underside";
    private static final Identifier COAT = Identifier.parse("goo:durable_coat");
    private static final String ABILITY_REQUIRED = "The ability registry must hold goo:durable_coat";
    private static final String BLOCK_REQUIRED = "The block registry must hold goo:durable_coat";
    private static final int ONE_LAYER = 1;
    private static final int FULL = 15;
    /** Where the resistance test reads the Coat cell. */
    private static final BlockPos READ_CELL = new BlockPos(2, 1, 2);
    private static final String RESISTS_LESS = "A Coat cell of %d layers should resist an explosion as obsidian does,"
            + " %s against %s";
    private static final String MINES_FASTER = "A Coat cell of %d layers should take a pick at least as long as"
            + " obsidian, %s against %s";
    private static final String UNBREAKABLE = "A Coat cell of %d layers should be mineable, its destroy speed is -1";
    /** The chamber's far corner: an obsidian shell from the origin to here, open inside. */
    private static final int CHAMBER_EDGE = 5;
    /** The dirt block one layer of Coat wraps, against the chamber's west wall. */
    private static final BlockPos COATED_DIRT = new BlockPos(1, 1, 2);
    /** The bare dirt block, against the chamber's east wall. */
    private static final BlockPos BARE_DIRT = new BlockPos(4, 1, 2);
    /** The TNT, as far from each dirt block's center as from the other. */
    private static final Vec3 TNT_AT = new Vec3(3.0, 1.0, 2.5);
    /** The coated dirt's faces the chamber leaves exposed, each a drip strikes. */
    private static final List<Direction> EXPOSED_FACES = List.of(Direction.UP, Direction.EAST, Direction.NORTH,
            Direction.SOUTH);
    /** Past the TNT's fuse and its blast. */
    private static final int BLAST_TICKS = 10;
    private static final String COATED_DIRT_BROKE = "The dirt one layer of Coat wraps should stand the blast,"
            + " stands %s";
    private static final String COAT_BROKE = "The Coat cell on the dirt's %s face should stand the blast, stands %s";
    private static final String BARE_DIRT_STOOD = "The bare dirt should break in the blast, stands %s";
    /** A stone block hung in open air, whose west face and underside the drips strike. */
    private static final BlockPos HUNG_BLOCK = new BlockPos(3, 4, 2);
    /** Long past a runny goo's pour. */
    private static final int SETTLE_TICKS = 40;
    private static final String SIDE_SLID = "The Coat drip on the hung block's west face should still hold there"
            + " once it settles, stands %s";
    private static final String UNDERSIDE_FELL = "The Coat drip under the hung block should still hold there once"
            + " it settles, stands %s";

    private CoatTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RESISTS_AS_OBSIDIAN, CoatTests::coatCellResistsAsObsidianDoes);
        GooTestFunctions.registerTest(registrar, KEEPS_A_BLOCK_THROUGH_TNT, CoatTests::oneLayerOfCoatKeepsABlockThroughTnt);
        GooTestFunctions.registerTest(registrar, HOLDS_ON_A_SIDE_AND_UNDERSIDE, CoatTests::coatHoldsOnASideAndAnUnderside);
    }

    /**
     * The registered Coat cell, at one layer and at fifteen, resists an explosion at least as obsidian does and takes
     * at least obsidian's time to mine, and is mineable.
     *
     * @param helper the gametest helper
     */
    public static void coatCellResistsAsObsidianDoes(GameTestHelper helper) {
        Block coat = coatBlock(helper);
        BlockPos at = helper.absolutePos(READ_CELL);
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        float obsidianResistance = obsidian.getExplosionResistance(helper.getLevel(), at, null);
        float obsidianSpeed = obsidian.getDestroySpeed(helper.getLevel(), at);
        for (int layers : List.of(ONE_LAYER, FULL)) {
            BlockState cell = coat.defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers);
            float resistance = cell.getExplosionResistance(helper.getLevel(), at, null);
            float speed = cell.getDestroySpeed(helper.getLevel(), at);
            helper.assertTrue(resistance >= obsidianResistance,
                    String.format(RESISTS_LESS, layers, resistance, obsidianResistance));
            helper.assertTrue(speed >= 0, String.format(UNBREAKABLE, layers));
            helper.assertTrue(speed >= obsidianSpeed, String.format(MINES_FASTER, layers, speed, obsidianSpeed));
        }
        helper.succeed();
    }

    /**
     * In an obsidian chamber, drips lay one layer of Coat on each exposed face of one dirt block, and TNT goes off
     * as far from it as from a bare dirt block: the coated dirt and its Coat stand, and the bare dirt is gone.
     *
     * @param helper the gametest helper
     */
    public static void oneLayerOfCoatKeepsABlockThroughTnt(GameTestHelper helper) {
        Block coat = coatBlock(helper);
        buildAnObsidianChamber(helper);
        helper.setBlock(COATED_DIRT, Blocks.DIRT);
        helper.setBlock(BARE_DIRT, Blocks.DIRT);
        AbilityDefinition ability = coatAbility(helper);
        ServerPlayer caster = coatCaster(helper, ability);
        for (Direction face : EXPOSED_FACES) {
            if (face == Direction.UP) {
                LandedDrips.landOn(helper, caster, ability, COATED_DIRT.above(), 1);
            } else {
                LandedDrips.landOnSide(helper, caster, ability, COATED_DIRT, face, 1);
            }
        }
        helper.getLevel().getServer().getPlayerList().remove(caster);
        PrimedTnt tnt = helper.spawn(EntityTypes.TNT, TNT_AT);
        tnt.setFuse(1);
        helper.runAfterDelay(BLAST_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(COATED_DIRT).is(Blocks.DIRT),
                    String.format(COATED_DIRT_BROKE, helper.getBlockState(COATED_DIRT)));
            for (Direction face : EXPOSED_FACES) {
                BlockState cell = helper.getBlockState(COATED_DIRT.relative(face));
                helper.assertTrue(cell.is(coat), String.format(COAT_BROKE, face, cell));
            }
            helper.assertTrue(helper.getBlockState(BARE_DIRT).isAir(),
                    String.format(BARE_DIRT_STOOD, helper.getBlockState(BARE_DIRT)));
            helper.succeed();
        });
    }

    /**
     * One Coat drip on the west face of a stone block hung in open air and one under it each leave a Coat cell that
     * still stands there once the pour has settled.
     *
     * @param helper the gametest helper
     */
    public static void coatHoldsOnASideAndAnUnderside(GameTestHelper helper) {
        Block coat = coatBlock(helper);
        helper.setBlock(HUNG_BLOCK, Blocks.STONE);
        for (int y = 1; y < HUNG_BLOCK.getY(); y++) {
            helper.setBlock(new BlockPos(HUNG_BLOCK.getX(), y, HUNG_BLOCK.getZ()), Blocks.AIR);
            helper.setBlock(new BlockPos(HUNG_BLOCK.getX() - 1, y, HUNG_BLOCK.getZ()), Blocks.AIR);
        }
        helper.setBlock(HUNG_BLOCK.west(), Blocks.AIR);
        AbilityDefinition ability = coatAbility(helper);
        ServerPlayer caster = coatCaster(helper, ability);
        LandedDrips.landOnSide(helper, caster, ability, HUNG_BLOCK, Direction.WEST, 1);
        LandedDrips.landOnSide(helper, caster, ability, HUNG_BLOCK, Direction.DOWN, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BlockState side = helper.getBlockState(HUNG_BLOCK.west());
            helper.assertTrue(side.is(coat), String.format(SIDE_SLID, side));
            BlockState under = helper.getBlockState(HUNG_BLOCK.below());
            helper.assertTrue(under.is(coat) && under.getValue(VolumeCellBlock.CEILING),
                    String.format(UNDERSIDE_FELL, under));
            helper.succeed();
        });
    }

    private static Block coatBlock(GameTestHelper helper) {
        Block coat = BuiltInRegistries.BLOCK.getValue(COAT);
        helper.assertTrue(coat instanceof VolumeCellBlock, BLOCK_REQUIRED);
        return coat;
    }

    private static AbilityDefinition coatAbility(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(COAT);
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }

    // A durable-holding caster who knows Coat and pays for the layers its drips lay.
    private static ServerPlayer coatCaster(GameTestHelper helper, AbilityDefinition ability) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.DURABLE);
        KnownRecipes.teachRequires(caster, ability);
        return caster;
    }

    // An obsidian shell from the bay's origin to its far corner, open inside, so the blast breaks nothing past it.
    private static void buildAnObsidianChamber(GameTestHelper helper) {
        for (int x = 0; x <= CHAMBER_EDGE; x++) {
            for (int y = 0; y <= CHAMBER_EDGE; y++) {
                for (int z = 0; z <= CHAMBER_EDGE; z++) {
                    boolean shell = x == 0 || y == 0 || z == 0 || x == CHAMBER_EDGE || y == CHAMBER_EDGE
                            || z == CHAMBER_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), shell ? Blocks.OBSIDIAN : Blocks.AIR);
                }
            }
        }
    }
}
