package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Gametests for Weird's abilities, each cast through the real landing path.
 * weird-bounces-and-softens-harm
 */
public final class WeirdTests {

    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String MAGMA = "goo:weird_magma";
    private static final String TURNS_LAVA_TO_MAGMA = "weird_turns_lava_to_magma";
    private static final BlockPos POOL_FLOOR = new BlockPos(2, 0, 2);
    private static final int POOL_REACH = 1;
    private static final String LAVA_LEFT = "Weird landing in lava should leave magma where the lava stood at ";
    private static final String BOUNCE = "goo:weird_bounce";
    private static final String PAD_BREAKS_THE_FALL = "weird_pad_breaks_the_fall";
    /** The test bay's roof stands five blocks over the pad, so the pig starts under it. */
    private static final int DROP_START = 3;
    /** A fall a pig at ten health dies of on stone, carried in as the fall it has already made. */
    private static final double FALLEN_BLOCKS = 20;
    private static final double FALL_SPEED = -1.0;
    private static final String NO_PAD = "Weird Bounce landing on stone should leave a bounce pad above it";
    private static final String NOT_LAUNCHED = "A pig falling onto the pad should leave it moving upward";
    private static final String HURT = "A pig falling onto the pad should keep its full health, has ";
    private static final String PAD_STANDS = "The bounce pad should be gone once its time has passed";

    private WeirdTests() {
    }

    /**
     * Registers Weird's gametests under their function names.
     *
     * @param reg takes a function name and its test
     */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> reg) {
        reg.accept(TURNS_LAVA_TO_MAGMA, WeirdTests::weirdTurnsLavaToMagma);
        reg.accept(PAD_BREAKS_THE_FALL, WeirdTests::weirdPadBreaksTheFall);
    }

    /**
     * A Weird Bounce blob lands on stone and leaves a pad; a pig falling onto
     * the pad with twenty blocks of fall behind it keeps its full health and
     * leaves the pad moving upward, and the pad is gone once the time
     * weird_bounce.json names has passed.
     *
     * @param helper the gametest helper
     */
    public static void weirdPadBreaksTheFall(GameTestHelper helper) {
        helper.setBlock(POOL_FLOOR, Blocks.STONE);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, BOUNCE));
        BlockPos pad = POOL_FLOOR.above();
        helper.assertTrue(helper.getBlockState(pad).is(GooBlocks.BOUNCE_PAD.get()), NO_PAD);
        Pig pig = helper.spawn(EntityType.PIG, pad.above(DROP_START));
        pig.fallDistance = FALLEN_BLOCKS;
        pig.setDeltaMovement(0, FALL_SPEED, 0);
        double padTop = helper.absolutePos(pad).getY() + 1;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(pig.getDeltaMovement().y > 0 && pig.getY() >= padTop, NOT_LAUNCHED);
                    helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), HURT + pig.getHealth());
                })
                .thenWaitUntil(() -> helper.assertFalse(helper.getBlockState(pad).is(GooBlocks.BOUNCE_PAD.get()),
                        PAD_STANDS))
                .thenSucceed();
    }

    /**
     * A Weird blob thrown through a three-by-three lava pool strikes its
     * floor, lands in the lava above it, and leaves magma blocks where the
     * pool's lava stood.
     *
     * @param helper the gametest helper
     */
    public static void weirdTurnsLavaToMagma(GameTestHelper helper) {
        for (BlockPos floor : poolFloor()) {
            helper.setBlock(floor, Blocks.STONE);
            helper.setBlock(floor.above(), Blocks.LAVA);
        }
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.WEIRD,
                NO_ENTITY, helper.absolutePos(POOL_FLOOR), Direction.UP, MAGMA));
        for (BlockPos floor : poolFloor()) {
            helper.assertTrue(helper.getBlockState(floor.above()).is(Blocks.MAGMA_BLOCK), LAVA_LEFT + floor.above());
        }
        helper.succeed();
    }

    private static Iterable<BlockPos> poolFloor() {
        return BlockPos.betweenClosed(POOL_FLOOR.offset(-POOL_REACH, 0, -POOL_REACH),
                POOL_FLOOR.offset(POOL_REACH, 0, POOL_REACH));
    }
}
