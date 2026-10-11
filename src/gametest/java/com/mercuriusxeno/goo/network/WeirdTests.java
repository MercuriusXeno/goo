package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gametests for Weird's abilities, each cast through the real landing path.
 * weird-bounces-and-softens-harm
 */
public final class WeirdTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String MAGMA = "goo:weird_magma";
    private static final String TURNS_LAVA_TO_MAGMA = "weird_turns_lava_to_magma";
    private static final BlockPos POOL_FLOOR = new BlockPos(2, 0, 2);
    private static final int POOL_REACH = 1;
    private static final String LAVA_LEFT = "Weird landing in lava should leave magma where the lava stood at ";

    private WeirdTests() {
    }

    /**
     * Registers Weird's gametests under their function names.
     *
     * @param reg takes a function name and its test
     */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> reg) {
        reg.accept(TURNS_LAVA_TO_MAGMA, WeirdTests::weirdTurnsLavaToMagma);
    }

    /**
     * A Weird blob thrown through a three-by-three lava pool strikes its
     * floor, lands in the lava above it, and leaves magma blocks where the
     * pool's lava stood.
     *
     * @param helper the gametest helper
     */
    public static void weirdTurnsLavaToMagma(GameTestHelper helper) {
        for (BlockPos floor : poolFloor()) {
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(), Blocks.LAVA);
        }
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, MAGMA));
        for (BlockPos floor : poolFloor()) {
            helper.assertTrue(helper.getBlockState(floor.above()).is(Blocks.MAGMA_BLOCK), LAVA_LEFT + floor.above());
        }
        helper.succeed();
    }

    private static Iterable<BlockPos> poolFloor() {
        return BlockPos.betweenClosed(POOL_FLOOR.offset(-POOL_REACH, 0, -POOL_REACH),
                POOL_FLOOR.offset(POOL_REACH, 0, POOL_REACH));
    }
}
