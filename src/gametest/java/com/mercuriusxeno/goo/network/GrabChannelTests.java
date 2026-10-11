package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gametests for Kinetic's Grab, driven through the channel's stream tick
 * and the throw a left click sends: a held zombie loses its target and
 * hangs off the ground in front of the player, looking down draws it
 * closer, a throw sends it along the look, and an item entity is held and
 * thrown the same (decision grab-holds-and-throws-a-physics-body).
 */
public final class GrabChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 2);
    private static final Vec3 ITEM_POS = new Vec3(3.5, 1.2, 2.5);
    /** Yaw looking east, along +x. */
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** A pitch halfway to the full draw, so the hold comes in from far toward near. */
    private static final float LOOKING_DOWN = 30f;
    private static final int HOLD_TICKS = 5;
    private static final int FLIGHT_TICKS = 2;
    private static final int HELD_GOO = 3;
    /** The ability's far hold distance, eye to the held entity's middle. */
    private static final double FAR = 4;
    private static final double SETTLED = 0.3;
    /** How far a held entity must hang above where it stood to read as lifted. */
    private static final double LIFTED = 0.4;
    /** How much closer the hold must draw at LOOKING_DOWN than level. */
    private static final double DRAWN_IN = 0.5;
    /** How far along the look a thrown entity must fly in FLIGHT_TICKS; one left alone moves none. */
    private static final double THROWN = 1.0;
    private static final double HALF = 0.5;
    private static final Identifier KINETIC_GRAB = Identifier.parse("goo:kinetic_grab");
    private static final String ABILITY_REQUIRED = "Ability registry must hold kinetic_grab";

    private GrabChannelTests() {
    }

    /**
     * A mob holding the player as its target loses it on the grab and hangs
     * in front of the player at the far distance, off the ground, its AI suspended.
     *
     * @param helper the gametest helper
     */
    public static void grabSuspendsTheZombie(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setTarget(player);
        double stoodAt = zombie.getY();
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.assertTrue(zombie.getTarget() == null, "A held zombie should have no target");
            helper.assertTrue(zombie.isNoAi(), "A held zombie's AI should be suspended");
            helper.assertTrue(zombie.getY() > stoodAt + LIFTED,
                    "A held zombie should hang off the ground, stood at " + stoodAt + ", hangs at " + zombie.getY());
            double distance = player.getEyePosition().distanceTo(middleOf(zombie));
            helper.assertTrue(Math.abs(distance - FAR) < SETTLED,
                    "A held zombie should hang " + FAR + " from the eye, hangs " + distance);
            leave(helper, player);
        });
    }

    /**
     * Pitching the look down while holding draws the held zombie closer to the eye.
     *
     * @param helper the gametest helper
     */
    public static void grabPitchDrawsCloser(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        AtomicReference<Double> levelDistance = new AtomicReference<>();
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS, () -> {
            levelDistance.set(player.getEyePosition().distanceTo(middleOf(zombie)));
            player.setXRot(LOOKING_DOWN);
        });
        for (int held = HOLD_TICKS + 1; held <= 2 * HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player)));
        }
        helper.runAfterDelay(2 * HOLD_TICKS + 1, () -> {
            double drawnDistance = player.getEyePosition().distanceTo(middleOf(zombie));
            helper.assertTrue(drawnDistance < levelDistance.get() - DRAWN_IN,
                    "Looking down should draw the zombie in from " + levelDistance.get() + ", hangs " + drawnDistance);
            leave(helper, player);
        });
    }

    /**
     * A throw while holding sends the zombie along the look and hands its AI back.
     *
     * @param helper the gametest helper
     */
    public static void grabThrowsAlongTheLook(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS);
        throwAfterHold(helper, player, zombie);
        helper.runAfterDelay(HOLD_TICKS + 2, () -> helper.assertTrue(!zombie.isNoAi(),
                "A thrown zombie's AI should be handed back"));
    }

    /**
     * An item entity is held and thrown along the look the same as a mob.
     *
     * @param helper the gametest helper
     */
    public static void grabThrowsAnItem(GameTestHelper helper) {
        ServerPlayer player = grabber(helper);
        Vec3 itemAt = helper.absoluteVec(ITEM_POS);
        ItemEntity item = new ItemEntity(helper.getLevel(), itemAt.x, itemAt.y, itemAt.z,
                new ItemStack(Items.SLIME_BALL));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, item.position());
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, holdTick(player)));
        helper.runAfterDelay(2, () -> player.setXRot(LEVEL));
        throwAfterHold(helper, player, item);
    }

    /**
     * Holds for HOLD_TICKS from the second tick, throws on the tick after,
     * and checks the entity flew along the look FLIGHT_TICKS later.
     */
    private static void throwAfterHold(GameTestHelper helper, ServerPlayer player, Entity thrown) {
        AtomicReference<Vec3> heldAt = new AtomicReference<>();
        for (int held = 2; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player)));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            heldAt.set(thrown.position());
            helper.assertTrue(GrabEvents.throwHeld(player), "The player should hold the entity to throw it");
        });
        helper.runAfterDelay(HOLD_TICKS + 1 + FLIGHT_TICKS, () -> {
            double along = thrown.position().subtract(heldAt.get()).dot(player.getLookAngle());
            helper.assertTrue(along > THROWN, "The thrown entity should fly along the look, flew " + along + " to y " + thrown.getY()
                    + (thrown.onGround() ? " on the ground" : " in the air"));
            leave(helper, player);
        });
    }

    private static void holdFor(GameTestHelper helper, ServerPlayer player, int ticks) {
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player)));
        }
    }

    private static GooStreamPayload holdTick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), KINETIC_GRAB.toString(),
                eye, eye.add(player.getLookAngle().scale(FAR)));
    }

    private static Vec3 middleOf(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() * HALF, 0);
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer grabber(GameTestHelper helper) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setXRot(LEVEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, grab);
        return player;
    }
}
