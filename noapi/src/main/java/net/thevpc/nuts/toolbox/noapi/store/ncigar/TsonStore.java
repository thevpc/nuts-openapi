package net.thevpc.nuts.toolbox.noapi.store.ncigar;

import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.util.NOptional;
import net.thevpc.nuts.util.NStringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class TsonStore implements NoApiStore {

    @Override
    public MStoreModel loadStoreModel(NPath source) {
        NElement root = NElementReader.ofJson().read(source.toFile().get());
        List<TypeCrossRef> typeCrossRefs = new ArrayList<>();
        MStoreModel mStoreModel = new MStoreModel();
        mStoreModel.setTitle(getTitle(root).orNull());
        mStoreModel.setVersion(getVersion(root).orNull());
        mStoreModel.setMultiDocuments(getMultiDocuments());
        mStoreModel.setId(getId(root));
        mStoreModel.setVariables(findVariables(root));
        mStoreModel.setConfigVariables(findConfigVariables(root));
        mStoreModel.setDescription(getDescription(root).orNull());
        mStoreModel.setConfigDescription(getConfigDescription(root).orNull());
        mStoreModel.setContact(getContact(root).orNull());
        mStoreModel.setChangeLogs(findChangeLogs(root));
        mStoreModel.setHeaders(findHeaders(root));
        mStoreModel.setSecuritySchemes(findSecuritySchemes(root));
        mStoreModel.setServers(findServers(root));
        mStoreModel.setTypesMap(findTypesMap(root));
        mStoreModel.setPaths(findPaths());
        mStoreModel.setTypeCrossRefs(typeCrossRefs);
        return mStoreModel;
    }

    public NOptional<String> getTitle(NElement root) {
        return root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("title")).then(x -> x.get().asStringValue().orNull());
    }

    
    public NOptional<String> getVersion(NElement root) {
        return root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("version").orNull()).then(x -> x.asStringValue().orNull());
    }

    
    public List<DocItemInfo> getMultiDocuments() {
        return new ArrayList<>();
    }

    
    public String getId(NElement root) {
        return root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("id").orNull()).then(x -> x.asStringValue().orNull()).get();
    }

    
    public List<MVar> findVariables(NElement root) {
        return parseMVars(
                root.toObject().then(x -> x.get("info").orNull())
                        .then(x -> x.asObject().get().get("variables").orNull()).orNull()
        );
    }

    
    public List<MVar> findConfigVariables(NElement root) {
        return parseMVars(
                root.toObject().then(x -> x.get("info").orNull())
                        .then(x -> x.asObject().get().get("config").orNull())
                        .then(x -> x.asObject().get().get("variables").orNull()).orNull()
        );
    }

    
    public NOptional<String> getDescription(NElement root) {
        return root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("description").orNull()).then(x -> x.asStringValue().orNull());
    }

    
    public NOptional<String> getConfigDescription(NElement root) {
        return root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("config").orNull())
                .then(x -> x.asObject().get().get("description").orNull())
                .then(x -> x.asStringValue().orNull());
    }

    
    public NOptional<MContact> getContact(NElement root) {
        NElement c = root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("contact").orNull()).orNull();
        if (c != null) {
            MContact mc = new MContact();
            mc.name = NOptional.of(c.asObject().get().get("name").orNull()).then(x -> x.asStringValue().orNull()).get();
            mc.email = NOptional.of(c.asObject().get().get("email").orNull()).then(x -> x.asStringValue().orNull()).get();
            mc.url = NOptional.of(c.asObject().get().get("url").orNull()).then(x -> x.asStringValue().orNull()).get();
            return NOptional.of(mc);
        }
        return NOptional.ofNamedEmpty("No contact found");
    }

    
    public List<MChangeLog> findChangeLogs(NElement root) {
        NElement c = root.toObject().then(x -> x.get("info").orNull())
                .then(x -> x.asObject().get().get("changes").orNull()).orNull();
        List<MChangeLog> all = new ArrayList<>();
        if (c instanceof NListContainerElement) {
            for (NElement te : ((NListContainerElement) c).children()) {
                MChangeLog cl = new MChangeLog();
                cl.version = NOptional.of(te.asObject().get().get("version").orNull()).then(x -> x.asStringValue().orNull()).get();
                cl.date = NOptional.of(te.asObject().get().get("date").orNull()).then(x -> x.asStringValue().orNull()).get();
                cl.observations = NOptional.of(te.asObject().get().get("observations").orNull()).then(x -> x.asStringValue().orNull()).get();
                cl.title = NOptional.of(te.asObject().get().get("title").orNull()).then(x -> x.asStringValue().orNull()).get();
                cl.enabled = NOptional.of(te.asObject().get().get("enabled").orNull()).then(x -> x.asStringValue().orNull()).get();
                cl.details = new ArrayList<>();
                NListContainerElement details = NOptional.of(te.asObject().get().get("details").orNull()).then(x -> x.toListContainer().orNull()).orNull();
                if (details != null) {
                    for (NElement d : details.children()) {
                        cl.details.add(d.asStringValue().orNull());
                    }
                }
                all.add(cl);
            }
        }
        return all;
    }

    
    public List<MHeader> findHeaders(NElement root) {
        List<MHeader> all = new ArrayList<>();
        NObjectElement components = root.toObject().then(x -> x.get("components"))
                .then(x -> x.get().asObject().orNull()).orNull();
        if (components != null) {
            for (NElement cc : components) {
                switch (cc.type()) {
                    case NAMED_UPLET: {
                        NUpletElement u = cc.asUplet().get();
                        if (u.name().orElse("").equals("header")) {
                            MHeader h = new MHeader();
                            for (NElement param : u.params()) {
                                if (param.isString() && h.name == null) {
                                    h.name = param.asStringValue().orNull();
                                    h.typeName = "string";
                                    h.smartTypeName = "string";
                                } else if (param.isPair()) {
                                    NPairElement p = param.asPair().get();
                                    if (h.name == null) {
                                        h.name = p.key().asStringValue().orNull();

                                        h.typeName = p.value().toString();
                                        h.smartTypeName = p.value().toString();
                                    } else {
                                        throw new IllegalArgumentException("unsupported " + param);
                                    }
                                }
                            }
                            if (h.description == null) {
                                h.description = resolveComments(cc);
                            }
                            all.add(h);
                        }
                    }
                }
            }
        }
        return all;
    }

    
    public List<MSecurityScheme> findSecuritySchemes(NElement root) {
        List<MSecurityScheme> all = new ArrayList<>();
        NObjectElement components = root.toObject().flatMap(x -> x.get("components"))
                .flatMap(x -> x.asObject()).orNull();
        if (components != null) {
            for (NElement cc : components) {
                switch (cc.type()) {
                    case NAMED_UPLET: {
                        NUpletElement u = cc.asUplet().get();
                        if (u.name().orElse("").equals("securityScheme")) {
                            MSecurityScheme h = new MSecurityScheme();
                            all.add(h);
                            for (NElement param : u.params()) {
                                if (param.isString() && h.name == null) {
                                    h.name = param.asStringValue().orNull();
                                } else if (param.isPair()) {
                                    NPairElement p2 = param.asPair().get();
                                    switch (p2.key().toString()) {
                                        case "name": {
                                            h.name = p2.value().toString();
                                            break;
                                        }
                                        case "description": {
                                            h.description = p2.value().toString();
                                            break;
                                        }
                                    }
                                } else if (param.isNamedUplet()) {
                                    NUpletElement vu = param.asUplet().get();
                                    h.typeName = NStringUtils.trim(vu.name().orNull());
                                    switch (h.typeName) {
                                        case "apiKey": {
                                            h.type = MSecurityScheme.Type.apiKey;
                                            break;
                                        }
                                        case "http": {
                                            h.type = MSecurityScheme.Type.http;
                                            break;
                                        }
                                        case "oauth2": {
                                            h.type = MSecurityScheme.Type.oauth2;
                                            break;
                                        }
                                        case "openIdConnect": {
                                            h.type = MSecurityScheme.Type.openIdConnect;
                                            break;
                                        }
                                        case "other": {
                                            h.type = MSecurityScheme.Type.other;
                                            break;
                                        }
                                    }
                                    for (NElement oo : vu.params()) {
                                        switch (oo.type()) {
                                            case PAIR: {
                                                NPairElement p2 = oo.asPair().get();
                                                switch (p2.key().toString()) {
                                                    case "in": {
                                                        h.in = p2.value().toString();
                                                        break;
                                                    }
                                                    case "openIdConnectUrl": {
                                                        h.openIdConnectUrl = p2.value().toString();
                                                        break;
                                                    }
                                                    case "bearerFormat": {
                                                        h.bearerFormat = p2.value().toString();
                                                        break;
                                                    }
                                                    case "scheme": {
                                                        h.scheme = p2.value().toString();
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (h.description == null) {
                                h.description = resolveComments(cc);
                            }

                        }
                    }
                }
            }
        }
        return all;
    }

    
    public List<MServer> findServers(NElement root) {
        List<MServer> all = new ArrayList<>();
        NListContainerElement components = root.toObject()
                .flatMap(x -> x.get("info"))
                .flatMap(x -> x.asObject().get().get("servers"))
                .flatMap(x -> x.asListContainer()).orNull();
        if (components != null) {
            for (NElement cc : components.children()) {
                switch (cc.type()) {
                    case NAMED_UPLET: {
                        NUpletElement u = cc.asUplet().get();
                        MServer h = new MServer();
                        h.name = NStringUtils.trim(u.name().orNull());
                        h.variables = new ArrayList<>();
                        all.add(h);
                        for (NElement oo : u.params()) {
                            switch (oo.type()) {
                                case DOUBLE_QUOTED_STRING:
                                case SINGLE_QUOTED_STRING:
                                case BACKTICK_STRING:
                                case TRIPLE_DOUBLE_QUOTED_STRING:
                                case TRIPLE_SINGLE_QUOTED_STRING:
                                case TRIPLE_BACKTICK_STRING:
                                case LINE_STRING: {
                                    if (h.url == null) {
                                        h.url = oo.asStringValue().orNull();
                                    }
                                    break;
                                }
                                case PAIR: {
                                    NPairElement p2 = oo.asPair().get();
                                    switch (p2.key().toString()) {
                                        case "url": {
                                            h.url = p2.value().toString();
                                            break;
                                        }
                                        case "description": {
                                            h.description = p2.value().toString();
                                            break;
                                        }
                                        case "variables": {
                                            h.variables = parseMVars(p2.value());
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (h.description == null) {
                            h.description = resolveComments(cc);
                        }

                    }
                }
            }
        }
        return all;
    }

    public List<MVar> parseMVars(NElement root) {
        List<MVar> all = new ArrayList<>();
        if (root != null) {
            for (NElement cc : root.toListContainer().get().children()) {
                switch (cc.type()) {
                    case PAIR: {
                        NPairElement p2 = cc.asPair().get();
                        MVar v = new MVar();
                        v.id = p2.key().toString();
                        v.name = v.id;
                        if (p2.value().isString()) {
                            v.value = p2.value().toString();
                        }
                        if (v.description == null) {
                            v.description = resolveComments(p2.key());
                        }
                        all.add(v);
                    }
                }
            }
        }
        return all;
    }

    
    public Map<String, TypeInfo> findTypesMap(NElement root) {
        Map<String, TypeInfo> all = new LinkedHashMap<>();
        NObjectElement components = root.toObject().flatMap(x -> x.get("components"))
                .flatMap(x -> x.asObject()).orNull();
        if (components != null) {
            for (NElement cc : components) {
                switch (cc.type()) {
                    case NAMED_PARAMETRIZED_OBJECT: {
                        NObjectElement u = cc.toObject().get();
                        if (u.name().orElse("").equals("schema")) {
                            TypeInfo h = new TypeInfo();
                            h.setType("object");
                            for (NElement param : u.params().get()) {
                                if (param.isAnyString() && h.getName() == null) {
                                    h.setName(param.asStringValue().orNull());
                                    h.setFullName(param.asStringValue().orNull());
                                    h.setSmartName(param.asStringValue().orNull());
                                    h.setUserType(param.asStringValue().orNull());
                                    all.put(h.getName(), h);
                                } else if (param.isPair()) {
                                    NPairElement p2 = param.asPair().get();
                                    switch (p2.key().asStringValue().orNull()) {
                                        case "description": {
                                            h.setDescription(p2.toString());
                                            break;
                                        }
                                        case "summary": {
                                            h.setSummary(p2.toString());
                                            break;
                                        }
                                    }
                                }
                            }
                            if (h.getDescription() == null) {
                                h.setDescription(resolveComments(cc));
                            }
                            // fields
                            List<NElement> body = u.children();
                            while (!body.isEmpty()) {
                                NElement param = body.remove(0);
                                switch (param.type()) {
                                    case PAIR: {
                                        FieldInfo f = new FieldInfo();
                                        NPairElement p2 = param.asPair().get();
                                        List<NElementAnnotation> annotations = p2.key().annotations();
                                        for (NElementAnnotation annotation : annotations) {
                                            if (annotation.name().equals("required")) {
                                                f.required = true;
                                            } else if (annotation.name().equals("deprecated")) {
                                                f.deprecated = true;
                                            } else if (annotation.name().equals("summary")) {
                                                f.summary = annotation.param(0).get().asStringValue().orNull();
                                            } else if (annotation.name().equals("description")) {
                                                f.description = annotation.param(0).get().asStringValue().orNull();
                                            }
                                        }
                                        NElement bodyElement = null;
                                        List<NElement> typeElements = new ArrayList<>();
                                        if (f.description == null) {
                                            f.description = resolveComments(p2.key());
                                        }
                                        String fieldName = p2.key().asStringValue().orNull();
                                        f.name = fieldName;
                                        h.getFields().add(f);

                                        NElement fieldType = p2;
                                        switch (fieldType.type()) {
                                            case NAMED_PARAMETRIZED_OBJECT: {
                                                NObjectElementBuilder b = fieldType.toObject().get().builder();
                                                List<NElement> body1 = b.children();
                                                b.clear();
                                                bodyElement = NElement.ofObject(body1.toArray(new NElement[0]));
                                                fieldType = b.build();
                                                f.baseFieldTypeName = b.name().orNull();
                                                f.baseFieldTypeName = fieldType.toString();
                                                break;
                                            }
                                            case NAMED_OBJECT: {
                                                NObjectElementBuilder b = fieldType.toObject().get().builder();
                                                f.baseFieldTypeName = b.name().orNull();
                                                List<NElement> body1 = b.children();
                                                b.clear();
                                                bodyElement = NElement.ofObject(body1.toArray(new NElement[0]));
                                                fieldType = NElement.ofName(b.name().orNull());
                                                f.fieldTypeName = b.name().orNull();
                                                f.baseFieldTypeName = b.name().orNull();
                                                break;
                                            }
                                            case NAMED_PARAMETRIZED_ARRAY:
                                            case NAMED_ARRAY: {
                                                f.arrays++;
                                                f.baseFieldTypeName = fieldType.toArray().get().name().orNull();
                                                f.fieldTypeName = fieldType.toArray().toString();
                                                f.arrayConstraints.add(fieldType.toArray().get().builder().clearParams().setParametrized(false).build().toString());
                                                break;
                                            }
                                            case DOUBLE_QUOTED_STRING:
                                            case SINGLE_QUOTED_STRING:
                                            case BACKTICK_STRING:
                                            case TRIPLE_DOUBLE_QUOTED_STRING:
                                            case TRIPLE_SINGLE_QUOTED_STRING:
                                            case TRIPLE_BACKTICK_STRING:
                                            case LINE_STRING:
                                            case NAME: {
                                                f.baseFieldTypeName = fieldType.asStringValue().orNull();
                                                f.fieldTypeName = fieldType.asStringValue().orNull();
                                                break;
                                            }
                                            default: {
                                                throw new IllegalArgumentException("Expected type name, found " + fieldType);
                                            }
                                        }

                                        typeElements.add(fieldType);
                                        boolean stillInLoop = true;
                                        while (stillInLoop && !body.isEmpty() && bodyElement == null) {
                                            NElement bb = body.get(0);

                                            switch (bb.type()) {
                                                case OBJECT: {
                                                    body.remove(0);
                                                    bodyElement = bb;
                                                    break;
                                                }
                                                case ARRAY: {
                                                    body.remove(0);
                                                    f.arrays++;
                                                    f.arrayConstraints.add(bb.toString());
                                                    typeElements.add(bb);
                                                    f.fieldTypeName += bb;
                                                    break;
                                                }
                                                default: {
                                                    stillInLoop = false;
                                                }
                                            }
                                        }
                                        if (bodyElement != null) {
                                            for (NElement example : bodyElement.toObject().get().children().stream().filter(x -> x.isNamedUplet() && x.asUplet().get().name().orElse("").equals("example")).collect(Collectors.toList())) {
                                                for (NElement p : example.asUplet().get().params()) {
                                                    f.examples.add(new MExample(
                                                            NStringUtils.firstNonBlank(resolveComments(p), resolveComments(example)),
                                                            p
                                                    ));
                                                }
                                            }
                                        }
                                        break;
                                    }
                                    case NAMED_UPLET: {
                                        NUpletElement uplet = param.asUplet().get();
                                        if (uplet.isNamedUplet() && uplet.name().orElse("").equals("example") && uplet.params().size() > 0) {
                                            for (NElement p : uplet.asUplet().get().params()) {
                                                h.examples.add(new MExample(
                                                        NStringUtils.firstNonBlank(resolveComments(p), resolveComments(uplet)),
                                                        p
                                                ));
                                            }
                                        } else {
                                            throw new IllegalArgumentException("expected example(...)");
                                        }
                                        break;
                                    }
                                    default: {
                                        throw new IllegalArgumentException("expected example(...)");
                                    }
                                }

                            }
                        }

                    }
                }
            }
        }
        for (TypeInfo value : new HashMap<>(all).values()) {
            for (FieldInfo field : value.getFields()) {
                field.schema = findType(
                        field.baseFieldTypeName, field.fieldTypeName, field.arrayConstraints, all
                );
            }
        }
        return all;
    }

    private TypeInfo findType(String baseTypeName, String fullName, List<String> arrConstraints, Map<String, TypeInfo> repo) {
        TypeInfo fullSchema = repo.get(fullName);
        if (fullSchema != null) {
            return fullSchema;
        }
        TypeInfo baseSchema = repo.get(baseTypeName);
        if (baseSchema == null) {
            switch (baseTypeName) {
                case "string":
                case "int":
                case "long":
                case "double":
                case "date":
                case "datetime":
                case "time":
                case "boolean":
                case "enum": {
                    baseSchema = new TypeInfo();
                    baseSchema.setType(baseTypeName);
                    baseSchema.setName(baseTypeName);
                    baseSchema.setFullName(baseTypeName);
                    baseSchema.setSmartName(baseTypeName);
                    baseSchema.setUserType(baseTypeName);
                    repo.put(baseTypeName, baseSchema);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("type " + baseTypeName + " not found");
                }
            }
        }
        if (arrConstraints.size() == 0) {
            return baseSchema;
        }
        ArrayList<String> nac = new ArrayList<>(arrConstraints);
        nac.remove(nac.size() - 1);
        return findType(baseTypeName, baseTypeName + String.join("", nac), nac, repo);
    }

    private String resolveComments(NElement e) {
        StringBuilder comments = new StringBuilder();
        List<NElementComment> comments1 = e.comments();
        for (NElementComment comment : comments1) {
            if (comments.length() > 0) {
                comments.append("\n");
            }
            comments.append(comment.text());
        }
        return comments.toString().trim();
    }

    
    public List<MPath> findPaths() {
        return Collections.emptyList();
    }


    
    public MConf loadConfigFile(NPath cf) {
        return null;
    }

    
    public Map<Object, Object> loadVars(String varsPath) {
        return Collections.emptyMap();
    }
}
