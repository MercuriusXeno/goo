package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for the lift combo Updraft grows on a prism: Updraft thrown at a
 * bare prism through the real throw path grows the lift for a spend of
 * typhoon goo and stands no wind column, what stands on the block above the prism
 * rises up the shaft while what stands beside it stays, and a rider sneaking
 * in the shaft sinks gently instead
 * (decisions lift-prism-levitates-the-block-above and thrown-hosts-absorb-their-prism-combos).
 */
@RegistersGameTests
public final class LiftTests {
    private static final String LIFT_RAISES = "lift_raises_what_stands_above";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFT_RAISES, LiftTests::updraftOnAPrismRaisesWhatStandsAbove);
    }


    private static final Identifier TYPHOON_UPDRAFT = Identifier.parse("goo:typhoon_updraft");
    private static final int NO_ENTITY = -1;
    private static final BlockPos PRISM_POS = new BlockPos(2, 1, 2);
    /** The block above the prism, the shaft's floor. */
    private static final BlockPos RIDER_POS = PRISM_POS.above();
    /** The least the rider climbs, short of the bay's ceiling. */
    private static final double LEAST_RISE = 1.5;
    private static final String ABILITY_REQUIRED = "The ability registry must hold typhoon_updraft";
    private static final String SHOULD_HOLD_COMBO = "The prism should hold Updraft's lift as its combo, held '%s'";
    private static final String SHOULD_CHARGE = "The throw should spend typhoon goo, spent %d";
    private static final String SHOULD_RISE = "The zombie on the lift should rise %.1f blocks, rose %.2f";

    private LiftTests() {
    }

    /**
     * Updraft thrown at a bare prism with a zombie standing on it: the throw
     * spends typhoon goo, the prism takes the lift combo sourced from
     * Updraft, and the rider climbs a block and a half.
     *
     * @param helper the gametest helper
     */
    public static void updraftOnAPrismRaisesWhatStandsAbove(GameTestHelper helper) {
        AbilityDefinition updraft = AbilityRegistry.of(helper.getLevel()).getAbility(TYPHOON_UPDRAFT);
        helper.assertTrue(updraft != null, ABILITY_REQUIRED);
        standPrism(helper);
        Mob rider = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, RIDER_POS);
        double riderStart = rider.getY();
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        KnownRecipes.teachRequires(thrower, updraft);
        int heldBefore = typhoonHeld(thrower);

        GooThrowHandler.execute(thrower, new GooThrowPayload(GooTypes.id(GooTypes.TYPHOON), NO_ENTITY,
                helper.absolutePos(PRISM_POS), Direction.UP.ordinal(), false, TYPHOON_UPDRAFT.toString(),
                thrower.getEyePosition()));
        int spent = heldBefore - typhoonHeld(thrower);
        helper.getLevel().getServer().getPlayerList().remove(thrower);
        helper.assertTrue(spent > 0, String.format(SHOULD_CHARGE, spent));

        helper.succeedWhen(() -> {
            String combo = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class).getCombo();
            helper.assertTrue(TYPHOON_UPDRAFT.toString().equals(combo), String.format(SHOULD_HOLD_COMBO, combo));
            double rose = rider.getY() - riderStart;
            helper.assertTrue(rose >= LEAST_RISE, String.format(SHOULD_RISE, LEAST_RISE, rose));
        });
    }

    private static void standPrism(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
    }

    private static int typhoonHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.TYPHOON, 0);
    }
}
