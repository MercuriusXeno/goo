package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Quantum's Phase as a world goo through the real paths: a drip lays a
 * Phase cell of one layer, and a villager standing in the goo is out of
 * phase, so the zombie that had it as its target loses it. Each test walls
 * its cell in stone so no cell pours its layers away.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * phase-shares-a-plane-between-the-phased
 */
@RegistersGameTests
public final class PhaseGooTests {

    private static final String DRIP_LAYS_ONE_LAYER = "phase_goo_drip_lays_a_cell_of_one_layer";
    private static final String VILLAGER_LOST_TO_ITS_ZOMBIE = "phase_goo_phases_a_villager_and_its_zombie_loses_it";
    private static final String STEPPING_OFF_ENDS_THE_PHASE = "phase_goo_stepping_off_ends_the_phase";
    /** Bare stone three blocks east of the walled cell, where the zombie is moved off the goo. */
    private static final BlockPos OFF_THE_GOO = new BlockPos(5, 2, 2);
    /** The ticks after stepping off by which the phase has ended. */
    private static final int STEP_OFF_TICKS = 2;
    private static final String NOT_PHASED_IN_THE_GOO = "A zombie standing in Phase goo should be out of phase";
    private static final String STILL_PHASED = "A zombie %d ticks off Phase goo should be back in phase, phase ends at %d,"
            + " game time %d";
    private static final Identifier PHASE = Identifier.parse("goo:quantum_phase");
    /** The floor block the drip strikes and the villager stands on, walled on its four sides one block up. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** Where the zombie stands, two blocks from the villager. */
    private static final BlockPos ZOMBIE_POS = new BlockPos(5, 2, 2);
    /** Ticks the villager stands in the goo before it is read: a tick to phase it, a tick for the zombie to drop it. */
    private static final int SETTLE_TICKS = 5;
    private static final String ABILITY_REQUIRED = "The ability registry must hold quantum_phase";
    private static final String LAYERS_WRONG = "The cell over the floor should be Phase of 1 layer, stands %s";
    private static final String TARGET_NOT_TAKEN = "The zombie should take the villager as its target before the goo";
    private static final String NOT_PHASED = "A villager standing in Phase goo should be out of phase";
    private static final String TARGET_KEPT = "The zombie should lose the villager standing in Phase goo";

    private PhaseGooTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DRIP_LAYS_ONE_LAYER, PhaseGooTests::dripLaysACellOfOneLayer);
        GooTestFunctions.registerTest(registrar, VILLAGER_LOST_TO_ITS_ZOMBIE,
                PhaseGooTests::phasesAVillagerAndItsZombieLosesIt);
        GooTestFunctions.registerTest(registrar, STEPPING_OFF_ENDS_THE_PHASE, PhaseGooTests::steppingOffEndsThePhase);
    }

    /**
     * One Phase drip striking a walled stone floor, from a quantum-holding
     * caster who pays for its layer, lays a Phase cell of one layer.
     *
     * @param helper the gametest helper
     */
    public static void dripLaysACellOfOneLayer(GameTestHelper helper) {
        BlockPos cell = wallTheCell(helper);
        AbilityDefinition phase = AbilityRegistry.of(helper.getLevel()).getAbility(PHASE);
        helper.assertTrue(phase != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.QUANTUM);
        KnownRecipes.teachRequires(caster, phase);
        LandedDrips.landOn(helper, caster, phase, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(cell);
        boolean oneLayer = state.is(GooBlocks.QUANTUM_PHASE.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A zombie targets a villager; a Phase cell laid under the villager puts
     * it out of phase, and the zombie no longer holds it as its target.
     *
     * @param helper the gametest helper
     */
    public static void phasesAVillagerAndItsZombieLosesIt(GameTestHelper helper) {
        BlockPos cell = wallTheCell(helper);
        helper.setBlock(ZOMBIE_POS.below(), Blocks.STONE);
        Mob villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, cell);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setTarget(villager);
        helper.assertTrue(zombie.getTarget() == villager, TARGET_NOT_TAKEN);
        helper.setBlock(cell, GooBlocks.QUANTUM_PHASE.get().defaultBlockState());
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(villager.getData(GooAttachments.OUT_OF_PHASE)
                    .standsAt(helper.getLevel().getGameTime()), NOT_PHASED);
            helper.assertTrue(zombie.getTarget() != villager, TARGET_KEPT);
            helper.succeed();
        });
    }

    /**
     * A zombie standing in a full Phase cell is out of phase; moved onto bare
     * stone, it is back in phase two ticks later, the phase expiring when it
     * steps off the goo.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     *
     * @param helper the gametest helper
     */
    public static void steppingOffEndsThePhase(GameTestHelper helper) {
        BlockPos cell = wallTheCell(helper);
        helper.setBlock(OFF_THE_GOO.below(), Blocks.STONE);
        helper.setBlock(cell, GooBlocks.QUANTUM_PHASE.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, cell.above());
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(phasedNow(helper, zombie), NOT_PHASED_IN_THE_GOO);
            Vec3 off = Vec3.atBottomCenterOf(helper.absolutePos(OFF_THE_GOO));
            zombie.teleportTo(off.x, off.y, off.z);
            helper.runAfterDelay(STEP_OFF_TICKS, () -> {
                helper.assertTrue(!phasedNow(helper, zombie), String.format(STILL_PHASED, STEP_OFF_TICKS,
                        zombie.getData(GooAttachments.OUT_OF_PHASE).expiresAt(), helper.getLevel().getGameTime()));
                helper.succeed();
            });
        });
    }

    private static boolean phasedNow(GameTestHelper helper, Mob mob) {
        return mob.getData(GooAttachments.OUT_OF_PHASE).standsAt(helper.getLevel().getGameTime());
    }

    private static BlockPos wallTheCell(GameTestHelper helper) {
        BlockPos cell = FLOOR.above();
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(cell, Blocks.AIR);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(cell.relative(side), Blocks.STONE);
        }
        return cell;
    }
}
