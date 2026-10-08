package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.UnmakeRule;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gametests for Unmake: a stream held at two cobblestones in a line and a
 * diamond block melts the nearer cobblestone on the very tick its crucible
 * value's work is done, drops the share of its goo the ability yields, and
 * leaves the cobblestone it hid and the dearer diamond block standing; a
 * stream held at a chicken melts it into the goo of its loot.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeTests {

    private static final Identifier UNMAKE = Identifier.parse("goo:unstable_unmake");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Three blocks east at eye height, on the look's axis. */
    private static final BlockPos CHEAP_POS = new BlockPos(4, 2, 3);
    /** Right behind the first cobblestone along the look, hidden from the player by it. */
    private static final BlockPos BEHIND_POS = CHEAP_POS.east();
    /** Four blocks east and one south at eye height, inside the cone beside the cobblestone. */
    private static final BlockPos DEAR_POS = new BlockPos(5, 2, 4);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** A little above level, so the cone clears the floor. */
    private static final float LOOKING_UP = -5f;
    /** unstable_unmake.json's work_per_goo and yield. */
    private static final double WORK_PER_GOO = 0.025;
    private static final double YIELD = 0.5;
    private static final int HOLD_TICKS = 40;
    private static final int HELD_GOO = 25;
    private static final double DROP_REACH = 2;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_unmake";
    private static final String NOT_DEAR = "The diamond block should take longer to unmake than the hold, needs %d";
    private static final String GONE_EARLY = "The cobblestone should stand one tick short of its work, %d ticks";
    private static final String NOT_GONE = "The cobblestone should be gone once its work of %d ticks is done";
    private static final String HOLD_TOO_LONG = "The hold should end before the hidden cobblestone, seen only once "
            + "the first melts at %d ticks, could melt too";
    /** The longest the chicken test holds, past a chicken's loot work at Unmake's rate. */
    private static final int MOB_HOLD_TICKS = 400;
    private static final String MOB_STANDS = "The chicken should melt within the hold";
    private static final String MOB_DROPPED_ITEMS = "A melted chicken should drop none of its items";
    private static final String MOB_LEFT_NO_GOO = "A melted chicken should leave goo of its loot";
    private static final String DEAR_GONE = "The diamond block should still stand after %d ticks";
    private static final String WRONG_YIELD = "The cobblestone should leave %s, left %s";

    private UnmakeTests() {
    }

    /**
     * A mock player holds Unmake at a cobblestone and a diamond block for
     * forty ticks: the cobblestone stands one tick short of its work and is
     * gone on that tick, leaving half its goo, while the diamond block stands.
     *
     * @param helper the gametest helper
     */
    public static void unmakeCheapBeforeDear(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        helper.setBlock(CHEAP_POS, Blocks.COBBLESTONE);
        helper.setBlock(BEHIND_POS, Blocks.COBBLESTONE);
        helper.setBlock(DEAR_POS, Blocks.DIAMOND_BLOCK);
        IGooValueLookup values = GooValues.of(helper.getLevel());
        GooValue cheap = values.lookup(new ItemStack(Blocks.COBBLESTONE));
        GooValue dear = values.lookup(new ItemStack(Blocks.DIAMOND_BLOCK));
        int cheapWork = UnmakeRule.workToUnmake(cheap.totalGoo(), WORK_PER_GOO);
        int dearWork = UnmakeRule.workToUnmake(dear.totalGoo(), WORK_PER_GOO);
        helper.assertTrue(dearWork > HOLD_TICKS, String.format(NOT_DEAR, dearWork));
        ServerPlayer player = streamer(helper);
        KnownRecipes.teachRequires(player, unmake);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.UNSTABLE), UNMAKE.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            int thisTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                if (thisTick == cheapWork - 1) {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, CHEAP_POS);
                    helper.assertBlockPresent(Blocks.COBBLESTONE, BEHIND_POS);
                } else if (thisTick == cheapWork) {
                    helper.assertTrue(helper.getBlockState(CHEAP_POS).isAir(), String.format(NOT_GONE, cheapWork));
                    helper.assertBlockPresent(Blocks.COBBLESTONE, BEHIND_POS);
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, DEAR_POS);
            helper.assertTrue(cheapWork + cheapWork > HOLD_TICKS, String.format(HOLD_TOO_LONG, cheapWork));
            helper.assertBlockPresent(Blocks.COBBLESTONE, BEHIND_POS);
            GooContents expected = UnmakeRule.yieldOf(cheap, YIELD);
            Map<ResourceKey<GooTypeDefinition>, Integer> dropped = droppedGoo(helper);
            helper.assertTrue(expected.getAll().equals(dropped), String.format(WRONG_YIELD, expected.getAll(), dropped));
            helper.succeed();
        });
    }

    /**
     * A mock player holds Unmake at a chicken: the chicken melts away within
     * the hold, leaving goo of its loot and none of its items.
     *
     * @param helper the gametest helper
     */
    public static void unmakeMeltsAMob(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        Mob chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, CHEAP_POS);
        chicken.setNoGravity(true);
        ServerPlayer player = streamer(helper);
        KnownRecipes.teachRequires(player, unmake);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.UNSTABLE), UNMAKE.toString(),
                player.getEyePosition(), player.getEyePosition());
        AtomicBoolean melted = new AtomicBoolean();
        for (int held = 1; held <= MOB_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> {
                if (melted.get()) {
                    return;
                }
                GooStreamHandler.streamTick(player, tick);
                if (chicken.isRemoved()) {
                    melted.set(true);
                    helper.getLevel().getServer().getPlayerList().remove(player);
                    assertMeltedIntoGoo(helper);
                    helper.succeed();
                }
            });
        }
        helper.runAfterDelay(MOB_HOLD_TICKS + 1L, () -> helper.assertTrue(melted.get(), MOB_STANDS));
    }

    /**
     * Asserts the chicken left goo and none of its items.
     *
     * @param helper the gametest helper
     */
    private static void assertMeltedIntoGoo(GameTestHelper helper) {
        AABB reach = new AABB(helper.absolutePos(CHEAP_POS)).inflate(DROP_REACH);
        boolean itemsLeft = helper.getLevel().getEntitiesOfClass(ItemEntity.class, reach).stream()
                .anyMatch(item -> GooStacks.keyOf(item.getItem()) == null);
        helper.assertFalse(itemsLeft, MOB_DROPPED_ITEMS);
        helper.assertFalse(droppedGoo(helper).isEmpty(), MOB_LEFT_NO_GOO);
    }

    /**
     * Sums the goo dropped near the cobblestone, by type.
     *
     * @param helper the gametest helper
     * @return the dropped goo per type
     */
    private static Map<ResourceKey<GooTypeDefinition>, Integer> droppedGoo(GameTestHelper helper) {
        AABB reach = new AABB(helper.absolutePos(CHEAP_POS)).inflate(DROP_REACH);
        Map<ResourceKey<GooTypeDefinition>, Integer> dropped = new HashMap<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, reach)) {
            ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(item.getItem());
            if (type != null) {
                dropped.merge(type, GooStacks.volumeOf(item.getItem()), Integer::sum);
            }
        }
        return dropped;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer streamer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_UP);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
