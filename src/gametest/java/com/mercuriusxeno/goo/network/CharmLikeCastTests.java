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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.RegisterEvent;
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
