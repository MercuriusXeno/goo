package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HealReport;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.StreamSound;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.FloorReach;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepHost;
import com.mercuriusxeno.goo.ability.spray.SprayPrograms;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

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
 * look (decision bore-vortex-with-a-worldspace-shake).
 */
public final class GooStreamHandler {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on its held pass's host: {}";
    /** Log: one held tick reached the server, so a hold that does nothing shows where it stops. */
    private static final String LOG_HELD_TICK = "Held tick of {} for {}: hold tick {}";
    /** Particles sprayed along the cone each tick. */
    private static final int PARTICLES_PER_TICK = 6;
    /**
     * Launch speed per block of the stream's range: a mote slowing by a tenth
     * each tick carries ten times its launch speed, so it reaches the cone's end.
     */
    private static final double LAUNCH_SPEED_PER_BLOCK = 0.1;
    /** Rays the spray casts each tick to find the floors it lands on. */
    private static final int FLOOR_RAYS_PER_TICK = 12;
    /** A particle sent with a count of zero flies along the vector it is handed. */
    private static final int ALONG_THE_VECTOR = 0;
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
        if (gooType == null || !GooThrowHandler.validateGlove(player)) {
            return;
        }
        AbilityDefinition ability = heldAbility(player, payload, gooType);
        int held = ability == null ? 0 : drainShare(player, gooType, ability);
        Goo.LOGGER.debug(LOG_HELD_TICK, payload.abilityId(), player.getName().getString(), held);
        if (held == 0) {
            return;
        }
        if (HeldRoute.channelsOnSelf(ability.delivery(), ability.badge())) {
            channelOnPlayer(player, new ChannelAim(payload.aimPoint(), payload.plane(), 0, held), ability);
        } else {
            strikeCone(player, payload.origin(), ability, held);
        }
        // mycosis-spore-stream-buds-and-poisons
        ability.delivery().sound().filter(sound -> sound.playsOn(held))
                .ifPresent(sound -> playStreamSound(player, sound));
    }

    /**
     * Plays one beat of the held ability's sound at the player, its pitch strayed a little.
     *
     * @param player the streaming player
     * @param sound  the stream's sound
     */
    private static void playStreamSound(ServerPlayer player, StreamSound sound) {
        float pitch = sound.pitchFor(player.getRandom().nextFloat());
        SoundPlays.play(player.level(), player.getEyePosition(),
                new SoundCue(sound.sound(), SoundKind.PLAYERS, sound.volume(), pitch));
    }

    /**
     * The ability the payload names, where the player may use it and it runs while held.
     *
     * @param player  the holding player
     * @param payload the held tick
     * @param gooType the ability's goo type
     * @return the ability, or null for one the player cannot use or one that does not run while held
     */
    private static @Nullable AbilityDefinition heldAbility(ServerPlayer player, GooStreamPayload payload,
                                                            ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        return ability != null && HeldRoute.runsWhileHeld(ability.delivery(), ability.badge()) ? ability : null;
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
        runSteps(PlayerHost.channeling(player.level(), player, aim), HostKind.PLAYER, ability.behaviors(), ability);
    }

    /**
     * Drains this tick's share of the ability's cost.
     *
     * @param player  the streaming player
     * @param gooType the ability's goo type
     * @param ability the stream ability
     * @return the hold's tick count, or 0 when the tick runs nothing: a second stream tick in one
     *         server tick, or a share the player cannot pay, which stops the stream
     */
    private static int drainShare(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                      AbilityDefinition ability) {
        MinecraftServer server = player.level().getServer();
        int held = GooServerState.of(server).streamHolds().advance(player.getUUID(), server.getTickCount());
        if (held == 0) {
            return 0;
        }
        int share = StreamHolds.shareAt(ability.cost(), ability.delivery().ticksPerCharge(), held);
        if (!GooSourceScanner.hasEnough(player, gooType, share)) {
            return 0;
        }
        GooSourceScanner.deplete(player, gooType, share);
        return held;
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
     */
    private static void strikeCone(ServerPlayer player, Vec3 origin, AbilityDefinition ability, int held) {
        ServerLevel level = player.level();
        Delivery delivery = ability.delivery();
        Vec3 apex = ThrowArc.clampToReach(player.getEyePosition(), origin, ThrowArc.HAND_REACH * player.getScale());
        Vec3 axis = player.getLookAngle();
        List<Step> entitySteps = channelSteps(ability.behaviors(), false);
        List<Integer> healed = new ArrayList<>();
        if (delivery.range() > 0) {
            // reserve-hearts-sit-behind-the-bar: a stream reaching nothing runs only on its caster
            sprayParticles(level, apex, axis, delivery);
            runBlockPass(player, axis, ability, held);
            for (LivingEntity living : livingInCone(level, player, apex, axis, delivery)) {
                HEALS.runNoting(living, healed, () -> runSteps(new EntityHost(level, living, player), HostKind.ENTITY,
                        entitySteps, ability));
            }
            sprayFloors(player, apex, axis, ability);
        }
        if (ability.hasTag(AbilityTags.SELF)) {
            // vitality-waves-regenerate-and-court
            HEALS.runNoting(player, healed,
                    () -> runSteps(new PlayerHost(level, player), HostKind.PLAYER, entitySteps, ability));
        }
        if (!healed.isEmpty()) {
            // vitality-waves-regenerate-and-court: the client homes goo to each healed thing and stars it
            EntityVisuals.sendToWatchers(player, new StreamHealedPayload(player.getId(), apex, healed));
        }
    }

    /**
     * Runs the stream's {@code on_blocks} steps on the floors its rays land on
     * (decision mycosis-spore-stream-buds-and-poisons).
     *
     * @param player  the streaming player
     * @param apex    the cone's apex
     * @param axis    the cone's axis
     * @param ability the stream ability
     */
    private static void sprayFloors(ServerPlayer player, Vec3 apex, Vec3 axis, AbilityDefinition ability) {
        if (ability.onBlocks().isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        Delivery delivery = ability.delivery();
        SprayPrograms.runOnFloors(level, FloorReach.struckInCone(level, player, apex, axis, delivery.range(),
                delivery.coneDegrees(), FLOOR_RAYS_PER_TICK, level.getRandom()), apex, ability);
    }

    /**
     * Runs a stream's block pass once this tick on the player, aimed at the
     * end of the reach along the look, where the stream's program holds steps
     * needing the channel
     * (decisions bore-vortex-with-a-worldspace-shake, petrify-stone-encasement-and-calcify-map).
     *
     * @param player  the streaming player
     * @param axis    the look
     * @param ability the stream ability
     * @param held    the hold's tick count, which a wave front grows by
     */
    private static void runBlockPass(ServerPlayer player, Vec3 axis, AbilityDefinition ability, int held) {
        List<Step> blockSteps = channelSteps(ability.behaviors(), true);
        if (blockSteps.isEmpty()) {
            return;
        }
        Delivery delivery = ability.delivery();
        ChannelAim aim = new ChannelAim(player.getEyePosition().add(axis.scale(delivery.range())), null,
                delivery.coneDegrees(), held);
        runSteps(PlayerHost.channeling(player.level(), player, aim), HostKind.PLAYER, blockSteps, ability);
    }

    /**
     * A stream program's top-level steps split by the pass they run in: those
     * needing the channel run once a tick on the player over the cone's blocks
     * (decisions bore-vortex-with-a-worldspace-shake,
     * petrify-stone-encasement-and-calcify-map), and the rest run on every
     * entity in the cone, and on the caster of a stream tagged self.
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
     * @param host    the pass's host
     * @param kind    the host's kind
     * @param steps   the pass's steps
     * @param ability the stream ability
     */
    private static void runSteps(StepHost host, HostKind kind, List<Step> steps, AbilityDefinition ability) {
        try {
            ProgramBehavior.forHost(steps, kind).tick(host);
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
    }

    /**
     * The living entities inside the cone, the streaming player aside.
     *
     * @param level    the server level
     * @param player   the streaming player
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @return the entities the stream reaches
     */
    private static List<LivingEntity> livingInCone(ServerLevel level, ServerPlayer player, Vec3 apex, Vec3 axis,
                                                   Delivery delivery) {
        AABB reach = new AABB(apex, apex).inflate(delivery.range());
        return level.getEntitiesOfClass(LivingEntity.class, reach, living -> living != player && living.isAlive()
                && StreamCone.contains(apex, axis, delivery.range(), delivery.coneDegrees(),
                        living.getBoundingBox().getCenter()));
    }

    /**
     * Launches the delivery's particle from the glove, each mote flying out
     * along its own heading inside the cone (decision mycosis-spore-stream-buds-and-poisons).
     *
     * @param level    the server level
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     */
    private static void sprayParticles(ServerLevel level, Vec3 apex, Vec3 axis, Delivery delivery) {
        RandomSource random = level.getRandom();
        double speed = delivery.range() * LAUNCH_SPEED_PER_BLOCK;
        delivery.particle().flatMap(SimpleParticles::resolve).ifPresent(particle -> {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                Vec3 heading = StreamCone.launchDirection(axis, delivery.coneDegrees(), random.nextDouble(),
                        random.nextDouble());
                level.sendParticles(particle, apex.x, apex.y, apex.z, ALONG_THE_VECTOR, heading.x, heading.y,
                        heading.z, speed);
            }
        });
    }
}
