package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
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

/**
 * Gametests for glow's Sunbeam through the real channel path: a mock player
 * holds the ray for one tick, its first tick landing a hit. Struck directly,
 * a zombie takes the whole undead hit and burns; aimed at a prism, the ray
 * refracts to three zombies around it, whose split hits sum past one direct
 * hit (decision sunbeam-splits-at-the-prism-with-a-glisten). A zombie the
 * aim assist locks off the crosshair takes the hit, and the impact's burst
 * and sound fire where the ray lands (decision sunbeam-lands-with-impact-and-aim).
 */
public final class SunbeamChannelTests {

    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    private static final BlockPos PRISM_POS = new BlockPos(4, 1, 3);
    private static final List<BlockPos> AROUND_THE_PRISM = List.of(new BlockPos(4, 1, 1), new BlockPos(4, 1, 5),
            new BlockPos(6, 1, 3));
    private static final BlockPos STRUCK_POS = new BlockPos(4, 1, 3);
    private static final BlockPos NEAR_CORNER_POS = new BlockPos(0, 1, 0);
    private static final BlockPos FAR_ZOMBIE_POS = new BlockPos(5, 1, 5);
    /** A block level with the standing player's eye, east of where the player stands. */
    private static final BlockPos WALL_POS = new BlockPos(4, 2, 3);
    /** A sound packet carries its position in eighths of a block, read back as a float. */
    private static final double SOUND_GRID = 0.125;
    private static final String NO_BURST = "The impact's light burst should reach the player at %s";
    private static final String NO_SOUND = "The impact's sound should reach the player at %s";
    /** Sunbeam's direct hit on an undead mob: 4 doubled. */
    private static final float UNDEAD_HIT = 8f;
    private static final float TOLERANCE = 0.01f;
    private static final double BODY_HEIGHT = 1.0;
    /** How far the crosshair sits off a mob the aim assist still locks: inside its 4 degree cone. */
    private static final float OFF_THE_CROSSHAIR = (float) Math.toRadians(3.5);
    private static final int HELD_GOO = 2;
    private static final Identifier SUNBEAM = Identifier.parse("goo:glow_sunbeam");
    private static final String ABILITY_REQUIRED = "Ability registry must hold glow_sunbeam";
    private static final String UNTOUCHED = "Refracted zombie %d should take a share of the hit, it lost %s";
    private static final String SUM_TOO_LOW = "The split hits should sum past one direct hit of %s, they summed %s";
    private static final String WHOLE_HIT = "The struck zombie should lose at least one whole undead hit of %s, it lost %s";
    private static final String NOT_BURNING = "The struck zombie is undead and should burn";

    private SunbeamChannelTests() {
    }

    /**
     * A mock player holds Sunbeam at a prism with three zombies in clear
     * line around it: each zombie loses a share of the hit, and the shares
     * sum past one direct hit.
     *
     * @param helper the gametest helper
     */
    public static void sunbeamRefractsToThree(GameTestHelper helper) {
        helper.setBlock(PRISM_POS.below(), Blocks.STONE);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        List<Zombie> zombies = AROUND_THE_PRISM.stream().map(pos -> helmeted(helper, pos)).toList();
        List<Float> before = zombies.stream().map(Zombie::getHealth).toList();
        ServerPlayer player = holdAt(helper, Vec3.atCenterOf(helper.absolutePos(PRISM_POS)));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float sum = 0;
            for (int i = 0; i < zombies.size(); i++) {
                float lost = before.get(i) - zombies.get(i).getHealth();
                helper.assertTrue(lost > 0, String.format(UNTOUCHED, i, lost));
                sum += lost;
            }
            helper.assertTrue(sum > UNDEAD_HIT, String.format(SUM_TOO_LOW, UNDEAD_HIT, sum));
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
     * A mock player holds Sunbeam with the crosshair just off a zombie the
     * aim assist locks and outlines, sending the point the client sends for
     * that hold: the zombie takes the hit
     * (decision sunbeam-lands-with-impact-and-aim).
     *
     * @param helper the gametest helper
     */
    public static void sunbeamHitsTheAimedZombie(GameTestHelper helper) {
        helper.setBlock(FAR_ZOMBIE_POS.below(), Blocks.STONE);
        Zombie zombie = helmeted(helper, FAR_ZOMBIE_POS);
        float before = zombie.getHealth();
        ServerPlayer player = standWithGlove(helper, NEAR_CORNER_POS);
        Vec3 eye = player.getEyePosition();
        Vec3 look = zombie.position().add(0, BODY_HEIGHT, 0).subtract(eye).normalize().yRot(OFF_THE_CROSSHAIR);
        Vec3 crosshair = eye.add(look.scale(player.blockInteractionRange()));
        AbilityDefinition sunbeam = AbilityRegistry.of(helper.getLevel()).getAbility(SUNBEAM);
        Vec3 aim = ChannelAim.heldAimPoint(sunbeam != null && sunbeam.hasTag(AbilityTags.LOCKS_ON),
                zombie.getBoundingBox().getCenter(), crosshair);
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.GLOW), SUNBEAM.toString(), eye, aim);
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            float lost = before - zombie.getHealth();
            helper.assertTrue(lost >= UNDEAD_HIT - TOLERANCE, String.format(WHOLE_HIT, UNDEAD_HIT, lost));
            helper.succeed();
        });
    }

    /**
     * A mock player holds Sunbeam on a stone block's face: on the hit tick
     * the impact's light burst and sound reach the player at the point the
     * ray lands (decision sunbeam-lands-with-impact-and-aim).
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
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, tick));
        helper.runAfterDelay(2, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            boolean burst = recorder.sentOf(ClientboundLevelParticlesPacket.class).stream().anyMatch(packet ->
                    packet.getParticle().getType() == ParticleTypes.END_ROD
                            && face.distanceTo(new Vec3(packet.getX(), packet.getY(), packet.getZ())) < TOLERANCE);
            boolean sound = recorder.sentOf(ClientboundSoundPacket.class).stream().anyMatch(packet ->
                    packet.getSound().is(SoundEvents.BEACON_POWER_SELECT.location())
                            && soundsAt(packet, face));
            helper.assertTrue(burst, String.format(NO_BURST, face));
            helper.assertTrue(sound, String.format(NO_SOUND, face));
            helper.succeed();
        });
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
