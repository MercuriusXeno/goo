package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.PacketRecorder;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for glow's Laser through the real channel path: a mock player
 * holds the ray for one tick, its first tick landing a hit. Struck directly,
 * a zombie loses health and burns; aimed at a block, the one ray strikes the
 * block and spares the mobs beside it (decision prism-leaves-the-mod). The
 * impact's burst fires where the ray lands, its sound on hits (decision
 * sunbeam-lands-with-impact-and-aim).
 */
@RegistersGameTests
public final class LaserChannelTests {
    private static final String LASER_BURNS_THE_UNDEAD_IT_STRIKES = "laser_burns_the_undead_it_strikes";
    private static final String LASER_IMPACT_FIRES_AT_THE_HIT = "laser_impact_fires_at_the_hit";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LASER_BURNS_THE_UNDEAD_IT_STRIKES, LaserChannelTests::laserBurnsTheUndeadItStrikes);
        GooTestFunctions.registerTest(registrar, LASER_IMPACT_FIRES_AT_THE_HIT, LaserChannelTests::laserImpactFiresAtTheHit);
        GooTestFunctions.registerTest(registrar, SUNBEAM_STRIKES_ONE_TARGET_AND_SPARES_THE_REST,
                LaserChannelTests::sunbeamStrikesOneTargetAndSparesTheRest);
    }

    private static final String SUNBEAM_STRIKES_ONE_TARGET_AND_SPARES_THE_REST =
            "sunbeam_strikes_one_target_and_spares_the_rest";
    /** Two cells beside the struck block, in clear sight of it and off the ray's line. */
    private static final List<BlockPos> BESIDE_THE_STRUCK_BLOCK = List.of(new BlockPos(4, 1, 1),
            new BlockPos(4, 1, 5));
    private static final String SPARED_STRUCK = "Zombie %d stands off the ray and should take no hit, it lost %s";
    private static final String BLOCK_UNSTRUCK = "The ray should land on the aimed block, its impact sounded %d times";

    /**
     * A mock player holds Sunbeam at a stone block with two zombies standing
     * beside it in clear sight, off the ray: the ray lands on the block, its
     * impact sounding there on the hit, and neither zombie loses health, since
     * the one ray strikes only what lies along the aim.
     * decision prism-leaves-the-mod
     *
     * @param helper the gametest helper
     */
    public static void sunbeamStrikesOneTargetAndSparesTheRest(GameTestHelper helper) {
        helper.setBlock(STRUCK_POS.below(), Blocks.STONE);
        helper.setBlock(STRUCK_POS, Blocks.STONE);
        List<Zombie> zombies = BESIDE_THE_STRUCK_BLOCK.stream().map(pos -> helmeted(helper, pos)).toList();
        List<Float> before = zombies.stream().map(Zombie::getHealth).toList();
        ServerPlayer player = holdAt(helper, Vec3.atCenterOf(helper.absolutePos(STRUCK_POS)));
        PacketRecorder recorder = PacketRecorder.attachTo(player);
        AABB block = new AABB(helper.absolutePos(STRUCK_POS)).inflate(SOUND_GRID + TOLERANCE);
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            long struck = recorder.sentOf(ClientboundSoundPacket.class).stream().filter(packet ->
                    packet.getSound().is(SoundEvents.BEACON_POWER_SELECT.location())
                            && block.contains(packet.getX(), packet.getY(), packet.getZ())).count();
            helper.assertTrue(struck == 1, String.format(BLOCK_UNSTRUCK, struck));
            for (int i = 0; i < zombies.size(); i++) {
                float lost = before.get(i) - zombies.get(i).getHealth();
                helper.assertTrue(lost == 0, String.format(SPARED_STRUCK, i, lost));
            }
            helper.succeed();
        });
    }


    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final BlockPos STRUCK_POS = new BlockPos(4, 1, 3);
    /** A block level with the standing player's eye, east of where the player stands. */
    private static final BlockPos WALL_POS = new BlockPos(4, 2, 3);
    /** A sound packet carries its position in eighths of a block, read back as a float. */
    private static final double SOUND_GRID = 0.125;
    private static final String NO_SOUND = "The impact's sound should reach the player once at %s on the hit, it reached %d";
    private static final String NO_FLAME = "The impact's flame should reach the player at %s over the hold";
    private static final float TOLERANCE = 0.01f;
    private static final double BODY_HEIGHT = 1.0;
    private static final int HELD_GOO = 2;
    private static final Identifier LASER = Identifier.parse("goo:glow_laser");
    private static final String ABILITY_REQUIRED = "Ability registry must hold glow_laser";
    private static final String WHOLE_HIT = "The struck zombie should lose health to the hit, it lost %s";
    private static final String NOT_BURNING = "The struck zombie is undead and should burn";

    private LaserChannelTests() {
    }

    /**
     * A mock player holds Laser on a zombie: the zombie loses health and
     * burns.
     *
     * @param helper the gametest helper
     */
    public static void laserBurnsTheUndeadItStrikes(GameTestHelper helper) {
        helper.setBlock(STRUCK_POS.below(), Blocks.STONE);
        Zombie zombie = helmeted(helper, STRUCK_POS);
        float before = zombie.getHealth();
        ServerPlayer player = holdAt(helper, zombie.position().add(0, BODY_HEIGHT, 0));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = before - zombie.getHealth();
            helper.assertTrue(lost > 0, String.format(WHOLE_HIT, lost));
            helper.assertTrue(zombie.isOnFire(), NOT_BURNING);
            helper.succeed();
        });
    }

    /**
     * A mock player holds Laser on a stone block's face, the first tick
     * hitting: the impact's sound reaches the player at the point the ray
     * lands once on that hit tick, and the hold runs on until the impact's
     * flame reaches the player there, on whichever held tick the impact
     * branch fires it (decision sunbeam-lands-with-impact-and-aim). The
     * end_rod burst left the impact for the client's rising motes
     * (decision sunbeam-sheds-motes-rising-from-the-beam).
     *
     * @param helper the gametest helper
     */
    public static void laserImpactFiresAtTheHit(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        ServerPlayer player = standWithGlove(helper, STAND_POS);
        PacketRecorder recorder = PacketRecorder.attachTo(player);
        Vec3 eye = player.getEyePosition();
        Vec3 face = new Vec3(helper.absolutePos(WALL_POS).getX(), eye.y, eye.z);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.GLOW), LASER.toString(), eye, face);
        long[] soundsAfterTheHit = {-1};
        int[] held = {0};
        // held until the flame shows, so the wait follows the impact branch however it is tuned
        helper.onEachTick(() -> {
            if (held[0] == 1) {
                soundsAfterTheHit[0] = soundsAt(recorder, face);
            }
            if (flamesAt(recorder, face) == 0) {
                held[0]++;
                GooStreamHandler.streamTick(player, tick);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(flamesAt(recorder, face) > 0, String.format(NO_FLAME, face));
            helper.assertTrue(soundsAfterTheHit[0] == 1, String.format(NO_SOUND, face, soundsAfterTheHit[0]));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static long flamesAt(PacketRecorder recorder, Vec3 point) {
        return recorder.sentOf(ClientboundLevelParticlesPacket.class).stream().filter(packet ->
                packet.particle().getType() == ParticleTypes.SMALL_FLAME
                        && point.distanceTo(new Vec3(packet.x(), packet.y(), packet.z())) < TOLERANCE).count();
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
        Zombie zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, pos);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * Stands a mock player holding glow goo in a glove and holds Laser at a
     * point for one tick, the hold's first, which lands a hit.
     *
     * @param helper the gametest helper
     * @param aim    the point the cursor rests on
     * @return the player
     */
    private static ServerPlayer holdAt(GameTestHelper helper, Vec3 aim) {
        ServerPlayer player = standWithGlove(helper, STAND_POS);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.GLOW), LASER.toString(),
                player.getEyePosition(), aim, helper.absolutePos(STAND_POS.below()), Direction.UP.get3DDataValue());
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        return player;
    }

    /**
     * Stands a mock player holding glow goo in a glove, taught Laser.
     *
     * @param helper the gametest helper
     * @param pos    where the player stands, relative to the test
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer standWithGlove(GameTestHelper helper, BlockPos pos) {
        AbilityDefinition laser = AbilityRegistry.of(helper.getLevel()).getAbility(LASER);
        helper.assertTrue(laser != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(pos));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.GLOW, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, laser);
        return player;
    }
}
