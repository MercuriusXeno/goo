package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.GrabStep;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.TargetingHint;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Grab's own pick cone end to end: the glove's pick, resolved
 * as the client resolves it with Grab selected, names the target each held
 * tick sends, and the server's Grab takes that target
 * (decision grab-cone-picks-more-aggressively-than-other-goo-types).
 */
@RegistersGameTests
public final class GrabPickConeTests {
    private static final String GRAB_HOLDS_THE_PIG_ITS_PICK_CONE_OUTLINES =
            "grab_holds_the_pig_its_pick_cone_outlines";
    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** Five blocks east of the player, far enough that the turned look passes beside the pig's box. */
    private static final BlockPos FIVE_BLOCKS_OUT = new BlockPos(5, 1, 2);
    /** How far off the pig the look turns: past the shared pick cone of 4 degrees, inside Grab's. */
    private static final float INSIDE_GRABS_CONE = 7f;
    /** How far the test's look ray runs: past the pig, short of the bay's far wall. */
    private static final double IN_THE_BAY = 6;
    private static final int HOLD_TICKS = 5;
    private static final int HELD_GOO = 3;
    private static final Identifier KINETIC_GRAB = Identifier.parse("goo:kinetic_grab");

    private GrabPickConeTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GRAB_HOLDS_THE_PIG_ITS_PICK_CONE_OUTLINES,
                GrabPickConeTests::grabHoldsThePigItsPickConeOutlines);
    }

    /**
     * A pig the look passes beside, off by more than the shared cone and
     * inside Grab's, is outlined by the glove's pick, and the held ticks
     * naming that pick lift it.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void grabHoldsThePigItsPickConeOutlines(GameTestHelper helper) {
        AbilityDefinition grab = AbilityRegistry.of(helper.getLevel()).getAbility(KINETIC_GRAB);
        helper.assertTrue(grab != null, "Ability registry must hold kinetic_grab");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.KINETIC, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, grab);
        Pig pig = helper.spawn(EntityType.PIG, FIVE_BLOCKS_OUT);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        player.setYRot(player.getYRot() + INSIDE_GRABS_CONE);
        // Grab's own reach runs the ray into the neighbouring tests' bays, where a mob on the ray is an
        // exact hit and outranks the cone; the ray stops inside this bay, past the pig.
        double reach = Math.min(IN_THE_BAY, GrabStep.reachOf(grab.behaviors(), null).orElseThrow());
        helper.assertTrue(pig.getBoundingBox().clip(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(reach))).isEmpty(),
                "The look should pass beside the pig, off its box");
        java.util.List<String> picks = new java.util.ArrayList<>();
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player, grab, reach, picks)));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            helper.assertTrue(GrabEvents.heldBy(player) == pig, "The pig the look passes " + INSIDE_GRABS_CONE
                    + " degrees beside should be picked and held, held " + GrabEvents.heldBy(player) + ", picked "
                    + picks);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /** A held tick naming the entity the glove's pick outlines this tick, as the client sends it. */
    private static GooStreamPayload holdTick(ServerPlayer player, AbilityDefinition grab, double reach,
            java.util.List<String> picks) {
        AimTargets.AimRay ray = AimTargets.AimRay.along(player.getEyePosition(), player.getLookAngle(), reach);
        TargetResult picked = AimTargets.resolve(player, ray, null, TargetingHint.of(grab.badge()),
                grab.behaviors()).target();
        picks.add(String.valueOf(picked));
        int pickedId = picked instanceof TargetResult.EntityTarget aimed ? aimed.entity().getId()
                : ChannelAim.NO_ENTITY;
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.KINETIC), KINETIC_GRAB.toString(), eye,
                ray.reach()).targeting(pickedId);
    }
}
