package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.*;

import java.util.*;

/**
 * Condensed TSON reader (desugarer). Expands the condensed dialect (prompt sections 3 and 4) into
 * the same OpenAPI {@code NElement} tree that the JSON/YAML readers produce, so the rest of the
 * NoAPI pipeline is untouched.
 *
 * <p>Everything that is not a recognised condensed pattern is passed through verbatim, therefore
 * plain TSON is valid input as well.</p>
 */
public class CondensedTsonDesugarer {

    private final NoApiDiagnostics diagnostics;
    private final Set<String> declaredSchemas = new LinkedHashSet<>();
    private final Set<String> declaredSecuritySchemes = new LinkedHashSet<>();

    private static final Set<String> OPERATION_ANNOTATIONS = new LinkedHashSet<>(Arrays.asList(
            "summary", "description", "operationId", "tags", "deprecated", "enabled", "security"));

    private static final Set<String> LOCATION_ANNOTATIONS = new LinkedHashSet<>(Arrays.asList(
            "path", "query", "header", "cookie", "param", "body"));

    public CondensedTsonDesugarer(NoApiDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    // ------------------------------------------------------------------ entry points

    public NElement desugar(NElement root) {
        NObjectElement o = root == null ? null : root.asObject().orNull();
        if (o == null) {
            diagnostics.error("E_SYNTAX", "document root must be an object", null);
            diagnostics.throwIfErrors();
            return root;
        }
        return desugarDocument(o);
    }

    public NElement desugarConfig(NElement root) {
        NObjectElement o = root == null ? null : root.asObject().orNull();
        if (o == null) {
            diagnostics.error("E_SYNTAX", "document root must be an object", null);
            diagnostics.throwIfErrors();
            return root;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            if ("variables".equals(name)) {
                r.add(name, desugarConfigVariables(Tson.valueOf(m), "variables"));
            } else {
                r.add(name, Tson.valueOf(m));
            }
        }
        diagnostics.throwIfErrors();
        return r.build();
    }

    private NElement desugarConfigVariables(NElement value, String path) {
        NObjectElement o = value.asObject().orNull();
        if (o == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if (v.asObject().isPresent()) {
                r.add(name, v);
            } else {
                // scalar short form
                NObjectElementBuilder vv = Tson.obj();
                vv.add("value", v);
                vv.add("observations", Tson.str(""));
                r.add(name, vv.build());
            }
        }
        return r.build();
    }

    // ------------------------------------------------------------------ document

    private NElement desugarDocument(NObjectElement doc) {
        // pre-scan declared names for reference resolution
        NObjectElement components = doc.getObject("components").orNull();
        if (components != null) {
            NObjectElement schemas = components.getObject("schemas").orNull();
            if (schemas != null) {
                for (NElement m : Tson.members(schemas)) {
                    declaredSchemas.add(Tson.nameOf(m));
                }
            }
            NObjectElement sec = components.getObject("securitySchemes").orNull();
            if (sec != null) {
                for (NElement m : Tson.members(sec)) {
                    declaredSecuritySchemes.add(Tson.nameOf(m));
                }
            }
        }

        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(doc)) {
            String name = Tson.nameOf(m);
            if (name == null) {
                continue;
            }
            switch (name) {
                case "openapi": {
                    r.add("openapi", Tson.valueOf(m));
                    break;
                }
                case "info": {
                    // @openapi may decorate the first node (info) - see PHASE0-NOTES P10
                    NElementAnnotation oa = Tson.annotation(m, "openapi");
                    if (oa != null && doc.get("openapi").isEmpty()) {
                        NElement v = oa.param(0).orNull();
                        r.add("openapi", v == null ? Tson.str("3.0.0") : v);
                    }
                    r.add("info", desugarInfo(Tson.valueOf(m), "info"));
                    break;
                }
                case "custom": {
                    r.add("custom", Tson.unnamed(Tson.valueOf(m)));
                    break;
                }
                case "servers": {
                    r.add("servers", desugarServers(Tson.valueOf(m), "servers"));
                    break;
                }
                case "paths": {
                    r.add("paths", desugarPaths(Tson.valueOf(m), "paths"));
                    break;
                }
                case "components": {
                    NElement cc = desugarComponents(Tson.valueOf(m), "components");
                    if (cc.asObject().isPresent() && cc.asObject().get().size() > 0) {
                        r.add("components", cc);
                    }
                    break;
                }
                case "tags": {
                    r.add("tags", Tson.valueOf(m));
                    break;
                }
                case "security": {
                    r.add("security", desugarSecurityList(Tson.valueOf(m), "security"));
                    break;
                }
                default: {
                    r.add(name, Tson.valueOf(m));
                    break;
                }
            }
        }
        diagnostics.throwIfErrors();
        return r.build();
    }

    private NElement desugarInfo(NElement value, String path) {
        NObjectElement info = value.asObject().orNull();
        if (info == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(info)) {
            String name = Tson.nameOf(m);
            if ("changes".equals(name)) {
                r.add("changes", desugarChanges(Tson.valueOf(m), path + ".changes"));
            } else {
                r.add(name, Tson.valueOf(m));
            }
        }
        return r.build();
    }

    private NElement desugarChanges(NElement value, String path) {
        NArrayElement arr = value.asArray().orNull();
        if (arr == null) {
            return value;
        }
        NArrayElementBuilder r = Tson.arr();
        int i = 0;
        for (NElement item : arr.children()) {
            NObjectElement ch = item.asObject().orNull();
            if (ch == null) {
                r.add(item);
            } else {
                NObjectElementBuilder c = Tson.obj();
                for (NElement m : Tson.members(ch)) {
                    String name = Tson.nameOf(m);
                    NElement v = Tson.valueOf(m);
                    if ("date".equals(name) && Tson.isNativeDate(v)) {
                        c.add("date", Tson.str(Tson.dateString(v)));
                    } else {
                        c.add(name, v);
                    }
                }
                r.add(c.build());
            }
            i++;
        }
        return r.build();
    }

    // ------------------------------------------------------------------ servers

    private NElement desugarServers(NElement value, String path) {
        NArrayElement arr = value.asArray().orNull();
        if (arr == null) {
            return value;
        }
        NArrayElementBuilder r = Tson.arr();
        for (NElement item : arr.children()) {
            NObjectElement o = item.asObject().orNull();
            if ("url".equals(headOf(item))) {
                List<NElement> params = Tson.paramsOf(item);
                NElement url = params.isEmpty() ? Tson.str("") : params.get(0);
                NObjectElementBuilder s = Tson.obj();
                s.add("url", url);
                if (o != null) {
                    for (NElement m : Tson.members(o)) {
                        s.add(Tson.nameOf(m), Tson.valueOf(m));
                    }
                }
                r.add(s.build());
            } else {
                r.add(item);
            }
        }
        return r.build();
    }

    // ------------------------------------------------------------------ security

    private NElement desugarSecurityList(NElement value, String path) {
        NArrayElement arr = value.asArray().orNull();
        if (arr == null) {
            return value;
        }
        NArrayElementBuilder r = Tson.arr();
        for (NElement item : arr.children()) {
            r.add(desugarSecurityItem(item, path));
        }
        return r.build();
    }

    private NElement desugarSecurityItem(NElement item, String path) {
        if (item.asObject().isPresent() && !Tson.isHeaded(item)) {
            return item;
        }
        String name = Tson.nameOf(item);
        NTupleElement t = item.asTuple().orNull();
        if (t != null) {
            NArrayElementBuilder scopes = Tson.arr();
            for (NElement p : t.params()) {
                scopes.add(Tson.str(Tson.asString(p)));
            }
            NObjectElementBuilder o = Tson.obj();
            o.add(name, scopes.build());
            return o.build();
        }
        if (name != null) {
            if (!declaredSecuritySchemes.isEmpty() && !declaredSecuritySchemes.contains(name)) {
                diagnostics.error("E_UNRESOLVED_REF", "unknown security scheme '" + name + "'", path);
            }
            NObjectElementBuilder o = Tson.obj();
            o.add(name, NElement.ofArray());
            return o.build();
        }
        return item;
    }

    // ------------------------------------------------------------------ paths

    private NElement desugarPaths(NElement value, String path) {
        NObjectElement paths = value.asObject().orNull();
        if (paths == null) {
            return value;
        }
        // ordered path items, ops grouped by path
        LinkedHashMap<String, NObjectElementBuilder> grouped = new LinkedHashMap<>();
        Set<String> seenOps = new HashSet<>();
        for (NElement m : Tson.members(paths)) {
            String name = Tson.nameOf(m);
            if (name == null) {
                continue;
            }
            if (Tson.isMethod(name) && Tson.isHeaded(m)) {
                String method = name.toLowerCase();
                List<NElement> params = Tson.headParams(m);
                String opPath = firstPositional(params);
                if (opPath == null) {
                    diagnostics.error("E_BAD_ARGS", "operation '" + name + "' has no path argument", path);
                    continue;
                }
                String opKey = method + " " + opPath;
                if (!seenOps.add(opKey)) {
                    diagnostics.error("E_DUP_OPERATION", "duplicate operation " + name + " " + opPath, path);
                }
                NElement opObj = desugarOperation(m, method, opPath, params, path + "." + opPath + "." + method);
                NObjectElementBuilder pb = grouped.get(opPath);
                if (pb == null) {
                    pb = Tson.obj();
                    grouped.put(opPath, pb);
                }
                pb.add(method, opObj);
            } else {
                // plain path item, read as-is
                grouped.put(name, Tson.obj().addAll(Tson.members(Tson.valueOf(m))));
            }
        }
        NObjectElementBuilder r = Tson.obj();
        for (Map.Entry<String, NObjectElementBuilder> e : grouped.entrySet()) {
            r.add(e.getKey(), e.getValue().build());
        }
        return r.build();
    }

    private NElement desugarOperation(NElement head, String method, String opPath, List<NElement> headParams, String path) {
        NObjectElement body = Tson.valueOf(head).asObject().orNull();
        NObjectElementBuilder op = Tson.obj();
        Set<String> opKeys = new LinkedHashSet<>();

        // annotations
        for (NElementAnnotation a : Tson.annotationsOf(head)) {
            if (!OPERATION_ANNOTATIONS.contains(a.name())) {
                diagnostics.error("E_UNKNOWN_ANNOTATION", "annotation @" + a.name() + " is not allowed on an operation", path);
                continue;
            }
            NElement kv = annotationToValue(a, path);
            if (kv != null) {
                NPairElement p = kv.asPair().get();
                String k = p.key().asStringValue().get();
                if (!opKeys.add(k)) {
                    diagnostics.error("E_DUP_KEY", "duplicate operation key '" + k + "'", path);
                }
                op.add(k, p.value());
            }
        }

        // head extra pairs
        for (NElement p : headParams) {
            NPairElement pair = p.asPair().orNull();
            if (pair != null) {
                String k = pair.key().asStringValue().get();
                if (!opKeys.add(k)) {
                    diagnostics.error("E_DUP_KEY", "duplicate operation key '" + k + "'", path);
                }
                op.add(k, pair.value());
            }
        }

        // body
        List<NElement> parameters = new ArrayList<>();
        NObjectElementBuilder responses = Tson.obj();
        boolean hasResponses = false;
        NElement requestBody = null;

        if (body != null) {
            for (NElement m : Tson.members(body)) {
                String name = Tson.nameOf(m);
                if (name == null) {
                    continue;
                }
                if ("in".equals(name)) {
                    requestBody = desugarRequestBody(m, path + ".in");
                } else if ("out".equals(name)) {
                    String status = firstPositional(Tson.headParams(m));
                    if (status == null) {
                        diagnostics.error("E_BAD_ARGS", "response 'out' has no status argument", path);
                        continue;
                    }
                    String code = status;
                    NElement resp = desugarResponse(m, path + ".out(" + code + ")");
                    if (responses.get(code).isPresent()) {
                        diagnostics.error("E_DUP_KEY", "duplicate response " + code, path);
                    }
                    responses.add(code, resp);
                    hasResponses = true;
                } else if (isParameterMember(m)) {
                    NElement par = desugarParameter(m, opPath, path);
                    if (par != null) {
                        // parameters can expand to a single param
                        parameters.add(par);
                    }
                } else if ("parameters".equals(name) && Tson.valueOf(m).asArray().isPresent()) {
                    for (NElement p : Tson.valueOf(m).asArray().get().children()) {
                        parameters.add(p);
                    }
                } else if ("requestBody".equals(name) || "responses".equals(name)
                        || "externalDocs".equals(name) || "callbacks".equals(name)
                        || "servers".equals(name) || "security".equals(name) || "deprecated".equals(name)) {
                    if (!opKeys.add(name)) {
                        diagnostics.error("E_DUP_KEY", "duplicate operation key '" + name + "'", path);
                    }
                    op.add(name, Tson.valueOf(m));
                } else {
                    // unknown member
                    diagnostics.error("E_UNKNOWN_HEAD", "unknown operation member '" + name + "'", path);
                }
            }
        }

        if (requestBody != null) {
            op.add("requestBody", requestBody);
        }
        if (!parameters.isEmpty()) {
            NArrayElementBuilder pa = Tson.arr();
            for (NElement p : parameters) {
                pa.add(p);
            }
            op.add("parameters", pa.build());
        }
        if (hasResponses) {
            op.add("responses", responses.build());
        }
        return op.build();
    }

    private boolean isParameterMember(NElement m) {
        for (NElementAnnotation a : Tson.annotationsOf(m)) {
            if (LOCATION_ANNOTATIONS.contains(a.name())) {
                return true;
            }
        }
        return false;
    }

    private NElement annotationToValue(NElementAnnotation a, String path) {
        switch (a.name()) {
            case "summary":
            case "description":
            case "operationId":
            case "enabled": {
                NElement v = a.param(0).orNull();
                return NElement.ofPair(a.name(), v == null ? Tson.str("") : v);
            }
            case "deprecated": {
                NElement v = a.param(0).orNull();
                return NElement.ofPair("deprecated", v == null ? Tson.ofBoolean(true) : v);
            }
            case "tags": {
                NArrayElementBuilder tags = Tson.arr();
                if (a.params().isPresent()) {
                    for (NElement p : a.params().get()) {
                        tags.add(p);
                    }
                }
                return NElement.ofPair("tags", tags.build());
            }
            case "security": {
                NArrayElementBuilder sc = Tson.arr();
                if (a.params().isPresent()) {
                    for (NElement p : a.params().get()) {
                        sc.add(desugarSecurityItem(p, path + ".security"));
                    }
                }
                return NElement.ofPair("security", sc.build());
            }
            default: {
                return null;
            }
        }
    }

    // ------------------------------------------------------------------ request body / responses

    private NElement desugarRequestBody(NElement member, String path) {
        NElement typeValue = Tson.valueOf(member);
        NElement body = typeValue.asObject().orNull();
        String head = Tson.nameOf(typeValue);
        NObjectElementBuilder rb = Tson.obj();
        String description = null;
        NElement example = null;
        boolean required = Tson.hasAnnotation(member, "required");
        NElementAnnotation reqAnn = Tson.annotation(member, "required");
        if (reqAnn != null && reqAnn.param(0).isPresent()) {
            required = Tson.asBoolean(reqAnn.param(0).get(), true);
        }
        if (body != null) {
            for (NElement m : Tson.members(body)) {
                String name = Tson.nameOf(m);
                NElement v = Tson.valueOf(m);
                if ("description".equals(name)) {
                    description = Tson.asString(v);
                } else if ("example".equals(name)) {
                    example = v;
                } else if ("required".equals(name)) {
                    required = Tson.asBoolean(v, required);
                } else {
                    diagnostics.error("E_UNKNOWN_HEAD", "unknown request-body member '" + name + "'", path);
                }
            }
        }
        if (description != null) {
            rb.add("description", Tson.str(description));
        }
        if (required) {
            rb.add("required", Tson.ofBoolean(true));
        }
        if (!"void".equals(head)) {
            rb.add("content", buildContent(typeValue, head, example, path));
        }
        return rb.build();
    }

    private NElement desugarResponse(NElement member, String path) {
        NElement typeValue = Tson.valueOf(member);
        NElement body = typeValue.asObject().orNull();
        String head = Tson.nameOf(typeValue);
        NObjectElementBuilder resp = Tson.obj();
        String description = null;
        NElement example = null;
        if (body != null) {
            for (NElement m : Tson.members(body)) {
                String name = Tson.nameOf(m);
                NElement v = Tson.valueOf(m);
                if ("description".equals(name)) {
                    description = Tson.asString(v);
                } else if ("example".equals(name)) {
                    example = v;
                } else {
                    diagnostics.error("E_UNKNOWN_HEAD", "unknown response member '" + name + "'", path);
                }
            }
        }
        if (description == null) {
            String status = responseStatusOf(member);
            String reason = Tson.reasonPhrase(status);
            description = reason != null ? reason : "OK";
            diagnostics.warn("W_NO_DESCRIPTION", "response " + status + " has no description; using '" + description + "'", path);
        }
        resp.add("description", Tson.str(description));
        if (!"void".equals(head)) {
            resp.add("content", buildContent(typeValue, head, example, path));
        }
        return resp.build();
    }

    private String responseStatusOf(NElement member) {
        String s = firstPositional(Tson.headParams(member));
        return s == null ? "default" : s;
    }

    private NElement buildContent(NElement typeValue, String head, NElement example, String path) {
        NElement schema = schemaOfTypeHead(typeValue, head, path);
        List<NElement> mediaParams = Tson.paramsOf(typeValue);
        List<String> mediaTypes = new ArrayList<>();
        if (mediaParams.isEmpty()) {
            mediaTypes.add("application/json");
        } else {
            for (NElement p : mediaParams) {
                String m = Tson.asString(p);
                mediaTypes.add(Tson.mediaType(m));
            }
        }
        NObjectElementBuilder content = Tson.obj();
        for (String media : mediaTypes) {
            NObjectElementBuilder entry = Tson.obj();
            entry.add("schema", schema);
            if (example != null) {
                entry.add("example", example);
            }
            content.add(media, entry.build());
        }
        return content.build();
    }

    /**
     * Schema of a type expression used in {@code in}/{@code out}: the params of the head are media
     * shorthands (handled by the caller), so only the head name is turned into a schema.
     */
    private NElement schemaOfTypeHead(NElement typeValue, String head, String path) {
        if (head == null) {
            return NElement.ofObject();
        }
        if ("void".equals(head)) {
            diagnostics.error("E_VOID_OUTSIDE_RESPONSE", "void is only allowed as an out(...) type", path);
            return NElement.ofObject();
        }
        if ("object".equals(head)) {
            return buildObjectSchema(typeValue, SchemaContext.PROPERTY, null, path);
        }
        if ("array".equals(head)) {
            return buildArraySchema(typeValue, path);
        }
        if (Tson.isPrimitive(head)) {
            NObjectElementBuilder b = Tson.obj();
            b.add("type", Tson.str(head));
            return b.build();
        }
        checkSchemaRef(head, path);
        NObjectElementBuilder b = Tson.obj();
        b.add("$ref", Tson.str("#/components/schemas/" + head));
        return b.build();
    }

    // ------------------------------------------------------------------ parameters

    private NElement desugarParameter(NElement member, String opPath, String path) {
        String name = Tson.nameOf(member);
        String location = null;
        for (NElementAnnotation a : Tson.annotationsOf(member)) {
            if (LOCATION_ANNOTATIONS.contains(a.name())) {
                if ("body".equals(a.name())) {
                    diagnostics.error("E_BAD_ARGS", "@body is only valid on the reserved 'in' member", path);
                    return null;
                }
                location = "param".equals(a.name())
                        ? (opPath != null && opPath.contains("{" + name + "}") ? "path" : "query")
                        : a.name();
            } else if (!"required".equals(a.name())) {
                diagnostics.error("E_UNKNOWN_ANNOTATION", "annotation @" + a.name() + " is not allowed on a parameter", path);
                return null;
            }
        }
        if (location == null) {
            diagnostics.error("E_MISSING_LOCATION", "parameter '" + name + "' has no location annotation", path);
            return null;
        }
        boolean required = false;
        NElementAnnotation reqAnn = Tson.annotation(member, "required");
        if (reqAnn != null) {
            required = reqAnn.param(0).isPresent() ? Tson.asBoolean(reqAnn.param(0).get(), true) : true;
        }
        NElement typeValue = Tson.valueOf(member);
        NElement schema = schemaOf(typeValue, SchemaContext.PROPERTY, path);
        NObjectElementBuilder par = Tson.obj();
        par.add("name", Tson.str(name));
        par.add("in", Tson.str(location));
        if (required) {
            par.add("required", Tson.ofBoolean(true));
        }
        // split attrs: description/example stay at parameter level, others go in schema
        splitParameterAttrs(par, schema, typeValue, path);
        return par.build();
    }

    private void splitParameterAttrs(NObjectElementBuilder par, NElement schema, NElement typeValue, String path) {
        NObjectElement schemaObj = schema.asObject().orNull();
        // move description/example from schema-level attrs to parameter level
        if (schemaObj != null) {
            NObjectElementBuilder s = Tson.obj();
            for (NElement m : Tson.members(schemaObj)) {
                String k = Tson.nameOf(m);
                NElement v = Tson.valueOf(m);
                if (("description".equals(k) || "example".equals(k) || "examples".equals(k)
                        || "deprecated".equals(k) || "style".equals(k) || "explode".equals(k)
                        || "allowEmptyValue".equals(k) || "allowReserved".equals(k))
                        && !"type".equals(k) && !"$ref".equals(k)) {
                    if (!par.get(k).isPresent()) {
                        par.add(k, v);
                    } else {
                        // parameter already has it (e.g. description before schema); keep schema one
                        s.add(k, v);
                    }
                } else {
                    s.add(k, v);
                }
            }
            par.add("schema", s.build());
        } else {
            par.add("schema", schema);
        }
    }

    // ------------------------------------------------------------------ components

    private NElement desugarComponents(NElement value, String path) {
        NObjectElement comps = value.asObject().orNull();
        if (comps == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(comps)) {
            String name = Tson.nameOf(m);
            if ("schemas".equals(name)) {
                r.add("schemas", desugarSchemas(Tson.valueOf(m), path + ".schemas"));
            } else if ("securitySchemes".equals(name)) {
                r.add("securitySchemes", desugarSecuritySchemes(Tson.valueOf(m), path + ".securitySchemes"));
            } else if ("headers".equals(name)) {
                r.add("headers", desugarHeaders(Tson.valueOf(m), path + ".headers"));
            } else {
                r.add(name, Tson.unnamed(Tson.valueOf(m)));
            }
        }
        return r.build();
    }

    // ------------------------------------------------------------------ schemas

    private NElement desugarSchemas(NElement value, String path) {
        NObjectElement schemas = value.asObject().orNull();
        if (schemas == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(schemas)) {
            String name = Tson.nameOf(m);
            if (name == null) {
                continue;
            }
            if (Tson.RESERVED_SCHEMA_NAMES.contains(name)) {
                diagnostics.error("E_RESERVED_NAME", "schema name '" + name + "' is reserved", path);
            }
            r.add(name, desugarSchemaDeclaration(m, path + "." + name));
        }
        return r.build();
    }

    private NElement desugarSchemaDeclaration(NElement member, String path) {
        if (Tson.hasAnnotation(member, "raw")) {
            return Tson.unnamed(Tson.valueOf(member));
        }
        String description = null;
        NElementAnnotation da = Tson.annotation(member, "description");
        if (da != null && da.param(0).isPresent()) {
            description = Tson.asString(da.param(0).get());
        }
        for (NElementAnnotation a : Tson.annotationsOf(member)) {
            if (!"description".equals(a.name()) && !"raw".equals(a.name())) {
                diagnostics.error("E_UNKNOWN_ANNOTATION", "annotation @" + a.name() + " is not allowed on a schema declaration", path);
            }
        }
        NElement value = Tson.valueOf(member);
        NElement out = schemaOf(value, SchemaContext.DECLARATION, path, description);
        return out;
    }

    private enum SchemaContext {PROPERTY, DECLARATION}

    private NElement schemaOf(NElement value, SchemaContext ctx, String path) {
        return schemaOf(value, ctx, path, null);
    }

    /**
     * Expands a type expression into an OpenAPI schema element.
     */
    private NElement schemaOf(NElement value, SchemaContext ctx, String path, String declDescription) {
        if (value == null) {
            return NElement.ofObject();
        }
        NObjectElement asObj = value.asObject().orNull();
        NArrayElement asArr = value.asArray().orNull();
        if (value.isPair()) {
            // shouldn't happen in type position
            return value;
        }
        if (asObj != null) {
            String head = Tson.isHeaded(value) ? Tson.nameOf(value) : null;
            if (head == null) {
                // plain object in type position: verbatim schema
                return Tson.unnamed(value);
            }
            if ("object".equals(head)) {
                return buildObjectSchema(value, ctx, declDescription, path);
            }
            if (Tson.isPrimitive(head)) {
                return buildPrimitiveSchema(head, Tson.paramsOf(value), path);
            }
            if (ctx == SchemaContext.DECLARATION) {
                // Name { props } : implicit object
                return buildObjectSchema(value, ctx, declDescription, path);
            }
            // property position: untyped, verbatim body
            return Tson.unnamed(value);
        }
        if (asArr != null) {
            if (Tson.isHeaded(value) && "array".equals(Tson.nameOf(value))) {
                return buildArraySchema(value, path);
            }
            return Tson.unnamed(value);
        }
        NTupleElement t = value.asTuple().orNull();
        String head = t != null ? Tson.nameOf(value) : Tson.asString(value);
        if (head == null) {
            return value;
        }
        if (t != null) {
            if (Tson.isPrimitive(head)) {
                return buildPrimitiveSchema(head, t.params(), path);
            }
            // reference with attrs
            checkSchemaRef(head, path);
            NObjectElementBuilder b = Tson.obj();
            b.add("$ref", Tson.str("#/components/schemas/" + head));
            return b.build();
        }
        // primitive / reference
        if (Tson.isPrimitive(head)) {
            return buildPrimitiveSchema(head, Collections.<NElement>emptyList(), path);
        }
        if ("void".equals(head)) {
            diagnostics.error("E_VOID_OUTSIDE_RESPONSE", "void is only allowed in an out(...) type", path);
            return NElement.ofObject();
        }
        checkSchemaRef(head, path);
        NObjectElementBuilder b = Tson.obj();
        b.add("$ref", Tson.str("#/components/schemas/" + head));
        return b.build();
    }

    private void checkSchemaRef(String name, String path) {
        if (declaredSchemas.isEmpty()) {
            return; // plain TSON without components - cannot validate
        }
        if (!declaredSchemas.contains(name)) {
            diagnostics.error("E_UNRESOLVED_REF", "unknown schema '" + name + "'", path);
        }
    }

    private NElement buildPrimitiveSchema(String type, List<NElement> params, String path) {
        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str(type));
        for (NElement p : params) {
            NPairElement pair = p.asPair().orNull();
            if (pair != null) {
                b.add(pair.key().asStringValue().get(), applyFormatSpecial(pair.key().asStringValue().get(), pair.value(), false));
            }
        }
        return b.build();
    }

    private NElement buildArraySchema(NElement value, String path) {
        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str("array"));
        List<NElement> children = Tson.members(value);
        if (children.size() != 1) {
            diagnostics.error("E_BAD_ARGS", "an array type must have exactly one item type", path);
        }
        if (!children.isEmpty()) {
            b.add("items", schemaOf(children.get(0), SchemaContext.PROPERTY, path + ".items"));
        }
        for (NElement p : Tson.paramsOf(value)) {
            NPairElement pair = p.asPair().orNull();
            if (pair != null) {
                b.add(pair.key().asStringValue().get(), pair.value());
            }
        }
        return b.build();
    }

    private NElement buildObjectSchema(NElement value, SchemaContext ctx, String declDescription, String path) {
        NObjectElement o = value.asObject().get();
        NObjectElementBuilder props = Tson.obj();
        List<String> required = new ArrayList<>();
        NElement example = null;

        for (NElement m : Tson.members(o)) {
            String pname = Tson.nameOf(m);
            if (pname == null) {
                continue;
            }
            for (NElementAnnotation a : Tson.annotationsOf(m)) {
                if (!"required".equals(a.name())) {
                    diagnostics.error("E_UNKNOWN_ANNOTATION", "annotation @" + a.name() + " is not allowed on a property", path);
                }
            }
            if ("example".equals(pname) && m.asPair().isPresent()) {
                // schema-level example (only pair form; `example {…}` named object is ambiguous)
                example = Tson.valueOf(m);
                continue;
            }
            if ("example".equals(pname) && Tson.isHeaded(m)) {
                example = Tson.valueOf(m);
                continue;
            }
            NElement propSchema = schemaOf(Tson.valueOf(m), SchemaContext.PROPERTY, path + ".properties." + pname);
            props.add(pname, propSchema);
            NElementAnnotation ra = Tson.annotation(m, "required");
            if (ra != null) {
                boolean req = ra.param(0).isPresent() ? Tson.asBoolean(ra.param(0).get(), true) : true;
                if (req) {
                    required.add(pname);
                }
            }
        }

        // params: attrs + extra required
        List<NElement> params = Tson.paramsOf(value);
        List<NElement> attrs = new ArrayList<>();
        List<String> extraRequired = new ArrayList<>();
        for (NElement p : params) {
            NPairElement pair = p.asPair().orNull();
            if (pair == null) {
                continue;
            }
            String k = pair.key().asStringValue().get();
            if ("required".equals(k) && pair.value().asArray().isPresent()) {
                for (NElement r : pair.value().asArray().get().children()) {
                    extraRequired.add(Tson.asString(r));
                }
            } else {
                attrs.add(pair);
            }
        }

        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str("object"));
        for (NElement p : attrs) {
            NPairElement pair = p.asPair().get();
            String k = pair.key().asStringValue().get();
            b.add(k, applyFormatSpecial(k, pair.value(), true));
        }
        if (declDescription != null) {
            b.add("description", Tson.str(declDescription));
        }
        if (!props.children().isEmpty()) {
            b.add("properties", props.build());
        }
        if (!required.isEmpty() || !extraRequired.isEmpty()) {
            NArrayElementBuilder ra = Tson.arr();
            for (String s : required) {
                ra.add(Tson.str(s));
            }
            for (String s : extraRequired) {
                if (!required.contains(s)) {
                    ra.add(Tson.str(s));
                }
            }
            b.add("required", ra.build());
        }
        if (example != null) {
            b.add("example", example);
        }
        return b.build();
    }

    private NElement applyFormatSpecial(String key, NElement value, boolean objectHead) {
        if (objectHead && "format".equals(key) && value != null && value.isName()) {
            String n = Tson.asString(value);
            if (n != null && !n.startsWith("object::")) {
                return Tson.str("object::" + n);
            }
        }
        return value;
    }

    // ------------------------------------------------------------------ security schemes / headers

    private NElement desugarSecuritySchemes(NElement value, String path) {
        NObjectElement o = value.asObject().orNull();
        if (o == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            r.add(name, desugarSecurityScheme(m, path + "." + name));
        }
        return r.build();
    }

    private NElement desugarSecurityScheme(NElement member, String path) {
        NElement value = Tson.valueOf(member);
        NObjectElement vo = value.asObject().orNull();
        String head = headOf(value);
        NObjectElementBuilder b = Tson.obj();
        if (head == null || ("http".equals(head) == false && "apiKey".equals(head) == false
                && "openIdConnect".equals(head) == false && "oauth2".equals(head) == false)) {
            // plain
            return Tson.unnamed(value);
        }
        if ("http".equals(head)) {
            NObjectElementBuilder b2 = Tson.obj();
            b2.add("type", Tson.str("http"));
            b2.add("scheme", Tson.str(firstPositional(Tson.paramsOf(value))));
            for (NElement m : Tson.members(vo)) {
                b2.add(Tson.nameOf(m), Tson.valueOf(m));
            }
            return b2.build();
        }
        if ("apiKey".equals(head)) {
            NObjectElementBuilder b2 = Tson.obj();
            b2.add("type", Tson.str("apiKey"));
            String in = null;
            String nm = null;
            for (NElement p : Tson.paramsOf(value)) {
                NPairElement pair = p.asPair().orNull();
                if (pair != null) {
                    String k = pair.key().asStringValue().get();
                    if ("header".equals(k) || "query".equals(k) || "cookie".equals(k)) {
                        in = k;
                        nm = Tson.asString(pair.value());
                    }
                }
            }
            b2.add("name", Tson.str(nm == null ? "" : nm));
            b2.add("in", Tson.str(in == null ? "header" : in));
            for (NElement m : Tson.members(vo)) {
                b2.add(Tson.nameOf(m), Tson.valueOf(m));
            }
            return b2.build();
        }
        if ("openIdConnect".equals(head)) {
            NObjectElementBuilder b2 = Tson.obj();
            b2.add("type", Tson.str("openIdConnect"));
            String url = null;
            for (NElement p : Tson.paramsOf(value)) {
                NPairElement pair = p.asPair().orNull();
                if (pair != null && "url".equals(pair.key().asStringValue().get())) {
                    url = Tson.asString(pair.value());
                }
            }
            b2.add("openIdConnectUrl", Tson.str(url == null ? "" : url));
            for (NElement m : Tson.members(vo)) {
                b2.add(Tson.nameOf(m), Tson.valueOf(m));
            }
            return b2.build();
        }
        // oauth2 { flows { ... } }
        NObjectElementBuilder b2 = Tson.obj();
        b2.add("type", Tson.str("oauth2"));
        for (NElement m : Tson.members(vo)) {
            b2.add(Tson.nameOf(m), Tson.valueOf(m));
        }
        return b2.build();
    }

    private NElement desugarHeaders(NElement value, String path) {
        NObjectElement o = value.asObject().orNull();
        if (o == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            NObjectElement vo = v.asObject().orNull();
            String head = headOf(v);
            if (head == null || !Tson.isPrimitive(head)) {
                // plain header
                r.add(name, Tson.unnamed(v));
                continue;
            }
            NObjectElementBuilder h = Tson.obj();
            NElementAnnotation req = Tson.annotation(m, "required");
            if (req != null) {
                boolean rv = req.param(0).isPresent() ? Tson.asBoolean(req.param(0).get(), true) : true;
                h.add("required", Tson.ofBoolean(rv));
            }
            NObjectElementBuilder schema = Tson.obj();
            schema.add("type", Tson.str(head));
            String description = null;
            for (NElement p : Tson.paramsOf(v)) {
                NPairElement pair = p.asPair().orNull();
                if (pair == null) {
                    continue;
                }
                String k = pair.key().asStringValue().get();
                if ("description".equals(k)) {
                    description = Tson.asString(pair.value());
                } else {
                    schema.add(k, pair.value());
                }
            }
            if (description != null) {
                h.add("description", Tson.str(description));
            }
            h.add("schema", schema.build());
            r.add(name, h.build());
        }
        return r.build();
    }

    // ------------------------------------------------------------------ utils

    /**
     * Head name of a value that is written with a head: {@code name { … }}, {@code name […]} or
     * {@code name(…)}. The Nuts parser represents a head with only parameters (no body) as a named
     * tuple rather than a named object, so both must be considered.
     */
    private String headOf(NElement v) {
        if (v == null) {
            return null;
        }
        if (v.asObject().isPresent() || v.asArray().isPresent() || v.asTuple().isPresent()) {
            return Tson.nameOf(v);
        }
        return null;
    }

    private String firstPositional(List<NElement> params) {
        for (NElement p : params) {
            if (!p.isPair()) {
                return Tson.asString(p);
            }
        }
        return null;
    }

    private String statusCode(NElement e) {
        Integer i = e.asIntValue().orNull();
        if (i != null) {
            return String.valueOf(i);
        }
        return Tson.asString(e);
    }
}
