package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Blaze's Melt through the throw handler's real path: thrown at stone it
 * costs its price and the stone becomes a lava source, and thrown at dirt
 * it is refused before anything is paid and the dirt stands.
 * melt-blob-absorbs-into-lava
 */
@RegistersGameTests
public final class MeltTests {

    private static final String MELT_STONE_TO_LAVA = "melt_stone_to_lava";
    private static final String MELT_REFUSES_DIRT = "melt_refuses_dirt";
    private static final Identifier MELT = Identifier.parse("goo:blaze_melt");
    private static final int NO_ENTITY = -1;
    /** The block the throw strikes, on the floor in the middle of the bay. */
    private static final BlockPos STRUCK = new BlockPos(2, 1, 2);
    /** Ticks a refused throw is watched for, past any flight's landing. */
    private static final int WATCH_TICKS = 40;
    private static final String ABILITY_REQUIRED = "The ability registry must hold blaze_melt";
    private static final String SHOULD_CHARGE = "Melt at stone should cost its %d mB, spent %d";
    private static final String SHOULD_MELT = "Melt should turn the stone into a lava source, stands %s";
    private static final String SHOULD_NOT_CHARGE = "Melt at dirt should be refused unpaid, spent %d";
    private static final String SHOULD_SPARE_DIRT = "Melt at dirt should leave the dirt, stands %s";

    private MeltTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, MELT_STONE_TO_LAVA, MeltTests::meltStoneToLava);
        GooTestFunctions.registerTest(registrar, MELT_REFUSES_DIRT, MeltTests::meltRefusesDirt);
    }

    /**
     * Melt thrown at stone costs its price, and the stone becomes a lava source.
     *
     * @param helper the gametest helper
     */
    public static void meltStoneToLava(GameTestHelper helper) {
        AbilityDefinition melt = melt(helper);
        int spent = throwAt(helper, Blocks.STONE, melt);
        helper.assertTrue(spent == melt.cost(), String.format(SHOULD_CHARGE, melt.cost(), spent));
        helper.succeedWhen(() -> helper.assertTrue(helper.getBlockState(STRUCK).is(Blocks.LAVA)
                && helper.getBlockState(STRUCK).getFluidState().isSource(),
                String.format(SHOULD_MELT, helper.getBlockState(STRUCK))));
    }

    /**
     * Melt thrown at dirt is refused: nothing is paid and the dirt stands.
     *
     * @param helper the gametest helper
     */
    public static void meltRefusesDirt(GameTestHelper helper) {
        int spent = throwAt(helper, Blocks.DIRT, melt(helper));
        helper.assertTrue(spent == 0, String.format(SHOULD_NOT_CHARGE, spent));
        helper.runAfterDelay(WATCH_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(STRUCK).is(Blocks.DIRT),
                    String.format(SHOULD_SPARE_DIRT, helper.getBlockState(STRUCK)));
            helper.succeed();
        });
    }

    private static AbilityDefinition melt(GameTestHelper helper) {
        AbilityDefinition melt = AbilityRegistry.of(helper.getLevel()).getAbility(MELT);
        helper.assertTrue(melt != null, ABILITY_REQUIRED);
        return melt;
    }

    /**
     * Throws Melt from a blaze-holding player at the top of a block.
     *
     * @return the blaze goo the throw spent
     */
    private static int throwAt(GameTestHelper helper, Block block, AbilityDefinition melt) {
        helper.setBlock(STRUCK, block);
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.BLAZE);
        KnownRecipes.teachRequires(thrower, melt);
        int heldBefore = blazeHeld(thrower);
        GooThrowHandler.execute(thrower, new GooThrowPayload(GooTypes.id(GooTypes.BLAZE), NO_ENTITY,
                helper.absolutePos(STRUCK), Direction.UP.ordinal(), false, MELT.toString(),
                thrower.getEyePosition()));
        int spent = heldBefore - blazeHeld(thrower);
        helper.getLevel().getServer().getPlayerList().remove(thrower);
        return spent;
    }

    private static int blazeHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.BLAZE, 0);
    }
}
