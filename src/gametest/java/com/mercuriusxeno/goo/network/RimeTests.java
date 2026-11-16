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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Frost's Rime through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Rime cell there, as slippery as ice.
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 * rime-goo-is-slippery-to-walk-on
 */
@RegistersGameTests
public final class RimeTests {

    private static final String RIME_DRIP_LAYS_AN_ICY_CELL = "rime_drip_lays_an_icy_cell_on_stone";
    private static final Identifier RIME = Identifier.parse("goo:frost_rime");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold frost_rime";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Rime, stands %s";
    private static final String FRICTION_WRONG = "The Rime cell should stand at ice's friction %s, stands at %s";
    private static final String RIME_FREEZES_WATER_AND_LAVA = "rime_freezes_the_water_and_lava_in_reach";
    private static final String RIME_ICE_THAWS = "rime_ice_thaws_once_no_rime_stands_in_reach";
    private static final Identifier RIME_ICE = Identifier.parse("goo:rime_ice");
    /** The Rime cell, lying on the bay floor. */
    private static final BlockPos RIME_CELL = new BlockPos(2, 1, 2);
    /** A water source sunk into the floor, a block and a half from the cell. */
    private static final BlockPos WATER_POOL = new BlockPos(1, 0, 3);
    /** A lava source sunk into the floor, across the cell from the water. */
    private static final BlockPos LAVA_POOL = new BlockPos(3, 0, 1);
    /** Ticks a laid cell takes to tick and freeze what is in reach, with room to spare. */
    private static final int CHILL_WAIT_TICKS = 10;
    /** Ticks a broken cell's ice takes to thaw: the thaw pass runs every second, with room to spare. */
    private static final int THAW_WAIT_TICKS = 45;
    private static final String SHOULD_ICE = "The water in the Rime cell's reach should stand as Rime ice, stands %s";
    private static final String SHOULD_OBSIDIAN = "The lava in the Rime cell's reach should stand as obsidian, stands %s";
    private static final String RIME_FREEZES_THE_SHOELESS = "rime_freezes_and_hurts_a_barefoot_zombie";
    /** One layer's height, where a thing standing on a one-layer cell stands. */
    private static final double ONE_LAYER = 1.0 / VolumeCellBlock.MAX_LAYERS;
    /** Ticks for the damage cadence to pass once, with room to spare. */
    private static final int DAMAGE_WAIT_TICKS = 30;
    private static final String SHOULD_FREEZE = "The barefoot zombie on Rime should be freezing, its frozen ticks stand at %s";
    private static final String SHOULD_HURT = "The barefoot zombie on Rime should be hurt, from %s it stands at %s";
    private static final String RIME_HOLDS_THE_FEET = "thin_rime_sets_the_friction_underfoot";
    /** Ticks a spawned mob takes to settle onto the cell, with room to spare. */
    private static final int SETTLE_TICKS = 5;
    private static final String SHOULD_STAND_ON_RIME = "The zombie's friction should come from the Rime cell, comes from %s";
    private static final String SHOULD_THAW = "The Rime ice should read water once no Rime cell stands in reach, stands %s";

    private RimeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RIME_DRIP_LAYS_AN_ICY_CELL, RimeTests::rimeDripLaysAnIcyCellOnStone);
        GooTestFunctions.registerTest(registrar, RIME_FREEZES_WATER_AND_LAVA, RimeTests::rimeFreezesTheWaterAndLavaInReach);
        GooTestFunctions.registerTest(registrar, RIME_ICE_THAWS, RimeTests::rimeIceThawsOnceNoRimeStandsInReach);
        GooTestFunctions.registerTest(registrar, RIME_FREEZES_THE_SHOELESS, RimeTests::rimeFreezesAndHurtsABarefootZombie);
        GooTestFunctions.registerTest(registrar, RIME_HOLDS_THE_FEET, RimeTests::thinRimeSetsTheFrictionUnderfoot);
    }

    /**
     * A mob standing on a one-layer Rime cell, thinner than the fifth of a
     * block vanilla reads under the feet, meets the cell's friction rather
     * than the stone's under it, so the thinnest Rime is as slippery as ice.
     * rime-goo-is-slippery-to-walk-on
     *
     * @param helper the gametest helper
     */
    public static void thinRimeSetsTheFrictionUnderfoot(GameTestHelper helper) {
        helper.setBlock(RIME_CELL.below(), Blocks.STONE);
        helper.setBlock(RIME_CELL, GooBlocks.FROST_RIME.get());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                Vec3.atBottomCenterOf(RIME_CELL).add(0, ONE_LAYER, 0));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            BlockPos underfoot = zombie.getBlockPosBelowThatAffectsMyMovement();
            helper.assertTrue(underfoot.equals(helper.absolutePos(RIME_CELL)),
                    String.format(SHOULD_STAND_ON_RIME, helper.getLevel().getBlockState(underfoot)));
            helper.succeed();
        });
    }

    /**
     * A zombie with nothing on its feet standing on a Rime cell has its
     * frozen ticks risen and its health fallen once the damage cadence has
     * passed. Its iron helmet keeps the sun from burning it, so only Rime
     * can lower its health.
     * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
     *
     * @param helper the gametest helper
     */
    public static void rimeFreezesAndHurtsABarefootZombie(GameTestHelper helper) {
        helper.setBlock(RIME_CELL.below(), Blocks.STONE);
        helper.setBlock(RIME_CELL, GooBlocks.FROST_RIME.get());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE,
                Vec3.atBottomCenterOf(RIME_CELL).add(0, ONE_LAYER, 0));
        zombie.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        float startingHealth = zombie.getHealth();
        helper.runAfterDelay(DAMAGE_WAIT_TICKS, () -> {
            helper.assertTrue(zombie.getTicksFrozen() > 0, String.format(SHOULD_FREEZE, zombie.getTicksFrozen()));
            helper.assertTrue(zombie.getHealth() < startingHealth,
                    String.format(SHOULD_HURT, startingHealth, zombie.getHealth()));
            helper.succeed();
        });
    }

    /**
     * A Rime cell standing over stone, once it has ticked, freezes the water
     * source in reach to Rime ice and the lava source in reach to obsidian.
     * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
     *
     * @param helper the gametest helper
     */
    public static void rimeFreezesTheWaterAndLavaInReach(GameTestHelper helper) {
        layRimeBesideWaterAndLava(helper);
        helper.runAfterDelay(CHILL_WAIT_TICKS, () -> {
            assertFrozen(helper);
            helper.succeed();
        });
    }

    /**
     * Water a Rime cell froze reads water again once the cell is broken and
     * the thaw pass has run, no Rime cell standing in reach of it.
     * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
     *
     * @param helper the gametest helper
     */
    public static void rimeIceThawsOnceNoRimeStandsInReach(GameTestHelper helper) {
        layRimeBesideWaterAndLava(helper);
        helper.runAfterDelay(CHILL_WAIT_TICKS, () -> {
            assertFrozen(helper);
            helper.setBlock(RIME_CELL, Blocks.AIR);
        });
        helper.runAfterDelay(CHILL_WAIT_TICKS + THAW_WAIT_TICKS, () -> {
            BlockState water = helper.getBlockState(WATER_POOL);
            helper.assertTrue(water.is(Blocks.WATER), String.format(SHOULD_THAW, water));
            helper.succeed();
        });
    }

    /**
     * Lays a one-layer Rime cell on stone with a walled water source and a
     * walled lava source sunk into the floor within its reach, apart from
     * each other so neither meets the other.
     */
    private static void layRimeBesideWaterAndLava(GameTestHelper helper) {
        helper.setBlock(RIME_CELL.below(), Blocks.STONE);
        basin(helper, WATER_POOL, Blocks.WATER);
        basin(helper, LAVA_POOL, Blocks.LAVA);
        helper.setBlock(RIME_CELL, GooBlocks.FROST_RIME.get());
    }

    private static void assertFrozen(GameTestHelper helper) {
        BlockState water = helper.getBlockState(WATER_POOL);
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(water.getBlock()).equals(RIME_ICE),
                String.format(SHOULD_ICE, water));
        BlockState lava = helper.getBlockState(LAVA_POOL);
        helper.assertTrue(lava.is(Blocks.OBSIDIAN), String.format(SHOULD_OBSIDIAN, lava));
    }

    private static void basin(GameTestHelper helper, BlockPos pos, Block fluid) {
        helper.setBlock(pos.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(pos.relative(side), Blocks.STONE);
        }
        helper.setBlock(pos, fluid);
    }

    /**
     * One Rime drip striking a stone block's top, from a frost-holding caster
     * who pays for its layer, lays a Rime cell of one layer over it, standing
     * at ice's friction.
     *
     * @param helper the gametest helper
     */
    public static void rimeDripLaysAnIcyCellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition rime = AbilityRegistry.of(helper.getLevel()).getAbility(RIME);
        helper.assertTrue(rime != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        KnownRecipes.teachRequires(caster, rime);
        LandedDrips.landOn(helper, caster, rime, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(RIME)
                && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        float ice = Blocks.ICE.getFriction();
        float friction = state.getBlock().getFriction();
        helper.assertTrue(friction == ice, String.format(FRICTION_WRONG, ice, friction));
        helper.succeed();
    }
}
