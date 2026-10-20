package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * A vat stack settles goo bottom-up whichever vat it is poured into: a vat placed on
 * another redistributes on its own fluid change, as the one below does.
 */
@RegistersGameTests
public final class VatStackTests {
    private static final String VAT_STACK_SETTLES_GOO_POURED_INTO_THE_TOP = "vat_stack_settles_goo_poured_into_the_top";

    private static final BlockPos BOTTOM = new BlockPos(1, 1, 1);
    private static final int VOLUME = 200;

    private VatStackTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, VAT_STACK_SETTLES_GOO_POURED_INTO_THE_TOP, VatStackTests::settlesGooPouredIntoTheTop);
    }

    /**
     * Rock poured into the top of two stacked vats settles into the bottom vat.
     *
     * @param helper the gametest helper
     */
    public static void settlesGooPouredIntoTheTop(GameTestHelper helper) {
        helper.setBlock(BOTTOM, GooBlocks.VAT.get());
        helper.setBlock(BOTTOM.above(), GooBlocks.VAT.get());
        VatBlockEntity bottom = helper.getBlockEntity(BOTTOM, VatBlockEntity.class);
        VatBlockEntity top = helper.getBlockEntity(BOTTOM.above(), VatBlockEntity.class);
        top.insertGoo(GooTypes.ROCK, VOLUME);
        helper.assertTrue(bottom.getContents().getVolume(GooTypes.ROCK) == VOLUME,
                "Rock poured into the top vat should settle into the bottom vat: bottom=" + bottom.getContents()
                        + " top=" + top.getContents() + " topState=" + top.getBlockState());
        helper.succeed();
    }
}
