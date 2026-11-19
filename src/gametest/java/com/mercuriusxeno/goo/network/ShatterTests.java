package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityRegistrySource;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Crystal's Shatter through the real landing its stream's drips take: a
 * drip striking stone's top lays a one-layer crystal cell there; and through
 * the real cut its shards make: a mob walking through the shards over a cell
 * is cut, and a cut depleting a cell's last layer leaves air.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * each-shatter-cut-has-a-chance-to-deplete-a-layer
 */
@RegistersGameTests
public final class ShatterTests {

    private static final String SHATTER_DRIP_LAYS_A_CELL = "shatter_drip_lays_a_cell_on_stone";
    private static final Identifier SHATTER = Identifier.parse("goo:crystal_shatter");
    /** The floor block a drip strikes, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block, where the drip's layer lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    private static final String ABILITY_REQUIRED = "The ability registry must hold crystal_shatter";
    private static final String LAYERS_WRONG = "The cell over the stone should hold one layer of Shatter, stands %s";
    private static final String SHATTER_CUTS_A_WALKER = "shatter_cuts_a_walking_mob";
    private static final String SHATTER_DEPLETES_TO_AIR = "shatter_cut_depletes_a_last_layer_to_air";
    private static final String WALKER_UNCUT = "The pig walking through the shards over the Shatter cell is unhurt";
    private static final String CELL_STANDS = "A cut at a deplete chance of 1 should leave air where the one layer"
            + " cell stood, stands %s";
    private static final String MOVER_UNCUT = "The pig stepping on the Shatter cell should be cut: %.2f of %.2f";
    private static final String SHATTER_STEP = "shatter";
    private static final String INTERVAL = "interval";
    private static final String DEPLETE_CHANCE = "deplete_chance";
    /** An interval every tick's game time divides, so the cut runs in the tick the test steps. */
    private static final int EVERY_TICK = 1;
    /** A deplete chance every roll falls under. */
    private static final double SURE_DEPLETE = 1.0;
    /** Health enough for a pig to outlast every cut the test waits through. */
    private static final float HARDY_HEALTH = 1024f;
    /** How fast the walking pig is pushed each tick, in blocks a tick. */
    private static final double WALK_SPEED = 0.15;
    /** Ticks per back-and-forth of the walking pig, so it stays over the cell. */
    private static final int WALK_PERIOD = 2;

    private ShatterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SHATTER_DRIP_LAYS_A_CELL, ShatterTests::shatterDripLaysACellOnStone);
        GooTestFunctions.registerTest(registrar, SHATTER_CUTS_A_WALKER, ShatterTests::shatterCutsAWalkingMob);
        GooTestFunctions.registerTest(registrar, SHATTER_DEPLETES_TO_AIR, ShatterTests::shatterCutDepletesALastLayerToAir);
    }

    /**
     * One Shatter drip striking a stone block's top lays a crystal cell of one layer over it.
     *
     * @param helper the gametest helper
     */
    public static void shatterDripLaysACellOnStone(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, Blocks.AIR);
        AbilityDefinition shatter = AbilityRegistry.of(helper.getLevel()).getAbility(SHATTER);
        helper.assertTrue(shatter != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(caster, shatter);
        LandedDrips.landOn(helper, caster, shatter, FLOOR, 1);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        BlockState state = helper.getBlockState(OVER_THE_FLOOR);
        boolean oneLayer = state.is(GooBlocks.CRYSTAL_SHATTER.get()) && state.getValue(VolumeCellBlock.LAYERS) == 1;
        helper.assertTrue(oneLayer, String.format(LAYERS_WRONG, state));
        helper.succeed();
    }

    /**
     * A pig walking back and forth through the shards over a full Shatter cell is cut.
     *
     * @param helper the gametest helper
     */
    public static void shatterCutsAWalkingMob(GameTestHelper helper) {
        layCell(helper, VolumeCellBlock.MAX_LAYERS);
        Pig walker = walkingPig(helper);
        float started = walker.getHealth();
        helper.succeedWhen(() -> helper.assertTrue(walker.getHealth() < started, WALKER_UNCUT));
    }

    /**
     * A pig moving over a one layer Shatter cell steps on it once while the
     * server holds Shatter cutting on every tick at a deplete chance of 1,
     * and in that tick the pig reads hurt and air stands where the cell stood.
     * The cut runs through the cell's own step, and the held abilities are
     * restored within the tick, so no other test meets them.
     * shatter-last-layer-test-is-made-fully-deterministic
     *
     * @param helper the gametest helper
     */
    public static void shatterCutDepletesALastLayerToAir(GameTestHelper helper) {
        layCell(helper, 1);
        Pig mover = helper.spawnWithNoFreeWill(EntityType.PIG, OVER_THE_FLOOR);
        mover.setDeltaMovement(WALK_SPEED, 0, 0);
        BlockPos cellPos = helper.absolutePos(OVER_THE_FLOOR);
        BlockState cellState = helper.getLevel().getBlockState(cellPos);
        MinecraftServer server = helper.getLevel().getServer();
        AbilityRegistrySource held = (AbilityRegistrySource) server.getServerResources().managers();
        AbilityRegistry shipped = held.abilityRegistry();
        held.holdAbilityRegistry(HeldAbilitySteps.withStepFields(shipped, SHATTER, SHATTER_STEP,
                Map.of(INTERVAL, EVERY_TICK, DEPLETE_CHANCE, SURE_DEPLETE)));
        try {
            cellState.getBlock().stepOn(helper.getLevel(), cellPos, cellState, mover);
        } finally {
            held.holdAbilityRegistry(shipped);
        }
        BlockState cell = helper.getBlockState(OVER_THE_FLOOR);
        helper.assertTrue(mover.getHealth() < mover.getMaxHealth(),
                String.format(MOVER_UNCUT, mover.getHealth(), mover.getMaxHealth()));
        helper.assertTrue(cell.isAir(), String.format(CELL_STANDS, cell));
        helper.succeed();
    }

    private static void layCell(GameTestHelper helper, int layers) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(OVER_THE_FLOOR, GooBlocks.CRYSTAL_SHATTER.get().defaultBlockState()
                .setValue(VolumeCellBlock.LAYERS, layers));
    }

    /**
     * Stands a pig too hardy to die on the cell, pushed back and forth every
     * tick so it moves through the shards while it stays over the cell.
     *
     * @param helper the gametest helper
     * @return the pig
     */
    private static Pig walkingPig(GameTestHelper helper) {
        // spawned over the cell, the pig drops onto the goo whatever its height
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, OVER_THE_FLOOR.above());
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HARDY_HEALTH);
        pig.setHealth(HARDY_HEALTH);
        helper.onEachTick(() -> pig.setDeltaMovement(
                helper.getTick() % WALK_PERIOD == 0 ? WALK_SPEED : -WALK_SPEED, pig.getDeltaMovement().y(), 0));
        return pig;
    }
}
