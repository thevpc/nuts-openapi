package net.thevpc.nuts.toolbox.noapi.model;

import java.util.List;
import java.util.Map;

public class MStoreModel {
    private String title;

    private String version;

    private List<DocItemInfo> multiDocuments;

    private String id;

    private List<MVar> variables;

    private List<MVar> configVariables;

    private String description;

    private String configDescription;

    private MContact contact;

    private List<MChangeLog> changeLogs;

    private List<MHeader> headers;

    private List<MSecurityScheme> securitySchemes;

    private List<MServer> servers;

    private Map<String, TypeInfo> typesMap;

    private List<MPath> paths;
    private List<TypeCrossRef> typeCrossRefs;

    public List<TypeCrossRef> getTypeCrossRefs() {
        return typeCrossRefs;
    }

    public String getTitle() {
        return title;
    }

    public MStoreModel setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public MStoreModel setVersion(String version) {
        this.version = version;
        return this;
    }

    public List<DocItemInfo> getMultiDocuments() {
        return multiDocuments;
    }

    public MStoreModel setMultiDocuments(List<DocItemInfo> multiDocuments) {
        this.multiDocuments = multiDocuments;
        return this;
    }

    public String getId() {
        return id;
    }

    public MStoreModel setId(String id) {
        this.id = id;
        return this;
    }

    public List<MVar> getVariables() {
        return variables;
    }

    public MStoreModel setVariables(List<MVar> variables) {
        this.variables = variables;
        return this;
    }

    public List<MVar> getConfigVariables() {
        return configVariables;
    }

    public MStoreModel setConfigVariables(List<MVar> configVariables) {
        this.configVariables = configVariables;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public MStoreModel setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getConfigDescription() {
        return configDescription;
    }

    public MStoreModel setConfigDescription(String configDescription) {
        this.configDescription = configDescription;
        return this;
    }

    public MContact getContact() {
        return contact;
    }

    public MStoreModel setContact(MContact contact) {
        this.contact = contact;
        return this;
    }

    public List<MChangeLog> getChangeLogs() {
        return changeLogs;
    }

    public MStoreModel setChangeLogs(List<MChangeLog> changeLogs) {
        this.changeLogs = changeLogs;
        return this;
    }

    public List<MHeader> getHeaders() {
        return headers;
    }

    public MStoreModel setHeaders(List<MHeader> headers) {
        this.headers = headers;
        return this;
    }

    public List<MSecurityScheme> getSecuritySchemes() {
        return securitySchemes;
    }

    public MStoreModel setSecuritySchemes(List<MSecurityScheme> securitySchemes) {
        this.securitySchemes = securitySchemes;
        return this;
    }

    public List<MServer> getServers() {
        return servers;
    }

    public MStoreModel setServers(List<MServer> servers) {
        this.servers = servers;
        return this;
    }

    public Map<String, TypeInfo> getTypesMap() {
        return typesMap;
    }

    public MStoreModel setTypesMap(Map<String, TypeInfo> typesMap) {
        this.typesMap = typesMap;
        return this;
    }

    public List<MPath> getPaths() {
        return paths;
    }

    public MStoreModel setPaths(List<MPath> paths) {
        this.paths = paths;
        return this;
    }

    public MStoreModel setTypeCrossRefs(List<TypeCrossRef> typeCrossRefs) {
        this.typeCrossRefs = typeCrossRefs;
        return this;
    }
}
