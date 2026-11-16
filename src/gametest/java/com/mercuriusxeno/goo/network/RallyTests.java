package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.ability.zoo.RallyEvents;
import com.mercuriusxeno.goo.ability.zoo.RallyFollow;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Zoo's Rally: the one peaceful mob a thrown Rally lands on
 * takes the rally, strikes the mob that hurt the caster, and holds the rally
 * until its rallier strikes it
 * (decisions zoo-rally-arms-the-peaceful, rally-is-a-mob-effect-on-one-target).
 */
@RegistersGameTests
public final class RallyTests {
    private static final String RALLY_COW_ATTACKS_THE_ZOMBIE = "rally_cow_attacks_the_zombie";
    private static final String RALLY_SURVIVES_A_RELOAD = "rally_survives_a_reload";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RALLY_COW_ATTACKS_THE_ZOMBIE, RallyTests::rallyCowAttacksTheZombie);
        GooTestFunctions.registerTest(registrar, RALLY_SURVIVES_A_RELOAD, RallyTests::rallySurvivesAReload);
        GooTestFunctions.registerTest(registrar, RALLY_COW_STRIKES_NOTHING_THROUGH_A_WALL,
                RallyTests::rallyCowStrikesNothingThroughAWall);
        GooTestFunctions.registerTest(registrar, RALLY_TAKES_ONE_OF_TWO_COWS, RallyTests::rallyTakesOneOfTwoCows);
        GooTestFunctions.registerTest(registrar, RALLY_HOLDS_PAST_THE_OLD_RALLY, RallyTests::rallyHoldsPastTheOldRally);
        GooTestFunctions.registerTest(registrar, RALLY_ENDS_ON_THE_RALLIERS_HIT, RallyTests::rallyEndsOnTheRalliersHit);
        GooTestFunctions.registerTest(registrar, RALLIERS_PUNCH_LEAVES_THE_COW_ALIVE,
                RallyTests::ralliersPunchLeavesTheCowAlive);
        GooTestFunctions.registerTest(registrar, SECOND_PUNCH_MAKES_THE_FREED_COW_A_FOE,
                RallyTests::secondPunchMakesTheFreedCowAFoe);
        GooTestFunctions.registerTest(registrar, RALLY_KEEPS_A_HURT_COWS_HEALTH, RallyTests::rallyKeepsAHurtCowsHealth);
        GooTestFunctions.registerTest(registrar, RALLY_COW_TAKES_THE_SIDE_ITS_RALLIER_WALKS_TOWARD,
                RallyTests::rallyCowTakesTheSideItsRallierWalksToward);
        GooTestFunctions.registerTest(registrar, RALLY_COW_KEEPS_WALKING_ONCE_ITS_RALLIER_STOPS,
                RallyTests::rallyCowKeepsWalkingOnceItsRallierStops);
        GooTestFunctions.registerTest(registrar, RALLY_COW_IGNORES_THE_WHEAT_IT_WOULD_FOLLOW,
                RallyTests::rallyCowIgnoresTheWheatItWouldFollow);
        GooTestFunctions.registerTest(registrar, RALLY_COW_WEARS_HALF_ITS_RALLIERS_ARMOR,
                RallyTests::rallyCowWearsHalfItsRalliersArmor);
        GooTestFunctions.registerTest(registrar, RALLIED_COW_HEALS_BACK_TO_ITS_RAISED_MAX_HEALTH,
                RallyTests::ralliedCowHealsBackToItsRaisedMaxHealth);
        GooTestFunctions.registerTest(registrar, RALLIED_COW_KEEPS_ITS_RAISED_MAX_HEALTH_AFTER_A_HIT,
                RallyTests::ralliedCowKeepsItsRaisedMaxHealthAfterAHit);
        GooTestFunctions.registerTest(registrar, RALLY_AGAIN_RELEASES_THE_COW, RallyTests::rallyAgainReleasesTheCow);
        GooTestFunctions.registerTest(registrar, RALLY_RELEASE_DEALS_NO_DAMAGE, RallyTests::rallyReleaseDealsNoDamage);
        GooTestFunctions.registerTest(registrar, RALLY_BY_A_SECOND_PLAYER_LEAVES_THE_RALLY_AS_IT_STOOD,
                RallyTests::rallyByASecondPlayerLeavesTheRallyAsItStood);
        GooTestFunctions.registerTest(registrar, CHARM_BY_A_SECOND_PLAYER_KEEPS_THE_FIRST_CHARMER,
                RallyTests::charmByASecondPlayerKeepsTheFirstCharmer);
        GooTestFunctions.registerTest(registrar, CHARM_LEAVES_A_RALLIED_COW_ITS_RALLY,
                RallyTests::charmLeavesARalliedCowItsRally);
        GooTestFunctions.registerTest(registrar, RALLY_LEAVES_A_CHARMED_COW_ITS_CHARM,
                RallyTests::rallyLeavesACharmedCowItsCharm);
        GooTestFunctions.registerTest(registrar, RELEASED_COW_TAKES_A_FRESH_RALLY,
                RallyTests::releasedCowTakesAFreshRally);
        GooTestFunctions.registerTest(registrar, RALLY_RELEASE_THROW_DRAINS_NO_ZOO_GOO,
                RallyTests::rallyReleaseThrowDrainsNoZooGoo);
        GooTestFunctions.registerTest(registrar, RALLY_RELEASE_THROW_NEEDS_NO_ZOO_GOO_HELD,
                RallyTests::rallyReleaseThrowNeedsNoZooGooHeld);
        GooTestFunctions.registerTest(registrar, RALLY_THROW_AT_ANOTHER_MOB_PAYS_ITS_COST,
                RallyTests::rallyThrowAtAnotherMobPaysItsCost);
        GooTestFunctions.registerTest(registrar, CHARMED_ZOMBIE_SPARES_A_RALLIED_COW,
                RallyTests::charmedZombieSparesARalliedCow);
        GooTestFunctions.registerTest(registrar, RALLIED_COW_SPARES_A_ZOMBIE_CHARMED_AFTER_IT_STRUCK,
                RallyTests::ralliedCowSparesAZombieCharmedAfterItStruck);
        GooTestFunctions.registerTest(registrar, CHARMED_ZOMBIE_AND_RALLIED_COW_LAND_NO_HIT_ON_EACH_OTHER,
                RallyTests::charmedZombieAndRalliedCowLandNoHitOnEachOther);
        GooTestFunctions.registerTest(registrar, RALLIED_COWS_TAKE_SLOTS_IN_RALLY_ORDER,
                RallyTests::ralliedCowsTakeSlotsInRallyOrder);
        GooTestFunctions.registerTest(registrar, RALLIED_COWS_SWAP_SLOTS_IN_THE_OTHER_RALLY_ORDER,
                RallyTests::ralliedCowsSwapSlotsInTheOtherRallyOrder);
        GooTestFunctions.registerTest(registrar, SLOW_AND_FAST_MINIONS_HOLD_THEIR_SLOTS,
                RallyTests::slowAndFastMinionsHoldTheirSlots);
        GooTestFunctions.registerTest(registrar, RALLIED_COW_WALKS_AROUND_ITS_RALLIER,
                RallyTests::ralliedCowWalksAroundItsRallier);
    }

    private static final String CHARMED_ZOMBIE_AND_RALLIED_COW_LAND_NO_HIT_ON_EACH_OTHER =
            "charmed_zombie_and_rallied_cow_land_no_hit_on_each_other";
    private static final String HIT_LANDED = "%s should stand at the %s it read before the hit, reads %s, last hit %s";
    private static final String MINIONS_FREED = "The cow should still hold its rally and the zombie its charm";

    /**
     * A charmed zombie strikes a rallied cow with its own melee attack, and
     * the cow strikes the zombie with the rally's own attack: each stands at
     * the health it read before the hit, the cow still rallied and the zombie
     * still charmed
     * (decision diagnose-and-correct-minions-killing-each-other).
     *
     * @param helper the gametest helper
     */
    public static void charmedZombieAndRalliedCowLandNoHitOnEachOther(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        Zombie zombie = charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS));
        charm(helper, zombie, caster);
        float cowBefore = cow.getHealth();
        float zombieBefore = zombie.getHealth();

        zombie.doHurtTarget(helper.getLevel(), cow);
        zombie.hurtServer(helper.getLevel(), cow.damageSources().mobAttack(cow), cow.getData(GooAttachments.RALLIED)
                .damage());

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(cow.getHealth() == cowBefore,
                String.format(HIT_LANDED, "The rallied cow", cowBefore, cow.getHealth(), lastHitOf(cow)));
        helper.assertTrue(zombie.getHealth() == zombieBefore,
                String.format(HIT_LANDED, "The charmed zombie", zombieBefore, zombie.getHealth(), lastHitOf(zombie)));
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED) && zombie.hasData(GooAttachments.CHARMED), MINIONS_FREED);
        helper.succeed();
    }

    private static final String CHARMED_ZOMBIE_SPARES_A_RALLIED_COW = "charmed_zombie_spares_a_rallied_cow";
    private static final String RALLIED_COW_SPARES_A_ZOMBIE_CHARMED_AFTER_IT_STRUCK =
            "rallied_cow_spares_a_zombie_charmed_after_it_struck";
    private static final Identifier HEX_CHARM = Identifier.fromNamespaceAndPath(Goo.MODID, "hex_charm");
    /**
     * The max health a charmed zombie stands at: half a zombie's twenty, low enough that the charm's chance,
     * which falls as max health rises, stands whole.
     */
    private static final double CERTAIN_CHARM_HEALTH = 10;
    /** Ticks a minion set on a minion is watched, past two of a zombie's melee swings and two of a rally's strikes. */
    private static final int MINION_WATCH_TICKS = 40;
    private static final String NOT_CHARMED = "The zombie should hold the charm";
    private static final String ZOMBIE_KEPT_THE_COW = "The charmed zombie should drop the rallied cow it was set on";
    private static final String COW_HURT = "The rallied cow should stand at the %s it read when set upon, reads %s, last hit %s";
    private static final String COW_KEPT_THE_ZOMBIE = "The rallied cow should take no charmed zombie as its foe";
    private static final String ZOMBIE_HURT =
            "The charmed zombie should stand at the %s it read when charmed, reads %s, last hit %s";

    /**
     * A free-willed zombie charmed by the rallier of a cow, set on that
     * rallied cow: 40 ticks on, the zombie targets the cow no longer and the
     * cow's health reads what it read when the zombie was set on it
     * (decision diagnose-and-correct-minions-killing-each-other).
     *
     * @param helper the gametest helper
     */
    public static void charmedZombieSparesARalliedCow(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        Zombie zombie = charmable(helper.spawn(EntityType.ZOMBIE, ZOMBIE_POS));
        charm(helper, zombie, caster);
        zombie.setTarget(cow);
        float before = cow.getHealth();
        helper.runAfterDelay(MINION_WATCH_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(zombie.getTarget() != cow, ZOMBIE_KEPT_THE_COW);
            helper.assertTrue(cow.getHealth() == before, String.format(COW_HURT, before, cow.getHealth(),
                    lastHitOf(cow)));
            helper.succeed();
        });
    }

    /**
     * A zombie strikes the rallier of a cow and the rallier strikes it back,
     * so it is both the mob that last hurt the rallier and the one the
     * rallier last struck, and then the rallier charms it: 40 ticks on, the
     * rallied cow targets the zombie no longer and the zombie's health reads
     * what it read when the charm landed
     * (decision diagnose-and-correct-minions-killing-each-other).
     *
     * @param helper the gametest helper
     */
    public static void ralliedCowSparesAZombieCharmedAfterItStruck(GameTestHelper helper) {
        ServerPlayer caster = survivalRallier(helper);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        Zombie zombie = charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS));
        caster.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), ZOMBIE_HIT);
        caster.attack(zombie);
        charm(helper, zombie, caster);
        helper.assertTrue(zombie.hasData(GooAttachments.CHARMED), NOT_CHARMED);
        float charmed = zombie.getHealth();
        helper.runAfterDelay(MINION_WATCH_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.getTarget() != zombie, COW_KEPT_THE_ZOMBIE);
            helper.assertTrue(zombie.getHealth() == charmed, String.format(ZOMBIE_HURT, charmed, zombie.getHealth(),
                    lastHitOf(zombie)));
            helper.succeed();
        });
    }

    /**
     * Stands a zombie at a max health its charm's chance stands whole at,
     * helmeted against the open sky's sunburn, which would pass for a hit.
     *
     * @param zombie the zombie
     * @return the zombie, at full health
     */
    private static Zombie charmable(Zombie zombie) {
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CERTAIN_CHARM_HEALTH);
        zombie.setHealth(zombie.getMaxHealth());
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Lands one Hex Charm goo on a mob as the charmer's throw, through the
     * scheduler a thrown goo's impact runs.
     *
     * @param helper  the gametest helper
     * @param mob     the mob struck
     * @param charmer the throwing player
     */
    private static void charm(GameTestHelper helper, Mob mob, ServerPlayer charmer) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_CHARM);
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), charmer, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, HEX_CHARM.toString()));
    }

    private static String lastHitOf(Mob mob) {
        return mob.getLastDamageSource() == null ? "none" : mob.getLastDamageSource().getMsgId();
    }

    private static final String RALLIED_COW_HEALS_BACK_TO_ITS_RAISED_MAX_HEALTH =
            "rallied_cow_heals_back_to_its_raised_max_health";
    private static final String RALLIED_COW_KEEPS_ITS_RAISED_MAX_HEALTH_AFTER_A_HIT =
            "rallied_cow_keeps_its_raised_max_health_after_a_hit";
    /**
     * A zombie's hit leaving the rallied cow at 5, under a cow's own max
     * health: a rally heals none of the cow's 10, so the hit lands on 10.
     */
    private static final float DEEP_HIT = 5f;
    /** Ticks after the hit the cow's max health is read again, past the hurt frames and any regrowth. */
    private static final int AFTER_THE_HIT_TICKS = 40;
    /** West of the cow, two blocks off, where the healer stands facing it. */
    private static final BlockPos HEALER_POS = new BlockPos(0, 1, 3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward a cow's middle two blocks off. */
    private static final float LOOKING_AT_THE_COW = 20f;
    /** Jelly goo enough to stream Heal past every tick the test runs, in thousands. */
    private static final int HEALER_GOO = 30;
    private static final Identifier JELLY_HEAL = Identifier.parse("goo:jelly_heal");
    private static final String HEAL_REQUIRED = "Ability registry must hold jelly_heal";
    private static final String WOUND_TOO_SHALLOW =
            "The hit should leave the cow under its unrallied max health %s, reads %s";
    private static final String HEALED_SHORT =
            "The healed cow should pass its unrallied max health %s and reach its raised max health %s, "
                    + "reads %s of max %s";
    private static final String MAX_HEALTH_DROPPED =
            "The struck cow's max health should still read the raised %s %s, reads %s";

    /**
     * A rallied cow wounded under a cow's own max health and healed by
     * Jelly's Heal stream heals past that max, up to the max health rally
     * raised (decision diagnose-and-correct-rally-max-health-lost-after-damage).
     *
     * @param helper the gametest helper
     */
    public static void ralliedCowHealsBackToItsRaisedMaxHealth(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        // a cow with no free will still walks to its rallier, and would leave the heal's cone
        cow.setNoAi(true);
        float unrallied = cow.getMaxHealth();
        rally(helper, cow, caster);
        float raised = cow.getMaxHealth();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        cow.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), DEEP_HIT);
        // an undead zombie in the heal's cone would take the heal as harm
        zombie.discard();
        helper.assertTrue(cow.getHealth() < unrallied, String.format(WOUND_TOO_SHALLOW, unrallied, cow.getHealth()));
        ServerPlayer healer = healer(helper);
        helper.onEachTick(() -> GooStreamHandler.streamTick(healer, GooStreamPayload.unplaned(
                GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(), healer.getEyePosition(),
                healer.getEyePosition())));
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.getHealth() > unrallied && cow.getHealth() >= raised - ATTRIBUTE_TOLERANCE,
                    String.format(HEALED_SHORT, unrallied, raised, cow.getHealth(), cow.getMaxHealth()));
            helper.getLevel().getServer().getPlayerList().remove(healer);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    private static final String RALLY_RELEASE_THROW_DRAINS_NO_ZOO_GOO = "rally_release_throw_drains_no_zoo_goo";
    private static final String RALLY_RELEASE_THROW_NEEDS_NO_ZOO_GOO_HELD =
            "rally_release_throw_needs_no_zoo_goo_held";
    private static final String RALLY_THROW_AT_ANOTHER_MOB_PAYS_ITS_COST = "rally_throw_at_another_mob_pays_its_cost";
    private static final int NO_FACE = -1;
    private static final String RELEASE_DRAINED = "The release throw should drain no zoo goo: held %s, then %s";
    private static final String THROW_UNPAID = "A Rally throw at %s should drain Rally's cost %s: held %s, then %s";

    /**
     * The rallier throws Rally at its rallied cow through the throw handler:
     * the rallier's zoo goo stands as it stood, and the cow's rally ends
     * (decision rally-release-cast-costs-nothing).
     *
     * @param helper the gametest helper
     */
    public static void rallyReleaseThrowDrainsNoZooGoo(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        int before = zooHeld(caster);

        GooThrowHandler.execute(caster, rallyThrow(caster, cow));

        int after = zooHeld(caster);
        helper.assertTrue(after == before, String.format(RELEASE_DRAINED, before, after));
        helper.succeedWhen(() -> {
            helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RELEASE_LEFT_THE_RALLY);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A rallied cow struck by a zombie reads the max health rally raised on
     * the tick of the hit and 40 ticks after it
     * (decision diagnose-and-correct-rally-max-health-lost-after-damage).
     *
     * @param helper the gametest helper
     */
    public static void ralliedCowKeepsItsRaisedMaxHealthAfterAHit(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        float raised = cow.getMaxHealth();
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.setNoAi(true);

        cow.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), LIGHT_HIT);

        helper.assertTrue(cow.getMaxHealth() == raised,
                String.format(MAX_HEALTH_DROPPED, "on the tick of the hit", raised, cow.getMaxHealth()));
        helper.runAfterDelay(AFTER_THE_HIT_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.getMaxHealth() == raised,
                    String.format(MAX_HEALTH_DROPPED, AFTER_THE_HIT_TICKS + " ticks after the hit", raised,
                            cow.getMaxHealth()));
            helper.succeed();
        });
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer healer(GameTestHelper helper) {
        AbilityDefinition heal = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(heal != null, HEAL_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(HEALER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_THE_COW);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.JELLY, HEALER_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, heal);
        return player;
    }

    /**
     * A rallier holding no zoo goo throws Rally at its rallied cow through
     * the throw handler, and the cow's rally ends
     * (decision rally-release-cast-costs-nothing).
     *
     * @param helper the gametest helper
     */
    public static void rallyReleaseThrowNeedsNoZooGooHeld(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        caster.getInventory().clearContent();
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));

        GooThrowHandler.execute(caster, rallyThrow(caster, cow));

        helper.succeedWhen(() -> {
            helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RELEASE_LEFT_THE_RALLY);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A Rally throw at an unrallied cow, and one at a cow another player
     * rallied, each drain Rally's cost from the thrower
     * (decision rally-release-cast-costs-nothing).
     *
     * @param helper the gametest helper
     */
    public static void rallyThrowAtAnotherMobPaysItsCost(GameTestHelper helper) {
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        ServerPlayer otherRallier = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow unrallied = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Cow othersCow = helper.spawnWithNoFreeWill(EntityType.COW, BESIDE_COW_POS);
        rally(helper, othersCow, otherRallier);
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(ZOO_RALLY);
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        int cost = ability.cost().exactIn(GooValues.of(helper.getLevel())).floor();

        assertThrowPays(helper, thrower, unrallied, "an unrallied cow", cost);
        assertThrowPays(helper, thrower, othersCow, "a cow another player rallied", cost);

        helper.getLevel().getServer().getPlayerList().remove(thrower);
        helper.getLevel().getServer().getPlayerList().remove(otherRallier);
        helper.succeed();
    }

    private static void assertThrowPays(GameTestHelper helper, ServerPlayer thrower, Mob mob, String kind, int cost) {
        int before = zooHeld(thrower);
        GooThrowHandler.execute(thrower, rallyThrow(thrower, mob));
        int after = zooHeld(thrower);
        helper.assertTrue(before - after == cost, String.format(THROW_UNPAID, kind, cost, before, after));
    }

    private static GooThrowPayload rallyThrow(ServerPlayer thrower, Mob target) {
        return new GooThrowPayload(GooTypes.id(GooTypes.ZOO), target.getId(), target.blockPosition(), NO_FACE, false,
                ZOO_RALLY.toString(), thrower.getEyePosition());
    }

    private static int zooHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ZOO, 0);
    }

    private static final String RALLY_AGAIN_RELEASES_THE_COW = "rally_again_releases_the_cow";
    private static final String RALLY_RELEASE_DEALS_NO_DAMAGE = "rally_release_deals_no_damage";
    private static final String RALLY_BY_A_SECOND_PLAYER_LEAVES_THE_RALLY_AS_IT_STOOD =
            "rally_by_a_second_player_leaves_the_rally_as_it_stood";
    private static final String CHARM_BY_A_SECOND_PLAYER_KEEPS_THE_FIRST_CHARMER =
            "charm_by_a_second_player_keeps_the_first_charmer";
    private static final String CHARM_LEAVES_A_RALLIED_COW_ITS_RALLY = "charm_leaves_a_rallied_cow_its_rally";
    private static final String RALLY_LEAVES_A_CHARMED_COW_ITS_CHARM = "rally_leaves_a_charmed_cow_its_charm";
    private static final String RELEASED_COW_TAKES_A_FRESH_RALLY = "released_cow_takes_a_fresh_rally";
    private static final String RELEASE_LEFT_THE_RALLY = "The rallier's second Rally should release the cow";
    private static final String RELEASE_LEFT_A_MODIFIER = "The released cow's %s should carry no %s";
    private static final String RELEASE_HEALTH = "The released cow's health should be %s, stands %s";
    private static final String RELEASE_HURT_THE_COW = "The release should record no damage on the cow, recorded %s";
    private static final String SECOND_PLAYER_CHANGED_THE_RALLY =
            "A second player's Rally should leave the cow's rally as it stood, %s, reads %s";
    private static final String SECOND_PLAYER_TOOK_THE_CHARM =
            "A second player's Charm should leave the first player as the zombie's charmer";
    private static final String CHARM_TOOK_A_RALLIED_COW =
            "%s Charm should leave the rallied cow its rally for the first player and no charm";
    private static final String RALLY_TOOK_A_CHARMED_COW =
            "%s Rally should leave the charmed cow its charm for the first player, no rally and max health %s, reads %s";
    /** A cow's base max health low enough that, rallied, Charm's chance pow(20 / max_health, 1.5) is certain. */
    private static final double CERTAIN_CHARM_COW_HEALTH = 4;
    private static final String NOT_RALLIED_FRESH = "The third Rally should rally the released cow for its caster";

    /**
     * The rallier casts Rally again on its rallied cow, and the cow holds no
     * rally and none of the rally's modifiers
     * (decision rally-cast-on-your-rallied-mob-releases-it).
     *
     * @param helper the gametest helper
     */
    public static void rallyAgainReleasesTheCow(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);

        rally(helper, cow, caster);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RELEASE_LEFT_THE_RALLY);
        assertNoModifier(helper, cow, Attributes.MAX_HEALTH, RallyEvents.HEALTH_MODIFIER);
        assertNoModifier(helper, cow, Attributes.MOVEMENT_SPEED, RallyEvents.SPEED_MODIFIER);
        assertNoModifier(helper, cow, Attributes.ARMOR, RallyEvents.ARMOR_MODIFIER);
        assertNoModifier(helper, cow, Attributes.ARMOR_TOUGHNESS, RallyEvents.TOUGHNESS_MODIFIER);
        helper.succeed();
    }

    private static void assertNoModifier(GameTestHelper helper, Mob mob, Holder<Attribute> attribute,
                                         Identifier modifier) {
        helper.assertTrue(mob.getAttribute(attribute).getModifier(modifier) == null,
                String.format(RELEASE_LEFT_A_MODIFIER, attribute.getRegisteredName(), modifier));
    }

    /**
     * The release deals the cow no damage: its health falls only to its
     * unbuffed max, and it records no damage source
     * (decision rally-cast-on-your-rallied-mob-releases-it).
     *
     * @param helper the gametest helper
     */
    public static void rallyReleaseDealsNoDamage(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        float unbuffedMax = cow.getMaxHealth();
        rally(helper, cow, caster);
        float before = cow.getHealth();

        rally(helper, cow, caster);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        float expected = Math.min(before, unbuffedMax);
        helper.assertTrue(cow.getHealth() == expected, String.format(RELEASE_HEALTH, expected, cow.getHealth()));
        helper.assertTrue(cow.getLastDamageSource() == null,
                String.format(RELEASE_HURT_THE_COW, cow.getLastDamageSource()));
        helper.succeed();
    }

    /**
     * A second player wearing a diamond chestplate the first lacks casts
     * Rally at a cow the first player rallied, and the cow's rally stands
     * as it stood, its first rallier kept
     * (decisions other-players-rally-and-charm-refuse-your-minions, rally-remembers-the-first-rallier).
     *
     * @param helper the gametest helper
     */
    public static void rallyByASecondPlayerLeavesTheRallyAsItStood(GameTestHelper helper) {
        ServerPlayer first = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        ServerPlayer second = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        second.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, first);
        Rallied before = cow.getData(GooAttachments.RALLIED);

        rally(helper, cow, second);

        helper.getLevel().getServer().getPlayerList().remove(first);
        helper.getLevel().getServer().getPlayerList().remove(second);
        Rallied after = cow.hasData(GooAttachments.RALLIED) ? cow.getData(GooAttachments.RALLIED) : null;
        helper.assertTrue(before.equals(after), String.format(SECOND_PLAYER_CHANGED_THE_RALLY, before, after));
        helper.succeed();
    }

    /**
     * A second player's Charm on a zombie the first player charmed leaves the
     * first player its charmer
     * (decision other-players-rally-and-charm-refuse-your-minions).
     *
     * @param helper the gametest helper
     */
    public static void charmByASecondPlayerKeepsTheFirstCharmer(GameTestHelper helper) {
        ServerPlayer first = SelfDeliveryTests.invoker(helper, GooTypes.HEX);
        ServerPlayer second = SelfDeliveryTests.invoker(helper, GooTypes.HEX);
        Zombie zombie = charmable(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS));
        charm(helper, zombie, first);
        helper.assertTrue(zombie.hasData(GooAttachments.CHARMED), NOT_CHARMED);

        charm(helper, zombie, second);

        helper.getLevel().getServer().getPlayerList().remove(first);
        helper.getLevel().getServer().getPlayerList().remove(second);
        helper.assertTrue(zombie.hasData(GooAttachments.CHARMED)
                && zombie.getData(GooAttachments.CHARMED).charmer().equals(first.getUUID()),
                SECOND_PLAYER_TOOK_THE_CHARM);
        helper.succeed();
    }

    /**
     * Charm cast at a rallied cow, by its rallier and by a second player,
     * leaves the cow its rally and no charm; the cow's health is lowered so
     * Charm's chance would land every cast
     * (decisions rallied-mobs-are-immune-to-charm, other-players-rally-and-charm-refuse-your-minions).
     *
     * @param helper the gametest helper
     */
    public static void charmLeavesARalliedCowItsRally(GameTestHelper helper) {
        ServerPlayer rallier = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        ServerPlayer second = SelfDeliveryTests.invoker(helper, GooTypes.HEX);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CERTAIN_CHARM_COW_HEALTH);
        cow.setHealth(cow.getMaxHealth());
        rally(helper, cow, rallier);

        charm(helper, cow, rallier);
        assertRallyStandsUncharmed(helper, cow, rallier, "The rallier's");
        charm(helper, cow, second);
        assertRallyStandsUncharmed(helper, cow, rallier, "A second player's");

        helper.getLevel().getServer().getPlayerList().remove(rallier);
        helper.getLevel().getServer().getPlayerList().remove(second);
        helper.succeed();
    }

    private static void assertRallyStandsUncharmed(GameTestHelper helper, Cow cow, ServerPlayer rallier,
                                                   String whose) {
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED)
                && cow.getData(GooAttachments.RALLIED).caster().equals(rallier.getUUID())
                && !cow.hasData(GooAttachments.CHARMED), String.format(CHARM_TOOK_A_RALLIED_COW, whose));
    }

    /**
     * Rally cast at a charmed cow, by its charmer and by a second player,
     * leaves the cow its charm, no rally and a cow's unrallied max health
     * (decisions charmed-mobs-are-immune-to-rally, other-players-rally-and-charm-refuse-your-minions).
     *
     * @param helper the gametest helper
     */
    public static void rallyLeavesACharmedCowItsCharm(GameTestHelper helper) {
        ServerPlayer charmer = SelfDeliveryTests.invoker(helper, GooTypes.HEX);
        ServerPlayer second = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        double baseMaxHealth = cow.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        charm(helper, cow, charmer);
        helper.assertTrue(cow.hasData(GooAttachments.CHARMED), NOT_CHARMED);

        rally(helper, cow, charmer);
        assertCharmStandsUnrallied(helper, cow, charmer, baseMaxHealth, "The charmer's");
        rally(helper, cow, second);
        assertCharmStandsUnrallied(helper, cow, charmer, baseMaxHealth, "A second player's");

        helper.getLevel().getServer().getPlayerList().remove(charmer);
        helper.getLevel().getServer().getPlayerList().remove(second);
        helper.succeed();
    }

    private static void assertCharmStandsUnrallied(GameTestHelper helper, Cow cow, ServerPlayer charmer,
                                                   double baseMaxHealth, String whose) {
        helper.assertTrue(cow.hasData(GooAttachments.CHARMED)
                && cow.getData(GooAttachments.CHARMED).charmer().equals(charmer.getUUID())
                && !cow.hasData(GooAttachments.RALLIED)
                && cow.getMaxHealth() == baseMaxHealth,
                String.format(RALLY_TOOK_A_CHARMED_COW, whose, baseMaxHealth, cow.getMaxHealth()));
    }

    /**
     * A cow released by its rallier's second Rally takes a fresh rally, with
     * its health buff, from a third
     * (decision rally-cast-on-your-rallied-mob-releases-it).
     *
     * @param helper the gametest helper
     */
    public static void releasedCowTakesAFreshRally(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        rally(helper, cow, caster);
        helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RELEASE_LEFT_THE_RALLY);

        rally(helper, cow, caster);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED)
                && cow.getData(GooAttachments.RALLIED).caster().equals(caster.getUUID()), NOT_RALLIED_FRESH);
        helper.assertTrue(cow.getAttribute(Attributes.MAX_HEALTH).getModifier(RallyEvents.HEALTH_MODIFIER) != null,
                NOT_RALLIED_FRESH);
        helper.succeed();
    }

    private static final String RALLY_COW_KEEPS_WALKING_ONCE_ITS_RALLIER_STOPS =
            "rally_cow_keeps_walking_once_its_rallier_stops";
    /**
     * The short step's start in march_bay, with room on both sides of the
     * rallier for the cow to walk round it to the spot ahead.
     */
    private static final Vec3 STEP_START = new Vec3(5.0, 1.0, 2.5);
    /** The short step's end, half a block on along the diagonal. */
    private static final Vec3 STEP_END = new Vec3(5.5, 1.0, 3.0);
    /** The cow's start, two blocks behind the step's end, too far to get ahead while the rallier moves. */
    private static final BlockPos BEHIND_THE_STEP_POS = new BlockPos(4, 1, 1);
    /** The step's pace, so the rallier moves for a handful of ticks before it stops. */
    private static final double STEP_PACE = 0.1;
    /** How far past the stopped rallier the cow must stand, short of its nearest spot less the arrival distance. */
    private static final double PAST_THE_STOP = 0.5;
    private static final String COW_STOPPED_SHORT =
            "The rallied cow should walk on %s blocks past its stopped rallier, stands %s past it";

    private static final String RALLY_COW_IGNORES_THE_WHEAT_IT_WOULD_FOLLOW =
            "rally_cow_ignores_the_wheat_it_would_follow";
    /** The free-willed cow's block, within the near distance of the rallier but past where wheat stops a cow. */
    private static final BlockPos FREE_COW_POS = new BlockPos(1, 1, 0);
    /** Where the still rallier stands holding wheat, nearly the near distance south of the cow. */
    private static final Vec3 WHEAT_STAND = new Vec3(1.5, 1.0, 4.3);
    /** Ticks for the wheat and the cow's stroll to have walked a free cow well over a block. */
    private static final int TEMPTED_TICKS = 80;
    /** How far the rallied cow may drift and still count as standing where it was. */
    private static final double STOOD_STILL = 0.5;
    private static final String COW_WANDERED = "The rallied cow should stand where it was, moved %s blocks";

    /**
     * A mock rallier steps half a block and stops beside a rallied cow that
     * starts behind it, and the cow walks on past the stopped rallier to the
     * spot on the side it headed toward
     * (decision unengaged-minions-move-where-you-move).
     *
     * @param helper the gametest helper
     */
    public static void rallyCowKeepsWalkingOnceItsRallierStops(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Vec3 start = helper.absoluteVec(STEP_START);
        Vec3 end = helper.absoluteVec(STEP_END);
        Vec3 heading = end.subtract(start).normalize();
        double length = end.distanceTo(start);
        caster.setPos(start);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, BEHIND_THE_STEP_POS);
        rally(helper, cow, caster);
        int[] walked = new int[1];
        helper.onEachTick(() -> caster.setPos(start.add(heading.scale(Math.min(length, STEP_PACE * walked[0]++)))));
        helper.succeedWhen(() -> {
            double past = cow.position().subtract(end).dot(heading);
            helper.assertTrue(STEP_PACE * walked[0] > length && past > PAST_THE_STOP,
                    String.format(COW_STOPPED_SHORT, PAST_THE_STOP, past));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A cow rallied with its own free will stands beside its still rallier,
     * who holds the wheat a cow follows, and stays where it was: no vanilla
     * goal walks a rallied mob, neither the wheat's tempt nor a stroll
     * (decision unengaged-minions-move-where-you-move). It and the other
     * movement tests batch alone in rally_movement_alone: a neighbouring
     * test's zombie within the rallier's foe reach is a foe the cow rightly
     * walks off to fight.
     *
     * @param helper the gametest helper
     */
    public static void rallyCowIgnoresTheWheatItWouldFollow(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        caster.setPos(helper.absoluteVec(WHEAT_STAND));
        caster.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WHEAT));
        Cow cow = helper.spawn(EntityType.COW, FREE_COW_POS);
        rally(helper, cow, caster);
        Vec3 stood = cow.position();
        helper.runAfterDelay(TEMPTED_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            double moved = cow.position().subtract(stood).horizontalDistance();
            helper.assertTrue(moved < STOOD_STILL, String.format(COW_WANDERED, moved));
            helper.succeed();
        });
    }

    private static final String RALLY_COW_WEARS_HALF_ITS_RALLIERS_ARMOR = "rally_cow_wears_half_its_ralliers_armor";
    /** The modifier standing in for the caster's gear, so the test sets the caster's armor outright. */
    private static final Identifier CASTER_GEAR = Identifier.fromNamespaceAndPath(Goo.MODID, "test_caster_gear");
    /** A full diamond set's armor and toughness, the caster's first gear. */
    private static final double FULL_DIAMOND_ARMOR = 20;
    private static final double FULL_DIAMOND_TOUGHNESS = 8;
    /** The diamond set less its chestplate, the caster's gear after the change. */
    private static final double DIAMOND_LESS_CHEST_ARMOR = 12;
    private static final double DIAMOND_LESS_CHEST_TOUGHNESS = 6;
    /** Ticks past any armor refresh the rally runs, so the cow has read the caster's new gear. */
    private static final int PAST_THE_ARMOR_REFRESH_TICKS = 50;
    private static final double ATTRIBUTE_TOLERANCE = 1e-6;
    private static final String ARMOR_STANDS = "The cow's %s should stand at %s, stands %s";

    /**
     * A caster in full diamond rallies a cow, which wears half the caster's
     * armor and toughness; the caster sheds its chestplate and the cow's armor
     * follows; the caster strikes the cow and its armor returns to a cow's own
     * (decision rally-minions-get-half-your-armor-and-a-floor).
     *
     * @param helper the gametest helper
     */
    public static void rallyCowWearsHalfItsRalliersArmor(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        wear(caster, FULL_DIAMOND_ARMOR, FULL_DIAMOND_TOUGHNESS);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        rally(helper, cow, caster);

        assertAttribute(helper, cow, Attributes.ARMOR, FULL_DIAMOND_ARMOR / 2);
        assertAttribute(helper, cow, Attributes.ARMOR_TOUGHNESS, FULL_DIAMOND_TOUGHNESS / 2);
        wear(caster, DIAMOND_LESS_CHEST_ARMOR, DIAMOND_LESS_CHEST_TOUGHNESS);
        helper.runAfterDelay(PAST_THE_ARMOR_REFRESH_TICKS, () -> {
            assertAttribute(helper, cow, Attributes.ARMOR, DIAMOND_LESS_CHEST_ARMOR / 2);
            assertAttribute(helper, cow, Attributes.ARMOR_TOUGHNESS, DIAMOND_LESS_CHEST_TOUGHNESS / 2);
            cow.hurtServer(helper.getLevel(), cow.damageSources().playerAttack(caster), LIGHT_HIT);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RALLY_SURVIVED_THE_HIT);
            assertAttribute(helper, cow, Attributes.ARMOR, cow.getAttributeBaseValue(Attributes.ARMOR));
            assertAttribute(helper, cow, Attributes.ARMOR_TOUGHNESS,
                    cow.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS));
            helper.succeed();
        });
    }

    private static final String RALLY_COW_TAKES_THE_SIDE_ITS_RALLIER_WALKS_TOWARD =
            "rally_cow_takes_the_side_its_rallier_walks_toward";
    /** Where the walking rallier starts, at the bay's west edge. */
    private static final Vec3 WALK_START = new Vec3(0.5, 1.0, 1.5);
    /** Where the walking rallier ends, at the bay's east edge. */
    private static final Vec3 WALK_END = new Vec3(5.5, 1.0, 1.5);
    /** The rallied cow's start, two blocks to the side of the rallier's start. */
    private static final BlockPos BESIDE_THE_WALK_POS = new BlockPos(0, 1, 3);
    /**
     * The rallier's pace in blocks a tick: above the standing speed, and slow
     * enough that the cow has most of the walk to get ahead inside the bay.
     */
    private static final double RALLIER_PACE = 0.04;
    /** How far along the walk past the rallier the cow must stand to count as ahead of it. */
    private static final double AHEAD_MARGIN = 1.0;
    private static final String COW_NOT_AHEAD =
            "The rallied cow should stand %s blocks past its walking rallier, stands %s past it";

    /**
     * A mock rallier walks steadily east beside a rallied cow with no foe
     * near, and the cow walks out ahead of it along the walk, to the side the
     * rallier heads toward
     * (decision unengaged-minions-move-where-you-move).
     *
     * @param helper the gametest helper
     */
    public static void rallyCowTakesTheSideItsRallierWalksToward(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Vec3 start = helper.absoluteVec(WALK_START);
        Vec3 end = helper.absoluteVec(WALK_END);
        // the bay may stand rotated, so the walk's world heading is read from its world ends
        Vec3 heading = end.subtract(start).normalize();
        caster.setPos(start);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, BESIDE_THE_WALK_POS);
        rally(helper, cow, caster);
        int[] walked = new int[1];
        helper.onEachTick(() -> {
            Vec3 step = start.add(heading.scale(RALLIER_PACE * walked[0]++));
            caster.setPos(step.distanceTo(start) < end.distanceTo(start) ? step : end);
        });
        helper.succeedWhen(() -> {
            double ahead = cow.position().subtract(caster.position()).dot(heading);
            helper.assertTrue(ahead > AHEAD_MARGIN, String.format(COW_NOT_AHEAD, AHEAD_MARGIN, ahead));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    private static final String RALLIED_COWS_TAKE_SLOTS_IN_RALLY_ORDER = "rallied_cows_take_slots_in_rally_order";
    private static final String RALLIED_COWS_SWAP_SLOTS_IN_THE_OTHER_RALLY_ORDER =
            "rallied_cows_swap_slots_in_the_other_rally_order";
    /** Where the marching rallier starts, at the bay's west edge on its middle line. */
    private static final Vec3 MARCH_START = new Vec3(0.5, 1.0, 2.5);
    /** Where the marching rallier stops, two blocks east, so the formation ahead of it stays inside the bay. */
    private static final Vec3 MARCH_END = new Vec3(2.5, 1.0, 2.5);
    /** Cow A's start, north of the rallier's start. */
    private static final BlockPos NORTH_COW_POS = new BlockPos(0, 1, 1);
    /** Cow B's start, south of the rallier's start. */
    private static final BlockPos SOUTH_COW_POS = new BlockPos(0, 1, 4);
    /** How near its own slot a cow must stand, in blocks. */
    private static final double AT_ITS_SLOT = 1.0;
    private static final String COW_OFF_ITS_SLOT =
            "Cow %s, rallied %s, should stand within %s of slot %s at %s and nearer it than slot %s at %s; stands at %s";

    /**
     * Rallies cow A and a tick later cow B beside a mock rallier that walks
     * steadily east and stops: cow A stands at the formation's first slot and
     * cow B at its second
     * (decisions each-rallied-mob-locks-into-a-fixed-slot, slots-follow-rally-order-size-and-count).
     *
     * @param helper the gametest helper
     */
    public static void ralliedCowsTakeSlotsInRallyOrder(GameTestHelper helper) {
        marchTwoCows(helper, true);
    }

    /**
     * The run of {@link #ralliedCowsTakeSlotsInRallyOrder} with cow B rallied
     * first: cow B stands at the first slot and cow A at the second
     * (decision slots-follow-rally-order-size-and-count).
     *
     * @param helper the gametest helper
     */
    public static void ralliedCowsSwapSlotsInTheOtherRallyOrder(GameTestHelper helper) {
        marchTwoCows(helper, false);
    }

    private static void marchTwoCows(GameTestHelper helper, boolean northCowFirst) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Vec3 start = helper.absoluteVec(MARCH_START);
        Vec3 end = helper.absoluteVec(MARCH_END);
        // the bay may stand rotated, so the march's world heading is read from its world ends
        Vec3 heading = end.subtract(start).normalize();
        double length = end.distanceTo(start);
        caster.setPos(start);
        Cow north = helper.spawnWithNoFreeWill(EntityType.COW, NORTH_COW_POS);
        Cow south = helper.spawnWithNoFreeWill(EntityType.COW, SOUTH_COW_POS);
        Cow first = northCowFirst ? north : south;
        Cow second = northCowFirst ? south : north;
        rally(helper, first, caster);
        // a tick apart, so the rally stamps differ and the order is the one rallied, not the uuids'
        helper.runAfterDelay(1, () -> rally(helper, second, caster));
        int[] walked = new int[1];
        helper.onEachTick(() -> caster.setPos(start.add(heading.scale(Math.min(length, RALLIER_PACE * walked[0]++)))));
        List<RallyFollow.Slot> slots = RallyFollow.formation(caster.getBbWidth(), List.of((double) first.getBbWidth(),
                (double) second.getBbWidth()));
        helper.succeedWhen(() -> {
            helper.assertTrue(RALLIER_PACE * walked[0] > length, "The rallier should finish its march");
            Vec3 velocity = heading.scale(RALLIER_PACE);
            Vec3 firstSlot = RallyFollow.destination(caster.position(), velocity, slots.get(0)).orElseThrow();
            Vec3 secondSlot = RallyFollow.destination(caster.position(), velocity, slots.get(1)).orElseThrow();
            assertAtSlot(helper, first, "first", firstSlot, secondSlot);
            assertAtSlot(helper, second, "second", secondSlot, firstSlot);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    private static final String SLOW_AND_FAST_MINIONS_HOLD_THEIR_SLOTS = "slow_and_fast_minions_hold_their_slots";
    /**
     * Where the walking rallier starts in march_bay, 40 blocks long, since the
     * test walls its mobs into the bay and a walk at a player's pace runs far.
     */
    private static final Vec3 MARCH_BAY_START = new Vec3(5.5, 1.0, 3.5);
    /** A point due east of the start, along the bay's length, which sets the walk's heading. */
    private static final Vec3 MARCH_BAY_HEADING = new Vec3(6.5, 1.0, 3.5);
    /** A player's walk, in blocks a tick. */
    private static final double WALKING_PACE = 0.216;
    /** The cow's start, beside the rallier. */
    private static final BlockPos BESIDE_THE_RALLIER_POS = new BlockPos(5, 1, 2);
    /** The fox's start, five blocks behind the rallier, so it trails its slot by eight. */
    private static final BlockPos BEHIND_THE_RALLIER_POS = new BlockPos(0, 1, 4);
    /** Ticks the rallier walks, 26 blocks: time for the fox to close on its slot and march there a while. */
    private static final int WALK_TICKS = 120;
    /** How near its slot a marching minion must stand on every tick, in blocks. */
    private static final double MARCHING_AT_ITS_SLOT = 1.0;
    private static final String TRAILER_NEVER_ARRIVED =
            "The trailing fox should close within %s of its slot by the walk's end; nearest %s, last %s, rallied %s";
    private static final String MARCHER_OFF_ITS_SLOT =
            "The %s should stand within %s of its slot on every tick after the fox arrived; tick %s stood %s off";

    /**
     * Rallies a cow beside a mock rallier and a fox four blocks behind it,
     * species whose speeds differ, and walks the rallier steadily east at a
     * player's walking pace: the fox closes on its slot, and from then on
     * each mob stands within a block of its slot on every tick, neither
     * falling behind nor running past
     * (decisions rallied-mobs-share-their-ralliers-speed, each-rallied-mob-locks-into-a-fixed-slot).
     *
     * @param helper the gametest helper
     */
    public static void slowAndFastMinionsHoldTheirSlots(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Vec3 start = helper.absoluteVec(MARCH_BAY_START);
        // the bay may stand rotated, so the walk's world heading is read from its world points
        Vec3 heading = helper.absoluteVec(MARCH_BAY_HEADING).subtract(start).normalize();
        caster.setPos(start);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, BESIDE_THE_RALLIER_POS);
        Fox fox = helper.spawnWithNoFreeWill(EntityType.FOX, BEHIND_THE_RALLIER_POS);
        rally(helper, cow, caster);
        helper.runAfterDelay(1, () -> rally(helper, fox, caster));
        List<RallyFollow.Slot> slots = RallyFollow.formation(caster.getBbWidth(), List.of((double) cow.getBbWidth(),
                (double) fox.getBbWidth()));
        Vec3 velocity = heading.scale(WALKING_PACE);
        int[] walked = new int[1];
        double[] foxNearest = {Double.MAX_VALUE};
        boolean[] foxArrived = new boolean[1];
        helper.onEachTick(() -> {
            if (walked[0] >= WALK_TICKS) {
                return;
            }
            caster.setPos(start.add(heading.scale(WALKING_PACE * walked[0]++)));
            double cowOff = offSlot(cow, caster, velocity, slots.get(0));
            double foxOff = offSlot(fox, caster, velocity, slots.get(1));
            foxNearest[0] = Math.min(foxNearest[0], foxOff);
            foxArrived[0] |= foxOff <= RallyFollow.ARRIVAL_DISTANCE;
            if (foxArrived[0]) {
                helper.assertTrue(cowOff <= MARCHING_AT_ITS_SLOT,
                        String.format(MARCHER_OFF_ITS_SLOT, "cow", MARCHING_AT_ITS_SLOT, walked[0], cowOff));
                helper.assertTrue(foxOff <= MARCHING_AT_ITS_SLOT,
                        String.format(MARCHER_OFF_ITS_SLOT, "fox", MARCHING_AT_ITS_SLOT, walked[0], foxOff));
            }
        });
        helper.runAfterDelay(WALK_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(foxArrived[0], String.format(TRAILER_NEVER_ARRIVED, RallyFollow.ARRIVAL_DISTANCE,
                    foxNearest[0], offSlot(fox, caster, velocity, slots.get(1)), fox.hasData(GooAttachments.RALLIED)));
            helper.succeed();
        });
    }

    private static final String RALLIED_COW_WALKS_AROUND_ITS_RALLIER = "rallied_cow_walks_around_its_rallier";
    /** Where the rallier starts in march_bay, three blocks in from its west end. */
    private static final Vec3 AWAY_WALK_START = new Vec3(8.5, 1.0, 3.5);
    /** A point due east of the start, along the bay's length, which sets the walk's heading. */
    private static final Vec3 AWAY_WALK_HEADING = new Vec3(9.5, 1.0, 3.5);
    /** The cow's start, two blocks straight behind the rallier, so its slot ahead lies across the rallier. */
    private static final BlockPos STRAIGHT_BEHIND_POS = new BlockPos(6, 1, 3);
    /** How far inside the keep-clear radius the cow may stray, a tenth of a block for a step's momentum. */
    private static final double KEEP_CLEAR_TOLERANCE = 0.1;
    private static final String COW_WALKED_INTO_ITS_RALLIER =
            "The cow should stay %s from its rallier on its way to its slot; tick %s it stood %s away";
    private static final String COW_NEVER_REACHED_ITS_SLOT =
            "The cow should reach its slot across the rallier by the walk's end; it stands %s off";

    /**
     * Stands a rallied cow straight behind a mock rallier and walks the
     * rallier away from it at a player's walking pace, so the cow's slot
     * lies across the rallier: on every tick until the cow reaches its slot
     * it stands outside the rallier's keep-clear radius, less a tenth of a
     * block, and it reaches the slot before the walk ends
     * (decision rallied-mobs-walk-around-their-rallier).
     *
     * @param helper the gametest helper
     */
    public static void ralliedCowWalksAroundItsRallier(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Vec3 start = helper.absoluteVec(AWAY_WALK_START);
        // the bay may stand rotated, so the walk's world heading is read from its world points
        Vec3 heading = helper.absoluteVec(AWAY_WALK_HEADING).subtract(start).normalize();
        caster.setPos(start);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, STRAIGHT_BEHIND_POS);
        rally(helper, cow, caster);
        RallyFollow.Slot slot = RallyFollow.formation(caster.getBbWidth(), List.of((double) cow.getBbWidth()))
                .getFirst();
        double keepClear = RallyFollow.keepClearRadius(caster.getBbWidth(), cow.getBbWidth()) - KEEP_CLEAR_TOLERANCE;
        Vec3 velocity = heading.scale(WALKING_PACE);
        int[] walked = new int[1];
        boolean[] arrived = new boolean[1];
        helper.onEachTick(() -> {
            if (walked[0] >= WALK_TICKS) {
                return;
            }
            caster.setPos(start.add(heading.scale(WALKING_PACE * walked[0]++)));
            arrived[0] |= offSlot(cow, caster, velocity, slot) <= RallyFollow.ARRIVAL_DISTANCE;
            double apart = cow.position().subtract(caster.position()).horizontalDistance();
            helper.assertTrue(arrived[0] || apart >= keepClear,
                    String.format(COW_WALKED_INTO_ITS_RALLIER, keepClear, walked[0], apart));
        });
        helper.runAfterDelay(WALK_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(arrived[0], String.format(COW_NEVER_REACHED_ITS_SLOT,
                    offSlot(cow, caster, velocity, slot)));
            helper.succeed();
        });
    }

    private static double offSlot(Mob mob, ServerPlayer caster, Vec3 velocity, RallyFollow.Slot slot) {
        Vec3 spot = RallyFollow.destination(caster.position(), velocity, slot).orElseThrow();
        return mob.position().subtract(spot).horizontalDistance();
    }

    private static void assertAtSlot(GameTestHelper helper, Cow cow, String rallied, Vec3 own, Vec3 other) {
        double toOwn = cow.position().subtract(own).horizontalDistance();
        double toOther = cow.position().subtract(other).horizontalDistance();
        helper.assertTrue(toOwn <= AT_ITS_SLOT && toOwn < toOther, String.format(COW_OFF_ITS_SLOT,
                cow.getUUID(), rallied, AT_ITS_SLOT, rallied, own, "other", other, cow.position()));
    }

    private static void wear(ServerPlayer caster, double armor, double toughness) {
        lift(caster.getAttribute(Attributes.ARMOR), armor);
        lift(caster.getAttribute(Attributes.ARMOR_TOUGHNESS), toughness);
    }

    private static void lift(AttributeInstance attribute, double amount) {
        attribute.addOrUpdateTransientModifier(new AttributeModifier(CASTER_GEAR, amount,
                AttributeModifier.Operation.ADD_VALUE));
    }

    private static void assertAttribute(GameTestHelper helper, Mob mob, Holder<Attribute> attribute,
                                        double expected) {
        double stands = mob.getAttributeValue(attribute);
        helper.assertTrue(Math.abs(stands - expected) < ATTRIBUTE_TOLERANCE,
                String.format(ARMOR_STANDS, attribute.getRegisteredName(), expected, stands));
    }

    private static final String RALLY_TAKES_ONE_OF_TWO_COWS = "rally_takes_one_of_two_cows";
    private static final String RALLY_HOLDS_PAST_THE_OLD_RALLY = "rally_holds_past_the_old_rally";
    private static final String RALLY_ENDS_ON_THE_RALLIERS_HIT = "rally_ends_on_the_ralliers_hit";
    /** The 100 ticks a rally lasted as a durative area, and a span past them. */
    private static final int PAST_THE_OLD_RALLY_TICKS = 100 + 50;
    /** A cow's own max health. */
    private static final float COW_MAX_HEALTH = 10f;
    /** A hit too light to kill a rallied cow, so the rally, not the cow, is what ends. */
    private static final float LIGHT_HIT = 1f;
    /** The block beside the targeted cow, where the second cow stands. */
    private static final BlockPos BESIDE_COW_POS = new BlockPos(1, 1, 3);
    private static final String TARGET_NOT_RALLIED = "The cow Rally landed on should hold the rally";
    private static final String BYSTANDER_RALLIED = "The cow beside the target should hold no rally";
    private static final String RALLY_LAPSED = "The cow should hold the rally past 100 ticks with nothing held";
    private static final String RALLY_SURVIVED_THE_HIT = "The rallier's hit should end the rally";
    private static final String STILL_BUFFED = "The struck cow's max health should be a cow's 10, stands %s";
    private static final String ABILITIES_REQUIRED = "zoo_rally should load";


    private static final Identifier ZOO_RALLY = Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_rally");
    private static final BlockPos COW_POS = new BlockPos(2, 1, 3);
    private static final BlockPos ZOMBIE_POS = new BlockPos(3, 1, 3);
    /** The hit the zombie lands on the caster, which makes it the caster's enemy. */
    private static final float ZOMBIE_HIT = 1f;

    private static final String COW_NOT_RALLIED = "The cow near the caster should hold the rally";
    private static final String ZOMBIE_UNHURT = "The rallied cow should strike the zombie that hurt the caster";
    private static final String COW_NOT_BUFFED = "The rallied cow's max health should rise past a cow's 10, stands %s";

    private static final String RALLY_COW_STRIKES_NOTHING_THROUGH_A_WALL = "rally_cow_strikes_nothing_through_a_wall";
    /** The wall's block, between the cow's and the zombie's. */
    private static final BlockPos WALL_POS = new BlockPos(3, 1, 3);
    /** The cow's centre off the wall block's west edge, its side against the post spanning 0.25 to 0.75. */
    private static final double COW_OFF_THE_POST = 0.25 - 0.46;
    /** The zombie's centre off the wall block's west edge, its side against the post's east face. */
    private static final double ZOMBIE_OFF_THE_POST = 0.75 + 0.31;
    private static final double BLOCK_CENTER = 0.5;
    /** Ticks for a cow in range to have struck several times over at 15 ticks a strike. */
    private static final int THROUGH_THE_WALL_TICKS = 40;
    private static final String COW_OUT_OF_RANGE = "The cow and zombie should stand in melee range across the wall";
    private static final String STRUCK_THROUGH_THE_WALL =
            "The zombie across the wall took %s, last from %s, by the cow: %s";

    private static final String RELOADED_LOST = "The saved cow should load back as a mob";
    private static final String RELOADED_UNRALLIED = "The reloaded cow should still hold the rally";
    private static final String RELOADED_UNBUFFED =
            "The reloaded cow's max health should still rise past a cow's 10, stands %s";

    private RallyTests() {
    }

    /**
     * A zombie hurts the caster, the caster throws Rally at the cow beside
     * the zombie, and the cow takes the rally and strikes it.
     *
     * @param helper the gametest helper
     */
    public static void rallyCowAttacksTheZombie(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        // the mock player helper makes a creative player, whom no hit lands on
        caster.setGameMode(GameType.SURVIVAL);
        // a player whose client has not reported loaded is invulnerable, and no mock client reports
        caster.connection.markClientLoaded();
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        // the bay stands open to the sky, and a bare-headed zombie's sunburn would pass for the cow's strike
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        caster.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), ZOMBIE_HIT);

        rally(helper, cow, caster);

        helper.succeedWhen(() -> {
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
            helper.assertTrue(cow.getMaxHealth() > COW_MAX_HEALTH,
                    String.format(COW_NOT_BUFFED, cow.getMaxHealth()));
            helper.assertTrue(zombie.getMaxHealth() - zombie.getHealth() > 0, ZOMBIE_UNHURT);
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * A rallied cow and the zombie that hurt the caster stand within melee
     * range of each other on either side of a cobblestone wall two blocks
     * high, and the cow lands no strike through it, the wall blocking its
     * line of sight (decision rallied-mobs-follow-the-player-who-rallied-them).
     *
     * @param helper the gametest helper
     */
    public static void rallyCowStrikesNothingThroughAWall(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        caster.setGameMode(GameType.SURVIVAL);
        caster.connection.markClientLoaded();
        helper.setBlock(WALL_POS, Blocks.COBBLESTONE_WALL);
        helper.setBlock(WALL_POS.above(), Blocks.COBBLESTONE_WALL);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        // the bay stands open to the sky, so a bare-headed zombie burns and its health reads more than strikes
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        // placed from the wall's world block on the world X axis, since the bay may stand rotated
        BlockPos wall = helper.absolutePos(WALL_POS);
        cow.setPos(wall.getX() + COW_OFF_THE_POST, wall.getY(), wall.getZ() + BLOCK_CENTER);
        // a cow with no free will still walks its path, and would round the post to strike in the open
        cow.setNoAi(true);
        zombie.setPos(wall.getX() + ZOMBIE_OFF_THE_POST, wall.getY(), wall.getZ() + BLOCK_CENTER);
        caster.hurtServer(helper.getLevel(), zombie.damageSources().mobAttack(zombie), ZOMBIE_HIT);
        helper.assertTrue(cow.isWithinMeleeAttackRange(zombie), COW_OUT_OF_RANGE);

        rally(helper, cow, caster);

        helper.runAfterDelay(THROUGH_THE_WALL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
            helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(),
                    String.format(STRUCK_THROUGH_THE_WALL, zombie.getMaxHealth() - zombie.getHealth(),
                            zombie.getLastDamageSource() == null ? "none" : zombie.getLastDamageSource().getMsgId(),
                            zombie.getLastHurtByMob() == cow));
            helper.succeed();
        });
    }

    /**
     * A rallied cow saved the way a chunk unload saves it and loaded back
     * keeps the rally and its buff
     * (decision zoo-rally-arms-the-peaceful).
     *
     * @param helper the gametest helper
     */
    public static void rallySurvivesAReload(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        Mob[] reloaded = new Mob[1];
        helper.succeedWhen(() -> {
            if (reloaded[0] == null) {
                helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
                reloaded[0] = reload(helper, cow);
            }
            helper.assertTrue(reloaded[0].hasData(GooAttachments.RALLIED), RELOADED_UNRALLIED);
            helper.assertTrue(reloaded[0].getMaxHealth() > COW_MAX_HEALTH,
                    String.format(RELOADED_UNBUFFED, reloaded[0].getMaxHealth()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }

    /**
     * Rally thrown at one of two adjacent cows rallies that cow and leaves
     * the other unrallied (decision rally-is-a-mob-effect-on-one-target).
     *
     * @param helper the gametest helper
     */
    public static void rallyTakesOneOfTwoCows(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        Cow bystander = helper.spawnWithNoFreeWill(EntityType.COW, BESIDE_COW_POS);

        rally(helper, cow, caster);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), TARGET_NOT_RALLIED);
        helper.assertFalse(bystander.hasData(GooAttachments.RALLIED), BYSTANDER_RALLIED);
        helper.succeed();
    }

    /**
     * A rallied cow still holds the rally past the 100 ticks the
     * area rally lasted, its caster holding nothing and casting nothing again
     * (decision rally-is-a-mob-effect-on-one-target).
     *
     * @param helper the gametest helper
     */
    public static void rallyHoldsPastTheOldRally(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        helper.runAfterDelay(PAST_THE_OLD_RALLY_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED), RALLY_LAPSED);
            helper.succeed();
        });
    }

    private static final String RALLIERS_PUNCH_LEAVES_THE_COW_ALIVE = "rally_ralliers_punch_leaves_the_cow_alive";
    private static final String SECOND_PUNCH_MAKES_THE_FREED_COW_A_FOE =
            "rally_second_punch_makes_the_freed_cow_a_foe";
    /** Ticks for both cows to settle under their rallies before the punch. */
    private static final int SETTLED_TICKS = 40;
    /** Ticks the punched cow is watched after the punch, past the other cow's strike interval. */
    private static final int WATCHED_TICKS = 20;
    /** Ticks for the other rallied cow to reach and strike the freed cow. */
    private static final int STRIKE_WINDOW_TICKS = 40;
    private static final String PUNCH_HURT =
            "The rallier's rally-ending punch should leave the cow alive at %s, unhurt, %s: alive %s, health %s, "
                    + "last damage %s, last attacker %s";
    private static final String PUNCH_KEPT_THE_RALLY = "The rallier's punch should end the cow's rally";
    private static final String SECOND_PUNCH_MISSED = "The second punch should lower the freed cow's %s health, reads %s";
    private static final String FREED_COW_SPARED = "The other rallied cow should strike the freed cow after the second punch";

    /**
     * The rallier punches one of its two rallied cows, at full rallied
     * health: the rally ends, the punch deals no damage, and the freed cow
     * stands alive at a cow's own 10 health, unstruck by the other rallied
     * cow, on the punch's tick and 20 ticks after.
     * rally-is-a-mob-effect-on-one-target
     *
     * @param helper the gametest helper
     */
    public static void ralliersPunchLeavesTheCowAlive(GameTestHelper helper) {
        ServerPlayer caster = survivalRallier(helper);
        Cow cow = helper.spawn(EntityType.COW, COW_POS);
        Cow other = helper.spawn(EntityType.COW, BESIDE_COW_POS);
        rally(helper, cow, caster);
        rally(helper, other, caster);
        helper.runAfterDelay(SETTLED_TICKS, () -> {
            caster.attack(cow);
            helper.assertFalse(cow.hasData(GooAttachments.RALLIED), PUNCH_KEPT_THE_RALLY);
            assertUnhurt(helper, cow, "on the punch's tick");
        });
        helper.runAfterDelay(SETTLED_TICKS + WATCHED_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            assertUnhurt(helper, cow, WATCHED_TICKS + " ticks after");
            helper.succeed();
        });
    }

    /**
     * The rallier punches one of its two rallied cows twice: the first
     * punch frees it unhurt, the second lands its damage, and the other
     * rallied cow then strikes the freed cow as it would any mob its
     * rallier struck.
     * rally-is-a-mob-effect-on-one-target
     *
     * @param helper the gametest helper
     */
    public static void secondPunchMakesTheFreedCowAFoe(GameTestHelper helper) {
        ServerPlayer caster = survivalRallier(helper);
        Cow cow = helper.spawn(EntityType.COW, COW_POS);
        Cow other = helper.spawn(EntityType.COW, BESIDE_COW_POS);
        rally(helper, cow, caster);
        rally(helper, other, caster);
        helper.runAfterDelay(SETTLED_TICKS, () -> {
            caster.attack(cow);
            float freed = cow.getHealth();
            caster.resetAttackStrengthTicker();
            caster.attack(cow);
            helper.assertTrue(cow.getHealth() < freed, String.format(SECOND_PUNCH_MISSED, freed, cow.getHealth()));
        });
        helper.runAfterDelay(SETTLED_TICKS + STRIKE_WINDOW_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cow.getLastHurtByMob() == other, FREED_COW_SPARED);
            helper.succeed();
        });
    }

    private static final String RALLY_KEEPS_A_HURT_COWS_HEALTH = "rally_keeps_a_hurt_cows_health";
    /** A hurt cow's health, well short of a cow's 10. */
    private static final float HURT_HEALTH = 4f;
    private static final String RALLY_HEALED =
            "Rally should keep the hurt cow at %s health while its max rises past %s: health %s of %s";

    /**
     * A cow at 4 health takes a rally: its max health rises and its health
     * stays at 4, the rally healing none of it.
     * zoo-rally-arms-the-peaceful
     *
     * @param helper the gametest helper
     */
    public static void rallyKeepsAHurtCowsHealth(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        cow.setHealth(HURT_HEALTH);
        rally(helper, cow, caster);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);
        helper.assertTrue(cow.getHealth() == HURT_HEALTH && cow.getMaxHealth() > COW_MAX_HEALTH,
                String.format(RALLY_HEALED, HURT_HEALTH, COW_MAX_HEALTH, cow.getHealth(), cow.getMaxHealth()));
        helper.succeed();
    }

    /**
     * A survival player holding zoo goo, whom hits land on and who lands hits.
     *
     * @param helper the gametest helper
     * @return the player
     */
    private static ServerPlayer survivalRallier(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        caster.setGameMode(GameType.SURVIVAL);
        caster.connection.markClientLoaded();
        return caster;
    }

    private static void assertUnhurt(GameTestHelper helper, Cow cow, String when) {
        helper.assertTrue(cow.isAlive() && cow.getHealth() == COW_MAX_HEALTH && cow.getLastDamageSource() == null,
                String.format(PUNCH_HURT, COW_MAX_HEALTH, when, cow.isAlive(), cow.getHealth(),
                        cow.getLastDamageSource() == null ? "none" : cow.getLastDamageSource().getMsgId(),
                        cow.getLastHurtByMob()));
    }

    /**
     * The rallier's own hit on its rallied cow ends the rally and takes its
     * buff off: no rally, a cow's own max health
     * (decision rally-is-a-mob-effect-on-one-target).
     *
     * @param helper the gametest helper
     */
    public static void rallyEndsOnTheRalliersHit(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.ZOO);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);
        rally(helper, cow, caster);
        helper.assertTrue(cow.hasData(GooAttachments.RALLIED), COW_NOT_RALLIED);

        cow.hurtServer(helper.getLevel(), cow.damageSources().playerAttack(caster), LIGHT_HIT);

        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertFalse(cow.hasData(GooAttachments.RALLIED), RALLY_SURVIVED_THE_HIT);
        helper.assertTrue(cow.getMaxHealth() == COW_MAX_HEALTH, String.format(STILL_BUFFED, cow.getMaxHealth()));
        helper.succeed();
    }

    /**
     * Lands one Rally goo on a mob as the caster's throw, through the
     * scheduler a thrown goo's impact runs.
     *
     * @param helper the gametest helper
     * @param mob    the mob struck
     * @param caster the throwing player
     */
    private static void rally(GameTestHelper helper, Mob mob, ServerPlayer caster) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(ZOO_RALLY);
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), caster, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, ZOO_RALLY.toString()));
    }

    /**
     * Saves a mob the way a chunk unload saves it, discards it and loads the
     * save back into the level.
     *
     * @param helper the gametest helper
     * @param mob    the mob saved
     * @return the mob loaded from the save
     */
    private static Mob reload(GameTestHelper helper, Mob mob) {
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        mob.save(saved);
        mob.discard();
        Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), saved.buildResult()), helper.getLevel(), EntitySpawnReason.LOAD,
                entity -> entity);
        helper.assertTrue(loaded instanceof Mob, RELOADED_LOST);
        helper.getLevel().addFreshEntity(loaded);
        return (Mob) loaded;
    }
}
