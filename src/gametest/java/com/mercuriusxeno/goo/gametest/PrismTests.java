package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for Crystal's Prism through the real throw path: a throw at a
 * floor grows a prism on its top face from a thrower holding no quartz,
 * since Prism takes no item cost (operator ruling 2026-10-10).
 * decision prism-blob-becomes-a-milky-quartz-crystal
 */
public final class PrismTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final Identifier CRYSTAL_PRISM = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_prism");
    private static final int NO_TARGET_ENTITY = -1;

    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_prism";
    private static final String NOT_ON_THE_FACE = "The prism should face up off the floor, faced %s";

    private PrismTests() {
    }

    /**
     * A crystal_prism throw at a stone floor's top face, from a thrower
     * holding no nether quartz, grows goo:prism on that face.
     *
     * @param helper the gametest helper
     */
    public static void prismGrowsOnTheFace(GameTestHelper helper) {
        AbilityDefinition prism = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_PRISM);
        helper.assertTrue(prism != null, ABILITY_REQUIRED);
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.CRYSTAL);
        KnownRecipes.teachRequires(player, prism);

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.CRYSTAL), NO_TARGET_ENTITY,
                helper.absolutePos(FLOOR_POS), Direction.UP.ordinal(), false, CRYSTAL_PRISM.toString(),
                player.getEyePosition()));

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(GooBlocks.PRISM.get(), FLOOR_POS.above());
            Direction facing = helper.getBlockState(FLOOR_POS.above()).getValue(PrismBlock.FACING);
            helper.assertTrue(facing == Direction.UP, String.format(NOT_ON_THE_FACE, facing));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }
}
