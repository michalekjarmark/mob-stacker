package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.StackShearable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/**
 * What one mooshroom under the top of a stack gives when the stack is sheared, on every loader; see
 * {@link SheepShearMixin} for who decides when.
 */
@Mixin(MushroomCow.class)
public class MushroomCowShearMixin implements StackShearable {

    /**
     * Five mushrooms for each member under the top mooshroom. By now vanilla has already turned the
     * top one into a cow (the stack went with it) and dropped its own five; this mooshroom is
     * discarded but still knows its variant, which is all a member's harvest needs.
     */
    @Override
    public void mobstacker$shearMembers(SoundSource source, int count) {
        MushroomCow self = (MushroomCow) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < 5; ++j) {
                self.level().addFreshEntity(new ItemEntity(self.level(), self.getX(), self.getY(1.0), self.getZ(), new ItemStack(self.getVariant().getBlockState().getBlock())));
            }
        }
    }
}
