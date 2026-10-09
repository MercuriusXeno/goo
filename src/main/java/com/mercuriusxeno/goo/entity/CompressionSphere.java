package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.registry.GooEntities;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The compression sphere a black hole leaves at its origin: a pseudo-item
 * that falls to the ground below and never enters an inventory; a player
 * touching it pops it open, spilling every stack it holds there as
 * ordinary item entities (decision black-hole-leaves-a-compression-sphere).
 */
public class CompressionSphere extends Entity {

    private static final String TAG_HOARD = "Hoard";
    private static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.98;
    private static final double GROUND_FRICTION = 0.6;

    private CompressedHoard hoard = new CompressedHoard();

    /**
     * The constructor the entity type builds a loaded or synced sphere with.
     *
     * @param type  the entity type
     * @param level the level
     */
    public CompressionSphere(EntityType<? extends CompressionSphere> type, Level level) {
        super(type, level);
    }

    /**
     * Stands a sphere holding a hoard at a point, still, so it falls
     * straight down to the ground below. An empty hoard leaves nothing.
     *
     * @param level the server level
     * @param at    where the sphere appears
     * @param held  the hoard it takes; emptied into the sphere
     */
    public static void leave(ServerLevel level, Vec3 at, CompressedHoard held) {
        if (held.isEmpty()) {
            return;
        }
        CompressionSphere sphere = new CompressionSphere(GooEntities.COMPRESSION_SPHERE.get(), level);
        sphere.setPos(at);
        sphere.hoard.takeAll(held);
        level.addFreshEntity(sphere);
    }

    /**
     * @return the hoard the sphere holds
     */
    public CompressedHoard hoard() {
        return hoard;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        // the client draws an orb and reads nothing the server holds
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public void tick() {
        super.tick();
        applyGravity();
        move(MoverType.SELF, getDeltaMovement());
        double friction = onGround() ? GROUND_FRICTION : AIR_DRAG;
        setDeltaMovement(getDeltaMovement().multiply(friction, AIR_DRAG, friction));
    }

    /**
     * Pops open on the server for any player but a spectator, spilling the
     * hoard where the sphere lies and removing it.
     *
     * @param player the touching player
     */
    @Override
    public void playerTouch(Player player) {
        if (!(level() instanceof ServerLevel server) || player.isSpectator() || isRemoved()) {
            return;
        }
        for (ItemStack stack : hoard.stacks()) {
            server.addFreshEntity(new ItemEntity(server, getX(), getY(), getZ(), stack.copy()));
        }
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        hoard = input.read(TAG_HOARD, CompressedHoard.CODEC).orElseGet(CompressedHoard::new);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store(TAG_HOARD, CompressedHoard.CODEC, hoard);
    }
}
