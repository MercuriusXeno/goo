package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The client-only entities a blob morphs into while the server holds the
 * real one back: each stands under the id the real entity holds, so the
 * real entity's spawn replaces it in the level once the morph ends. A stand-in
 * no spawn has replaced a while after its morph is discarded.
 * slime-blob-morphs-into-the-slime-it-becomes
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class MorphStandIns {

    /** Ticks past its morph a stand-in waits for the real entity's spawn, two seconds. */
    static final int SPAWN_GRACE_TICKS = 40;

    private static final Map<Integer, StandIn> STANDING = new HashMap<>();

    /**
     * A stand-in and the game time it is discarded at unless replaced.
     *
     * @param entity    the stand-in
     * @param expiresAt the game time it is discarded at
     */
    private record StandIn(Entity entity, long expiresAt) {
    }

    private MorphStandIns() {
    }

    /**
     * Stands a client-only entity of a type at a point under the id the real
     * entity will spawn with.
     *
     * @param level      the client level
     * @param type       the entity's type
     * @param entityId   the id the real entity holds
     * @param at         where the entity will stand
     * @param morphTicks the ticks the morph into it takes
     */
    public static void stand(ClientLevel level, Identifier type, int entityId, Vec3 at, int morphTicks) {
        BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(found -> found.create(level, EntitySpawnReason.LOAD))
                .ifPresent(entity -> {
                    entity.setId(entityId);
                    entity.snapTo(at.x, at.y, at.z, 0f, 0f);
                    level.addEntity(entity);
                    STANDING.put(entityId, new StandIn(entity,
                            level.getGameTime() + morphTicks + SPAWN_GRACE_TICKS));
                });
    }

    /**
     * Forgets each stand-in the real entity has replaced, and discards each
     * one still standing past its grace.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            STANDING.clear();
            return;
        }
        Iterator<Map.Entry<Integer, StandIn>> each = STANDING.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<Integer, StandIn> entry = each.next();
            StandIn standIn = entry.getValue();
            if (level.getEntity(entry.getKey()) != standIn.entity()) {
                each.remove();
            } else if (level.getGameTime() >= standIn.expiresAt()) {
                level.removeEntity(entry.getKey(), Entity.RemovalReason.DISCARDED);
                each.remove();
            }
        }
    }
}
