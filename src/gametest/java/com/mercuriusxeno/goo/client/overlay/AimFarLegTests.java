package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the aim's far leg: a leg the aim carries on along out of a
 * linked gate's partner picks the mob on that leg, however far the partner
 * stands from the aiming player
 * (decision aim-and-shots-carry-through-linked-gates).
 */
@RegistersGameTests
public final class AimFarLegTests {
    private static final String FAR_LEG_PICKS_THE_MOB_BY_THE_PARTNER = "far_leg_picks_the_mob_by_the_partner";
    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Where the far leg starts, out of a partner standing well clear of the player. */
    private static final Vec3 LEG_FROM = new Vec3(14.5, 1.5, 2.5);
    /** Where the far leg ends, the reach it has left run on east. */
    private static final Vec3 LEG_TO = new Vec3(22.5, 1.5, 2.5);
    /** The pig on the far leg, past the player's own surroundings. */
    private static final BlockPos PIG_POS = new BlockPos(18, 1, 2);
    private static final Identifier KINETIC_GRAB = Identifier.parse("goo:kinetic_grab");

    private AimFarLegTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, FAR_LEG_PICKS_THE_MOB_BY_THE_PARTNER,
                AimFarLegTests::farLegPicksTheMobByThePartner);
    }

    /**
     * A far leg running out of a partner fourteen blocks off picks the pig
     * standing on it for Grab.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void farLegPicksTheMobByThePartner(GameTestHelper helper) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, "Ability registry must hold kinetic_grab");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        Pig pig = helper.spawn(EntityType.PIG, PIG_POS);
        AimAssistResolver.AimHit hit = AimAssistResolver.findClosestAimHit(player, helper.absoluteVec(LEG_FROM),
                helper.absoluteVec(LEG_TO), null, grab.behaviors());
        helper.assertTrue(hit instanceof AimAssistResolver.AimHit.EntityHit entityHit && entityHit.entity() == pig,
                "The far leg should pick the pig on it, picked " + hit);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
