package work.lclpnet.notica.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public interface ClientJoinGameCallback {

    Event<ClientJoinGameCallback> EVENT = EventFactory.createArrayBacked(ClientJoinGameCallback.class, callbacks -> networkHandler -> {
        for (var callback : callbacks) {
            callback.onJoin(networkHandler);
        }
    });

    void onJoin(ClientPlayNetworkHandler networkHandler);
}
