package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.ability.SpireLift;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooSpirePayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
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
 * The glove's side of a Spire: routes the right clicks of a selection cast
 * by its footprint into {@link SpireChoreography} rather than the throw
 * press, advances it each tick off the use key and the look, and sends the
 * submit. A screen, a hand without the glove or another selection drops the
 * cast.
 * decision spire-rips-walls-and-platforms
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SpireCast {

    /** The blocks the client prices a Spire preview by, the tag the shipped Spire lifts. */
    private static final TagKey<Block> PREVIEW_LIFTS =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Goo.MODID, "spire_liftable"));

    private static final SpireChoreography CAST = new SpireChoreography();
    private static InteractionHand castHand = InteractionHand.MAIN_HAND;
    private static @Nullable String castAbility;

    private SpireCast() {
    }

    /**
     * Whether the held glove's selection is cast by its footprint.
     *
     * @param player the local player
     * @return true for a Spire selection
     */
    public static boolean selected(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        return ability != null && ability.tags().contains(AbilityTags.FOOTPRINT);
    }

    /**
     * A right click with a Spire selected: pins the corner, or submits the
     * cast while its rise is being set.
     *
     * @param player the local player
     * @param hand   the hand holding the glove
     */
    public static void press(LocalPlayer player, InteractionHand hand) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        if (selection == null || selection.getGooType() == null) {
            return;
        }
        if (CAST.phase() == SpireChoreography.Phase.IDLE) {
            castHand = hand;
            castAbility = selection.abilityId();
        }
        Optional<SpireFootprint> submitted = CAST.press(pinnedGround(player));
        submitted.ifPresent(footprint -> {
            player.connection.send(new ServerboundCustomPayloadPacket(new GooSpirePayload(
                    GooTypes.id(selection.getGooType()), selection.abilityId(), footprint.corner(),
                    footprint.opposite(), footprint.rise())));
            player.swing(castHand);
        });
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
        if (CAST.phase() != SpireChoreography.Phase.IDLE && (mc.screen != null || !stillCasting(player))) {
            CAST.cancel();
        }
        CAST.tick(mc.options.keyUse.isDown(), player.getEyePosition(), player.getViewVector(1f), player.getXRot());
    }

    private static boolean stillCasting(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        return player.getItemInHand(castHand).getItem() instanceof GooGloveItem && selection != null
                && selection.abilityId().equals(castAbility);
    }

    /**
     * The live cast's footprint, which the preview draws.
     *
     * @return the footprint, empty while no cast is live
     */
    public static Optional<SpireFootprint> footprint() {
        return CAST.footprint();
    }

    /**
     * Whether the live cast has fixed its footprint and is setting its rise.
     *
     * @return true while the pitch sets the rise
     */
    public static boolean rising() {
        return CAST.phase() == SpireChoreography.Phase.RISING;
    }

    /**
     * The rock goo the live cast would spend, priced through the plan the
     * server lifts by, at the rise the player's goo pays for.
     *
     * @param player the local player
     * @return the planned lift, empty while no cast is live or nothing it covers lifts
     */
    public static Optional<SpireLift> plannedLift(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        Optional<SpireFootprint> footprint = CAST.footprint();
        if (selection == null || selection.getGooType() == null || footprint.isEmpty()) {
            return Optional.empty();
        }
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(selection.getGooType(), 0);
        return SpireLift.affordable(player.level(), footprint.get(), PREVIEW_LIFTS, GooValues.of(player.level()),
                selection.getGooType(), holdings);
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
