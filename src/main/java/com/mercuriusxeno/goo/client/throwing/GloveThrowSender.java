package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.ReserveVisual;
import com.mercuriusxeno.goo.client.ability.VitalityVisual;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.network.GooChargePayload;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.network.GooTouchHandler;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Client-only helper that resolves the player's aim target and sends
 * a {@link GooThrowPayload} to the server.
 */
public final class GloveThrowSender {

    /**
     * Sentinel value indicating no entity target.
     */
    private static final int NO_ENTITY = -1;

    private GloveThrowSender() {
    }

    /**
     * Resolves the current aim target and sends the throw packet for the
     * held glove's selection.
     *
     * @param player    the local player
     * @param heldTicks the ticks the use key was held, which a charged ability fires by
     * @return true when a payload was sent, the one press the arm swings for
     */
    public static boolean sendThrow(Player player, int heldTicks) {
        GloveSelection selection = heldSelection(player);
        ResourceKey<GooTypeDefinition> gooType = selection == null ? null : selection.getGooType();
        if (gooType == null || ThrowFreezeState.isThrowBlocked()) {
            return false;
        }
        return sendFor(player, gooType, selection.abilityId(), heldTicks);
    }

    /**
     * Sends the press's payload by the ability's route: a held ability's
     * first tick, a self ability's invocation, or an aimed throw.
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @param heldTicks the ticks the use key was held
     * @return true when a payload was sent
     */
    private static boolean sendFor(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId,
                                   int heldTicks) {
        Delivery delivery = selectedDelivery(abilityId);
        if (HeldRoute.runsWhileHeld(delivery, selectedBadge(abilityId))) {
            return sendStreamTick(player, gooType, abilityId);
        }
        if (delivery.kind() == DeliveryKind.SELF && delivery.charges()) {
            return sendCharge(player, gooType, abilityId, heldTicks);
        }
        return delivery.kind() == DeliveryKind.SELF
                ? sendSelf(player, gooType, abilityId)
                : sendAimed(player, gooType, abilityId);
    }

    /**
     * Carries a held glove one tick further: a stream or channel runs one
     * more tick, and every other delivery does nothing past its press
     * (decisions stream-delivery-held-cone,
     * flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param player the local player
     */
    public static void sendHold(Player player) {
        GloveSelection selection = heldSelection(player);
        ResourceKey<GooTypeDefinition> gooType = selection == null ? null : selection.getGooType();
        if (gooType != null && HeldRoute.runsWhileHeld(selectedDelivery(selection.abilityId()),
                selectedBadge(selection.abilityId()))) {
            sendStreamTick(player, gooType, selection.abilityId());
        }
    }

    /**
     * Sends one tick of a held ability from the glove hand, with the point
     * under the cursor and the plane the press began at, while the player
     * holds any goo of the type; the server prices the tick and stops the
     * hold when the goo runs out.
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return true when the tick was sent
     */
    private static boolean sendStreamTick(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        if (!GooSourceScanner.hasEnough(player, gooType, 1)) {
            return false;
        }
        var connection = Minecraft.getInstance().getConnection();
        Vec3 origin = lineOrigin();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                    held(GooTypes.id(gooType), abilityId, origin, cursorPoint(player))));
        }
        VitalityVisual.drawFog(player, abilityId, selectedArea(abilityId), origin);
        ReserveVisual.drawDrain(player, abilityId);
        return true;
    }

    /**
     * One tick of a held ability, carrying the face the press began on.
     *
     * @param gooTypeId the goo type string identifier
     * @param abilityId the selected ability id string
     * @param origin    the glove hand
     * @param aimPoint  the world point under the cursor
     * @return the payload
     */
    private static GooStreamPayload held(String gooTypeId, String abilityId, Vec3 origin, Vec3 aimPoint) {
        ChannelAim.FacePlane plane = GloveUseTracker.pressPlane();
        return plane == null ? GooStreamPayload.unplaned(gooTypeId, abilityId, origin, aimPoint)
                : new GooStreamPayload(gooTypeId, abilityId, origin, aimPoint, plane.block(),
                        plane.face().get3DDataValue());
    }

    /**
     * The world point under the cursor: the block face the crosshair rests
     * on, or the end of the player's block reach where it rests on none
     * (decision flatten-disc-cursor-breaks-above-the-plane).
     *
     * @param player the local player
     * @return the cursor's world point
     */
    public static Vec3 cursorPoint(Player player) {
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return player.getEyePosition().add(player.getViewVector(1f).scale(player.blockInteractionRange()));
    }

    /**
     * Sends a charged self ability's release with the ticks it was held,
     * when the player can afford it (decision nova-ring-grows-with-the-hold).
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @param heldTicks the ticks the use key was held
     * @return true when the payload was sent
     */
    private static boolean sendCharge(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId,
                                      int heldTicks) {
        if (!affordsThrow(AbilitySyncHandler.findAbility(abilityId),
                amount -> GooSourceScanner.hasEnough(player, gooType, amount),
                reagent -> ReagentScanner.holds(player, reagent))) {
            return false;
        }
        sendPayload(new GooChargePayload(GooTypes.id(gooType), abilityId, heldTicks));
        return true;
    }

    /**
     * Sends a self ability's payload, naming no target, when the player can
     * afford its cost (decision self-delivery-runs-on-player).
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return true when the payload was sent
     */
    private static boolean sendSelf(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        if (!affordsThrow(AbilitySyncHandler.findAbility(abilityId),
                amount -> GooSourceScanner.hasEnough(player, gooType, amount),
                reagent -> ReagentScanner.holds(player, reagent))) {
            return false;
        }
        sendPayload(new GooThrowPayload(GooTypes.id(gooType), NO_ENTITY, player.blockPosition(), NO_ENTITY,
                false, abilityId, lineOrigin(), player.position()));
        return true;
    }

    /**
     * Sends the payload at the aimed target when the player can afford it.
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return true when the payload was sent
     */
    private static boolean sendAimed(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        TargetResult target = AimTracker.currentTarget();
        GooThrowPayload payload = affordablePayload(player, target, gooType, abilityId);
        if (payload == null) {
            return false;
        }
        ThrowFreezeState.arm(target);
        sendPayload(payload);
        return true;
    }

    /**
     * Whether the selected ability touches an entity within reach, the radius
     * the aim draws its ring at.
     * decision mob-ability-touches-at-reach
     *
     * @param abilityId the selected ability id string
     * @return true for a selected mob ability that flies a line
     */
    public static boolean selectedTouchesAtReach(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability != null && GooTouchHandler.touchesAtReach(ability.delivery(), ability.badge());
    }

    /**
     * Builds the throw payload for the aimed target when the player can afford it.
     *
     * @param player    the local player
     * @param target    the resolved aim target
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return the payload, or null for no target or an unaffordable throw
     */
    private static @Nullable GooThrowPayload affordablePayload(Player player, TargetResult target,
            ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        GooThrowPayload payload = targetToPayload(target, gooType, abilityId, lineOrigin());
        if (payload == null || !affordsThrow(AbilitySyncHandler.findAbility(abilityId),
                amount -> GooSourceScanner.hasEnough(player, gooType, amount),
                reagent -> ReagentScanner.holds(player, reagent))) {
            return null;
        }
        return payload;
    }

    /**
     * Whether the player can afford a throw priced the way the server
     * prices it, and holds one of every item the ability consumes, checked
     * before any swing, packet or sound.
     * decision unaffordable-click-does-nothing
     * decision ability-json-names-its-reagent
     *
     * @param ability      the selected ability's synced copy, or null when none synced
     * @param holdsAtLeast whether the player holds at least an mB amount of the type
     * @param holdsItem    whether the player holds one of an item
     * @return true when the holdings cover the cost and every reagent
     */
    static boolean affordsThrow(@Nullable ClientAbility ability, IntPredicate holdsAtLeast,
            Predicate<Identifier> holdsItem) {
        return holdsAtLeast.test(throwCostOf(ability))
                && (ability == null || ReagentScanner.holdsEvery(ability.consumes(), holdsItem));
    }

    /**
     * Prices a throw the way the server does, falling back to its flat cost
     * for an ability the client holds no synced copy of.
     *
     * @param ability the selected ability's synced copy, or null when none synced
     * @return the cost in mB
     */
    static int throwCostOf(@Nullable ClientAbility ability) {
        return ability == null ? GooThrowHandler.THROW_COST : ability.cost();
    }

    /**
     * The cost of the held glove's throw as the crosshair panel reads it: a
     * held effect's upkeep a second, as "20/s", any other its one-shot cost.
     * self-effects-trickle-until-ended
     *
     * @param player the local player
     * @return the formatted cost, or empty when the glove holds no selection
     */
    public static Optional<String> aimedCostLabel(Player player) {
        GloveSelection selection = heldSelection(player);
        if (selection == null || selection.getGooType() == null) {
            return Optional.empty();
        }
        ClientAbility ability = AbilitySyncHandler.findAbility(selection.abilityId());
        return Optional.of(ability == null ? GooFormat.formatAmount(throwCostOf(null)) : ability.costLabel());
    }

    /**
     * The items the held glove's throw consumes beside its goo cost
     * (decision ability-json-names-its-reagent).
     *
     * @param player the local player
     * @return the consumed item ids, empty when the glove holds no selection or the ability consumes none
     */
    public static List<Identifier> aimedReagents(Player player) {
        GloveSelection selection = heldSelection(player);
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        return ability == null ? List.of() : ability.consumes();
    }

    /**
     * The delivery of the selected ability, or a plain arc where the client
     * holds no synced copy (decision standing-abilities-name-arc-or-beam).
     *
     * @param abilityId the selected ability id string
     * @return the delivery
     */
    public static Delivery selectedDelivery(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? Delivery.ARC : ability.delivery();
    }

    /**
     * The area of the selected ability, the shape the glove draws while right
     * click is held (decision right-click-held-previews-release-throws).
     *
     * @param abilityId the selected ability id string
     * @return the area, NONE where the client holds no synced copy
     */
    public static AbilityArea selectedArea(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? AbilityArea.NONE : ability.area();
    }

    /**
     * The badge of the selected ability, the one source of its target kind
     * (decision target-kind-configured-per-ability).
     *
     * @param abilityId the selected ability id string
     * @return the badge, or null where the client holds no synced copy
     */
    public static @Nullable AbilityBadge selectedBadge(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability == null ? null : ability.badge();
    }

    /**
     * The point the aim line starts at, the glove goo the player sees, so
     * the flight leaves from where the line was drawn (decision
     * diagnose-then-fix-goo-off-the-line).
     *
     * @return the world-space aim line origin
     */
    private static Vec3 lineOrigin() {
        return GloveAim.handPosition(Minecraft.getInstance().gameRenderer.getMainCamera());
    }

    /**
     * Converts a target result into a throw payload, or null if no valid target.
     *
     * @param target    the aim target
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the payload, or null for no target
     */
    private static @Nullable GooThrowPayload targetToPayload(TargetResult target,
            ResourceKey<GooTypeDefinition> gooType, String abilityId, Vec3 origin) {
        if (target instanceof TargetResult.None) {
            return null;
        }
        return buildPayload(target, GooTypes.id(gooType), abilityId, origin);
    }

    /**
     * Reads the selection of the glove the player holds, main hand first.
     * Every selection names an ability (decision no-throw-without-ability),
     * so a glove with none selected throws nothing.
     *
     * @param player the local player
     * @return the selection, or null when the glove holds none
     */
    public static @Nullable GloveSelection heldSelection(Player player) {
        ItemStack glove = player.getMainHandItem();
        if (!(glove.getItem() instanceof GooGloveItem)) {
            glove = player.getOffhandItem();
        }
        return GooGloveItem.getSelection(glove);
    }

    /**
     * Builds the payload for non-None targets, kept apart from the None early
     * exit so the switch stays within the complexity threshold.
     *
     * @param target    the resolved non-None aim target
     * @param typeId    the goo type registry id
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the constructed throw payload
     */
    private static GooThrowPayload buildPayload(TargetResult target, String typeId, String abilityId,
            Vec3 origin) {
        return switch (target) {
            case TargetResult.EntityTarget et -> entityPayload(typeId, et, abilityId, origin);
            case TargetResult.BlockTarget bt -> blockPayload(typeId, bt, abilityId, origin);
            case TargetResult.GlowCrystalTarget gct -> new GooThrowPayload(typeId, NO_ENTITY,
                    gct.pos(), gct.face().ordinal(), false, abilityId, origin, gct.point());
            case TargetResult.PointTarget pt -> new GooThrowPayload(typeId, NO_ENTITY,
                    pt.pos(), pt.face().ordinal(), false, abilityId, origin, pt.point());
            default -> throw new IllegalArgumentException(target.toString());
        };
    }

    /**
     * Builds a throw payload aimed at an entity.
     *
     * @param typeId    the goo type registry id
     * @param et        the entity aim target
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the entity-targeted throw payload
     */
    private static GooThrowPayload entityPayload(String typeId,
            TargetResult.EntityTarget et, String abilityId, Vec3 origin) {
        return new GooThrowPayload(typeId, et.entity().getId(), BlockPos.ZERO, NO_ENTITY,
                false, abilityId, origin, et.point());
    }

    /**
     * Builds a throw payload aimed at a block face.
     *
     * @param typeId    the goo type registry id
     * @param bt        the block face aim target
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the block-targeted throw payload
     */
    private static GooThrowPayload blockPayload(String typeId,
            TargetResult.BlockTarget bt, String abilityId, Vec3 origin) {
        return new GooThrowPayload(typeId, NO_ENTITY, bt.pos(), bt.face().ordinal(),
                bt.grannyArc(), abilityId, origin, bt.point());
    }

    /**
     * Sends a custom payload packet to the server.
     *
     * @param payload the payload to send
     */
    private static void sendPayload(CustomPacketPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
