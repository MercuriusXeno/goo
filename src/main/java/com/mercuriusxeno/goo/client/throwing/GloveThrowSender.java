package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.ChargeSize;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.DurativeRoute;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.SpireGrowth;
import com.mercuriusxeno.goo.ability.StreamSound;
import com.mercuriusxeno.goo.ability.program.BlinkLanding;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.CameraEye;
import com.mercuriusxeno.goo.client.ability.CastOrigin;
import com.mercuriusxeno.goo.client.ability.ReserveVisual;
import com.mercuriusxeno.goo.client.ability.VitalityVisual;
import com.mercuriusxeno.goo.client.ability.WindLines;
import com.mercuriusxeno.goo.client.hud.DurativeWash;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.AimTracker;
import com.mercuriusxeno.goo.client.sound.FadingLoops;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.network.GooChargePayload;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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
     * @return true when a payload was sent, the one press the arm plays its pose's release for
     */
    public static boolean sendThrow(Player player, int heldTicks) {
        GloveSelection selection = heldSelection(player);
        ResourceKey<GooTypeDefinition> gooType = selection == null ? null : selection.getGooType();
        if (gooType == null) {
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
        if (delivery.charges()) {
            return sendCharge(player, gooType, abilityId, heldTicks);
        }
        return delivery.kind() == DeliveryKind.SELF
                ? sendSelf(player, gooType, abilityId)
                : choosesOrSendsAimed(player, gooType, abilityId);
    }

    /**
     * Sends an aimed press: with Portal in hand on the player's own portals
     * it chooses or links instead of throwing.
     * portals-link-by-looking-through-portal-vision
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return true when a payload was sent
     */
    private static boolean choosesOrSendsAimed(Player player, ResourceKey<GooTypeDefinition> gooType,
            String abilityId) {
        return PortalAim.selected(abilityId) && PortalAim.press(player) || sendAimed(player, gooType, abilityId);
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
            // decision outlined-target-is-the-one-pick: the tick names the entity the glove outlines
            connection.send(new ServerboundCustomPayloadPacket(
                    held(GooTypes.id(gooType), abilityId, origin, heldAimPoint(player, abilityId))
                            .targeting(outlinedEntityId())));
        }
        // every-ability-casts-from-the-lifted-palm-blob: the server's cone keeps the glove, the drawn effect leaves the blob
        Vec3 drawnFrom = throwOrigin();
        VitalityVisual.drawFog(player, abilityId, selectedArea(abilityId), drawnFrom);
        WindLines.blow(player, abilityId, selectedArea(abilityId), drawnFrom);
        ReserveVisual.drawDrain(player, abilityId);
        loopStreamSound(player, abilityId);
        return true;
    }

    /**
     * Keeps a held stream's looped sound sounding for its holder one more
     * tick at the holder's ears, fading out once the hold ends
     * (decisions decay-gnats-degrade-each-block-once, broil-heat-wave-ring-cooks-ahead).
     *
     * @param player    the local player
     * @param abilityId the selected ability id string
     */
    private static void loopStreamSound(Player player, String abilityId) {
        selectedDelivery(abilityId).sound().filter(StreamSound::loop).ifPresent(sound ->
                FadingLoops.keepAliveAtListener(abilityId, sound.sound(), SoundSource.PLAYERS, sound.volume(),
                        sound.pitchFor(player.getRandom().nextFloat())));
    }

    /**
     * The id of the entity the glove outlines this frame.
     *
     * @return the outlined entity's id, or NO_ENTITY where it outlines none
     */
    private static int outlinedEntityId() {
        return AimTracker.currentTarget() instanceof TargetResult.EntityTarget outlined
                ? outlined.entity().getId() : NO_ENTITY;
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
     * The point a held tick aims at: the mob the aim assist outlines, for an
     * ability that locks on, else the point under the crosshair.
     * decision sunbeam-lands-with-impact-and-aim
     *
     * @param player    the local player
     * @param abilityId the selected ability id string
     * @return the aimed world point
     */
    private static Vec3 heldAimPoint(Player player, String abilityId) {
        Vec3 lockedMob = AimTracker.currentTarget() instanceof TargetResult.EntityTarget locked ? locked.point() : null;
        return ChannelAim.heldAimPoint(locksOn(abilityId), lockedMob, cursorPoint(player));
    }

    /**
     * Whether an ability's held aim follows the aim assist's lock.
     * decision sunbeam-lands-with-impact-and-aim
     *
     * @param abilityId the ability id string, null for none
     * @return true for an ability tagged to lock on
     */
    public static boolean locksOn(@Nullable String abilityId) {
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        return ability != null && ability.tags().contains(AbilityTags.LOCKS_ON);
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
        return sentCursorPoint(Minecraft.getInstance().hitResult, CameraEye.entitySight(player, 1f),
                player.blockInteractionRange());
    }

    /**
     * The world point under the cursor as the frame draws it: along the main
     * camera's ray for the local player in first person, so a cursor drawn
     * there eases with the view on sneak; else the point the server is sent.
     *
     * @param player      the local player
     * @param partialTick the frame's partial tick
     * @return the drawn cursor's world point
     */
    public static Vec3 drawnCursorPoint(Player player, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return cursorPoints(CameraEye.followsTheCamera(mc, player), CameraEye.of(mc, player, partialTick),
                CameraEye.entitySight(player, 1f), mc.hitResult, player.blockInteractionRange(),
                CameraEye.blocksSeenBy(player)).drawn();
    }

    /**
     * The cursor's two points: the one the frame draws and the one sent to
     * the server, which computes from the entity eye.
     *
     * @param drawn the point the frame draws the cursor at
     * @param sent  the point a held tick sends
     */
    public record CursorPoints(Vec3 drawn, Vec3 sent) {
    }

    /**
     * The cursor's drawn and sent points. The sent point is vanilla's pick
     * or the entity eye's reach, what the server computes from; the drawn
     * point follows the camera's ray in first person.
     * decision flatten-disc-cursor-breaks-above-the-plane
     * decision diagnose-and-correct-eye-aimed-visuals-jumping-on-sneak
     *
     * @param followsTheCamera whether the player is the local player seen in first person
     * @param camera           the main camera's sight
     * @param entity           the player's own sight
     * @param picked           vanilla's pick this frame, from the entity eye, or null
     * @param reach            the player's block reach
     * @param blocks           the clip the camera's ray is traced against
     * @return the two points
     */
    public static CursorPoints cursorPoints(boolean followsTheCamera, CameraEye.Sight camera, CameraEye.Sight entity,
                                            @Nullable HitResult picked, double reach, CameraEye.ViewClip blocks) {
        Vec3 sent = sentCursorPoint(picked, entity, reach);
        if (!followsTheCamera) {
            return new CursorPoints(sent, sent);
        }
        BlockHitResult hit = blocks.clip(camera.eye(), camera.eye().add(camera.look().scale(reach)));
        return new CursorPoints(hit.getLocation(), sent);
    }

    private static Vec3 sentCursorPoint(@Nullable HitResult picked, CameraEye.Sight entity, double reach) {
        if (picked instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return entity.eye().add(entity.look().scale(reach));
    }

    /**
     * Whether the selected ability is sized by its charge, growing while
     * right click is held and cast at the aimed target on release.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param abilityId the selected ability id string
     * @return true for a selection sized by its charge
     */
    public static boolean selectedSizesByCharge(@Nullable String abilityId) {
        return selectedDelivery(abilityId).sizesByCharge();
    }

    /**
     * The size the live charge of the held glove's sized ability has reached,
     * cut back to what the player's goo pays for.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param player the local player
     * @return the size, or empty when no charge of a sized ability stands
     */
    public static OptionalDouble chargedSize(Player player) {
        GloveSelection selection = heldSelection(player);
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        if (ability == null || selection.getGooType() == null || !GloveUseTracker.charging()
                || !ability.delivery().sizesByCharge()) {
            return OptionalDouble.empty();
        }
        int holdings = GooSourceScanner.aggregateAvailable(player).getOrDefault(selection.getGooType(), 0);
        return OptionalDouble.of(ChargeSize.affordable(ability.delivery().sizeAt(GloveUseTracker.heldTicks()),
                ability.cost(), holdings));
    }

    /**
     * Sends a charged ability's release with the ticks it was held,
     * when the player can afford it (decision nova-ring-grows-with-the-hold);
     * a cast sized by its charge carries the target the crosshair aims, and
     * sends nothing aiming at none.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @param heldTicks the ticks the use key was held
     * @return true when the payload was sent
     */
    private static boolean sendCharge(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId,
                                      int heldTicks) {
        boolean sized = selectedSizesByCharge(abilityId);
        Optional<GooThrowPayload> aimed = sized ? aimedTarget(gooType, abilityId) : Optional.empty();
        if (sized && aimed.isEmpty() || !affordsThrow(AbilitySyncHandler.findAbility(abilityId),
                amount -> GooSourceScanner.hasEnough(player, gooType, amount),
                reagent -> ReagentScanner.holds(player, reagent))) {
            return false;
        }
        sendPayload(new GooChargePayload(GooTypes.id(gooType), abilityId, heldTicks, GloveUseTracker.scatterSeed(),
                aimed));
        return true;
    }

    /**
     * The target the crosshair aims as a release lets go.
     *
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return the target, empty where the crosshair aims at nothing
     */
    private static Optional<GooThrowPayload> aimedTarget(ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        return Optional.ofNullable(targetToPayload(AimTracker.currentTarget(), gooType, abilityId, throwOrigin()));
    }

    /**
     * Sends a self ability's payload, naming no target, when the player can
     * afford its cost (decision self-delivery-runs-on-player). A blink is
     * priced from the trip it would make along the look
     * (decision blink-lands-safely-costed-by-distance).
     *
     * @param player    the local player
     * @param gooType   the selected goo type
     * @param abilityId the selected ability id string
     * @return true when the payload was sent
     */
    private static boolean sendSelf(Player player, ResourceKey<GooTypeDefinition> gooType, String abilityId) {
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        if (!affordsThrow(ability, BlinkAim.trip(player, ability, 1f),
                amount -> GooSourceScanner.hasEnough(player, gooType, amount),
                reagent -> ReagentScanner.holds(player, reagent))) {
            return false;
        }
        sendPayload(new GooThrowPayload(GooTypes.id(gooType), NO_ENTITY, player.blockPosition(), NO_ENTITY,
                false, abilityId, lineOrigin(), player.position()));
        if (ability != null && DurativeRoute.isDurative(ability.delivery(), ability.badge())
                && !player.getData(GooAttachments.HELD_EFFECTS).holds(ability.id())) {
            washOverTheView(player, gooType);
        }
        return true;
    }

    /**
     * Washes the local player's view in its goo as a durative use lands; the
     * lifted blob settles onto the palm as every cast's does.
     * durative-self-abilities-wash-over-the-view-in-place-of-eating
     * operator ruling: the cuboid vanishing as the wash flows "doesn't look good at all", so it settles
     *
     * @param player  the local player
     * @param gooType the durative ability's goo type
     */
    private static void washOverTheView(Player player, ResourceKey<GooTypeDefinition> gooType) {
        DurativeWash.wash(gooType, player.level().getGameTime());
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
        sendPayload(payload);
        return true;
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
        GooThrowPayload payload = targetToPayload(target, gooType, abilityId, throwOrigin());
        ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        // click-costs-one-mb-and-nothing-under-sculker: a throw a standing ability waives needs no goo held
        boolean free = ability != null && ability.freeFor(player.getData(GooAttachments.HELD_EFFECTS)::holds);
        if (payload == null || refusesStruck(player, payload, ability) || !affordsThrow(ability,
                amount -> free || GooSourceScanner.hasEnough(player, gooType, amount),
                reagent -> ReagentScanner.holds(player, reagent))) {
            return null;
        }
        return payload;
    }

    /**
     * Whether the ability refuses the block the throw would strike, so the
     * aim sends nothing and nothing is paid, as an unaffordable click does;
     * Molten strikes only meltable stone.
     * melt-blob-absorbs-into-lava
     *
     * @param player  the local player
     * @param payload the throw the aim would send
     * @param ability the selected ability's synced copy, or null when none synced
     * @return true when a step of the ability refuses the struck block
     */
    private static boolean refusesStruck(Player player, GooThrowPayload payload, @Nullable ClientAbility ability) {
        return ability != null && payload.targetEntityId() < 0
                && GooThrowHandler.refusesStruck(player.level().getBlockState(payload.targetPos()),
                ability.behaviors());
    }

    /**
     * Whether the player can afford a throw priced the way the server
     * prices it, and holds one of every item the ability consumes, checked
     * before any pose, packet or sound.
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
        return affordsThrow(ability, Optional.empty(), holdsAtLeast, holdsItem);
    }

    /**
     * Whether the player can afford a throw priced from the trip it makes,
     * and holds one of every item the ability consumes.
     * decision blink-lands-safely-costed-by-distance
     *
     * @param ability      the selected ability's synced copy, or null when none synced
     * @param trip         the blink's trip, empty for an ability making none
     * @param holdsAtLeast whether the player holds at least an mB amount of the type
     * @param holdsItem    whether the player holds one of an item
     * @return true when the holdings cover the cost and every reagent
     */
    static boolean affordsThrow(@Nullable ClientAbility ability, Optional<BlinkLanding> trip,
            IntPredicate holdsAtLeast, Predicate<Identifier> holdsItem) {
        return holdsAtLeast.test(throwCostOf(ability, trip))
                && (ability == null || ReagentScanner.holdsEvery(ability.consumes(), holdsItem));
    }

    /**
     * Prices a throw the way the server does: the flat cost plus what a
     * blink's trip adds, falling back to the server's flat cost for an
     * ability the client holds no synced copy of.
     * decision blink-lands-safely-costed-by-distance
     *
     * @param ability the selected ability's synced copy, or null when none synced
     * @param trip    the blink's trip, empty for an ability making none
     * @return the cost in mB
     */
    static int throwCostOf(@Nullable ClientAbility ability, Optional<BlinkLanding> trip) {
        return ability == null ? GooThrowHandler.THROW_COST
                : BlinkAim.tripCost(ability.distancePrice(), ability.cost(), trip);
    }

    /**
     * The cost of the held glove's throw as the crosshair panel reads it: a
     * held effect's upkeep a second, as "20/s", a blink the cost of the trip
     * it would make this frame, any other its one-shot cost.
     * self-effects-trickle-until-ended
     * blink-lands-safely-costed-by-distance
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
        OptionalInt sized = sizedCost(player, ability);
        if (sized.isPresent()) {
            return Optional.of(GooFormat.formatAmount(sized.getAsInt()));
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Optional.of(ability == null ? GooFormat.formatAmount(throwCostOf(null, Optional.empty()))
                : ability.costLabel(BlinkAim.trip(player, ability, partialTick)));
    }

    /**
     * The cost of a cast sized as it is made: a charged cast's size reached,
     * or a Spire's planned stone.
     *
     * @param player  the local player
     * @param ability the selected ability, or null where the client holds no synced copy
     * @return the cost, empty where no sized cast is live
     */
    private static OptionalInt sizedCost(Player player, @Nullable ClientAbility ability) {
        OptionalDouble charged = chargedSize(player);
        if (ability != null && charged.isPresent()) {
            // spontaneous-abilities-cast-at-the-target-with-no-thrown-blob: a sized cast reads the price of its size
            return OptionalInt.of(ChargeSize.costAt(ability.cost(), charged.getAsDouble()));
        }
        if (player instanceof LocalPlayer local && SpireCast.footprint().isPresent()) {
            // spire-grows-stone-out-of-the-top: a Spire reads the goo value of the stone it would grow
            return OptionalInt.of(SpireCast.plannedGrowth(local).map(SpireGrowth::cost).orElse(0));
        }
        return OptionalInt.empty();
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
     * Where a throw leaves: the cast origin, the lifted palm blob where it
     * stands as right click lets go, which the server sends as the flight's
     * start and the wave's hand.
     * every-ability-casts-from-the-lifted-palm-blob
     *
     * @return the world-space start of the throw
     */
    static Vec3 throwOrigin() {
        return CastOrigin.ofLocal(Minecraft.getInstance());
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
        GooThrowPayload payload = payloadFor(target, typeId, abilityId, origin);
        TargetResult.Passage passage = target.passage();
        // aim-and-shots-carry-through-linked-gates: a far-side target names the gate the aim passed
        return passage == null ? payload : payload.through(passage.gate(), passage.facePoint());
    }

    /**
     * The payload a target names, before any gate it was reached through.
     *
     * @param target    the resolved non-None aim target
     * @param typeId    the goo type registry id
     * @param abilityId the selected ability id string
     * @param origin    the aim line start, where the flight leaves from
     * @return the throw payload
     */
    private static GooThrowPayload payloadFor(TargetResult target, String typeId, String abilityId, Vec3 origin) {
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
    static void sendPayload(CustomPacketPayload payload) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }
}
