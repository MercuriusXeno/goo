package com.mercuriusxeno.goo.client.ability;

import com.google.common.reflect.TypeToken;
import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * The share each mob an unmake is working has melted on this client. A
 * melting mob squashes like wax as it melts, lower and wider, and is coated
 * in the goo it melts into, its types mingled, a fresh coat laid every second
 * it is held so the goo stays on it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltingMobs {

    /** The store the client's unmake handler and the renderers share. */
    public static final MeltingMobs CLIENT = new MeltingMobs();

    /** Render data carrying how far a mob has melted, absent while it stands whole. */
    public static final ContextKey<Float> MELTED =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "melted"));

    /** The share of its height a fully melted mob loses. */
    static final float SQUASH = 0.55f;
    /** How far a fully melted mob widens, as a share of its width. */
    static final float SPREAD = 0.3f;
    /** Ticks between fresh coats on a held mob. */
    static final long COAT_EVERY = 20;
    /** How far to either side of the body's middle each type's coat strikes, as a share of its width. */
    private static final double COAT_SIDE = 0.25;
    /** How far up the body each further type's coat strikes, as a share of its height. */
    private static final double COAT_RISE = 0.2;

    private record Heard(float melted, long tick, long coatedAt) {
    }

    private final Map<Integer, Heard> melting = new HashMap<>();

    /**
     * Records the share of a mob melted, coating it in its goo when the melt
     * begins and every second after.
     *
     * @param entityId the mob's entity id
     * @param fraction the share melted, from 0 whole to 1 gone
     * @param goo      the goo it melts into
     * @param now      the game time the share arrived
     */
    public void record(int entityId, float fraction, MingledGoo goo, long now) {
        Heard last = melting.get(entityId);
        boolean fresh = last == null || now - last.tick() > MeltingBlocks.STALE_TICKS;
        long coatedAt = fresh || now - last.coatedAt() >= COAT_EVERY ? now : last.coatedAt();
        if (coatedAt == now) {
            coat(entityId, goo, now);
        }
        melting.put(entityId, new Heard(fraction, now, coatedAt));
    }

    /**
     * How far a mob has melted now, while the unmake still works it.
     *
     * @param entityId the mob's entity id
     * @param now      the game time
     * @return the share, or 0 for a mob not being melted
     */
    public float meltedOf(int entityId, float now) {
        Heard heard = melting.get(entityId);
        return heard == null || now - heard.tick() > MeltingBlocks.STALE_TICKS ? 0f : heard.melted();
    }

    /**
     * Lays one coat of each of the goo's types over the mob, each struck at
     * its own spot on the body.
     *
     * @param entityId the mob's entity id
     * @param goo      the goo
     * @param now      the game time
     */
    private static void coat(int entityId, MingledGoo goo, long now) {
        Minecraft mc = Minecraft.getInstance();
        Entity mob = mc.level == null ? null : mc.level.getEntity(entityId);
        if (mob == null || mc.player == null) {
            return;
        }
        AABB body = mob.getBoundingBox();
        Vec3 aim = body.getCenter().subtract(mc.player.getEyePosition()).normalize();
        int index = 0;
        for (ResourceKey<GooTypeDefinition> type : goo.types()) {
            double side = (index & 1) == 0 ? COAT_SIDE : -COAT_SIDE;
            Vec3 hit = body.getCenter().add(side * body.getXsize(), body.getYsize() * COAT_RISE * index, 0);
            index++;
            MobCoats.CLIENT.coat(entityId, type, now, new MobCoats.Strike(hit, aim,
                    new MobCoats.Stance(mob.position(), mob.getYRot())));
        }
    }

    /**
     * Registers the stamp carrying a melting mob's share onto its render state.
     *
     * @param event the render state modifier registration event
     */
    @SubscribeEvent
    public static void registerStamp(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
        }, MeltingMobs::stampMelted);
    }

    /**
     * Stamps how far a melting mob has melted onto its render state.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampMelted(Entity entity, EntityRenderState state) {
        float now = entity.level().getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float melted = CLIENT.meltedOf(entity.getId(), now);
        if (melted > 0f) {
            state.setRenderData(MELTED, melted);
        }
    }

    /**
     * Squashes a melting mob as it draws, lower and wider about its feet.
     *
     * @param event the living render event, before the mob draws
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        Float melted = event.getRenderState().getRenderData(MELTED);
        if (melted != null) {
            event.getPoseStack().pushPose();
            float wide = 1f + SPREAD * melted;
            event.getPoseStack().scale(wide, 1f - SQUASH * melted, wide);
        }
    }

    /**
     * Undoes the squash once the mob has drawn.
     *
     * @param event the living render event, after the mob draws
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (event.getRenderState().getRenderData(MELTED) != null) {
            event.getPoseStack().popPose();
        }
    }
}
