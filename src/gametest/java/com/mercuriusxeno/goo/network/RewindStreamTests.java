package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for yore Rewind: a mock player aims at an ordinary penned pig at
 * its own full health and streams Rewind from the glove hand, as a player
 * does; the pig stands frozen while held and goes free once let go; an adult
 * becomes a baby, a baby shrinks into its spawn egg, and no block the stream
 * crosses changes.
 * rewind-fills-while-held
 * rewind-shrinks-adult-to-baby-to-egg
 * rewind-ships-on-yore
 */
public final class RewindStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a floor the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    private static final int HELD_GOO = 6;
    /** Past the eighty ticks yore_rewind.json's flat share of 1.25 a tick takes to fill the hundred. */
    private static final int HOLD_TICKS = 85;
    /** Halfway through the hold, short of the ritual. */
    private static final int MID_HOLD_TICKS = 40;
    /** yore_rewind.json's regress ticks, the shrink into the egg. */
    private static final int SHRINK_TICKS = 20;
    /** Past RewindEvents' grace after the stream lets go. */
    private static final int RELEASE_TICKS = 6;
    /** The glove hand sits this far right of the eye and below it, as a player's glove does. */
    private static final double HAND_RIGHT = 0.5;
    private static final double HAND_DOWN = 0.4;
    private static final double ITEM_SEARCH_RADIUS = 2.0;
    private static final Identifier YORE_REWIND = Identifier.parse("goo:yore_rewind");
    private static final String ABILITY_REQUIRED = "Ability registry must hold yore_rewind";
    private static final String SHOULD_FREEZE = "A held pig should stand frozen with no AI";
    private static final String SHOULD_STAY_ADULT = "The pig should stay an adult short of the ritual";
    private static final String SHOULD_BE_BABY = "The held adult should have become a baby; counters %s";
    private static final String SHOULD_GO_FREE = "The released pig should have its AI back";
    private static final String SHOULD_VANISH = "The rewound baby should be gone into its egg";
    private static final String SHOULD_DROP_ONLY_EGG = "Exactly one pig spawn egg should drop; found %s";

    private RewindStreamTests() {
    }

    /**
     * A held adult pig stands frozen halfway, becomes a baby once its ritual
     * fills after about four seconds, and goes free a few ticks after the
     * stream lets go.
     *
     * @param helper the gametest helper
     */
    public static void rewindAdultToBaby(GameTestHelper helper) {
        Mob pig = pennedPig(helper, false);
        ServerPlayer player = rewinder(helper, pig);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(MID_HOLD_TICKS, () -> {
            helper.assertTrue(pig.isNoAi(), SHOULD_FREEZE);
            helper.assertFalse(pig.isBaby(), SHOULD_STAY_ADULT);
        });
        helper.runAfterDelay(HOLD_TICKS + 1, () -> helper.assertTrue(pig.isAlive() && pig.isBaby(),
                String.format(SHOULD_BE_BABY, pig.getData(GooAttachments.ENTITY_COUNTERS))));
        helper.runAfterDelay(HOLD_TICKS + RELEASE_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(pig.isNoAi(), SHOULD_GO_FREE);
            helper.succeed();
        });
    }

    /**
     * A held baby pig shrinks into its spawn egg once its ritual fills: after
     * the shrink, one pig spawn egg stands where it stood and the pig is gone.
     *
     * @param helper the gametest helper
     */
    public static void rewindBabyToEgg(GameTestHelper helper) {
        Mob pig = pennedPig(helper, true);
        Vec3 stood = pig.position();
        ServerPlayer player = rewinder(helper, pig);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + SHRINK_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeedWhen(() -> {
                helper.assertTrue(pig.isRemoved(), SHOULD_VANISH);
                List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(stood, stood).inflate(ITEM_SEARCH_RADIUS));
                helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(Items.PIG_SPAWN_EGG),
                        String.format(SHOULD_DROP_ONLY_EGG, items.stream().map(ItemEntity::getItem).toList()));
            });
        });
    }

    /**
     * Rewind held through a pig's whole ritual leaves every block it crosses
     * as it stood: the pig's glass pen and its stone floor.
     *
     * @param helper the gametest helper
     */
    public static void rewindLeavesBlocks(GameTestHelper helper) {
        Mob pig = pennedPig(helper, false);
        ServerPlayer player = rewinder(helper, pig);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertBlockPresent(Blocks.STONE, TARGET_POS.below());
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.assertBlockPresent(Blocks.GLASS, TARGET_POS.relative(side));
                helper.assertBlockPresent(Blocks.GLASS, TARGET_POS.relative(side).above());
            }
            helper.succeed();
        });
    }

    /**
     * Floors the target cell with stone, rings it with glass two high and
     * stands an ordinary pig in it, at its own full health with its AI on.
     */
    private static Mob pennedPig(GameTestHelper helper, boolean baby) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(TARGET_POS.relative(side), Blocks.GLASS);
            helper.setBlock(TARGET_POS.relative(side).above(), Blocks.GLASS);
        }
        Mob pig = helper.spawn(EntityType.PIG, TARGET_POS);
        pig.setBaby(baby);
        return pig;
    }

    /**
     * Streams Rewind every tick of a hold, from the first tick, out of the
     * glove hand beside and below the eye, as a player's glove sends it.
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int ticks) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 hand = player.getEyePosition().add(right.scale(HAND_RIGHT)).subtract(0, HAND_DOWN, 0);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.YORE), YORE_REWIND.toString(),
                hand, player.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer rewinder(GameTestHelper helper, Mob target) {
        AbilityDefinition rewind = AbilityRegistry.of(helper.getLevel()).getAbility(YORE_REWIND);
        helper.assertTrue(rewind != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, target.getBoundingBox().getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.YORE, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, rewind);
        return player;
    }
}
