package com.frikinjay.mobstacker.compat;

import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
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

    /** {@code LivingEntity.die}'s kill score call: the score itself is gone from 1.21.2. */
    //? if >=1.21.2 {
    /*public static final String AWARD_KILL_SCORE =
            "Lnet/minecraft/world/entity/LivingEntity;awardKillScore(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V";
    *///?} else {
    public static final String AWARD_KILL_SCORE =
            "Lnet/minecraft/world/entity/LivingEntity;awardKillScore(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/world/damagesource/DamageSource;)V";
    //?}

    /** The hit in {@code Mob.doHurtTarget} and the index of its damage: hurtServer from 1.21.2. */
    //? if >=1.21.2 {
    /*public static final String MELEE_HURT =
            "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    public static final int MELEE_HURT_DAMAGE = 2;
    *///?} else {
    public static final String MELEE_HURT =
            "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z";
    public static final int MELEE_HURT_DAMAGE = 1;
    //?}

    /** {@code Mob.convertTo}: the overload every conversion ends in (two of them from 1.21.2). */
    //? if >=1.21.2 {
    /*public static final String CONVERT_TO =
            "convertTo(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/ConversionParams;Lnet/minecraft/world/entity/EntitySpawnReason;Lnet/minecraft/world/entity/ConversionParams$AfterConversion;)Lnet/minecraft/world/entity/Mob;";
    *///?} else {
    public static final String CONVERT_TO = "convertTo";
    //?}

    /** A packet sent with a listener (unconfirmed: 26.1 takes Netty's listener). */
    //? if >=26.1 {
    /*public static final String SEND_WITH_LISTENER =
            "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V";
    *///?} else {
    public static final String SEND_WITH_LISTENER =
            "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V";
    //?}

    /** The custom click action of a command in a chat panel (see {@link #runCommand}). */
    public static final ResourceLocation CLICKED_COMMAND = id("mobstacker", "command");

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
        //? if >=26.1 {
        /*return net.minecraft.network.chat.ComponentSerialization.CODEC
                .encodeStart(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), component)
                .getOrThrow().toString();
        *///?} else if >=1.20.5 {
        /*return Component.Serializer.toJson(component, registries);
        *///?} else {
        return Component.Serializer.toJson(component);
        //?}
    }

    /** The other way; null when the text is not a component. */
    public static Component fromJson(String json, RegistryAccess registries) {
        //? if >=26.1 {
        /*try {
            return net.minecraft.network.chat.ComponentSerialization.CODEC
                    .parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                            com.google.gson.JsonParser.parseString(json))
                    .result().orElse(null);
        } catch (RuntimeException notJson) {
            return null;
        }
        *///?} else if >=1.20.5 {
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
        //? if >=1.21.6 {
        /*return (CompoundTag) ItemStack.CODEC
                .encodeStart(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), stack).getOrThrow();
        *///?} else if >=1.20.5 {
        /*return (CompoundTag) stack.save(registries);
        *///?} else {
        return stack.save(new CompoundTag());
        //?}
    }

    /** An item saved by {@link #saveItem}; empty when the tag holds none. */
    public static ItemStack loadItem(CompoundTag tag, RegistryAccess registries) {
        //? if >=1.21.6 {
        /*return ItemStack.OPTIONAL_CODEC
                .parse(registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag)
                .result().orElse(ItemStack.EMPTY);
        *///?} else if >=1.20.5 {
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

    // ---- From here on: what changed between 1.21.2 and 26.3. Versions marked "unconfirmed" are the
    // best known ones; a node in between moves them if they are wrong. ----

    /** A new entity of this type, not yet in the world. The reason only counts from 1.21.2. */
    public static <T extends Entity> T create(EntityType<T> type, Level level, MobSpawnType reason) {
        //? if >=1.21.2 {
        /*return type.create(level, reason);
        *///?} else {
        return type.create(level);
        //?}
    }

    /** The entity's saved data, without its id. From 1.21.6 entities save through a ValueOutput. */
    public static CompoundTag save(Entity entity) {
        //? if >=1.21.6 {
        /*net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput
                .createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, entity.level().registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
        *///?} else {
        return entity.saveWithoutId(new CompoundTag());
        //?}
    }

    /** Loads data saved by {@link #save} into the entity. */
    public static void load(Entity entity, CompoundTag data) {
        //? if >=1.21.6 {
        /*entity.load(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, entity.level().registryAccess(), data));
        *///?} else {
        entity.load(data);
        //?}
    }

    /** Drops an item where the entity stands (server side). */
    public static ItemEntity spawnAtLocation(Entity entity, ItemStack stack) {
        //? if >=1.21.2 {
        /*return entity.level() instanceof ServerLevel level ? entity.spawnAtLocation(level, stack) : null;
        *///?} else {
        return entity.spawnAtLocation(stack);
        //?}
    }

    /** Drops an item where the entity stands, {@code yOffset} higher (server side). */
    public static ItemEntity spawnAtLocation(Entity entity, ItemStack stack, float yOffset) {
        //? if >=1.21.2 {
        /*return entity.level() instanceof ServerLevel level ? entity.spawnAtLocation(level, stack, yOffset) : null;
        *///?} else {
        return entity.spawnAtLocation(stack, yOffset);
        //?}
    }

    /** The item registered under {@code id}, air when there is none. */
    public static Item item(ResourceLocation id) {
        //? if >=1.21.2 {
        /*return BuiltInRegistries.ITEM.getValue(id);
        *///?} else {
        return BuiltInRegistries.ITEM.get(id);
        //?}
    }

    /** The id a registry key stands for. */
    public static ResourceLocation keyId(ResourceKey<?> key) {
        //? if >=1.21.11 {
        /*return key.identifier();
        *///?} else {
        return key.location();
        //?}
    }

    /** Every tag the registry has. */
    public static <T> Stream<TagKey<T>> tagKeys(Registry<T> registry) {
        //? if >=1.21.2 {
        /*return registry.getTags().map(net.minecraft.core.HolderSet.Named::key);
        *///?} else {
        return registry.getTagNames();
        //?}
    }

    /** Whether the registry has this tag (the data pack that defines it may not be loaded). */
    public static <T> boolean hasTag(Registry<T> registry, TagKey<T> tag) {
        //? if >=1.21.2 {
        /*return registry.get(tag).isPresent();
        *///?} else {
        return registry.getTag(tag).isPresent();
        //?}
    }

    /** Who owns a tamed animal, or null. */
    public static UUID ownerId(OwnableEntity owned) {
        //? if >=1.21.5 {
        /*net.minecraft.world.entity.EntityReference<?> owner = owned.getOwnerReference();
        return owner == null ? null : owner.getUUID();
        *///?} else {
        return owned.getOwnerUUID();
        //?}
    }

    /** Whether a mob wears a saddle. From 1.21.5 the saddle is an equipment slot. */
    public static boolean isSaddled(Entity entity) {
        //? if >=1.21.5 {
        /*return entity instanceof Mob mob && mob.isSaddled();
        *///?} else {
        return entity instanceof net.minecraft.world.entity.Saddleable saddleable && saddleable.isSaddled();
        //?}
    }

    /** Whether a saddle would go on this mob now. */
    public static boolean takesSaddle(Mob mob) {
        //? if >=1.21.5 {
        /*return !mob.isSaddled() && mob.isEquippableInSlot(new ItemStack(net.minecraft.world.item.Items.SADDLE), EquipmentSlot.SADDLE);
        *///?} else {
        return mob instanceof net.minecraft.world.entity.Saddleable saddleable
                && saddleable.isSaddleable() && !saddleable.isSaddled();
        //?}
    }

    /** Whether two villagers (or zombie villagers) come from the same biome. */
    public static boolean sameVillagerType(VillagerDataHolder a, VillagerDataHolder b) {
        //? if >=1.21.5 {
        /*return a.getVillagerData().type().equals(b.getVillagerData().type());
        *///?} else {
        return a.getVillagerData().getType() == b.getVillagerData().getType();
        //?}
    }

    /** Whether a villager (or zombie villager) has a job. */
    public static boolean hasProfession(VillagerDataHolder villager) {
        //? if >=1.21.5 {
        /*return !villager.getVillagerData().profession().is(VillagerProfession.NONE);
        *///?} else {
        return villager.getVillagerData().getProfession() != VillagerProfession.NONE;
        //?}
    }

    /** Takes a villager's job away: jobless, level 1. */
    public static void makeJobless(Villager villager) {
        setProfession(villager, VillagerProfession.NONE, 1);
    }

    /** Gives a villager a job at a level. From 1.21.5 a profession is named by its registry key. */
    //? if >=1.21.5 {
    /*public static void setProfession(Villager villager, ResourceKey<VillagerProfession> job, int level) {
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(villager.level().registryAccess(), job).withLevel(level));
    }
    *///?} else {
    public static void setProfession(Villager villager, VillagerProfession job, int level) {
        villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(level));
    }
    //?}

    /** Puts a saddle on a mob that can wear one. */
    public static void saddle(Mob mob) {
        ItemStack saddle = new ItemStack(net.minecraft.world.item.Items.SADDLE);
        //? if >=1.21.5 {
        /*mob.setItemSlot(EquipmentSlot.SADDLE, saddle);
        *///?} else if >=1.20.5 {
        /*((net.minecraft.world.entity.Saddleable) mob).equipSaddle(saddle, null);
        *///?} else {
        ((net.minecraft.world.entity.Saddleable) mob).equipSaddle(null);
        //?}
    }

    /** One dye item (unconfirmed: 26.1, where the dyes became one collection). */
    public static Item dye(net.minecraft.world.item.DyeColor color) {
        //? if >=26.1 {
        /*return net.minecraft.world.item.Items.DYE.pick(color);
        *///?} else {
        return net.minecraft.world.item.DyeItem.byColor(color);
        //?}
    }

    /** The source with this operator level instead of its own. */
    public static CommandSourceStack withPermission(CommandSourceStack source, int level) {
        //? if >=1.21.11 {
        /*return source.withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.forLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        *///?} else {
        return source.withPermission(level);
        //?}
    }

    /** Whether the entity may use commands of this operator level (only a player can). */
    public static boolean hasPermission(Entity entity, int level) {
        return entity instanceof Player player && hasPermission(player, level);
    }

    /** Whether a player sees the answers to its commands ({@code sendCommandFeedback}). */
    public static boolean acceptsSuccess(ServerPlayer player) {
        //? if >=26.1 {
        /*return player.commandSource().acceptsSuccess();
        *///?} else {
        return player.acceptsSuccess();
        //?}
    }

    /** Puts an item into the player's inventory, or drops it at the player's feet when it is full. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        //? if >=26.1 {
        /*player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
        *///?} else {
        player.getInventory().placeItemBackInInventory(stack);
        //?}
    }

    /** A tropical fish's pattern (one of the twelve shapes). */
    public static net.minecraft.world.entity.animal.TropicalFish.Pattern fishPattern(net.minecraft.world.entity.animal.TropicalFish fish) {
        //? if >=1.21.5 {
        /*return fish.getPattern();
        *///?} else {
        return fish.getVariant();
        //?}
    }

    /** A player's name and id, whatever the profile type of the version (a record from 1.21.9). */
    public record PlayerRef(UUID id, String name) {
    }

    /** The players a {@code GameProfileArgument} names. */
    public static List<PlayerRef> profiles(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context, String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        List<PlayerRef> out = new ArrayList<>();
        //? if >=1.21.9 {
        /*for (net.minecraft.server.players.NameAndId profile : net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(context, name)) {
            out.add(new PlayerRef(profile.id(), profile.name()));
        }
        *///?} else {
        for (com.mojang.authlib.GameProfile profile : net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(context, name)) {
            out.add(new PlayerRef(profile.getId(), profile.getName()));
        }
        //?}
        return out;
    }

    /** The name the server knows for a player id, online or remembered, or null. */
    public static String knownName(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        //? if >=1.21.9 {
        /*if (online != null) {
            return online.getGameProfile().name();
        }
        return server.services().nameToIdCache().get(id).map(net.minecraft.server.players.NameAndId::name).orElse(null);
        *///?} else {
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(null);
        }
        return null;
        //?}
    }

    /** A right click on a mob, as the player's hand does it (unconfirmed: 26.1 added the spot clicked). */
    public static net.minecraft.world.InteractionResult interact(Mob mob, Player player, net.minecraft.world.InteractionHand hand) {
        //? if >=26.1 {
        /*return mob.interact(player, hand, mob.position());
        *///?} else {
        return mob.interact(player, hand);
        //?}
    }

    /**
     * Shears a mob. From 1.21.2 vanilla wants the level, from 1.21.5 the shears too (for the loot
     * table); a plain pair stands in for them here, as the shears' own wear is counted elsewhere.
     */
    public static void shear(net.minecraft.world.entity.Shearable target, net.minecraft.sounds.SoundSource source) {
        //? if >=1.21.5 {
        /*if (target instanceof Entity entity && entity.level() instanceof ServerLevel level) {
            target.shear(level, source, new ItemStack(net.minecraft.world.item.Items.SHEARS));
        }
        *///?} else if >=1.21.2 {
        /*if (target instanceof Entity entity && entity.level() instanceof ServerLevel level) {
            target.shear(level, source);
        }
        *///?} else {
        target.shear(source);
        //?}
    }

    /** The inventory slots that hold items rather than armour: the main inventory, then the off hand. */
    public static List<ItemStack> carriedItems(Player player) {
        //? if >=1.21.5 {
        /*List<ItemStack> items = new ArrayList<>(player.getInventory().getNonEquipmentItems());
        items.add(player.getOffhandItem());
        *///?} else {
        List<ItemStack> items = new ArrayList<>(player.getInventory().items);
        items.addAll(player.getInventory().offhand);
        //?}
        return items;
    }

    /** Whether a player may use commands of this operator level. */
    public static boolean hasPermission(Player player, int level) {
        //? if >=1.21.11 {
        /*return player.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        *///?} else {
        return player.hasPermissions(level);
        //?}
    }

    /** Whether a command source may use commands of this operator level. */
    public static boolean hasPermission(CommandSourceStack source, int level) {
        //? if >=1.21.11 {
        /*return source.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(
                net.minecraft.server.permissions.PermissionLevel.byId(level)));
        *///?} else {
        return source.hasPermission(level);
        //?}
    }

    /** Whether the player is the one who opened this singleplayer (or LAN) world. */
    public static boolean isSingleplayerOwner(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        //? if >=1.21.9 {
        /*return server.isSingleplayerOwner(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
        *///?} else {
        return server.isSingleplayerOwner(player.getGameProfile());
        //?}
    }

    /** A message over the hotbar (unconfirmed: 26.1). */
    public static void actionBar(Player player, Component message) {
        //? if >=26.1 {
        /*player.sendOverlayMessage(message);
        *///?} else {
        player.displayClientMessage(message, true);
        //?}
    }

    /** A message over the hotbar, or in the chat. */
    public static void tell(Player player, Component message, boolean overHotbar) {
        if (overHotbar) {
            actionBar(player, message);
        } else {
            player.sendSystemMessage(message);
        }
    }

    /** Clicking the text puts the command into the chat box. */
    public static ClickEvent suggestCommand(String command) {
        //? if >=1.21.5 {
        /*return new ClickEvent.SuggestCommand(command);
        *///?} else {
        return new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        //?}
    }

    /**
     * Clicking the text runs the command, at once, as on 1.20.1. From 1.21.6 (unconfirmed) the client
     * asks first whenever a clicked command needs a permission (all of the mod's panels), so from then
     * the click is a custom action that the server runs as the player who clicked
     * ({@code ServerCommonPacketListenerImplMixin}, {@code MobStackerCommands.runClicked}).
     */
    public static ClickEvent runCommand(String command) {
        //? if >=1.21.6 {
        /*return new ClickEvent.Custom(CLICKED_COMMAND, java.util.Optional.of(net.minecraft.nbt.StringTag.valueOf(command)));
        *///?} else if >=1.21.5 {
        /*return new ClickEvent.RunCommand(command);
        *///?} else {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //?}
    }

    /** Hovering the text shows this. */
    public static HoverEvent showText(Component text) {
        //? if >=1.21.5 {
        /*return new HoverEvent.ShowText(text);
        *///?} else {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //?}
    }

    /** The entity's scoreboard tags ({@code /tag}). */
    public static Set<String> tags(Entity entity) {
        //? if >=1.21.5 {
        /*return entity.entityTags();
        *///?} else {
        return entity.getTags();
        //?}
    }

    /** Makes an entity immune to everything but /kill (unconfirmed: 26.1). */
    public static void setInvulnerable(Entity entity) {
        //? if >=26.1 {
        /*entity.setPermanentlyInvulnerable(true);
        *///?} else {
        entity.setInvulnerable(true);
        //?}
    }

    /** Whether an armor stand shows no base plate. */
    public static boolean hasNoBasePlate(ArmorStand stand) {
        //? if >=1.21.5 {
        /*return !stand.showBasePlate();
        *///?} else {
        return stand.isNoBasePlate();
        //?}
    }

    /** Whether the item is a sword. From 1.21.5 there is no sword class, only the tag. */
    public static boolean isSword(ItemStack stack) {
        //? if >=1.21.5 {
        /*return stack.is(net.minecraft.tags.ItemTags.SWORDS);
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.SwordItem;
        //?}
    }

    /**
     * Vanilla's "standing still enough to sweep": how far the player walked this tick against its speed,
     * and from 1.21.2 (unconfirmed) its known movement against 2.5 times its speed.
     */
    public static boolean stillEnoughToSweep(Player player) {
        //? if >=1.21.2 {
        /*return player.getKnownMovement().horizontalDistanceSqr()
                < net.minecraft.util.Mth.square(player.getSpeed() * 2.5);
        *///?} else {
        return player.walkDist - player.walkDistO < (double) player.getSpeed();
        //?}
    }

    /** Gives the killer the kill score for one more of this mob. */
    public static void awardKillScore(LivingEntity killer, LivingEntity killed, DamageSource source) {
        //? if >=1.21.2 {
        /*killer.awardKillScore(killed, source);
        *///?} else {
        if (killed.deathScore >= 0) {
            killer.awardKillScore(killed, killed.deathScore, source);
        }
        //?}
    }

    /** Sends a packet to every player who sees the entity (unconfirmed: 1.21.2). */
    public static void sendToTracking(ServerLevel level, Entity entity, Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener> packet) {
        //? if >=1.21.2 {
        /*level.getChunkSource().sendToTrackingPlayers(entity, packet);
        *///?} else {
        level.getChunkSource().broadcast(entity, packet);
        //?}
    }

    /** A colour as a number for the wire (unconfirmed: 26.1, where ChatFormatting lost its id). */
    public static int formatId(ChatFormatting format) {
        //? if >=26.1 {
        /*return format.ordinal();
        *///?} else {
        return format.getId();
        //?}
    }

    /** A text colour's RGB, or null for a format that is no colour (bold, reset...). */
    public static Integer rgb(ChatFormatting format) {
        net.minecraft.network.chat.TextColor color = net.minecraft.network.chat.TextColor.fromLegacyFormat(format);
        return color == null ? null : color.getValue();
    }

    /** The colour {@link #formatId} gave, or null. */
    public static ChatFormatting formatById(int id) {
        //? if >=26.1 {
        /*return id >= 0 && id < ChatFormatting.values().length ? ChatFormatting.values()[id] : null;
        *///?} else {
        return ChatFormatting.getById(id);
        //?}
    }

    /** A new entity of this type to load saved data into (a copy of a stack member). */
    public static <T extends Entity> T createForLoad(EntityType<T> type, Level level) {
        //? if >=1.21.2 {
        /*return type.create(level, MobSpawnType.LOAD);
        *///?} else {
        return type.create(level);
        //?}
    }

    /** The colour a dye item gives, or null when the item is no dye (unconfirmed: 26.1, a component). */
    public static net.minecraft.world.item.DyeColor dyeColor(ItemStack stack) {
        //? if >=26.1 {
        /*return stack.get(net.minecraft.core.component.DataComponents.DYE);
        *///?} else {
        return stack.getItem() instanceof net.minecraft.world.item.DyeItem dye ? dye.getDyeColor() : null;
        //?}
    }
}
