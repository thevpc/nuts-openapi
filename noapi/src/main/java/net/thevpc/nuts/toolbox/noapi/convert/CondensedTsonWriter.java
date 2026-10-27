package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.*;

import java.util.*;

/**
 * Condensed TSON writer. Emits the canonical condensed text for an OpenAPI or config tree.
 *
 * <p>The writer never emits a comma (the reader accepts whitespace separators only), never emits
 * a {@code ¶}/{@code ¶¶} line string (they cannot be used in an inline position) and quotes every
 * string value except in the positions listed in {@link Bare} (see §0 of the syntax reference).</p>
 *
 * <p>Self-verification is performed by {@link NoApiConversion}.</p>
 */
public class CondensedTsonWriter {

    private final StringBuilder sb = new StringBuilder();
    private int indent = 0;

    /**
     * A description longer than this is written in the body of the type instead of its head: the
     * head of a primitive stays readable and the order of the members is preserved.
     */
    private static final int DESCRIPTION_IN_BODY = 60;

    private static final String BARE_TOKEN = "[A-Za-z_][A-Za-z0-9_.-]*";

    /** The members an object schema may have to be written in the condensed form. */
    private static final Set<String> OBJECT_SCHEMA_KEYS = new LinkedHashSet<>(Arrays.asList(
            "type", "format", "description", "properties", "required", "example", "deprecated"));

    private static final Map<String, String> MEDIA_REVERSE = new HashMap<>();

    static {
        for (Map.Entry<String, String> e : Tson.MEDIA_SHORTHANDS.entrySet()) {
            MEDIA_REVERSE.put(e.getValue(), e.getKey());
        }
    }

    /**
     * The position of a string value; with the value itself it decides whether the writer may emit
     * it bare (see §0 of the syntax reference). Everything that is not listed here is free text
     * and is always quoted.
     */
    private enum Bare {
        /** free text, versions, paths, URLs: always quoted */
        NEVER,
        /** keys, schema names, security scheme names, scope names */
        KEY,
        /** enum values */
        ENUM,
        /** non-object {@code format} */
        FORMAT,
        /** http {@code scheme} and {@code bearerFormat} */
        SCHEME,
        /** the name argument of {@code apiKey(...)} */
        APIKEY_NAME,
        /** {@code target-id} and {@code openapi-document-id} */
        DOC_ID
    }

    public static String writeDocument(NElement root) {
        CondensedTsonWriter w = new CondensedTsonWriter();
        w.doc(root);
        return w.sb.toString();
    }

    public static String writeConfig(NElement root) {
        CondensedTsonWriter w = new CondensedTsonWriter();
        w.config(root);
        return w.sb.toString();
    }

    // ------------------------------------------------------------------ document

    private void doc(NElement root) {
        NObjectElement o = root.asObject().orNull();
        boolean hasInfo = o != null && o.get("info").isPresent();
        String documentId = null;
        if (o != null) {
            NObjectElement custom = o.getObject("custom").orNull();
            if (custom != null) {
                documentId = Tson.asString(custom.get("openapi-document-id").orNull());
            }
        }
        boolean first = true;
        for (NElement m : Tson.members(root)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if (!first) {
                blank();
            }
            first = false;
            switch (name) {
                case "openapi": {
                    if (hasInfo) {
                        line("@openapi(" + scalar(v, Bare.NEVER) + ")");
                    } else {
                        line("openapi: " + scalar(v, Bare.NEVER));
                    }
                    break;
                }
                case "info": {
                    writeInfo(v, documentId);
                    break;
                }
                case "custom": {
                    writeCustom(v, documentId != null && hasInfo);
                    break;
                }
                case "servers": {
                    writeServers(v);
                    break;
                }
                case "paths": {
                    writePaths(v);
                    break;
                }
                case "components": {
                    writeComponents(v);
                    break;
                }
                case "tags": {
                    writeNamedPlain("tags", v);
                    break;
                }
                case "security": {
                    writeSecurity(v);
                    break;
                }
                default: {
                    writePlainMember(name, v);
                    break;
                }
            }
        }
    }

    private void writeInfo(NElement v, String documentId) {
        NObjectElement o = v.asObject().orNull();
        String id = documentId;
        if (o != null && o.get("id").isPresent()) {
            id = Tson.asString(o.get("id").orNull());
        }
        line("info {");
        indent++;
        if (id != null) {
            line("id: " + scalar(NElement.ofString(id), Bare.DOC_ID));
        }
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                if ("id".equals(k)) {
                    continue;
                }
                NElement val = Tson.valueOf(m);
                if ("changes".equals(k)) {
                    writeChanges(val);
                } else {
                    line(key(k) + ": " + plainValue(val, indent));
                }
            }
        }
        indent--;
        line("}");
    }

    private void writeChanges(NElement v) {
        NArrayElement a = v.asArray().orNull();
        if (a == null) {
            line("changes: " + plainValue(v, indent));
            return;
        }
        List<NElement> items = Tson.members(a);
        if (items.isEmpty()) {
            line("changes: []");
            return;
        }
        line("changes [");
        indent++;
        for (NElement it : items) {
            NObjectElement io = it.asObject().orNull();
            if (io == null) {
                line(plainValue(it, indent));
                continue;
            }
            lineBlockObject(io);
        }
        indent--;
        line("]");
    }

    private void writeCustom(NElement v, boolean skipDocumentId) {
        NObjectElement o = v.asObject().orNull();
        List<String> keys = new ArrayList<>();
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                if (skipDocumentId && "openapi-document-id".equals(k)) {
                    // written as info.id
                    continue;
                }
                keys.add(k);
            }
        }
        if (keys.isEmpty()) {
            // nothing to write: the section would be empty
            return;
        }
        line("custom {");
        indent++;
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                if (skipDocumentId && "openapi-document-id".equals(k)) {
                    // written as info.id
                    continue;
                }
                line(key(k) + ": " + plainValue(Tson.valueOf(m), indent));
            }
        }
        indent--;
        line("}");
    }

    // ------------------------------------------------------------------ servers / security

    private void writeServers(NElement v) {
        List<NElement> items = Tson.members(v);
        if (items.isEmpty()) {
            line("servers []");
            return;
        }
        line("servers [");
        indent++;
        for (NElement it : items) {
            NObjectElement o = it.asObject().orNull();
            NElement url = o == null ? null : o.get("url").orNull();
            if (url == null) {
                line(plainValue(it, indent));
                continue;
            }
            // the attributes of the server go in the parentheses of its head
            List<String> args = new ArrayList<>();
            args.add(scalar(url, Bare.NEVER));
            List<String> bodyMembers = new ArrayList<>();
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                if ("url".equals(k)) {
                    continue;
                }
                NElement mv = Tson.valueOf(m);
                if (mv.asObject().isPresent() || mv.asArray().isPresent()) {
                    bodyMembers.add(key(k) + ": " + plainValue(mv, indent));
                } else {
                    args.add(key(k) + ": " + plainValue(mv, indent));
                }
            }
            StringBuilder head = new StringBuilder("url(").append(String.join(" ", args)).append(")");
            if (bodyMembers.isEmpty()) {
                line(head.toString());
                continue;
            }
            line(head + " {");
            indent++;
            for (String m : bodyMembers) {
                line(m);
            }
            indent--;
            line("}");
        }
        indent--;
        line("]");
    }

    private void writeSecurity(NElement v) {
        List<NElement> items = Tson.members(v);
        if (items.isEmpty()) {
            line("security []");
            return;
        }
        line("security [");
        indent++;
        for (NElement it : items) {
            line(securityRequirementExpr(it));
        }
        indent--;
        line("]");
    }

    /**
     * A security requirement: one scheme gives {@code A} or {@code A(read)}, several schemes give
     * the group {@code all(A B(read))} (all of them are required at the same time).
     */
    private String securityRequirementExpr(NElement it) {
        NObjectElement o = it.asObject().orNull();
        if (o == null) {
            return plainValue(it, indent);
        }
        List<NElement> ms = Tson.members(o);
        if (ms.isEmpty()) {
            // an empty requirement (optional authentication) is written verbatim
            return "{}";
        }
        if (ms.size() == 1) {
            return securitySchemeExpr(ms.get(0));
        }
        StringBuilder s = new StringBuilder("all(");
        for (int i = 0; i < ms.size(); i++) {
            if (i > 0) {
                s.append(" ");
            }
            s.append(securitySchemeExpr(ms.get(i)));
        }
        return s.append(")").toString();
    }

    private String securitySchemeExpr(NElement m) {
        String name = Tson.nameOf(m);
        List<NElement> scopes = Tson.members(Tson.valueOf(m));
        if (name == null) {
            return plainValue(m, indent);
        }
        if (!isBareToken(name)) {
            // a quoted name cannot be used as a tuple head: keep the plain form
            return plainValue(m, indent);
        }
        if (scopes.isEmpty()) {
            return name;
        }
        StringBuilder s = new StringBuilder(name).append("(");
        for (int i = 0; i < scopes.size(); i++) {
            if (i > 0) {
                s.append(" ");
            }
            s.append(key(Tson.asString(scopes.get(i))));
        }
        return s.append(")").toString();
    }

    // ------------------------------------------------------------------ paths

    private void writePaths(NElement v) {
        line("paths {");
        indent++;
        for (NElement pm : Tson.members(v)) {
            String path = Tson.nameOf(pm);
            NObjectElement item = Tson.valueOf(pm).asObject().orNull();
            if (item == null) {
                line(key(path) + ": " + plainValue(Tson.valueOf(pm), indent));
                continue;
            }
            if (!isCondensablePathItem(item)) {
                line(key(path) + ": " + plainValue(item, indent));
                continue;
            }
            for (NElement mm : Tson.members(item)) {
                String method = Tson.nameOf(mm);
                if (!Tson.isMethod(method)) {
                    continue;
                }
                blank();
                writeOperation(method.toUpperCase(), path, Tson.valueOf(mm).asObject().orNull());
            }
        }
        indent--;
        line("}");
    }

    private boolean isCondensablePathItem(NObjectElement item) {
        for (NElement m : Tson.members(item)) {
            if (!Tson.isMethod(Tson.nameOf(m))) {
                return false;
            }
        }
        return true;
    }

    private static final Set<String> OP_ANNOTATIONS = new LinkedHashSet<>(Arrays.asList(
            "summary", "description", "operationId", "tags", "deprecated", "enabled", "security"));

    private void writeOperation(String method, String path, NObjectElement op) {
        if (op == null) {
            line(method + "(" + scalar(NElement.ofString(path), Bare.NEVER) + ") {}");
            return;
        }
        // the members that can be written as annotations: only when they come first, so that the
        // order of the members of the operation is preserved
        List<NElement> annMembers = new ArrayList<>();
        List<NElement> bodyMembers = new ArrayList<>();
        boolean annotationsFirst = true;
        for (NElement m : Tson.members(op)) {
            String k = Tson.nameOf(m);
            if (annotationsFirst && OP_ANNOTATIONS.contains(k)) {
                annMembers.add(m);
            } else {
                annotationsFirst = false;
                bodyMembers.add(m);
            }
        }
        for (NElement m : annMembers) {
            writeOperationAnnotation(Tson.nameOf(m), Tson.valueOf(m));
        }
        line(method + "(" + scalar(NElement.ofString(path), Bare.NEVER) + ") {");
        indent++;
        for (NElement m : bodyMembers) {
            String k = Tson.nameOf(m);
            NElement val = Tson.valueOf(m);
            switch (k) {
                case "requestBody": {
                    writeRequestBody(val);
                    break;
                }
                case "responses": {
                    writeResponses(val);
                    break;
                }
                case "parameters": {
                    writeParameters(val);
                    break;
                }
                case "security": {
                    writeSecurity(val);
                    break;
                }
                default: {
                    writePlainMember(k, val);
                    break;
                }
            }
        }
        indent--;
        line("}");
    }

    private void writeOperationAnnotation(String k, NElement v) {
        switch (k) {
            case "deprecated": {
                line(Tson.asBoolean(v, true) ? "@deprecated" : "@deprecated(false)");
                break;
            }
            case "tags": {
                StringBuilder s = new StringBuilder("@tags(");
                List<NElement> items = Tson.members(v);
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        s.append(" ");
                    }
                    s.append(scalar(items.get(i), Bare.KEY));
                }
                s.append(")");
                line(s.toString());
                break;
            }
            case "security": {
                StringBuilder s = new StringBuilder("@security(");
                List<NElement> items = Tson.members(v);
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        s.append(" ");
                    }
                    s.append(securityRequirementExpr(items.get(i)));
                }
                s.append(")");
                line(s.toString());
                break;
            }
            default: {
                line("@" + k + "(" + scalar(v, Bare.NEVER) + ")");
                break;
            }
        }
    }

    private void writeParameters(NElement v) {
        List<NElement> items = Tson.members(v);
        if (items.isEmpty()) {
            return;
        }
        line("parameters [");
        indent++;
        for (NElement it : items) {
            line(plainValue(it, indent));
        }
        indent--;
        line("]");
    }

    // ------------------------------------------------------------------ request body / responses

    private void writeRequestBody(NElement rb) {
        NObjectElement o = rb.asObject().orNull();
        if (o == null) {
            writePlainMember("requestBody", rb);
            return;
        }
        NObjectElement content = o.getObject("content").orNull();
        String media = null;
        String head = null;
        NElement example = null;
        if (content != null && content.size() == 1) {
            NElement entry = Tson.members(content).get(0);
            media = Tson.nameOf(entry);
            NObjectElement mediaObj = Tson.valueOf(entry).asObject().orNull();
            NElement schema = mediaObj == null ? null : mediaObj.get("schema").orNull();
            head = ioTypeHead(schema);
            if (head != null && mediaObj != null) {
                example = mediaObj.get("example").orNull();
            }
        }
        if (head == null) {
            // the plain OpenAPI form
            writePlainMember("requestBody", rb);
            return;
        }
        boolean required = Tson.asBoolean(o.get("required").orNull(), false);
        NElement desc = o.get("description").orNull();
        List<String> attrs = new ArrayList<>();
        attrs.add(mediaShorthand(media));
        if (desc != null) {
            attrs.add("description: " + scalar(desc, Bare.NEVER));
        }
        if (example != null) {
            attrs.add("example: " + inlineValue(example));
        }
        // the other members of the request body (the headers, …) stay in a body
        List<String> bodyMembers = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("content".equals(k) || "required".equals(k) || "description".equals(k)) {
                continue;
            }
            bodyMembers.add(key(k) + ": " + plainValue(Tson.valueOf(m), indent));
        }
        StringBuilder open = new StringBuilder();
        if (required) {
            open.append("@required ");
        }
        open.append("in: ").append(head).append("(").append(String.join(" ", attrs)).append(")");
        if (bodyMembers.isEmpty()) {
            // no body to write
            line(open.toString());
            return;
        }
        line(open.toString() + " {");
        indent++;
        for (String m : bodyMembers) {
            line(m);
        }
        indent--;
        line("}");
    }

    private void writeResponses(NElement responses) {
        if (Tson.members(responses).isEmpty()) {
            // an empty responses object is written as it is
            writePlainMember("responses", responses);
            return;
        }
        if (!canCondenseResponses(responses)) {
            writePlainMember("responses", responses);
            return;
        }
        for (NElement m : Tson.members(responses)) {
            writeResponse(Tson.nameOf(m), Tson.valueOf(m));
        }
    }

    private boolean canCondenseResponses(NElement responses) {
        for (NElement m : Tson.members(responses)) {
            if (!canCondenseResponse(Tson.valueOf(m))) {
                return false;
            }
        }
        return true;
    }

    private boolean canCondenseResponse(NElement resp) {
        NObjectElement o = resp.asObject().orNull();
        if (o == null) {
            return false;
        }
        if (o.get("$ref").isPresent()) {
            // a reference to a response of components.responses
            return isResponseComponentRef(o);
        }
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if (!"description".equals(k) && !"content".equals(k)) {
                return false;
            }
        }
        NObjectElement content = o.getObject("content").orNull();
        if (content == null || content.size() == 0) {
            return true;
        }
        if (content.size() != 1) {
            return false;
        }
        NElement entry = Tson.members(content).get(0);
        NObjectElement mediaObj = Tson.valueOf(entry).asObject().orNull();
        if (mediaObj == null) {
            return false;
        }
        for (NElement m : Tson.members(mediaObj)) {
            String k = Tson.nameOf(m);
            if (!"schema".equals(k) && !"example".equals(k)) {
                return false;
            }
        }
        return ioTypeHead(mediaObj.get("schema").orNull()) != null;
    }

    /**
     * True when the reference points to a response of {@code components.responses} (and not to a
     * schema): only then it may be written as a name ({@code out(500): InternalError}).
     */
    private boolean isResponseComponentRef(NObjectElement o) {
        String r = Tson.asString(o.get("$ref").orNull());
        return r != null && r.startsWith("#/components/responses/");
    }

    private void writeResponse(String status, NElement resp) {
        NObjectElement o = resp.asObject().orNull();
        if (o != null && o.get("$ref").isPresent() && isResponseComponentRef(o)) {
            // a reference to a response of components.responses
            line("out(" + statusToken(status) + "): " + refName(o));
            return;
        }
        String expr = responseExpr(resp);
        if (expr == null) {
            writePlainMember("responses", resp);
            return;
        }
        line("out(" + statusToken(status) + "): " + expr);
    }

    /**
     * The expression of a response: the type of its content and its attributes. Returns {@code null}
     * when the response cannot be condensed (the caller then writes the plain form).
     */
    private String responseExpr(NElement resp) {
        NObjectElement o = resp.asObject().orNull();
        if (o == null) {
            return null;
        }
        List<String> attrs = new ArrayList<>();
        List<String> bodyMembers = new ArrayList<>();
        String typePart = "void";
        NElement example = null;
        NObjectElement content = o.getObject("content").orNull();
        if (content != null && content.size() > 0) {
            if (content.size() != 1) {
                return null;
            }
            NElement entry = Tson.members(content).get(0);
            String media = Tson.nameOf(entry);
            NObjectElement mediaObj = Tson.valueOf(entry).asObject().orNull();
            if (mediaObj == null) {
                return null;
            }
            for (NElement m : Tson.members(mediaObj)) {
                String k = Tson.nameOf(m);
                if (!"schema".equals(k) && !"example".equals(k)) {
                    return null;
                }
            }
            String head = ioTypeHead(mediaObj.get("schema").orNull());
            if (head == null) {
                return null;
            }
            typePart = head;
            attrs.add(mediaShorthand(media));
            example = mediaObj.get("example").orNull();
        }
        NElement desc = null;
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("content".equals(k)) {
                continue;
            }
            if ("description".equals(k)) {
                desc = Tson.valueOf(m);
            } else {
                // the other members of the response object (the headers, the links, …) stay in a body
                bodyMembers.add(key(k) + ": " + plainValue(Tson.valueOf(m), indent));
            }
        }
        if (desc != null) {
            attrs.add("description: " + scalar(desc, Bare.NEVER));
        }
        if (example != null) {
            attrs.add("example: " + inlineValue(example));
        }
        StringBuilder s = new StringBuilder(typePart);
        if (!attrs.isEmpty()) {
            s.append("(").append(String.join(" ", attrs)).append(")");
        }
        if (bodyMembers.isEmpty()) {
            return s.toString();
        }
        s.append(" {\n");
        indent++;
        for (String m : bodyMembers) {
            s.append(indentStr()).append(m).append("\n");
        }
        indent--;
        s.append(indentStr()).append("}");
        return s.toString();
    }

    private String statusToken(String status) {
        if (status.matches("[0-9]+")) {
            return status;
        }
        if ("default".equals(status)) {
            return "default";
        }
        return scalar(NElement.ofString(status), Bare.NEVER);
    }

    private String mediaShorthand(String media) {
        String s = MEDIA_REVERSE.get(media);
        if (s != null) {
            return s;
        }
        return scalar(NElement.ofString(media), Bare.NEVER);
    }

    /**
     * The head name of an in/out schema: a primitive type or a schema reference. Returns {@code null}
     * when the schema cannot be represented by a head (then the caller emits the plain form).
     */
    private String ioTypeHead(NElement schema) {
        if (schema == null) {
            return null;
        }
        NObjectElement o = schema.asObject().orNull();
        if (o == null) {
            return null;
        }
        NElement ref = o.get("$ref").orNull();
        if (ref != null) {
            String r = Tson.asString(ref);
            int i = r.lastIndexOf('/');
            return i >= 0 ? r.substring(i + 1) : r;
        }
        NElement type = o.get("type").orNull();
        if (type != null) {
            String t = Tson.asString(type);
            if (Tson.isPrimitive(t)) {
                // only acceptable when there are no other structural keys
                if (o.size() == 1) {
                    return t;
                }
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ components

    private void writeComponents(NElement v) {
        NObjectElement o = v.asObject().orNull();
        line("components {");
        indent++;
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                NElement val = Tson.valueOf(m);
                switch (k) {
                    case "schemas": {
                        writeSchemas(val);
                        break;
                    }
                    case "securitySchemes": {
                        writeSecuritySchemes(val);
                        break;
                    }
                    case "headers": {
                        writeHeaders(val);
                        break;
                    }
                    case "responses": {
                        writeResponseComponents(val);
                        break;
                    }
                    default: {
                        writeNamedPlain(k, val);
                        break;
                    }
                }
            }
        }
        indent--;
        line("}");
    }

    private void writeSchemas(NElement v) {
        NObjectElement o = v.asObject().orNull();
        if (o == null || o.size() == 0) {
            return;
        }
        line("schemas {");
        indent++;
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            writeSchemaDeclaration(name, Tson.valueOf(m));
        }
        indent--;
        line("}");
    }

    /**
     * The {@code components.responses} section: each member is a response, written like the
     * responses of an operation ({@code InternalError: ErrorResponse(json, description: "…")}).
     */
    private void writeResponseComponents(NElement v) {
        NObjectElement o = v.asObject().orNull();
        if (o == null || o.size() == 0) {
            return;
        }
        line("responses {");
        indent++;
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            NElement resp = Tson.valueOf(m);
            String expr = responseExpr(resp);
            if (expr == null) {
                writePlainMember(name, resp);
                continue;
            }
            line(key(name) + ": " + expr);
        }
        indent--;
        line("}");
    }

    private void writeSchemaDeclaration(String name, NElement schema) {
        NObjectElement o = schema.asObject().orNull();
        if (o == null) {
            writePlainMember(name, schema);
            return;
        }
        String ann = deprecatedAnnotation(o);
        if (!canCondenseSchema(o)) {
            line(ann + "@raw " + key(name) + ": " + plainValue(schema, indent));
            return;
        }
        if ("object".equals(Tson.asString(o.get("type").orNull())) && isBareToken(name)) {
            // the declaration of an object schema: LoginRequest(description: "…") { … }
            line(ann + objectExpr(o, name));
            return;
        }
        writeSchemaMember(ann, name, schema);
    }

    /**
     * The {@code @deprecated} annotation of a schema written as a member or a declaration; empty
     * when the schema is not deprecated.
     */
    private static String deprecatedAnnotation(NObjectElement o) {
        NElement d = o.get("deprecated").orNull();
        if (d == null || !Tson.asBoolean(d, false)) {
            return "";
        }
        return "@deprecated ";
    }

    /**
     * True when the schema can be written with a condensed head: an object schema may only have the
     * known members (any other member would be read as a property).
     */
    private boolean canCondenseSchema(NObjectElement o) {
        if (o.get("$ref").isPresent()) {
            return true;
        }
        String t = Tson.asString(o.get("type").orNull());
        if ("object".equals(t)) {
            for (NElement m : Tson.members(o)) {
                if (!OBJECT_SCHEMA_KEYS.contains(Tson.nameOf(m))) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    /**
     * Writes a schema as the value of a member: {@code name: <expression>}.
     */
    private void writeSchemaMember(String prefix, String name, NElement schema) {
        line(prefix + key(name) + ": " + schemaExpr(schema));
    }

    // ------------------------------------------------------------------ securitySchemes / headers

    private void writeSecuritySchemes(NElement v) {
        NObjectElement o = v.asObject().orNull();
        if (o == null || o.size() == 0) {
            return;
        }
        line("securitySchemes {");
        indent++;
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            NElement scheme = Tson.valueOf(m);
            NObjectElement so = scheme.asObject().orNull();
            String type = so == null ? null : Tson.asString(so.get("type").orNull());
            if ("http".equals(type)) {
                writeSchemeMember(name, "http", scalar(so.get("scheme").orNull(), Bare.SCHEME), so, "type", "scheme");
            } else if ("apiKey".equals(type)) {
                String in = Tson.asString(so.get("in").orNull());
                String nm = Tson.asString(so.get("name").orNull());
                writeSchemeMember(name, "apiKey", key(in) + ": " + scalar(NElement.ofString(nm), Bare.APIKEY_NAME), so, "type", "in", "name");
            } else if ("openIdConnect".equals(type)) {
                writeSchemeMember(name, "openIdConnect", "url: " + scalar(so.get("openIdConnectUrl").orNull(), Bare.NEVER), so, "type", "openIdConnectUrl");
            } else if ("oauth2".equals(type)) {
                writeSchemeMember(name, "oauth2", null, so, "type");
            } else {
                writePlainMember(name, scheme);
            }
        }
        indent--;
        line("}");
    }

    /**
     * Writes a security scheme: the head, with its argument and its attributes, and, when there are
     * other members, a body with them.
     */
    private void writeSchemeMember(String name, String prefix, String arg, NObjectElement so, String... skip) {
        Set<String> skipped = new HashSet<>(Arrays.asList(skip));
        List<String> args = new ArrayList<>();
        if (arg != null) {
            args.add(arg);
        }
        List<String> bodyMembers = new ArrayList<>();
        for (NElement m : Tson.members(so)) {
            String k = Tson.nameOf(m);
            if (skipped.contains(k)) {
                continue;
            }
            NElement v = Tson.valueOf(m);
            if (v.asObject().isPresent() || v.asArray().isPresent()) {
                bodyMembers.add(key(k) + ": " + plainValue(v, indent));
            } else if ("bearerFormat".equals(k)) {
                args.add(k + ": " + scalar(v, Bare.SCHEME));
            } else {
                args.add(key(k) + ": " + plainValue(v, indent));
            }
        }
        StringBuilder head = new StringBuilder(prefix);
        if (!args.isEmpty()) {
            head.append("(").append(String.join(" ", args)).append(")");
        }
        if (bodyMembers.isEmpty()) {
            line(key(name) + ": " + head);
            return;
        }
        line(key(name) + ": " + head + " {");
        indent++;
        for (String m : bodyMembers) {
            line(m);
        }
        indent--;
        line("}");
    }

    private void writeHeaders(NElement v) {
        NObjectElement o = v.asObject().orNull();
        if (o == null || o.size() == 0) {
            return;
        }
        line("headers {");
        indent++;
        for (NElement m : Tson.members(o)) {
            String name = Tson.nameOf(m);
            NElement header = Tson.valueOf(m);
            NObjectElement ho = header.asObject().orNull();
            NElement schema = ho == null ? null : ho.get("schema").orNull();
            if (schema == null) {
                writePlainMember(name, header);
                continue;
            }
            NObjectElement so = schema.asObject().orNull();
            String t = so == null ? null : Tson.asString(so.get("type").orNull());
            if (t == null || !Tson.isPrimitive(t)) {
                writePlainMember(name, header);
                continue;
            }
            StringBuilder ann = new StringBuilder();
            NElement required = ho.get("required").orNull();
            if (required != null) {
                if (Tson.asBoolean(required, true)) {
                    ann.append("@required ");
                } else {
                    ann.append("@required(false) ");
                }
            }
            NElement description = ho.get("description").orNull();
            List<String> attrs = new ArrayList<>();
            if (description != null) {
                attrs.add("description: " + scalar(description, Bare.NEVER));
            }
            for (NElement sm : Tson.members(so)) {
                String k = Tson.nameOf(sm);
                if (!"type".equals(k)) {
                    attrs.add(k + ": " + plainValue(Tson.valueOf(sm), indent));
                }
            }
            line(ann + key(name) + ": " + t + "(" + String.join(" ", attrs) + ")");
        }
        indent--;
        line("}");
    }

    // ------------------------------------------------------------------ schema type expressions

    /**
     * The expression of a schema (a property, a declaration or an array item).
     */
    private String schemaExpr(NElement schema) {
        NObjectElement o = schema.asObject().orNull();
        if (o == null) {
            return plainValue(schema, indent);
        }
        if (o.get("$ref").isPresent()) {
            return refExpr(o);
        }
        String t = Tson.asString(o.get("type").orNull());
        if ("object".equals(t)) {
            return objectExpr(o);
        }
        if ("array".equals(t)) {
            return arrayExpr(o);
        }
        if ("string".equals(t) && isEnumSchema(o)) {
            return enumExpr(o);
        }
        if ("string".equals(t) && isTimestampSchema(o)) {
            return timestampExpr(o);
        }
        if (t != null && Tson.isPrimitive(t)) {
            return primitiveExpr(o);
        }
        // untyped: inline the schema verbatim
        return plainValue(schema, indent);
    }

    private boolean isEnumSchema(NObjectElement o) {
        if (!"string".equals(Tson.asString(o.get("type").orNull()))) {
            // the enum sugar is a string schema
            return false;
        }
        NElement e = o.get("enum").orNull();
        if (e == null || !e.asArray().isPresent()) {
            return false;
        }
        for (NElement v : Tson.members(e.asArray().get())) {
            if (!v.isString() && !v.isName()) {
                return false;
            }
        }
        return true;
    }

    private boolean isTimestampSchema(NObjectElement o) {
        return "date-time".equals(Tson.asString(o.get("format").orNull()));
    }

    /**
     * The expression of an object schema: the head carries the attributes, the body carries the
     * properties and the schema example.
     */
    private String objectExpr(NObjectElement o) {
        return objectExpr(o, "object");
    }

    /**
     * An object schema: the attributes (the description, the example, …) go in the parentheses of
     * its head and the properties in its body. In a declaration the head is the name of the schema
     * ({@code LoginRequest(…) { … }}), elsewhere it is the {@code object} keyword.
     */
    private String objectExpr(NObjectElement o, String head) {
        List<Attr> attrs = schemaAttrs(o, true);
        List<String> headAttrs = new ArrayList<>();
        for (Attr a : attrs) {
            headAttrs.add(a.text);
        }
        NObjectElement props = o.getObject("properties").orNull();
        List<String> required = stringList(o.get("required").orNull());
        List<String> propNames = new ArrayList<>();
        if (props != null) {
            for (NElement m : Tson.members(props)) {
                propNames.add(Tson.nameOf(m));
            }
        }
        List<String> extraRequired = new ArrayList<>();
        for (String r : required) {
            if (!propNames.contains(r)) {
                extraRequired.add(r);
            }
        }
        if (!extraRequired.isEmpty()) {
            StringBuilder ra = new StringBuilder("required: [");
            for (int i = 0; i < extraRequired.size(); i++) {
                if (i > 0) {
                    ra.append(" ");
                }
                ra.append(scalar(NElement.ofString(extraRequired.get(i)), Bare.KEY));
            }
            ra.append("]");
            headAttrs.add(ra.toString());
        }
        StringBuilder s = new StringBuilder(head);
        if (!headAttrs.isEmpty()) {
            s.append("(").append(String.join(" ", headAttrs)).append(")");
        }
        if (props == null) {
            return s.append(" { }").toString();
        }
        s.append(" {\n");
        indent++;
        boolean useBlock = needsPropertiesBlock(props);
        if (useBlock) {
            line("properties {");
            indent++;
        }
        for (NElement m : Tson.members(props)) {
            String pname = Tson.nameOf(m);
            NElement pschema = Tson.valueOf(m);
            StringBuilder p = new StringBuilder();
            if (required.contains(pname)) {
                p.append("@required ");
            }
            NObjectElement po = pschema.asObject().orNull();
            if (po != null && Tson.asBoolean(po.get("deprecated").orNull(), false)) {
                p.append("@deprecated ");
            }
            p.append(key(pname)).append(": ").append(schemaExpr(pschema));
            s.append(indentStr()).append(p).append("\n");
        }
        if (useBlock) {
            indent--;
            s.append(indentStr()).append("}\n");
        }
        indent--;
        s.append(indentStr()).append("}");
        return s.toString();
    }

    /**
     * A property named {@code properties} whose schema is an object cannot be written in the body
     * of the object: it would be read as a properties block.
     */
    private boolean needsPropertiesBlock(NObjectElement props) {
        for (NElement m : Tson.members(props)) {
            if ("properties".equals(Tson.nameOf(m))) {
                NElement v = Tson.valueOf(m);
                if (v.asObject().isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private String arrayExpr(NObjectElement o) {
        List<Attr> attrs = schemaAttrs(o, false);
        List<String> headAttrs = new ArrayList<>();
        for (Attr a : attrs) {
            headAttrs.add(a.text);
        }
        NElement items = o.get("items").orNull();
        StringBuilder s = new StringBuilder("array");
        if (!headAttrs.isEmpty()) {
            s.append("(").append(String.join(" ", headAttrs)).append(")");
        }
        s.append("[").append(items == null ? "object" : schemaExpr(items)).append("]");
        return s.toString();
    }

    /**
     * The expression of a primitive schema: the attributes go in the head, unless the description is
     * too long: it then moves to the body (with the attributes that follow it).
     */
    private String primitiveExpr(NObjectElement o) {
        String t = Tson.asString(o.get("type").orNull());
        List<Attr> attrs = schemaAttrs(o, false);
        return headWithBody(t, attrs, null);
    }

    private String enumExpr(NObjectElement o) {
        NElement values = o.get("enum").orNull();
        List<String> items = new ArrayList<>();
        for (NElement v : Tson.members(values.asArray().get())) {
            items.add(scalar(v, Bare.ENUM));
        }
        List<Attr> attrs = new ArrayList<>();
        attrs.add(new Attr("values: [" + String.join(" ", items) + "]", false, 0));
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("type".equals(k) || "enum".equals(k)) {
                continue;
            }
            attrs.add(attrOf(k, Tson.valueOf(m), false));
        }
        return headWithBody("enum", attrs, null);
    }

    private String timestampExpr(NObjectElement o) {
        List<Attr> attrs = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("type".equals(k) || "format".equals(k)) {
                continue;
            }
            attrs.add(attrOf(k, Tson.valueOf(m), false));
        }
        return headWithBody("timestamp", attrs, null);
    }

    /**
     * Builds the head of a type and, when needed, its body: the description (and the attributes
     * that follow it) move to the body when the description is longer than
     * {@link #DESCRIPTION_IN_BODY} characters.
     */
    private String headWithBody(String head, List<Attr> attrs, List<String> bodyLines) {
        int split = -1;
        for (int i = 0; i < attrs.size(); i++) {
            if (attrs.get(i).description) {
                if (attrs.get(i).descriptionLength > DESCRIPTION_IN_BODY) {
                    split = i;
                }
                break;
            }
        }
        List<String> headAttrs = new ArrayList<>();
        List<String> bodyAttrs = new ArrayList<>();
        for (int i = 0; i < attrs.size(); i++) {
            if (split >= 0 && i >= split) {
                bodyAttrs.add(attrs.get(i).text);
            } else {
                headAttrs.add(attrs.get(i).text);
            }
        }
        StringBuilder s = new StringBuilder(head);
        if (!headAttrs.isEmpty()) {
            s.append("(").append(String.join(" ", headAttrs)).append(")");
        }
        if (bodyAttrs.isEmpty() && (bodyLines == null || bodyLines.isEmpty())) {
            return s.toString();
        }
        s.append(" {\n");
        indent++;
        for (String a : bodyAttrs) {
            s.append(indentStr()).append(a).append("\n");
        }
        if (bodyLines != null) {
            for (String b : bodyLines) {
                s.append(indentStr()).append(b).append("\n");
            }
        }
        indent--;
        s.append(indentStr()).append("}");
        return s.toString();
    }

    /**
     * The attributes of a schema, in the order of the members.
     */
    private List<Attr> schemaAttrs(NObjectElement o, boolean objectHead) {
        List<Attr> attrs = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if ("type".equals(k) || "properties".equals(k) || "required".equals(k)
                    || "items".equals(k)) {
                continue;
            }
            if ("enum".equals(k) && isEnumSchema(o)) {
                // written by the enum sugar
                continue;
            }
            if ("format".equals(k) && "string".equals(Tson.asString(o.get("type").orNull()))
                    && isTimestampSchema(o)) {
                // written by the timestamp sugar
                continue;
            }
            attrs.add(attrOf(k, v, objectHead));
        }
        return attrs;
    }

    private Attr attrOf(String k, NElement v, boolean objectHead) {
        if ("description".equals(k)) {
            String s = Tson.asString(v);
            return new Attr("description: " + scalar(v, Bare.NEVER), true, s == null ? 0 : s.length());
        }
        if ("example".equals(k)) {
            // the example stays on a single line, in the parentheses of its head
            return new Attr("example: " + inlineValue(v), false, 0);
        }
        if ("format".equals(k)) {
            String s = Tson.asString(v);
            if (objectHead && s != null && s.startsWith("object::")) {
                // the format of an object schema is a bare name
                return new Attr("format: " + scalar(NElement.ofString(s.substring("object::".length())), Bare.FORMAT), false, 0);
            }
            return new Attr("format: " + scalar(v, Bare.FORMAT), false, 0);
        }
        if ("bearerFormat".equals(k)) {
            return new Attr("bearerFormat: " + scalar(v, Bare.SCHEME), false, 0);
        }
        return new Attr(k + ": " + attrValue(v), false, 0);
    }

    /**
     * The value of an attribute: an array is written on a single line, its items being separated
     * by spaces (the dialect has no comma separator).
     */
    private String attrValue(NElement v) {
        NArrayElement a = v.asArray().orNull();
        if (a != null) {
            StringBuilder s = new StringBuilder("[");
            boolean first = true;
            for (NElement c : Tson.members(a)) {
                if (!first) {
                    s.append(" ");
                }
                first = false;
                s.append(scalar(c, Bare.NEVER));
            }
            s.append("]");
            return s.toString();
        }
        return plainValue(v, indent);
    }

    private static final class Attr {
        final String text;
        final boolean description;
        final int descriptionLength;

        Attr(String text, boolean description, int descriptionLength) {
            this.text = text;
            this.description = description;
            this.descriptionLength = descriptionLength;
        }
    }

    private String refName(NObjectElement o) {
        String r = Tson.asString(o.get("$ref").orNull());
        int i = r.lastIndexOf('/');
        return key(i >= 0 ? r.substring(i + 1) : r);
    }

    /**
     * The expression of a reference: the name of the schema, with the attributes that document it
     * (a reference may be documented, as in {@code Tokens(description: "the tokens")}). The
     * attributes stay in the head: a reference has no body.
     */
    private String refExpr(NObjectElement o) {
        String name = refName(o);
        List<String> attrs = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("$ref".equals(k)) {
                continue;
            }
            attrs.add(attrOf(k, Tson.valueOf(m), false).text);
        }
        if (attrs.isEmpty()) {
            return name;
        }
        return name + "(" + String.join(" ", attrs) + ")";
    }

    private List<String> stringList(NElement arr) {
        List<String> r = new ArrayList<>();
        for (NElement e : Tson.members(arr)) {
            r.add(Tson.asString(e));
        }
        return r;
    }

    // ------------------------------------------------------------------ config

    private void config(NElement root) {
        for (NElement m : Tson.members(root)) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if ("variables".equals(name)) {
                line("variables {");
                indent++;
                for (NElement vm : Tson.members(v)) {
                    String vn = Tson.nameOf(vm);
                    NObjectElement vo = Tson.valueOf(vm).asObject().orNull();
                    if (vo != null && isSimpleVariable(vo)) {
                        line(key(vn) + ": " + scalar(vo.get("value").orNull(), Bare.NEVER));
                    } else {
                        line(key(vn) + ": " + plainValue(Tson.valueOf(vm), indent));
                    }
                }
                indent--;
                line("}");
            } else {
                line(key(name) + ": " + plainValue(v, indent));
            }
        }
    }

    private boolean isSimpleVariable(NObjectElement vo) {
        if (vo.size() != 2) {
            return false;
        }
        NElement obs = vo.get("observations").orNull();
        if (obs == null) {
            return false;
        }
        String s = Tson.asString(obs);
        return s == null || s.isEmpty();
    }

    // ------------------------------------------------------------------ plain / verbatim

    private void writeNamedPlain(String name, NElement v) {
        // A quoted name cannot be used as a TSON head (the Nuts parser splits
        // `"name" { ... }` into a bare string plus an unnamed object), so fall back to a pair.
        if (isBareToken(name)) {
            line(name + " " + plainValue(v, indent));
        } else {
            writePlainMember(name, v);
        }
    }

    private void writePlainMember(String name, NElement v) {
        line(key(name) + ": " + plainValue(v, indent));
    }

    private void lineBlockObject(NObjectElement o) {
        line("{");
        indent++;
        for (NElement m : Tson.members(o)) {
            line(key(Tson.nameOf(m)) + ": " + plainValue(Tson.valueOf(m), indent));
        }
        indent--;
        line("}");
    }

    /**
     * A value written on a single line: the members of an object and the items of an array are
     * separated by a space. It is used for the examples, that stay in the parentheses of their head.
     */
    private String inlineValue(NElement e) {
        if (e == null) {
            return "null";
        }
        NObjectElement o = e.asObject().orNull();
        if (o != null) {
            if (o.size() == 0) {
                return "{}";
            }
            StringBuilder s = new StringBuilder("{ ");
            boolean first = true;
            for (NElement m : Tson.members(o)) {
                if (!first) {
                    s.append(" ");
                }
                first = false;
                s.append(key(Tson.nameOf(m))).append(": ").append(inlineValue(Tson.valueOf(m)));
            }
            return s.append(" }").toString();
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null) {
            if (a.size() == 0) {
                return "[]";
            }
            StringBuilder s = new StringBuilder("[ ");
            boolean first = true;
            for (NElement c : Tson.members(a)) {
                if (!first) {
                    s.append(" ");
                }
                first = false;
                s.append(inlineValue(c));
            }
            return s.append(" ]").toString();
        }
        return scalar(e, Bare.NEVER);
    }

    /**
     * A value in a data position: every string is quoted, so that a bare {@code true} or
     * {@code 123} keeps its meaning.
     */
    private String plainValue(NElement e, int ind) {
        if (e == null) {
            return "null";
        }
        NObjectElement o = e.asObject().orNull();
        if (o != null) {
            if (o.size() == 0) {
                return "{}";
            }
            StringBuilder s = new StringBuilder("{\n");
            for (NElement m : Tson.members(o)) {
                s.append(spaces(ind + 1)).append(key(Tson.nameOf(m))).append(": ")
                        .append(plainValue(Tson.valueOf(m), ind + 1)).append("\n");
            }
            s.append(spaces(ind)).append("}");
            return s.toString();
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null) {
            if (a.size() == 0) {
                return "[]";
            }
            StringBuilder s = new StringBuilder("[\n");
            for (NElement c : Tson.members(a)) {
                s.append(spaces(ind + 1)).append(plainValue(c, ind + 1)).append("\n");
            }
            s.append(spaces(ind)).append("]");
            return s.toString();
        }
        return scalar(e, Bare.NEVER);
    }

    private boolean isBareToken(String s) {
        return s != null && s.matches(BARE_TOKEN)
                && !"true".equals(s) && !"false".equals(s) && !"null".equals(s);
    }

    private String key(String k) {
        if (k == null) {
            return "\"\"";
        }
        if (isBareToken(k)) {
            return k;
        }
        return scalar(NElement.ofString(k), Bare.NEVER);
    }

    /**
     * A scalar value. Strings are quoted, except in the positions that allow a bare token.
     */
    private String scalar(NElement e, Bare bare) {
        if (e == null) {
            return "null";
        }
        if (e.asObject().isPresent() || e.asArray().isPresent()) {
            return plainValue(e, indent);
        }
        if (Tson.isTemporal(e)) {
            if (e.type() == NElementType.LOCAL_TIME) {
                // the parser drops the seconds of a time: keep the value as a string
                return quote(e);
            }
            // a date, a date-time or an instant is written as a literal
            return Tson.asString(e);
        }
        if (e.isString() || e.isName()) {
            String s = e.asStringValue().orElse("");
            if (bare != Bare.NEVER && isBareToken(s)) {
                return s;
            }
            return quote(e);
        }
        // a number, a boolean or null
        String t = formatPlain(e);
        return t == null ? "null" : t;
    }

    /**
     * A double quoted string. The TSON strings have no backslash escape: a double quote is written
     * twice ({@code ""}), and a line break is kept as it is.
     */
    private String quote(NElement e) {
        String s = Tson.asString(e);
        if (s == null) {
            return "\"\"";
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static String formatPlain(NElement e) {
        String s = NElementWriter.ofPlainTson().compact(true).formatPlain(e);
        if (s == null) {
            return null;
        }
        int len = s.length();
        while (len > 0 && (s.charAt(len - 1) == '\n' || s.charAt(len - 1) == '\r')) {
            len--;
        }
        return s.substring(0, len);
    }

    // ------------------------------------------------------------------ low level

    private void line(String s) {
        sb.append(spaces(indent)).append(s).append("\n");
    }

    private void blank() {
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n'
                && sb.length() > 1 && sb.charAt(sb.length() - 2) != '\n') {
            sb.append("\n");
        }
    }

    private String indentStr() {
        return spaces(indent);
    }

    private static String spaces(int n) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < n; i++) {
            s.append("  ");
        }
        return s.toString();
    }
}
