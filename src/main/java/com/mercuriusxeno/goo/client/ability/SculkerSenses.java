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
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * What a Sculker senses, while its brew stands. Every sound heard within
 * reach ripples out from its source and lights the block faces open to air
 * its front crosses, which stay remembered and draw sculk teal, weaker the
 * farther away they stand ({@link SculkerEchoes}). An entity that moves or
 * sounds is remembered too: it draws the sculk outline while it stays within
 * sight, and leaves the echoing silhouette of the shared afterimage as it
 * moves or sounds, dimmer the farther it stands. The brew's end forgets it all.
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
    private static final int FACE_ALPHA = 150;
    /** A remembered face this faint is not drawn at all. */
    private static final float FAINTEST = 0.02f;
    private static final AABB UNIT_CUBE = new AABB(0, 0, 0, 1, 1, 1);

    private static final SculkerEchoes ECHOES = new SculkerEchoes();
    /** Each remembered entity's id and the game time it last left a silhouette. */
    private static final Map<Integer, Long> REMEMBERED_ENTITIES = new HashMap<>();

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
        return player != null && REMEMBERED_ENTITIES.containsKey(entity.getId())
                && SculkerEchoes.strengthAt(entity.distanceTo(player)) > 0;
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
            REMEMBERED_ENTITIES.clear();
            return;
        }
        long now = level.getGameTime();
        ECHOES.advance(now, pos -> level.getBlockState(pos).isAir());
        REMEMBERED_ENTITIES.keySet().removeIf(id -> level.getEntity(id) == null);
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(SculkerEchoes.SIGHT_REACH))) {
            if (movedEnough(entity.position().subtract(entity.xo, entity.yo, entity.zo))) {
                hearEntity(mc, player, entity, now);
            }
        }
    }

    /**
     * Hears a sound within sight: its echo ripples out of its source, and
     * the entities making it are remembered.
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
        hearSound(mc, player, new Vec3(event.getSound().getX(), event.getSound().getY(), event.getSound().getZ()));
    }

    private static void hearSound(Minecraft mc, LocalPlayer player, Vec3 at) {
        if (mc.level == null || SculkerEchoes.strengthAt(at.distanceTo(player.position())) <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        ECHOES.hear(at, now);
        for (Entity owner : mc.level.getEntities(player, new AABB(at, at).inflate(SOUND_OWNER_REACH))) {
            hearEntity(mc, player, owner, now);
        }
    }

    /**
     * Remembers an entity heard and leaves its echoing silhouette, no
     * oftener than the silhouette gap, dimmer the farther it stands.
     *
     * @param mc     the client
     * @param player the local player
     * @param entity the entity heard
     * @param now    the game time
     */
    private static void hearEntity(Minecraft mc, LocalPlayer player, Entity entity, long now) {
        Long last = REMEMBERED_ENTITIES.get(entity.getId());
        if (last != null && now - last < SILHOUETTE_GAP_TICKS) {
            return;
        }
        REMEMBERED_ENTITIES.put(entity.getId(), now);
        EntityRenderState snapshot = EntitySnapshots.of(mc, entity.getId());
        if (snapshot != null) {
            float strength = SculkerEchoes.strengthAt(entity.distanceTo(player));
            Afterimages.CLIENT.add(snapshot, entity.position(), ARGB.scaleRGB(SCULK_RGB, strength), now,
                    SILHOUETTE_LIFE_TICKS);
        }
    }

    /**
     * Draws every remembered face within sight, its opacity falling with its
     * distance from the player.
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
        ECHOES.remembered().keySet().forEach(face -> {
            float strength = SculkerEchoes.strength(face, standing);
            if (strength > FAINTEST) {
                AABB box = UNIT_CUBE.move(Vec3.atLowerCornerOf(face.pos()).subtract(camera));
                ScrySweep.emitBoxFace(pose, consumer, box, face.side(),
                        ARGB.color(Math.round(FACE_ALPHA * strength), SCULK_RGB));
            }
        });
        buffers.endBatch(GooRenderTypes.SCRY_FACES_TYPE);
    }
}
