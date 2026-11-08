package com.frikinjay.mobstacker.compat;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.AABB;
//? if >=1.21 {
/*import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
*///?}

/**
 * The small things Minecraft renamed or reshaped between versions, each said once. Code that only
 * needs one of them calls it here instead of carrying a version condition of its own; bigger
 * differences (rendering, screens, networking) live where they are used.
 *
 * <p>The version where each one changed is the best known one; the nodes between 1.20.1 and 1.21.1
 * (1.20.2-1.20.6) will confirm them when they are added.
 */
public final class Compat {
    private Compat() {
    }

    // How an entity's saved attributes look. From 1.20.5 the keys are lower case and a modifier is
    // known by a resource id instead of a UUID and a name.
    //? if >=1.20.5 {
    /*public static final String ATTRIBUTES_KEY = "attributes";
    public static final String MODIFIERS_KEY = "modifiers";
    public static final String MODIFIER_ID_KEY = "id";
    public static final String LEADER_ZOMBIE_BONUS = "minecraft:leader_zombie_bonus";
    *///?} else {
    public static final String ATTRIBUTES_KEY = "Attributes";
    public static final String MODIFIERS_KEY = "Modifiers";
    public static final String MODIFIER_ID_KEY = "Name";
    public static final String LEADER_ZOMBIE_BONUS = "Leader zombie bonus";
    //?}

    /** {@code LivingEntity.die}'s call that drops the loot; it takes the level from 1.21. */
    //? if >=1.21 {
    /*public static final String DROP_ALL_DEATH_LOOT =
            "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V";
    *///?} else {
    public static final String DROP_ALL_DEATH_LOOT =
            "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/world/damagesource/DamageSource;)V";
    //?}

    /** The {@code ItemStack} method that wears a weapon after a hit. */
    //? if >=1.21 {
    /*public static final String WEAPON_HIT_METHOD = "postHurtEnemy";
    *///?} else {
    public static final String WEAPON_HIT_METHOD = "hurtEnemy";
    //?}

    /** The call that wears a dispenser's shears; its first argument is the wear. */
    //? if >=1.21 {
    /*public static final String DISPENSER_SHEARS_WEAR =
            "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V";
    *///?} else {
    public static final String DISPENSER_SHEARS_WEAR =
            "Lnet/minecraft/world/item/ItemStack;hurt(ILnet/minecraft/util/RandomSource;Lnet/minecraft/server/level/ServerPlayer;)Z";
    //?}

    /**
     * The target of vanilla's "wear the shears" call in {@code Sheep/MushroomCow/SnowGolem.mobInteract}:
     * a callback until 1.20.5, the slot from then on.
     */
    //? if >=1.20.5 {
    /*public static final String HURT_AND_BREAK =
            "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;)V";
    *///?} else {
    public static final String HURT_AND_BREAK =
            "Lnet/minecraft/world/item/ItemStack;hurtAndBreak(ILnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V";
    //?}

    /** {@code namespace:path}. The constructor went private in 1.21. */
    public static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        /*return ResourceLocation.fromNamespaceAndPath(namespace, path);
        *///?} else {
        return new ResourceLocation(namespace, path);
        //?}
    }

    /** The id an entity data value travels under. {@code EntityDataAccessor} became a record in 1.20.5. */
    public static int dataId(EntityDataAccessor<?> accessor) {
        //? if >=1.20.5 {
        /*return accessor.id();
        *///?} else {
        return accessor.getId();
        //?}
    }

    /** A text component as JSON. From 1.20.5 it needs the registries (item and other components in it). */
    public static String toJson(Component component, RegistryAccess registries) {
        //? if >=1.20.5 {
        /*return Component.Serializer.toJson(component, registries);
        *///?} else {
        return Component.Serializer.toJson(component);
        //?}
    }

    /** The other way; null when the text is not a component. */
    public static Component fromJson(String json, RegistryAccess registries) {
        //? if >=1.20.5 {
        /*return Component.Serializer.fromJson(json, registries);
        *///?} else {
        return Component.Serializer.fromJson(json);
        //?}
    }

    /**
     * A non-empty item as the game saves it. From 1.20.5 items are components instead of an NBT tag, and
     * the saved shape is {@code {id, count, components}} rather than {@code {id, Count, tag}}.
     */
    public static CompoundTag saveItem(ItemStack stack, RegistryAccess registries) {
        //? if >=1.20.5 {
        /*return (CompoundTag) stack.save(registries);
        *///?} else {
        return stack.save(new CompoundTag());
        //?}
    }

    /** An item saved by {@link #saveItem}; empty when the tag holds none. */
    public static ItemStack loadItem(CompoundTag tag, RegistryAccess registries) {
        //? if >=1.20.5 {
        /*return ItemStack.parseOptional(registries, tag);
        *///?} else {
        return ItemStack.of(tag);
        //?}
    }

    /** Whether the item would vanish instead of dropping (the curse of vanishing, or what does its job from 1.21). */
    public static boolean vanishes(ItemStack stack) {
        //? if >=1.21 {
        /*return EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
        *///?} else {
        return EnchantmentHelper.hasVanishingCurse(stack);
        //?}
    }

    /**
     * How much of an attack a sweep deals to what stands around the target: level / (level + 1) of
     * the attacker's Sweeping Edge until 1.21, where the enchantment became an attribute
     * ({@code sweeping_damage_ratio}) that anything - another mod's weapon too - can raise.
     */
    public static float sweepingRatio(LivingEntity attacker) {
        //? if >=1.21 {
        /*net.minecraft.world.entity.ai.attributes.AttributeInstance ratio =
                attacker.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SWEEPING_DAMAGE_RATIO);
        return ratio == null ? 0.0F : (float) ratio.getValue();
        *///?} else {
        int level = EnchantmentHelper.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, attacker);
        return level <= 0 ? 0.0F : (float) level / (level + 1);
        //?}
    }

    /** Whether a horse wears armor. From 1.20.5 that is the body slot every mob has. */
    public static boolean wearsArmor(AbstractHorse horse) {
        //? if >=1.20.5 {
        /*return horse.isWearingBodyArmor();
        *///?} else {
        return horse.isWearingArmor();
        //?}
    }

    /** Wears an item in a slot by {@code amount}, breaking it (with the sound) when it runs out. */
    public static void hurtItem(ItemStack stack, int amount, LivingEntity owner, EquipmentSlot slot) {
        //? if >=1.20.5 {
        /*stack.hurtAndBreak(amount, owner, slot);
        *///?} else {
        stack.hurtAndBreak(amount, owner, broken -> broken.broadcastBreakEvent(slot));
        //?}
    }

    /** Whether two items may share a stack: same item, same data. */
    public static boolean sameItemSameData(ItemStack a, ItemStack b) {
        //? if >=1.20.5 {
        /*return ItemStack.isSameItemSameComponents(a, b);
        *///?} else {
        return ItemStack.isSameItemSameTags(a, b);
        //?}
    }

    /** The box a mob of this type takes up standing at x, y, z. */
    public static AABB spawnBox(EntityType<?> type, double x, double y, double z) {
        //? if >=1.20.5 {
        /*return type.getSpawnAABB(x, y, z);
        *///?} else {
        return type.getAABB(x, y, z);
        //?}
    }

    /** Whether a typed character may go into a text box. */
    public static boolean isAllowedChatCharacter(char c) {
        //? if >=1.20.5 {
        /*return net.minecraft.util.StringUtil.isAllowedChatCharacter(c);
        *///?} else {
        return net.minecraft.SharedConstants.isAllowedChatCharacter(c);
        //?}
    }
}
