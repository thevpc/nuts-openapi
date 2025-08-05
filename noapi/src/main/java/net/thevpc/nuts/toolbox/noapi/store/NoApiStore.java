package net.thevpc.nuts.toolbox.noapi.store;

import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.util.NOptional;

import java.util.List;
import java.util.Map;

public interface NoApiStore {
    MStoreModel loadStoreModel(NPath cf);

    MConf loadConfigFile(NPath cf);

    Map<Object, Object> loadVars(String varsPath);
}
