package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.block.ability.AbilityBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for EffectBlockPlacement on the ability path: an ability goo
 * lands through ChainPlacementRules and stands or stacks its ability block.
 */
@RegistersGameTests
public final class PlacementTests {
    private static final String PL_ABILITY_WATERLOG = "pl_ability_waterlog";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PL_ABILITY_WATERLOG, PlacementTests::abilityWaterlogsInWater);
    }


    private static final BlockPos WALL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos AIR_POS = new BlockPos(1, 1, 2);
    private static final String CRYSTAL_SHATTER = "goo:crystal_shatter";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";

    private PlacementTests() {}

    /**
     * Throws one ability goo at a block face through the ability placement
     * path.
     *
     * @param helper    the gametest helper
     * @param hit       the struck block, relative
     * @param face      the struck face
     * @param type      the goo type
     * @param abilityId the ability the goo names
     */
    private static void throwAbility(GameTestHelper helper, BlockPos hit, Direction face,
                                     ResourceKey<GooTypeDefinition> type, String abilityId) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(hit), type, face, ability);
    }

    /**
     * An ability goo striking stone behind a water source places its
     * marker waterlogged in the water.
     *
     * @param helper the gametest helper
     */
    public static void abilityWaterlogsInWater(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        helper.setBlock(AIR_POS, Blocks.WATER);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.CRYSTAL, CRYSTAL_SHATTER);
        helper.assertBlockPresent(GooBlocks.ABILITY_BLOCK.get(), AIR_POS);
        helper.assertBlockProperty(AIR_POS, AbilityBlock.WATERLOGGED, true);
        helper.succeed();
    }

}
