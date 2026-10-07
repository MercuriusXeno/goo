package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for prism combos through the real landing path: a goo landing on
 * a prism runs its type's prism ability, or the landing ability's own
 * on_prism reaction, on a marker host at the prism and records the combo; a
 * type with neither leaves the prism as it is; a combined prism refuses a
 * second combo. The test datapack's seventeenth type carries the probes.
 * decision prism-hosts-the-combos
 */
public final class PrismComboTests {

    private static final BlockPos FLOOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PRISM_POS = FLOOR_POS.above();
    private static final BlockPos PIG_POS = new BlockPos(2, 2, 1);
    private static final int NO_ENTITY = -1;
    private static final String TEST_PACK = "gootest";
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH =
            ResourceKey.create(GooTypes.REGISTRY, Identifier.fromNamespaceAndPath(TEST_PACK, "seventeenth"));
    private static final String SEVENTEENTH_PROBE = TEST_PACK + ":seventeenth_probe";
    private static final String PRISM_PROBE = TEST_PACK + ":seventeenth_prism_probe";
    private static final String BULB_PROBE = TEST_PACK + ":seventeenth_bulb_probe";
    private static final String CRYSTAL_CLOUD = "goo:crystal_cloud";

    private static final String WRONG_COMBO = "The prism should hold combo '%s', held '%s'";
    private static final String PROGRAM_DID_NOT_RUN = "The combo's program should strike the pig beside the prism";
    private static final String LANDED_BESIDE = "A landing on a prism should stand nothing beside it";

    private PrismComboTests() {
    }

    /**
     * A seventeenth goo whose ability names no prism reaction lands on a prism:
     * the type's lowest-order prism ability runs at the prism, striking the
     * pig beside it, and the prism records that ability as its combo.
     *
     * @param helper the gametest helper
     */
    public static void comboRunsTheTypePrismAbility(GameTestHelper helper) {
        Pig pig = standPrismBesidePig(helper);

        landOnPrism(helper, SEVENTEENTH, SEVENTEENTH_PROBE);

        assertCombo(helper, PRISM_PROBE);
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), PROGRAM_DID_NOT_RUN);
        helper.succeed();
    }

    /**
     * A goo whose ability carries an on_prism reaction lands on a prism: that
     * reaction runs at the prism in place of the type's prism ability, and the
     * prism records the landing ability as its combo.
     *
     * @param helper the gametest helper
     */
    public static void comboRunsOnPrismBehaviors(GameTestHelper helper) {
        Pig pig = standPrismBesidePig(helper);

        landOnPrism(helper, SEVENTEENTH, BULB_PROBE);

        assertCombo(helper, BULB_PROBE);
        helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), PROGRAM_DID_NOT_RUN);
        helper.succeed();
    }

    /**
     * A crystal goo, whose type has no prism ability and whose ability names
     * no prism reaction, lands on a prism: the prism stands plain, and the
     * landing stands nothing beside it.
     *
     * @param helper the gametest helper
     */
    public static void prismWithoutComboStays(GameTestHelper helper) {
        standPrism(helper);

        landOnPrism(helper, GooTypes.CRYSTAL, CRYSTAL_CLOUD);

        assertCombo(helper, PrismBlockEntity.NO_COMBO);
        helper.assertTrue(helper.getBlockState(PRISM_POS.above()).isAir(), LANDED_BESIDE);
        helper.succeed();
    }

    /**
     * A prism holding the type's prism combo takes a second landing whose
     * ability carries its own reaction: the second combo is refused and the
     * prism keeps the first.
     *
     * @param helper the gametest helper
     */
    public static void combinedPrismRefusesSecond(GameTestHelper helper) {
        standPrism(helper);
        landOnPrism(helper, SEVENTEENTH, SEVENTEENTH_PROBE);

        landOnPrism(helper, SEVENTEENTH, BULB_PROBE);

        assertCombo(helper, PRISM_PROBE);
        helper.succeed();
    }

    private static Pig standPrismBesidePig(GameTestHelper helper) {
        standPrism(helper);
        helper.setBlock(PIG_POS.below(), Blocks.STONE);
        return helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);
    }

    private static void standPrism(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
    }

    private static void landOnPrism(GameTestHelper helper, ResourceKey<GooTypeDefinition> type, String abilityId) {
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), null, type, NO_ENTITY,
                helper.absolutePos(PRISM_POS), Direction.UP, abilityId));
        arrivals.drainArrivedEffects(arrivalTick);
    }

    private static void assertCombo(GameTestHelper helper, String expected) {
        helper.assertBlockPresent(GooBlocks.PRISM.get(), PRISM_POS);
        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        helper.assertTrue(expected.equals(prism.getCombo()), String.format(WRONG_COMBO, expected, prism.getCombo()));
    }
}
