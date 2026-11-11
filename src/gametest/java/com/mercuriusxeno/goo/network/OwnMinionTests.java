package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametests for a harmful mob ability sparing its caster's own minions
 * through the real throw path: metal javelin thrown at a cow the caster
 * rallied or a husk it charmed leaves the mob whole, the same throw at an
 * unowned cow lands, and quantum link, untagged, still lands on the caster's
 * rallied cow (decision mob-targeted-attacks-refuse-your-minions).
 */
@RegistersGameTests
public final class OwnMinionTests {
    private static final String JAVELIN_SPARES_RALLIED = "own_minion_javelin_spares_the_casters_rallied_cow";
    private static final String JAVELIN_SPARES_CHARMED = "own_minion_javelin_spares_the_casters_charmed_mob";
    private static final String JAVELIN_STRIKES_UNOWNED = "own_minion_javelin_strikes_an_unowned_mob";
    private static final String LINK_LANDS_ON_RALLIED = "own_minion_link_lands_on_the_casters_rallied_cow";

    private static final BlockPos MOB_POS = new BlockPos(3, 1, 3);
    /** Two blocks west of the mob, inside a player's reach of three. */
    private static final BlockPos PLAYER_POS = MOB_POS.west(2);
    private static final int SETTLE_TICKS = 1;
    private static final int NO_ENTITY = -1;
    private static final int HELD_GOO = 4;
    private static final float RALLY_DAMAGE = 6f;
    private static final String METAL_JAVELIN = "goo:metal_javelin";
    private static final String QUANTUM_LINK = "goo:quantum_link";
    private static final String SHOULD_SPARE = "The caster's own %s should keep its health %.1f, stands at %.1f";
    private static final String SHOULD_STRIKE = "An unowned cow should lose health from %.1f, stands at %.1f";
    private static final String SHOULD_LINK = "Quantum link should strike the caster's rallied cow, leaving it waiting";

    private OwnMinionTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, JAVELIN_SPARES_RALLIED, OwnMinionTests::javelinSparesRalliedCow);
        GooTestFunctions.registerTest(registrar, JAVELIN_SPARES_CHARMED, OwnMinionTests::javelinSparesCharmedMob);
        GooTestFunctions.registerTest(registrar, JAVELIN_STRIKES_UNOWNED, OwnMinionTests::javelinStrikesUnownedMob);
        GooTestFunctions.registerTest(registrar, LINK_LANDS_ON_RALLIED, OwnMinionTests::linkLandsOnRalliedCow);
    }

    /**
     * Metal javelin thrown at a cow its caster rallied leaves the cow at full health.
     *
     * @param helper the gametest helper
     */
    public static void javelinSparesRalliedCow(GameTestHelper helper) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        ServerPlayer player = thrower(helper, GooTypes.METAL);
        assertSpared(helper, player, cow, "rallied cow", () -> rally(helper, cow, player));
    }

    /**
     * Metal javelin thrown at a husk its caster charmed leaves the husk at full health.
     *
     * @param helper the gametest helper
     */
    public static void javelinSparesCharmedMob(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS);
        ServerPlayer player = thrower(helper, GooTypes.METAL);
        assertSpared(helper, player, husk, "charmed husk",
                () -> husk.setData(GooAttachments.CHARMED, new Charmed(player.getUUID())));
    }

    /**
     * Metal javelin thrown at a cow nobody rallied or charmed takes its health.
     *
     * @param helper the gametest helper
     */
    public static void javelinStrikesUnownedMob(GameTestHelper helper) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        ServerPlayer player = thrower(helper, GooTypes.METAL);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float before = cow.getHealth();
            GooThrowHandler.execute(player, payload(GooTypes.METAL, METAL_JAVELIN, player, cow));
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(cow.getHealth() < before, String.format(SHOULD_STRIKE, before, cow.getHealth()));
            helper.succeed();
        });
    }

    /**
     * Quantum link, carrying no harmful tag, thrown at a cow its caster
     * rallied strikes it: the caster's link waits on that cow for its pair.
     *
     * @param helper the gametest helper
     */
    public static void linkLandsOnRalliedCow(GameTestHelper helper) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        ServerPlayer player = thrower(helper, GooTypes.QUANTUM);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            rally(helper, cow, player);
            GooThrowHandler.execute(player, payload(GooTypes.QUANTUM, QUANTUM_LINK, player, cow));
            Optional<UUID> waiting = player.getData(GooAttachments.FATE_WAITING);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(waiting.isPresent() && waiting.get().equals(cow.getUUID()), SHOULD_LINK);
            helper.succeed();
        });
    }

    /**
     * Throws metal javelin at a mob the caster makes its own in the same
     * tick, since a rally whose caster holds no Rally stands down on the
     * mob's next tick, and asserts the mob kept its health.
     */
    private static void assertSpared(GameTestHelper helper, ServerPlayer player, Mob mob, String kind,
            Runnable makeOwn) {
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            makeOwn.run();
            float before = mob.getHealth();
            GooThrowHandler.execute(player, payload(GooTypes.METAL, METAL_JAVELIN, player, mob));
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(mob.getHealth() == before, String.format(SHOULD_SPARE, kind, before, mob.getHealth()));
            helper.succeed();
        });
    }

    private static void rally(GameTestHelper helper, Mob mob, ServerPlayer caster) {
        mob.setData(GooAttachments.RALLIED, new Rallied(caster.getUUID(), RALLY_DAMAGE));
    }

    private static GooThrowPayload payload(ResourceKey<GooTypeDefinition> gooType, String ability,
            ServerPlayer player, Mob target) {
        return new GooThrowPayload(GooTypes.id(gooType), target.getId(), BlockPos.ZERO, NO_ENTITY, false,
                ability, player.getEyePosition());
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer thrower(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(PLAYER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
