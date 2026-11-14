package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooMobEffects;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Spore: a blob landing on a shroom network spreads that
 * network over the ground beside it, one landing off any network buds the
 * ground, every landing spores the mobs beside it, and one thrown at a mob
 * lands at its feet, growing no network where there is none
 * (decision colonize-blob-grows-the-network).
 */
@RegistersGameTests
public final class SporeTests {
    private static final String SPORE_SPREADS_NYLIUM = "spore_spreads_nylium";
    private static final String SPORE_BUDS_OFF_THE_NETWORK = "spore_buds_off_the_network";
    private static final String SPORE_STRUCK_ON_A_MOB = "spore_struck_on_a_mob";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPORE_SPREADS_NYLIUM, SporeTests::sporeSpreadsNylium);
        GooTestFunctions.registerTest(registrar, SPORE_BUDS_OFF_THE_NETWORK, SporeTests::sporeBudsOffTheNetwork);
        GooTestFunctions.registerTest(registrar, SPORE_STRUCK_ON_A_MOB, SporeTests::sporeStruckOnAMob);
    }


    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String SPORE = "goo:shroom_spore";
    private static final BlockPos LANDED_ON = new BlockPos(2, 0, 2);
    private static final BlockPos BESIDE = LANDED_ON.east();
    /** How far south of the landing's middle the pig stands: under a block, inside any spore radius of one. */
    private static final double CLOSE_BESIDE = 0.6;
    private static final BlockPos TWO_OFF = LANDED_ON.south(2);
    private static final String LANDING_BUDDED = "Spore landing on no network should bud the landing";
    private static final int SETTLE_TICKS = 2;
    private static final String PIG_SPORED = "A pig beside the landing should carry the spore poison and spores";

    private SporeTests() {
    }

    /**
     * Spore lands on crimson nylium with netherrack beside it and two
     * blocks off: both turn crimson nylium.
     *
     * @param helper the gametest helper
     */
    public static void sporeSpreadsNylium(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.CRIMSON_NYLIUM);
        helper.setBlock(BESIDE, Blocks.NETHERRACK);
        helper.setBlock(TWO_OFF, Blocks.NETHERRACK);
        landOn(helper, LANDED_ON);
        helper.assertBlockPresent(Blocks.CRIMSON_NYLIUM, BESIDE);
        helper.assertBlockPresent(Blocks.CRIMSON_NYLIUM, TWO_OFF);
        helper.succeed();
    }

    /**
     * Spore lands on grass, on no network, with dirt beside it and a pig close
     * beside: the landing takes a bud and the pig carries the spore poison
     * and spores.
     *
     * @param helper the gametest helper
     */
    public static void sporeBudsOffTheNetwork(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.GRASS_BLOCK);
        helper.setBlock(BESIDE, Blocks.DIRT);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDED_ON.above().south());
        // well inside the spore radius however it is tuned, at the landing cell's own south edge
        pig.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(LANDED_ON.above()).add(0, 0, CLOSE_BESIDE)));
        landOn(helper, LANDED_ON);
        helper.assertTrue(helper.getBlockState(LANDED_ON.above()).is(GooBlocks.FUNGAL_BUD.get()), LANDING_BUDDED);
        helper.assertTrue(pig.hasEffect(GooMobEffects.MYCOSIS) && pig.hasData(GooAttachments.SPORED), PIG_SPORED);
        helper.succeed();
    }

    /**
     * Spore thrown at a pig lands at its feet: the pig carries the spore
     * effect and spores.
     *
     * @param helper the gametest helper
     */
    public static void sporeStruckOnAMob(GameTestHelper helper) {
        helper.setBlock(LANDED_ON, Blocks.GRASS_BLOCK);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, LANDED_ON.above());
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.SHROOM,
                    pig.getId(), pig.blockPosition(), Direction.UP, SPORE));
            helper.assertTrue(pig.hasEffect(GooMobEffects.MYCOSIS) && pig.hasData(GooAttachments.SPORED), PIG_SPORED);
            helper.succeed();
        });
    }

    private static void landOn(GameTestHelper helper, BlockPos block) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.SHROOM,
                NO_ENTITY, helper.absolutePos(block), Direction.UP, SPORE));
    }
}
