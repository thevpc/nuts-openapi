package net.thevpc.nuts.toolbox.noapi.store;

import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.util.NOptional;
import net.thevpc.nuts.util.NStringUtils;
import net.thevpc.tson.*;

import java.util.*;
import java.util.stream.Collectors;

public class TsonStore implements NoApiStore {
    TsonElement root;
    List<TypeCrossRef> typeCrossRefs = new ArrayList<>();

    public TsonStore(NPath source) {
        root = Tson.reader().setSkipComments(false).read(source.toFile().get(), TsonElement.class);
    }

    @Override
    public NOptional<String> getTitle() {
        return NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("title")).then(x -> x.toStr()).then(x -> x.value());
    }

    @Override
    public NOptional<String> getVersion() {
        return NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("version")).then(x -> x.toStr()).then(x -> x.value());
    }

    @Override
    public List<DocItemInfo> getMultiDocuments() {
        return new ArrayList<>();
    }

    @Override
    public String getId() {
        return NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("id")).then(x -> x.toStr()).then(x -> x.value()).get();
    }

    @Override
    public List<MVar> findVariables() {
        return parseMVars(
                NOptional.of(root.toObject()).then(x -> x.get("info"))
                        .then(x -> x.toObject().get("variables")).orNull()
        );
    }

    @Override
    public List<MVar> findConfigVariables() {
        return parseMVars(
                NOptional.of(root.toObject()).then(x -> x.get("info"))
                        .then(x -> x.toObject().get("config"))
                        .then(x -> x.toObject().get("variables")).orNull()
        );
    }

    @Override
    public NOptional<String> getDescription() {
        return NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("description")).then(x -> x.toStr()).then(x -> x.value());
    }

    @Override
    public NOptional<String> getConfigDescription() {
        return NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("config"))
                .then(x -> x.toObject().get("description"))
                .then(x -> x.toStr()).then(x -> x.value());
    }

    @Override
    public NOptional<MContact> getContact() {
        TsonElement c = NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("contact")).orNull();
        if (c != null) {
            MContact mc = new MContact();
            mc.name = NOptional.of(c.toObject().get("name")).then(x -> x.toStr()).then(x -> x.value()).get();
            mc.email = NOptional.of(c.toObject().get("email")).then(x -> x.toStr()).then(x -> x.value()).get();
            mc.url = NOptional.of(c.toObject().get("url")).then(x -> x.toStr()).then(x -> x.value()).get();
            return NOptional.of(mc);
        }
        return NOptional.ofNamedEmpty("No contact found");
    }

    @Override
    public List<MChangeLog> findChangeLogs() {
        TsonElement c = NOptional.of(root.toObject()).then(x -> x.get("info"))
                .then(x -> x.toObject().get("changes")).orNull();
        List<MChangeLog> all = new ArrayList<>();
        if (c instanceof TsonListContainer) {
            for (TsonElement te : ((TsonListContainer) c).body()) {
                MChangeLog cl = new MChangeLog();
                cl.version = NOptional.of(te.toObject().get("version")).then(x -> x.toStr()).then(x -> x.value()).get();
                cl.date = NOptional.of(te.toObject().get("date")).then(x -> x.toStr()).then(x -> x.value()).get();
                cl.observations = NOptional.of(te.toObject().get("observations")).then(x -> x.toStr()).then(x -> x.value()).get();
                cl.title = NOptional.of(te.toObject().get("title")).then(x -> x.toStr()).then(x -> x.value()).get();
                cl.details = new ArrayList<>();
                TsonListContainer details = NOptional.of(te.toObject().get("details")).then(x -> x.toListContainer()).orNull();
                if (details != null) {
                    for (TsonElement d : details.body()) {
                        cl.details.add(d.toStr().value());
                    }
                }
                all.add(cl);
            }
        }
        return all;
    }

    @Override
    public List<MHeader> findHeaders() {
        List<MHeader> all = new ArrayList<>();
        TsonObject components = NOptional.of(root.toObject()).then(x -> x.get("components"))
                .then(x -> x.toObject()).orNull();
        if (components != null) {
            for (TsonElement cc : components) {
                switch (cc.type()) {
                    case NAMED_UPLET: {
                        TsonUplet u = cc.toUplet();
                        if (u.name().equals("header")) {
                            MHeader h = new MHeader();
                            for (TsonElement param : u.params()) {
                                if (param.isString() && h.name == null) {
                                    h.name = param.toStr().value();
                                    h.typeName = "string";
                                    h.smartTypeName = "string";
                                } else if (param.isPair()) {
                                    TsonPair p = param.toPair();
                                    if (h.name == null) {
                                        h.name = p.key().toStr().value();

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

    @Override
    public List<MSecurityScheme> findSecuritySchemes() {
        List<MSecurityScheme> all = new ArrayList<>();
        TsonObject components = NOptional.of(root.toObject()).then(x -> x.get("components"))
                .then(x -> x.toObject()).orNull();
        if (components != null) {
            for (TsonElement cc : components) {
                switch (cc.type()) {
                    case NAMED_UPLET: {
                        TsonUplet u = cc.toUplet();
                        if (u.name().equals("securityScheme")) {
                            MSecurityScheme h = new MSecurityScheme();
                            all.add(h);
                            for (TsonElement param : u.params()) {
                                if (param.isString() && h.name == null) {
                                    h.name = param.toStr().value();
                                } else if (param.isPair()) {
                                    TsonPair p2 = param.toPair();
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
                                    TsonUplet vu = param.toUplet();
                                    h.typeName = NStringUtils.trim(vu.name());
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
                                    for (TsonElement oo : vu.params()) {
                                        switch (oo.type()) {
                                            case PAIR: {
                                                TsonPair p2 = oo.toPair();
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

    @Override
    public List<MServer> findServers() {
        List<MServer> all = new ArrayList<>();
        TsonListContainer components = NOptional.of(root.toObject())
                .then(x -> x.get("info"))
                .then(x -> x.toObject().get("servers"))
                .then(x -> x.toListContainer()).orNull();
        if (components != null) {
            for (TsonElement cc : components.body()) {
                switch (cc.type()) {
                    case NAMED_UPLET: {
                        TsonUplet u = cc.toUplet();
                        MServer h = new MServer();
                        h.name = NStringUtils.trim(u.name());
                        h.variables = new ArrayList<>();
                        all.add(h);
                        for (TsonElement oo : u.params()) {
                            switch (oo.type()) {
                                case STRING: {
                                    if (h.url == null) {
                                        h.url = oo.toStr().value();
                                    }
                                    break;
                                }
                                case PAIR: {
                                    TsonPair p2 = oo.toPair();
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

    public List<MVar> parseMVars(TsonElement root) {
        List<MVar> all = new ArrayList<>();
        if (root != null) {
            for (TsonElement cc : root.toListContainer().body()) {
                switch (cc.type()) {
                    case PAIR: {
                        TsonPair p2 = cc.toPair();
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

    @Override
    public Map<String, TypeInfo> findTypesMap() {
        Map<String, TypeInfo> all = new LinkedHashMap<>();
        TsonObject components = NOptional.of(root.toObject()).then(x -> x.get("components"))
                .then(x -> x.toObject()).orNull();
        if (components != null) {
            for (TsonElement cc : components) {
                switch (cc.type()) {
                    case NAMED_PARAMETRIZED_OBJECT: {
                        TsonObject u = cc.toObject();
                        if (u.name().equals("schema")) {
                            TypeInfo h = new TypeInfo();
                            h.setType("object");
                            for (TsonElement param : u.params()) {
                                if (param.isAnyString() && h.getName() == null) {
                                    h.setName(param.toStr().value());
                                    h.setFullName(param.toStr().value());
                                    h.setSmartName(param.toStr().value());
                                    h.setUserType(param.toStr().value());
                                    all.put(h.getName(), h);
                                } else if (param.isPair()) {
                                    TsonPair p2 = param.toPair();
                                    switch (p2.key().toStr().value()) {
                                        case "description": {
                                            h.setDescription(p2.value().toString());
                                            break;
                                        }
                                        case "summary": {
                                            h.setSummary(p2.value().toString());
                                            break;
                                        }
                                    }
                                }
                            }
                            if (h.getDescription() == null) {
                                h.setDescription(resolveComments(cc));
                            }
                            // fields
                            List<TsonElement> body = u.body().toList();
                            while (!body.isEmpty()) {
                                TsonElement param = body.remove(0);
                                switch (param.type()) {
                                    case PAIR: {
                                        FieldInfo f = new FieldInfo();
                                        TsonPair p2 = param.toPair();
                                        TsonAnnotation[] annotations = p2.key().annotations();
                                        for (TsonAnnotation annotation : annotations) {
                                            if (annotation.name().equals("required")) {
                                                f.required = true;
                                            } else if (annotation.name().equals("deprecated")) {
                                                f.deprecated = true;
                                            } else if (annotation.name().equals("summary")) {
                                                f.summary = annotation.arg(0).toStr().value();
                                            } else if (annotation.name().equals("description")) {
                                                f.description = annotation.arg(0).toStr().value();
                                            }
                                        }
                                        TsonElement bodyElement = null;
                                        List<TsonElement> typeElements = new ArrayList<>();
                                        if (f.description == null) {
                                            f.description = resolveComments(p2.key());
                                        }
                                        String fieldName = p2.key().toStr().value();
                                        f.name = fieldName;
                                        h.getFields().add(f);

                                        TsonElement fieldType = p2.value();
                                        switch (fieldType.type()) {
                                            case NAMED_PARAMETRIZED_OBJECT: {
                                                TsonObjectBuilder b = fieldType.toObject().builder();
                                                List<TsonElement> body1 = b.body();
                                                b.clearBody();
                                                bodyElement = Tson.ofObject(body1.stream().toArray(TsonElementBase[]::new));
                                                fieldType = b.build();
                                                f.baseFieldTypeName = b.name();
                                                f.baseFieldTypeName = fieldType.toString();
                                                break;
                                            }
                                            case NAMED_OBJECT: {
                                                TsonObjectBuilder b = fieldType.toObject().builder();
                                                f.baseFieldTypeName = b.name();
                                                List<TsonElement> body1 = b.body();
                                                b.clearBody();
                                                bodyElement = Tson.ofObject(body1.stream().toArray(TsonElementBase[]::new));
                                                fieldType = Tson.ofName(b.name());
                                                f.fieldTypeName = b.name();
                                                f.baseFieldTypeName = b.name();
                                                break;
                                            }
                                            case NAMED_PARAMETRIZED_ARRAY:
                                            case NAMED_ARRAY: {
                                                f.arrays++;
                                                f.baseFieldTypeName = fieldType.toArray().name();
                                                f.fieldTypeName = fieldType.toArray().toString();
                                                f.arrayConstraints.add(fieldType.toArray().builder().clearParams().setParametrized(false).build().toString());
                                                break;
                                            }
                                            case STRING:
                                            case NAME: {
                                                f.baseFieldTypeName = fieldType.toStr().value();
                                                f.fieldTypeName = fieldType.toStr().value();
                                                break;
                                            }
                                            default: {
                                                throw new IllegalArgumentException("Expected type name, found " + fieldType);
                                            }
                                        }

                                        typeElements.add(fieldType);
                                        boolean stillInLoop = true;
                                        while (stillInLoop && !body.isEmpty() && bodyElement == null) {
                                            TsonElement bb = body.get(0);

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
                                            for (TsonElement example : bodyElement.toObject().body().toList().stream().filter(x -> x.isNamedUplet() && x.toUplet().name().equals("example")).collect(Collectors.toList())) {
                                                for (TsonElement p : example.toUplet().params()) {
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
                                        TsonUplet uplet = param.toUplet();
                                        if (uplet.isNamedUplet() && uplet.name().equals("example") && uplet.paramsCount() > 0) {
                                            for (TsonElement p : uplet.toUplet().params()) {
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

    private String resolveComments(TsonElement e) {
        StringBuilder comments = new StringBuilder();
        TsonComments comments1 = e.comments();
        if (comments1 != null) {
            for (TsonComment comment : comments1.getComments()) {
                if (comments.length() > 0) {
                    comments.append("\n");
                }
                comments.append(comment.text());
            }
        }
        return comments.toString().trim();
    }

    @Override
    public List<MPath> findPaths() {
        return Collections.emptyList();
    }

    @Override
    public List<TypeCrossRef> typeCrossRefs() {
        return typeCrossRefs;
    }

    @Override
    public MConf loadConfigFile(NPath cf) {
        return null;
    }

    @Override
    public Map<Object, Object> loadVars(String varsPath) {
        return Collections.emptyMap();
    }
}
