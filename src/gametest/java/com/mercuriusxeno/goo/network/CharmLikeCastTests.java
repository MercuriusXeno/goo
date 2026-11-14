package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests casting a mob ability through the charm-like delivery the way the
 * glove's release does, through {@link GooThrowHandler#execute} naming the mob
 * the press took: the blob darts to a mob standing within reach rather than
 * touching it, so its program has not run on the cast's tick, and has once
 * the dart lands (decision charm-like-abilities-lock-the-highlighted-mob-on-right-click).
 */
@RegistersGameTests
public final class CharmLikeCastTests {

    private static final String PHASE_OTHER_DARTS = "phase_other_darts_to_the_taken_mob_and_phases_it";
    private static final String RALLY_DARTS = "rally_darts_to_the_taken_mob_and_rallies_it";
    private static final String SHULK_SPEWS = "shulk_spews_a_shulker_bullet_at_the_taken_mob";
    private static final Identifier SHULK = Identifier.parse("goo:typhoon_shulk");
    /** How far around the zombie the test looks for the bullet, past the two blocks it starts from. */
    private static final double BULLET_SEARCH_BLOCKS = 8;
    private static final String SHOULD_SPEW_ONE_BULLET = "Shulk should spew one shulker bullet, found %d";
    private static final String BULLET_SHOULD_HOME_ON_THE_TAKEN_MOB = "The bullet should home on the zombie taken";
    private static final String BULLET_SHOULD_LEVITATE = "The bullet's hit should levitate the zombie";
    private static final String LEVITATION_SHOULD_WAIT_FOR_THE_HIT =
            "The zombie should levitate only once the bullet reaches it";

    /** Two blocks from where the caster stands, inside the reach a touch would land at. */
    private static final BlockPos MOB_POS = new BlockPos(3, 1, 3);
    /** The tick after spawning, once the level's entity index holds the spawned mob. */
    private static final int SETTLE_TICKS = 1;
    /** Ticks past the cast by which the dart has crossed the two blocks to the mob and landed. */
    private static final int DART_LANDED_TICKS = 5;
    /** The face a throw at an entity names: none. */
    private static final int NO_FACE = -1;
    private static final Identifier PHASE_OTHER = Identifier.parse("goo:quantum_phase_other");
    private static final Identifier RALLY = Identifier.parse("goo:zoo_rally");
    private static final String ABILITY_REQUIRED = "The cast ability should load";
    private static final String SHOULD_DART_NOT_TOUCH =
            "%s should dart to a mob within reach, landing only once the dart arrives";
    private static final String SHOULD_LAND = "%s should land on the taken mob once its dart arrives";

    private CharmLikeCastTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, PHASE_OTHER_DARTS, CharmLikeCastTests::phaseOtherDartsAndPhases);
        GooTestFunctions.registerTest(registrar, RALLY_DARTS, CharmLikeCastTests::rallyDartsAndRallies);
        GooTestFunctions.registerTest(registrar, SHULK_SPEWS, CharmLikeCastTests::shulkSpewsAShulkerBulletAtTheTakenMob);
    }

    /**
     * Phase Other cast at a zombie leaves it out of phase once the dart lands.
     *
     * @param helper the gametest helper
     */
    public static void phaseOtherDartsAndPhases(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MOB_POS);
        castAndAssert(helper, GooTypes.QUANTUM, PHASE_OTHER, zombie,
                mob -> mob.getData(GooAttachments.OUT_OF_PHASE).standsAt(helper.getLevel().getGameTime()));
    }

    /**
     * Rally cast at a cow makes it the caster's minion once the dart lands.
     *
     * @param helper the gametest helper
     */
    public static void rallyDartsAndRallies(GameTestHelper helper) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        ServerPlayer[] caster = new ServerPlayer[1];
        caster[0] = castAndAssert(helper, GooTypes.ZOO, RALLY, cow, mob -> OwnMinions.ownMinionOf(caster[0], mob));
    }

    /**
     * Shulk cast at a zombie spews one vanilla shulker bullet from the blob,
     * homing on the zombie the press took, and the bullet's hit levitates it,
     * a shulker's own attack (operator: "it's literally a shulker attack").
     *
     * @param helper the gametest helper
     */
    public static void shulkSpewsAShulkerBulletAtTheTakenMob(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, MOB_POS);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.TYPHOON, SHULK);
        AbilityDefinition shulk = AbilityRegistry.of(helper.getLevel()).getAbility(SHULK);
        helper.assertTrue(shulk != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(caster, shulk);
        helper.runAfterDelay(SETTLE_TICKS, () -> GooThrowHandler.execute(caster, new GooThrowPayload(
                GooTypes.id(GooTypes.TYPHOON), zombie.getId(), BlockPos.ZERO, NO_FACE, false, SHULK.toString(),
                caster.getEyePosition(), zombie.getBoundingBox().getCenter())));
        helper.runAfterDelay(SETTLE_TICKS + 1, () -> {
            List<ShulkerBullet> bullets = helper.getLevel().getEntitiesOfClass(ShulkerBullet.class,
                    zombie.getBoundingBox().inflate(BULLET_SEARCH_BLOCKS));
            helper.assertTrue(bullets.size() == 1, String.format(SHOULD_SPEW_ONE_BULLET, bullets.size()));
            helper.assertTrue(targetOf(helper, bullets.getFirst()).filter(zombie.getUUID()::equals).isPresent(),
                    BULLET_SHOULD_HOME_ON_THE_TAKEN_MOB);
            helper.assertFalse(zombie.hasEffect(MobEffects.LEVITATION), LEVITATION_SHOULD_WAIT_FOR_THE_HIT);
            helper.succeedWhen(() -> {
                helper.assertTrue(zombie.hasEffect(MobEffects.LEVITATION), BULLET_SHOULD_LEVITATE);
                helper.getLevel().getServer().getPlayerList().remove(caster);
            });
        });
    }

    /**
     * The entity a shulker bullet homes on, as it saves it.
     *
     * @param helper the gametest helper
     * @param bullet the bullet
     * @return the target's UUID, empty where it homes on none
     */
    private static Optional<UUID> targetOf(GameTestHelper helper, ShulkerBullet bullet) {
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        bullet.saveWithoutId(saved);
        return saved.buildResult().read("Target", UUIDUtil.CODEC);
    }

    /**
     * Casts the ability at the mob as the glove's release names it, asserting
     * the program has not landed on the cast's tick and has once the dart arrives.
     *
     * @param helper  the gametest helper
     * @param gooType the ability's goo type, which the caster holds
     * @param ability the cast ability
     * @param mob     the mob the press took
     * @param landed  whether the ability's program stands on the mob
     * @return the caster
     */
    private static ServerPlayer castAndAssert(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType,
            Identifier ability, Mob mob, Predicate<Mob> landed) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, gooType, ability);
        AbilityDefinition definition = AbilityRegistry.of(helper.getLevel()).getAbility(ability);
        helper.assertTrue(definition != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(caster, definition);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(gooType), mob.getId(), BlockPos.ZERO,
                    NO_FACE, false, ability.toString(), caster.getEyePosition(), mob.getBoundingBox().getCenter()));
            helper.assertFalse(landed.test(mob), String.format(SHOULD_DART_NOT_TOUCH, ability));
            helper.runAfterDelay(DART_LANDED_TICKS, () -> {
                boolean stands = landed.test(mob);
                helper.getLevel().getServer().getPlayerList().remove(caster);
                helper.assertTrue(stands, String.format(SHOULD_LAND, ability));
                helper.succeed();
            });
        });
        return caster;
    }
}
