package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Blaze's Ignite: its blob lights a fire where it lands and breaks nothing.
 * ignite-fire-and-explosive-combo
 */
@RegistersGameTests
public final class IgniteTests {

    private static final String IGNITE_LIGHTS_A_FIRE = "ignite_lights_a_fire";
    private static final Identifier IGNITE = Identifier.parse("goo:blaze_ignite");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    /** The floor block in the middle of the bay the blobs land on. */
    private static final BlockPos FLOOR = new BlockPos(8, FLOOR_TOP, 8);
    /** The cell over the floor block, where a blob landing on the floor lands. */
    private static final BlockPos OVER_THE_FLOOR = FLOOR.above();
    /** The tick after the landing, once the explosion's fires stand. */
    private static final int SETTLE_TICKS = 1;
    private static final String ABILITY_REQUIRED = "The ability registry must hold %s";
    private static final String SHOULD_LIGHT = "Ignite should light a fire where it lands, stands %s";

    private IgniteTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, IGNITE_LIGHTS_A_FIRE, IgniteTests::igniteLightsAFire);
    }

    /**
     * Ignite landing on the floor lights a fire in the cell over it.
     *
     * @param helper the gametest helper
     */
    public static void igniteLightsAFire(GameTestHelper helper) {
        stoneFloor(helper);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(FLOOR), GooTypes.BLAZE, Direction.UP,
                ability(helper, IGNITE));
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(OVER_THE_FLOOR).getBlock() instanceof BaseFireBlock,
                    String.format(SHOULD_LIGHT, helper.getBlockState(OVER_THE_FLOOR)));
            helper.succeed();
        });
    }

    private static void stoneFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, FLOOR_TOP, FLOOR_SPAN - 1)
                .forEach(cell -> helper.setBlock(cell, Blocks.STONE));
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }
}
