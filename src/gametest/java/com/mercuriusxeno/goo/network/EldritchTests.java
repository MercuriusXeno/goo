package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.SurvivalPlayers;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Gametests for xeno's Eldritch Sight: an out-of-phase zombie takes a
 * player for its target only while that player is eldritch, whether by
 * the held ability or the brew, and turns from a plain player, the way an
 * in-phase zombie does not.
 * eldritch-sight-reveals-the-out-of-phase
 */
public final class EldritchTests {

    /** Goo the players carry, in thousands: enough for the eat and its upkeep. */
    private static final int HELD_GOO = 2;
    private static final Identifier XENO_ELDRITCH = Identifier.parse("goo:xeno_eldritch");
    private static final String SHOULD_HOLD = "Once the eat finishes the player should hold Eldritch Sight";
    private static final String SHOULD_TARGET_HELD = "An out-of-phase zombie should target the player holding Eldritch Sight, targeted %s";
    private static final String SHOULD_TARGET_BREWED = "An out-of-phase zombie should target the player under the xeno brew, targeted %s";
    private static final String SHOULD_IGNORE_PLAIN = "An out-of-phase zombie should not target a plain player, targeted %s";
    private static final String SHOULD_IGNORE_ENDED = "An out-of-phase zombie should not target a player whose Eldritch Sight ended, targeted %s";
    private static final String IN_PHASE_SHOULD_TARGET = "An in-phase zombie should target the plain player, targeted %s";

    private EldritchTests() {
    }

    /**
     * A survival player eats Eldritch Sight from the glove and another drinks
     * the xeno brew: an out-of-phase zombie offered each player takes each
     * one for its target.
     *
     * @param helper the gametest helper
     */
    public static void eldritchPlayerIsSeen(GameTestHelper helper) {
        ServerPlayer holder = eatenEldritch(helper);
        boolean held = holder.getData(GooAttachments.HELD_EFFECTS).holds(XENO_ELDRITCH);
        ServerPlayer drinker = targetable(helper);
        BrewEffectTests.drink(drinker, GooTypes.XENO);
        Zombie zombie = zombie(helper, true);

        LivingEntity heldTarget = offer(zombie, holder);
        LivingEntity brewedTarget = offer(zombie, drinker);
        zombie.discard();
        helper.getLevel().getServer().getPlayerList().remove(holder);
        helper.getLevel().getServer().getPlayerList().remove(drinker);
        helper.assertTrue(held, SHOULD_HOLD);
        helper.assertTrue(heldTarget == holder, String.format(SHOULD_TARGET_HELD, heldTarget));
        helper.assertTrue(brewedTarget == drinker, String.format(SHOULD_TARGET_BREWED, brewedTarget));
        helper.succeed();
    }

    /**
     * An out-of-phase zombie offered a plain player takes no target, while an
     * in-phase zombie takes that player; and a player whose held Eldritch
     * Sight is invoked again to end it is ignored once more.
     *
     * @param helper the gametest helper
     */
    public static void plainPlayerIsUnseen(GameTestHelper helper) {
        ServerPlayer plain = targetable(helper);
        ServerPlayer ended = eatenEldritch(helper);
        SelfDeliveryTests.invoke(ended, GooTypes.XENO, XENO_ELDRITCH);
        ended.doTick();
        Zombie outOfPhase = zombie(helper, true);
        Zombie inPhase = zombie(helper, false);

        LivingEntity plainTarget = offer(outOfPhase, plain);
        LivingEntity endedTarget = offer(outOfPhase, ended);
        LivingEntity inPhaseTarget = offer(inPhase, plain);
        outOfPhase.discard();
        inPhase.discard();
        helper.getLevel().getServer().getPlayerList().remove(plain);
        helper.getLevel().getServer().getPlayerList().remove(ended);
        helper.assertTrue(plainTarget == null, String.format(SHOULD_IGNORE_PLAIN, plainTarget));
        helper.assertTrue(endedTarget == null, String.format(SHOULD_IGNORE_ENDED, endedTarget));
        helper.assertTrue(inPhaseTarget == plain, String.format(IN_PHASE_SHOULD_TARGET, inPhaseTarget));
        helper.succeed();
    }

    /**
     * A survival player a mob can take for its target, holding a glove and
     * xeno goo; vanilla's mock player stands creative, which no mob targets.
     */
    private static ServerPlayer targetable(GameTestHelper helper) {
        ServerPlayer player = SurvivalPlayers.placeIn(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.XENO, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }

    /**
     * A targetable player who invoked Eldritch Sight from the glove and ate
     * it through, ticked once so the held effect stands.
     */
    private static ServerPlayer eatenEldritch(GameTestHelper helper) {
        ServerPlayer player = targetable(helper);
        SelfDeliveryTests.invoke(player, GooTypes.XENO, XENO_ELDRITCH);
        SelfDeliveryTests.eatThrough(player);
        player.doTick();
        return player;
    }

    private static Zombie zombie(GameTestHelper helper, boolean outOfPhase) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        zombie.setNoAi(true);
        zombie.setPos(helper.getBounds().getCenter());
        zombie.setData(GooAttachments.OUT_OF_PHASE, outOfPhase);
        helper.getLevel().addFreshEntity(zombie);
        return zombie;
    }

    /**
     * Offers a zombie a player as its target the way its targeting goals do,
     * and answers the target it took, clearing it after.
     */
    private static @Nullable LivingEntity offer(Zombie zombie, ServerPlayer player) {
        zombie.setTarget(player);
        LivingEntity taken = zombie.getTarget();
        zombie.setTarget(null);
        return taken;
    }
}
