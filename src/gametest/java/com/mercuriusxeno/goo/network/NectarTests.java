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
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Jelly's Nectar, the jelly goo its held stream sprays: a drip
 * striking a block's top, from a jelly-holding caster who pays for its
 * layer, lays a Nectar cell of one layer there; an adult cow reaching the
 * goo falls in love, eating a layer of it; two zombies reaching it turn on
 * each other over it; and a drip striking a calf grows it, spent on it.
 * nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob
 * nectar-goo-keeps-feeds-effects-as-a-universal-food
 * each-feeding-eats-a-layer-of-nectar
 */
@RegistersGameTests
public final class NectarTests {

    private static final String DRIP_LAYS_A_JELLY_CELL = "nectar_drip_lays_a_jelly_cell_of_one_layer";
    private static final String COURTS_AN_ADULT_COW = "nectar_goo_courts_an_adult_cow_for_a_layer";
    private static final String SETS_TWO_ZOMBIES_FIGHTING = "nectar_goo_sets_two_zombies_fighting";
    private static final String DRIP_FEEDS_A_STRUCK_CALF = "nectar_drip_feeds_a_struck_calf";
    private static final Identifier NECTAR = Identifier.parse("goo:jelly_nectar");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    private static final int ONE_DRIP = 1;
    /** The cell the goo tests set into the stone's top course, walled on every side. */
    private static final BlockPos GOO_CELL = new BlockPos(2, 2, 2);
    private static final int FED_CELL_LAYERS = 5;
    /** The stone course the goo tests lay, its top one block over the bay's floor. */
    private static final int STONE_TOP_Y = 2;
    private static final int PEN_SPAN = 5;
    private static final int PEN_WALL_TOP_Y = 4;
    /** Where the calf stands for the strike test, on open stone. */
    private static final BlockPos CALF_FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold jelly_nectar";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Nectar of one layer, stands %s";
    private static final String COW_NOT_FED = "The cow should fall in love eating a layer: in love %b, the cell stands %s";
    private static final String ZOMBIES_NOT_FIGHTING = "Two zombies at Nectar goo should target each other: %s and %s";
    private static final String CALF_NOT_FED = "A struck calf should grow to an adult, the drip spent: baby %b, under it %s";

    private NectarTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_JELLY_CELL, NectarTests::dripLaysAJellyCellOfOneLayer);
        GooTestFunctions.registerTest(registrar, COURTS_AN_ADULT_COW, NectarTests::courtsAnAdultCowForALayer);
        GooTestFunctions.registerTest(registrar, SETS_TWO_ZOMBIES_FIGHTING, NectarTests::setsTwoZombiesFighting);
        GooTestFunctions.registerTest(registrar, DRIP_FEEDS_A_STRUCK_CALF, NectarTests::dripFeedsAStruckCalf);
    }

    /**
     * One Nectar drip striking a walled stone floor, from a jelly-holding
     * caster who pays for its layer, lays a Nectar cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAJellyCellOfOneLayer(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.landOn(helper, caster, nectar, DRIP_FLOOR, ONE_DRIP);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(NECTAR)
                && state.getValue(VolumeCellBlock.LAYERS) == ONE_DRIP, String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * An adult cow penned beside a five-layer Nectar cell walks to it and
     * eats: it falls in love, and the cell stands a layer lower.
     *
     * @param helper the gametest helper
     */
    public static void courtsAnAdultCowForALayer(GameTestHelper helper) {
        layPennedGoo(helper);
        Cow cow = helper.spawn(EntityType.COW, GOO_CELL.above().east());
        helper.succeedWhen(() -> {
            BlockState cell = helper.getBlockState(GOO_CELL);
            helper.assertTrue(cow.isInLove() && cell.is(GooBlocks.JELLY_NECTAR.get())
                    && cell.getValue(VolumeCellBlock.LAYERS) == FED_CELL_LAYERS - 1,
                    String.format(COW_NOT_FED, cow.isInLove(), cell));
        });
    }

    /**
     * Two zombies on either side of a Nectar cell, with no player in the
     * structure, turn on each other over it.
     *
     * @param helper the gametest helper
     */
    public static void setsTwoZombiesFighting(GameTestHelper helper) {
        layPennedGoo(helper);
        Mob first = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, GOO_CELL.above().east());
        Mob second = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, GOO_CELL.above().west());
        helper.succeedWhen(() -> helper.assertTrue(first.getTarget() == second && second.getTarget() == first,
                String.format(ZOMBIES_NOT_FIGHTING, first.getTarget(), second.getTarget())));
    }

    /**
     * A Nectar drip falling onto a calf strikes it and grows it to an adult,
     * spent on it, so no goo lands under it.
     *
     * @param helper the gametest helper
     */
    public static void dripFeedsAStruckCalf(GameTestHelper helper) {
        helper.setBlock(CALF_FLOOR, Blocks.STONE);
        Cow calf = helper.spawnWithNoFreeWill(EntityType.COW, CALF_FLOOR.above());
        calf.setBaby(true);
        AbilityDefinition nectar = AbilityRegistry.of(helper.getLevel()).getAbility(NECTAR);
        helper.assertTrue(nectar != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(caster, nectar);
        LandedDrips.strike(helper, caster, nectar, calf);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState under = helper.getBlockState(CALF_FLOOR.above());
        helper.assertTrue(!calf.isBaby() && under.isAir(), String.format(CALF_NOT_FED, calf.isBaby(), under));
        helper.succeed();
    }

    /**
     * Lays stone across the pen and sets a five-layer Nectar cell into its
     * top course, walled on every side, with a stone wall around the pen's
     * rim two blocks tall.
     *
     * @param helper the gametest helper
     */
    private static void layPennedGoo(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 1, 0, PEN_SPAN - 1, STONE_TOP_Y, PEN_SPAN - 1)
                .forEach(pos -> helper.setBlock(pos.immutable(), Blocks.STONE));
        BlockPos.betweenClosed(0, STONE_TOP_Y + 1, 0, PEN_SPAN - 1, PEN_WALL_TOP_Y, PEN_SPAN - 1)
                .forEach(pos -> {
                    boolean rim = pos.getX() == 0 || pos.getZ() == 0 || pos.getX() == PEN_SPAN - 1
                            || pos.getZ() == PEN_SPAN - 1;
                    helper.setBlock(pos.immutable(), rim ? Blocks.STONE : Blocks.AIR);
                });
        helper.setBlock(GOO_CELL, GooBlocks.JELLY_NECTAR.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, FED_CELL_LAYERS));
    }
}
