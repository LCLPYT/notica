package work.lclpnet.kibu.nbs.impl;

import work.lclpnet.kibu.nbs.api.ExtendedOctaveRange;

public class BoolExtendedOctaveRange implements ExtendedOctaveRange {

    private boolean supported = false;

    @Override
    public boolean isSupported() {
        return supported;
    }

    public void setSupported(boolean supported) {
        this.supported = supported;
    }
}
