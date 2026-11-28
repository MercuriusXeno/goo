package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.SpursStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Osseous's Spurs through the real shot its cell makes: a zombie walking across a full Spurs cell is struck by a
 * bone spike the cell shoots at it, and a Spurs cell with nothing walking over it shoots nothing.
 * spurs-shoots-bone-spikes-at-walkers
 */
@RegistersGameTests
public final class SpursTests {

    private static final String SPURS_SHOOT_A_WALKER = "spurs_shoot_a_bone_spike_at_a_zombie_walking_across_them";
    private static final String SPURS_SHOOT_NOTHING = "spurs_shoot_nothing_from_a_cell_nothing_walks_over";
    private static final Identifier SPURS = Identifier.parse("goo:osseous_spurs");
    private static final String ABILITY_REQUIRED = "The ability registry must hold osseous_spurs";
    /** The lane's westmost stone, where the zombie starts; the lane runs east across the bay. */
    private static final BlockPos LANE = new BlockPos(0, 1, 1);
    /** The lane's eastmost offset from its start, the bay's far wall short of one block. */
    private static final int LANE_EAST = 5;
    /** The Spurs cell's offset east of the lane's start, sunk in the lane so its top lies flush with the stone. */
    private static final int CELL_EAST = 2;
    /** The empty cell, sunk in stone in the middle of the bay. */
    private static final BlockPos EMPTY_CELL = new BlockPos(2, 1, 3);
    /** Ticks the empty cell is watched, several cooldowns. */
    private static final int EMPTY_TICKS = 60;
    private static final String NO_SHOT = "No bone spike flew from the Spurs cell the zombie crossed";
    private static final String NOT_CROSSED = "The zombie has not crossed the Spurs cell yet";
    private static final String NOT_STRUCK = "A zombie that crossed a full Spurs cell should have lost at least %.2f"
            + " health: %.2f at the start, %.2f now";
    private static final String SHOT_AT_NOTHING = "A Spurs cell with nothing walking over it shot a bone spike";

    private SpursTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPURS_SHOOT_A_WALKER, SpursTests::spursShootABoneSpikeAtAWalker);
        GooTestFunctions.registerTest(registrar, SPURS_SHOOT_NOTHING, SpursTests::spursShootNothingAtNothing);
    }

    /**
     * A zombie walks a lane of stone across a full Spurs cell sunk flush in it; a bone spike flies from the cell,
     * and once the zombie has crossed its health reads lower than it started by at least the strike's damage. The
     * zombie wears no armor, so the strike lands whole, and a carved pumpkin, so daylight does not burn it.
     *
     * @param helper the gametest helper
     */
    public static void spursShootABoneSpikeAtAWalker(GameTestHelper helper) {
        float strikeDamage = strikeDamageOf(helper);
        for (int east = 0; east <= LANE_EAST; east++) {
            BlockPos stone = LANE.east(east);
            helper.setBlock(stone.below(), Blocks.BEDROCK);
            helper.setBlock(stone, Blocks.STONE);
            helper.setBlock(stone.above(), Blocks.AIR);
        }
        BlockPos cell = LANE.east(CELL_EAST);
        helper.setBlock(cell, GooBlocks.OSSEOUS_SPURS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        AtomicBoolean shot = watchForShots(helper);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, LANE.above());
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        float started = zombie.getHealth();
        helper.walkTo(zombie, LANE.east(LANE_EAST).above(), 1f);
        double crossed = helper.absolutePos(cell).getX() + 1.0;
        helper.succeedWhen(() -> {
            helper.assertTrue(shot.get(), NO_SHOT);
            helper.assertTrue(zombie.getX() > crossed, NOT_CROSSED);
            helper.assertTrue(zombie.getHealth() <= started - strikeDamage,
                    String.format(NOT_STRUCK, strikeDamage, started, zombie.getHealth()));
        });
    }

    /**
     * A full Spurs cell sunk in stone with nothing on it is watched for several cooldowns, and no bone spike flies.
     *
     * @param helper the gametest helper
     */
    public static void spursShootNothingAtNothing(GameTestHelper helper) {
        strikeDamageOf(helper);
        helper.setBlock(EMPTY_CELL.below(), Blocks.BEDROCK);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(EMPTY_CELL.relative(side), Blocks.STONE);
        }
        helper.setBlock(EMPTY_CELL, GooBlocks.OSSEOUS_SPURS.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        AtomicBoolean shot = watchForShots(helper);
        helper.runAfterDelay(EMPTY_TICKS, () -> {
            helper.assertFalse(shot.get(), SHOT_AT_NOTHING);
            helper.succeed();
        });
    }

    /**
     * Watches the bone spikes flying in the test's bay each tick of the test, so a spike another test shoots
     * meanwhile is no shot of this one.
     *
     * @param helper the gametest helper
     * @return set once a bone spike has flown
     */
    private static AtomicBoolean watchForShots(GameTestHelper helper) {
        AtomicBoolean shot = new AtomicBoolean();
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        AABB bay = helper.getBounds().inflate(1);
        helper.onEachTick(() -> {
            if (state.boneSpikes().fliesWithin(helper.getLevel(), bay)) {
                shot.set(true);
            }
        });
        return shot;
    }

    /**
     * The damage the spurs step's strike deals, its first damage step's amount.
     *
     * @param helper the gametest helper
     * @return the strike's damage
     */
    private static float strikeDamageOf(GameTestHelper helper) {
        AbilityDefinition spurs = AbilityRegistry.of(helper.getLevel()).getAbility(SPURS);
        helper.assertTrue(spurs != null, ABILITY_REQUIRED);
        SpursStep step = SpursStep.in(spurs.behaviors()).orElseThrow();
        DamageStep damage = step.strike().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow();
        return damage.amount().evaluateFloat(Variables.NONE);
    }
}
