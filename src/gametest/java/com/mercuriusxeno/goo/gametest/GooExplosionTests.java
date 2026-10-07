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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Goo's explosion through the landing path: Blast struck into
 * a stone floor breaks a crater that stays inside the sphere its preview
 * draws, every block it breaks drops whole, and items in its sphere survive
 * (decisions goo-ray-diminishes-block-resistance, preview-sphere-is-max-reach,
 * explosion-drops-whole-and-spares-items).
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

    private static final int SETTLE_TICKS = 2;
    private static final int ITEM_COUNT = 7;
    private static final double ITEM_DRIFT = 0.1;

    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_explode";
    private static final String NO_EXPLODE_STEP = "Blast's program holds no explode step";
    private static final String STRUCK_STANDS = "Blast left the stone under the aim point standing";
    private static final String BEYOND_SPHERE = "Blast broke stone at %s, %.2f blocks from the aim point, past its reach %.2f";

    private static final String DIRT_LOST = "Blast removed %d dirt but dropped %d dirt items";
    private static final String NOTHING_REMOVED = "Blast removed no dirt";
    private static final String ITEM_DIED = "The item in Blast's sphere died";
    private static final String ITEM_SHRANK = "The item in Blast's sphere holds %s, not %d cobblestone";
    private static final String ITEM_MOVED = "The item in Blast's sphere moved %.3f blocks";

    private GooExplosionTests() {
    }

    private static AbilityDefinition blast(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }

    private static void strike(GameTestHelper helper, AbilityDefinition ability) {
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.UNSTABLE, Direction.UP, ability,
                helper.absoluteVec(AIM));
    }

    /**
     * Blast struck into a stone floor removes the stone under the aim point,
     * and every stone cell it removes lies within the max reach of its power.
     *
     * @param helper the gametest helper
     */
    public static void blastCraterStaysInsideItsSphere(GameTestHelper helper) {
        AbilityDefinition ability = blast(helper);
        ExplodeStep explode = ability.behaviors().stream().filter(ExplodeStep.class::isInstance)
                .map(ExplodeStep.class::cast).findFirst().orElse(null);
        helper.assertTrue(explode != null, NO_EXPLODE_STEP);
        double reach = ExplosionMarch.maxReach(explode.power().evaluateFloat(Variables.NONE));
        layFloor(helper, Blocks.STONE);

        strike(helper, ability);

        helper.assertTrue(helper.getBlockState(STRUCK).isAir(), STRUCK_STANDS);
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, FLOOR_TOP, FLOOR_SPAN - 1).forEach(cell -> {
            double distance = Vec3.atCenterOf(cell).distanceTo(AIM);
            helper.assertTrue(!helper.getBlockState(cell).isAir() || distance <= reach,
                    String.format(BEYOND_SPHERE, cell.toShortString(), distance, reach));
        });
        helper.succeed();
    }

    /**
     * Blast struck into a dirt bed drops one dirt item for every dirt block
     * it removes, none lost to drop decay.
     *
     * @param helper the gametest helper
     */
    public static void blastDropsEveryDirtItBreaks(GameTestHelper helper) {
        AbilityDefinition ability = blast(helper);
        layFloor(helper, Blocks.DIRT);
        long before = countFloor(helper, Blocks.DIRT);

        strike(helper, ability);

        long removed = before - countFloor(helper, Blocks.DIRT);
        int dropped = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem)
                .filter(stack -> stack.is(Items.DIRT)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(removed > 0, NOTHING_REMOVED);
        helper.assertTrue(removed == dropped, String.format(DIRT_LOST, removed, dropped));
        helper.succeed();
    }

    /**
     * An item lying at Blast's aim point on an obsidian floor survives the
     * blast with its stack and its place.
     *
     * @param helper the gametest helper
     */
    public static void blastSparesItemsInItsSphere(GameTestHelper helper) {
        AbilityDefinition ability = blast(helper);
        layFloor(helper, Blocks.OBSIDIAN);
        Vec3 spawned = helper.absoluteVec(AIM);
        ItemEntity item = new ItemEntity(helper.getLevel(), spawned.x(), spawned.y(), spawned.z(),
                new ItemStack(Items.COBBLESTONE, ITEM_COUNT), 0, 0, 0);
        helper.getLevel().addFreshEntity(item);

        strike(helper, ability);

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            helper.assertTrue(item.isAlive(), ITEM_DIED);
            ItemStack held = item.getItem();
            helper.assertTrue(held.is(Items.COBBLESTONE) && held.getCount() == ITEM_COUNT,
                    String.format(ITEM_SHRANK, held, ITEM_COUNT));
            double drift = item.position().distanceTo(spawned);
            helper.assertTrue(drift <= ITEM_DRIFT, String.format(ITEM_MOVED, drift));
            helper.succeed();
        });
    }

    private static void layFloor(GameTestHelper helper, Block floor) {
        BlockPos.betweenClosed(0, 0, 0, FLOOR_SPAN - 1, BAY_HEIGHT - 1, FLOOR_SPAN - 1).forEach(cell ->
                helper.setBlock(cell, cell.getY() <= FLOOR_TOP ? floor : Blocks.AIR));
    }

    private static long countFloor(GameTestHelper helper, Block floor) {
        return BlockPos.betweenClosedStream(0, 0, 0, FLOOR_SPAN - 1, FLOOR_TOP, FLOOR_SPAN - 1)
                .filter(cell -> helper.getBlockState(cell).is(floor)).count();
    }
}
