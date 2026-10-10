package com.mercuriusxeno.goo.ability.nether;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs Undead against the world for a player it stands on: a smite weapon
 * strikes the player as it strikes a zombie, and direct daylight burns the
 * player the attachment's damage each second; harming and healing invert
 * through {@code LivingEntityUndeadMixin} (decision undead-nether-hearts-burn-in-sunlight).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class UndeadEvents {

    /** Smite's bonus damage a level against the undead, vanilla's. */
    static final float SMITE_DAMAGE_PER_LEVEL = 2.5f;
    /** The sun burns once a second. */
    static final int SUN_BURN_PERIOD = 20;
    /**
     * The damage type the sun deals an undead player, aggravated against its
     * nether hearts (decision undead-nether-hearts-burn-in-sunlight).
     */
    public static final ResourceKey<DamageType> SUNBURN =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Goo.MODID, "sunburn"));

    private UndeadEvents() {
    }

    /**
     * Adds smite's bonus to a hit on an undead player by a smite weapon.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.getData(GooAttachments.UNDEAD).stands()) {
            return;
        }
        ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon == null || weapon.isEmpty()) {
            return;
        }
        Holder<Enchantment> smite = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SMITE);
        int level = EnchantmentHelper.getTagEnchantmentLevel(smite, weapon);
        if (level > 0) {
            event.setAmount(event.getAmount() + SMITE_DAMAGE_PER_LEVEL * level);
        }
    }

    /**
     * Burns an undead player in direct daylight once a second.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % SUN_BURN_PERIOD != 0) {
            return;
        }
        Undead undead = player.getData(GooAttachments.UNDEAD);
        if (undead.stands() && inDirectDaylight(player)) {
            ServerLevel level = player.level();
            DamageSource sunburn = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                    .getOrThrow(SUNBURN));
            player.hurtServer(level, sunburn, undead.sunDamage());
        }
    }

    /**
     * Answers whether the sun reaches the player as it reaches a burning
     * zombie: a time and place monsters burn, the sky open above the eyes,
     * and no water, rain or powder snow on the player.
     *
     * @param player the player
     * @return true in direct daylight
     */
    static boolean inDirectDaylight(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos eyes = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        return level.environmentAttributes().getValue(EnvironmentAttributes.MONSTERS_BURN, player.position())
                && !player.isInWaterOrRain() && !player.isInPowderSnow && level.canSeeSky(eyes);
    }
}
