package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Petrify's fog: while right click holds Petrify, puffs of dust pour from the
 * glove each tick and billow out over the cone to its reach, each churning as
 * it rolls, a medusa's gaze miasma the player emits. Each puff keeps the
 * glove and aim it left with, so the fog trails the hand's motion instead of
 * sticking to it, and the poured dust plays out after the hold ends. Each
 * puff draws as a quad facing the camera, filled by {@code petrify_fog.fsh};
 * the cone itself, the area of effect, is never outlined.
 * decision petrify-fog-is-billowing-roiling-dust
 * decision petrify-stone-encasement-and-calcify-map
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PetrifyFog {

    /** Puffs poured a tick, their births spread across it so the stream runs smooth. */
    private static final int PUFFS_PER_TICK = 6;
    private static final double HALF = 0.5;
    private static final float SIGNED_TO_UNIT = 0.5f;
    /** A quad corner's two sides of its center, along each of its directions. */
    private static final int OUT = 1;
    private static final int BACK = -OUT;

    /** The poured puffs, and the reach and half angle they pour by. */
    private static final List<PetrifyPuffs.Puff> PUFFS = new ArrayList<>();
    private static double range;
    private static double halfAngle;

    private PetrifyFog() {
    }

    /**
     * Pours a tick's puffs while Petrify is held, and lets every puff past its
     * life go.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        double now = level.getGameTime();
        PUFFS.removeIf(puff -> PetrifyPuffs.ageShare(puff, now) >= 1);
        LocalPlayer player = mc.player;
        ClientAbility petrify = player == null ? null : heldPetrify(player);
        if (petrify == null) {
            return;
        }
        range = petrify.delivery().range();
        halfAngle = Math.toRadians(petrify.delivery().coneDegrees() * HALF);
        Vec3 glove = GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        RandomSource random = level.getRandom();
        for (int puff = 0; puff < PUFFS_PER_TICK; puff++) {
            PUFFS.add(PetrifyPuffs.pour(glove, player.getViewVector(1f), halfAngle,
                    now + (double) puff / PUFFS_PER_TICK, random.nextDouble(), random.nextDouble(),
                    random.nextFloat()));
        }
    }

    /**
     * Draws the poured puffs after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || PUFFS.isEmpty()) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Camera camera = mc.gameRenderer.getMainCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.PETRIFY_FOG_TYPE));
        Vec3 left = toVec3(camera.leftVector());
        Vec3 up = toVec3(camera.upVector());
        Vec3 view = toVec3(camera.forwardVector());
        for (PetrifyPuffs.Puff puff : PUFFS) {
            Vec3 center = PetrifyPuffs.positionAt(puff, now, range, halfAngle).subtract(camera.position());
            double radius = PetrifyPuffs.radiusAt(PetrifyPuffs.distanceAt(PetrifyPuffs.ageShare(puff, now), range),
                    halfAngle);
            emitPuff(quads, new Billboard(center, left.scale(radius), up.scale(radius), view),
                    (float) PetrifyPuffs.ageShare(puff, now), puff.seed());
        }
        buffers.endBatch(GooRenderTypes.PETRIFY_FOG_TYPE);
    }

    /**
     * A puff's quad, facing the camera.
     *
     * @param center the puff's center, camera relative
     * @param left   the quad's half width, toward the camera's left
     * @param up     the quad's half height, toward the camera's up
     * @param view   the camera's forward vector, the quad's normal
     */
    private record Billboard(Vec3 center, Vec3 left, Vec3 up, Vec3 view) {
    }

    /**
     * Emits a puff's quad. Each vertex's color carries the puff's share of
     * its life in red, the corner in green and blue and the puff's seed in
     * alpha, since a core pipeline takes no per-draw uniforms.
     *
     * @param quads     the quad emitter
     * @param billboard the puff's quad
     * @param ageShare  the puff's share of its life
     * @param seed      the puff's seed
     */
    private static void emitPuff(FlatQuadContext quads, Billboard billboard, float ageShare, float seed) {
        corner(quads, billboard, BACK, BACK, ageShare, seed);
        corner(quads, billboard, OUT, BACK, ageShare, seed);
        corner(quads, billboard, OUT, OUT, ageShare, seed);
        corner(quads, billboard, BACK, OUT, ageShare, seed);
    }

    private static void corner(FlatQuadContext quads, Billboard billboard, int across, int upward, float ageShare,
                               float seed) {
        Vec3 at = billboard.center().add(billboard.left().scale(across)).add(billboard.up().scale(upward));
        int color = ARGB.color(ColorBytes.toByte(seed), ColorBytes.toByte(ageShare),
                ColorBytes.toByte((across + 1f) * SIGNED_TO_UNIT),
                ColorBytes.toByte((upward + 1f) * SIGNED_TO_UNIT));
        Vec3 view = billboard.view();
        quads.vertex((float) at.x, (float) at.y, (float) at.z, color, (float) view.x, (float) view.y, (float) view.z);
    }

    private static Vec3 toVec3(Vector3fc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    /**
     * The Petrify the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Petrify is held
     */
    private static @Nullable ClientAbility heldPetrify(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean petrifies = ability != null && ability.behaviors().stream().anyMatch(PetrifyStep.class::isInstance);
        return petrifies && GloveUseTracker.runsHeld(player) ? ability : null;
    }
}
