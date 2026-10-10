package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.RadiantAuraPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Radiant's held aura as every client tracking the caster sees it: a soft
 * glow-yellow shell of light around the caster, breathing slowly, and a
 * quiet looping shimmer, both while the caster holds Radiant and a moment
 * after (operator ruling 2026-10-09).
 * decision radiant-wisps-where-light-is-low
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RadiantAura {

    /** Ticks the aura keeps showing after the last hold tick arrived. */
    static final int LINGER_TICKS = 2;
    static final float AURA_RADIUS = 1.2f;
    private static final int AURA_ALPHA = 26;
    private static final int AURA_RGB = 0xFFE628;
    private static final float BREATH_PER_TICK = 0.15f;
    private static final float BREATH_DEPTH = 0.08f;
    private static final double BODY_CENTER = 0.9;

    private static final Map<Integer, Long> HELD_AT = new HashMap<>();

    private RadiantAura() {
    }

    /**
     * Handles a hold tick on the client thread, starting the shimmer as a hold begins.
     *
     * @param payload the aura payload
     * @param context the network context
     */
    public static void onPayload(RadiantAuraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            boolean starting = !isShowing(payload.casterId());
            HELD_AT.put(payload.casterId(), mc.level.getGameTime());
            Entity caster = mc.level.getEntity(payload.casterId());
            if (starting && caster != null) {
                mc.getSoundManager().play(new Shimmer(caster));
            }
        });
    }

    /**
     * Whether a caster's aura still shows.
     *
     * @param casterId the caster's entity id
     * @return true within the linger of the last hold tick
     */
    static boolean isShowing(int casterId) {
        Minecraft mc = Minecraft.getInstance();
        Long at = HELD_AT.get(casterId);
        return at != null && mc.level != null && mc.level.getGameTime() - at <= LINGER_TICKS;
    }

    /**
     * Draws every aura still showing once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || HELD_AT.isEmpty()) {
            HELD_AT.clear();
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float time = mc.level.getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        HELD_AT.keySet().removeIf(id -> !isShowing(id) || mc.level.getEntity(id) == null);
        for (int id : HELD_AT.keySet()) {
            Entity caster = mc.level.getEntity(id);
            Vec3 center = caster.getPosition(partialTick).add(0, BODY_CENTER, 0).subtract(camera);
            float radius = AURA_RADIUS * (1f + BREATH_DEPTH * Mth.sin(time * BREATH_PER_TICK));
            ColorSphere.emit(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.GLOW_SHELL_TYPE),
                    center, radius, ARGB.color(AURA_ALPHA, AURA_RGB));
        }
        buffers.endBatch(GooRenderTypes.GLOW_SHELL_TYPE);
    }

    /** The held shimmer: a quiet looping amethyst resonance following the caster. */
    private static final class Shimmer extends AbstractTickableSoundInstance {

        private static final float VOLUME = 0.35f;
        private static final float PITCH = 1.5f;

        private final Entity caster;

        Shimmer(Entity caster) {
            super(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.caster = caster;
            this.looping = true;
            this.delay = 0;
            this.volume = VOLUME;
            this.pitch = PITCH;
            follow();
        }

        @Override
        public void tick() {
            if (caster.isRemoved() || !isShowing(caster.getId())) {
                stop();
                return;
            }
            follow();
        }

        private void follow() {
            x = caster.getX();
            y = caster.getY() + BODY_CENTER;
            z = caster.getZ();
        }
    }
}
