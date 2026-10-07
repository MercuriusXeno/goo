package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Goo's explosion through the landing path: Blast struck into
 * a stone floor breaks a crater that stays inside the sphere its preview
 * draws (decisions goo-ray-diminishes-block-resistance, preview-sphere-is-max-reach).
 */
public final class GooExplosionTests {

    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_explode");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The stone block Blast strikes, in the middle of the floor. */
    private static final BlockPos STRUCK = new BlockPos(8, FLOOR_TOP, 8);
    /** The aim point, the center of the struck block's top face. */
    private static final Vec3 AIM = new Vec3(8.5, FLOOR_TOP + 1.0, 8.5);

    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_explode";
    private static final String NO_EXPLODE_STEP = "Blast's program holds no explode step";
    private static final String STRUCK_STANDS = "Blast left the stone under the aim point standing";
    private static final String BEYOND_SPHERE = "Blast broke stone at %s, %.2f blocks from the aim point, past its reach %.2f";

    private GooExplosionTests() {
    }

    /**
     * Blast struck into a stone floor removes the stone under the aim point,
     * and every stone cell it removes lies within the max reach of its power.
     *
     * @param helper the gametest helper
     */
    public static void blastCraterStaysInsideItsSphere(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        ExplodeStep explode = ability.behaviors().stream().filter(ExplodeStep.class::isInstance)
                .map(ExplodeStep.class::cast).findFirst().orElse(null);
        helper.assertTrue(explode != null, NO_EXPLODE_STEP);
        double reach = ExplosionMarch.maxReach(explode.power().evaluateFloat(Variables.NONE));
        layStoneFloor(helper);

        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.UNSTABLE, Direction.UP, ability,
                helper.absoluteVec(AIM));

        helper.assertTrue(helper.getBlockState(STRUCK).isAir(), STRUCK_STANDS);
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, FLOOR_TOP, FLOOR_SPAN - 1).forEach(cell -> {
            double distance = Vec3.atCenterOf(cell).distanceTo(AIM);
            helper.assertTrue(!helper.getBlockState(cell).isAir() || distance <= reach,
                    String.format(BEYOND_SPHERE, cell.toShortString(), distance, reach));
        });
        helper.succeed();
    }

    private static void layStoneFloor(GameTestHelper helper) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? Blocks.STONE : Blocks.AIR));
    }
}
