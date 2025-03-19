package net.thevpc.nuts.toolbox.noapi.store.swagger;

import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.toolbox.noapi.util._StringUtils;
import net.thevpc.nuts.util.NBlankable;
import net.thevpc.nuts.util.NOptional;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class SwaggerStore implements NoApiStore {
    private NElement apiElement;
    private List<TypeCrossRef> typeCrossRefs = new ArrayList<>();

    public SwaggerStore(NPath source) {
        boolean json = false;
//        Path sourcePath = Paths.get(source).normalize().toAbsolutePath();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(source.getInputStream()))) {
            String t;
            while ((t = r.readLine()) != null) {
                t = t.trim();
                if (t.length() > 0) {
                    if (t.startsWith("{")) {
                        json = true;
                    }
                    break;
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        if (json) {
            this.apiElement = NElements.of().json().parse(source, NElement.class);
        } else {
//            return NElements.of().json().parse(inputStream, NutsElement.class);
            try (InputStream is = source.getInputStream()) {
                final Object o = new Yaml().load(is);
                this.apiElement = NElements.of().toElement(o);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }

    public SwaggerStore(NElement apiElement) {
        this.apiElement = apiElement;
    }

    @Override
    public MConf loadConfigFile(NPath cf) {
        NObjectElement obj = NElements.of().parse(cf).asObject().get();
        MConf c = new MConf();
        c.targetName = obj.getString("target-name").get();
        c.targetId = obj.getString("target-id").get();
        c.documentId = obj.getString("openapi-document-id").get();
        c.version = obj.getString("observations").orElse("");
        c.variables = new ArrayList<>();
        for (NPairElement srv : obj.getObjectByPath("variables").orElse(NObjectElement.ofEmpty()).pairs().collect(Collectors.toList())) {
            String id = srv.key().asString().get();
            String value = srv.value().asObject().get().getString("value").get();
            String observations = srv.value().asObject().get().getString("observations").get();
            c.variables.add(new MVar(id, id, id, new MExample[0], value, observations));
        }
        return c;
    }

    @Override
    public Map<Object, Object> loadVars(String varsPath) {
        if (!NBlankable.isNonBlank(varsPath)) {
            if (NPath.of(varsPath).isRegularFile()) {
                return NElements.of().parse(NPath.of(varsPath), Map.class);
            } else if (NPath.of(varsPath + ".json").isRegularFile()) {
                return NElements.of().parse(NPath.of(varsPath + ".json"), Map.class);
            } else {
                return new HashMap<>();
            }
        }
        return new HashMap<>();
    }

    @Override
    public List<TypeCrossRef> typeCrossRefs() {
        return typeCrossRefs;
    }

    public NOptional<String> getVersion() {
        return _info().getString("version");
    }

    @Override
    public NOptional<String> getDescription() {
        return _info().getString("description");
    }

    @Override
    public NOptional<String> getConfigDescription() {
        return _info().getStringByPath("custom", "config", "description");
    }

    @Override
    public NOptional<MContact> getContact() {
        NOptional<NObjectElement> c = _info().getObject("contact");
        if (!c.isPresent()) {
            return NOptional.ofNamedEmpty("No contact found");
        }
        NObjectElement contact = c.orElse(NObjectElement.ofEmpty());
        MContact m = new MContact();
        m.email = contact.getString("name").orElse("");
        m.url = contact.getString("url").orElse("");
        m.name = contact.getString("name").orElse("");
        return NOptional.of(m);
    }

    private NObjectElement _info() {
        return apiElement.asObject().get().getObject("info").orElse(NElements.of().ofEmptyObject());
    }

    @Override
    public NOptional<String> getTitle() {
        return _info().getString("title");
    }

    public List<DocItemInfo> getMultiDocuments() {
        NObjectElement multiDocument = apiElement.asObject().get().getObjectByPath("custom", "multi-document").orElse(NElements.of().ofEmptyObject());
        List<DocItemInfo> docInfos = new ArrayList<>();
        for (NPairElement entry : multiDocument.pairs().collect(Collectors.toList())) {
            DocItemInfo d = new DocItemInfo();
            d.id = entry.key().asString().get();
            d.raw = entry.value().asObject().orElse(NElements.of().ofEmptyObject());
            for (NPairElement nPairElement : d.raw.get("variables").orElse(NElements.of().ofEmptyObject()).asObject().get().pairs().collect(Collectors.toList())) {
                d.vars.put(String.valueOf(nPairElement.key()), String.valueOf(nPairElement.value()));
            }
            docInfos.add(d);
        }
        return docInfos;
    }

    @Override
    public String getId() {
        return _root().getStringByPath("custom", "openapi-document-id").get();
    }

    @Override
    public List<MVar> findConfigVariables() {
        List<MVar> all = new ArrayList<>();
        for (NPairElement srv : apiElement.asObject().get().getObjectByPath("custom", "config", "variables").orElse(NObjectElement.ofEmpty()).pairs().collect(Collectors.toList())) {
            String id = srv.key().asString().get();
            String name = (srv.value().asObject().get().getString("name").get());
            Object example = (srv.value().asObject().get().get("example").orNull());
            String description = (srv.value().asObject().get().getString("description").get());

            all.add(new MVar(id, name, description, example == null ? new MExample[0] : new MExample[]{new MExample(null, example)}, null, null));
        }
        return all;
    }

    @Override
    public List<MVar> findVariables() {
        List<MVar> all = new ArrayList<>();
        for (NPairElement srv : apiElement.asObject().get().getObjectByPath("custom", "variables").orElse(NObjectElement.ofEmpty()).pairs().collect(Collectors.toList())) {
            String name = srv.key().asString().get();
            String value = (srv.value().asString().get());
            all.add(new MVar(name, name, name, new MExample[0], value, null));
        }
        return all;
    }

    @Override
    public List<MChangeLog> findChangeLogs() {
        NArrayElement changeLog = _info().getArray("changes").orElse(NArrayElement.ofEmpty());
        return changeLog.stream().map(x -> {
            NObjectElement o = x.asObject().get();
            MChangeLog m = new MChangeLog(
                    o.getString("date").orElse(""),
                    o.getString("version").orElse(""),
                    o.getString("title").orElse(""),
                    o.getString("observations").orElse("")
            );
            for (NElement item : o.getArray("details").orElse(NArrayElement.ofEmpty())) {
                m.details.add(item.asString().get());
            }
            return m;
        }).collect(Collectors.toList());
    }

    @Override
    public List<MHeader> findHeaders() {
        NObjectElement components = _root().getObject("components").orElse(NObjectElement.ofEmpty());
        return _parseHeaderList(components.getObject("headers").orElse(NObjectElement.ofEmpty()));
    }

    private List<MHeader> _parseHeaderList(NElement rr) {
        return _parseHeaderList(rr.asObject().orElse(NObjectElement.ofEmpty()).stream().collect(Collectors.toList()), null, "Header");
    }

    private List<MHeader> _parseHeaderList(List<NElement> rr, String url, String paramType) {
        List<MHeader> all = new ArrayList<>();
        for (NElement item : rr) {
            NPairElement ee = item.asPair().get();
            MHeader h = new MHeader();
            h.name = ee.key().toString();
            NObjectElement vobj = ee.value().asObject().get();
            h.deprecated = vobj.getBoolean("deprecated").orElse(false);
            h.required = vobj.getBoolean("required").orElse(false);
            h.typeName = vobj.getString("type")
                    .orElseUse(() ->
                            vobj.getObject("schema")
                                    .orElse(NObjectElement.ofEmpty())
                                    .getString("type")
                    )
                    .orElse("");
            h.description = vobj.getString("description").orElse("");
            NElement example = vobj.get("example").orNull();
            if (example != null) {
                h.examples.add(new MExample(null, example));
            }
            h.smartTypeName = getSmartTypeName(vobj);
            if (url != null) {
                typeCrossRefs.add(new TypeCrossRef(
                        h.typeName, url, paramType
                ));
            }
            all.add(h);
        }
        return all;
    }

    private String getSmartTypeName(NObjectElement obj) {
        String e = _StringUtils.nvl(obj.getString("type").orNull(), "string");
        if ("array".equals(e)) {
            NObjectElement items = obj.getObject("items").orNull();
            if (items != null) {
                return getSmartTypeName(items) + "[]";
            } else {
                return e;
            }
        } else {
            return e;
        }
    }

    private NObjectElement _root() {
        return apiElement.asObject().get();
    }

    @Override
    public List<MSecurityScheme> findSecuritySchemes() {
        NObjectElement components = _root().getObject("components").orElse(NObjectElement.ofEmpty());
        NObjectElement securitySchemes = components.getObject("securitySchemes").orElse(NObjectElement.ofEmpty());
        List<MSecurityScheme> all = new ArrayList<>();
        for (NElement item : securitySchemes) {
            NPairElement ee = item.asPair().get();
            NObjectElement eev = ee.value().asObject().get();
            String type = eev.getString("type").orElse("");
            MSecurityScheme r = new MSecurityScheme();
            r.typeName = type;
            r.id = ee.key().toString();
            r.name = eev.getString("name").orElse("");
            r.description = eev.getString("description").orElse("");
            r.in = eev.getString("in").orElse("");
            r.openIdConnectUrl = eev.getString("openIdConnectUrl").orElse("");
            r.scheme = eev.getString("scheme").orElse("");
            r.bearerFormat = eev.getString("bearerFormat").orElse("");
            switch (type) {
                case "apiKey": {
                    r.type = MSecurityScheme.Type.apiKey;
                    break;
                }
                case "http": {
                    r.type = MSecurityScheme.Type.http;
                    break;
                }
                case "oauth2": {
                    r.type = MSecurityScheme.Type.oauth2;
                    break;
                }
                case "openIdConnect": {
                    r.type = MSecurityScheme.Type.openIdConnect;
                    break;
                }
                default: {
                    r.type = MSecurityScheme.Type.other;
                }
            }
            all.add(r);
        }
        return all;
    }

    @Override
    public List<MServer> findServers() {
        List<MServer> all = new ArrayList<>();
        for (NElement srv : _root().getArray("servers").orElse(NElements.of().ofEmptyArray())) {
            MServer r = new MServer();
            NObjectElement srvObj = (NObjectElement) srv.asObject().orElse(NElements.of().ofEmptyObject());

            r.url = srvObj.getString("url").orNull();
            r.description = srvObj.getString("description").orNull();
            r.variables = new ArrayList<>();

            NElement vars = srvObj.get("variables").orNull();
            if (vars != null && !vars.isEmpty()) {
                for (NElement item : vars.asObject().get()) {
                    NPairElement v = item.asPair().get();
                    MVar cv = new MVar();
                    cv.id = v.key().asString().get();
                    cv.name = v.key().asString().get();
                    cv.value = v.value().asObject().get().getString("default").orElse("");
                    cv.description = v.value().asObject().get().getString("description").orNull();
                    r.variables.add(cv);
                }
            }
            all.add(r);
        }
        return all;

    }

    @Override
    public Map<String, TypeInfo> findTypesMap() {
        return new OpenApiParser().parseTypes(_root());
    }

    @Override
    public List<MPath> findPaths() {
        NObjectElement paths = _root().get("paths").orElse(NElements.of().ofEmptyObject()).asObject().get();
        return paths.stream().map(x -> {
            NPairElement pair = (NPairElement) x.asPair().get();
            MPath p = new MPath();
            p.url = pair.key().asString().get();
            NArrayElement dparameters = null;
            for (NElement sub : pair.value().asObject().get()) {
                NPairElement v = sub.asPair().get();
                switch (v.key().asString().get()) {
                    case "enabled": {
                        p.enabled = v.value().asString().orElse(null);
                        break;
                    }
                    case "summary": {
                        p.summary = v.value().asString().orElse(null);
                        break;
                    }
                    case "description": {
                        p.description = v.value().asString().orElse(null);
                        break;
                    }
                    case "parameters": {
                        dparameters = v.value().asArray().get();
                        break;
                    }
                }
            }
            p.calls = new ArrayList<>();
            for (NElement sub : pair.value().asObject().get()) {
                NPairElement v = sub.asPair().get();
                switch (v.key().asString().get()) {
                    case "enabled":
                    case "summary":
                    case "description": {
                        break;
                    }
                    default: {
                        MCall call = _fillApiPathMethod(
                                v.value().asObject().orElse(NElements.of().ofEmptyObject()),
                                v.key().asString().get(),
                                p.url,
                                dparameters
                        );
                        p.calls.add(call);
                    }
                }
            }
            return p;
        }).collect(Collectors.toList());
    }

    MCall _fillApiPathMethod(NObjectElement call, String method, String url,
                             NArrayElement dparameters) {
        Map<String, TypeInfo> allTypes = findTypesMap();
        NObjectElement schemas = _root().getObjectByPath("components", "schemas").orNull();
        MCall cc = new MCall();
        cc.method = method;
        cc.summary = call.getString("summary").orNull();
        cc.description = call.getString("description").orNull();
        NArrayElement parameters = call.getArray("parameters")
                .orElseUse(() -> NOptional.of(dparameters))
                .orElseGet(() -> NArrayElementBuilder.of().build());
        cc.headerParameters = _parseHeaderList(parameters.stream().filter(x -> "header".equals(x.asObject().get().getString("in").orNull())).collect(Collectors.toList()), url, "Header Parameter");
        cc.queryParameters = _parseHeaderList(parameters.stream().filter(x -> "query".equals(x.asObject().get().getString("in").orNull())).collect(Collectors.toList()), url, "Query Parameter");
        cc.pathParameters = _parseHeaderList(parameters.stream().filter(x -> "path".equals(x.asObject().get().getString("in").orNull())).collect(Collectors.toList()), url, "Path Parameter");
        NObjectElement requestBody = call.getObject("requestBody").orNull();
        if (requestBody != null && !requestBody.isEmpty()) {
            cc.requestBody = new MCall.RequestBody();
            NObjectElement r = requestBody.getObject("content").orElseGet(() -> NObjectElement.ofEmpty());
            cc.requestBody.required = requestBody.getBoolean("required").orElse(false);
            cc.requestBody.description = requestBody.getString("description").orElse("");
            cc.requestBody.contents = new ArrayList<>();
            for (NElement item : r) {
                NPairElement ii = item.asPair().get();
                MCall.Content vv = new MCall.Content();
                vv.contentType = ii.key().asString().get();
                NObjectElement iiv = ii.value().asObject().get();
                TypeInfo o = new OpenApiParser().parseOneType(iiv, null, allTypes);
                String description = iiv.getString("description").orNull();
                Object example = iiv.get("example").orNull();
                vv.type = o;
                cc.requestBody.contents.add(vv);
                if (o.getRef() != null) {
                    NElement s = schemas.get(o.getRef()).orNull();
                    if (example == null && s != null) {
                        example = s.asObject().get().get("example").orNull();
                    }
                    if (description == null && s != null) {
                        description = s.asObject().get().getString("description").orNull();
                    }
                }
                if(example!=null) {
                    vv.examples.add(new MExample(null,example));
                }
                vv.description = description;
            }
        }
        cc.responses = new ArrayList<>();
        call.getObject("responses").get().stream()
                .forEach(item -> {
                    NPairElement x = item.asPair().get();
                    NElement s = x.key();
                    NElement v = x.value();
                    MCall.Response response = new MCall.Response();
                    cc.responses.add(response);
                    response.code = s.toString();
                    response.description = v.asObject().get().getString("description").orElse("");
                    response.contents = new ArrayList<>();
                    for (NElement icontent : v.asObject().get().getObject("content").orElse(NObjectElement.ofEmpty())) {
                        NPairElement content = icontent.asPair().get();
                        TypeInfo o = new OpenApiParser().parseOneType(content.value().asObject().get(), null, allTypes);
                        MCall.Content respContent = new MCall.Content();
                        response.contents.add(respContent);
                        if (o.getUserType().equals("$ref")) {
                            typeCrossRefs.add(new TypeCrossRef(
                                    o.getRef(),
                                    url, "Response (" + s + ")"
                            ));
                            respContent.contentType = content.key().asString().get();
                            respContent.type = o;
                            respContent.typeName = o.getRef();
                            respContent.examples.addAll(o.getExamples());
                        } else {
                            respContent.contentType = content.key().asString().get();
                            respContent.type = o;
                            respContent.typeName = o.getRef();
                            respContent.examples.addAll(o.getExamples());
                        }
                    }
                });
        return cc;
    }
}
