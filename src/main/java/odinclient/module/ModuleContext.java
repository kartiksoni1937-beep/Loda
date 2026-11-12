package odinclient.module;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import odinclient.event.EventBus;
import odinclient.event.SimpleEventBus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class ModuleContext {
    public static final Logger LOGGER = LoggerFactory.getLogger("OdinClient");
    public static final EventBus EVENT_BUS = new SimpleEventBus();

    private ModuleContext() {}
}
