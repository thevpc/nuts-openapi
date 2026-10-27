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
    private final Set<String> declaredResponses = new LinkedHashSet<>();

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
            NPairElement p = root == null ? null : root.asPair().orNull();
            if (p != null) {
                // a document with a single member is parsed as a pair
                return desugarDocument(Tson.obj().add(p.key().asStringValue().orElse(""), p.value()).build());
            }
            diagnostics.error("E_SYNTAX", "document root must be an object", null);
            diagnostics.throwIfErrors();
            return root;
        }
        checkMemberNames(o, "$");
        return desugarDocument(o);
    }

    /**
     * Reports the members that have no name. They come from an invalid construct, typically an
     * annotation written without parentheses ({@code @example{…}} instead of {@code @example({…})}).
     */
    private void checkMemberNames(NElement e, String path) {
        NObjectElement o = e.asObject().orNull();
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                String name = Tson.nameOf(m);
                if (name == null) {
                    diagnostics.error("E_SYNTAX",
                            "a member of " + path + " has no name: an annotation takes parentheses, as in @example({...})",
                            path);
                    continue;
                }
                checkMemberNames(Tson.valueOf(m), path + "." + name);
            }
            return;
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null) {
            int i = 0;
            for (NElement c : Tson.members(a)) {
                checkMemberNames(c, path + "[" + i + "]");
                i++;
            }
        }
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
        NElement result = dedup(r.build(), "$");
        diagnostics.throwIfErrors();
        return result;
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
        NObjectElement components = sectionOf(doc, "components");
        if (components != null) {
            NObjectElement schemas = sectionOf(components, "schemas");
            if (schemas != null) {
                for (NElement m : Tson.members(schemas)) {
                    declaredSchemas.add(Tson.nameOf(m));
                }
            }
            NObjectElement sec = sectionOf(components, "securitySchemes");
            if (sec != null) {
                for (NElement m : Tson.members(sec)) {
                    declaredSecuritySchemes.add(Tson.nameOf(m));
                }
            }
            NObjectElement res = sectionOf(components, "responses");
            if (res != null) {
                for (NElement m : Tson.members(res)) {
                    declaredResponses.add(Tson.nameOf(m));
                }
            }
        }

        NObjectElementBuilder r = Tson.obj();
        // the document id: 'info.id' is equivalent to 'custom.openapi-document-id'
        String documentId = null;
        NElement customValue = null;
        for (NElement m : Tson.members(doc)) {
            String name = Tson.nameOf(m);
            if ("info".equals(name)) {
                NElement idm = Tson.member(Tson.valueOf(m), "id");
                if (idm != null) {
                    documentId = Tson.asString(Tson.valueOf(idm));
                }
            } else if ("custom".equals(name)) {
                customValue = Tson.valueOf(m);
            }
        }
        boolean customAdded = false;
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
                    if (customValue != null || documentId != null) {
                        // the custom section is always placed right after the info
                        r.add("custom", buildCustom(customValue, documentId));
                        customAdded = true;
                    }
                    break;
                }
                case "custom": {
                    if (!customAdded) {
                        r.add("custom", buildCustom(Tson.valueOf(m), documentId));
                        customAdded = true;
                    }
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
        if (!customAdded && documentId != null) {
            r.add("custom", buildCustom(null, documentId));
        }
        NElement result = dedup(r.build(), "$");
        diagnostics.throwIfErrors();
        return result;
    }

    /**
     * A named section of an object ({@code components { … }}): the members are looked up by their
     * name, whether they are written as pairs or as named containers.
     */
    private static NObjectElement sectionOf(NObjectElement container, String name) {
        if (container == null) {
            return null;
        }
        for (NElement m : Tson.members(container)) {
            if (name.equals(Tson.nameOf(m))) {
                NElement v = Tson.valueOf(m);
                return v.asObject().orNull();
            }
        }
        return null;
    }

    /**
     * Builds the {@code custom} section: the document id ({@code info.id}) is stored there, under
     * {@code openapi-document-id}, and the other members are kept as they are.
     */
    private NElement buildCustom(NElement value, String documentId) {
        NObjectElement o = value == null ? null : value.asObject().orNull();
        NObjectElementBuilder b = Tson.obj();
        boolean hasDocumentId = false;
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                if ("openapi-document-id".equals(Tson.nameOf(m))) {
                    hasDocumentId = true;
                }
            }
        }
        if (documentId != null && hasDocumentId) {
            diagnostics.error("E_DUPLICATE_ID",
                    "the document id is given twice, as 'info.id' and as 'custom.openapi-document-id': keep only one of them",
                    "custom.openapi-document-id");
        }
        if (documentId != null && !hasDocumentId) {
            b.add("openapi-document-id", Tson.str(documentId));
        }
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                b.add(Tson.nameOf(m), Tson.valueOf(m));
            }
        }
        return b.build();
    }

    /**
     * TSON allows a key to be repeated, JSON does not: only the last occurrence of a key is kept
     * (a warning is emitted for the duplicated keys).
     */
    private NElement dedup(NElement e, String path) {
        NObjectElement o = e.asObject().orNull();
        if (o != null) {
            List<NElement> children = Tson.members(o);
            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, Integer> last = new LinkedHashMap<>();
            for (int i = 0; i < children.size(); i++) {
                String k = Tson.nameOf(children.get(i));
                counts.put(k, counts.containsKey(k) ? counts.get(k) + 1 : 1);
                last.put(k, i);
            }
            Set<String> warned = new HashSet<>();
            NObjectElementBuilder b = Tson.obj();
            for (int i = 0; i < children.size(); i++) {
                NElement m = children.get(i);
                String k = Tson.nameOf(m);
                Integer li = last.get(k);
                if (li != null && li != i) {
                    // a superseded occurrence: only the last one is kept
                    continue;
                }
                if (counts.get(k) > 1 && warned.add(k)) {
                    diagnostics.warn("W_DUP_KEY",
                            "the key '" + k + "' is given " + counts.get(k) + " times: only the last value is kept",
                            path + "." + k);
                }
                b.add(k, dedup(Tson.valueOf(m), path + "." + k));
            }
            return b.build();
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null) {
            NArrayElementBuilder b = Tson.arr();
            int i = 0;
            for (NElement c : Tson.members(a)) {
                b.add(dedup(c, path + "[" + i + "]"));
                i++;
            }
            return b.build();
        }
        return e;
    }

    private NElement desugarInfo(NElement value, String path) {
        NObjectElement info = value.asObject().orNull();
        if (info == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(info)) {
            String name = Tson.nameOf(m);
            if ("id".equals(name)) {
                // the document id is stored in the custom section
                continue;
            }
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
                List<NElement> params = Tson.positionalParamsOf(item);
                NElement url = params.isEmpty() ? Tson.str("") : params.get(0);
                NObjectElementBuilder s = Tson.obj();
                s.add("url", url);
                // the attributes of the server: the named params of the head and the members of its body
                Set<String> seen = new LinkedHashSet<>();
                for (NPairElement pair : Tson.namedParamsOf(item)) {
                    String k = pair.key().asStringValue().orElse("");
                    if (!seen.add(k)) {
                        diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                        continue;
                    }
                    s.add(k, pair.value());
                }
                if (o != null) {
                    for (NElement m : Tson.members(o)) {
                        String k = Tson.nameOf(m);
                        if (k != null && !seen.add(k)) {
                            diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                            continue;
                        }
                        s.add(k, Tson.valueOf(m));
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
            // plain requirement object (possibly empty): verbatim
            return item;
        }
        String head = headOf(item);
        if ("all".equals(head)) {
            return desugarSecurityGroup(item, path);
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
            checkSecuritySchemeName(name, path);
            if (!declaredSecuritySchemes.isEmpty() && !declaredSecuritySchemes.contains(name)) {
                diagnostics.error("E_UNRESOLVED_REF", "unknown security scheme '" + name + "'", path);
            }
            NObjectElementBuilder o = Tson.obj();
            o.add(name, NElement.ofArray());
            return o.build();
        }
        return item;
    }

    /**
     * A security group {@code all(A B)} is a <b>single</b> security requirement: all its members
     * are required at the same time (and they all belong to the same requirement object).
     */
    private NElement desugarSecurityGroup(NElement item, String path) {
        List<NElement> members = new ArrayList<>();
        NTupleElement t = item.asTuple().orNull();
        if (t != null) {
            members.addAll(t.params());
        } else {
            members.addAll(Tson.members(item));
        }
        if (members.isEmpty()) {
            diagnostics.error("E_BAD_SECURITY", "security group 'all' must list at least one security scheme, as in all(ApiKeyAuth JWTAuth)", path);
            return NElement.ofObject();
        }
        NObjectElementBuilder o = Tson.obj();
        for (NElement e : members) {
            NElement requirement = desugarSecurityItem(e, path);
            NObjectElement ro = requirement.asObject().orNull();
            if (ro == null) {
                diagnostics.error("E_BAD_SECURITY", "'" + Tson.nameOf(e) + "' is not a valid security scheme", path);
                continue;
            }
            for (NElement m : Tson.members(ro)) {
                o.add(Tson.nameOf(m), Tson.valueOf(m));
            }
        }
        return o.build();
    }

    /**
     * {@code all} introduces a security group, so it cannot be used as a security scheme name.
     */
    private void checkSecuritySchemeName(String name, String path) {
        if (Tson.RESERVED_SECURITY_NAMES.contains(name)) {
            diagnostics.error("E_RESERVED_NAME", "security scheme name '" + name + "' is reserved", path);
        }
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
                // path item object: @deprecated is expanded, the condensed operations are desugared
                // (the path is the one of the item when they don't repeat it), the other members are
                // kept as-is
                NObjectElement item = Tson.valueOf(m).asObject().orNull();
                NObjectElementBuilder pb = Tson.obj();
                if (item != null) {
                    for (NElementAnnotation a : Tson.annotationsOf(m)) {
                        if ("deprecated".equals(a.name())) {
                            if (!item.get("deprecated").isPresent()) {
                                pb.add("deprecated", a.param(0).isPresent() ? a.param(0).get() : Tson.ofBoolean(true));
                            }
                        } else {
                            diagnostics.error("E_UNKNOWN_ANNOTATION",
                                    "annotation @" + a.name() + " is not allowed on a path item",
                                    path + "." + name);
                        }
                    }
                    for (NElement im : Tson.members(item)) {
                        String imName = Tson.nameOf(im);
                        if (imName == null) {
                            continue;
                        }
                        if (Tson.isMethod(imName) && Tson.isHeaded(im)) {
                            String method = imName.toLowerCase();
                            List<NElement> params = Tson.headParams(im);
                            String opPath = firstPositional(params);
                            if (opPath == null) {
                                opPath = name;
                            }
                            pb.add(method, desugarOperation(im, method, opPath, params,
                                    path + "." + name + "." + method));
                        } else {
                            pb.add(imName, Tson.valueOf(im));
                        }
                    }
                } else {
                    pb.addAll(Tson.members(Tson.valueOf(m)));
                }
                grouped.put(name, pb);
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
                    if (requestBody != null) {
                        diagnostics.error("E_DUP_KEY", "the operation has two request bodies: 'in' and 'requestBody'", path);
                        continue;
                    }
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
                } else if ("requestBody".equals(name)) {
                    if (requestBody != null) {
                        diagnostics.error("E_DUP_KEY", "the operation has two request bodies: 'in' and 'requestBody'", path);
                        continue;
                    }
                    NElement rbValue = Tson.valueOf(m);
                    if (isCondensedRequestBody(m, rbValue)) {
                        // the condensed form: requestBody: @required Type(mediaType)
                        requestBody = desugarRequestBody(m, path + ".requestBody");
                    } else {
                        // the plain OpenAPI form: requestBody: { description: ..., content: { ... } }
                        if (!opKeys.add(name)) {
                            diagnostics.error("E_DUP_KEY", "duplicate operation key '" + name + "'", path);
                        }
                        op.add(name, Tson.unnamed(rbValue));
                    }
                } else if ("security".equals(name)) {
                    if (!opKeys.add(name)) {
                        diagnostics.error("E_DUP_KEY", "duplicate operation key '" + name + "'", path);
                    }
                    op.add(name, desugarSecurityList(Tson.valueOf(m), path + ".security"));
                } else if ("responses".equals(name)) {
                    // the plain OpenAPI form: the responses are collected like the condensed ones,
                    // so that they always come after the request body and the parameters
                    NElement rv = Tson.valueOf(m);
                    NObjectElement ro = rv.asObject().orNull();
                    if (ro == null) {
                        diagnostics.error("E_BAD_ARGS", "the responses of an operation must be an object", path);
                        continue;
                    }
                    for (NElement cm : Tson.members(ro)) {
                        String code = Tson.nameOf(cm);
                        if (responses.get(code).isPresent()) {
                            diagnostics.error("E_DUP_KEY", "duplicate response " + code, path);
                        }
                        responses.add(code, Tson.valueOf(cm));
                    }
                    hasResponses = true;
                } else if ("externalDocs".equals(name) || "callbacks".equals(name)
                        || "servers".equals(name) || "deprecated".equals(name)
                        || "tags".equals(name) || "summary".equals(name)
                        || "description".equals(name) || "operationId".equals(name)) {
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

    /**
     * Tells the condensed request body ({@code requestBody: @required Type(mediaType)}) from the
     * plain OpenAPI request body ({@code requestBody: { description: …, content: { … } }}).
     *
     * <p>The condensed form is recognised by its shape: it carries an annotation, it is written with
     * a head (a named tuple, a named object) or it is a bare type name. Everything else is the plain
     * form and is copied verbatim.</p>
     */
    private boolean isCondensedRequestBody(NElement member, NElement value) {
        if (!Tson.isPair(member)) {
            // requestBody { … } : the plain OpenAPI form
            return false;
        }
        if (!Tson.annotationsOf(member).isEmpty()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        if (value.asTuple().isPresent()) {
            return true;
        }
        if (value.asObject().isPresent()) {
            return value.asObject().get().isNamed();
        }
        if (value.asArray().isPresent()) {
            return value.asArray().get().isNamed();
        }
        return value.isName();
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
        String head = Tson.nameOf(typeValue);
        NObjectElementBuilder rb = Tson.obj();
        String description = null;
        NElement example = null;
        NElement examples = null;
        boolean required = Tson.hasAnnotation(member, "required");
        NElementAnnotation reqAnn = Tson.annotation(member, "required");
        if (reqAnn != null && reqAnn.param(0).isPresent()) {
            required = Tson.asBoolean(reqAnn.param(0).get(), true);
        }
        for (NElement m : memberAttrs(typeValue, path)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if ("description".equals(name)) {
                description = Tson.asString(v);
            } else if ("example".equals(name)) {
                example = v;
            } else if ("examples".equals(name)) {
                examples = v;
            } else if ("required".equals(name)) {
                required = Tson.asBoolean(v, required);
            } else {
                // the other members of the Request Body Object (headers, …) are passed through
                rb.add(name, v);
            }
        }
        if (example != null && examples != null) {
            diagnostics.error("E_DUP_KEY", "the request body has both an 'example' and an 'examples' member", path);
        }
        if (description != null) {
            rb.add("description", Tson.str(description));
        }
        if (required) {
            rb.add("required", Tson.ofBoolean(true));
        }
        if (!"void".equals(head)) {
            rb.add("content", buildContent(typeValue, head, example, examples, path));
        }
        return rb.build();
    }

    private NElement desugarResponse(NElement member, String path) {
        NElement typeValue = Tson.valueOf(member);
        String head = Tson.nameOf(typeValue);
        if (isResponseComponentRef(typeValue, head)) {
            // out(500): InternalError : a reference to a response of components.responses
            NObjectElementBuilder ref = Tson.obj();
            ref.add("$ref", Tson.str("#/components/responses/" + head));
            return ref.build();
        }
        NObjectElementBuilder resp = Tson.obj();
        String description = null;
        NElement example = null;
        NElement examples = null;
        List<NElement> otherMembers = new ArrayList<>();
        for (NElement m : memberAttrs(typeValue, path)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if ("description".equals(name)) {
                description = Tson.asString(v);
            } else if ("example".equals(name)) {
                example = v;
            } else if ("examples".equals(name)) {
                examples = v;
            } else {
                // the other members of the Response Object (headers, links, …) are passed through
                otherMembers.add(m);
            }
        }
        if (example != null && examples != null) {
            diagnostics.error("E_DUP_KEY", "the response has both an 'example' and an 'examples' member", path);
        }
        if (description == null) {
            String status = responseStatusOf(member);
            String reason = Tson.reasonPhrase(status);
            description = reason != null ? reason : "OK";
            diagnostics.warn("W_NO_DESCRIPTION", "response " + status + " has no description; using '" + description + "'", path);
        }
        resp.add("description", Tson.str(description));
        if (!"void".equals(head)) {
            resp.add("content", buildContent(typeValue, head, example, examples, path));
        }
        for (NElement m : otherMembers) {
            resp.add(Tson.nameOf(m), Tson.valueOf(m));
        }
        return resp.build();
    }

    /**
     * The attributes of a request body or of a response: the named params of the head
     * ({@code out(200): T(json, description: "d")}) and the members of its body
     * ({@code out(200): T(json) { description: "d" }}). The same attribute in both places is an error.
     */
    private List<NElement> memberAttrs(NElement head, String path) {
        List<NElement> r = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (NPairElement pair : Tson.namedParamsOf(head)) {
            String k = pair.key().asStringValue().orElse("");
            if (!seen.add(k)) {
                diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                continue;
            }
            r.add(pair);
        }
        NObjectElement body = head.asObject().orNull();
        if (body != null) {
            for (NElement m : Tson.members(body)) {
                String k = Tson.nameOf(m);
                if (k == null) {
                    continue;
                }
                if (!seen.add(k)) {
                    diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                    continue;
                }
                r.add(m);
            }
        }
        return r;
    }

    /**
     * True when the type of a response is a reference to a component response: the head has no
     * parameter at all (no media type) and its name is declared in {@code components.responses}.
     */
    private boolean isResponseComponentRef(NElement typeValue, String head) {
        if (head == null || "void".equals(head)) {
            return false;
        }
        if (!Tson.paramsOf(typeValue).isEmpty()) {
            return false;
        }
        return declaredResponses.contains(head);
    }

    private String responseStatusOf(NElement member) {
        String s = firstPositional(Tson.headParams(member));
        return s == null ? "default" : s;
    }

    private NElement buildContent(NElement typeValue, String head, NElement example, NElement examples, String path) {
        NElement schema = schemaOfTypeHead(typeValue, head, path);
        List<NElement> mediaParams = Tson.positionalParamsOf(typeValue);
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
            if (examples != null) {
                entry.add("examples", examples);
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
            return buildArraySchema(typeValue, path, null);
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
            } else if ("responses".equals(name)) {
                r.add("responses", desugarResponses(Tson.valueOf(m), path + ".responses"));
            } else if ("headers".equals(name)) {
                r.add("headers", desugarHeaders(Tson.valueOf(m), path + ".headers"));
            } else {
                r.add(name, Tson.unnamed(Tson.valueOf(m)));
            }
        }
        return r.build();
    }

    // ------------------------------------------------------------------ component responses

    /**
     * The {@code components.responses} section: each member is a Response Object, written with the
     * type of its content and its attributes ({@code InternalError: ErrorResponse(json,
     * description: "…", example: {…})}).
     */
    private NElement desugarResponses(NElement value, String path) {
        NObjectElement o = value.asObject().orNull();
        if (o == null) {
            return value;
        }
        NObjectElementBuilder r = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            r.add(name, desugarResponseComponent(m, path + "." + name));
        }
        return r.build();
    }

    private NElement desugarResponseComponent(NElement member, String path) {
        NElement typeValue = Tson.valueOf(member);
        String head = Tson.nameOf(typeValue);
        if (head == null) {
            // the plain OpenAPI form: the response object is kept as it is
            return Tson.unnamed(typeValue);
        }
        NObjectElementBuilder resp = Tson.obj();
        String description = null;
        NElement example = null;
        NElement examples = null;
        List<NElement> otherMembers = new ArrayList<>();
        for (NElement m : memberAttrs(typeValue, path)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if ("description".equals(name)) {
                description = Tson.asString(v);
            } else if ("example".equals(name)) {
                example = v;
            } else if ("examples".equals(name)) {
                examples = v;
            } else {
                otherMembers.add(m);
            }
        }
        if (example != null && examples != null) {
            diagnostics.error("E_DUP_KEY", "the response has both an 'example' and an 'examples' member", path);
        }
        if (description == null) {
            description = "Response";
            diagnostics.warn("W_NO_DESCRIPTION", "the response component has no description; using '" + description + "'", path);
        }
        resp.add("description", Tson.str(description));
        if (!"void".equals(head)) {
            resp.add("content", buildContent(typeValue, head, example, examples, path));
        }
        for (NElement m : otherMembers) {
            resp.add(Tson.nameOf(m), Tson.valueOf(m));
        }
        return resp.build();
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
        SchemaExtras extras = SchemaExtras.of(member, path, "a schema declaration");
        for (NElementAnnotation a : Tson.annotationsOf(member)) {
            if (!"description".equals(a.name()) && !"example".equals(a.name())
                    && !"deprecated".equals(a.name()) && !"raw".equals(a.name())) {
                diagnostics.error("E_UNKNOWN_ANNOTATION", "annotation @" + a.name() + " is not allowed on a schema declaration", path);
            }
        }
        NElement value = Tson.valueOf(member);
        NElement out = schemaOf(value, SchemaContext.DECLARATION, path, extras);
        return out;
    }

    private enum SchemaContext {PROPERTY, DECLARATION}

    /**
     * The description and the example of a schema, given as annotations ({@code @description("…")},
     * {@code @example({…})}) on the declaration or on the property.
     */
    private static final class SchemaExtras {
        String description;
        NElement example;
        NElement deprecated;

        static SchemaExtras of(NElement member, String path, String what) {
            SchemaExtras r = new SchemaExtras();
            NElementAnnotation da = Tson.annotation(member, "description");
            if (da != null && da.param(0).isPresent()) {
                r.description = Tson.asString(da.param(0).get());
            }
            NElementAnnotation ea = Tson.annotation(member, "example");
            if (ea != null && ea.param(0).isPresent()) {
                r.example = ea.param(0).get();
            }
            NElementAnnotation ra = Tson.annotation(member, "deprecated");
            if (ra != null) {
                r.deprecated = ra.param(0).isPresent() ? ra.param(0).get() : Tson.ofBoolean(true);
            }
            return r;
        }
    }

    private NElement schemaOf(NElement value, SchemaContext ctx, String path) {
        return schemaOf(value, ctx, path, null);
    }

    /**
     * Expands a type expression into an OpenAPI schema element.
     */
    private NElement schemaOf(NElement value, SchemaContext ctx, String path, SchemaExtras extras) {
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
                return buildObjectSchema(value, ctx, extras, path);
            }
            if ("enum".equals(head)) {
                return buildEnumSchema(value, path, extras);
            }
            if ("timestamp".equals(head) || "datetime".equals(head)) {
                return buildTimestampSchema(value, path, extras);
            }
            if (Tson.isPrimitive(head)) {
                return buildPrimitiveSchema(head, mergedAttrs(value, path), path, extras);
            }
            if (ctx == SchemaContext.DECLARATION) {
                // Name { props } : implicit object
                return buildObjectSchema(value, ctx, extras, path);
            }
            // property position: untyped, verbatim body
            return Tson.unnamed(value);
        }
        if (asArr != null) {
            if (Tson.isHeaded(value) && "array".equals(Tson.nameOf(value))) {
                return buildArraySchema(value, path, extras);
            }
            return Tson.unnamed(value);
        }
        NTupleElement t = value.asTuple().orNull();
        String head = t != null ? Tson.nameOf(value) : Tson.asString(value);
        if (head == null) {
            return value;
        }
        if (t != null) {
            if ("enum".equals(head)) {
                return buildEnumSchema(value, path, extras);
            }
            if ("timestamp".equals(head) || "datetime".equals(head)) {
                return buildTimestampSchema(value, path, extras);
            }
            if (Tson.isPrimitive(head)) {
                return buildPrimitiveSchema(head, mergedAttrs(value, path), path, extras);
            }
            // reference with attrs: the attributes document the reference and are kept with it
            checkSchemaRef(head, path);
            NObjectElementBuilder b = Tson.obj();
            b.add("$ref", Tson.str("#/components/schemas/" + head));
            for (NElement a : Tson.paramsOf(value)) {
                NPairElement pair = a.asPair().orNull();
                if (pair != null) {
                    b.add(pair.key().asStringValue().get(), pair.value());
                }
            }
            addSchemaExtras(b, extras, path);
            return b.build();
        }
        // primitive / reference
        if ("enum".equals(head)) {
            diagnostics.error("E_BAD_ARGS", "enum requires a non-empty 'values' list, as in enum(values: [A B])", path);
            return NElement.ofObject();
        }
        if ("timestamp".equals(head)) {
            return buildTimestampSchema(value, path, extras);
        }
        if ("datetime".equals(head)) {
            // 'datetime' is an accepted alias of 'timestamp'
            return buildTimestampSchema(value, path, extras);
        }
        if (Tson.isPrimitive(head)) {
            return buildPrimitiveSchema(head, Collections.<NElement>emptyList(), path, extras);
        }
        if ("void".equals(head)) {
            diagnostics.error("E_VOID_OUTSIDE_RESPONSE", "void is only allowed in an out(...) type", path);
            return NElement.ofObject();
        }
        checkSchemaRef(head, path);
        NObjectElementBuilder b = Tson.obj();
        b.add("$ref", Tson.str("#/components/schemas/" + head));
        addSchemaExtras(b, extras, path);
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

    /**
     * True when the pair is flagged {@code @metadata}: inside a body the member is then a keyword
     * of the type itself ({@code @metadata description: …}) and not a property.
     */
    private static boolean isMetadataMember(NElement m) {
        return Tson.hasMemberAnnotation(m, "metadata");
    }

    /**
     * The attributes of a type head: the pairs given in the parentheses and the pairs of the
     * trailing body. The same keyword in both places is an error ({@code E_DUPLICATE_ATTR}).
     *
     * <p>This is used for the types that have no properties (primitives, {@code enum},
     * {@code timestamp}): every member of their body is a keyword of the type, so
     * {@code string { description: "d" }} is the description of the string and not a property.</p>
     */
    private List<NElement> mergedAttrs(NElement value, String path) {
        List<NElement> attrs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (NElement p : Tson.paramsOf(value)) {
            attrs.add(p);
            seen.add(attrNameOf(p));
        }
        for (NElement m : Tson.members(value)) {
            NPairElement pair = m.asPair().orNull();
            if (pair == null) {
                continue;
            }
            String k = attrNameOf(m);
            if (!seen.add(k)) {
                diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                continue;
            }
            attrs.add(pair);
        }
        return attrs;
    }

    private static String attrNameOf(NElement member) {
        NPairElement pair = member.asPair().orNull();
        if (pair != null) {
            String s = pair.key().asStringValue().orElse(null);
            if (s != null) {
                return s;
            }
        }
        return Tson.nameOf(member);
    }

    /**
     * The {@code enum(values: [A B])} sugar is a string schema with an {@code enum} keyword.
     */
    private NElement buildEnumSchema(NElement value, String path, SchemaExtras extras) {
        NElement values = null;
        List<NElement> attrs = new ArrayList<>();
        for (NElement a : mergedAttrs(value, path)) {
            NPairElement pair = a.asPair().get();
            String k = pair.key().asStringValue().get();
            if ("values".equals(k)) {
                values = pair.value();
            } else {
                attrs.add(pair);
            }
        }
        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str("string"));
        if (values == null || !values.asArray().isPresent() || values.asArray().get().size() == 0) {
            diagnostics.error("E_BAD_ARGS", "enum requires a non-empty 'values' list, as in enum(values: [A B])", path);
        } else {
            b.add("enum", values);
        }
        for (NElement a : attrs) {
            NPairElement pair = a.asPair().get();
            String k = pair.key().asStringValue().get();
            if ("type".equals(k)) {
                diagnostics.error("E_BAD_ARGS", "enum implies type: string; remove the type attribute", path);
                continue;
            }
            b.add(k, pair.value());
        }
        addSchemaExtras(b, extras, path);
        return b.build();
    }

    /**
     * The {@code timestamp} sugar is a string schema with the {@code date-time} format.
     */
    private NElement buildTimestampSchema(NElement value, String path, SchemaExtras extras) {
        List<NElement> attrs = new ArrayList<>();
        for (NElement a : mergedAttrs(value, path)) {
            NPairElement pair = a.asPair().get();
            String k = pair.key().asStringValue().get();
            if ("format".equals(k)) {
                diagnostics.error("E_BAD_ARGS", "timestamp implies format: date-time; remove the format attribute", path);
                continue;
            }
            attrs.add(pair);
        }
        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str("string"));
        b.add("format", Tson.str("date-time"));
        for (NElement a : attrs) {
            NPairElement pair = a.asPair().get();
            b.add(pair.key().asStringValue().get(), pair.value());
        }
        addSchemaExtras(b, extras, path);
        return b.build();
    }

    /**
     * Appends the description and the example given as annotations to a schema.
     */
    private void addSchemaExtras(NObjectElementBuilder b, SchemaExtras extras, String path) {
        if (extras == null) {
            return;
        }
        if (extras.description != null) {
            b.add("description", Tson.str(extras.description));
        }
        if (extras.example != null) {
            b.add("example", extras.example);
        }
        if (extras.deprecated != null && !b.get("deprecated").isPresent()) {
            b.add("deprecated", extras.deprecated);
        }
    }

    private NElement buildPrimitiveSchema(String type, List<NElement> params, String path, SchemaExtras extras) {
        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str(type));
        for (NElement p : params) {
            NPairElement pair = p.asPair().orNull();
            if (pair != null) {
                b.add(pair.key().asStringValue().get(), applyFormatSpecial(pair.key().asStringValue().get(), pair.value(), false));
            }
        }
        addSchemaExtras(b, extras, path);
        return b.build();
    }

    private NElement buildArraySchema(NElement value, String path, SchemaExtras extras) {
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
        if (extras != null) {
            addSchemaExtras(b, extras, path);
        }
        return b.build();
    }

    private NElement buildObjectSchema(NElement value, SchemaContext ctx, SchemaExtras extras, String path) {
        NObjectElement o = value.asObject().get();
        NObjectElementBuilder props = Tson.obj();
        List<String> required = new ArrayList<>();

        for (NElement m : Tson.members(o)) {
            String pname = Tson.nameOf(m);
            if (pname == null) {
                continue;
            }
            if ("properties".equals(pname) && isPropertiesBlock(Tson.valueOf(m))) {
                // an explicit properties block: its members are the properties of this schema
                for (NElement pm : Tson.members(Tson.valueOf(m))) {
                    addProperty(pm, props, required, path);
                }
                continue;
            }
            if (isMetadataMember(m)) {
                // flagged @metadata: a keyword of the schema itself, not a property
                continue;
            }
            addProperty(m, props, required, path);
        }

        // params: attrs + extra required
        List<NElement> params = Tson.paramsOf(value);
        List<NElement> attrs = new ArrayList<>();
        List<String> extraRequired = new ArrayList<>();
        String paramDescription = null;
        NElement paramExample = null;
        NElement paramDeprecated = null;
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
            } else if ("description".equals(k)) {
                // the description of the schema itself, as the @description annotation gives it
                paramDescription = Tson.asString(pair.value());
            } else if ("example".equals(k)) {
                // the example of the schema itself, as the @example annotation gives it
                paramExample = pair.value();
            } else if ("deprecated".equals(k)) {
                paramDeprecated = pair.value();
            } else {
                attrs.add(pair);
            }
        }

        // the description and the example may come from the annotations or from a @metadata member
        String description = extras == null ? null : extras.description;
        NElement example = extras == null ? null : extras.example;
        NElement deprecated = extras == null ? null : extras.deprecated;
        if (description != null && paramDescription != null) {
            diagnostics.error("E_DUPLICATE_ATTR", "attribute 'description' is given more than once", path);
        } else if (description == null) {
            description = paramDescription;
        }
        if (example != null && paramExample != null) {
            diagnostics.error("E_DUPLICATE_ATTR", "attribute 'example' is given more than once", path);
        } else if (example == null) {
            example = paramExample;
        }
        if (deprecated != null && paramDeprecated != null) {
            diagnostics.error("E_DUPLICATE_ATTR", "attribute 'deprecated' is given more than once", path);
        } else if (deprecated == null) {
            deprecated = paramDeprecated;
        }
        List<NElement> metaMembers = new ArrayList<>();
        for (NElement m : metaMembersOf(o)) {
            String k = attrNameOf(m);
            if ("description".equals(k)) {
                if (description != null) {
                    diagnostics.error("E_DUPLICATE_ATTR", "attribute 'description' is given more than once", path);
                } else {
                    description = Tson.asString(Tson.valueOf(m));
                }
            } else if ("example".equals(k)) {
                if (example != null) {
                    diagnostics.error("E_DUPLICATE_ATTR", "attribute 'example' is given more than once", path);
                } else {
                    example = Tson.valueOf(m);
                }
            } else if ("deprecated".equals(k)) {
                if (deprecated != null) {
                    diagnostics.error("E_DUPLICATE_ATTR", "attribute 'deprecated' is given more than once", path);
                } else {
                    deprecated = Tson.valueOf(m);
                }
            } else {
                metaMembers.add(m);
            }
        }

        NObjectElementBuilder b = Tson.obj();
        b.add("type", Tson.str("object"));
        for (NElement p : attrs) {
            NPairElement pair = p.asPair().get();
            String k = pair.key().asStringValue().get();
            b.add(k, applyFormatSpecial(k, pair.value(), true));
        }
        if (description != null) {
            b.add("description", Tson.str(description));
        }
        if (deprecated != null) {
            b.add("deprecated", deprecated);
        }
        for (NElement m : metaMembers) {
            b.add(attrNameOf(m), Tson.valueOf(m));
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

    /**
     * A {@code properties { … }} block is an object whose members are the properties of the schema.
     */
    private boolean isPropertiesBlock(NElement value) {
        return value != null && value.asObject().isPresent();
    }

    /**
     * The members of the body of an object schema that are flagged {@code @metadata}: they are
     * keywords of the schema itself, not properties.
     */
    private List<NElement> metaMembersOf(NObjectElement body) {
        List<NElement> r = new ArrayList<>();
        for (NElement m : Tson.members(body)) {
            if (isMetadataMember(m)) {
                r.add(m);
            }
        }
        return r;
    }

    /**
     * Adds one property (and its {@code required} flag) to the given schema.
     */
    private void addProperty(NElement m, NObjectElementBuilder props, List<String> required, String path) {
        String pname = Tson.nameOf(m);
        if (pname == null) {
            return;
        }
        for (NElementAnnotation a : Tson.annotationsOf(m)) {
            if (!"required".equals(a.name()) && !"metadata".equals(a.name())
                    && !"description".equals(a.name()) && !"example".equals(a.name())
                    && !"deprecated".equals(a.name())) {
                diagnostics.error("E_UNKNOWN_ANNOTATION", "annotation @" + a.name() + " is not allowed on a property", path);
            }
        }
        SchemaExtras extras = SchemaExtras.of(m, path, "a property");
        NElement propSchema = schemaOf(Tson.valueOf(m), SchemaContext.PROPERTY, path + ".properties." + pname, extras);
        props.add(pname, propSchema);
        NElementAnnotation ra = Tson.annotation(m, "required");
        if (ra != null) {
            boolean req = ra.param(0).isPresent() ? Tson.asBoolean(ra.param(0).get(), true) : true;
            if (req) {
                required.add(pname);
            }
        }
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
            checkSecuritySchemeName(name, path + "." + name);
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
            b2.add("scheme", Tson.str(firstPositional(Tson.positionalParamsOf(value))));
            for (NElement m : schemeMembers(value, vo, path)) {
                b2.add(Tson.nameOf(m), Tson.valueOf(m));
            }
            return b2.build();
        }
        if ("apiKey".equals(head)) {
            NObjectElementBuilder b2 = Tson.obj();
            b2.add("type", Tson.str("apiKey"));
            String in = null;
            String nm = null;
            int argCount = 0;
            for (NPairElement p : Tson.namedParamsOf(value)) {
                String k = p.key().asStringValue().get();
                if ("header".equals(k) || "query".equals(k) || "cookie".equals(k)) {
                    argCount++;
                    in = k;
                    nm = Tson.asString(p.value());
                }
            }
            if (argCount != 1 || in == null) {
                diagnostics.error("E_APIKEY_ARGS",
                        "apiKey takes exactly one location argument, the place of the key: write apiKey(header: X-API-KEY), apiKey(query: api_key) or apiKey(cookie: session); the other arguments are the attributes of the scheme",
                        path);
            } else {
                b2.add("name", Tson.str(nm == null ? "" : nm));
                b2.add("in", Tson.str(in));
            }
            for (NElement m : schemeMembers(value, vo, path)) {
                b2.add(Tson.nameOf(m), Tson.valueOf(m));
            }
            return b2.build();
        }
        if ("openIdConnect".equals(head)) {
            NObjectElementBuilder b2 = Tson.obj();
            b2.add("type", Tson.str("openIdConnect"));
            String url = null;
            for (NPairElement p : Tson.namedParamsOf(value)) {
                if ("url".equals(p.key().asStringValue().get())) {
                    url = Tson.asString(p.value());
                }
            }
            b2.add("openIdConnectUrl", Tson.str(url == null ? "" : url));
            for (NElement m : schemeMembers(value, vo, path)) {
                b2.add(Tson.nameOf(m), Tson.valueOf(m));
            }
            return b2.build();
        }
        // oauth2 { flows { ... } }
        NObjectElementBuilder b2 = Tson.obj();
        b2.add("type", Tson.str("oauth2"));
        for (NElement m : schemeMembers(value, vo, path)) {
            b2.add(Tson.nameOf(m), Tson.valueOf(m));
        }
        return b2.build();
    }

    /**
     * The members of a security scheme: the named params of its head ({@code apiKey(header: X-API-KEY,
     * description: "…")}) and the members of its body ({@code apiKey(header: X-API-KEY) { description: "…" }}).
     */
    private List<NElement> schemeMembers(NElement value, NObjectElement body, String path) {
        List<NElement> r = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (NPairElement pair : Tson.namedParamsOf(value)) {
            String k = pair.key().asStringValue().orElse("");
            if ("header".equals(k) || "query".equals(k) || "cookie".equals(k) || "url".equals(k)) {
                // the argument of the scheme, not one of its members
                continue;
            }
            if (!seen.add(k)) {
                diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                continue;
            }
            r.add(pair);
        }
        if (body != null) {
            for (NElement m : Tson.members(body)) {
                String k = Tson.nameOf(m);
                if (k != null && !seen.add(k)) {
                    diagnostics.error("E_DUPLICATE_ATTR", "attribute '" + k + "' is given more than once", path);
                    continue;
                }
                r.add(m);
            }
        }
        return r;
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
