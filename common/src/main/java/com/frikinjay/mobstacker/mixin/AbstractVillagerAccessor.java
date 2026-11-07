package com.frikinjay.mobstacker.mixin;

import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A villager's trades, which vanilla only ever replaces on the client ({@code overrideOffers} is
 * empty on the server). Used when a stacked villager that already took a job hands it to the one
 * member that leaves the stack with it ({@code MobStacker#shedClaims}).
 */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {

    @Accessor("offers")
    MerchantOffers mobstacker$offers();

    @Accessor("offers")
    void mobstacker$setOffers(MerchantOffers offers);
}
