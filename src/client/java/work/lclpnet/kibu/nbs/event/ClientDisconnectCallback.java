package work.lclpnet.kibu.nbs.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface ClientDisconnectCallback {

    Event<ClientDisconnectCallback> EVENT = EventFactory.createArrayBacked(ClientDisconnectCallback.class, callbacks -> () -> {
        for (var callback : callbacks) {
            callback.onDisconnected();
        }
    });

    void onDisconnected();
}
