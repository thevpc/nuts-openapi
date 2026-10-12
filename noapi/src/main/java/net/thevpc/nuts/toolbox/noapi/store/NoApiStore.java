package net.thevpc.nuts.toolbox.noapi.store;

import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.util.NOptional;

import java.util.List;
import java.util.Map;

public interface NoApiStore {
    /**
     * Builds the NoAPI model from an already parsed (and, for TSON, already desugared) document.
     * The tree must have the same shape as the one currently produced for JSON/YAML documents.
     */
    MStoreModel loadStoreModel(NElement source);

    MConf loadConfigFile(NElement cf);

    Map<Object, Object> loadVars(String varsPath);
}
