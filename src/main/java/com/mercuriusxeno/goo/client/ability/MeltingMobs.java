package com.mercuriusxeno.goo.client.ability;

import com.google.common.reflect.TypeToken;
import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * The share each mob an unmake is working has melted on this client, as
 * {@link MeltingBlocks} holds blocks. A melting mob's render state is stamped
 * so its own body is not drawn while its goo copy sags in its place.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MeltingMobs {

    /** The store the client's unmake handler and the melt renderers share. */
    public static final MeltingMobs CLIENT = new MeltingMobs();

    /** Render data marking a mob whose goo copy stands in for its body. */
    public static final ContextKey<Boolean> MELTING =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "melting"));

    private record Heard(float melted, long tick) {
    }

    private final Map<Integer, Heard> melting = new HashMap<>();

    /**
     * Records the share of a mob melted.
     *
     * @param entityId the mob's entity id
     * @param fraction the share melted, from 0 whole to 1 gone
     * @param now      the game time the share arrived
     */
    public void record(int entityId, float fraction, long now) {
        melting.put(entityId, new Heard(fraction, now));
    }

    /**
     * Every mob still being worked and its share, forgetting each gone quiet.
     *
     * @param now the game time including the partial tick
     * @return each worked mob's id and share
     */
    public Map<Integer, Float> worked(float now) {
        melting.values().removeIf(heard -> now - heard.tick() > MeltingBlocks.STALE_TICKS);
        Map<Integer, Float> shares = new HashMap<>();
        melting.forEach((id, heard) -> shares.put(id, heard.melted()));
        return shares;
    }

    /**
     * Whether a mob is being melted now.
     *
     * @param entityId the mob's entity id
     * @param now      the game time
     * @return true while the unmake works it
     */
    public boolean isMelting(int entityId, float now) {
        Heard heard = melting.get(entityId);
        return heard != null && now - heard.tick() <= MeltingBlocks.STALE_TICKS;
    }

    /**
     * Registers the stamp marking a melting mob's render state.
     *
     * @param event the render state modifier registration event
     */
    @SubscribeEvent
    public static void registerStamp(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<EntityRenderer<Entity, EntityRenderState>>() {
        }, MeltingMobs::stampMelting);
    }

    /**
     * Stamps a melting mob's render state so its body is not drawn.
     *
     * @param entity the entity
     * @param state  its render state
     */
    public static void stampMelting(Entity entity, EntityRenderState state) {
        float now = entity.level().getGameTime()
                + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (CLIENT.isMelting(entity.getId(), now)) {
            state.setRenderData(MELTING, true);
        }
    }
}
