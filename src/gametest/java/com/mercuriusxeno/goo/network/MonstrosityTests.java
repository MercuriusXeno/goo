package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooEntities;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for the Eldritch Monstrosity: it stands out of phase from the
 * moment it enters a level, hunts an eldritch player on its own goals, and
 * dies under a phased player's hit into xeno goo.
 * monstrosities-harvest-under-quantum
 */
@RegistersGameTests
public final class MonstrosityTests {
    private static final String ENTERS_OUT_OF_PHASE = "monstrosity_enters_out_of_phase";
    private static final String HUNTS_THE_ELDRITCH = "monstrosity_hunts_the_eldritch";
    private static final String DROPS_XENO = "monstrosity_drops_xeno";

    private static final Identifier PHASE = Identifier.parse("goo:quantum_phase");
    private static final Identifier XENO_UNVEIL = Identifier.parse("goo:xeno_unveil");
    private static final int HELD_XENO = 3;
    private static final BlockPos MONSTROSITY_POS = new BlockPos(3, 1, 3);
    private static final float KILLING_HIT = 1000.0F;
    private static final int SETTLE_TICKS = 1;

    private MonstrosityTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ENTERS_OUT_OF_PHASE, MonstrosityTests::monstrosityEntersOutOfPhase);
        GooTestFunctions.registerTest(registrar, HUNTS_THE_ELDRITCH, MonstrosityTests::monstrosityHuntsTheEldritch);
        GooTestFunctions.registerTest(registrar, DROPS_XENO, MonstrosityTests::monstrosityDropsXeno);
    }

    /**
     * A monstrosity entering a level stands out of phase, a phase that never fades.
     *
     * @param helper the gametest helper
     */
    public static void monstrosityEntersOutOfPhase(GameTestHelper helper) {
        EldritchMonstrosity monstrosity = helper.spawn(GooEntities.ELDRITCH_MONSTROSITY.get(), MONSTROSITY_POS);
        OutOfPhase phase = monstrosity.getData(GooAttachments.OUT_OF_PHASE);
        helper.assertTrue(OutOfPhase.held().equals(phase),
                "A monstrosity should enter the level out of phase for good, stands " + phase);
        helper.succeed();
    }

    /**
     * A free-willed monstrosity beside a player holding Unveil on the glove takes
     * that player as its target through its own goals.
     *
     * @param helper the gametest helper
     */
    public static void monstrosityHuntsTheEldritch(GameTestHelper helper) {
        ServerPlayer eldritch = SurvivalPlayers.placeIn(helper);
        eldritch.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        eldritch.getInventory().add(GooStacks.createForOutput(GooTypes.XENO, HELD_XENO * GooStacks.THOUSAND));
        HeartOverlayTests.invoke(eldritch, GooTypes.XENO, XENO_UNVEIL);
        EldritchMonstrosity monstrosity = helper.spawn(GooEntities.ELDRITCH_MONSTROSITY.get(), MONSTROSITY_POS);
        helper.succeedWhen(() -> {
            helper.assertTrue(monstrosity.getTarget() == eldritch,
                    "A monstrosity should target the eldritch player, targeted " + monstrosity.getTarget());
            helper.getLevel().getServer().getPlayerList().remove(eldritch);
        });
    }

    /**
     * A player holding Phase kills a monstrosity, which drops xeno goo where it falls.
     *
     * @param helper the gametest helper
     */
    public static void monstrosityDropsXeno(GameTestHelper helper) {
        ServerPlayer phased = HeartOverlayTests.selfInvoked(helper, GooTypes.QUANTUM, PHASE);
        EldritchMonstrosity monstrosity = helper.spawnWithNoFreeWill(GooEntities.ELDRITCH_MONSTROSITY.get(),
                MONSTROSITY_POS);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            monstrosity.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(phased),
                    KILLING_HIT);
            helper.assertTrue(monstrosity.isDeadOrDying(), "A phased player's hit should kill the monstrosity");
            int xeno = droppedXeno(helper, monstrosity);
            helper.getLevel().getServer().getPlayerList().remove(phased);
            helper.assertTrue(xeno == EldritchMonstrosity.XENO_DROP_MB,
                    "A slain monstrosity should drop " + EldritchMonstrosity.XENO_DROP_MB + " xeno goo, dropped " + xeno);
            helper.succeed();
        });
    }

    private static int droppedXeno(GameTestHelper helper, EldritchMonstrosity monstrosity) {
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                monstrosity.getBoundingBox().inflate(2.0));
        int volume = 0;
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (GooTypes.XENO.equals(GooStacks.keyOf(stack))) {
                volume += GooStacks.volumeOf(stack);
            }
        }
        return volume;
    }
}
