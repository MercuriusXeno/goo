package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for typhoon Updraft landed through the real effect path: its
 * column carries a zombie standing in it upward, and its blob is gone once
 * the duration ends (decision updraft-blob-stands-a-column-of-wind); the
 * column refills the air of a zombie it carries (decision
 * updraft-refills-the-air-of-entities-it-propels). The bay's ceiling stands
 * below the column's top, so the test watches the climb.
 */
@RegistersGameTests
public final class UpdraftTests {
    private static final String UPDRAFT_LIFTS_A_ZOMBIE = "updraft_lifts_a_zombie";
    private static final String UPDRAFT_REFILLS_A_ZOMBIES_AIR = "updraft_refills_a_zombies_air";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, UPDRAFT_LIFTS_A_ZOMBIE, UpdraftTests::updraftLiftsAZombie);
        GooTestFunctions.registerTest(registrar, UPDRAFT_REFILLS_A_ZOMBIES_AIR, UpdraftTests::updraftRefillsAZombiesAir);
    }


    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String UPDRAFT = "goo:typhoon_updraft";
    private static final Variables NO_VARIABLES = name -> OptionalDouble.of(0);
    /** The tick the blob lands on, before its column first carries anything. */
    private static final int LANDING_TICK = 1;
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** Where the blob stands, on the floor's top face. */
    private static final BlockPos BLOB = FLOOR.above();
    /** Beside the blob, inside the column's width. */
    private static final BlockPos ZOMBIE_POS = BLOB.east();
    private static final int CLIMB_TICKS = 15;
    /** The least the zombie climbs in those ticks, short of the bay's ceiling. */
    private static final double LEAST_CLIMB = 2.5;
    private static final String BLOB_STANDS = "The updraft's blob should stand where it landed";
    private static final String SHOULD_CLIMB = "The column should carry the zombie up %.1f blocks in %d ticks, climbed %.2f";
    private static final String SHOULD_REFILL = "The column should refill an airless zombie to %d air in %d ticks, read %d";
    private static final String UPDRAFT_REQUIRED = "The server should hold goo:typhoon_updraft with an updraft step";

    private UpdraftTests() {
    }

    /**
     * An updraft lands on the floor beside a zombie: within fifteen ticks the
     * zombie has climbed two and a half blocks.
     *
     * @param helper the gametest helper
     */
    public static void updraftLiftsAZombie(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ZOMBIE_POS.below(), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        double startY = zombie.getY();
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, GooTypes.TYPHOON,
                NO_ENTITY, helper.absolutePos(FLOOR), Direction.UP, UPDRAFT));
        helper.assertTrue(helper.getBlockState(BLOB).is(GooBlocks.ABILITY_BLOCK.get()), BLOB_STANDS);
        helper.runAfterDelay(CLIMB_TICKS, () -> {
            double climbed = zombie.getY() - startY;
            helper.assertTrue(climbed >= LEAST_CLIMB, String.format(SHOULD_CLIMB, LEAST_CLIMB, CLIMB_TICKS, climbed));
            helper.succeed();
        });
    }

    /**
     * An updraft lands on the floor beside a zombie whose air is empty: in
     * the ticks its full air takes at the column's rate, the zombie reads
     * its full air.
     *
     * @param helper the gametest helper
     */
    public static void updraftRefillsAZombiesAir(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(ZOMBIE_POS.below(), Blocks.STONE);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setAirSupply(0);
        int full = zombie.getMaxAirSupply();
        int ticks = LANDING_TICK + (int) Math.ceil((double) full / airRefilledATick(helper));
        landUpdraft(helper.getLevel(), helper.absolutePos(FLOOR));
        helper.runAfterDelay(ticks, () -> {
            helper.assertTrue(zombie.getAirSupply() == full,
                    String.format(SHOULD_REFILL, full, ticks, zombie.getAirSupply()));
            helper.succeed();
        });
    }

    /**
     * Lands goo:typhoon_updraft through the real effect path on a floor block's top face in any level, its blob
     * standing on the floor.
     *
     * @param level the level
     * @param floor the floor block, in the level's own coordinates
     */
    public static void landUpdraft(ServerLevel level, BlockPos floor) {
        GooEffectScheduler.applyEffect(new PendingEffect(0, level, null, GooTypes.TYPHOON, NO_ENTITY, floor,
                Direction.UP, UPDRAFT));
    }

    /**
     * The air the updraft's column refills an entity it carries each tick,
     * as goo:typhoon_updraft's updraft step names it.
     *
     * @param helper the gametest helper
     * @return the air refilled a tick
     */
    public static int airRefilledATick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AbilityDefinition updraft = AbilityRegistry.of(level).getAbility(Identifier.parse(UPDRAFT));
        helper.assertTrue(updraft != null, UPDRAFT_REQUIRED);
        return LingerStep.bodyOf(updraft.behaviors()).flatMap(body -> body.stream()
                        .filter(UpdraftStep.class::isInstance).map(UpdraftStep.class::cast).findFirst())
                .map(step -> step.air().evaluateInt(NO_VARIABLES))
                .orElseThrow(() -> new IllegalStateException(UPDRAFT_REQUIRED));
    }
}
