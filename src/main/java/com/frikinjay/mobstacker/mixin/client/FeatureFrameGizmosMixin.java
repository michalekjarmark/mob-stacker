package com.frikinjay.mobstacker.mixin.client;

//? if >=26.3 {
/*import com.frikinjay.mobstacker.client.LateGizmos;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

/^*
 * The translucent gizmos (the region boxes without X-ray) are drawn after the water, the clouds and
 * the weather instead of before the translucent terrain (see {@link LateGizmos}): skipped in
 * {@code executeTranslucent}, drawn from the end of the classic transparency by
 * {@code LevelRendererGizmosMixin}. 26.3's improved transparency never comes here (its gizmos are
 * part of the order-independent pass) and is left as it is.
 ^/
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class FeatureFrameGizmosMixin implements LateGizmos {

    @Shadow
    private FeatureFrameContext context;

    @Shadow
    private SubmitNodeStorage submitNodeStorage;

    @Shadow
    private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext frameContext, RenderPass pass) {
    }

    @Redirect(method = "executeTranslucent",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;translucentGizmos:Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;")),
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executePhase(Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;Lnet/minecraft/client/renderer/feature/FeatureFrameContext;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void mobstacker$gizmosLater(FeatureRenderDispatcher.PreparedFrame frame, FeatureRenderPhase<?> phase,
                                        FeatureFrameContext frameContext, RenderPass pass) {
        // Drawn by mobstacker$executeGizmos, after the weather.
    }

    @Override
    public void mobstacker$executeGizmos(RenderPass pass) {
        FeatureFrameContext frameContext = context;
        SubmitNodeStorage storage = submitNodeStorage;
        if (frameContext == null || storage == null) {
            return;
        }
        for (SubmitNodeCollection collection : storage.getSubmitsPerOrder().values()) {
            executePhase(collection.translucentGizmos, frameContext, pass);
        }
    }
}
*///?} else if >=26.2 {
/*import com.frikinjay.mobstacker.client.LateGizmos;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

import java.util.List;
import java.util.Map;

/^*
 * The gizmos (the region boxes without X-ray) are drawn after the water, the clouds and the weather
 * instead of before the translucent terrain (see {@link LateGizmos}): skipped in
 * {@code executeTranslucent}, drawn at the start of the always-on-top pass by
 * {@code LevelRendererGizmosMixin}, before that pass clears the depth.
 ^/
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public abstract class FeatureFrameGizmosMixin implements LateGizmos {

    @Shadow
    @Final
    private Map<Object, List<?>> groupsByPhase;

    @Shadow
    private FeatureFrameContext context;

    @Shadow
    private SubmitNodeStorage submitNodeStorage;

    @Shadow
    private void executePhase(FeatureRenderPhase<?> phase, FeatureFrameContext frameContext) {
    }

    @Redirect(method = "executeTranslucent",
            slice = @Slice(from = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollection;gizmos:Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;")),
            at = @At(value = "INVOKE", ordinal = 0,
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executePhase(Lnet/minecraft/client/renderer/feature/phase/FeatureRenderPhase;Lnet/minecraft/client/renderer/feature/FeatureFrameContext;)V"))
    private void mobstacker$gizmosLater(FeatureRenderDispatcher.PreparedFrame frame, FeatureRenderPhase<?> phase,
                                        FeatureFrameContext frameContext) {
        // Drawn by mobstacker$executeGizmos, after the weather.
    }

    @Override
    public void mobstacker$executeGizmos() {
        FeatureFrameContext frameContext = context;
        SubmitNodeStorage storage = submitNodeStorage;
        if (frameContext == null || storage == null) {
            return;
        }
        for (SubmitNodeCollection collection : storage.getSubmitsPerOrder().values()) {
            executePhase(collection.gizmos, frameContext);
        }
    }

    @Override
    public boolean mobstacker$hasGizmos() {
        SubmitNodeStorage storage = submitNodeStorage;
        if (storage == null) {
            return false;
        }
        for (SubmitNodeCollection collection : storage.getSubmitsPerOrder().values()) {
            List<?> groups = groupsByPhase.get(collection.gizmos);
            if (groups != null && !groups.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
*///?}
