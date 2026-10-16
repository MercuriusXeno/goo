package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.petrify.BlockExposures;
import com.mercuriusxeno.goo.ability.program.HeaterStep;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Blaze's heater prism: Flames channeled at a bare prism for the hold makes
 * it a heater, a heater flames and burns the zombie beside it, and a heater
 * with no mob in its reach flames nothing.
 * heater-prism-flames-nearby-creatures
 */
@RegistersGameTests
public final class HeaterTests {

    private static final String HEATER_FROM_FLAMES = "heater_from_flames";
    private static final String HEATER_FLAMES_THE_ZOMBIE = "heater_flames_the_zombie";
    private static final String HEATER_IDLE_WHEN_ALONE = "heater_idle_when_alone";
    /** Flames, whose on_prism reaction is the heater. */
    private static final Identifier FLAMES = Identifier.parse("goo:blaze_flames");
    private static final BlockPos PRISM_POS = new BlockPos(0, 1, 0);
    /** Three blocks south of the prism, where the player holds Flames on it. */
    private static final BlockPos STAND_POS = new BlockPos(0, 1, 3);
    /** Two blocks east of the prism, inside the heater's six. */
    private static final BlockPos NEAR_POS = new BlockPos(2, 1, 0);
    /** The bay's far corner, past the heater's six. */
    private static final BlockPos FAR_POS = new BlockPos(5, 1, 5);
    /** Ticks a heater runs before its watch is read: its fire grown out and several burns landed, the zombie alive. */
    private static final int HEAT_TICKS = 12;
    private static final String ABILITY_REQUIRED = "The ability registry must hold blaze flames";
    private static final String SHOULD_BE_HEATER = "Flames held on the prism should make it a heater, stands %s";
    private static final String SHOULD_WARM = "Part way through the hold the prism should warm toward ember, stands %.3f tint %x";
    private static final String SHOULD_FLAME = "The heater should be flaming the zombie beside it";
    private static final String SHOULD_HURT = "The zombie beside the heater should lose health, lost %.2f";
    private static final String SHOULD_BURN = "The zombie beside the heater should be alight";
    private static final String SHOULD_IDLE = "A heater with no mob in its reach should flame nothing";
    private static final String SHOULD_SPARE = "The zombie past the heater's reach should keep its health, lost %.2f";

    private HeaterTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, HEATER_FROM_FLAMES, HeaterTests::heaterFromFlames);
        GooTestFunctions.registerTest(registrar, HEATER_FLAMES_THE_ZOMBIE, HeaterTests::heaterFlamesTheZombie);
        GooTestFunctions.registerTest(registrar, HEATER_IDLE_WHEN_ALONE, HeaterTests::heaterIdleWhenAlone);
    }

    /**
     * Flames held on a bare prism for its hold converts it into the heater.
     *
     * @param helper the gametest helper
     */
    public static void heaterFromFlames(GameTestHelper helper) {
        barePrism(helper);
        int converted = PrismChanneler.hold(helper, FLAMES) + 1;
        ServerPlayer holder = PrismChanneler.holdOn(helper, GooTypes.BLAZE, FLAMES, PRISM_POS, STAND_POS,
                converted - 1);
        // prism-conversion-creeps-over-the-hold: part way through the hold the prism warms toward ember
        helper.runAfterDelay(converted / 2, () -> {
            BlockExposures exposures = GooServerState.of(helper.getLevel().getServer()).blockExposures();
            float share = exposures.shareAt(helper.getLevel(), helper.absolutePos(PRISM_POS));
            int tint = exposures.tintAt(helper.getLevel(), helper.absolutePos(PRISM_POS));
            helper.assertTrue(share > 0f && share < 1f && tint == HeaterStep.EMBER,
                    String.format(SHOULD_WARM, share, tint));
        });
        helper.runAfterDelay(converted, () -> {
            helper.getLevel().getServer().getPlayerList().remove(holder);
            String combo = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class).getCombo();
            helper.assertTrue(FLAMES.toString().equals(combo), String.format(SHOULD_BE_HEATER, combo));
            helper.succeed();
        });
    }

    /**
     * A heater flames the zombie two blocks off: it burns and loses health.
     *
     * @param helper the gametest helper
     */
    public static void heaterFlamesTheZombie(GameTestHelper helper) {
        heaterPrism(helper);
        Mob zombie = helmetedZombie(helper, NEAR_POS);
        float healthBefore = zombie.getHealth();
        helper.runAfterDelay(HEAT_TICKS, () -> {
            helper.assertTrue(HeaterStep.flaming(helper.getLevel(), helper.absolutePos(PRISM_POS)), SHOULD_FLAME);
            float lost = healthBefore - zombie.getHealth();
            helper.assertTrue(lost > 0, String.format(SHOULD_HURT, lost));
            helper.assertTrue(zombie.isOnFire(), SHOULD_BURN);
            helper.succeed();
        });
    }

    /**
     * A heater with its only zombie past its reach flames nothing and spares it.
     *
     * @param helper the gametest helper
     */
    public static void heaterIdleWhenAlone(GameTestHelper helper) {
        heaterPrism(helper);
        Mob zombie = helmetedZombie(helper, FAR_POS);
        float healthBefore = zombie.getHealth();
        helper.runAfterDelay(HEAT_TICKS, () -> {
            helper.assertFalse(HeaterStep.flaming(helper.getLevel(), helper.absolutePos(PRISM_POS)), SHOULD_IDLE);
            float lost = healthBefore - zombie.getHealth();
            helper.assertTrue(lost == 0, String.format(SHOULD_SPARE, lost));
            helper.succeed();
        });
    }

    private static void barePrism(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
    }

    private static void heaterPrism(GameTestHelper helper) {
        barePrism(helper);
        AbilityDefinition flames = AbilityRegistry.of(helper.getLevel()).getAbility(FLAMES);
        helper.assertTrue(flames != null, ABILITY_REQUIRED);
        helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class).runCombo(GooTypes.BLAZE, FLAMES.toString(),
                PrismCombos.comboSteps(flames));
    }

    private static Mob helmetedZombie(GameTestHelper helper, BlockPos at) {
        // the bay has no floor there, and a falling zombie drops out of the heater's sight
        helper.setBlock(at.below(), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, at);
        // a helmet keeps daylight from setting it alight on its own
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }
}
