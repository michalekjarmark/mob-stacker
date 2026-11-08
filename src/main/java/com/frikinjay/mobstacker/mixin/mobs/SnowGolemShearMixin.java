package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;

/**
 * What one snow golem under the top of a stack gives when the stack is sheared, on every loader; see
 * {@link SheepShearMixin} for who decides when.
 */
@Mixin(SnowGolem.class)
public class SnowGolemShearMixin implements StackShearable {

    /**
     * One carved pumpkin for each golem under the top one, dropped the way vanilla drops the top
     * one's. No sound: the top one's shear already made the one a click should.
     */
    @Override
    public void mobstacker$shearMembers(SoundSource source, int count) {
        SnowGolem self = (SnowGolem) (Object) this;
        if (self.level().isClientSide) {
            return;
        }
        for (int i = 0; i < count; i++) {
            self.spawnAtLocation(new ItemStack(Items.CARVED_PUMPKIN), 1.7F);
        }
    }
}
