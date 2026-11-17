package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.typhoon.ShulkerSpew;
import com.mercuriusxeno.goo.block.ability.LeechDrain;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.HeardSounds;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the server's sounds whose source sits at or beside a
 * player's head: that player is sent the sound to play at the listener and
 * no positioned play, and every other player near is sent it at the point it
 * played before; a source on a body that is no player plays positioned for
 * everyone.
 * goo-sound-at-the-listener-reaches-both-ears
 * sweep-checks-every-goo-audio-source-location
 */
@RegistersGameTests
public final class HeadSoundTests {

    private static final String NOVA_CASTER_HEARS_AT_THE_LISTENER = "frost_nova_caster_hears_its_sound_at_the_listener";
    private static final String STEP_ON_A_PLAYER_AT_THE_LISTENER = "sound_step_on_a_player_plays_at_their_listener";
    private static final String STEP_ON_A_MOB_POSITIONED = "sound_step_on_a_mob_plays_at_its_body_centre";
    private static final String SPEW_AT_THE_LISTENER = "shulker_spew_reaches_its_caster_at_the_listener";
    private static final String DRAIN_AT_THE_LISTENER = "leech_drain_reaches_its_victim_at_the_listener";
    private static final Identifier FROST_NOVA = Identifier.parse("goo:frost_nova");
    private static final Identifier GLASS_BREAK = Identifier.parse("minecraft:block.glass.break");
    private static final Identifier SHULKER_SHOOT = Identifier.parse("minecraft:entity.shulker.shoot");
    private static final Identifier SOUL_ESCAPE = Identifier.parse("minecraft:particle.soul_escape");
    private static final double BODY_CENTRE = 0.5;
    /** Where the onlooker stands from the player the sound plays at, in earshot. */
    private static final Vec3 ONLOOKER_OFFSET = new Vec3(0, 0, 3);
    /** Where the pig stands from the player, in earshot. */
    private static final Vec3 PIG_OFFSET = new Vec3(2, 0, 0);
    /** Where the held blob stands from the caster's eye, a hand's width ahead and down. */
    private static final Vec3 BLOB_OFFSET = new Vec3(0.3, -0.3, 0.4);

    private HeadSoundTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, NOVA_CASTER_HEARS_AT_THE_LISTENER,
                HeadSoundTests::frostNovaCasterHearsItsSoundAtTheListener);
        GooTestFunctions.registerTest(registrar, STEP_ON_A_PLAYER_AT_THE_LISTENER,
                HeadSoundTests::soundStepOnAPlayerPlaysAtTheirListener);
        GooTestFunctions.registerTest(registrar, STEP_ON_A_MOB_POSITIONED,
                HeadSoundTests::soundStepOnAMobPlaysAtItsBodyCentre);
        GooTestFunctions.registerTest(registrar, SPEW_AT_THE_LISTENER,
                HeadSoundTests::shulkerSpewReachesItsCasterAtTheListener);
        GooTestFunctions.registerTest(registrar, DRAIN_AT_THE_LISTENER,
                HeadSoundTests::leechDrainReachesItsVictimAtTheListener);
    }

    /**
     * A Frost Nova released through the charge payload sends its caster
     * Nova's glass break at the listener and none positioned, and sends an
     * onlooker one glass break at the caster's body centre.
     *
     * @param helper the gametest helper
     */
    public static void frostNovaCasterHearsItsSoundAtTheListener(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        AbilityDefinition nova = AbilityRegistry.of(helper.getLevel()).getAbility(FROST_NOVA);
        helper.assertTrue(nova != null, "Ability registry must hold goo:frost_nova");
        KnownRecipes.teachRequires(caster, nova);
        ServerPlayer onlooker = onlookerBeside(helper, caster);
        PacketRecorder casterHeard = PacketRecorder.attachClaiming(caster, ListenerSoundPayload.class);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        Vec3 bodyCentre = bodyCentreOf(caster);
        GooThrowHandler.releaseCharge(caster, new GooChargePayload(GooTypes.id(GooTypes.FROST),
                FROST_NOVA.toString(), nova.delivery().charge().maxTicks()));
        assertHeardAtTheListener(helper, casterHeard, GLASS_BREAK, "Frost Nova's caster");
        assertHeardPositioned(helper, onlookerHeard, GLASS_BREAK, bodyCentre, "Frost Nova's onlooker");
        removeAll(helper, caster, onlooker);
        helper.succeed();
    }

    /**
     * A sound step run on a player host is handed to that player at the
     * listener and none positioned, and to an onlooker at the player's body
     * centre.
     *
     * @param helper the gametest helper
     */
    public static void soundStepOnAPlayerPlaysAtTheirListener(GameTestHelper helper) {
        ServerPlayer target = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        ServerPlayer onlooker = onlookerBeside(helper, target);
        PacketRecorder targetHeard = PacketRecorder.attachClaiming(target, ListenerSoundPayload.class);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        new EntityHost(helper.getLevel(), target, null).playSound(SoundCue.of(GLASS_BREAK));
        assertHeardAtTheListener(helper, targetHeard, GLASS_BREAK, "A sound step's player target");
        assertHeardPositioned(helper, onlookerHeard, GLASS_BREAK, bodyCentreOf(target), "A sound step's onlooker");
        removeAll(helper, target, onlooker);
        helper.succeed();
    }

    /**
     * A sound step run on a mob plays once at the mob's body centre for its
     * thrower and an onlooker alike, and hands no one the at-listener form.
     *
     * @param helper the gametest helper
     */
    public static void soundStepOnAMobPlaysAtItsBodyCentre(GameTestHelper helper) {
        ServerPlayer thrower = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        ServerPlayer onlooker = onlookerBeside(helper, thrower);
        Pig pig = helper.spawn(EntityType.PIG, helper.relativeVec(thrower.position().add(PIG_OFFSET)));
        PacketRecorder throwerHeard = PacketRecorder.attachClaiming(thrower, ListenerSoundPayload.class);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        new EntityHost(helper.getLevel(), pig, thrower).playSound(SoundCue.of(GLASS_BREAK));
        Vec3 pigCentre = bodyCentreOf(pig);
        assertHeardPositioned(helper, throwerHeard, GLASS_BREAK, pigCentre, "A sound step on a mob, its thrower");
        assertHeardPositioned(helper, onlookerHeard, GLASS_BREAK, pigCentre, "A sound step on a mob, its onlooker");
        helper.assertTrue(throwerHeard.payloadsOf(ListenerSoundPayload.class).isEmpty(),
                "A sound step on a mob should hand its thrower nothing at the listener");
        removeAll(helper, thrower, onlooker);
        helper.succeed();
    }

    /**
     * A shulker spew from the held blob sends its caster the shoot at the
     * listener and none positioned, and sends an onlooker the shoot at the blob.
     *
     * @param helper the gametest helper
     */
    public static void shulkerSpewReachesItsCasterAtTheListener(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON);
        ServerPlayer onlooker = onlookerBeside(helper, caster);
        Pig taken = helper.spawn(EntityType.PIG, helper.relativeVec(caster.position().add(PIG_OFFSET)));
        PacketRecorder casterHeard = PacketRecorder.attachClaiming(caster, ListenerSoundPayload.class);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        Vec3 blob = caster.getEyePosition().add(BLOB_OFFSET);
        ShulkerSpew.spew(caster, taken, blob).discard();
        assertHeardAtTheListener(helper, casterHeard, SHULKER_SHOOT, "A shulker spew's caster");
        assertHeardPositioned(helper, onlookerHeard, SHULKER_SHOOT, blob, "A shulker spew's onlooker");
        removeAll(helper, caster, onlooker);
        helper.succeed();
    }

    /**
     * A leech drain on a player sends them the drain's sound at the listener
     * and none positioned, and sends an onlooker the sound at the victim's
     * body centre.
     *
     * @param helper the gametest helper
     */
    public static void leechDrainReachesItsVictimAtTheListener(GameTestHelper helper) {
        ServerPlayer victim = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        ServerPlayer onlooker = onlookerBeside(helper, victim);
        PacketRecorder victimHeard = PacketRecorder.attachClaiming(victim, ListenerSoundPayload.class);
        PacketRecorder onlookerHeard = PacketRecorder.attachTo(onlooker);
        LeechDrain.soundTheDrain(helper.getLevel(), victim);
        assertHeardAtTheListener(helper, victimHeard, SOUL_ESCAPE, "A leech drain's player victim");
        assertHeardPositioned(helper, onlookerHeard, SOUL_ESCAPE, bodyCentreOf(victim), "A leech drain's onlooker");
        removeAll(helper, victim, onlooker);
        helper.succeed();
    }

    /**
     * A player standing in earshot of another.
     *
     * @param helper the gametest helper
     * @param beside the player the onlooker stands near
     * @return the onlooker
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer onlookerBeside(GameTestHelper helper, ServerPlayer beside) {
        ServerPlayer onlooker = helper.makeMockServerPlayerInLevel();
        Vec3 stand = beside.position().add(ONLOOKER_OFFSET);
        onlooker.setPos(stand.x, stand.y, stand.z);
        return onlooker;
    }

    private static Vec3 bodyCentreOf(Entity entity) {
        return new Vec3(entity.getX(), entity.getY(BODY_CENTRE), entity.getZ());
    }

    private static void assertHeardAtTheListener(GameTestHelper helper, PacketRecorder heard, Identifier sound,
                                                 String who) {
        long atListener = heard.payloadsOf(ListenerSoundPayload.class).stream()
                .filter(payload -> payload.cue().sound().equals(sound)).count();
        helper.assertTrue(atListener == 1 && HeardSounds.of(heard, sound).isEmpty(), who + " should be sent " + sound
                + " once at the listener and never positioned; sent " + atListener + " at the listener and "
                + "positioned at " + HeardSounds.describe(HeardSounds.of(heard, sound)));
    }

    private static void assertHeardPositioned(GameTestHelper helper, PacketRecorder heard, Identifier sound,
                                              Vec3 at, String who) {
        helper.assertTrue(HeardSounds.of(heard, sound).size() == 1 && HeardSounds.at(heard, sound, at) == 1,
                who + " should be sent " + sound + " once at " + at + ", sent "
                        + HeardSounds.describe(HeardSounds.of(heard, sound)));
    }

    private static void removeAll(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }
}
