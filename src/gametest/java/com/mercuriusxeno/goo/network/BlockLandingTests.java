package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for a goo landing on a block through
 * {@link GooEffectScheduler#applyEffect}: a throw naming no ability lands
 * nothing, and a throw naming an ability places that ability's marker
 * (decision no-throw-without-ability). They sit in the scheduler's package
 * because the scheduler is package-private.
 */
public final class BlockLandingTests {

    /** The block the goo strikes. */
    private static final BlockPos WALL_POS = new BlockPos(1, 1, 1);
    /** The air on the struck face, where a marker would stand. */
    private static final BlockPos FACE_POS = WALL_POS.south();
    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String NO_ABILITY = "";
    private static final String CRYSTAL_CLOUD = "goo:crystal_cloud";
    private static final String BLAST = "goo:unstable_explode";
    private static final String BLAST_LEFT_A_BLOCK = "Blast left something standing at the face it landed on";
    private static final String MARKER_WRONG_ABILITY = "The landed marker does not carry the thrown ability";

    private BlockLandingTests() {
    }

    /**
     * Lands one crystal goo on the stone wall's south face.
     *
     * @param helper    the gametest helper
     * @param abilityId the ability the throw names
     */
    private static void landOnWall(GameTestHelper helper, String abilityId) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.CRYSTAL,
                NO_ENTITY, helper.absolutePos(WALL_POS), Direction.SOUTH, abilityId));
    }

    /**
     * A block throw naming no ability leaves no marker on the struck face.
     *
     * @param helper the gametest helper
     */
    public static void noAbilityLandsNothing(GameTestHelper helper) {
        landOnWall(helper, NO_ABILITY);
        helper.assertBlockNotPresent(GooBlocks.ABILITY_BLOCK.get(), FACE_POS);
        helper.succeed();
    }

    /**
     * A block throw naming an ability stands a marker carrying that ability
     * on the struck face.
     *
     * @param helper the gametest helper
     */
    public static void abilityLandsItsMarker(GameTestHelper helper) {
        landOnWall(helper, CRYSTAL_CLOUD);
        AbilityBlockEntity marker = helper.getBlockEntity(FACE_POS, AbilityBlockEntity.class);
        helper.assertTrue(CRYSTAL_CLOUD.equals(marker.getAbilityId()), MARKER_WRONG_ABILITY);
        helper.succeed();
    }

    /**
     * A blast lands, explodes the tick it splats and leaves only air at the
     * face it struck: a program that ends that tick places nothing of its
     * own (decision lingering-abilities-place-their-own-thing).
     *
     * @param helper the gametest helper
     */
    public static void blastLandsNoBlock(GameTestHelper helper) {
        landOnWall(helper, BLAST);
        helper.assertTrue(helper.getBlockState(FACE_POS).isAir(), BLAST_LEFT_A_BLOCK);
        helper.succeed();
    }
}
