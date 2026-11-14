package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.EnvelopBurstPayload;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * The slime shells charm-like casts leave on their mobs, keyed by entity id:
 * the dart's landing wraps the mob in a shell of the goo, which stands whole
 * for a moment, then melts from the head down to the feet, shedding drips
 * along its melt line, and is gone; a failed attempt bursts it off at once in
 * a spray of goo.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 * operator: "the blob darts out and envelops them in a sort of purple slime"
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SlimeShells {

    /** Game ticks the shell stands whole after the dart lands. */
    public static final int HOLD_TICKS = 6;
    /** Game ticks the shell takes to melt from the head to the feet, about a second. */
    public static final int MELT_TICKS = 20;
    /** Game ticks a shell stands in all. */
    public static final int LIFE_TICKS = HOLD_TICKS + MELT_TICKS;

    /** The client's shells. */
    public static final SlimeShells CLIENT = new SlimeShells();

    private static final int OPAQUE_BLACK = 0xFF000000;
    /** Points around the mob's box a burst sprays from: its middle, its crown and its four sides. */
    private static final List<Vec3> BURST_POINTS = List.of(Vec3.ZERO, new Vec3(0, 0.5, 0), new Vec3(0.5, 0, 0),
            new Vec3(-0.5, 0, 0), new Vec3(0, 0, 0.5), new Vec3(0, 0, -0.5));

    private final Map<Integer, Shell> shells = new HashMap<>();

    /**
     * One mob's shell.
     *
     * @param gooType the goo it is made of
     * @param landed  the game tick the dart landed
     */
    private record Shell(ResourceKey<GooTypeDefinition> gooType, long landed) {
    }

    /**
     * A shell as a frame or a tick reads it.
     *
     * @param gooType the goo it is made of
     * @param melted  how far down the body it has melted, 0 whole to 1 gone
     */
    public record Worn(ResourceKey<GooTypeDefinition> gooType, float melted) {
    }

    /**
     * How far a shell has melted at an age.
     *
     * @param ageTicks game ticks since the dart landed, with the partial tick
     * @return 0 through the hold, rising to 1 across the melt
     */
    static float melted(float ageTicks) {
        return Math.clamp((ageTicks - HOLD_TICKS) / MELT_TICKS, 0f, 1f);
    }

    /**
     * Wraps a mob in a fresh shell, replacing any it wears.
     *
     * @param entityId the mob's id
     * @param gooType  the goo the dart was made of
     * @param tick     the game tick the dart landed
     */
    public void envelop(int entityId, ResourceKey<GooTypeDefinition> gooType, long tick) {
        shells.put(entityId, new Shell(gooType, tick));
    }

    /**
     * Takes a mob's shell off at once.
     *
     * @param entityId the mob's id
     * @return the goo the shell was made of, empty where the mob wore none
     */
    public Optional<ResourceKey<GooTypeDefinition>> burst(int entityId) {
        return Optional.ofNullable(shells.remove(entityId)).map(Shell::gooType);
    }

    /**
     * The shell a mob wears at a moment.
     *
     * @param entityId the mob's id
     * @param gameTime the game time, with the partial tick
     * @return the shell, empty where the mob wears none or it has melted away
     */
    public Optional<Worn> shellOf(int entityId, float gameTime) {
        Shell shell = shells.get(entityId);
        if (shell == null || gameTime - shell.landed() >= LIFE_TICKS) {
            return Optional.empty();
        }
        return Optional.of(new Worn(shell.gooType(), melted(gameTime - shell.landed())));
    }

    /** Drops every shell, as a disconnect does. */
    public void clear() {
        shells.clear();
    }

    /**
     * Sheds a drip a tick from each melting shell's melt line, and drops the
     * shells that have melted away.
     *
     * @param event the client tick
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        long tick = level.getGameTime();
        CLIENT.shells.entrySet().removeIf(entry -> tick - entry.getValue().landed() >= LIFE_TICKS);
        RandomGenerator random = RandomGenerator.getDefault();
        CLIENT.shells.forEach((entityId, shell) -> {
            Entity mob = level.getEntity(entityId);
            float melted = melted(tick - shell.landed());
            if (mob != null && melted > 0f) {
                Vec3 drip = meltLinePoint(mob.getBoundingBox(), melted, random);
                level.addParticle(ColorParticleOption.create(GooParticles.SPLAT_DRIP.get(), argbOf(level, shell)),
                        drip.x, drip.y, drip.z, 0, 0, 0);
            }
        });
    }

    /**
     * A point on the side of a mob's box at the height its shell has melted
     * down to, where a drip leaves.
     *
     * @param box    the mob's box
     * @param melted how far down the body the shell has melted
     * @param random the random the side and the place along it are drawn from
     * @return the point
     */
    static Vec3 meltLinePoint(AABB box, float melted, RandomGenerator random) {
        double y = box.maxY - melted * box.getYsize();
        double along = random.nextDouble();
        boolean low = random.nextBoolean();
        return random.nextBoolean()
                ? new Vec3(low ? box.minX : box.maxX, y, box.minZ + along * box.getZsize())
                : new Vec3(box.minX + along * box.getXsize(), y, low ? box.minZ : box.maxZ);
    }

    /**
     * Bursts a mob's shell off in a spray of its goo, on the client thread.
     *
     * @param payload the burst
     * @param context the network context
     */
    public static void onBurst(EnvelopBurstPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            Entity mob = level == null ? null : level.getEntity(payload.entityId());
            Optional<ResourceKey<GooTypeDefinition>> goo = CLIENT.burst(payload.entityId());
            if (mob != null && goo.isPresent()) {
                spray(level, mob.getBoundingBox(), GooColors.get(level.registryAccess(), goo.get()));
            }
        });
    }

    /**
     * Sprays goo off a mob's box from its middle, its crown and its sides.
     *
     * @param level the client level
     * @param box   the mob's box
     * @param rgb   the goo's color
     */
    private static void spray(ClientLevel level, AABB box, int rgb) {
        Vec3 middle = box.getCenter();
        for (Vec3 point : BURST_POINTS) {
            Vec3 from = middle.add(point.x * box.getXsize(), point.y * box.getYsize(), point.z * box.getZsize());
            MobHitBurst.spawn(level, from, middle, rgb);
        }
    }

    private static int argbOf(ClientLevel level, Shell shell) {
        return GooColors.get(level.registryAccess(), shell.gooType()) | OPAQUE_BLACK;
    }
}
