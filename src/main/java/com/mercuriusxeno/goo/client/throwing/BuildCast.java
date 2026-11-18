package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.BuildBounds;
import com.mercuriusxeno.goo.ability.BuildFootprint;
import com.mercuriusxeno.goo.ability.BuildGrowth;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.program.BuildStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooBuildPayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * The glove's side of a Build: routes the right clicks of a selection cast
 * by its footprint into {@link BuildChoreography} rather than the throw
 * press, advances it each tick off the use key and the look, and sends the
 * submit. A screen, a hand without the glove or another selection drops the
 * cast.
 * decision spire-rips-walls-and-platforms
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class BuildCast {

    private static final BuildChoreography CAST = new BuildChoreography();
    private static InteractionHand castHand = InteractionHand.MAIN_HAND;
    private static @Nullable String castAbility;

    private BuildCast() {
    }

    /**
     * Whether the held glove's selection is cast by its footprint.
     *
     * @param player the local player
     * @return true for a Build selection
     */
    public static boolean selected(LocalPlayer player) {
        GloveSelection selection = GooCasters.heldSelection(player);
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        return ability != null && ability.tags().contains(AbilityTags.FOOTPRINT);
    }

    /**
     * A right click with a Build selected: pins the corner, or submits the
     * cast while its rise is being set.
     *
     * @param player the local player
     * @param hand   the hand holding the glove
     */
    public static void press(LocalPlayer player, InteractionHand hand) {
        GloveSelection selection = GooCasters.heldSelection(player);
        if (selection == null || selection.getGooType() == null) {
            return;
        }
        if (CAST.phase() == BuildChoreography.Phase.IDLE) {
            castHand = hand;
            castAbility = selection.abilityId();
        }
        Optional<BuildFootprint> submitted = CAST.press(pinnedGround(player), boundsOf(selection));
        submitted.ifPresent(footprint -> {
            player.connection.send(new ServerboundCustomPayloadPacket(new GooBuildPayload(
                    GooTypes.id(selection.getGooType()), selection.abilityId(), footprint.corner(),
                    footprint.opposite(), footprint.rise())));
            // diagnose-and-correct-hand-swings-outside-throws: Build throws no blob, so its resting pose plays no swing
            GlovePoses.release(player, hand);
        });
    }

    /**
     * The bounds the selected ability caps its cast at, read off its synced build step.
     * decision build-dimensions-reach-sixteen-by-sixteen-by-sixteen
     *
     * @param selection the glove's selection
     * @return the bounds, sixteen by sixteen by sixteen where the client holds no synced copy of the ability
     */
    private static BuildBounds boundsOf(GloveSelection selection) {
        ClientAbility ability = AbilitySyncHandler.findAbility(selection.abilityId());
        return ability == null ? BuildBounds.SIXTEEN_CUBED : BuildStep.boundsOf(ability.behaviors());
    }

    /**
     * Advances the live cast each client tick.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            CAST.cancel();
            return;
        }
        if (CAST.phase() != BuildChoreography.Phase.IDLE && (mc.screen != null || !stillCasting(player))) {
            CAST.cancel();
        }
        CAST.tick(mc.options.keyUse.isDown(), player.getEyePosition(), player.getViewVector(1f), player.getXRot());
    }

    private static boolean stillCasting(LocalPlayer player) {
        GloveSelection selection = GooCasters.heldSelection(player);
        return GooCasters.casterHand(player) == castHand && selection != null
                && selection.abilityId().equals(castAbility);
    }

    /**
     * The live cast's footprint, which the preview draws.
     *
     * @return the footprint, empty while no cast is live
     */
    public static Optional<BuildFootprint> footprint() {
        return CAST.footprint();
    }

    /**
     * Whether the live cast has fixed its footprint and is setting its rise.
     *
     * @return true while the pitch sets the rise
     */
    public static boolean rising() {
        return CAST.phase() == BuildChoreography.Phase.RISING;
    }

    /**
     * The rock goo the live cast would spend, priced through the plan the
     * server grows by, at the rise the player's goo pays for, each block at the ability's synced cost.
     * decision spire-grows-stone-out-of-the-top
     * decision spire-cost-field-prices-each-cubic-meter-of-stone-at-mundane-block
     *
     * @param player the local player
     * @return the planned growth, empty while no cast is live, the client holds no synced copy of the ability,
     *         or nothing it covers grows
     */
    public static Optional<BuildGrowth> plannedGrowth(LocalPlayer player) {
        GloveSelection selection = GooCasters.heldSelection(player);
        Optional<BuildFootprint> footprint = CAST.footprint();
        if (selection == null || selection.getGooType() == null || footprint.isEmpty()) {
            return Optional.empty();
        }
        ClientAbility ability = AbilitySyncHandler.findAbility(selection.abilityId());
        if (ability == null) {
            return Optional.empty();
        }
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(selection.getGooType(), 0);
        return BuildGrowth.affordable(player.level(), footprint.get(),
                ability.costPrice().exactIn(GooValues.of(player.level())), selection.getGooType(),
                player.getData(GooAttachments.REMAINDERS), holdings);
    }

    /**
     * The ground cell the look meets within the throw range, which a press pins.
     *
     * @param player the local player
     * @return the cell, or null where the look meets no block in range
     */
    private static @Nullable BlockPos pinnedGround(LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(GooThrowHandler.MAX_RANGE));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }
}
