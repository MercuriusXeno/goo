package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for aeon Rewind: a mock player streams it on a penned cow, which
 * stands frozen while held and goes free once let go; an adult becomes a
 * baby, a baby shrinks into its spawn egg, and no block the stream crosses
 * changes.
 * rewind-fills-while-held
 * rewind-shrinks-adult-to-baby-to-egg
 */
public final class RewindStreamTests {

    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the player, at its height, on a floor the test lays. */
    private static final BlockPos TARGET_POS = STAND_POS.east(3);
    private static final int HELD_GOO = 6;
    /**
     * A max health of one makes aeon_rewind.json's share 5 / pow(1, 0.6) * 1 / 1,
     * five a tick, so twenty held ticks fill the ritual's hundred.
     */
    private static final double QUICK_MAX_HEALTH = 1.0;
    /** Past the twenty ticks the ritual takes at five a tick, short of a second ritual. */
    private static final int HOLD_TICKS = 25;
    /** Partway through the hold, short of the ritual. */
    private static final int MID_HOLD_TICKS = 10;
    /** aeon_rewind.json's regress ticks, the shrink into the egg. */
    private static final int SHRINK_TICKS = 20;
    /** Past RewindEvents' grace after the stream lets go. */
    private static final int RELEASE_TICKS = 6;
    private static final double ITEM_SEARCH_RADIUS = 2.0;
    private static final Identifier AEON_REWIND = Identifier.parse("goo:aeon_rewind");
    private static final String ABILITY_REQUIRED = "Ability registry must hold aeon_rewind";
    private static final String MOB_HAS_MAX_HEALTH = "The cow carries a max health attribute";
    private static final String SHOULD_FREEZE = "A held cow should stand frozen with no AI";
    private static final String SHOULD_STAY_ADULT = "The cow should stay an adult short of the ritual";
    private static final String SHOULD_BE_BABY = "The held adult should have become a baby";
    private static final String SHOULD_GO_FREE = "The released cow should have its AI back";
    private static final String SHOULD_VANISH = "The rewound baby should be gone into its egg";
    private static final String SHOULD_DROP_ONLY_EGG = "Exactly one cow spawn egg should drop; found %s";

    private RewindStreamTests() {
    }

    /**
     * A held adult cow stands frozen partway, becomes a baby once its ritual
     * fills, and goes free a few ticks after the stream lets go.
     *
     * @param helper the gametest helper
     */
    public static void rewindAdultToBaby(GameTestHelper helper) {
        Mob cow = pennedCow(helper, false);
        ServerPlayer player = rewinder(helper, cow);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(MID_HOLD_TICKS, () -> {
            helper.assertTrue(cow.isNoAi(), SHOULD_FREEZE);
            helper.assertFalse(cow.isBaby(), SHOULD_STAY_ADULT);
        });
        helper.runAfterDelay(HOLD_TICKS + 1, () -> helper.assertTrue(cow.isAlive() && cow.isBaby(), SHOULD_BE_BABY));
        helper.runAfterDelay(HOLD_TICKS + RELEASE_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(cow.isNoAi(), SHOULD_GO_FREE);
            helper.succeed();
        });
    }

    /**
     * A held baby cow shrinks into its spawn egg once its ritual fills: after
     * the shrink, one cow spawn egg stands where it stood and the cow is gone.
     *
     * @param helper the gametest helper
     */
    public static void rewindBabyToEgg(GameTestHelper helper) {
        Mob cow = pennedCow(helper, true);
        Vec3 stood = cow.position();
        ServerPlayer player = rewinder(helper, cow);
        hold(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + SHRINK_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeedWhen(() -> {
                helper.assertTrue(cow.isRemoved(), SHOULD_VANISH);
                List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(stood, stood).inflate(ITEM_SEARCH_RADIUS));
                helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(Items.COW_SPAWN_EGG),
                        String.format(SHOULD_DROP_ONLY_EGG, items.stream().map(ItemEntity::getItem).toList()));
            });
        });
    }

    /**
     * Rewind held through a cow's whole ritual leaves every block it crosses
     * as it stood: the cow's glass pen and its stone floor.
     *
     * @param helper the gametest helper
     */
    public static void rewindLeavesBlocks(GameTestHelper helper) {
        Mob cow = pennedCow(helper, false);
        ServerPlayer player = rewinder(helper, cow);
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
     * stands a cow with its AI on in it, whose max health of one fills the
     * ritual in twenty held ticks.
     */
    private static Mob pennedCow(GameTestHelper helper, boolean baby) {
        helper.setBlock(TARGET_POS.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(TARGET_POS.relative(side), Blocks.GLASS);
            helper.setBlock(TARGET_POS.relative(side).above(), Blocks.GLASS);
        }
        Mob cow = helper.spawn(EntityType.COW, TARGET_POS);
        cow.setBaby(baby);
        AttributeInstance maxHealth = cow.getAttribute(Attributes.MAX_HEALTH);
        helper.assertTrue(maxHealth != null, MOB_HAS_MAX_HEALTH);
        maxHealth.setBaseValue(QUICK_MAX_HEALTH);
        cow.setHealth((float) QUICK_MAX_HEALTH);
        return cow;
    }

    /**
     * Streams Rewind every tick of a hold, from the first tick.
     */
    private static void hold(GameTestHelper helper, ServerPlayer player, int ticks) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.AEON), AEON_REWIND.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer rewinder(GameTestHelper helper, Mob cow) {
        AbilityDefinition rewind = AbilityRegistry.of(helper.getLevel()).getAbility(AEON_REWIND);
        helper.assertTrue(rewind != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.AEON, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, rewind);
        return player;
    }
}
