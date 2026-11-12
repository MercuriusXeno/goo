package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for glow's Sunbeam through the real channel path: a mock player
 * holds the ray for one tick, its first tick landing a hit. Struck directly,
 * a zombie takes the whole undead hit and burns; aimed at a prism, the ray
 * refracts to three zombies around it, whose split hits sum past one direct
 * hit (decision sunbeam-splits-at-the-prism-with-a-glisten). A zombie the
 * aim assist locks off the crosshair takes the hit, and the impact's burst
 * fires where the ray lands each tick, its sound on hits (decision sunbeam-lands-with-impact-and-aim).
 */
@RegistersGameTests
public final class SunbeamChannelTests {
    private static final String SUNBEAM_REFRACTS_TO_THREE = "sunbeam_refracts_to_three";
    private static final String SUNBEAM_BURNS_THE_UNDEAD_IT_STRIKES = "sunbeam_burns_the_undead_it_strikes";
    private static final String SUNBEAM_IMPACT_FIRES_AT_THE_HIT = "sunbeam_impact_fires_at_the_hit";
    private static final String SUNBEAM_CONVERTS_A_BARE_PRISM = "sunbeam_converts_a_bare_prism";
    private static final String SUNBEAM_WARMS_A_BARE_PRISM_AS_IT_CHARGES = "sunbeam_warms_a_bare_prism_as_it_charges";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SUNBEAM_REFRACTS_TO_THREE, SunbeamChannelTests::sunbeamRefractsToThree);
        GooTestFunctions.registerTest(registrar, SUNBEAM_BURNS_THE_UNDEAD_IT_STRIKES, SunbeamChannelTests::sunbeamBurnsTheUndeadItStrikes);
        GooTestFunctions.registerTest(registrar, SUNBEAM_IMPACT_FIRES_AT_THE_HIT, SunbeamChannelTests::sunbeamImpactFiresAtTheHit);
        GooTestFunctions.registerTest(registrar, SUNBEAM_CONVERTS_A_BARE_PRISM, SunbeamChannelTests::sunbeamConvertsABarePrism);
        GooTestFunctions.registerTest(registrar, SUNBEAM_WARMS_A_BARE_PRISM_AS_IT_CHARGES,
                SunbeamChannelTests::sunbeamWarmsABarePrismAsItCharges);
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final BlockPos PRISM_POS = new BlockPos(4, 1, 3);
    /** Three cells around the prism, each inside test_bay's 6 by 6 by 6 bounds, where no neighbouring test reaches. */
    private static final List<BlockPos> AROUND_THE_PRISM = List.of(new BlockPos(4, 1, 1), new BlockPos(4, 1, 5),
            new BlockPos(5, 1, 3));
    private static final BlockPos STRUCK_POS = new BlockPos(4, 1, 3);
    /** A block level with the standing player's eye, east of where the player stands. */
    private static final BlockPos WALL_POS = new BlockPos(4, 2, 3);
    /** A sound packet carries its position in eighths of a block, read back as a float. */
    private static final double SOUND_GRID = 0.125;
    private static final String NO_SOUND = "The impact's sound should reach the player once at %s on the hit, it reached %d";
    private static final String NO_FLAME = "The impact's flame should reach the player at %s on the hold's second tick";
    /** Sunbeam's direct hit on an undead mob: 4 doubled. */
    private static final float UNDEAD_HIT = 8f;
    private static final float TOLERANCE = 0.01f;
    private static final double BODY_HEIGHT = 1.0;
    private static final int HELD_GOO = 2;
    private static final Identifier SUNBEAM = Identifier.parse("goo:glow_sunbeam");
    private static final String ABILITY_REQUIRED = "Ability registry must hold glow_sunbeam";
    private static final String UNTOUCHED = "Refracted zombie %d should take a share of the hit, it lost %s";
    private static final String WHOLE_HIT = "The struck zombie should lose at least one whole undead hit of %s, it lost %s";
    private static final String NOT_BURNING = "The struck zombie is undead and should burn";
    /** Zap, whose relay is the combo the refracting prism holds. */
    private static final Identifier RELAY_HOST = Identifier.parse("goo:pulse_zap");
    private static final String RELAY_REQUIRED = "The prism should take Zap's relay combo";
    private static final String NOT_CONVERTED = "A full Sunbeam hold should make the prism a reflector, held '%s'";

    private SunbeamChannelTests() {
    }

    /**
     * A mock player holds Sunbeam at a prism with three zombies in clear
     * line around it: each zombie loses a share of the hit.
     *
     * @param helper the gametest helper
     */
    public static void sunbeamRefractsToThree(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        // channeled-hosts-convert-prisms-over-the-hold: only a prism holding a combo refracts Sunbeam
        PrismBlockEntity prism = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class);
        AbilityDefinition zap = AbilityRegistry.of(helper.getLevel()).getAbility(RELAY_HOST);
        helper.assertTrue(zap != null && prism.runCombo(GooTypes.PULSE, RELAY_HOST.toString(),
                PrismCombos.comboSteps(zap)), RELAY_REQUIRED);
        List<Zombie> zombies = AROUND_THE_PRISM.stream().map(pos -> helmeted(helper, pos)).toList();
        List<Float> before = zombies.stream().map(Zombie::getHealth).toList();
        ServerPlayer player = holdAt(helper, Vec3.atCenterOf(helper.absolutePos(PRISM_POS)));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            for (int i = 0; i < zombies.size(); i++) {
                float lost = before.get(i) - zombies.get(i).getHealth();
                helper.assertTrue(lost > 0, String.format(UNTOUCHED, i, lost));
            }
            helper.succeed();
        });
    }

    /**
     * A mock player holds Sunbeam on a zombie: the zombie loses at least one
     * whole undead hit, the burn it starts taking its own share, and burns.
     *
     * @param helper the gametest helper
     */
    public static void sunbeamBurnsTheUndeadItStrikes(GameTestHelper helper) {
        helper.setBlock(STRUCK_POS.below(), Blocks.STONE);
        Zombie zombie = helmeted(helper, STRUCK_POS);
        float before = zombie.getHealth();
        ServerPlayer player = holdAt(helper, zombie.position().add(0, BODY_HEIGHT, 0));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = before - zombie.getHealth();
            helper.assertTrue(lost >= UNDEAD_HIT - TOLERANCE, String.format(WHOLE_HIT, UNDEAD_HIT, lost));
            helper.assertTrue(zombie.isOnFire(), NOT_BURNING);
            helper.succeed();
        });
    }

    /**
     * A mock player holds Sunbeam on a stone block's face for two ticks, the
     * first hitting and the second between hits: the impact's sound reaches
     * the player at the point the ray lands on the hit tick alone, and its
     * flame on the hold's second tick, glow_sunbeam.json's impact branch on
     * held (decision sunbeam-lands-with-impact-and-aim). The end_rod burst
     * left the impact for the client's rising motes
     * (decision sunbeam-sheds-motes-rising-from-the-beam).
     *
     * @param helper the gametest helper
     */
    public static void sunbeamImpactFiresAtTheHit(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        ServerPlayer player = standWithGlove(helper, STAND_POS);
        PacketRecorder recorder = PacketRecorder.attachTo(player);
        Vec3 eye = player.getEyePosition();
        Vec3 face = new Vec3(helper.absolutePos(WALL_POS).getX(), eye.y, eye.z);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.GLOW), SUNBEAM.toString(), eye, face);
        long[] soundsAfterTheHit = new long[1];
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        helper.runAfterDelay(2, () -> {
            soundsAfterTheHit[0] = soundsAt(recorder, face);
            GooStreamHandler.streamTick(player, tick);
        });
        helper.runAfterDelay(3, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(soundsAfterTheHit[0] == 1, String.format(NO_SOUND, face, soundsAfterTheHit[0]));
            helper.assertTrue(flamesAt(recorder, face) > 0, String.format(NO_FLAME, face));
            helper.succeed();
        });
    }

    private static long flamesAt(PacketRecorder recorder, Vec3 point) {
        return recorder.sentOf(ClientboundLevelParticlesPacket.class).stream().filter(packet ->
                packet.getParticle().getType() == ParticleTypes.SMALL_FLAME
                        && point.distanceTo(new Vec3(packet.getX(), packet.getY(), packet.getZ())) < TOLERANCE).count();
    }

    private static long soundsAt(PacketRecorder recorder, Vec3 point) {
        return recorder.sentOf(ClientboundSoundPacket.class).stream().filter(packet ->
                packet.getSound().is(SoundEvents.BEACON_POWER_SELECT.location()) && soundsAt(packet, point)).count();
    }

    /**
     * Whether a sound packet sounds at a point, within the eighth of a block
     * it carries and the float it reads back as, which far from the origin
     * rounds past an eighth.
     *
     * @param packet the sound packet
     * @param point  the point
     * @return true where every axis lies within that tolerance
     */
    private static boolean soundsAt(ClientboundSoundPacket packet, Vec3 point) {
        return nearOnAxis(packet.getX(), point.x) && nearOnAxis(packet.getY(), point.y)
                && nearOnAxis(packet.getZ(), point.z);
    }

    private static boolean nearOnAxis(double sent, double expected) {
        return Math.abs(sent - expected) <= SOUND_GRID + Math.ulp((float) expected);
    }

    private static Zombie helmeted(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Stands a mock player holding glow goo in a glove and holds Sunbeam at a
     * point for one tick, the hold's first, which lands a hit.
     *
     * @param helper the gametest helper
     * @param aim    the point the cursor rests on
     * @return the player
     */
    private static ServerPlayer holdAt(GameTestHelper helper, Vec3 aim) {
        ServerPlayer player = standWithGlove(helper, STAND_POS);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.GLOW), SUNBEAM.toString(),
                player.getEyePosition(), aim, helper.absolutePos(STAND_POS.below()), Direction.UP.get3DDataValue());
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        return player;
    }

    /**
     * A mock player holds Sunbeam on a bare prism for one charge's ticks: the
     * prism converts into the reflector combo, Sunbeam's on_prism reaction.
     * channeled-hosts-convert-prisms-over-the-hold
     *
     * @param helper the gametest helper
     */
    public static void sunbeamConvertsABarePrism(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        ServerPlayer player = standWithGlove(helper, STAND_POS);
        int hold = AbilityRegistry.of(helper.getLevel()).getAbility(SUNBEAM).delivery().ticksPerCharge();
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.GLOW), SUNBEAM.toString(),
                player.getEyePosition(), Vec3.atCenterOf(helper.absolutePos(PRISM_POS)),
                helper.absolutePos(STAND_POS.below()), Direction.UP.get3DDataValue());
        for (int held = 1; held <= hold; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(hold + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            String combo = helper.getBlockEntity(PRISM_POS, PrismBlockEntity.class).getCombo();
            helper.assertTrue(SUNBEAM.toString().equals(combo), String.format(NOT_CONVERTED, combo));
            helper.succeed();
        });
    }

    private static final String NOT_WARMING = "Half a charge of Sunbeam should warm the bare prism toward its combo, "
            + "its share stood at %s";

    /**
     * A mock player holds Sunbeam on a bare prism for half a charge: the
     * prism warms toward the reflector it is becoming, its exposure share
     * rising with the charge, so the conversion shows rather than jumps.
     * prism-conversion-creeps-over-the-hold
     *
     * @param helper the gametest helper
     */
    public static void sunbeamWarmsABarePrismAsItCharges(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        ServerPlayer player = standWithGlove(helper, STAND_POS);
        int half = AbilityRegistry.of(helper.getLevel()).getAbility(SUNBEAM).delivery().ticksPerCharge() / 2;
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.GLOW), SUNBEAM.toString(),
                player.getEyePosition(), Vec3.atCenterOf(helper.absolutePos(PRISM_POS)),
                helper.absolutePos(STAND_POS.below()), Direction.UP.get3DDataValue());
        for (int held = 1; held <= half; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(half + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float share = com.mercuriusxeno.goo.registry.GooServerState.of(helper.getLevel().getServer())
                    .blockExposures().shareAt(helper.getLevel(), helper.absolutePos(PRISM_POS));
            helper.assertTrue(share > 0f, String.format(NOT_WARMING, share));
            helper.succeed();
        });
    }

    /**
     * Stands a mock player holding glow goo in a glove, taught Sunbeam.
     *
     * @param helper the gametest helper
     * @param pos    where the player stands, relative to the test
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer standWithGlove(GameTestHelper helper, BlockPos pos) {
        AbilityDefinition sunbeam = AbilityRegistry.of(helper.getLevel()).getAbility(SUNBEAM);
        helper.assertTrue(sunbeam != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(pos));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.GLOW, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, sunbeam);
        return player;
    }
}
