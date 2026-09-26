package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * Gametests for the fuse a chain marker keeps through a declared throw
 * and a stack (decision diagnose-then-fix-fuse-and-cost).
 */
public final class ChainFuseTests {

    private static final BlockPos MARKER_POS = new BlockPos(3, 1, 3);
    private static final Identifier PROXIMITY_MINE = Identifier.parse("goo:unstable_proximity_mine");
    /** The proximity mine JSON's fuse: armed until a trigger, never counting down. */
    private static final int MINE_FUSE = -1;
    /** Ticks the test waits after the second blob, past the twenty-tick unstable fuse the Java table carried. */
    private static final int WAIT_TICKS = 40;
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String FUSE_OVERWRITTEN = "The mine's fuse read %d, not the JSON's %d";
    private static final String MINE_DETONATED = "The mine detonated before any trigger";

    private ChainFuseTests() {}

    /**
     * A proximity mine takes a declared throw and the blob it lands, and
     * after forty ticks still stands with the JSON's fuse of -1.
     *
     * @param helper the gametest helper
     */
    public static void mineKeepsJsonFuse(GameTestHelper helper) {
        AbilityDefinition mine = AbilityRegistry.getAbility(PROXIMITY_MINE);
        helper.assertTrue(mine != null, ABILITIES_REQUIRED);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        marker.initChainFromAbility(GooTypes.UNSTABLE, Direction.UP, mine);
        marker.stallFuse();
        marker.tryStack();
        helper.runAfterDelay(WAIT_TICKS, () -> {
            helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), MARKER_POS);
            ChainMarkerBlockEntity standing = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
            helper.assertTrue(standing.getFuseRemaining() == MINE_FUSE,
                    FUSE_OVERWRITTEN.formatted(standing.getFuseRemaining(), MINE_FUSE));
            helper.assertTrue(standing.getBehavior() == null, MINE_DETONATED);
            helper.succeed();
        });
    }
}
