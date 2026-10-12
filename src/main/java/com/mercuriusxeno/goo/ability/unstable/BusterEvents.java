package com.mercuriusxeno.goo.ability.unstable;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.held.LaidState;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a held Buster: each melee hit its player lands strikes every living
 * thing within the radius of the struck one for the same damage, and each
 * block the player breaks breaks the 3x3x3 around it where a block is no
 * harder than the one broken, each dropping as the player's tool gives it.
 * Both land with a small explosion's burst and bang, and the player wears
 * the unstable green overlay while it stands.
 * buster-makes-tools-and-attacks-aoe
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class BusterEvents {

    /** Ticks between one overlay refresh and the next, so a player who starts watching sees it within a second. */
    static final int OVERLAY_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh and its fade, so the overlay never dips. */
    static final int OVERLAY_TICKS = OVERLAY_REFRESH_TICKS * 3;
    private static final int OVERLAY_CLEARED = 0;
    private static final float NOT_BUSTING = 0f;
    /** The least hardness a breakable block has; an unbreakable block, bedrock among them, reads negative. */
    private static final float MIN_BREAKABLE_HARDNESS = 0f;
    private static final double BODY_CENTER = 0.5;
    private static final float BANG_VOLUME = 0.5f;
    private static final float BANG_PITCH = 1.6f;
    private static final int BURST_COUNT = 1;
    /** How far the 3x3x3 reaches from the broken block along each axis. */
    private static final int CUBE_REACH = 1;

    /**
     * True while Buster's own hits and breaks run, so the damage and the
     * breaks they cause start no blast of their own.
     */
    private static boolean spreading;

    private BusterEvents() {
    }

    /**
     * Lays Buster on a player at a blast radius, under its overlay.
     *
     * @param player the busting player
     * @param radius how far from the struck entity a hit's blast reaches, in blocks
     */
    public static void lay(Player player, float radius) {
        player.setData(GooAttachments.BUSTER_RADIUS, radius);
        sendOverlay(player, OVERLAY_TICKS);
    }

    /**
     * Clears Buster from a player as its held effect ends: the blasts stop
     * and the overlay drops on every client.
     *
     * @param player the player
     */
    public static void clear(ServerPlayer player) {
        player.setData(GooAttachments.BUSTER_RADIUS, NOT_BUSTING);
        sendOverlay(player, OVERLAY_CLEARED);
    }

    /**
     * The radius a player's hits blast at, zero for a player Buster leaves alone.
     *
     * @param player the player
     * @return the blast radius in blocks
     */
    public static float radiusOf(Player player) {
        return player.hasData(GooAttachments.BUSTER_RADIUS) ? player.getData(GooAttachments.BUSTER_RADIUS) : NOT_BUSTING;
    }

    /**
     * Whether a block beside a broken one breaks with it: a breakable block
     * no harder than the broken block.
     *
     * @param neighborHardness the neighbor's hardness, negative for an unbreakable block
     * @param brokenHardness   the broken block's hardness
     * @return true where the neighbor breaks
     */
    public static boolean breaksBeside(float neighborHardness, float brokenHardness) {
        return neighborHardness >= MIN_BREAKABLE_HARDNESS && neighborHardness <= brokenHardness;
    }

    /**
     * Refreshes the overlay of each player whose held effects lay Buster.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % OVERLAY_REFRESH_TICKS != 0
                || !player.hasData(GooAttachments.HELD_EFFECTS)) {
            return;
        }
        boolean busting = player.getData(GooAttachments.HELD_EFFECTS).held().stream()
                .anyMatch(held -> held.lays().contains(LaidState.BUSTER));
        if (busting) {
            sendOverlay(player, OVERLAY_TICKS);
        }
    }

    /**
     * Spreads a busting player's melee hit to every living thing within the
     * blast radius of the struck one, for the damage the hit dealt.
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        ServerPlayer attacker = bustingMeleeAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        LivingEntity struck = event.getEntity();
        ServerLevel level = attacker.level();
        List<LivingEntity> caught = caughtAround(struck, attacker, radiusOf(attacker));
        spreadAs(() -> caught.forEach(other ->
                other.hurtServer(level, attacker.damageSources().playerAttack(attacker), event.getNewDamage())));
        burst(level, struck.position().add(0, struck.getBbHeight() * BODY_CENTER, 0));
    }

    /**
     * Breaks the 3x3x3 around a block a busting player breaks, each block
     * no harder than the broken one, as the player's own break.
     *
     * @param event the break event, before the block is removed
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (spreading || !(event.getPlayer() instanceof ServerPlayer player) || radiusOf(player) <= NOT_BUSTING) {
            return;
        }
        BlockPos origin = event.getPos();
        float hardness = event.getState().getDestroySpeed(player.level(), origin);
        spreadAs(() -> neighborsOf(origin).forEach(pos -> breakBeside(player, pos, hardness)));
        burst(player.level(), Vec3.atCenterOf(origin));
    }

    /**
     * The player behind a melee hit Buster spreads: a busting player's own
     * punch or swing, never a blast Buster spread itself.
     *
     * @param source the hit's damage source
     * @return the busting attacker, or null for a hit Buster leaves alone
     */
    private static @Nullable ServerPlayer bustingMeleeAttacker(DamageSource source) {
        if (spreading || !(source.getEntity() instanceof ServerPlayer attacker)
                || source.getDirectEntity() != attacker || !source.is(DamageTypes.PLAYER_ATTACK)) {
            return null;
        }
        return radiusOf(attacker) > NOT_BUSTING ? attacker : null;
    }

    private static List<LivingEntity> caughtAround(LivingEntity struck, ServerPlayer attacker, float radius) {
        return attacker.level().getEntitiesOfClass(LivingEntity.class, struck.getBoundingBox().inflate(radius),
                other -> other != struck && other != attacker && other.isAlive() && other.distanceTo(struck) <= radius);
    }

    private static List<BlockPos> neighborsOf(BlockPos origin) {
        List<BlockPos> neighbors = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-CUBE_REACH, -CUBE_REACH, -CUBE_REACH),
                origin.offset(CUBE_REACH, CUBE_REACH, CUBE_REACH))) {
            if (!pos.equals(origin)) {
                neighbors.add(pos.immutable());
            }
        }
        return neighbors;
    }

    private static void breakBeside(ServerPlayer player, BlockPos pos, float brokenHardness) {
        BlockState beside = player.level().getBlockState(pos);
        if (!beside.isAir() && breaksBeside(beside.getDestroySpeed(player.level(), pos), brokenHardness)) {
            player.gameMode.destroyBlock(pos);
        }
    }

    /**
     * Runs Buster's own hits or breaks, which start no blast of their own.
     *
     * @param spread the hits or breaks
     */
    private static void spreadAs(Runnable spread) {
        spreading = true;
        try {
            spread.run();
        } finally {
            spreading = false;
        }
    }
    private static void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, BURST_COUNT, 0, 0, 0, 0);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, BANG_VOLUME,
                BANG_PITCH);
    }

    private static void sendOverlay(LivingEntity target, int ticks) {
        EntityVisuals.sendToWatchers(target, new AilmentPayload(target.getId(), AilmentKind.BUSTER, ticks));
    }
}
