package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.network.LeechPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * The tether Drain stands from each mob it drains to the caster: a steady
 * dark purple ribbon from the mob's body center to the caster's chest, drawn
 * every frame while the mob's last trickle payload is younger than its lapse.
 * Lifetap's splashes stand none.
 * drained-mobs-show-stream-burst-and-tether
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrainTethers {

    /** The tethers the client draws. */
    static final DrainTethers CLIENT = new DrainTethers();

    /**
     * Ticks the server's tick order and the network may add between one
     * strike's payload and the next, past the strike interval itself.
     */
    private static final int STRIKE_SLACK_TICKS = 5;
    /**
     * Ticks a tether stands past its last payload: one strike interval, a held
     * field's ten ticks, with the slack before the next strike's payload lands,
     * so a held drain never flickers its tether.
     */
    static final int TETHER_LAPSE_TICKS = LeechWisps.TRICKLE_SPREAD + STRIKE_SLACK_TICKS;
    private static final int TETHER_ALPHA = 220;
    /** The tether's color: the hex ailment's dark purple, mostly opaque. */
    private static final int TETHER_COLOR = ARGB.color(TETHER_ALPHA, AilmentKind.HEX.rgb());
    private static final float WIDTH_SCALE = 4f;
    private static final double BODY_CENTER = 0.5;
    private static final double CHEST = 0.7;

    private final Map<Integer, Tether> byVictim = new HashMap<>();

    /**
     * A standing tether.
     *
     * @param casterId the id of the caster the victim is tethered to
     * @param lapsesAt the game tick the tether is gone at
     */
    record Tether(int casterId, long lapsesAt) {
    }

    /**
     * Stands or renews the tether a Drain trickle payload names; a Lifetap
     * splash stands none.
     *
     * @param payload the leech payload
     * @param now     the client's game tick
     */
    void record(LeechPayload payload, long now) {
        if (!payload.splash()) {
            byVictim.put(payload.victimId(), new Tether(payload.healedId(), now + TETHER_LAPSE_TICKS));
        }
    }

    /**
     * Whether a victim's tether stands at a tick.
     *
     * @param victimId the victim's id
     * @param now      the client's game tick
     * @return true while the tether stands
     */
    boolean holds(int victimId, long now) {
        Tether tether = byVictim.get(victimId);
        return tether != null && stands(tether, now);
    }

    /**
     * Drops each lapsed tether, and each whose lapse lies further ahead than
     * one lapse, which a level change setting the clock back leaves.
     *
     * @param now the client's game tick
     */
    void dropLapsed(long now) {
        byVictim.values().removeIf(tether -> !stands(tether, now));
    }

    private static boolean stands(Tether tether, long now) {
        return now < tether.lapsesAt() && tether.lapsesAt() - now <= TETHER_LAPSE_TICKS;
    }

    /**
     * Draws each standing tether after the translucent blocks.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        CLIENT.dropLapsed(level.getGameTime());
        if (CLIENT.byVictim.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(type));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        float width = mc.getWindow().getAppropriateLineWidth() * WIDTH_SCALE;
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        CLIENT.byVictim.forEach((victimId, tether) -> {
            if (level.getEntity(victimId) instanceof Entity victim
                    && level.getEntity(tether.casterId()) instanceof Entity caster) {
                lines.emitPolyline(camera, new Vec3[] {heightOf(victim, partialTick, BODY_CENTER),
                        heightOf(caster, partialTick, CHEST)}, TETHER_COLOR, width);
            }
        });
        buffers.endBatch(type);
    }

    private static Vec3 heightOf(Entity entity, float partialTick, double share) {
        return entity.getPosition(partialTick).add(0, entity.getBbHeight() * share, 0);
    }
}
