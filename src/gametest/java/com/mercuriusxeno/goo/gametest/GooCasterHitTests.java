package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.GougeStep;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooChargePayload;
import com.mercuriusxeno.goo.network.GooEffectScheduler;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.HeartOverlayTests;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Gametests for the goo hits a player causes directly: Flames' burn, Nova's ring, an Iceborn snowball's ice,
 * Kindle's retaliation, a goo explosion and a hucked Gouge hunk, each driven through its shipped path by a mock
 * player at a mob, each hurt with its own damage type naming that player as its cause
 * (decisions direct-goo-hit-credits-its-caster, goo-hit-keeps-its-kind-and-names-the-player-behind-it).
 */
@RegistersGameTests
public final class GooCasterHitTests {
    private static final String FLAMES_NAMES_THE_PLAYER = "goo_caster_hit_flames_names_the_player";
    private static final String NOVA_NAMES_THE_PLAYER = "goo_caster_hit_nova_names_the_player";
    private static final String ICEBORN_SNOWBALL_NAMES_THE_PLAYER = "goo_caster_hit_iceborn_snowball_names_the_player";
    private static final String KINDLE_RETALIATION_NAMES_THE_PLAYER =
            "goo_caster_hit_kindle_retaliation_names_the_player";
    private static final String GOO_EXPLOSION_NAMES_THE_PLAYER = "goo_caster_hit_goo_explosion_names_the_player";
    private static final String BLOCK_HUNK_NAMES_THE_PLAYER = "goo_caster_hit_block_hunk_names_the_player";

    private static final Identifier BLAZE_FLAMES = Identifier.parse("goo:blaze_flames");
    private static final Identifier FROST_NOVA = Identifier.parse("goo:frost_nova");
    private static final Identifier BLAZE_KINDLE = Identifier.parse("goo:blaze_kindle");
    private static final Identifier ROCK_GOUGE = Identifier.parse("goo:rock_gouge");
    /** The gametests' own thrown blast, a plain explosion landing on a block. */
    private static final String TEST_BLAST = "goo:test_blast";

    /** The caster stands at the bay's west edge, looking east along the row the mob stands in. */
    private static final BlockPos CASTER_POS = new BlockPos(0, 1, 2);
    /** Three blocks east of the caster, inside Flames' reach and a full Nova's ring. */
    private static final BlockPos MOB_POS = CASTER_POS.east(3);
    /** Beside the Kindle player, at melee reach. */
    private static final BlockPos ATTACKER_POS = new BlockPos(2, 1, 3);
    /** The stone block the blast lands on, its south face toward the mob. */
    private static final BlockPos BLAST_STONE = new BlockPos(3, 1, 2);
    /** Two blocks south of the blast's landing cell, well inside its reach. */
    private static final BlockPos BLAST_MOB_POS = BLAST_STONE.south(3);
    /** Far outside the blast's reach, where the caster stands clear of it. */
    private static final BlockPos FAR_FROM_THE_BLAST = new BlockPos(0, 1, -16);
    /** Gouge's slab of stone, one block deep along x and three wide, the level look striking its middle. */
    private static final int SLAB_X = 2;
    private static final int SLAB_LOW_Y = 1;
    private static final int SLAB_HIGH_Y = 2;
    private static final int SLAB_LOW_Z = 1;
    private static final int SLAB_HIGH_Z = 3;
    /** Where the hucked hunk flies into the mob, past the hold. */
    private static final BlockPos HUNK_MOB_POS = new BlockPos(5, 1, 2);

    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** How far along the look a held stream or channel aims. */
    private static final double REACH = 6;
    private static final int SETTLE_TICKS = 1;
    private static final int NO_ENTITY = -1;
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    /** Through Gouge's wind-up, then long enough for the hunk to rise out of its hole and reach the hold. */
    private static final int GOUGE_HOLD_TICKS = GougeStep.WINDUP_TICKS + 14;
    /** The snowball starts this far west of the mob's middle, flying east into it. */
    private static final double SNOWBALL_LEAD = 1.5;
    private static final float SNOWBALL_SPEED = 1.5f;

    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_NAME_THE_PLAYER = "%s should hurt the %s as %s naming the player, "
            + "last hit by %s as %s";

    private GooCasterHitTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FLAMES_NAMES_THE_PLAYER, GooCasterHitTests::flamesNamesThePlayer);
        GooTestFunctions.registerTest(registrar, NOVA_NAMES_THE_PLAYER, GooCasterHitTests::novaNamesThePlayer);
        GooTestFunctions.registerTest(registrar, ICEBORN_SNOWBALL_NAMES_THE_PLAYER,
                GooCasterHitTests::icebornSnowballNamesThePlayer);
        GooTestFunctions.registerTest(registrar, KINDLE_RETALIATION_NAMES_THE_PLAYER,
                GooCasterHitTests::kindleRetaliationNamesThePlayer);
        GooTestFunctions.registerTest(registrar, GOO_EXPLOSION_NAMES_THE_PLAYER,
                GooCasterHitTests::gooExplosionNamesThePlayer);
        GooTestFunctions.registerTest(registrar, BLOCK_HUNK_NAMES_THE_PLAYER, GooCasterHitTests::blockHunkNamesThePlayer);
    }

    /**
     * A mock player holding Flames on a husk three blocks off burns it with in_fire naming the player.
     *
     * @param helper the gametest helper
     */
    public static void flamesNamesThePlayer(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS);
        ServerPlayer player = casterFacingEast(helper, GooTypes.BLAZE, BLAZE_FLAMES);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GooStreamHandler.streamTick(player, heldTick(player, GooTypes.BLAZE, BLAZE_FLAMES));
            assertNamesThePlayer(helper, player, husk, "Flames", DamageTypes.IN_FIRE);
        });
    }

    /**
     * A mock player releasing a full Nova beside a husk freezes it with freeze naming the player.
     *
     * @param helper the gametest helper
     */
    public static void novaNamesThePlayer(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS);
        ServerPlayer player = casterFacingEast(helper, GooTypes.FROST, FROST_NOVA);
        int fullHold = ability(helper, FROST_NOVA).delivery().charge().maxTicks();
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GooThrowHandler.releaseCharge(player,
                    new GooChargePayload(GooTypes.id(GooTypes.FROST), FROST_NOVA.toString(), fullHold));
            assertNamesThePlayer(helper, player, husk, "Nova", DamageTypes.FREEZE);
        });
    }

    /**
     * A snowball an Iceborn mock player threw strikes a husk, whose ice hurts it with freeze naming the player.
     *
     * @param helper the gametest helper
     */
    public static void icebornSnowballNamesThePlayer(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS);
        ServerPlayer player = SelfDeliveryTests.invoker(helper, GooTypes.FROST);
        standFacingEast(helper, player, CASTER_POS);
        player.setData(GooAttachments.HEART_OVERLAY, HeartOverlay.NONE.hold(HeartKind.ICEBORN, player.getHealth(),
                player.getMaxHealth(), HeartOverlay.WHOLE_HIT, helper.getLevel().getGameTime()));
        Snowball snowball = new Snowball(helper.getLevel(), player, new ItemStack(Items.SNOWBALL));
        Vec3 middle = husk.getBoundingBox().getCenter();
        snowball.setPos(middle.x - SNOWBALL_LEAD, middle.y, middle.z);
        snowball.shoot(1, 0, 0, SNOWBALL_SPEED, 0f);
        helper.getLevel().addFreshEntity(snowball);
        succeedOnceHit(helper, player, husk, "An Iceborn snowball", DamageTypes.FREEZE);
    }

    /**
     * A husk striking a kindled mock player takes Kindle's retaliatory fire as in_fire naming the player.
     *
     * @param helper the gametest helper
     */
    public static void kindleRetaliationNamesThePlayer(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.crawledWhole(
                HeartOverlayTests.selfInvoked(helper, GooTypes.BLAZE, BLAZE_KINDLE));
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, ATTACKER_POS);
        husk.doHurtTarget(helper.getLevel(), player);
        assertNamesThePlayer(helper, player, husk, "Kindle's retaliation", DamageTypes.IN_FIRE);
    }

    /**
     * A blast a mock player threw lands on a stone block beside an iron golem, whose explosion hurts the golem
     * as explosion naming the player.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void gooExplosionNamesThePlayer(GameTestHelper helper) {
        helper.setBlock(BLAST_STONE, Blocks.STONE);
        Mob golem = helper.spawnWithNoFreeWill(EntityType.IRON_GOLEM, BLAST_MOB_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        standFacingEast(helper, player, FAR_FROM_THE_BLAST);
        GooEffectScheduler arrivals = GooServerState.of(helper.getLevel().getServer()).gooEffects();
        int arrivalTick = helper.getLevel().getServer().getTickCount();
        arrivals.enqueue(new PendingEffect(arrivalTick, helper.getLevel(), player, GooTypes.UNSTABLE, NO_ENTITY,
                helper.absolutePos(BLAST_STONE), Direction.SOUTH, TEST_BLAST));
        arrivals.drainArrivedEffects(arrivalTick);
        assertNamesThePlayer(helper, player, golem, "A goo explosion", DamageTypes.EXPLOSION);
    }

    /**
     * A mock player holding Gouge on a slab of stone lifts it as a hunk and hucks it into a husk, which the hunk
     * crushes as falling_block naming the player.
     *
     * @param helper the gametest helper
     */
    public static void blockHunkNamesThePlayer(GameTestHelper helper) {
        for (int y = SLAB_LOW_Y; y <= SLAB_HIGH_Y; y++) {
            for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
                helper.setBlock(new BlockPos(SLAB_X, y, z), Blocks.STONE);
            }
        }
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, HUNK_MOB_POS);
        ServerPlayer player = casterFacingEast(helper, GooTypes.ROCK, ROCK_GOUGE);
        for (int held = 1; held <= GOUGE_HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () ->
                    GooStreamHandler.streamTick(player, heldTick(player, GooTypes.ROCK, ROCK_GOUGE)));
        }
        helper.runAfterDelay(GOUGE_HOLD_TICKS + 1, () -> helper.assertTrue(GrabEvents.throwHeld(player),
                "The player should hold the hunk to huck it"));
        succeedOnceHit(helper, player, husk, "A hucked hunk", DamageTypes.FALLING_BLOCK);
    }

    /**
     * Passes the test once the mob's latest hit holds the damage type and names the player, checked each tick
     * against the latest hit the mob took, which outlasts the mob's own record of it.
     *
     * @param helper the gametest helper
     * @param player the player behind the hit
     * @param mob    the struck mob
     * @param hit    the hit, for the failure message
     * @param type   the damage type the hit keeps
     */
    private static void succeedOnceHit(GameTestHelper helper, ServerPlayer player, LivingEntity mob, String hit,
                                       ResourceKey<DamageType> type) {
        DamageSource[] latest = {null};
        helper.succeedWhen(() -> {
            DamageSource now = mob.getLastDamageSource();
            if (now != null) {
                latest[0] = now;
            }
            assertNamesThePlayer(helper, player, mob, hit, type, latest[0]);
        });
    }

    /**
     * Asserts the mob's last hit holds the damage type and names the player as its cause, then takes the player
     * out of the level and passes the test.
     *
     * @param helper the gametest helper
     * @param player the player behind the hit
     * @param mob    the struck mob
     * @param hit    the hit, for the failure message
     * @param type   the damage type the hit keeps
     */
    private static void assertNamesThePlayer(GameTestHelper helper, ServerPlayer player, LivingEntity mob, String hit,
                                             ResourceKey<DamageType> type) {
        assertNamesThePlayer(helper, player, mob, hit, type, mob.getLastDamageSource());
    }

    /**
     * Asserts a hit on the mob holds the damage type and names the player as its cause, then takes the player
     * out of the level and passes the test.
     *
     * @param helper the gametest helper
     * @param player the player behind the hit
     * @param mob    the struck mob
     * @param hit    the hit, for the failure message
     * @param type   the damage type the hit keeps
     * @param last   the hit the mob took, or null for none
     */
    private static void assertNamesThePlayer(GameTestHelper helper, ServerPlayer player, LivingEntity mob, String hit,
                                             ResourceKey<DamageType> type, @Nullable DamageSource last) {
        helper.assertTrue(last != null && last.getEntity() == player && last.is(type),
                String.format(SHOULD_NAME_THE_PLAYER, hit, mob.getName().getString(), type.identifier(),
                        causeOf(last), last == null ? "nothing" : last.typeHolder().getRegisteredName()));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static String causeOf(@Nullable DamageSource source) {
        return source == null || source.getEntity() == null ? "no one" : source.getEntity().getName().getString();
    }

    /**
     * A mock player standing at the caster's cell looking level east, holding a glove and the goo of the type,
     * taught the ability's reagents.
     *
     * @param helper  the gametest helper
     * @param gooType the goo type the player holds
     * @param ability the ability the player casts
     * @return the player
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer casterFacingEast(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
                                                 Identifier ability) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        standFacingEast(helper, player, CASTER_POS);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, CASTER_GOO));
        KnownRecipes.teachRequires(player, ability(helper, ability));
        return player;
    }

    private static void standFacingEast(GameTestHelper helper, ServerPlayer player, BlockPos cell) {
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(cell));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setXRot(LEVEL);
    }

    private static GooStreamPayload heldTick(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                             Identifier ability) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(gooType), ability.toString(), eye,
                eye.add(player.getLookAngle().scale(REACH)));
    }

    private static AbilityDefinition ability(GameTestHelper helper, Identifier id) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(id);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, id));
        return ability;
    }
}
