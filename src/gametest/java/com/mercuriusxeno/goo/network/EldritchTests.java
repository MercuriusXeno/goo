package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.quantum.OutOfPhase;
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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for xeno's Eldritch Sight: an out-of-phase zombie takes a player
 * holding the ability for its target.
 * eldritch-sight-reveals-the-out-of-phase
 */
@RegistersGameTests
public final class EldritchTests {
    private static final String ELDRITCH_PLAYER_IS_SEEN = "eldritch_player_is_seen";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ELDRITCH_PLAYER_IS_SEEN, EldritchTests::eldritchPlayerIsSeen);
    }


    /** Goo the players carry, in thousands: enough for the eat and its upkeep. */
    private static final int HELD_GOO = 2;
    private static final Identifier XENO_ELDRITCH = Identifier.parse("goo:xeno_eldritch");
    private static final String SHOULD_HOLD = "Once the eat finishes the player should hold Eldritch Sight";
    private static final String SHOULD_TARGET_HELD = "An out-of-phase zombie should target the player holding Eldritch Sight, targeted %s";

    private EldritchTests() {
    }

    /**
     * A survival player eats Eldritch Sight from the glove: an out-of-phase
     * zombie offered the player takes it for its target.
     *
     * @param helper the gametest helper
     */
    public static void eldritchPlayerIsSeen(GameTestHelper helper) {
        ServerPlayer holder = eatenEldritch(helper);
        boolean held = holder.getData(GooAttachments.HELD_EFFECTS).holds(XENO_ELDRITCH);
        Zombie zombie = outOfPhaseZombie(helper);

        LivingEntity heldTarget = offer(zombie, holder);
        zombie.discard();
        helper.getLevel().getServer().getPlayerList().remove(holder);
        helper.assertTrue(held, SHOULD_HOLD);
        helper.assertTrue(heldTarget == holder, String.format(SHOULD_TARGET_HELD, heldTarget));
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

    private static Zombie outOfPhaseZombie(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        zombie.setNoAi(true);
        zombie.setPos(helper.getBounds().getCenter());
        zombie.setData(GooAttachments.OUT_OF_PHASE, OutOfPhase.held());
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
