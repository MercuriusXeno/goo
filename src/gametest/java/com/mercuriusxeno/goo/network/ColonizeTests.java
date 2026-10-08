package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Gametests for Colonize: a blob landing on a shroom network spreads that
 * network over the ground beside it, one landing off any network buds the
 * ground, every landing spores the mobs beside it, and one thrown at a mob
 * lands at its feet, growing no network where there is none
 * (decision colonize-blob-grows-the-network).
 */
public final class ColonizeTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String COLONIZE = "goo:shroom_colonize";
    private static final BlockPos LANDED_ON = new BlockPos(2, 0, 2);
    private static final BlockPos BESIDE = LANDED_ON.east();
    private static final BlockPos TWO_OFF = LANDED_ON.south(2);
    private static final String LANDING_BUDDED = "Colonize landing on no network should bud the landing";
    private static final String NO_MYCELIUM = "Colonize landing on no network should grow no mycelium";
    private static final int SPORE_LEVEL_II = 1;
    private static final int SETTLE_TICKS = 2;
    private static final String BUD_BESIDE = "No bud should grow beside a standing mushroom";
    private static final String MUSHROOM_KEPT = "The mushroom beside the landing should stand";
    private static final String NO_VANILLA = "Spore should carry no vanilla slowness, weakness or poison";
    private static final String PIG_SPORED = "A pig beside the landing should carry the spore poison and spores";

    private ColonizeTests() {
    }

    /**
     * Colonize lands on crimson nylium with netherrack beside it and two
     * blocks off: both turn crimson nylium.
     *
     * @param helper the gametest helper
     */
    public static void colonizeSpreadsNylium(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.CRIMSON_NYLIUM);
        helper.setBlock(BESIDE, Blocks.NETHERRACK);
        helper.setBlock(TWO_OFF, Blocks.NETHERRACK);
        landOn(helper, LANDED_ON);
        helper.assertBlockPresent(Blocks.CRIMSON_NYLIUM, BESIDE);
        helper.assertBlockPresent(Blocks.CRIMSON_NYLIUM, TWO_OFF);
        helper.succeed();
    }

    /**
     * Colonize lands on grass, on no network, with dirt beside it and a pig a
     * block off: the landing takes a bud, the grass and the dirt stay as they
     * are, and the pig carries the spore poison and spores.
     *
     * @param helper the gametest helper
     */
    public static void colonizeBudsOffTheNetwork(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.GRASS_BLOCK);
        helper.setBlock(BESIDE, Blocks.DIRT);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDED_ON.above().south());
        landOn(helper, LANDED_ON);
        helper.assertTrue(helper.getBlockState(LANDED_ON.above()).is(GooBlocks.FUNGAL_BUD.get()), LANDING_BUDDED);
        helper.assertTrue(helper.getBlockState(LANDED_ON).is(Blocks.GRASS_BLOCK)
                && helper.getBlockState(BESIDE).is(Blocks.DIRT), NO_MYCELIUM);
        helper.assertTrue(pig.hasEffect(GooMobEffects.MYCOSIS) && pig.hasData(GooAttachments.SPORED), PIG_SPORED);
        helper.succeed();
    }

    /**
     * Spore thrown at a pig lands at its feet: the pig carries the spore
     * effect at level II and spores, and no vanilla effect stands beside it.
     *
     * @param helper the gametest helper
     */
    public static void sporeStruckOnAMob(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.GRASS_BLOCK);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDED_ON.above());
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.SHROOM,
                    pig.getId(), pig.blockPosition(), Direction.UP, COLONIZE));
            MobEffectInstance spores = pig.getEffect(GooMobEffects.MYCOSIS);
            helper.assertTrue(spores != null && spores.getAmplifier() == SPORE_LEVEL_II && pig.hasData(
                    GooAttachments.SPORED), PIG_SPORED);
            helper.assertFalse(pig.hasEffect(MobEffects.SLOWNESS) || pig.hasEffect(MobEffects.WEAKNESS)
                    || pig.hasEffect(MobEffects.POISON), NO_VANILLA);
            helper.succeed();
        });
    }

    /**
     * Spore lands on grass, on no network, with a red mushroom standing beside
     * the landing cell: no bud grows touching the mushroom, so neither knocks
     * the other off.
     *
     * @param helper the gametest helper
     */
    public static void sporeBudsNoneBesideAMushroom(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.GRASS_BLOCK);
        helper.setBlock(BESIDE, Blocks.GRASS_BLOCK);
        helper.setBlock(BESIDE.above(), Blocks.RED_MUSHROOM);
        landOn(helper, LANDED_ON);
        helper.assertFalse(helper.getBlockState(LANDED_ON.above()).is(GooBlocks.FUNGAL_BUD.get()), BUD_BESIDE);
        helper.assertTrue(helper.getBlockState(BESIDE.above()).is(Blocks.RED_MUSHROOM), MUSHROOM_KEPT);
        helper.succeed();
    }

    private static void landOn(GameTestHelper helper, BlockPos block) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.SHROOM,
                NO_ENTITY, helper.absolutePos(block), Direction.UP, COLONIZE));
    }
}
