package com.p1nero.cataclysm_dimension.entity;

import com.p1nero.cataclysm_dimension.CataclysmDimensionMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public class CDEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, CataclysmDimensionMod.MOD_ID);

    // 归返裂隙:大 boss 死后在死亡点开启,右键离开维度。静止 MISC 实体,防火(顾及下界主题维度)。
    @SuppressWarnings("deprecation")
    public static final RegistryObject<EntityType<ReturnRiftEntity>> RETURN_RIFT =
            ENTITY_TYPES.register("return_rift", () -> EntityType.Builder.<ReturnRiftEntity>of(ReturnRiftEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("return_rift"));
}
