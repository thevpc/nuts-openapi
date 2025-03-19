package net.thevpc.nuts.toolbox.noapi.store;

import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.util.NOptional;

import java.util.List;
import java.util.Map;

public interface NoApiStore {
    NOptional<String> getTitle();

    NOptional<String> getVersion();

    List<DocItemInfo> getMultiDocuments();

    String getId();

    List<MVar> findVariables();

    List<MVar> findConfigVariables();

    NOptional<String> getDescription();

    NOptional<String> getConfigDescription();

    NOptional<MContact> getContact();

    List<MChangeLog> findChangeLogs();

    List<MHeader> findHeaders();

    List<MSecurityScheme> findSecuritySchemes();

    List<MServer> findServers();

    Map<String, TypeInfo> findTypesMap();

    List<MPath> findPaths();

    List<TypeCrossRef> typeCrossRefs();

    MConf loadConfigFile(NPath cf);

    Map<Object, Object> loadVars(String varsPath);
}
