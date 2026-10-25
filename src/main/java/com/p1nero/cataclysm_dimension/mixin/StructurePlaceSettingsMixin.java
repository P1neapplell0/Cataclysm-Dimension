package com.p1nero.cataclysm_dimension.mixin;

import com.p1nero.cataclysm_dimension.StructureResetManager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructurePlaceSettings.class)
public abstract class StructurePlaceSettingsMixin {
    @Inject(method = "isIgnoreEntities", at = @At("HEAD"), cancellable = true)
    private void rewind$includeSavedEntities(CallbackInfoReturnable<Boolean> cir) {
        if (StructureResetManager.isPlacing()) cir.setReturnValue(false);
    }
}
