package work.lclpnet.notica.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public interface SongVolumeChangedCallback {

    Event<SongVolumeChangedCallback> EVENT = EventFactory.createArrayBacked(SongVolumeChangedCallback.class, callbacks -> () -> {
        for (var callback : callbacks) {
            callback.onVolumeChanged();
        }
    });

    void onVolumeChanged();
}
