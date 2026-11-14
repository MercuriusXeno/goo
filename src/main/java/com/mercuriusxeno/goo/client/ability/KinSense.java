package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.zoo.Kin;
import com.mercuriusxeno.goo.client.fade.Afterglow;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The mobs Kin senses on its holder's client: every mob inside Kin's
 * radius, hostile and passive alike, gathered each client tick from the
 * entities the level renders. A sensed mob appears glowing, so it is outlined
 * through walls, and the outline's strength stands whole while Kin stands.
 * leader-outlines-every-mob-through-walls
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class KinSense {

    /** The local player's sensed set, read by the outline stamp and the glowing mixin. */
    public static final KinSense CLIENT = new KinSense();

    private final Set<Integer> sensed = new HashSet<>();
    private final Afterglow<Kin> glow = new Afterglow<>();
    private @Nullable Kin standing;

    /**
     * Whether Kin senses an entity: a mob inside the radius.
     *
     * @param entity the entity
     * @param center the Kin holder's position
     * @param radius Kin's radius
     * @return true for a mob within the radius of the center
     */
    static boolean senses(Entity entity, Vec3 center, double radius) {
        return entity instanceof Mob && entity.distanceToSqr(center) <= radius * radius;
    }

    /**
     * Gathers the mobs Kin senses this tick. While Kin stands the set
     * is gathered afresh; once it ends the last set stands where Kin left
     * it until the fade is spent.
     * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
     *
     * @param kin   the holder's Kin, NONE once it ended
     * @param entities the entities the level renders
     * @param center   the holder's position
     * @param time     the game time
     */
    void sense(Kin kin, Iterable<? extends Entity> entities, Vec3 center, float time) {
        standing = kin.stands() ? kin : null;
        float strength = strength(time);
        if (standing == null) {
            if (strength <= 0f) {
                sensed.clear();
            }
            return;
        }
        sensed.clear();
        for (Entity entity : entities) {
            if (senses(entity, center, kin.radius())) {
                sensed.add(entity.getId());
            }
        }
    }

    /**
     * The strength the sensed mobs' marks draw at.
     * brew-effect-visuals-linger-and-fade-over-a-second-after-the-effect-ends
     *
     * @param time the game time, partial tick included
     * @return one while Kin stands, falling to zero over the second after it ends
     */
    public float strength(float time) {
        return glow.read(standing, time).map(Afterglow.Glow::strength).orElse(0f);
    }

    /**
     * Whether a mob appears glowing because Kin senses it.
     *
     * @param entityId the mob's id
     * @return true while the mob stands in the sensed set
     */
    public boolean appearsGlowing(int entityId) {
        return sensed.contains(entityId);
    }

    /**
     * The ids of the mobs Kin senses.
     *
     * @return the sensed set, unmodifiable
     */
    public Set<Integer> sensedIds() {
        return Collections.unmodifiableSet(sensed);
    }

    /**
     * Gathers the local player's sensed set each client tick.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            CLIENT.sense(Kin.NONE, Set.of(), Vec3.ZERO, 0f);
            return;
        }
        CLIENT.sense(player.getData(GooAttachments.KIN), mc.level.entitiesForRendering(), player.position(),
                mc.level.getGameTime());
    }
}
