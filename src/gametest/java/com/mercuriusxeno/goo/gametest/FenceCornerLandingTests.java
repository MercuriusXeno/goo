package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * A blob landing inside an L-shaped fence corner, on the side of one arm that
 * faces the other arm, still lands: Blast explodes there and the corner is
 * gone. The struck face looks onto the other arm's fence block, so the cell
 * beyond the face takes no blob.
 */
@RegistersGameTests
public final class FenceCornerLandingTests {
    private static final String BLAST_INSIDE_A_FENCE_CORNER = "blast_lands_inside_a_fence_corner";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLAST_INSIDE_A_FENCE_CORNER, FenceCornerLandingTests::blastLandsInsideAFenceCorner);
    }


    /** The gametests' own thrown blast, today's Blast before it was drag-sized, for the landing machinery. */
    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "test_blast");
    /** The corner post, an arm running east and an arm running south. */
    private static final BlockPos CORNER = new BlockPos(2, 1, 2);
    /** On the south side of the east arm, inside the corner's own block. */
    private static final Vec3 INSIDE_THE_CORNER = new Vec3(2.8, 1.6, 2.5625);
    private static final String ABILITY_REQUIRED = "Ability registry must hold test_blast";
    private static final String SHOULD_EXPLODE = "A blob landing inside a fence corner should explode, the corner stands";

    private FenceCornerLandingTests() {
    }

    /**
     * Blast lands on the east arm's south side inside the corner: the corner
     * post is blown away.
     *
     * @param helper the gametest helper
     */
    public static void blastLandsInsideAFenceCorner(GameTestHelper helper) {
        AbilityDefinition blast = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(blast != null, ABILITY_REQUIRED);
        helper.setBlock(CORNER.below(), Blocks.STONE);
        helper.setBlock(CORNER.east().below(), Blocks.STONE);
        helper.setBlock(CORNER.south().below(), Blocks.STONE);
        helper.setBlock(CORNER, Blocks.OAK_FENCE);
        helper.setBlock(CORNER.east(), Blocks.OAK_FENCE);
        helper.setBlock(CORNER.south(), Blocks.OAK_FENCE);
        helper.runAfterDelay(1, () -> {
            AbilityImpact.land(helper.getLevel(), helper.absolutePos(CORNER), GooTypes.UNSTABLE, Direction.SOUTH,
                    blast, helper.absoluteVec(INSIDE_THE_CORNER));
            helper.assertTrue(!helper.getBlockState(CORNER).is(Blocks.OAK_FENCE), SHOULD_EXPLODE);
            helper.succeed();
        });
    }
}
