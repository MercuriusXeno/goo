package com.mercuriusxeno.goo.entity;

import com.mercuriusxeno.goo.ability.quantum.SwapBodyRef;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * A quantum swap body: an inert figure wearing its owner's skin where they
 * stood, holding the health they had there, until the owner swaps back into
 * it. Nothing hurts it and it does not move; a body its owner no longer
 * claims, as one left in an unloaded chunk when they swapped elsewhere,
 * falls away once its owner is about.
 * swap-body-stands-until-reverted
 */
public class SwapBody extends Mannequin {

    private static final String TAG_OWNER = "owner";
    private static final String TAG_HELD_HEALTH = "held_health";
    /** Ticks between one look at whether the owner still claims the body and the next. */
    private static final int CLAIM_CHECK_TICKS = 20;

    /**
     * Builds a body for its type and level: the client installs one that
     * draws the owner's skin, the server keeps this one.
     */
    private static EntityType.EntityFactory<SwapBody> constructor = SwapBody::new;

    private @Nullable UUID owner;
    private float heldHealth;

    /**
     * Creates a body with no owner yet.
     *
     * @param type  the swap body's type
     * @param level the level it stands in
     */
    @SuppressWarnings("unchecked") // Mannequin names its own type; a swap body is one of its kind under its own id
    public SwapBody(EntityType<? extends SwapBody> type, Level level) {
        super((EntityType<Mannequin>) (EntityType<?>) type, level);
    }

    /**
     * Builds a body through the factory this side installed.
     *
     * @param type  the swap body's type
     * @param level the level it stands in
     * @return the body
     */
    public static SwapBody build(EntityType<SwapBody> type, Level level) {
        return constructor.create(type, level);
    }

    /**
     * Installs the factory a side builds bodies with, as the client does to draw the owner's skin.
     *
     * @param factory the factory
     */
    public static void buildWith(EntityType.EntityFactory<SwapBody> factory) {
        constructor = factory;
    }

    /**
     * Shapes the body after a player where they stand: their skin, their
     * facing, and the health it holds for them.
     *
     * @param player the owner
     */
    public void standFor(ServerPlayer player) {
        owner = player.getUUID();
        heldHealth = player.getHealth();
        entityData.set(DATA_PROFILE, player.getProfile());
        snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        setYHeadRot(player.getYHeadRot());
        setYBodyRot(player.yBodyRot);
    }

    /**
     * @return the health the body holds for its owner
     */
    public float heldHealth() {
        return heldHealth;
    }

    /**
     * @return the player whose body this is, or null before it stands for one
     */
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server && owner != null && tickCount % CLAIM_CHECK_TICKS == 0 && !claimed(server)) {
            discard();
        }
    }

    /**
     * Whether the owner still claims this body; with the owner away the claim stands.
     *
     * @param level the level the body stands in
     * @return false only when the owner is about and claims another body or none
     */
    private boolean claimed(ServerLevel level) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return true;
        }
        Optional<SwapBodyRef> held = player.getData(GooAttachments.SWAP_BODY);
        return held.isPresent() && held.get().body().equals(getUUID());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (owner != null) {
            output.store(TAG_OWNER, UUIDUtil.CODEC, owner);
        }
        output.store(TAG_HELD_HEALTH, Codec.FLOAT, heldHealth);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        owner = input.read(TAG_OWNER, UUIDUtil.CODEC).orElse(null);
        heldHealth = input.read(TAG_HELD_HEALTH, Codec.FLOAT).orElse(0f);
    }
}
