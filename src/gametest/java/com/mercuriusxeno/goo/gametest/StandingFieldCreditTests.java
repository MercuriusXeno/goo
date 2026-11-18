package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.block.ability.AbilityBlockEntity;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametests for the hits a standing goo field deals: the metal spike trap fixture's spikes, landed through the ability registry, strike
 * naming the player who threw the trap and keeping goo:stalagmite, strike naming no one where no player threw it,
 * leave that player's rally and charm standing on the mobs struck, and the field's block entity carries the
 * player through a save.
 * standing-goo-thing-credits-whoever-placed-it
 * goo-hit-keeps-its-kind-and-names-the-player-behind-it
 * crediting-a-goo-hit-changes-nothing-else
 */
@RegistersGameTests
public final class StandingFieldCreditTests {
    private static final String SPIKE_TRAP_NAMES_ITS_THROWER = "standing_credit_spike_trap_names_the_player_who_threw_it";
    private static final String UNTHROWN_SPIKE_TRAP_NAMES_NONE = "standing_credit_spike_trap_no_player_threw_names_no_entity";
    private static final String SPIKE_TRAP_KEEPS_RALLY_AND_CHARM = "standing_credit_spike_trap_keeps_rally_and_charm";
    private static final String SPIKE_TRAP_PLACER_SURVIVES_A_SAVE = "standing_credit_spike_trap_placer_survives_a_save";
    private static final String LURKER_NAMES_ITS_THROWER = "standing_credit_lurker_names_the_player_who_threw_it";
    private static final String UNTHROWN_LURKER_NAMES_NONE = "standing_credit_lurker_no_player_threw_names_no_entity";
    private static final String UNSTABLE_LURKER = "goo:unstable_lurker";
    private static final String SHOULD_NAME = "%s should hit as %s naming %s, its latest such hit named %s";

    private static final String SPIKE_TRAP = "goo:test_spike_trap";
    private static final String ABILITIES_REQUIRED = "Ability registry must hold " + SPIKE_TRAP;
    private static final String NOT_STRUCK = "The spike trap field has not struck the %s yet";
    private static final String SHOULD_NAME_THE_THROWER =
            "The spike trap's strike should name the player who threw it as goo:stalagmite, last hit by %s from %s";
    private static final String SHOULD_NAME_NONE =
            "The spike trap's strike with no player behind it should name no entity as goo:stalagmite, last hit by %s from %s";
    private static final String SHOULD_KEEP = "The thrower's own %s should keep its %s after the spike trap struck it, holds it %s";
    private static final String PLACER_LOST = "The reloaded spike trap block entity holds placer %s, the player who threw it was %s";

    /** The block the spike trap's goo lands on; the field stands in the cell above it. */
    private static final BlockPos STRUCK = new BlockPos(3, 1, 3);
    private static final BlockPos FIELD = STRUCK.above();
    /** Two mob cells, each two and a half blocks from the field's center, inside its 3.75 block reach. */
    private static final BlockPos SOUTH_OF_THE_FIELD = new BlockPos(3, 1, 5);
    private static final BlockPos EAST_OF_THE_FIELD = new BlockPos(5, 1, 3);
    /** Blocks above the field the thrower hangs, out of the field's reach so it strikes only the mobs. */
    private static final int THROWER_HEIGHT = 12;
    /** Health enough for a mob to outlast every charge a spike trap field holds. */
    private static final float HARDY_HEALTH = 1024f;
    private static final float RALLY_DAMAGE = 6f;

    private StandingFieldCreditTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPIKE_TRAP_NAMES_ITS_THROWER,
                StandingFieldCreditTests::spikeTrapStrikeNamesThePlayerWhoThrewIt);
        GooTestFunctions.registerTest(registrar, UNTHROWN_SPIKE_TRAP_NAMES_NONE,
                StandingFieldCreditTests::spikeTrapStrikeNoPlayerThrewNamesNoEntity);
        GooTestFunctions.registerTest(registrar, SPIKE_TRAP_KEEPS_RALLY_AND_CHARM,
                StandingFieldCreditTests::spikeTrapStrikeKeepsTheThrowersRallyAndCharm);
        GooTestFunctions.registerTest(registrar, SPIKE_TRAP_PLACER_SURVIVES_A_SAVE,
                StandingFieldCreditTests::spikeTrapPlacerSurvivesASave);
        GooTestFunctions.registerTest(registrar, LURKER_NAMES_ITS_THROWER,
                StandingFieldCreditTests::lurkerBlastNamesThePlayerWhoThrewIt);
        GooTestFunctions.registerTest(registrar, UNTHROWN_LURKER_NAMES_NONE,
                StandingFieldCreditTests::lurkerBlastNoPlayerThrewNamesNoEntity);
    }

    /**
     * A Lurker landed for a mock player explodes on a pig that comes within its reach, the blast naming that
     * player as minecraft:explosion.
     *
     * @param helper the gametest helper
     */
    public static void lurkerBlastNamesThePlayerWhoThrewIt(GameTestHelper helper) {
        ServerPlayer thrower = throwerOverhead(helper);
        Mob pig = hardy(helper.spawnWithNoFreeWill(EntityType.PIG, SOUTH_OF_THE_FIELD));
        land(helper, STRUCK, UNSTABLE_LURKER, GooTypes.UNSTABLE, thrower.getUUID());
        succeedOnceNamed(helper, pig, "Lurker's blast", DamageTypes.EXPLOSION, thrower);
    }

    /**
     * A Lurker landed with no player behind it explodes on a pig within its reach, the blast naming no entity as
     * minecraft:explosion.
     *
     * @param helper the gametest helper
     */
    public static void lurkerBlastNoPlayerThrewNamesNoEntity(GameTestHelper helper) {
        Mob pig = hardy(helper.spawnWithNoFreeWill(EntityType.PIG, SOUTH_OF_THE_FIELD));
        land(helper, STRUCK, UNSTABLE_LURKER, GooTypes.UNSTABLE, null);
        succeedOnceNamed(helper, pig, "Lurker's blast", DamageTypes.EXPLOSION, null);
    }

    // a mob's last hit may be a fall or a fire it took since, so the test holds the latest hit of the type it awaits
    private static void succeedOnceNamed(GameTestHelper helper, Mob mob, String hitBy, ResourceKey<DamageType> type,
                                         @Nullable ServerPlayer player) {
        DamageSource[] latest = {null};
        helper.succeedWhen(() -> {
            DamageSource now = mob.getLastDamageSource();
            latest[0] = now != null && now.is(type) ? now : latest[0];
            helper.assertTrue(latest[0] != null, String.format(NOT_STRUCK, mob.getName().getString()));
            helper.assertTrue(latest[0].getEntity() == player, String.format(SHOULD_NAME, hitBy, type.identifier(),
                    player == null ? "no one" : player.getName().getString(), causeOf(latest[0])));
            if (player != null) {
                helper.getLevel().getServer().getPlayerList().remove(player);
            }
        });
    }

    private static void land(GameTestHelper helper, BlockPos struck, String abilityId,
                             ResourceKey<GooTypeDefinition> type, @Nullable UUID thrower) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, "Ability registry must hold " + abilityId);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(struck), type, Direction.UP, ability, null, 0,
                thrower);
    }

    /**
     * The spike trap, landed for a mock player, strikes a pig in its field with goo:stalagmite naming that player.
     *
     * @param helper the gametest helper
     */
    public static void spikeTrapStrikeNamesThePlayerWhoThrewIt(GameTestHelper helper) {
        ServerPlayer thrower = throwerOverhead(helper);
        Mob pig = hardy(helper.spawnWithNoFreeWill(EntityType.PIG, SOUTH_OF_THE_FIELD));
        landSpikeTrap(helper, thrower.getUUID());
        helper.succeedWhen(() -> {
            DamageSource last = struck(helper, pig, "pig");
            helper.assertTrue(last.getEntity() == thrower && last.is(DamageStep.STALAGMITE),
                    String.format(SHOULD_NAME_THE_THROWER, causeOf(last), last));
            helper.getLevel().getServer().getPlayerList().remove(thrower);
        });
    }

    /**
     * The spike trap, landed with no player behind it, strikes a pig in its field with goo:stalagmite naming no entity.
     *
     * @param helper the gametest helper
     */
    public static void spikeTrapStrikeNoPlayerThrewNamesNoEntity(GameTestHelper helper) {
        Mob pig = hardy(helper.spawnWithNoFreeWill(EntityType.PIG, SOUTH_OF_THE_FIELD));
        landSpikeTrap(helper, null);
        helper.succeedWhen(() -> {
            DamageSource last = struck(helper, pig, "pig");
            helper.assertTrue(last.getEntity() == null && last.is(DamageStep.STALAGMITE),
                    String.format(SHOULD_NAME_NONE, causeOf(last), last));
        });
    }

    /**
     * The spike trap, landed for a mock player, strikes a cow that player rallied and a husk that player charmed, and
     * both the rally and the charm still stand once each has been struck.
     *
     * @param helper the gametest helper
     */
    public static void spikeTrapStrikeKeepsTheThrowersRallyAndCharm(GameTestHelper helper) {
        ServerPlayer thrower = throwerOverhead(helper);
        Mob cow = hardy(helper.spawnWithNoFreeWill(EntityType.COW, SOUTH_OF_THE_FIELD));
        cow.setData(GooAttachments.RALLIED, new Rallied(thrower.getUUID(), RALLY_DAMAGE));
        Mob husk = hardy(helper.spawnWithNoFreeWill(EntityType.HUSK, EAST_OF_THE_FIELD));
        husk.setData(GooAttachments.CHARMED, new Charmed(thrower.getUUID()));
        landSpikeTrap(helper, thrower.getUUID());
        helper.succeedWhen(() -> {
            struck(helper, cow, "rallied cow");
            struck(helper, husk, "charmed husk");
            helper.assertTrue(cow.hasData(GooAttachments.RALLIED),
                    String.format(SHOULD_KEEP, "cow", "rally", cow.hasData(GooAttachments.RALLIED)));
            helper.assertTrue(husk.hasData(GooAttachments.CHARMED),
                    String.format(SHOULD_KEEP, "husk", "charm", husk.hasData(GooAttachments.CHARMED)));
            helper.getLevel().getServer().getPlayerList().remove(thrower);
        });
    }

    /**
     * The spike trap's block entity, landed for a player, saves to a tag through its codec, and a fresh block entity
     * loaded from that tag holds the same player as the field's placer.
     *
     * @param helper the gametest helper
     */
    public static void spikeTrapPlacerSurvivesASave(GameTestHelper helper) {
        UUID thrower = UUID.randomUUID();
        landSpikeTrap(helper, thrower);
        AbilityBlockEntity standing = helper.getBlockEntity(FIELD, AbilityBlockEntity.class);
        CompoundTag saved = standing.saveCustomOnly(helper.getLevel().registryAccess());
        AbilityBlockEntity reloaded = new AbilityBlockEntity(standing.getBlockPos(), standing.getBlockState());
        reloaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(),
                saved));
        helper.assertTrue(thrower.equals(reloaded.placer()), String.format(PLACER_LOST, reloaded.placer(), thrower));
        helper.succeed();
    }

    private static void landSpikeTrap(GameTestHelper helper, @Nullable UUID thrower) {
        helper.setBlock(STRUCK, Blocks.STONE);
        AbilityDefinition spikeTrap = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(SPIKE_TRAP));
        helper.assertTrue(spikeTrap != null, ABILITIES_REQUIRED);
        AbilityImpact.land(helper.getLevel(), helper.absolutePos(STRUCK), GooTypes.METAL, Direction.UP, spikeTrap,
                null, 0, thrower);
    }

    private static DamageSource struck(GameTestHelper helper, Mob mob, String name) {
        DamageSource last = mob.getLastDamageSource();
        helper.assertTrue(last != null, String.format(NOT_STRUCK, name));
        return last;
    }

    private static Mob hardy(Mob mob) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HARDY_HEALTH);
        mob.setHealth(HARDY_HEALTH);
        return mob;
    }

    private static String causeOf(DamageSource source) {
        return source.getEntity() == null ? "no one" : source.getEntity().getName().getString();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer throwerOverhead(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 hang = Vec3.atBottomCenterOf(helper.absolutePos(FIELD.above(THROWER_HEIGHT)));
        player.setNoGravity(true);
        player.setPos(hang.x, hang.y, hang.z);
        return player;
    }
}
