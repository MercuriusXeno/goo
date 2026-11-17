package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.SpikesStep;
import com.mercuriusxeno.goo.ability.program.Variables;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Metal's Spikes through the real landing its stream's drips take: a drip
 * striking stone's top lays a one-layer Spikes cell there; and through the
 * real strike its cell makes: a zombie walking across a full Spikes cell is
 * impaled for the strike's damage.
 * metal-world-goo-is-named-spikes
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * urchin-rests-bristled-and-spears
 */
@RegistersGameTests
public final class SpikesTests {

    private static final String SPIKES_DRIP_LAYS_A_CELL = "spikes_drip_lays_a_metal_cell_of_one_layer";
    private static final Identifier SPIKES = Identifier.parse("goo:metal_spikes");
    /** The stone the drip strikes, in the middle of the bay; the cell stands over it. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    private static final String ABILITY_REQUIRED = "The ability registry must hold metal_spikes";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Spikes, stands %s";
    private static final String SPIKES_IMPALE_A_WALKER = "spikes_impale_a_zombie_walking_across_them";
    /** The lane's westmost stone, where the zombie starts; the lane runs east across the bay. */
    private static final BlockPos LANE = new BlockPos(0, 1, 1);
    /** The lane's eastmost offset from its start, the bay's far wall short of one block. */
    private static final int LANE_EAST = 5;
    /** The Spikes cell's offset east of the lane's start, sunk in the lane so its top lies flush with the stone. */
    private static final int CELL_EAST = 2;
    private static final String NOT_CROSSED = "The zombie has not crossed the Spikes cell yet";
    private static final String NOT_IMPALED = "A zombie that crossed a full Spikes cell should have lost at least %.2f"
            + " health: %.2f at the start, %.2f now";

    private SpikesTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPIKES_DRIP_LAYS_A_CELL, SpikesTests::spikesDripLaysAMetalCellOfOneLayer);
        GooTestFunctions.registerTest(registrar, SPIKES_IMPALE_A_WALKER, SpikesTests::spikesImpaleAZombieWalkingAcrossThem);
    }

    /**
     * One Spikes drip striking a stone's top lays a Spikes cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void spikesDripLaysAMetalCellOfOneLayer(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE.defaultBlockState());
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(SPIKES);
        helper.assertTrue(spikes != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.METAL);
        KnownRecipes.teachRequires(caster, spikes);
        LandedDrips.landOn(helper, caster, spikes, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState cell = helper.getBlockState(FLOOR.above());
        boolean oneLayer = cell.is(GooBlocks.METAL_SPIKES.get()) && cell.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, cell));
        helper.succeed();
    }

    /**
     * A zombie walks a lane of stone across a full Spikes cell sunk flush in
     * it; once it has crossed, its health reads lower than it started by at
     * least the damage the spikes step's strike names. The zombie wears no
     * armor, so the strike lands whole, and a carved pumpkin, so daylight
     * does not burn it.
     *
     * @param helper the gametest helper
     */
    public static void spikesImpaleAZombieWalkingAcrossThem(GameTestHelper helper) {
        AbilityDefinition spikes = AbilityRegistry.of(helper.getLevel()).getAbility(SPIKES);
        helper.assertTrue(spikes != null, ABILITY_REQUIRED);
        float strikeDamage = strikeDamageOf(spikes);
        for (int east = 0; east <= LANE_EAST; east++) {
            BlockPos stone = LANE.east(east);
            helper.setBlock(stone.below(), Blocks.BEDROCK);
            helper.setBlock(stone, Blocks.STONE);
            helper.setBlock(stone.above(), Blocks.AIR);
        }
        BlockPos cell = LANE.east(CELL_EAST);
        helper.setBlock(cell, GooBlocks.METAL_SPIKES.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, VolumeCellBlock.MAX_LAYERS));
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, LANE.above());
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        float started = zombie.getHealth();
        helper.walkTo(zombie, LANE.east(LANE_EAST).above(), 1f);
        double crossed = helper.absolutePos(cell).getX() + 1.0;
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getX() > crossed, NOT_CROSSED);
            helper.assertTrue(zombie.getHealth() <= started - strikeDamage,
                    String.format(NOT_IMPALED, strikeDamage, started, zombie.getHealth()));
        });
    }

    /**
     * The damage the spikes step's strike deals, its first damage step's amount.
     *
     * @param spikes the Spikes ability
     * @return the strike's damage
     */
    private static float strikeDamageOf(AbilityDefinition spikes) {
        SpikesStep step = SpikesStep.in(spikes.behaviors()).orElseThrow();
        DamageStep damage = step.strike().stream().filter(DamageStep.class::isInstance).map(DamageStep.class::cast)
                .findFirst().orElseThrow();
        return damage.amount().evaluateFloat(Variables.NONE);
    }
}
