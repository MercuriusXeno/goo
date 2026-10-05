package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.SelfEatRoute;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for the self delivery through the real throw path: a glove use
 * with a self ability starts the player eating the glove, and the cost at
 * stack zero drains and the program runs on the player when the eat
 * finishes; an eat let go before then runs nothing and drains nothing.
 * The mock server player has no connection ticking it, so each test ticks
 * it the way the connection would, through doTick.
 * decision self-delivery-runs-on-player
 * decision self-brew-goos-eat-before-the-effect
 */
public final class SelfDeliveryTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final int NO_ENTITY = -1;
    /** Two thousand mB, two blinks' worth. */
    private static final int HELD_GOO = 2;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final Identifier ENDER_BLINK = Identifier.parse("goo:ender_blink");
    /** The range ender_blink.json's teleport step names. */
    private static final double BLINK_RANGE = 8;
    private static final double MOVE_TOLERANCE = 1e-6;
    private static final Identifier TYPHOON_PROPEL = Identifier.parse("goo:typhoon_propel");
    /** Pitch forty-five degrees above level. */
    private static final float LOOKING_UP = -45f;
    /** Halfway through the eat, when nothing has landed yet. */
    private static final int MID_EAT = SelfEatRoute.EAT_TICKS / 2;
    /** The tick after the eat's last tick, when the finish has run. */
    private static final int AFTER_EAT = SelfEatRoute.EAT_TICKS + 1;
    /** The tick a letting-go player releases the use at. */
    private static final int RELEASE_AT = 5;
    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_START_EATING = "Invoking a self ability should start the player eating";
    private static final String SHOULD_STOP_EATING = "A released eat should leave the player out of the using state";
    private static final String SHOULD_HOLD_MID_EAT = "Mid-eat the player should stand where it stood, moved %.3f";
    private static final String SHOULD_DRAIN_NOTHING_MID_EAT = "Mid-eat no goo should drain, drained %d";
    private static final String SHOULD_BLINK_EAST = "The player should move %.1f east when the eat finishes, moved %.3f";
    private static final String SHOULD_DRAIN_COST = "The finish should drain the stack-zero cost of %d mB, drained %d";
    private static final String SHOULD_RUN_NOTHING = "A released eat should leave the player where it stood, moved %.3f";
    private static final String SHOULD_DRAIN_NOTHING = "A released eat should drain nothing, drained %d";
    private static final String SHOULD_PROPEL_UP_AND_EAST = "The finish should push the player up and east, moved x %.3f y %.3f";
    private static final String SHOULD_NOT_EAT_UNKNOWN = "A refused blink should start no eat";
    private static final String SHOULD_STAY_REFUSED = "A refused blink should leave the player where it stood, moved ";
    private static final String SHOULD_DRAIN_NOTHING_REFUSED = "A refused blink should drain nothing, drained ";

    private SelfDeliveryTests() {
    }

    /**
     * A mock player facing east invokes ender blink: it starts eating, stands
     * put with its goo whole mid-eat, and moves the blink's range east with
     * the cost drained when the eat finishes.
     *
     * @param helper the gametest helper
     */
    public static void enderBlink(GameTestHelper helper) {
        AbilityDefinition blink = requireAbility(helper, ENDER_BLINK);
        ServerPlayer player = invoker(helper, GooTypes.ENDER, ENDER_BLINK);
        KnownRecipes.teachRequires(player, blink);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = enderHeld(player);

        invoke(player, GooTypes.ENDER, ENDER_BLINK);

        helper.assertTrue(player.isUsingItem(), SHOULD_START_EATING);
        eatThrough(helper, player, SelfEatRoute.EAT_TICKS);
        helper.runAfterDelay(MID_EAT, () -> assertNothingLanded(helper, player, xBefore, heldBefore));
        helper.runAfterDelay(AFTER_EAT, () -> {
            double moved = player.getX() - xBefore;
            int drained = heldBefore - enderHeld(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(moved - BLINK_RANGE) < MOVE_TOLERANCE,
                    String.format(SHOULD_BLINK_EAST, BLINK_RANGE, moved));
            helper.assertTrue(drained == blink.cost(), String.format(SHOULD_DRAIN_COST, blink.cost(), drained));
            helper.succeed();
        });
    }

    /**
     * A mock player invokes ender blink and lets go of the use before the eat
     * finishes: the eat ends, the player stays put and no goo drains.
     *
     * @param helper the gametest helper
     */
    public static void blinkLetGoMidEatRunsNothing(GameTestHelper helper) {
        AbilityDefinition blink = requireAbility(helper, ENDER_BLINK);
        ServerPlayer player = invoker(helper, GooTypes.ENDER, ENDER_BLINK);
        KnownRecipes.teachRequires(player, blink);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = enderHeld(player);

        invoke(player, GooTypes.ENDER, ENDER_BLINK);

        helper.assertTrue(player.isUsingItem(), SHOULD_START_EATING);
        eatThrough(helper, player, SelfEatRoute.EAT_TICKS);
        helper.runAfterDelay(RELEASE_AT, player::releaseUsingItem);
        helper.runAfterDelay(AFTER_EAT, () -> {
            double moved = player.getX() - xBefore;
            int drained = heldBefore - enderHeld(player);
            boolean using = player.isUsingItem();
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertFalse(using, SHOULD_STOP_EATING);
            helper.assertTrue(Math.abs(moved) < MOVE_TOLERANCE, String.format(SHOULD_RUN_NOTHING, moved));
            helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING, drained));
            helper.succeed();
        });
    }

    /**
     * A mock player looking up and east invokes typhoon propulsion: it stands
     * put mid-eat, and when the eat finishes the push along its look carries
     * it up and east.
     *
     * @param helper the gametest helper
     */
    public static void typhoonPropel(GameTestHelper helper) {
        AbilityDefinition propel = requireAbility(helper, TYPHOON_PROPEL);
        ServerPlayer player = invoker(helper, GooTypes.TYPHOON, TYPHOON_PROPEL);
        KnownRecipes.teachRequires(player, propel);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_UP);
        double xBefore = player.getX();
        // The mock settles onto the bay floor during the eat, so the rise is measured from mid-eat.
        double[] yMidEat = new double[1];
        int heldBefore = typhoonHeld(player);

        invoke(player, GooTypes.TYPHOON, TYPHOON_PROPEL);

        helper.assertTrue(player.isUsingItem(), SHOULD_START_EATING);
        eatThrough(helper, player, SelfEatRoute.EAT_TICKS);
        helper.runAfterDelay(MID_EAT, () -> {
            yMidEat[0] = player.getY();
            assertNothingLanded(helper, player, xBefore, heldBefore);
        });
        helper.runAfterDelay(AFTER_EAT, () -> {
            double movedX = player.getX() - xBefore;
            double movedY = player.getY() - yMidEat[0];
            int drained = heldBefore - typhoonHeld(player);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(movedX > MOVE_TOLERANCE && movedY > MOVE_TOLERANCE,
                    String.format(SHOULD_PROPEL_UP_AND_EAST, movedX, movedY));
            helper.assertTrue(drained == propel.cost(), String.format(SHOULD_DRAIN_COST, propel.cost(), drained));
            helper.succeed();
        });
    }

    /**
     * A mock player who has never melted an ender pearl invokes ender blink:
     * the throw is refused whole, so no eat starts, the player stays put and
     * no goo drains (decision ability-hidden-until-recipes-known).
     *
     * @param helper the gametest helper
     */
    public static void gatedBlinkRefusedWithoutTheRecipe(GameTestHelper helper) {
        ServerPlayer player = invoker(helper, GooTypes.ENDER, ENDER_BLINK);
        player.setYRot(FACING_EAST);
        player.setXRot(0);
        double xBefore = player.getX();
        int heldBefore = enderHeld(player);

        invoke(player, GooTypes.ENDER, ENDER_BLINK);

        boolean using = player.isUsingItem();
        double moved = player.getX() - xBefore;
        int drained = heldBefore - enderHeld(player);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertFalse(using, SHOULD_NOT_EAT_UNKNOWN);
        helper.assertTrue(moved == 0, SHOULD_STAY_REFUSED + moved);
        helper.assertTrue(drained == 0, SHOULD_DRAIN_NOTHING_REFUSED + drained);
        helper.succeed();
    }

    private static void invoke(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, Identifier ability) {
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(gooType), NO_ENTITY,
                player.blockPosition(), NO_ENTITY, false, ability.toString(), player.getEyePosition()));
    }

    /**
     * Ticks the player the way its connection would, once per tick for the
     * eat's duration, so the use counts down and finishes on the server.
     *
     * @param helper the gametest helper
     * @param player the eating player
     * @param ticks  how many ticks to carry the player through
     */
    private static void eatThrough(GameTestHelper helper, ServerPlayer player, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) {
            helper.runAfterDelay(tick, player::doTick);
        }
    }

    private static void assertNothingLanded(GameTestHelper helper, ServerPlayer player, double xBefore,
            int heldBefore) {
        double moved = player.getX() - xBefore;
        int drained = heldBefore - GooSourceScanner.aggregateAvailable(player).values().stream()
                .mapToInt(Integer::intValue).sum();
        helper.assertTrue(Math.abs(moved) < MOVE_TOLERANCE, String.format(SHOULD_HOLD_MID_EAT, moved));
        helper.assertTrue(drained == 0, String.format(SHOULD_DRAIN_NOTHING_MID_EAT, drained));
    }

    private static AbilityDefinition requireAbility(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }

    private static int enderHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ENDER, 0);
    }

    private static int typhoonHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.TYPHOON, 0);
    }

    /**
     * A mock player standing in the bay, holding a glove whose selection
     * names the ability, as a real glove does when its use reaches the
     * server, with two costs of the type in its inventory.
     *
     * @param helper  the gametest helper
     * @param gooType the ability's goo type
     * @param ability the ability the glove selects
     * @return the player
     */
    static ServerPlayer invoker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
            Identifier ability) {
        ServerPlayer player = invoker(helper, gooType);
        GooGloveItem.setSelection(player.getMainHandItem(), GloveSelection.ofAbility(gooType, ability));
        return player;
    }

    /**
     * A mock player standing in the bay, holding a glove with no selection
     * and two costs of the type in its inventory.
     *
     * @param helper  the gametest helper
     * @param gooType the goo type the player holds
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    static ServerPlayer invoker(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
