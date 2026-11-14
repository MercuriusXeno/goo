package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.PayloadRecorder;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.HealPulse;
import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.Variables;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for the stream delivery: holding blaze flames at a zombie runs
 * the ability on it each tick of the hold, drains blaze goo while held,
 * and drains nothing once released
 * (decision stream-delivery-held-cone); holding jelly heal at a hurt cow heals
 * the cow and the caster both and courts no cow (decision nourish-and-healing-ship-on-jelly),
 * once per pulse interval (decision diagnose-and-correct-heal-spamming-zombie-noises).
 */
@RegistersGameTests
public final class StreamDeliveryTests {
    private static final String STREAM_BLAZE_FLAMES = "stream_blaze_flames";
    private static final String VITALITY_HEALS = "vitality_heals_cow_and_caster";
    private static final String FLAMES_DAMAGES_PER_TICK = "flames_damages_per_tick";
    /** Ten held ticks, enough for the flames' burn to land on the zombie. */
    private static final int FLAMES_HOLD_TICKS = 10;
    private static final String SHOULD_LOSE = "The flamed zombie should lose health to the burn, lost %.2f";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, STREAM_BLAZE_FLAMES, StreamDeliveryTests::blazeFlames);
        GooTestFunctions.registerTest(registrar, VITALITY_HEALS, StreamDeliveryTests::vitalityHealsCowAndCaster);
        GooTestFunctions.registerTest(registrar, FLAMES_DAMAGES_PER_TICK, StreamDeliveryTests::flamesDamagesPerTick);
        GooTestFunctions.registerTest(registrar, HEAL_TOUCHES_FULL_COW, StreamDeliveryTests::healTouchesAFullHealthCow);
        GooTestFunctions.registerTest(registrar, HEAL_HARMS_ZOMBIE, StreamDeliveryTests::healHarmsAZombie);
        GooTestFunctions.registerTest(registrar, HEAL_PULSES_ON_COW, StreamDeliveryTests::healPulsesOnAWoundedCow);
        GooTestFunctions.registerTest(registrar, HEAL_PULSES_ON_CASTER, StreamDeliveryTests::healPulsesOnAWoundedCaster);
    }

    /**
     * A mock player holds jelly heal on a wounded cow four blocks ahead for
     * three pulse intervals: after each held tick the cow stands at its
     * starting health plus one pulse amount for each pulse landed so far, a
     * pulse on the hold's first tick and one each interval after.
     * diagnose-and-correct-heal-spamming-zombie-noises
     *
     * @param helper the gametest helper
     */
    public static void healPulsesOnAWoundedCow(GameTestHelper helper) {
        AbilityDefinition heal = jellyHeal(helper);
        HealPulse pulse = healPulseOf(heal);
        float amount = amountOf(pulse);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, ZOMBIE_POS);
        cow.setHealth(cow.getMaxHealth() - (HELD_PULSES + 1) * amount);
        ServerPlayer player = streamer(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(player, heal);
        holdHealTracing(helper, player, HELD_PULSES * pulse.every(), cow,
                health -> assertPulsedHealth(helper, health, pulse.every(), amount));
    }

    /**
     * A hungry, wounded mock player holds jelly heal for three pulse
     * intervals: after each held tick the caster stands at its starting
     * health plus one pulse amount for each pulse landed so far. Its food
     * sits below natural regeneration, so only the stream heals it.
     * diagnose-and-correct-heal-spamming-zombie-noises
     *
     * @param helper the gametest helper
     */
    public static void healPulsesOnAWoundedCaster(GameTestHelper helper) {
        AbilityDefinition heal = jellyHeal(helper);
        HealPulse pulse = healPulseOf(heal);
        float amount = amountOf(pulse);
        ServerPlayer player = streamer(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(player, heal);
        player.setHealth(player.getMaxHealth() - (HELD_PULSES + 1) * amount);
        player.getFoodData().setFoodLevel(FOOD_BELOW_REGEN);
        player.getFoodData().setSaturation(0);
        holdHealTracing(helper, player, HELD_PULSES * pulse.every(), player,
                health -> assertPulsedHealth(helper, health, pulse.every(), amount));
    }

    /**
     * Holds jelly heal for a number of ticks, reading a watched thing's
     * health after each held tick inside that tick's own task, then removes
     * the player, runs the check over the readings and succeeds.
     *
     * @param helper    the gametest helper
     * @param player    the streaming player
     * @param holdTicks the held ticks
     * @param watched   the living thing whose health is read
     * @param check     what the readings must show; index 0 holds the health before the hold
     */
    static void holdHealTracing(GameTestHelper helper, ServerPlayer player, int holdTicks, LivingEntity watched,
                                Consumer<float[]> check) {
        float[] health = new float[holdTicks + 1];
        health[0] = watched.getHealth();
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= holdTicks; held++) {
            int reading = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, tick);
                health[reading] = watched.getHealth();
            });
        }
        helper.runAfterDelay(holdTicks + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            check.accept(health);
            helper.succeed();
        });
    }

    /**
     * Fails the test unless each reading stands at the first plus one pulse
     * amount for each pulse landed through its held tick.
     *
     * @param helper   the gametest helper
     * @param health   the readings, index 0 before the hold and index n after its n-th tick
     * @param every    the ticks between pulses
     * @param perPulse the health each pulse adds, negative for one that harms
     */
    static void assertPulsedHealth(GameTestHelper helper, float[] health, int every, float perPulse) {
        for (int held = 1; held < health.length; held++) {
            float expected = health[0] + perPulse * pulsesThrough(held, every);
            helper.assertTrue(Math.abs(health[held] - expected) < HEALTH_TOLERANCE,
                    String.format(SHOULD_STAND_PULSED, held, expected, health[held]));
        }
    }

    /**
     * The pulses a hold has landed through a held tick: one on its first
     * tick and one each interval after.
     *
     * @param held  the hold's tick count, 1 on its first tick
     * @param every the ticks between pulses
     * @return the pulses landed
     */
    static int pulsesThrough(int held, int every) {
        return (held - 1) / every + 1;
    }

    /**
     * The loaded jelly heal, failing the test where the registry lacks it.
     *
     * @param helper the gametest helper
     * @return the ability
     */
    static AbilityDefinition jellyHeal(GameTestHelper helper) {
        AbilityDefinition heal = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(heal != null, VITALITY_REQUIRED);
        return heal;
    }

    /**
     * The pulse jelly heal's heal step carries, as the loaded definition reads it.
     *
     * @param heal the loaded jelly heal
     * @return the heal step's params
     */
    static HealPulse healPulseOf(AbilityDefinition heal) {
        return heal.behaviors().stream()
                .filter(step -> step instanceof LeafStep<?> leaf && leaf.leaf() == LeafSteps.HEAL)
                .map(step -> (HealPulse) ((LeafStep<?>) step).params())
                .findFirst().orElseThrow();
    }

    /**
     * The health one pulse restores, its amount evaluated as a literal.
     *
     * @param pulse the heal step's params
     * @return the health
     */
    static float amountOf(HealPulse pulse) {
        return pulse.amount().evaluateFloat(Variables.NONE);
    }

    /**
     * A mock player holds jelly heal on a zombie four blocks ahead for twenty
     * ticks: where a living mob would gain health, the zombie loses it.
     * heal-harms-an-undead-target
     *
     * @param helper the gametest helper
     */
    public static void healHarmsAZombie(GameTestHelper helper) {
        AbilityDefinition heal = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(heal != null, VITALITY_REQUIRED);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        // the helmet keeps the noon sun off the zombie, so only the stream harms it
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float healthBefore = zombie.getHealth();
        ServerPlayer player = streamer(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(player, heal);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = healthBefore - zombie.getHealth();
            helper.assertTrue(lost > 0, String.format(SHOULD_HARM_ZOMBIE, lost));
            helper.succeed();
        });
    }

    /**
     * A mock player holds jelly heal on a cow at full life four blocks ahead:
     * the heal raises nothing, yet the payload the caster receives names the
     * cow among the mobs it touched, so the cow shows its life.
     * touched-mobs-show-hearts-or-a-count-over-max
     *
     * @param helper the gametest helper
     */
    public static void healTouchesAFullHealthCow(GameTestHelper helper) {
        AbilityDefinition heal = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(heal != null, VITALITY_REQUIRED);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, ZOMBIE_POS);
        ServerPlayer player = streamer(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(player, heal);
        PayloadRecorder<StreamHealedPayload> received = PayloadRecorder.attachTo(player, StreamHealedPayload.class);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= TOUCH_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(TOUCH_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            boolean namesCow = received.received().stream()
                    .anyMatch(payload -> payload.touchedIds().contains(cow.getId()));
            helper.assertTrue(namesCow, String.format(SHOULD_TOUCH_FULL_COW, received.received().size()));
            helper.succeed();
        });
    }

    /**
     * A mock player holds blaze flames at a zombie four blocks ahead for ten
     * ticks: the burn lands through vanilla's hurt and the zombie loses health.
     *
     * @param helper the gametest helper
     */
    public static void flamesDamagesPerTick(GameTestHelper helper) {
        AbilityDefinition flames = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_FLAMES);
        helper.assertTrue(flames != null, ABILITY_REQUIRED);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float healthBefore = zombie.getHealth();
        ServerPlayer player = streamer(helper, GooTypes.BLAZE);
        KnownRecipes.teachRequires(player, flames);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.BLAZE), BLAZE_FLAMES.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= FLAMES_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(FLAMES_HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = healthBefore - zombie.getHealth();
            helper.assertTrue(lost > 0, String.format(SHOULD_LOSE, lost));
            helper.succeed();
        });
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Four blocks east of the player. */
    static final BlockPos ZOMBIE_POS = STAND_POS.east(4);
    private static final int HELD_GOO = 2;
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward the zombie's middle four blocks off. */
    private static final float LOOKING_AT_ZOMBIE = 9f;
    private static final Identifier BLAZE_FLAMES = Identifier.parse("goo:blaze_flames");
    /** Twenty held ticks. */
    private static final int HOLD_TICKS = 20;
    private static final String ABILITY_REQUIRED = "Ability registry must hold blaze_flames";
    private static final String SHOULD_START_UNBURNT = "The helmeted zombie should not burn before the stream";
    private static final String SHOULD_BURN = "The streamed zombie should be on fire";
    private static final String SHOULD_DRAIN = "Twenty ticks of hold should drain blaze goo, drained %d mB";
    private static final Identifier JELLY_HEAL = Identifier.parse("goo:jelly_heal");
    /** A cow stands at ten health, so four leaves it hurt with room to heal. */
    private static final float HURT_COW_HEALTH = 4;
    /** A player stands at twenty health, so ten leaves the caster hurt with room to heal. */
    private static final float HURT_PLAYER_HEALTH = 10;
    /** One short of the eighteen food natural regeneration needs, so only vitality heals the caster. */
    private static final int FOOD_BELOW_REGEN = 17;
    private static final String VITALITY_REQUIRED = "Ability registry must hold jelly_heal";
    private static final String SHOULD_HEAL_COW = "The streamed cow should heal past %.1f, stands at %.2f";
    private static final String SHOULD_HEAL_CASTER = "The caster should heal past %.1f, stands at %.2f";
    private static final String HEAL_TOUCHES_FULL_COW = "heal_touches_a_full_health_cow";
    /** A few held ticks, enough for the heal to strike the cone. */
    private static final int TOUCH_HOLD_TICKS = 5;
    private static final String HEAL_HARMS_ZOMBIE = "heal_harms_a_zombie";
    private static final String SHOULD_HARM_ZOMBIE =
            "The healed zombie should lose health where a living mob would gain it, lost %.2f";
    private static final String HEAL_PULSES_ON_COW = "heal_pulses_on_a_wounded_cow";
    private static final String HEAL_PULSES_ON_CASTER = "heal_pulses_on_a_wounded_caster";
    /** The pulse intervals a pulse test's hold spans. */
    static final int HELD_PULSES = 3;
    private static final float HEALTH_TOLERANCE = 1e-3f;
    private static final String SHOULD_STAND_PULSED = "After held tick %d health should stand at %.2f, stands at %.2f";
    private static final String SHOULD_TOUCH_FULL_COW =
            "A heal payload should name the full-health cow among its touched mobs; %d payloads named none";

    private StreamDeliveryTests() {
    }

    /**
     * A mock player holds blaze flames at a zombie four blocks ahead for
     * twenty ticks: the zombie burns and the blaze goo drops.
     *
     * @param helper the gametest helper
     */
    public static void blazeFlames(GameTestHelper helper) {
        AbilityDefinition flames = AbilityRegistry.of(helper.getLevel()).getAbility(BLAZE_FLAMES);
        helper.assertTrue(flames != null, ABILITY_REQUIRED);
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        ServerPlayer player = streamer(helper, GooTypes.BLAZE);
        KnownRecipes.teachRequires(player, flames);
        int heldBefore = blazeHeld(player);
        helper.assertFalse(zombie.isOnFire(), SHOULD_START_UNBURNT);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.BLAZE), BLAZE_FLAMES.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int drained = heldBefore - blazeHeld(player);
            helper.assertTrue(zombie.isOnFire(), SHOULD_BURN);
            helper.assertTrue(drained > 0, String.format(SHOULD_DRAIN, drained));
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * A hungry, hurt mock player holds jelly heal at a hurt cow four blocks
     * ahead for twenty ticks: the cow and the caster both stand healthier
     * than they started. The caster's food sits below natural regeneration,
     * so only the stream heals it.
     *
     * @param helper the gametest helper
     */
    public static void vitalityHealsCowAndCaster(GameTestHelper helper) {
        AbilityDefinition vitality = AbilityRegistry.of(helper.getLevel()).getAbility(JELLY_HEAL);
        helper.assertTrue(vitality != null, VITALITY_REQUIRED);
        Cow cow = helper.spawnWithNoFreeWill(EntityType.COW, ZOMBIE_POS);
        cow.setHealth(HURT_COW_HEALTH);
        ServerPlayer player = streamer(helper, GooTypes.JELLY);
        KnownRecipes.teachRequires(player, vitality);
        player.setHealth(HURT_PLAYER_HEALTH);
        player.getFoodData().setFoodLevel(FOOD_BELOW_REGEN);
        player.getFoodData().setSaturation(0);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.JELLY), JELLY_HEAL.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(cow.getHealth() > HURT_COW_HEALTH,
                    String.format(SHOULD_HEAL_COW, HURT_COW_HEALTH, cow.getHealth()));
            helper.assertTrue(player.getHealth() > HURT_PLAYER_HEALTH,
                    String.format(SHOULD_HEAL_CASTER, HURT_PLAYER_HEALTH, player.getHealth()));
            helper.succeed();
        });
    }

    private static int blazeHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.BLAZE, 0);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    static ServerPlayer streamer(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setXRot(LOOKING_AT_ZOMBIE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
