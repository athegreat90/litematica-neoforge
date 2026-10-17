package team.cagayakegirls.forgematica;

import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.compat.modmenu.ModMenuImpl;
import fi.dy.masa.litematica.network.ServuxLitematicaHandler;
import fi.dy.masa.litematica.network.ServuxLitematicaPacket;
import fi.dy.masa.litematica.render.LitematicaPipelines;
import fi.dy.masa.malilib.network.IPluginClientPlayHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = Forgematica.MOD_ID, dist = Dist.CLIENT)
public class Forgematica {
    public static final String MOD_ID = "forgematica";

    public Forgematica(IEventBus modEventBus, ModContainer modContainer) {
        if (FMLLoader.getCurrent().getDist().isClient()) {
            modEventBus.addListener(LitematicaPipelines::registerPipelines);
            modContainer.registerExtensionPoint(IConfigScreenFactory.class, new ModMenuImpl().getModConfigScreenFactory());

            // MaFgLib 0.6.0 registers payloads through NeoForge's RegisterPayloadHandlersEvent, so the Servux channel
            // has to be registered while the mod is being constructed, not later from the game-init handler.
            ServuxLitematicaHandler.getInstance().registerPlayPayload(ServuxLitematicaPacket.Payload.ID,
                                                                      ServuxLitematicaPacket.Payload.CODEC,
                                                                      IPluginClientPlayHandler.BOTH_CLIENT);

            new Litematica().onInitialize();
        }
    }
}
