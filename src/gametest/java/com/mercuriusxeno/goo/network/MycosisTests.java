package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Shroom's Mycosis as a world goo through the real paths: one of its
 * stream's drips lays a one-layer Mycosis cell, a zombie standing in the goo
 * carries the mycosis effect and the spores, and an infected zombie dying on
 * bare floor leaves Mycosis goo where it fell. Each test walls its cells in
 * stone so no cell pours its layers away.
 * mycosis-is-the-residual-goo-that-infests-with-mushrooms
 * mycosis-goo-infects-what-touches-it-as-the-spray-did
 */
@RegistersGameTests
public final class MycosisTests {

    private static final String DRIP_LAYS_A_SHROOM_CELL = "mycosis_drip_lays_a_shroom_cell_of_one_layer";
    private static final String GOO_INFECTS_A_ZOMBIE = "mycosis_goo_infects_a_zombie_standing_in_it";
    private static final String DEAD_LEAVE_GOO = "mycosis_infected_zombie_dying_leaves_goo_where_it_fell";
    private static final Identifier MYCOSIS = Identifier.parse("goo:shroom_mycosis");
    /** The floor block the drip strikes, walled on its four sides one block up. */
    private static final BlockPos DRIP_FLOOR = new BlockPos(2, 1, 2);
    /** The full cell the zombie stands on. */
    private static final BlockPos INFECTING_CELL = new BlockPos(1, 2, 1);
    /** The bare cell the infected zombie dies in, its floor stone and no goo near it. */
    private static final BlockPos FELL_CELL = new BlockPos(3, 2, 3);
    private static final String ABILITY_REQUIRED = "The ability registry must hold shroom_mycosis";
    private static final String DRIP_LAYERS_WRONG = "The cell over the floor should be Mycosis of 1 layer, stands %s";
    private static final String NOT_INFECTED = "The zombie standing in Mycosis should carry the mycosis effect";
    private static final String NOT_SPORED = "The zombie standing in Mycosis should carry spores";
    private static final String NO_GOO_WHERE_IT_FELL = "The infected zombie should leave Mycosis where it fell, stands %s";

    private MycosisTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_A_SHROOM_CELL, MycosisTests::dripLaysAShroomCell);
        GooTestFunctions.registerTest(registrar, GOO_INFECTS_A_ZOMBIE, MycosisTests::gooInfectsAZombie);
        GooTestFunctions.registerTest(registrar, DEAD_LEAVE_GOO, MycosisTests::infectedZombieDyingLeavesGoo);
    }

    /**
     * One Mycosis drip striking a walled stone top, from a shroom-holding
     * caster who pays for its layer, lays a Mycosis cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysAShroomCell(GameTestHelper helper) {
        BlockPos cell = DRIP_FLOOR.above();
        helper.setBlock(DRIP_FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        wallIn(helper, cell);
        AbilityDefinition mycosis = AbilityRegistry.of(helper.getLevel()).getAbility(MYCOSIS);
        helper.assertTrue(mycosis != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.SHROOM);
        KnownRecipes.teachRequires(caster, mycosis);
        LandedDrips.landOn(helper, caster, mycosis, DRIP_FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        helper.assertTrue(state.is(GooBlocks.SHROOM_MYCOSIS.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1,
                String.format(DRIP_LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A zombie standing on a full Mycosis cell carries the mycosis effect
     * and the spores that burst Mycosis from its corpse.
     * mycosis-goo-infects-what-touches-it-as-the-spray-did
     *
     * @param helper the gametest helper
     */
    public static void gooInfectsAZombie(GameTestHelper helper) {
        layFullCell(helper, INFECTING_CELL);
        Mob zombie = helmetedZombieOn(helper, INFECTING_CELL);
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.hasEffect(GooMobEffects.MYCOSIS), NOT_INFECTED);
            helper.assertTrue(zombie.hasData(GooAttachments.SPORED), NOT_SPORED);
        });
    }

    /**
     * A zombie infected on a full Mycosis cell, moved onto bare stone floor
     * away from the goo and killed there, leaves a Mycosis cell where it fell.
     * mycosis-goo-infects-what-touches-it-as-the-spray-did
     *
     * @param helper the gametest helper
     */
    public static void infectedZombieDyingLeavesGoo(GameTestHelper helper) {
        layFullCell(helper, INFECTING_CELL);
        helper.setBlock(FELL_CELL.below(), Blocks.STONE);
        helper.setBlock(FELL_CELL, Blocks.AIR);
        Mob zombie = helmetedZombieOn(helper, INFECTING_CELL);
        AtomicBoolean killed = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (!killed.get()) {
                helper.assertTrue(zombie.hasData(GooAttachments.SPORED), NOT_SPORED);
                Vec3 fell = Vec3.atBottomCenterOf(helper.absolutePos(FELL_CELL));
                zombie.teleportTo(fell.x, fell.y, fell.z);
                zombie.kill(helper.getLevel());
                killed.set(true);
            }
            BlockState left = helper.getBlockState(FELL_CELL);
            helper.assertTrue(left.is(GooBlocks.SHROOM_MYCOSIS.get()), String.format(NO_GOO_WHERE_IT_FELL, left));
        });
    }

    /**
     * Lays a full Mycosis cell on a stone floor, walled in stone on its four
     * sides so no layer pours away.
     *
     * @param helper the gametest helper
     * @param cell   the cell, relative to the test
     */
    private static void layFullCell(GameTestHelper helper, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        wallIn(helper, cell);
        helper.setBlock(cell, GooBlocks.SHROOM_MYCOSIS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
    }

    private static void wallIn(GameTestHelper helper, BlockPos cell) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
    }

    /**
     * A zombie standing on a cell's top, helmeted so daylight leaves it unburnt.
     *
     * @param helper the gametest helper
     * @param cell   the cell it stands on, relative to the test
     * @return the zombie
     */
    private static Mob helmetedZombieOn(GameTestHelper helper, BlockPos cell) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, Vec3.atBottomCenterOf(cell.above()));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }
}
