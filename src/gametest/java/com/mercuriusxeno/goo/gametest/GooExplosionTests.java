package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.DragSize;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.entity.CompressionSphere;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Gametests for Goo's explosion through the landing path: Blast landed at its
 * reference size into a stone floor breaks a crater that stays inside the
 * sphere its preview draws, every block it breaks drops whole, and items in
 * its sphere survive (decisions goo-ray-diminishes-block-resistance,
 * preview-sphere-is-max-reach, explosion-drops-whole-and-spares-items,
 * blast-is-drag-sized-like-the-black-hole).
 */
@RegistersGameTests
public final class GooExplosionTests {
    private static final String BLAST_DROPS_EVERY_DIRT = "blast_drops_every_dirt";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BLAST_DROPS_EVERY_DIRT, GooExplosionTests::blastDropsEveryDirtItBreaks);
    }


    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_blast");
    private static final int FLOOR_SPAN = 16;
    private static final int FLOOR_TOP = 2;
    private static final int BAY_HEIGHT = 5;
    /** The stone block Blast strikes, in the middle of the floor. */
    private static final BlockPos STRUCK = new BlockPos(8, FLOOR_TOP, 8);
    /** The aim point, the center of the struck block's top face. */
    private static final Vec3 AIM = new Vec3(8.5, FLOOR_TOP + 1.0, 8.5);

    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_blast";

    private static final String DIRT_LOST = "Blast removed %d dirt but dropped %d dirt items";
    private static final String NOTHING_REMOVED = "Blast removed no dirt";

    private GooExplosionTests() {
    }

    private static AbilityDefinition blast(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }

    private static void strike(GameTestHelper helper, AbilityDefinition ability) {
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.UNSTABLE, Direction.UP, ability,
                helper.absoluteVec(AIM), DragSize.REFERENCE_RADIUS);
    }

    /**
     * Blast struck into a dirt bed drops one dirt item for every dirt block
     * it removes, none lost to drop decay, counting the dirt a large Blast
     * gathers into its compression sphere
     * (decision large-blast-gathers-its-mined-drops-into-a-green-cored-sphere).
     *
     * @param helper the gametest helper
     */
    public static void blastDropsEveryDirtItBreaks(GameTestHelper helper) {
        AbilityDefinition ability = blast(helper);
        layFloor(helper, Blocks.DIRT);
        long before = countFloor(helper, Blocks.DIRT);

        strike(helper, ability);

        long removed = before - countFloor(helper, Blocks.DIRT);
        Stream<ItemStack> loose = helper.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem);
        Stream<ItemStack> gathered = helper.getLevel().getEntitiesOfClass(CompressionSphere.class, helper.getBounds())
                .stream().flatMap(sphere -> sphere.hoard().stacks().stream());
        int dropped = Stream.concat(loose, gathered).filter(stack -> stack.is(Items.DIRT))
                .mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(removed > 0, NOTHING_REMOVED);
        helper.assertTrue(removed == dropped, String.format(DIRT_LOST, removed, dropped));
        helper.succeed();
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
