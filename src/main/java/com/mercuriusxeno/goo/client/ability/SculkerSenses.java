package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.EntitySnapshots;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Sculker senses, while its brew stands. Every sound heard within
 * reach ripples out from its source and lights the block faces open to air
 * its front crosses, which draw sculk teal and fade out, weaker the farther
 * away they stand; a louder sound ripples farther and lasts longer, a mob's
 * alike, and every living mob within hearing gives off a quiet ripple every
 * couple of seconds ({@link SculkerEchoes}). A mob that moves or sounds draws the sculk
 * outline for as long as its sound's reveal lasts while it stays within
 * sight, and leaves the echoing silhouette of the shared afterimage, dimmer
 * the farther it stands. The brew's end forgets it all.
 * decision sculker-sight-persists-where-echoes-have-touched
 * decision afterimage-is-one-shared-effect
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SculkerSenses {

    /** Sculk teal, the color every echo draws in, which the vision lets through bright. */
    public static final int SCULK_RGB = 0x1ED8D2;
    /** The squared distance a tick an entity must move to be heard, past a mob's idle sway. */
    static final double MOVED_SQR = 0.0004;
    /** How near a sound's source an entity must stand for the sound to be its own. */
    private static final double SOUND_OWNER_REACH = 1.5;
    /** Ticks between one silhouette an entity leaves and the next. */
    private static final int SILHOUETTE_GAP_TICKS = 10;
    /** Ticks each silhouette takes to grow and fade. */
    private static final int SILHOUETTE_LIFE_TICKS = 16;
    /** The opacity a remembered face draws at beside the player. */
    private static final int FACE_ALPHA = 80;
    /** A remembered face this faint is not drawn at all. */
    private static final float FAINTEST = 0.02f;
    private static final AABB UNIT_CUBE = new AABB(0, 0, 0, 1, 1, 1);

    private static final SculkerEchoes ECHOES = new SculkerEchoes();
    /** Each heard entity's id and the game time its outline fades out at. */
    private static final Map<Integer, Long> OUTLINED_UNTIL = new HashMap<>();
    /** Each heard entity's id and the game time it last left a silhouette. */
    private static final Map<Integer, Long> SILHOUETTE_AT = new HashMap<>();

    private SculkerSenses() {
    }

    /**
     * Whether an entity draws its outline for the local Sculker now.
     *
     * @param entity the entity drawn
     * @return true for a remembered entity within sight
     */
    public static boolean outlines(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && heardRecently(entity) && SculkerEchoes.strengthAt(entity.distanceTo(player)) > 0;
    }

    private static boolean heardRecently(Entity entity) {
        Long until = OUTLINED_UNTIL.get(entity.getId());
        return until != null && entity.level().getGameTime() < until;
    }

    /**
     * Whether an entity moved enough this tick to be heard.
     *
     * @param moved how far it moved since last tick
     * @return true past an idle sway
     */
    static boolean movedEnough(Vec3 moved) {
        return moved.lengthSqr() > MOVED_SQR;
    }

    /**
     * Grows the echoes, hears every entity in sight that moved this tick,
     * and forgets what has left the level, while the local player's Sculker
     * stands; forgets everything once it ends.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !SculkerSwim.dives(player)) {
            ECHOES.clear();
            OUTLINED_UNTIL.clear();
            SILHOUETTE_AT.clear();
            return;
        }
        long now = level.getGameTime();
        ECHOES.advance(now, pos -> level.getBlockState(pos).isAir());
        forgetFadedEntities(level, now);
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(SculkerEchoes.HEARING_REACH))) {
            hearMovingOrPulsing(mc, player, entity, now);
        }
    }

    /**
     * Hears a living mob on its ambient pulse, a quiet ripple from where it
     * stands, and any entity that moved this tick.
     *
     * @param mc     the client
     * @param player the local player
     * @param entity an entity within hearing
     * @param now    the game time
     */
    private static void hearMovingOrPulsing(Minecraft mc, LocalPlayer player, Entity entity, long now) {
        if (entity instanceof LivingEntity && SculkerEchoes.pulses(now, entity.getId(), entity.distanceTo(player))) {
            ECHOES.hear(entity.position(), now, SculkerEchoes.PULSE_LOUDNESS);
            hearEntity(mc, player, entity, now, SculkerEchoes.fadeTicksFor(SculkerEchoes.PULSE_LOUDNESS));
        } else if (movedEnough(entity.position().subtract(entity.xo, entity.yo, entity.zo))) {
            hearEntity(mc, player, entity, now, SculkerEchoes.fadeTicksFor(1f));
        }
    }

    /**
     * Forgets every heard entity that has left the level or whose outline has faded.
     *
     * @param level the client level
     * @param now   the game time
     */
    private static void forgetFadedEntities(ClientLevel level, long now) {
        OUTLINED_UNTIL.entrySet().removeIf(heard -> level.getEntity(heard.getKey()) == null || now >= heard.getValue());
        SILHOUETTE_AT.keySet().retainAll(OUTLINED_UNTIL.keySet());
    }

    /**
     * Hears a sound within sight at its loudness: a mob's sound lights the
     * mob and the area just near it, briefly, and any other ripples out of
     * its source and lights the faces its front crosses.
     *
     * @param event the sound about to play
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || event.getSound() == null || !SculkerSwim.dives(player)) {
            return;
        }
        SoundInstance sound = event.getSound();
        hearSound(mc, player, new Vec3(sound.getX(), sound.getY(), sound.getZ()), loudnessOf(mc, sound));
    }

    /**
     * A sound's loudness: its volume, 1 for an ordinary sound, read once the
     * sound has been resolved to the file it plays.
     *
     * @param mc    the client
     * @param sound the sound about to play
     * @return its loudness
     */
    private static float loudnessOf(Minecraft mc, SoundInstance sound) {
        if (sound.getSound() == null) {
            sound.resolve(mc.getSoundManager());
        }
        return sound.getSound() == null ? 1f : sound.getVolume();
    }

    private static void hearSound(Minecraft mc, LocalPlayer player, Vec3 at, float loudness) {
        if (mc.level == null || SculkerEchoes.strengthAt(at.distanceTo(player.position())) <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        List<Entity> mobs = mc.level.getEntities(player, new AABB(at, at).inflate(SOUND_OWNER_REACH),
                LivingEntity.class::isInstance);
        ECHOES.hear(at, now, loudness);
        int fade = SculkerEchoes.fadeTicksFor(loudness);
        for (Entity mob : mobs) {
            hearEntity(mc, player, mob, now, fade);
        }
    }

    /**
     * Outlines an entity heard for as long as its sound's reveal lasts and
     * leaves its echoing silhouette, no oftener than the silhouette gap,
     * dimmer the farther it stands.
     *
     * @param mc        the client
     * @param player    the local player
     * @param entity    the entity heard
     * @param now       the game time
     * @param fadeTicks how long the reveal of its sound lasts
     */
    private static void hearEntity(Minecraft mc, LocalPlayer player, Entity entity, long now, int fadeTicks) {
        OUTLINED_UNTIL.merge(entity.getId(), now + fadeTicks, Math::max);
        Long last = SILHOUETTE_AT.get(entity.getId());
        if (last != null && now - last < SILHOUETTE_GAP_TICKS) {
            return;
        }
        SILHOUETTE_AT.put(entity.getId(), now);
        EntityRenderState snapshot = EntitySnapshots.of(mc, entity.getId());
        if (snapshot != null) {
            float strength = SculkerEchoes.strengthAt(entity.distanceTo(player));
            Afterimages.CLIENT.add(snapshot, entity.position(), ARGB.scaleRGB(SCULK_RGB, strength), now,
                    SILHOUETTE_LIFE_TICKS);
        }
    }

    /**
     * Draws every lit face within sight, its opacity falling with its
     * distance from the player and as it fades.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || ECHOES.remembered().isEmpty()) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.SCRY_FACES_TYPE);
        Vec3 standing = player.position();
        long now = mc.level.getGameTime();
        ECHOES.remembered().forEach((face, lit) -> {
            float strength = SculkerEchoes.strength(face, lit, standing, now) * SculkerVision.presence();
            if (strength > FAINTEST) {
                AABB box = UNIT_CUBE.move(Vec3.atLowerCornerOf(face.pos()).subtract(camera));
                ScrySweep.emitBoxFace(pose, consumer, box, face.side(),
                        ARGB.color(Math.round(FACE_ALPHA * strength), SCULK_RGB));
            }
        });
        buffers.endBatch(GooRenderTypes.SCRY_FACES_TYPE);
    }
}
