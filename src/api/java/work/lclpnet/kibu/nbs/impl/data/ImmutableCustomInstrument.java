package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.CustomInstrument;

public record ImmutableCustomInstrument(String name, String soundFile, byte key) implements CustomInstrument {
}
