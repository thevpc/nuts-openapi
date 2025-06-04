package net.thevpc.nuts.toolbox.noapi.store.swagger;

import net.thevpc.nuts.NIllegalArgumentException;
import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.toolbox.noapi.util._StringUtils;
import net.thevpc.nuts.util.*;

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
            this.apiElement = NElementParser.ofJson().parse(source, NElement.class);
        } else {
//            return NElementParser.ofJson().parse(inputStream, NutsElement.class);
            this.apiElement= NElementParser.ofYaml().parse(source);
        }
    }

    public SwaggerStore(NElement apiElement) {
        this.apiElement = apiElement;
    }

    @Override
    public MConf loadConfigFile(NPath cf) {
        NObjectElement obj = NElementParser.ofJson().parse(cf).asObject().get();
        MConf c = new MConf();
        c.targetName = obj.getStringValue("target-name").get();
        c.targetId = obj.getStringValue("target-id").get();
        c.documentId = obj.getStringValue("openapi-document-id").get();
        c.version = obj.getStringValue("observations").orElse("");
        c.variables = new ArrayList<>();
        for (NPairElement srv : obj.getObjectByPath("variables").orElse(NObjectElement.ofEmpty()).pairs()) {
            String id = srv.key().asStringValue().get();
            String value = srv.value().asObject().get().getStringValue("value").get();
            String observations = srv.value().asObject().get().getStringValue("observations").get();
            c.variables.add(new MVar(id, id, id, new MExample[0], value, observations));
        }
        return c;
    }

    @Override
    public Map<Object, Object> loadVars(String varsPath) {
        if (!NBlankable.isNonBlank(varsPath)) {
            if (NPath.of(varsPath).isRegularFile()) {
                return NElementParser.ofJson().parse(NPath.of(varsPath), Map.class);
            } else if (NPath.of(varsPath + ".json").isRegularFile()) {
                return NElementParser.ofJson().parse(NPath.of(varsPath + ".json"), Map.class);
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
        return _info().getStringValue("version");
    }

    @Override
    public NOptional<String> getDescription() {
        return _info().getStringValue("description");
    }

    @Override
    public NOptional<String> getConfigDescription() {
        return _info().getByPath("custom", "config", "description").map(NElement::asLiteral).flatMap(NLiteral::asString);
    }

    @Override
    public NOptional<MContact> getContact() {
        NOptional<NObjectElement> c = _info().getObject("contact");
        if (!c.isPresent()) {
            return NOptional.ofNamedEmpty("No contact found");
        }
        NObjectElement contact = c.orElse(NObjectElement.ofEmpty());
        MContact m = new MContact();
        m.email = contact.getStringValue("name").orElse("");
        m.url = contact.getStringValue("url").orElse("");
        m.name = contact.getStringValue("name").orElse("");
        return NOptional.of(m);
    }

    private NObjectElement _info() {
        return apiElement.asObject().get().getObject("info").orElse(NElement.ofObject());
    }

    @Override
    public NOptional<String> getTitle() {
        return _info().getStringValue("title");
    }

    public List<DocItemInfo> getMultiDocuments() {
        NObjectElement multiDocument = apiElement.asObject().get().getObjectByPath("custom", "multi-document").orElse(NElement.ofObject());
        List<DocItemInfo> docInfos = new ArrayList<>();
        for (NPairElement entry : multiDocument.pairs()) {
            DocItemInfo d = new DocItemInfo();
            d.id = entry.key().asStringValue().get();
            d.raw = entry.value().asObject().orElse(NElement.ofObject());
            for (NPairElement nPairElement : d.raw.get("variables").orElse(NElement.ofObject()).asObject().get().pairs()) {
                d.vars.put(String.valueOf(nPairElement.key()), String.valueOf(nPairElement.value()));
            }
            docInfos.add(d);
        }
        return docInfos;
    }

    @Override
    public String getId() {
        return _root().getByPath("custom", "openapi-document-id").map(NElement::asLiteral).flatMap(NLiteral::asString).get();
    }

    @Override
    public List<MVar> findConfigVariables() {
        List<MVar> all = new ArrayList<>();
        for (NPairElement srv : apiElement.asObject().get().getObjectByPath("custom", "config", "variables").orElse(NObjectElement.ofEmpty()).pairs()) {
            String id = srv.key().asStringValue().get();
            String name = (srv.value().asObject().get().getStringValue("name").get());
            NElement example = (srv.value().asObject().get().get("example").orNull());
            String description = (srv.value().asObject().get().getStringValue("description").get());

            all.add(new MVar(id, name, description, example == null ? new MExample[0] : new MExample[]{new MExample(null, example)}, null, null));
        }
        return all;
    }

    @Override
    public List<MVar> findVariables() {
        List<MVar> all = new ArrayList<>();
        for (NPairElement srv : apiElement.asObject().get().getObjectByPath("custom", "variables").orElse(NObjectElement.ofEmpty()).pairs()) {
            String name = srv.key().asStringValue().get();
            String value = (srv.value().asStringValue().get());
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
                    o.getStringValue("date").orElse(""),
                    o.getStringValue("version").orElse(""),
                    o.getStringValue("title").orElse(""),
                    o.getStringValue("observations").orElse("")
            );
            for (NElement item : o.getArray("details").orElse(NArrayElement.ofEmpty())) {
                m.details.add(item.asStringValue().get());
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
            h.deprecated = vobj.getBooleanValue("deprecated").orElse(false);
            h.required = vobj.getBooleanValue("required").orElse(false);
            h.typeName = vobj.getStringValue("type")
                    .orElseUse(() ->
                            vobj.getObject("schema")
                                    .orElse(NObjectElement.ofEmpty())
                                    .getStringValue("type")
                    )
                    .orElse("");
            h.description = vobj.getStringValue("description").orElse("");
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

    private List<MParam> _parseParamList(List<NElement> rr, String url, String paramType) {
        List<MParam> all = new ArrayList<>();
        for (NElement item : rr) {
            MParam h = new MParam();
            NObjectElement vobj;
            if(item.asPair().isPresent()){
                NPairElement ee = item.asPair().get();
                h.name = ee.key().toString();
                if(ee.value().asObject().isPresent()){
                    vobj=ee.value().asObject().get();
                }else if(ee.value().isNull()){
                    vobj= NElement.ofObject();
                }else{
                    throw new NIllegalArgumentException(NMsg.ofC("expected pair of string:object fo parameters content"));
                }
            }else if(item.asObject().isPresent()){
                NObjectElement ee = item.asObject().get();
                h.name = ee.name().orNull();
                h.name = ee.getStringValue("name").orElse(h.name);
                vobj=ee;
            }else{
                throw new NIllegalArgumentException(NMsg.ofC("expected pair or object fo parameters content"));
            }
            h.in = vobj.getStringValue("in").orNull();
            h.deprecated = vobj.getBooleanValue("deprecated").orElse(false);
            h.required = vobj.getBooleanValue("required").orElse(false);
            h.typeName = vobj.getStringValue("type")
                    .orElseUse(() ->
                            vobj.getObject("schema")
                                    .orElse(NObjectElement.ofEmpty())
                                    .getStringValue("type")
                    )
                    .orElse("");
            h.description = vobj.getStringValue("description").orElse("");
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
        String e = _StringUtils.nvl(obj.getStringValue("type").orNull(), "string");
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
            String type = eev.getStringValue("type").orElse("");
            MSecurityScheme r = new MSecurityScheme();
            r.typeName = type;
            r.id = ee.key().toString();
            r.name = eev.getStringValue("name").orElse("");
            r.description = eev.getStringValue("description").orElse("");
            r.in = eev.getStringValue("in").orElse("");
            r.openIdConnectUrl = eev.getStringValue("openIdConnectUrl").orElse("");
            r.scheme = eev.getStringValue("scheme").orElse("");
            r.bearerFormat = eev.getStringValue("bearerFormat").orElse("");
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
        for (NElement srv : _root().getArray("servers").orElse(NElement.ofArray())) {
            MServer r = new MServer();
            NObjectElement srvObj = (NObjectElement) srv.asObject().orElse(NElement.ofObject());

            r.url = srvObj.getStringValue("url").orNull();
            r.description = srvObj.getStringValue("description").orNull();
            r.variables = new ArrayList<>();

            NElement vars = srvObj.get("variables").orNull();
            if (vars != null && !vars.isEmpty()) {
                for (NElement item : vars.asObject().get()) {
                    NPairElement v = item.asPair().get();
                    MVar cv = new MVar();
                    cv.id = v.key().asStringValue().get();
                    cv.name = v.key().asStringValue().get();
                    cv.value = v.value().asObject().get().getStringValue("default").orElse("");
                    cv.description = v.value().asObject().get().getStringValue("description").orNull();
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
        NObjectElement paths = _root().get("paths").orElse(NElement.ofObject()).asObject().get();
        return paths.stream().map(x -> {
            NPairElement pair = (NPairElement) x.asPair().get();
            MPath p = new MPath();
            p.url = pair.key().asStringValue().get();
            NArrayElement dparameters = null;
            for (NElement sub : pair.value().asObject().get()) {
                NPairElement v = sub.asPair().get();
                switch (v.key().asStringValue().get()) {
                    case "enabled": {
                        p.enabled = v.value().asStringValue().orElse(null);
                        break;
                    }
                    case "summary": {
                        p.summary = v.value().asStringValue().orElse(null);
                        break;
                    }
                    case "description": {
                        p.description = v.value().asStringValue().orElse(null);
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
                switch (NStringUtils.trim(v.key().asStringValue().get()).toLowerCase()) {
                    case "enabled":
                    case "summary":
                    case "description":
                    case "parameters":
                    {
                        break;
                    }
                    case "post":
                    case "get":
                    case "put":
                    case "patch":
                    case "head":
                    case "options":
                    case "trace":
                    case "connect":
                    case "delete":
                    {
                        MCall call = _fillApiPathMethod(
                                v.value().asObject().orElse(NElement.ofObject()),
                                v.key().asStringValue().get(),
                                p.url,
                                dparameters
                        );
                        p.calls.add(call);
                        break;
                    }
                    default: {
                        throw new NIllegalArgumentException(NMsg.ofC("unsupported method '%s' in %s",v.key().asStringValue().get(),p.url));
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
        cc.summary = call.getStringValue("summary").orNull();
        cc.description = call.getStringValue("description").orNull();
        NArrayElement parameters = call.getArray("parameters")
                .orElseGet(() -> NArrayElementBuilder.of().build());

        cc.headerParameters = new ArrayList<>();
        cc.queryParameters = new ArrayList<>();
        cc.pathParameters = new ArrayList<>();

        if(dparameters!=null) {
            cc.headerParameters.addAll(_parseParamList(dparameters.stream().filter(x -> "header".equals(x.asObject().get().getStringValue("in").orNull())).collect(Collectors.toList()), url, "Header Parameter"));
            cc.queryParameters.addAll(_parseParamList(dparameters.stream().filter(x -> "query".equals(x.asObject().get().getStringValue("in").orNull())).collect(Collectors.toList()), url, "Query Parameter"));
            cc.pathParameters.addAll(_parseParamList(dparameters.stream().filter(x -> "path".equals(x.asObject().get().getStringValue("in").orNull())).collect(Collectors.toList()), url, "Path Parameter"));
        }

        cc.headerParameters.addAll(_parseParamList(parameters.stream().filter(x -> "header".equals(x.asObject().get().getStringValue("in").orNull())).collect(Collectors.toList()), url, "Header Parameter"));
        cc.queryParameters.addAll(_parseParamList(parameters.stream().filter(x -> "query".equals(x.asObject().get().getStringValue("in").orNull())).collect(Collectors.toList()), url, "Query Parameter"));
        cc.pathParameters.addAll(_parseParamList(parameters.stream().filter(x -> "path".equals(x.asObject().get().getStringValue("in").orNull())).collect(Collectors.toList()), url, "Path Parameter"));

        NObjectElement requestBody = call.getObject("requestBody").orNull();
        if (requestBody != null && !requestBody.isEmpty()) {
            cc.requestBody = new MCall.RequestBody();
            NObjectElement r = requestBody.getObject("content").orElseGet(() -> NObjectElement.ofEmpty());
            cc.requestBody.required = requestBody.getBooleanValue("required").orElse(false);
            cc.requestBody.description = requestBody.getStringValue("description").orElse("");
            cc.requestBody.contents = new ArrayList<>();
            for (NElement item : r) {
                NPairElement ii = item.asPair().get();
                MCall.Content vv = new MCall.Content();
                vv.contentType = ii.key().asStringValue().get();
                NObjectElement iiv = ii.value().asObject().get();
                TypeInfo o = new OpenApiParser().parseOneType(iiv, null, allTypes);
                String description = iiv.getStringValue("description").orNull();
                NElement example = iiv.get("example").orNull();
                vv.type = o;
                cc.requestBody.contents.add(vv);
                if (o.getRef() != null) {
                    NElement s = schemas.get(o.getRef()).orNull();
                    if (example == null && s != null) {
                        example = s.asObject().get().get("example").orNull();
                    }
                    if (description == null && s != null) {
                        description = s.asObject().get().getStringValue("description").orNull();
                    }
                }
                if(example!=null) {
                    vv.examples.add(new MExample(null,example));
                }
                vv.description = description;
            }
        }
        cc.responses = new ArrayList<>();
        NObjectElement responses = call.getObject("responses").orNull();
        if(responses==null){
            throw new NIllegalArgumentException(NMsg.ofC("Missing 'responses' element in %s",call));
        }
        responses.stream()
                .forEach(item -> {
                    NPairElement x = item.asPair().get();
                    NElement s = x.key();
                    NElement v = x.value();
                    MCall.Response response = new MCall.Response();
                    cc.responses.add(response);
                    response.code = s.toString();
                    response.description = v.asObject().get().getStringValue("description").orElse("");
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
                            respContent.contentType = content.key().asStringValue().get();
                            respContent.type = o;
                            respContent.typeName = o.getRef();
                            respContent.examples.addAll(o.getExamples());
                        } else {
                            respContent.contentType = content.key().asStringValue().get();
                            respContent.type = o;
                            respContent.typeName = o.getRef();
                            respContent.examples.addAll(o.getExamples());
                        }
                    }
                });
        return cc;
    }
}
