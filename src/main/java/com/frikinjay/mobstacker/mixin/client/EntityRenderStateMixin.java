package com.frikinjay.mobstacker.mixin.client;

//? if >=26.1 {
/*import com.frikinjay.mobstacker.client.StackLabelState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/^* The stack label on an entity's render state (see EntityRendererMixin). ^/
@Mixin(EntityRenderState.class)
public abstract class EntityRenderStateMixin implements StackLabelState {
    @Unique
    private Component mobstacker$label;
    @Unique
    private Vec3 mobstacker$labelAt;

    @Override
    public Component mobstacker$label() {
        return mobstacker$label;
    }

    @Override
    public void mobstacker$setLabel(Component label) {
        mobstacker$label = label;
    }

    @Override
    public Vec3 mobstacker$labelAt() {
        return mobstacker$labelAt;
    }

    @Override
    public void mobstacker$setLabelAt(Vec3 at) {
        mobstacker$labelAt = at;
    }
}
*///?}
