package work.lclpnet.kibu.nbs;

import work.lclpnet.kibu.nbs.impl.KibuNbsApiImpl;

public interface KibuNbsAPI {

    static KibuNbsAPI getInstance() {
        return KibuNbsApiImpl.getInstance();
    }
}
