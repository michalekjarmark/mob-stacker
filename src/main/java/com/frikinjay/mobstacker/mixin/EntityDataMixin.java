package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.ICustomDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.frikinjay.mobstacker.MobStacker.STACK_DATA_KEY;

@Mixin(Entity.class)
public class EntityDataMixin implements ICustomDataHolder {

    @Unique
    private CompoundTag mobstacker$stackData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mobstacker$onConstruct(CallbackInfo ci) {
        this.mobstacker$stackData = new CompoundTag();
    }

    /**
     * Puts a copy of the stack data in the save, never the live compound. A chunk's entities are
     * written to disk on the storage thread while the server goes on ticking them, and the live one
     * changes every tick (a separation grace counting down and then removed, a wound, the size), so
     * the writer could read it mid-change - a ConcurrentModificationException and a chunk that failed
     * to save. Forge copies its own ForgeData for the same reason. And a caller that edits the save,
     * as a merge does, no longer edits the mob behind its back.
     */
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void mobstacker$onSaveWithoutId(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        compound.put(STACK_DATA_KEY, this.mobstacker$stackData.copy());
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void mobstacker$onLoad(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains(STACK_DATA_KEY, 10)) {
            this.mobstacker$stackData = compound.getCompound(STACK_DATA_KEY);
        }
    }

    @Override
    public CompoundTag mobstacker$getCustomData() {
        return this.mobstacker$stackData;
    }
}