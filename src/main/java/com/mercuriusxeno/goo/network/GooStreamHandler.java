package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityPrice;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HealReport;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.StreamSound;
import com.mercuriusxeno.goo.ability.end.EndAvailability;
import com.mercuriusxeno.goo.ability.end.EndWayBack;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.DripLanding;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.FloorReach;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.LeafStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.PlacementPrice;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepHost;
import com.mercuriusxeno.goo.ability.program.TartarusStep;
import com.mercuriusxeno.goo.ability.spray.SprayPrograms;
import com.mercuriusxeno.goo.ability.tartarus.TartarusCast;
import com.mercuriusxeno.goo.ability.tartarus.TartarusHolds;
import com.mercuriusxeno.goo.block.ability.FluidFooting;
import com.mercuriusxeno.goo.block.ability.SlickLayPace;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.item.SprayerSetting;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.ConeShape;
import com.mercuriusxeno.goo.throwing.ConvergingCone;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Server side of a stream delivery: each tick the glove's use stays down,
 * one share of the ability's cost drains and its programs run on every
 * living entity inside the cone; a hold stops when the use releases or the
 * goo runs out (decision stream-delivery-held-cone). A channel, a self
 * ability wearing the channeled badge, drains the same share and runs its
 * programs on the player, carrying the tick's aim
 * (decision flatten-disc-cursor-breaks-above-the-plane). A stream whose
 * program needs the channel runs a block pass instead of striking entities:
 * its programs run on the player, aimed at the end of its reach along the
 * look, Bore's reach being its caster's block reach
 * (decisions bore-vortex-with-a-worldspace-shake, bore-reaches-as-far-as-your-block-reach).
 */
public final class GooStreamHandler {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on its held pass's host: {}";
    /** Log: one held tick reached the server, so a hold that does nothing shows where it stops. */
    private static final String LOG_HELD_TICK = "Held tick of {} for {}: hold tick {}";
    /**
     * Launch speed per block of the stream's range: a mote slowing by a tenth
     * each tick carries ten times its launch speed, so it reaches the cone's end.
     */
    public static final double LAUNCH_SPEED_PER_BLOCK = 0.1;
    /** Below this squared gap the crosshair sits on the hand and the look aims instead. */
    private static final double MIN_AIM_LENGTH_SQUARED = 1e-6;
    /** The drips a tick of motes off the arc carries: none. */
    private static final int NO_DRIPS = 0;
    /**
     * How far Mist's mist reaches from the eye, in blocks: short, a tuning value.
     * operator ruling 2026-11-18: Mist is short range
     */
    static final double MIST_REACH = 5;
    /** Spots a mister's mist reaches a tick for each drip the residual throws a tick at that flow. */
    private static final int MIST_SPOTS_PER_DRIP = 4;
    /** Rays the spray casts each tick to find the floors it lands on. */
    private static final int FLOOR_RAYS_PER_TICK = 12;
    /** Reads which living things a tick's program healed. */
    private static final HealReport<LivingEntity> HEALS =
            new HealReport<>(LivingEntity::getHealth, LivingEntity::getId);

    private GooStreamHandler() {
    }

    /**
     * Handles a stream tick on the server thread.
     *
     * @param payload the stream tick
     * @param context the network context
     */
    public static void handle(GooStreamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                streamTick(player, payload);
            }
        });
    }

    /**
     * Runs one tick of a held ability: a stream or channel of the type, held
     * in a glove, drains its share for this tick of the hold, then a stream
     * strikes its cone and a channel runs on the player.
     *
     * @param player  the streaming player
     * @param payload the stream tick
     */
    public static void streamTick(ServerPlayer player, GooStreamPayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !GooThrowHandler.validateCaster(player, gooType)) {
            return;
        }
        AbilityDefinition ability = heldAbility(player, payload, gooType);
        int held = ability == null ? 0 : drainShare(player, gooType, ability);
        Goo.LOGGER.debug(LOG_HELD_TICK, payload.abilityId(), player.getName().getString(), held);
        if (held == 0) {
            return;
        }
        runHeldTick(player, payload, ability, held);
        // every-ability-casts-from-the-lifted-palm-blob: each held tick the server runs keeps the blob lifted
        PalmBlobChargePayload.sendLift(player);
        // mycosis-spore-stream-buds-and-poisons
        ability.delivery().sound().filter(sound -> sound.playsOn(held))
                .ifPresent(sound -> playStreamSound(player, sound));
    }

    /**
     * Runs one tick of a sprayer's hold: the residual of its fitted canister's goo sprays through the path the
     * glove's residuals run, paying from that canister alone. The sprayer checks for no glove and no recipe
     * knowledge, since a residual is its goo type's resting behavior.
     * decision spray-item-sprays-residuals-beside-the-glove
     * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
     *
     * @param player the spraying player
     * @param origin the sprayer's nozzle end, where the drips leave
     */
    public static void sprayerTick(ServerPlayer player, Vec3 origin) {
        ItemStack sprayer = SprayerItem.heldSprayer(player);
        SprayerSetting setting = SprayerItem.settingOf(sprayer);
        // sprayer-wheel-picks-nozzle-flow-and-goo-type: the type picked on the wheel sprays where the canister holds it
        Optional<AbilityDefinition> residual = SprayerItem.sprayedResidual(
                CanisterItem.getFluidContent(SprayerItem.fittedCanister(sprayer)), setting.pickedType(),
                AbilityRegistry.of(player.level())::residualAbilityFor);
        if (residual.isEmpty()) {
            return;
        }
        AbilityDefinition ability = residual.get();
        int held = sprayerShare(player, ability);
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_HELD_TICK, ability.id(), player.getName().getString(), held);
        }
        if (held == 0) {
            return;
        }
        strikeCone(player, origin, ability, held, new HeldSpray(PaysFrom.SPRAYER_CANISTER, setting.flow().rate(),
                setting.nozzle()));
        ability.delivery().sound().filter(sound -> sound.playsOn(held))
                .ifPresent(sound -> playStreamSound(player, sound));
    }

    /**
     * Advances a sprayer's hold and pays this tick's share from its canister; a residual draining on placement
     * takes nothing for the tick, paying as what it lays lands.
     *
     * @param player  the spraying player
     * @param ability the sprayed residual
     * @return the hold's tick count, or 0 for a second tick in one server tick or a share the canister cannot pay
     */
    private static int sprayerShare(ServerPlayer player, AbilityDefinition ability) {
        MinecraftServer server = player.level().getServer();
        int held = GooServerState.of(server).streamHolds().advance(player.getUUID(), server.getTickCount());
        if (held == 0 || ability.delivery().drainsOnPlacement()) {
            return held;
        }
        AbilityPrice cost = ability.cost().forCasterIn(player.level().dimension(), ability.behaviors());
        int share = StreamHolds.shareAt(cost.exactIn(GooValues.of(player.level())),
                ability.delivery().ticksPerCharge(), held);
        return AbilityCharges.charge(player, PaysFrom.SPRAYER_CANISTER, ability.gooType(), GooFraction.of(share))
                ? held : 0;
    }

    /**
     * Runs a paid tick of a held ability: a channel runs on the player, and
     * a stream strikes its cone.
     *
     * @param player  the holding player
     * @param payload the held tick
     * @param ability the held ability
     * @param held    the hold's tick count, 1 on its first tick
     */
    private static void runHeldTick(ServerPlayer player, GooStreamPayload payload, AbilityDefinition ability,
                                    int held) {
        if (HeldRoute.channelsOnSelf(ability.delivery(), ability.badge())) {
            channelOnPlayer(player, new ChannelAim(payload.aimPoint(), payload.plane(), 0, held, true,
                    payload.targetEntityId()), ability);
        } else {
            strikeCone(player, payload.origin(), ability, held, HeldSpray.GLOVE);
        }
    }

    /**
     * The glove hand a stream's cone opens from: the hand the client sent,
     * kept within the player's reach.
     *
     * @param player the streaming player
     * @param origin the glove hand the client sent
     * @return the cone's apex
     */
    static Vec3 coneApex(ServerPlayer player, Vec3 origin) {
        return ThrowArc.clampToReach(player.getEyePosition(), origin, ThrowArc.HAND_REACH * player.getScale());
    }

    /**
     * Plays one beat of the held ability's sound at the player, its pitch
     * strayed a little: everyone near hears it at the holder's eye, and the
     * holder hears it at the listener, or, for a looped sound, hears their
     * own fading loop instead.
     *
     * @param player the streaming player
     * @param sound  the stream's sound
     */
    private static void playStreamSound(ServerPlayer player, StreamSound sound) {
        SoundCue cue = new SoundCue(sound.sound(), SoundKind.PLAYERS, sound.volume(),
                sound.pitchFor(player.getRandom().nextFloat()));
        if (sound.loop()) {
            // decay-gnats-degrade-each-block-once
            SoundPlays.playExcept(player.level(), player, player.getEyePosition(), cue);
        } else {
            // diagnose-and-correct-the-spray-squelch-playing-in-one-ear
            SoundPlays.playAtListener(player.level(), player, player.getEyePosition(), cue);
        }
    }

    /**
     * The ability the payload names, where the player may use it, it stands available to them and it runs while
     * held: End grayed out in the End runs nothing and drains nothing.
     *
     * @param player  the holding player
     * @param payload the held tick
     * @param gooType the ability's goo type
     * @return the ability, or null for one the player cannot use or one that does not run while held
     */
    private static @Nullable AbilityDefinition heldAbility(ServerPlayer player, GooStreamPayload payload,
                                                            ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        // decision end-is-grayed-out-in-the-end-unless-you-came-by-the-ability
        boolean grayedOut = ability != null && EndAvailability.graysOut(ability.behaviors(),
                player.level().dimension(), EndWayBack.heldBy(player).stands());
        return ability != null && !grayedOut && HeldRoute.runsWhileHeld(ability.delivery(), ability.badge())
                ? ability : null;
    }

    /**
     * Runs a channel's programs on the player for this tick of the hold,
     * logging a program the player host refuses.
     *
     * @param player  the channeling player
     * @param aim     the hold's aim this tick
     * @param ability the channel ability
     */
    private static void channelOnPlayer(ServerPlayer player, ChannelAim aim, AbilityDefinition ability) {
        runSteps(PlayerHost.channeling(player.level(), player, aim), HostKind.PLAYER, ability.behaviors(),
                new CastOn(player.level(), player, ability.id()));
    }

    /**
     * Drains this tick's share of the ability's cost; a stream draining on
     * placement takes nothing for the tick, paying as what it lays lands.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param player  the streaming player
     * @param gooType the ability's goo type
     * @param ability the stream ability
     * @return the hold's tick count, or 0 when the tick runs nothing: a second stream tick in one
     *         server tick, a share the player cannot pay, which stops the stream, or a Tartarus hold
     *         its first tick refused, which drains and consumes nothing
     */
    private static int drainShare(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                      AbilityDefinition ability) {
        MinecraftServer server = player.level().getServer();
        int held = GooServerState.of(server).streamHolds().advance(player.getUUID(), server.getTickCount());
        if (held == 0) {
            return 0;
        }
        // decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
        Optional<TartarusCast> tartarus = TartarusStep.in(ability.behaviors())
                .map(step -> TartarusHolds.castFor(player, step.reach(), held));
        if (tartarus.filter(TartarusCast::refused).isPresent() || !paysReagents(player, ability, held)) {
            return 0;
        }
        if (ability.delivery().drainsOnPlacement()) {
            return held;
        }
        // decision end-gate-in-the-end-is-free-and-returns-you-where-you-portalled-in
        AbilityPrice cost = ability.cost().forCasterIn(player.level().dimension(), ability.behaviors());
        int tickShare = StreamHolds.shareAt(cost.exactIn(GooValues.of(player.level())),
                ability.delivery().ticksPerCharge(), held);
        int share = tartarus.map(cast -> cast.charged(tickShare)).orElse(tickShare);
        if (!GooSourceScanner.hasEnough(player, gooType, share)) {
            return 0;
        }
        GooSourceScanner.deplete(player, gooType, share);
        return held;
    }

    /**
     * Whether a hold has its reagents: a held ability consuming items takes
     * one of each on the hold's first tick, and a hold that could not pay
     * then runs nothing until a new hold starts; one consuming nothing pays
     * nothing (decision ability-json-names-its-reagent).
     *
     * @param player  the holding player
     * @param ability the held ability
     * @param held    the hold's tick count, 1 on its first tick
     * @return true when the hold may run this tick
     */
    private static boolean paysReagents(ServerPlayer player, AbilityDefinition ability, int held) {
        if (ability.consumes().isEmpty()) {
            return true;
        }
        StreamHolds holds = GooServerState.of(player.level().getServer()).streamHolds();
        if (held == ChannelAim.FIRST_TICK && ReagentScanner.holdsEvery(player, ability.consumes())) {
            ReagentScanner.consumeOneOfEach(player, ability.consumes());
            holds.markPaid(player.getUUID());
        }
        return holds.paid(player.getUUID());
    }

    /**
     * Sprays the cone from the glove hand along the player's look, runs the
     * block pass on the player and the entity pass on every living entity
     * inside the cone.
     *
     * @param player  the streaming player
     * @param origin  the glove hand the client sent
     * @param ability the stream ability
     * @param held    the hold's tick count, 1 on its first tick
     * @param spray   where what the tick lays pays from and how much it throws
     */
    private static void strikeCone(ServerPlayer player, Vec3 origin, AbilityDefinition ability, int held,
                                   HeldSpray spray) {
        ServerLevel level = player.level();
        // bore-reaches-as-far-as-your-block-reach
        Delivery delivery = ability.delivery().reachingTo(player.blockInteractionRange());
        Vec3 apex = coneApex(player, origin);
        Vec3 axis = player.getLookAngle();
        CastOn cast = new CastOn(level, player, ability.id(), spray);
        List<Step> entitySteps = channelSteps(ability.behaviors(), false);
        TickHeals heals = new TickHeals(new ArrayList<>(), new ArrayList<>());
        if (delivery.range() > 0 || delivery.throwsDrips()) {
            // reserve-hearts-sit-behind-the-bar: a stream reaching nothing runs only on its caster
            List<LivingEntity> struck = sprayAndStrike(cast, apex, axis, ability, delivery, held);
            boolean heal = heals(entitySteps);
            for (LivingEntity living : struck) {
                HEALS.runNoting(living, heals.healed(), () -> runSteps(new EntityHost(level, living, player),
                        HostKind.ENTITY, entitySteps, cast));
                if (heal) {
                    // touched-mobs-show-hearts-or-a-count-over-max: a mob already at full life shows its life too
                    heals.touched().add(living.getId());
                }
            }
            sprayFloors(player, apex, axis, ability, delivery);
        }
        strikeCaster(cast, ability, apex, entitySteps, heals);
    }

    /**
     * Who one held tick's heal reached: those whose health rose, and every
     * mob in the cone the heal ran on.
     *
     * @param healed  the ids of the living whose health rose
     * @param touched the ids of the mobs in the cone the heal ran on
     */
    private record TickHeals(List<Integer> healed, List<Integer> touched) {
    }

    /**
     * Whether a program heals: a heal step runs somewhere in it.
     *
     * @param steps the program
     * @return true where a heal step stands among the steps or their children
     */
    private static boolean heals(List<Step> steps) {
        return steps.stream().flatMap(GooStreamHandler::withDescendants)
                .anyMatch(step -> step instanceof LeafStep<?> leaf && leaf.leaf() == LeafSteps.HEAL);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(GooStreamHandler::withDescendants));
    }

    /**
     * Runs the stream's block pass, shows its motes, and answers the living
     * its entity pass strikes this tick: a mob-first stream's swarm converges
     * on the mob it chases or the block under the crosshair, biting the mob
     * once its gnats arrive; any other stream sprays its particle onto the
     * point the crosshair lands on.
     *
     * @param cast    the tick's cast
     * @param apex    the cone's apex, the glove hand
     * @param axis    the look
     * @param ability  the stream ability
     * @param delivery the stream's delivery as it reaches for the caster this tick
     * @param held     the hold's tick count
     * @return the living the entity pass strikes
     */
    private static List<LivingEntity> sprayAndStrike(CastOn cast, Vec3 apex, Vec3 axis, AbilityDefinition ability,
                                                     Delivery delivery, int held) {
        ServerPlayer player = cast.player();
        if (delivery.throwsDrips()) {
            // goop-wobbles-a-mob-its-drip-strikes: a stream throwing drips strikes the living its drips strike, not
            // the living in its cone
            sprayResidual(cast, apex, axis, ability, delivery, held);
            return List.of();
        }
        // decay-gnats-degrade-each-block-once: what the stream strikes follows the look; only its motes
        // fly from the hand, a mob-first swarm's converging on the mob it chases or the block it paints
        BlockHitResult crosshair = crosshairHit(player, delivery.range());
        Vec3 aim = aimFrom(apex, crosshair.getLocation(), axis);
        List<LivingEntity> struck = runPasses(cast, apex, axis, ability, delivery, held);
        if (ability.hasTag(AbilityTags.MOB_FIRST)) {
            SwarmStrike.send(player, apex, aim, delivery, struck, crosshair);
            return SwarmStrike.reached(player, apex, struck, held);
        }
        sprayParticles(player, apex, aim, delivery,
                crosshairSpeed(delivery.range(), apex.distanceTo(crosshair.getLocation())));
        return struck;
    }

    /**
     * Throws a stream's drips into flight from the blob, as many as the
     * delivery throws a tick, each onto its share's spot of the cone at the
     * exit speed alone; each lands what the program's landing step lays where
     * its arc strikes, during the hold or after it, and a drip of a program
     * striking the living ends on the first living thing it meets.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     * goop-drains-per-cubic-meter-of-slime-placed: the spray's pace is how many drips land a tick, each a layer
     * goop-wobbles-a-mob-its-drip-strikes
     * goop-and-slick-drips-add-none-of-the-players-movement
     *
     * @param cast    the tick's cast, naming who sprays and where they pay from
     * @param apex    where the drips leave, the lifted blob
     * @param cone    the cone the drips land in
     * @param ability the stream ability
     * @param held    the hold's tick count, which picks the tick's places in the cone
     * @return true where the drips strike the living they meet
     */
    private static boolean throwDrips(CastOn cast, Vec3 apex, ConvergingCone cone, AbilityDefinition ability,
                                      int held) {
        ServerPlayer player = cast.player();
        Optional<DripLanding> landing = DripLanding.in(channelSteps(ability.behaviors(), true));
        boolean strikesLiving = !channelSteps(ability.behaviors(), false).isEmpty();
        if (landing.isEmpty() && !strikesLiving) {
            return false;
        }
        DripFlights.Thrower thrower = new DripFlights.Thrower(player.level().dimension(), player.getUUID(),
                player.getId(), ability, landing, strikesLiving, cast.spray().paysFrom());
        Delivery delivery = ability.delivery();
        DripFlights flights = GooServerState.of(player.level().getServer()).dripFlights();
        // spray-item-takes-different-nozzles-doing-different-things: the sprayer's nozzle shapes the cone
        flightsOf(thrower, new DripThrow(apex, cone, exitSpeedOf(cast, delivery),
                DripArc.gravityIn(player.level().dimension())), cast.spray().shapeOf(delivery),
                CoatRay.scatteredShares(held, dripsThisTick(player, delivery, landing, held, cast.spray().dripRate())))
                .forEach(flights::launch);
        return strikesLiving;
    }

    /**
     * Sprays a stream throwing drips: a mister's mist lays and shows over the cone, and any other spray runs its
     * block pass, throws its drips and shows them.
     *
     * @param cast    the tick's cast
     * @param apex    where the drips leave, the lifted blob
     * @param axis    the look
     * @param ability  the stream ability
     * @param delivery the stream's delivery as it reaches for the caster this tick
     * @param held     the hold's tick count
     */
    private static void sprayResidual(CastOn cast, Vec3 apex, Vec3 axis, AbilityDefinition ability, Delivery delivery,
                                      int held) {
        ServerPlayer player = cast.player();
        // residual-spray-cone-centers-on-the-look-from-the-blob: the drips, the block pass and the drawn drips
        // all leave the blob onto one cone centered on the look, whatever block the crosshair meets
        // nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob: an aim near a mob centers the cone on it
        double exitSpeed = exitSpeedOf(cast, delivery);
        DripArc.Flight flight = dripFlightIn(player.level(), ability);
        ConvergingCone cone = NectarThrows.guidedCone(player, channelSteps(ability.behaviors(), true))
                .orElseGet(() -> ConvergingCone.along(player.getEyePosition(), axis, exitSpeed, flight));
        if (cast.spray().mists()) {
            // mister-draws-mist-and-lays-one-layer-on-bare-spots: the mist lays and shows, no drip flies
            layMist(cast, cone, ability, held);
            sprayDrips(cast, apex, cone, ability, false, held);
            return;
        }
        runBlockPass(cast, dripsEnd(apex, cone, delivery, exitSpeed, flight), delivery, ability, held, true);
        sprayDrips(cast, apex, cone, ability, throwDrips(cast, apex, cone, ability, held), held);
    }

    /**
     * Lays a mister's tick: the cone's rays reach out from the eye as far as the mist reaches, and each cell a ray
     * meets that holds no layer takes one through the residual's own landing, paid for as it lands, so a covered
     * spot takes nothing and costs nothing, and adhesion, cohesion and the residual's own cell stay its own. The
     * flow scales how many spots a tick the mist reaches.
     * mister-draws-mist-and-lays-one-layer-on-bare-spots
     * mister-nozzle-applies-one-layer-at-most
     *
     * @param cast    the tick's cast
     * @param cone    the cone the mist covers, from the eye along the look
     * @param ability the sprayed residual
     * @param held    the hold's tick count, which picks the tick's spots in the cone
     */
    private static void layMist(CastOn cast, ConvergingCone cone, AbilityDefinition ability, int held) {
        Optional<DripLanding> landing = DripLanding.in(channelSteps(ability.behaviors(), true));
        if (landing.isEmpty()) {
            return;
        }
        ServerPlayer player = cast.player();
        ServerLevel level = cast.level();
        Delivery delivery = ability.delivery();
        ConeShape shape = cast.spray().shapeOf(delivery);
        ChannelHost host = PlayerHost.landing(level, player, placementPrice(player, ability, cast.spray().paysFrom()));
        CoatRay.BlockClip clip = CoatRay.clippingFor(level, landing.get().fluidFooting());
        int spots = MIST_SPOTS_PER_DRIP * dripsThisTick(player, delivery, landing, held, cast.spray().dripRate());
        for (CoatRay.ConeShare share : CoatRay.scatteredShares(held, spots)) {
            Vec3 ray = StreamCone.launchDirection(cone.axis(), shape, share.tilt(), share.turn());
            clip.firstHit(cone.eye(), cone.eye().add(ray.scale(MIST_REACH)))
                    .flatMap(strike -> landing.get().cellFed(host, strike))
                    .filter(fed -> VolumeCellBlock.layersOf(level.getBlockState(fed.cell())) == 0)
                    .ifPresent(fed -> landing.get().feed(host, fed));
        }
    }

    /**
     * Where a tick's drips leave and what they are thrown onto.
     *
     * @param apex      where the drips leave, the lifted blob
     * @param cone      the cone they land in
     * @param exitSpeed their exit speed, blocks per tick
     * @param gravity   the pull on them each tick
     */
    public record DripThrow(Vec3 apex, ConvergingCone cone, double exitSpeed, double gravity) {
    }

    /**
     * A tick's drip flights: each leaves the blob at its share's launch.
     * residual-spray-cone-centers-on-the-look-from-the-blob: every drip leaves the blob
     *
     * @param thrower  who threw them
     * @param thrown   where they leave and what they are thrown onto
     * @param delivery the stream delivery
     * @param shares   the tick's places in the cone, one a drip
     * @return the flights, one a share
     */
    static List<DripFlights.Flight> flightsOf(DripFlights.Thrower thrower, DripThrow thrown, Delivery delivery,
                                              List<CoatRay.ConeShare> shares) {
        return flightsOf(thrower, thrown, ConeShape.round(delivery.coneDegrees()), shares);
    }

    /**
     * A tick's drip flights in a cone of a shape.
     * spray-item-takes-different-nozzles-doing-different-things
     *
     * @param thrower who threw them
     * @param thrown  where they leave and what they are thrown onto
     * @param shape   the cone's widths across and up and down
     * @param shares  the tick's places in the cone, one a drip
     * @return the flights, one a share
     */
    static List<DripFlights.Flight> flightsOf(DripFlights.Thrower thrower, DripThrow thrown, ConeShape shape,
                                              List<CoatRay.ConeShare> shares) {
        return shares.stream().map(share -> new DripFlights.Flight(thrower, thrown.apex(),
                dripLaunch(thrown, shape, share))).toList();
    }

    /**
     * One server drip's launch velocity for its share of the cone, the
     * launch its drawn drip takes from the same blob onto the same cone.
     * residual-spray-cone-centers-on-the-look-from-the-blob
     *
     * @param thrown   where the drip leaves and what it is thrown onto
     * @param delivery the stream delivery
     * @param share    the drip's place in the cone
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 dripLaunch(DripThrow thrown, Delivery delivery, CoatRay.ConeShare share) {
        return dripLaunch(thrown, ConeShape.round(delivery.coneDegrees()), share);
    }

    /**
     * One server drip's launch for its share of a cone of a shape, the launch its drawn drip takes.
     * spray-item-takes-different-nozzles-doing-different-things
     *
     * @param thrown where the drip leaves and what it is thrown onto
     * @param shape  the cone's widths across and up and down
     * @param share  the drip's place in the cone
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 dripLaunch(DripThrow thrown, ConeShape shape, CoatRay.ConeShare share) {
        return thrown.cone().launchFrom(thrown.apex(), shape, share, thrown.exitSpeed(), thrown.gravity());
    }

    /**
     * What a stream's drips fly against in a level: its blocks, stopping at
     * the fluid sources the drips' landing names, under the level's gravity.
     * rime-drips-stop-at-a-water-or-lava-surface
     *
     * @param level   the level the drips fly in
     * @param ability the stream ability
     * @return the flight, meeting no living thing
     */
    private static DripArc.Flight dripFlightIn(ServerLevel level, AbilityDefinition ability) {
        FluidFooting footing = DripLanding.in(channelSteps(ability.behaviors(), true))
                .map(DripLanding::fluidFooting).orElse(FluidFooting.NONE);
        return new DripArc.Flight(CoatRay.clippingFor(level, footing), DripArc.NO_LIVING,
                DripFlights.airspaceOf(level), DripArc.gravityIn(level.dimension()));
    }

    /**
     * Runs a stream's entity steps on a living thing one of its drips struck,
     * its caster as their source. Each strike runs them again, so a mob the
     * drips keep striking has its wobble refreshed by each, as the cone
     * refreshed it each tick it stood there.
     * goop-wobbles-a-mob-its-drip-strikes
     *
     * @param level   the level the drip struck in
     * @param caster  the player who threw the drip
     * @param living  the living thing struck
     * @param ability the stream ability
     */
    static void strikeLiving(ServerLevel level, ServerPlayer caster, LivingEntity living, AbilityDefinition ability) {
        runSteps(new EntityHost(level, living, caster), HostKind.ENTITY, channelSteps(ability.behaviors(), false),
                new CastOn(level, caster, ability.id()));
    }

    /**
     * Where a stream's block pass reaches along the look: the end of its range.
     *
     * @param player   the streaming player
     * @param axis     the look
     * @param delivery the stream's delivery
     * @return the point the block pass aims at
     */
    private static Vec3 rangeEnd(ServerPlayer player, Vec3 axis, Delivery delivery) {
        return player.getEyePosition().add(axis.scale(delivery.range()));
    }

    /**
     * Where a stream throwing drips aims its block pass: where the arc of
     * the drip thrown from the blob onto the cone's axis ends.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     * residual-spray-cone-centers-on-the-look-from-the-blob
     *
     * @param apex      where the drips leave, the lifted blob
     * @param cone      the cone the drips land in
     * @param delivery  the stream's delivery
     * @param exitSpeed the drips' exit speed, blocks per tick
     * @param flight    what a drip's flight runs against
     * @return the point the block pass aims at
     */
    private static Vec3 dripsEnd(Vec3 apex, ConvergingCone cone, Delivery delivery, double exitSpeed,
                                 DripArc.Flight flight) {
        return DripArc.flyToItsEnd(apex, cone.launchFrom(apex, delivery.coneDegrees(), CoatRay.coneFan().getFirst(),
                exitSpeed, flight.gravity()), flight).position();
    }


    /**
     * Runs the entity pass on the caster of a stream tagged self, then tells
     * the watchers who the tick healed (decision vitality-waves-regenerate-and-court).
     *
     * @param cast        the tick's cast
     * @param ability     the stream ability
     * @param apex        the cone's apex
     * @param entitySteps the entity pass's steps
     * @param heals       who the tick healed and touched so far; the caster joins the healed
     */
    private static void strikeCaster(CastOn cast, AbilityDefinition ability, Vec3 apex, List<Step> entitySteps,
                                     TickHeals heals) {
        ServerPlayer player = cast.player();
        if (ability.hasTag(AbilityTags.SELF)) {
            HEALS.runNoting(player, heals.healed(),
                    () -> runSteps(new PlayerHost(cast.level(), player), HostKind.PLAYER, entitySteps, cast));
        }
        if (!heals.healed().isEmpty() || !heals.touched().isEmpty()) {
            // the client homes goo to each healed thing and stars it, and shows each touched mob's life
            EntityVisuals.sendToWatchers(player,
                    new StreamHealedPayload(player.getId(), apex, heals.healed(), heals.touched()));
        }
    }

    /**
     * One tick's cast of a stream or channel: where it runs, who holds it,
     * and which ability.
     *
     * @param level     the server level
     * @param player    the holding player
     * @param abilityId the ability's id, for a refusal's log
     * @param spray     where what the tick lays pays from and how much it throws
     */
    private record CastOn(ServerLevel level, ServerPlayer player, Identifier abilityId, HeldSpray spray) {

        /**
         * A glove's cast, paying from the goo the inventory carries.
         *
         * @param level     the server level
         * @param player    the holding player
         * @param abilityId the ability's id
         */
        CastOn(ServerLevel level, ServerPlayer player, Identifier abilityId) {
            this(level, player, abilityId, HeldSpray.GLOVE);
        }
    }

    /**
     * Where the crosshair lands within a stream's reach: the first block the
     * player's look meets, or a miss at the end of the reach along the look.
     *
     * @param player the streaming player
     * @param range  the stream's reach in blocks
     * @return the hit, its location the struck face's point or the reach's end
     */
    private static BlockHitResult crosshairHit(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
    }

    /**
     * The direction from the cone's apex to the crosshair's point, so a stream
     * opening from the hand still meets the crosshair rather than running
     * beside its line.
     *
     * @param apex     the cone's apex, the glove hand
     * @param target   where the crosshair lands
     * @param fallback the look, for a target on the apex itself
     * @return the unit axis
     */
    static Vec3 aimFrom(Vec3 apex, Vec3 target, Vec3 fallback) {
        Vec3 line = target.subtract(apex);
        return line.lengthSqr() < MIN_AIM_LENGTH_SQUARED ? fallback : line.normalize();
    }

    /**
     * Runs the stream's {@code on_blocks} steps on the floors its rays land on
     * (decision mycosis-spore-stream-buds-and-poisons).
     *
     * @param player  the streaming player
     * @param apex    the cone's apex
     * @param axis    the cone's axis
     * @param ability  the stream ability
     * @param delivery the stream's delivery as it reaches for the caster this tick
     */
    private static void sprayFloors(ServerPlayer player, Vec3 apex, Vec3 axis, AbilityDefinition ability,
                                    Delivery delivery) {
        if (ability.onBlocks().isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        SprayPrograms.runOnFloors(level, FloorReach.struckInCone(level, player, apex, axis, delivery.range(),
                delivery.coneDegrees(), FLOOR_RAYS_PER_TICK, level.getRandom()), apex, ability);
    }

    /**
     * Runs a stream's block pass once this tick on the player, aimed at the
     * end of the reach along the look, where the stream's program holds steps
     * needing the channel
     * (decisions bore-vortex-with-a-worldspace-shake, petrify-stone-encasement-and-calcify-map).
     *
     * @param cast     the tick's cast
     * @param reachEnd the point the pass aims at
     * @param delivery the stream's delivery this tick
     * @param ability  the stream ability
     * @param held     the hold's tick count, which a wave front grows by
     * @param reaching whether the pass reaches new blocks, false while a mob-first stream bites
     */
    private static void runBlockPass(CastOn cast, Vec3 reachEnd, Delivery delivery, AbilityDefinition ability,
                                     int held, boolean reaching) {
        List<Step> blockSteps = channelSteps(ability.behaviors(), true);
        if (blockSteps.isEmpty()) {
            return;
        }
        ServerPlayer player = cast.player();
        ChannelAim aim = new ChannelAim(reachEnd, null, delivery.coneDegrees(), held, reaching);
        runSteps(PlayerHost.channeling(player.level(), player, aim, placementPrice(player, ability, cast.spray().paysFrom())),
                HostKind.PLAYER, blockSteps, cast);
    }

    /**
     * What each thing a stream lays pays as it lands: for a stream draining
     * on placement, the cost over the placements one cost buys, charged
     * exactly with the remainder paying first; for any other, nothing.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param player   the streaming player
     * @param ability  the stream ability
     * @param paysFrom where the price is paid from
     * @return the placement price
     */
    static PlacementPrice placementPrice(ServerPlayer player, AbilityDefinition ability, PaysFrom paysFrom) {
        if (!ability.delivery().drainsOnPlacement()) {
            return PlacementPrice.FREE;
        }
        return placementsPerCost -> AbilityCharges.charge(player, paysFrom, ability.gooType(),
                ability.cost().exactIn(GooValues.of(player.level())).dividedBy(placementsPerCost));
    }

    /**
     * A stream program's top-level steps split by the pass they run in: those
     * needing the channel or working the cone's blocks run once a tick on the
     * player over the cone's blocks (decisions bore-vortex-with-a-worldspace-shake,
     * petrify-stone-encasement-and-calcify-map),
     * and the rest run on every entity in the cone, and on the caster of a
     * stream tagged self.
     *
     * @param behaviors the stream's top-level steps
     * @param channel   true for the block pass's steps, false for the entity pass's
     * @return the steps of that pass, in program order
     */
    static List<Step> channelSteps(List<Step> behaviors, boolean channel) {
        return ChannelHost.passSteps(behaviors, channel);
    }

    /**
     * Runs a pass's steps on its host, logging a program the host refuses.
     *
     * @param host  the pass's host
     * @param kind  the host's kind
     * @param steps the pass's steps
     * @param cast  the tick's cast
     */
    private static void runSteps(StepHost host, HostKind kind, List<Step> steps, CastOn cast) {
        try {
            ProgramBehavior.forHost(steps, kind).tick(host);
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, cast.abilityId(), e.getMessage());
        }
    }

    /**
     * The living entities the stream reaches, the streaming player aside:
     * those inside the cone sprayed from the glove, and those the crosshair
     * finds, inside the same cone cast from the eye.
     * rewind-fills-while-held: the glove's cone runs parallel to the look a hand's width aside, so a
     * narrow cone alone misses the mob under the crosshair
     *
     * @param level    the server level
     * @param player   the streaming player
     * @param apex     the cone's apex at the glove
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @return the entities the stream reaches
     */
    static List<LivingEntity> livingInCone(ServerLevel level, ServerPlayer player, Vec3 apex, Vec3 axis,
                                           Delivery delivery) {
        Vec3 eye = player.getEyePosition();
        double living = delivery.livingReach();
        AABB reach = new AABB(apex, apex).inflate(living).minmax(new AABB(eye, eye).inflate(living));
        return level.getEntitiesOfClass(LivingEntity.class, reach, entity -> entity != player && entity.isAlive()
                && (inCone(apex, axis, delivery, entity) || inCone(eye, axis, delivery, entity)));
    }

    /**
     * Whether a living entity stands inside the stream's cone cast from an
     * apex, out to how far the stream reaches the living, or crosses the
     * cone's axis within that reach.
     *
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @param living   the candidate
     * @return true when the stream reaches the entity from the apex
     */
    static boolean inCone(Vec3 apex, Vec3 axis, Delivery delivery, LivingEntity living) {
        // decay-gnats-degrade-each-block-once: a stream may seek the living past the blocks it works
        double reach = delivery.livingReach();
        return StreamCone.contains(apex, axis, reach, delivery.coneDegrees(), living.getBoundingBox().getCenter())
                || living.getBoundingBox().clip(apex, apex.add(axis.normalize().scale(reach))).isPresent();
    }

    /**
     * Runs the stream's block pass this tick and answers the living its
     * entity pass strikes: every living thing in the cone, beside the block
     * pass; or, for a mob-first stream, the nearest mob, while the block pass
     * reaches no new block but keeps working the ones already reached, and
     * the block pass alone when no mob stands in the cone.
     *
     * @param cast     the tick's cast
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param ability  the stream ability
     * @param delivery the stream's delivery
     * @param held     the hold's tick count, which a wave front grows by
     * @return the living the entity pass strikes this tick
     */
    private static List<LivingEntity> runPasses(CastOn cast, Vec3 apex, Vec3 axis, AbilityDefinition ability,
                                                Delivery delivery, int held) {
        List<LivingEntity> inCone = livingInCone(cast.level(), cast.player(), apex, axis, delivery);
        // decay-gnats-degrade-each-block-once: a mob in the cone takes the swarm, else the blocks do;
        // the operator's ruling: blocks already painted keep stepping while the swarm bites
        boolean mobFirst = ability.hasTag(AbilityTags.MOB_FIRST);
        List<LivingEntity> struck = mobFirst ? mostAlignedMob(inCone, cast.player().getEyePosition(), axis) : inCone;
        runBlockPass(cast, rangeEnd(cast.player(), axis, delivery), delivery, ability, held,
                !mobFirst || struck.isEmpty());
        return struck;
    }

    /**
     * The mob a mob-first stream strikes among the living it reaches: the
     * one lying nearest the cone's axis, the smallest angle off the look from
     * the eye, so the swarm goes where the player aims rather than for the
     * mob that happens to stand closest (decision decay-gnats-degrade-each-block-once).
     *
     * @param living the living entities in the cone
     * @param eye    the streaming player's eye
     * @param axis   the look
     * @return the most aligned mob alone, or none when no mob stands in the cone
     */
    static List<LivingEntity> mostAlignedMob(List<LivingEntity> living, Vec3 eye, Vec3 axis) {
        Vec3 look = axis.normalize();
        return living.stream().filter(Mob.class::isInstance)
                .max(Comparator.comparingDouble(entity -> look.dot(entity.getBoundingBox().getCenter().subtract(eye)
                        .normalize())))
                .map(List::of).orElse(List.of());
    }

    /**
     * Tells the caster's watchers to launch this tick's motes of the
     * delivery's particle from the glove. A stream throwing drips flies each
     * along one of its drips' arcs, so the drips land where the coat lands
     * (decisions goop-and-slick-coat-lands-where-each-drip-arc-strikes,
     * goop-and-slick-spray-follows-the-coat); any other flies each out along
     * its own heading inside the cone at the speed given, coming to rest as
     * far off as the crosshair lands (decision mycosis-spore-stream-buds-and-poisons).
     * The clients launch them, naming the caster, so each mote swings with its own
     * caster's glove (decision glove-stream-motes-swing-with-the-glove-at-launch).
     *
     * @param player   the spraying player
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @param speed    each mote's launch speed along its heading, blocks per tick
     */
    static void sprayParticles(ServerPlayer player, Vec3 apex, Vec3 axis, Delivery delivery, double speed) {
        delivery.particle().ifPresent(particle -> EntityVisuals.sendToWatchers(player, motesOf(player.getId(), apex,
                axis, particle, delivery, speed, exitSpeedOf(player, delivery))));
    }

    /**
     * Tells the caster's watchers to draw this tick's drips, the very drips
     * the server threw this tick, each on its own arc onto the cone, naming
     * whether a drawn drip ends on a living thing it meets.
     * goop-wobbles-a-mob-its-drip-strikes
     * residual-spray-cone-centers-on-the-look-from-the-blob
     * operator UAT 2026-11-17: every drawn drip lands; a drip you see splat lays its layer where it splats
     *
     * @param cast          the tick's cast, naming who sprays and how much a tick
     * @param apex          where the drips leave, the lifted blob
     * @param cone          the cone the drips land in
     * @param ability       the stream ability
     * @param strikesLiving whether the stream's drips end on the living they meet
     * @param held          the hold's tick count, which seeds the tick's drips' places in the cone
     */
    private static void sprayDrips(CastOn cast, Vec3 apex, ConvergingCone cone, AbilityDefinition ability,
                                   boolean strikesLiving, int held) {
        ServerPlayer player = cast.player();
        Delivery delivery = ability.delivery();
        int drips = dripsThisTick(player, delivery, DripLanding.in(channelSteps(ability.behaviors(), true)), held,
                cast.spray().dripRate());
        delivery.particle().ifPresent(particle -> EntityVisuals.sendToWatchers(player, sprayPayloadOf(player.getId(),
                apex, cone, particle, new DrawnDrips(exitSpeedOf(cast, delivery), strikesLiving, held,
                        drips, cast.spray().shapeOf(delivery)), cast.spray().mists(), ability.gooType())));
    }

    /**
     * What a tick's spray shows the watchers: a mister's mist over the cone in the goo's colours, or the drips any
     * other spray threw, each on its own arc.
     * mister-draws-mist-and-lays-one-layer-on-bare-spots
     *
     * @param casterId the caster's entity id
     * @param apex     where the drips leave, the lifted blob
     * @param cone     the cone the spray lands in
     * @param particle the goo's spray particle
     * @param drawn    how the tick's drawn drips fly, in the cone's shape
     * @param mists    whether the spray mists
     * @param gooType  the sprayed goo type
     * @return the payload
     */
    static CustomPacketPayload sprayPayloadOf(int casterId, Vec3 apex, ConvergingCone cone, Identifier particle,
                                              DrawnDrips drawn, boolean mists,
                                              ResourceKey<GooTypeDefinition> gooType) {
        return mists ? new SprayMistPayload(casterId, apex, cone.axis(), drawn.shape(), particle, gooType,
                MIST_REACH) : dripMotesOf(casterId, apex, cone, particle, drawn);
    }

    /**
     * How a tick's drawn drips fly.
     *
     * @param exitSpeed     a drawn drip's exit speed, blocks per tick
     * @param strikesLiving whether a drawn drip ends on the living it meets
     * @param held          the hold's tick count, which seeds the tick's drips' places in the cone
     * @param drips         the drips the server threw this tick
     * @param shape         the cone the drips are thrown in, the sprayer's nozzle's shape
     */
    record DrawnDrips(double exitSpeed, boolean strikesLiving, int held, int drips, ConeShape shape) {
    }

    /**
     * The payload that draws a tick's drips for a stream throwing them: each
     * drawn drip leaves the blob onto its spot of the cone at the exit
     * speed, its heading speed unused.
     * residual-spray-cone-centers-on-the-look-from-the-blob
     *
     * @param casterId the caster's entity id
     * @param apex     where the drips leave, the lifted blob
     * @param cone     the cone the drips land in
     * @param particle the drip's particle
     * @param drawn    how the tick's drawn drips fly, in the cone's shape
     * @return the payload
     */
    static StreamMotesPayload dripMotesOf(int casterId, Vec3 apex, ConvergingCone cone, Identifier particle,
                                          DrawnDrips drawn) {
        return new StreamMotesPayload(casterId, apex, cone.axis(), particle, drawn.shape(), 0,
                drawn.exitSpeed(), drawn.strikesLiving(), drawn.held(), drawn.drips(), cone.eye(), cone.lands());
    }

    /**
     * The payload that launches a tick's drawn motes off the arc about an axis.
     *
     * @param casterId the caster's entity id
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param particle the mote's particle
     * @param delivery the stream delivery
     * @param speed    each mote's launch speed along its heading, blocks per tick
     * @param exitSpeed a drawn drip's exit speed, blocks per tick
     * @return the payload
     */
    static StreamMotesPayload motesOf(int casterId, Vec3 apex, Vec3 axis, Identifier particle, Delivery delivery,
                                      double speed, double exitSpeed) {
        return new StreamMotesPayload(casterId, apex, axis, particle, ConeShape.round(delivery.coneDegrees()), speed,
                exitSpeed,
                false, 0, NO_DRIPS, apex, 0);
    }

    /**
     * The drips a stream throws this tick: its delivery's drips a tick, or,
     * for a landing whose drips come faster with speed, that many times the
     * caster's horizontal speed over their base walking speed, never fewer,
     * so it lays the same goo per block the caster covers.
     * slick-resolves-faster-by-speed-over-unadjusted-speed
     *
     * @param player   the streaming player
     * @param delivery the stream delivery
     * @param landing  what the stream's drips lay, empty where they lay nothing
     * @param held     the hold's tick count
     * @param flowRate the multiple of the delivery's drips the spray throws, one for the glove
     * @return the tick's drips
     */
    static int dripsThisTick(ServerPlayer player, Delivery delivery, Optional<DripLanding> landing, int held,
                             double flowRate) {
        // spray-item-sprays-more-or-less-per-tick: the sprayer's flow scales the tick's drips, the glove's is one
        if (landing.filter(DripLanding::throwsFasterBySpeed).isEmpty()) {
            return SlickLayPace.dripsThisTick(delivery.dripsPerTick(), flowRate, held);
        }
        double walk = SlickLayPace.unadjustedSpeed(player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
        double factor = SlickLayPace.factor(player.getKnownMovement().horizontalDistance(), walk);
        return SlickLayPace.dripsThisTick(delivery.dripsPerTick(), factor * flowRate, held);
    }

    /**
     * The speed a tick's drips leave at, the nozzle carrying them farther.
     * operator ruling 2026-11-18: Thin goes farther and Jet farther still
     *
     * @param cast     the tick's cast, naming the caster and the nozzle
     * @param delivery the stream delivery
     * @return the exit speed, blocks per tick
     */
    private static double exitSpeedOf(CastOn cast, Delivery delivery) {
        return exitSpeedOf(cast.player(), delivery) * cast.spray().reachScale();
    }

    /**
     * The speed a stream's drips leave a caster at: the delivery's throw
     * speed plus the horizontal speed the caster's client reports, since the
     * server leaves a player's delta movement at rest, so a running caster
     * never outruns the goo they lay; the drawn drips leave at the same.
     * goop-and-slick-drips-leave-at-one-exit-speed
     *
     * @param player   the streaming player
     * @param delivery the stream delivery
     * @return the exit speed, blocks per tick
     */
    static double exitSpeedOf(ServerPlayer player, Delivery delivery) {
        return delivery.exitSpeedAt(player.getKnownMovement().horizontalDistance());
    }

    /**
     * The launch speed that rests a mote a distance off: a mote slowing by a
     * tenth each tick coasts ten times its launch speed.
     *
     * @param reach how far the mote flies, in blocks
     * @return the launch speed, blocks per tick
     */
    public static double restingSpeed(double reach) {
        return reach * LAUNCH_SPEED_PER_BLOCK;
    }

    /**
     * The launch speed of a mote: it rests as far off as the
     * crosshair lands, or at the stream's range past it
     * (decision mycosis-spore-stream-buds-and-poisons).
     *
     * @param range             the stream's reach in blocks
     * @param crosshairDistance the distance from the glove to where the crosshair lands
     * @return the launch speed, blocks per tick
     */
    static double crosshairSpeed(double range, double crosshairDistance) {
        return restingSpeed(Math.min(range, crosshairDistance));
    }

    /**
     * One mote's launch velocity for its share of the cone: its heading
     * inside the cone at the payload's speed.
     *
     * @param motes the tick's motes
     * @param share the mote's place in the cone
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 moteLaunch(StreamMotesPayload motes, CoatRay.ConeShare share) {
        return moteLaunch(StreamCone.launchDirection(motes.axis(), motes.coneShape(), share.tilt(), share.turn()),
                motes.speed());
    }

    /**
     * A glove mote's launch velocity: its heading inside the cone at its
     * launch speed alone, however the player spraying it moves.
     * glove-stream-motes-carry-the-players-travel-velocity
     *
     * @param heading the mote's unit heading inside the cone
     * @param speed   the mote's launch speed, blocks per tick
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 moteLaunch(Vec3 heading, double speed) {
        return heading.scale(speed);
    }
}
