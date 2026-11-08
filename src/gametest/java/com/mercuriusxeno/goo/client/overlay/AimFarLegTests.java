package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.PortalChoice;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import java.util.List;
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
    private static final String GRAB_AIM_OUTLINES_THE_MOB_THROUGH_A_PORTAL =
            "grab_aim_outlines_the_mob_through_a_portal";
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
        GooTestFunctions.registerTest(registrar, GRAB_AIM_OUTLINES_THE_MOB_THROUGH_A_PORTAL,
                AimFarLegTests::grabAimOutlinesTheMobThroughAPortal);
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

    /** The wall between the player and the zombie, its west face the plane x 6. */
    private static final int WALL_X = 6;
    /** The near portal's lower block, on the wall's west face before the player. */
    private static final BlockPos NEAR_PORTAL = new BlockPos(WALL_X, 1, 2);
    /** The far portal's lower block, on the wall's east face two blocks north. */
    private static final BlockPos FAR_PORTAL = new BlockPos(WALL_X, 1, 0);
    /** The zombie, east of the wall before the far portal, tall enough to stand on the leg out of it. */
    private static final BlockPos PAST_THE_WALL = new BlockPos(WALL_X + 2, 1, 0);
    /** Yaw looking east, along +x. */
    private static final float FACING_EAST = -90f;

    /**
     * Grab aimed at a linked portal outlines the zombie standing on the line
     * out of its partner, past the wall, carrying the portal it passed.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void grabAimOutlinesTheMobThroughAPortal(GameTestHelper helper) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, "Ability registry must hold kinetic_grab");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setXRot(0f);
        for (int z = -1; z <= 3; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.STONE);
            }
        }
        ServerLevel level = helper.getLevel();
        Portal near = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(NEAR_PORTAL)), Direction.WEST,
                Direction.UP);
        Portal far = new Portal(GlobalPos.of(level.dimension(), helper.absolutePos(FAR_PORTAL)), Direction.EAST,
                Direction.UP);
        PortalChoice.place(level, player.getUUID(), near);
        PortalChoice.place(level, player.getUUID(), far);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, PAST_THE_WALL);
        zombie.setNoAi(true);
        GatePassage.setClientPairs(GatePassage.pairsOf(level.getServer()));
        try {
            AimTargets.AimRay ray = AimTargets.AimRay.along(player.getEyePosition(), player.getLookAngle(),
                    GrabStep.reachOf(grab.behaviors(), null).orElseThrow());
            TargetResult target = AimTargets.resolve(player, ray, null, TargetingHint.of(grab.badge()),
                    grab.behaviors()).target();
            helper.assertTrue(target instanceof TargetResult.EntityTarget aimed && aimed.entity() == zombie,
                    "Grab aimed at the portal should outline the zombie past it, aimed " + target);
        } finally {
            GatePassage.setClientPairs(List.of());
        }
        PortalChoice.breakAt(level, near.block().pos(), near.face());
        PortalChoice.breakAt(level, far.block().pos(), far.face());
        zombie.discard();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
