package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.deep.DiveSwim;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
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
import java.util.List;
import java.util.Map;

/**
 * What a diver senses: within reach of the diver, an entity that moves or
 * makes a sound, and a block a sound plays at with no entity there, each for
 * a moment after. A sensed entity draws the vanilla outline, a sensed block a
 * glowing box, both white, which the dive's vision burns teal.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DiveSenses {

    /** How far from the diver, in blocks, a movement or a sound is sensed. */
    static final double SENSE_REACH = 32;
    /** Ticks a sensed thing stays outlined after it last moved or sounded. */
    static final int SENSED_TICKS = 20;
    /** The squared distance a tick an entity must move to be sensed, past a mob's idle sway. */
    static final double MOVED_SQR = 0.0004;
    /** How near a sound's source an entity must stand for the sound to be its own. */
    private static final double SOUND_OWNER_REACH = 1.5;
    private static final int OUTLINE_ARGB = 0xFFFFFFFF;
    private static final float WIDTH_SCALE = 1.5f;
    private static final double BOX_INSET = 0.02;

    /** Each sensed entity's id and the tick its outline ends at. */
    private static final Map<Integer, Long> SENSED_ENTITIES = new HashMap<>();
    /** Each sensed block and the tick its box ends at. */
    private static final Map<BlockPos, Long> SENSED_BLOCKS = new HashMap<>();

    private DiveSenses() {
    }

    /**
     * Whether an entity draws its outline for the local diver now.
     *
     * @param entity the entity drawn
     * @return true while the diver senses it
     */
    public static boolean outlines(Entity entity) {
        Long until = SENSED_ENTITIES.get(entity.getId());
        return until != null && until > entity.level().getGameTime();
    }

    /**
     * Whether an entity moved enough this tick to be sensed.
     *
     * @param moved how far it moved since last tick
     * @return true past an idle sway
     */
    static boolean movedEnough(Vec3 moved) {
        return moved.lengthSqr() > MOVED_SQR;
    }

    /**
     * Senses every entity in reach that moved this tick, and drops what has
     * gone quiet, while the local player's dive stands.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || !DiveSwim.dives(player)) {
            SENSED_ENTITIES.clear();
            SENSED_BLOCKS.clear();
            return;
        }
        long now = level.getGameTime();
        SENSED_ENTITIES.values().removeIf(until -> until <= now);
        SENSED_BLOCKS.values().removeIf(until -> until <= now);
        for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(SENSE_REACH))) {
            if (movedEnough(entity.position().subtract(entity.xo, entity.yo, entity.zo))) {
                SENSED_ENTITIES.put(entity.getId(), now + SENSED_TICKS);
            }
        }
    }

    /**
     * Senses where a sound plays in reach: the entities making it, or the
     * block it plays at where none stands there.
     *
     * @param event the sound about to play
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || event.getSound() == null || !DiveSwim.dives(player)) {
            return;
        }
        senseSound(mc, player, new Vec3(event.getSound().getX(), event.getSound().getY(), event.getSound().getZ()));
    }

    private static void senseSound(Minecraft mc, LocalPlayer player, Vec3 at) {
        if (mc.level == null || at.distanceTo(player.position()) > SENSE_REACH) {
            return;
        }
        long until = mc.level.getGameTime() + SENSED_TICKS;
        List<Entity> owners = mc.level.getEntities(player, new AABB(at, at).inflate(SOUND_OWNER_REACH));
        if (owners.isEmpty()) {
            SENSED_BLOCKS.put(BlockPos.containing(at), until);
        }
        owners.forEach(owner -> SENSED_ENTITIES.put(owner.getId(), until));
    }

    /**
     * Draws a box around each sensed block while its moment lasts, at full
     * white so the vision reads it as sensed.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (SENSED_BLOCKS.isEmpty() || mc.level == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        SENSED_BLOCKS.keySet().forEach(pos -> lines.emitWireframe(boxAround(pos, camera), OUTLINE_ARGB, width));
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }

    private static CuboidBounds boxAround(BlockPos pos, Vec3 camera) {
        return new CuboidBounds((float) (pos.getX() + BOX_INSET - camera.x), (float) (pos.getX() + 1 - BOX_INSET - camera.x),
                (float) (pos.getZ() + BOX_INSET - camera.z), (float) (pos.getZ() + 1 - BOX_INSET - camera.z),
                (float) (pos.getY() + BOX_INSET - camera.y), (float) (pos.getY() + 1 - BOX_INSET - camera.y));
    }
}
