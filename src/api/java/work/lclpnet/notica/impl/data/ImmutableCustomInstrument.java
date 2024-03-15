package work.lclpnet.notica.impl.data;

import work.lclpnet.notica.api.data.CustomInstrument;

public record ImmutableCustomInstrument(String name, String soundFile, byte key) implements CustomInstrument {
}
