package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.network.BlobEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for a blob landing on a block through
 * {@link BlobEffectScheduler#applyEffect}: a throw naming no ability lands
 * nothing, and a throw naming an ability places that ability's marker
 * (decision no-throw-without-ability). They sit in the scheduler's package
 * because the scheduler is package-private.
 */
public final class BlockLandingTests {

    /** The block the blob strikes. */
    private static final BlockPos WALL_POS = new BlockPos(1, 1, 1);
    /** The air on the struck face, where a marker would stand. */
    private static final BlockPos FACE_POS = WALL_POS.south();
    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String NO_ABILITY = "";
    private static final String BLAZE_TUNNEL = "goo:blaze_tunnel";
    private static final String MARKER_WRONG_ABILITY = "The landed marker does not carry the thrown ability";

    private BlockLandingTests() {
    }

    /**
     * Lands one blaze blob on the stone wall's south face.
     *
     * @param helper    the gametest helper
     * @param abilityId the ability the throw names
     */
    private static void landOnWall(GameTestHelper helper, String abilityId) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        BlobEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.BLAZE,
                NO_ENTITY, helper.absolutePos(WALL_POS), Direction.SOUTH, abilityId));
    }

    /**
     * A block throw naming no ability leaves no marker on the struck face.
     *
     * @param helper the gametest helper
     */
    public static void noAbilityLandsNothing(GameTestHelper helper) {
        landOnWall(helper, NO_ABILITY);
        helper.assertBlockNotPresent(GooBlocks.CHAIN_MARKER.get(), FACE_POS);
        helper.succeed();
    }

    /**
     * A block throw naming an ability stands a marker carrying that ability
     * on the struck face.
     *
     * @param helper the gametest helper
     */
    public static void abilityLandsItsMarker(GameTestHelper helper) {
        landOnWall(helper, BLAZE_TUNNEL);
        ChainMarkerBlockEntity marker = helper.getBlockEntity(FACE_POS, ChainMarkerBlockEntity.class);
        helper.assertTrue(BLAZE_TUNNEL.equals(marker.getAbilityId()), MARKER_WRONG_ABILITY);
        helper.succeed();
    }
}
