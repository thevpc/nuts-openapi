package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.*;

import java.util.*;

/**
 * Condensed TSON writer (prompt 3.8). Emits the canonical condensed text for an OpenAPI or config
 * tree. Scalar quoting/escaping is delegated to the Nuts plain-TSON writer (which reliably
 * round-trips), while structure and the condensed heads are emitted here.
 *
 * <p>Self-verification is performed by {@link NoApiConversion}.</p>
 */
public class CondensedTsonWriter {

    private final StringBuilder sb = new StringBuilder();
    private int indent = 0;

    private static final Set<String> OP_ANNOTATIONS = new LinkedHashSet<>(Arrays.asList(
            "summary", "description", "operationId", "tags", "deprecated", "enabled", "security"));

    private static final Map<String, String> MEDIA_REVERSE = new HashMap<>();

    static {
        for (Map.Entry<String, String> e : Tson.MEDIA_SHORTHANDS.entrySet()) {
            MEDIA_REVERSE.put(e.getValue(), e.getKey());
        }
    }

    public static String writeDocument(NElement root) {
        CondensedTsonWriter w = new CondensedTsonWriter();
        w.doc(root, false);
        return w.sb.toString();
    }

    public static String writeConfig(NElement root) {
        CondensedTsonWriter w = new CondensedTsonWriter();
        w.config(root);
        return w.sb.toString();
    }

    // ------------------------------------------------------------------ document

    private void doc(NElement root, boolean config) {
        List<NElement> members = Tson.members(root);
        boolean first = true;
        // @openapi decorates info
        boolean hasOpenapi = root.asObject().isPresent() && root.asObject().get().get("openapi").isPresent();
        boolean hasInfo = root.asObject().isPresent() && root.asObject().get().get("info").isPresent();
        for (NElement m : members) {
            String name = Tson.nameOf(m);
            NElement v = Tson.valueOf(m);
            if (!first) {
                blank();
            }
            first = false;
            switch (name) {
                case "openapi": {
                    if (hasInfo) {
                        line("@openapi(" + scalar(v) + ")");
                    } else {
                        line("openapi: " + scalar(v));
                    }
                    break;
                }
                case "info": {
                    writeInfo(v);
                    break;
                }
                case "custom": {
                    writeNamedPlain("custom", v);
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

    private void writeInfo(NElement v) {
        NObjectElement o = v.asObject().orNull();
        line("info {");
        indent++;
        if (o != null) {
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
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
                line(plainValue(it, indent) + ",");
                continue;
            }
            lineBlockObject(io, true);
        }
        indent--;
        line("]");
    }

    // ------------------------------------------------------------------ servers / security

    private void writeServers(NElement v) {
        List<NElement> items = Tson.members(v);
        line("servers [");
        indent++;
        for (NElement it : items) {
            NObjectElement o = it.asObject().orNull();
            NElement url = o == null ? null : o.get("url").orNull();
            if (url == null) {
                line(plainValue(it, indent) + ",");
                continue;
            }
            line("url(" + scalar(url) + ") {");
            indent++;
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                if (!"url".equals(k)) {
                    line(key(k) + ": " + plainValue(Tson.valueOf(m), indent));
                }
            }
            indent--;
            line("},");
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
            NObjectElement o = it.asObject().orNull();
            if (o == null || o.size() != 1) {
                line(plainValue(it, indent) + ",");
                continue;
            }
            NElement nameEl = Tson.members(o).get(0);
            String name = Tson.nameOf(nameEl);
            NElement scopes = Tson.valueOf(nameEl);
            List<NElement> sc = Tson.members(scopes);
            if (!isBareKey(name) && !sc.isEmpty()) {
                // quoted names cannot be used as a tuple head
                line(plainValue(it, indent) + ",");
                continue;
            }
            if (sc.isEmpty()) {
                line(key(name) + ",");
            } else {
                StringBuilder s = new StringBuilder(name).append("(");
                for (int i = 0; i < sc.size(); i++) {
                    if (i > 0) {
                        s.append(", ");
                    }
                    s.append(key(Tson.asString(sc.get(i))));
                }
                s.append("),");
                line(s.toString());
            }
        }
        indent--;
        line("]");
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
            boolean condensable = isCondensablePathItem(item);
            if (!condensable) {
                line(key(path) + ": " + plainValue(item, indent) + ",");
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

    private void writeOperation(String method, String path, NObjectElement op) {
        if (op == null) {
            line(method + "(" + scalar(NElement.ofString(path)) + ") {}");
            return;
        }
        // annotations first
        for (NElement m : Tson.members(op)) {
            String k = Tson.nameOf(m);
            if (OP_ANNOTATIONS.contains(k)) {
                writeOperationAnnotation(k, Tson.valueOf(m));
            }
        }
        line(method + "(" + scalar(NElement.ofString(path)) + ") {");
        indent++;
        for (NElement m : Tson.members(op)) {
            String k = Tson.nameOf(m);
            NElement val = Tson.valueOf(m);
            if (OP_ANNOTATIONS.contains(k)) {
                continue;
            }
            if ("in".equals(k)) {
                writeRequestBody(val);
            } else if ("responses".equals(k)) {
                writeResponses(val);
            } else if ("parameters".equals(k)) {
                writeParameters(val);
            } else {
                line(key(k) + ": " + plainValue(val, indent));
            }
        }
        indent--;
        line("}");
    }

    private void writeOperationAnnotation(String k, NElement v) {
        switch (k) {
            case "deprecated": {
                if (Tson.asBoolean(v, true)) {
                    line("@deprecated");
                } else {
                    line("@deprecated(false)");
                }
                break;
            }
            case "tags": {
                StringBuilder s = new StringBuilder("@tags(");
                List<NElement> items = Tson.members(v);
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) {
                        s.append(", ");
                    }
                    s.append(scalar(items.get(i)));
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
                        s.append(", ");
                    }
                    s.append(securityItemExpr(items.get(i)));
                }
                s.append(")");
                line(s.toString());
                break;
            }
            default: {
                line("@" + k + "(" + scalar(v) + ")");
                break;
            }
        }
    }

    private String securityItemExpr(NElement it) {
        NObjectElement o = it.asObject().orNull();
        if (o == null || o.size() != 1) {
            return plainValue(it, indent);
        }
        NElement nameEl = Tson.members(o).get(0);
        String name = Tson.nameOf(nameEl);
        List<NElement> sc = Tson.members(Tson.valueOf(nameEl));
        if (!isBareKey(name) && !sc.isEmpty()) {
            return plainValue(it, indent);
        }
        if (sc.isEmpty()) {
            return key(name);
        }
        StringBuilder s = new StringBuilder(name).append("(");
        for (int i = 0; i < sc.size(); i++) {
            if (i > 0) {
                s.append(", ");
            }
            s.append(key(Tson.asString(sc.get(i))));
        }
        return s.append(")").toString();
    }

    private void writeParameters(NElement v) {
        List<NElement> items = Tson.members(v);
        if (items.isEmpty()) {
            return;
        }
        line("parameters [");
        indent++;
        for (NElement it : items) {
            line(plainValue(it, indent) + ",");
        }
        indent--;
        line("]");
    }

    private void writeRequestBody(NElement rb) {
        NObjectElement o = rb.asObject().orNull();
        if (o == null) {
            line("in: " + plainValue(rb, indent));
            return;
        }
        NObjectElement content = o.getObject("content").orNull();
        if (content == null || content.size() != 1) {
            writeNamedPlain("requestBody", rb);
            return;
        }
        NElement entry = Tson.members(content).get(0);
        String media = Tson.nameOf(entry);
        NElement mediaObj = Tson.valueOf(entry);
        NElement schema = mediaObj.asObject().orNull() == null ? null : mediaObj.asObject().get().get("schema").orNull();
        String head = ioTypeHead(schema);
        if (head == null) {
            writeNamedPlain("requestBody", rb);
            return;
        }
        boolean required = Tson.asBoolean(o.get("required").orNull(), false);
        StringBuilder open = new StringBuilder();
        if (required) {
            open.append("@required ");
        }
        open.append("in: ").append(head).append("(").append(mediaShorthand(media)).append(") {");
        line(open.toString());
        indent++;
        NElement desc = o.get("description").orNull();
        if (desc != null) {
            line("description: " + scalar(desc));
        }
        NElement ex = mediaObj.asObject().get().get("example").orNull();
        if (ex != null) {
            line("example: " + plainValue(ex, indent));
        }
        indent--;
        line("}");
    }

    private void writeResponses(NElement responses) {
        for (NElement m : Tson.members(responses)) {
            String status = Tson.nameOf(m);
            NElement resp = Tson.valueOf(m);
            writeResponse(status, resp);
        }
    }

    private void writeResponse(String status, NElement resp) {
        NObjectElement o = resp.asObject().orNull();
        if (o == null) {
            plainResponsesFallback();
            return;
        }
        NObjectElement content = o.getObject("content").orNull();
        String typePart;
        NElement example = null;
        if (content == null || content.size() == 0) {
            typePart = "void";
        } else if (content.size() == 1) {
            NElement entry = Tson.members(content).get(0);
            String media = Tson.nameOf(entry);
            NObjectElement mediaObj = Tson.valueOf(entry).asObject().orNull();
            NElement schema = mediaObj == null ? null : mediaObj.get("schema").orNull();
            String head = ioTypeHead(schema);
            if (head == null) {
                plainResponsesFallback();
                return;
            }
            example = mediaObj.get("example").orNull();
            typePart = head + "(" + mediaShorthand(media) + ")";
        } else {
            plainResponsesFallback();
            return;
        }
        line("out(" + statusToken(status) + "): " + typePart + " {");
        indent++;
        NElement desc = o.get("description").orNull();
        if (desc != null) {
            line("description: " + scalar(desc));
        }
        if (example != null) {
            line("example: " + plainValue(example, indent));
        }
        indent--;
        line("}");
    }

    // set when a response could not be condensed; caller then falls back to plain responses
    private boolean responsesFallback = false;

    private void plainResponsesFallback() {
        responsesFallback = true;
    }

    private String statusToken(String status) {
        if (status.matches("[0-9]+")) {
            return status;
        }
        if ("default".equals(status)) {
            return "default";
        }
        return scalar(NElement.ofString(status));
    }

    private String mediaShorthand(String media) {
        String s = MEDIA_REVERSE.get(media);
        if (s != null) {
            return s;
        }
        return scalar(NElement.ofString(media));
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
                if ("schemas".equals(k)) {
                    writeSchemas(val);
                } else if ("securitySchemes".equals(k)) {
                    writeSecuritySchemes(val);
                } else if ("headers".equals(k)) {
                    writeHeaders(val);
                } else {
                    writeNamedPlain(k, val);
                }
            }
        }
        indent--;
        line("}");
    }

    private void writeSchemas(NElement v) {
        line("schemas {");
        indent++;
        for (NElement m : Tson.members(v)) {
            String name = Tson.nameOf(m);
            NElement schema = Tson.valueOf(m);
            writeSchemaDeclaration(name, schema);
        }
        indent--;
        line("}");
    }

    private void writeSchemaDeclaration(String name, NElement schema) {
        NObjectElement o = schema.asObject().orNull();
        if (o == null) {
            line(key(name) + ": " + plainValue(schema, indent));
            return;
        }
        if (isRawSchema(o)) {
            line("@raw " + key(name) + ": " + plainValue(schema, indent));
            return;
        }
        NElement desc = o.get("description").orNull();
        if (desc != null) {
            line("@description(" + scalar(desc) + ")");
        }
        NElement type = o.get("type").orNull();
        String t = type == null ? null : Tson.asString(type);
        if ("object".equals(t)) {
            line(key(name) + ": " + objectExpr(o, true));
        } else if ("array".equals(t)) {
            line(key(name) + ": " + arrayExpr(o));
        } else if (t != null && Tson.isPrimitive(t)) {
            line(key(name) + ": " + primitiveExpr(o));
        } else if (o.get("$ref").isPresent()) {
            line(key(name) + ": " + refName(o));
        } else {
            line("@raw " + key(name) + ": " + plainValue(schema, indent));
        }
    }

    private boolean isRawSchema(NObjectElement o) {
        for (String k : new String[]{"allOf", "oneOf", "anyOf", "not", "additionalProperties"}) {
            if (o.get(k).isPresent()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ securitySchemes / headers

    private void writeSecuritySchemes(NElement v) {
        line("securitySchemes {");
        indent++;
        for (NElement m : Tson.members(v)) {
            String name = Tson.nameOf(m);
            NElement scheme = Tson.valueOf(m);
            NObjectElement o = scheme.asObject().orNull();
            String type = o == null ? null : Tson.asString(o.get("type").orNull());
            if ("http".equals(type)) {
                line(key(name) + ": http(" + bareName(Tson.asString(o.get("scheme").orNull())) + ") {");
                indent++;
                for (NElement x : Tson.members(o)) {
                    String k = Tson.nameOf(x);
                    if (!"type".equals(k) && !"scheme".equals(k)) {
                        line(key(k) + ": " + plainValue(Tson.valueOf(x), indent));
                    }
                }
                indent--;
                line("}");
            } else if ("apiKey".equals(type)) {
                String in = Tson.asString(o.get("in").orNull());
                String nm = Tson.asString(o.get("name").orNull());
                line(key(name) + ": apiKey(" + key(in) + ": " + scalar(NElement.ofString(nm)) + ") {");
                indent++;
                for (NElement x : Tson.members(o)) {
                    String k = Tson.nameOf(x);
                    if (!"type".equals(k) && !"in".equals(k) && !"name".equals(k)) {
                        line(key(k) + ": " + plainValue(Tson.valueOf(x), indent));
                    }
                }
                indent--;
                line("}");
            } else if ("openIdConnect".equals(type)) {
                line(key(name) + ": openIdConnect(url: " + scalar(o.get("openIdConnectUrl").orNull()) + ") {");
                indent++;
                for (NElement x : Tson.members(o)) {
                    String k = Tson.nameOf(x);
                    if (!"type".equals(k) && !"openIdConnectUrl".equals(k)) {
                        line(key(k) + ": " + plainValue(Tson.valueOf(x), indent));
                    }
                }
                indent--;
                line("}");
            } else if ("oauth2".equals(type)) {
                line(key(name) + ": oauth2 {");
                indent++;
                for (NElement x : Tson.members(o)) {
                    String k = Tson.nameOf(x);
                    if (!"type".equals(k)) {
                        line(key(k) + ": " + plainValue(Tson.valueOf(x), indent));
                    }
                }
                indent--;
                line("}");
            } else {
                line(key(name) + ": " + plainValue(scheme, indent));
            }
        }
        indent--;
        line("}");
    }

    private void writeHeaders(NElement v) {
        line("headers {");
        indent++;
        for (NElement m : Tson.members(v)) {
            String name = Tson.nameOf(m);
            NElement header = Tson.valueOf(m);
            NObjectElement o = header.asObject().orNull();
            NElement schema = o == null ? null : o.get("schema").orNull();
            if (schema == null) {
                line(key(name) + ": " + plainValue(header, indent));
                continue;
            }
            NElement required = o.get("required").orNull();
            StringBuilder ann = new StringBuilder();
            if (required != null) {
                if (Tson.asBoolean(required, true)) {
                    ann.append("@required ");
                } else {
                    ann.append("@required(false) ");
                }
            }
            line(ann + key(name) + ": " + headerSchemaExpr(schema, o.get("description").orNull()));
        }
        indent--;
        line("}");
    }

    private String headerSchemaExpr(NElement schema, NElement headerDescription) {
        NObjectElement o = schema.asObject().orNull();
        String t = o == null ? null : Tson.asString(o.get("type").orNull());
        if (t == null || !Tson.isPrimitive(t)) {
            return plainValue(schema, indent);
        }
        StringBuilder s = new StringBuilder(t);
        List<String> attrs = new ArrayList<>();
        if (headerDescription != null) {
            attrs.add("description: " + scalar(headerDescription));
        }
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if (!"type".equals(k)) {
                attrs.add(k + ": " + plainValue(Tson.valueOf(m), indent));
            }
        }
        if (!attrs.isEmpty()) {
            s.append("(").append(String.join(", ", attrs)).append(")");
        }
        return s.toString();
    }

    // ------------------------------------------------------------------ schema type expressions

    private String objectExpr(NObjectElement o, boolean declContext) {
        StringBuilder s = new StringBuilder("object");
        List<String> attrs = schemaAttrs(o, declContext, true);
        List<String> required = stringList(o.get("required").orNull());
        List<String> propNames = new ArrayList<>();
        NObjectElement props = o.getObject("properties").orNull();
        if (props != null) {
            for (NElement m : Tson.members(props)) {
                propNames.add(Tson.nameOf(m));
            }
        }
        List<String> extras = new ArrayList<>();
        for (String r : required) {
            if (!propNames.contains(r)) {
                extras.add(r);
            }
        }
        if (!extras.isEmpty()) {
            StringBuilder ra = new StringBuilder("required: [");
            for (int i = 0; i < extras.size(); i++) {
                if (i > 0) {
                    ra.append(", ");
                }
                ra.append(scalar(NElement.ofString(extras.get(i))));
            }
            ra.append("]");
            attrs.add(ra.toString());
        }
        if (!attrs.isEmpty()) {
            s.append("(").append(String.join(", ", attrs)).append(")");
        }
        // body
        NElement example = o.get("example").orNull();
        if (props == null && example == null) {
            return s.append(" { }").toString();
        }
        s.append(" {\n");
        indent++;
        if (props != null) {
            for (NElement m : Tson.members(props)) {
                String pname = Tson.nameOf(m);
                StringBuilder p = new StringBuilder();
                if (required.contains(pname)) {
                    p.append("@required ");
                }
                p.append(key(pname)).append(": ").append(schemaExpr(Tson.valueOf(m), false));
                s.append(indentStr()).append(p).append("\n");
            }
        }
        if (example != null) {
            s.append(indentStr()).append("example: ").append(plainValue(example, indent)).append("\n");
        }
        indent--;
        s.append(indentStr()).append("}");
        return s.toString();
    }

    private String arrayExpr(NObjectElement o) {
        StringBuilder s = new StringBuilder("array");
        List<String> attrs = schemaAttrs(o, false, false);
        NElement items = o.get("items").orNull();
        if (!attrs.isEmpty()) {
            s.append("(").append(String.join(", ", attrs)).append(")");
        }
        s.append("[").append(items == null ? "object" : schemaExpr(items, false)).append("]");
        return s.toString();
    }

    /**
     * Property/declaration type expression.
     */
    private String schemaExpr(NElement schema, boolean declContext) {
        NObjectElement o = schema.asObject().orNull();
        if (o == null) {
            return plainValue(schema, indent);
        }
        if (o.get("$ref").isPresent()) {
            return refName(o);
        }
        String t = Tson.asString(o.get("type").orNull());
        if ("object".equals(t)) {
            return objectExpr(o, declContext);
        }
        if ("array".equals(t)) {
            return arrayExpr(o);
        }
        if (t != null && Tson.isPrimitive(t)) {
            return primitiveExpr(o);
        }
        // untyped: inline the schema verbatim
        return plainValue(schema, indent);
    }

    private String primitiveExpr(NObjectElement o) {
        String t = Tson.asString(o.get("type").orNull());
        StringBuilder s = new StringBuilder(t);
        List<String> attrs = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if (!"type".equals(k)) {
                if ("format".equals(k)) {
                    attrs.add("format: " + bareName(Tson.asString(Tson.valueOf(m))));
                } else {
                    attrs.add(k + ": " + plainValue(Tson.valueOf(m), indent));
                }
            }
        }
        if (!attrs.isEmpty()) {
            s.append("(").append(String.join(", ", attrs)).append(")");
        }
        return s.toString();
    }

    /**
     * Attrs of an object/array schema that go in the head parentheses (everything except the
     * structural keys, and except {@code description} in declaration context).
     */
    private List<String> schemaAttrs(NObjectElement o, boolean declContext, boolean isObject) {
        List<String> attrs = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("type".equals(k) || "properties".equals(k) || "required".equals(k)
                    || "example".equals(k) || "items".equals(k)) {
                continue;
            }
            if (declContext && "description".equals(k)) {
                continue;
            }
            NElement v = Tson.valueOf(m);
            if ("format".equals(k) && isObject) {
                String f = Tson.asString(v);
                if (f != null && f.startsWith("object::")) {
                    attrs.add("format: " + bareName(f.substring("object::".length())));
                    continue;
                }
            }
            attrs.add(k + ": " + plainValue(v, indent));
        }
        return attrs;
    }

    private String refName(NObjectElement o) {
        String r = Tson.asString(o.get("$ref").orNull());
        int i = r.lastIndexOf('/');
        return key(i >= 0 ? r.substring(i + 1) : r);
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
                        line(key(vn) + ": " + scalar(vo.get("value").orNull()));
                    } else if (isBareKey(vn)) {
                        line(vn + " " + plainValue(Tson.valueOf(vm), indent));
                    } else {
                        line(key(vn) + ": " + plainValue(Tson.valueOf(vm), indent));
                    }
                }
                indent--;
                line("}");
            } else {
                line(key(name) + ": " + scalar(v));
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
        if (isBareKey(name)) {
            line(name + " " + plainValue(v, indent));
        } else {
            line(key(name) + ": " + plainValue(v, indent));
        }
    }

    private void writePlainMember(String name, NElement v) {
        line(key(name) + ": " + plainValue(v, indent));
    }

    private void lineBlockObject(NObjectElement o, boolean trailingComma) {
        line("{");
        indent++;
        for (NElement m : Tson.members(o)) {
            line(key(Tson.nameOf(m)) + ": " + plainValue(Tson.valueOf(m), indent));
        }
        indent--;
        line(trailingComma ? "}," : "}");
    }

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
                s.append(spaces(ind + 1)).append(plainValue(c, ind + 1)).append(",\n");
            }
            s.append(spaces(ind)).append("]");
            return s.toString();
        }
        return scalar(e);
    }

    private boolean isBareKey(String k) {
        return k != null && k.matches("[A-Za-z_][A-Za-z0-9_]*")
                && !"true".equals(k) && !"false".equals(k) && !"null".equals(k);
    }

    private String key(String k) {
        if (k == null) {
            return "\"\"";
        }
        if (isBareKey(k)) {
            return k;
        }
        return scalar(NElement.ofString(k));
    }

    private String bareName(String s) {
        if (s == null) {
            return "null";
        }
        if (s.matches("[A-Za-z_][A-Za-z0-9_.:-]*") && !"true".equals(s) && !"false".equals(s) && !"null".equals(s)) {
            return s;
        }
        return scalar(NElement.ofString(s));
    }

    private String scalar(NElement e) {
        if (e == null) {
            return "null";
        }
        if (e.asObject().isPresent() || e.asArray().isPresent()) {
            return plainValue(e, indent);
        }
        String s = NElementWriter.ofPlainTson().compact(true).formatPlain(e);
        if (s == null) {
            return "null";
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
