package com.p1nero.cataclysm_dimension.client;

import com.p1nero.cataclysm_dimension.entity.CDEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 客户端专用注册(专用服务端不加载本类)。从主类的 dist.isClient() 分支调用,显式挂 mod 总线。
 */
public final class CataclysmDimensionClient {

    private CataclysmDimensionClient() {
    }

    public static void init(ModContainer container, IEventBus modBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (ignored, parent) -> new DimensionConfigScreen(parent));
        modBus.addListener(CataclysmDimensionClient::onRegisterRenderers);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CDEntities.RETURN_RIFT.get(), ReturnRiftRenderer::new);
    }
}
