package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.*;

import java.util.*;

/**
 * Canonicalization of an OpenAPI tree (prompt 7.3). The canonical form is the only allowed
 * difference between a document and its round trip, so both the original and the round-tripped
 * tree are canonicalized before being compared.
 *
 * <ul>
 *   <li><b>C1</b> empty {@code tags}, {@code parameters}, {@code required} arrays and empty
 *   {@code components.responses} / {@code components.parameters} objects are dropped.</li>
 *   <li><b>C2</b> {@code required} is re-ordered to property order; names not in
 *   {@code properties} are kept.</li>
 *   <li><b>C3</b> path-level {@code summary}, {@code description}, {@code parameters},
 *   {@code enabled} are pushed into each operation.</li>
 *   <li><b>C4</b> parameter-level schema keywords move into {@code schema}.</li>
 *   <li><b>C5</b> {@code format: "object::X"} is kept on object schemas (bare form already
 *   expanded by the reader).</li>
 *   <li><b>C6</b> applied at comparison time (see {@link NoApiTreeComparator}).</li>
 *   <li><b>C7</b> response keys are strings (the model already uses string keys).</li>
 *   <li><b>C8</b> the document id is stored in {@code custom.openapi-document-id}: an
 *   {@code info.id} member is moved there (and removed from the info).</li>
 *   <li><b>C9</b> the members of an {@code apiKey} security scheme are ordered
 *   {@code type}, {@code name}, {@code in} (the other members keep their order).</li>
 * </ul>
 */
public final class NoApiCanonicalizer {

    private static final Set<String> PATH_FIELDS = new LinkedHashSet<>(Arrays.asList(
            "summary", "description", "parameters", "enabled"));

    private static final Set<String> SCHEMA_KEYWORDS = new LinkedHashSet<>(Arrays.asList(
            "type", "format", "items", "enum", "default", "minimum", "maximum", "minLength",
            "maxLength", "minItems", "maxItems", "pattern", "uniqueItems", "multipleOf",
            "exclusiveMinimum", "exclusiveMaximum", "nullable", "readOnly", "writeOnly",
            "allOf", "oneOf", "anyOf", "not", "additionalProperties", "title", "example"));

    private NoApiCanonicalizer() {
    }

    public static NElement canonicalize(NElement root) {
        return moveDocumentId(canon(root, null));
    }

    /**
     * The document id is stored in {@code custom.openapi-document-id}: an {@code info.id} member is
     * moved there (and removed from the info). When there is no custom section, it is created right
     * after the info. The custom section is always placed right after the info, so that the order of
     * the document members does not depend on where the custom section was written.
     */
    private static NElement moveDocumentId(NElement root) {
        NObjectElement o = root.asObject().orNull();
        if (o == null) {
            return root;
        }
        NElement infoValue = o.get("info").orNull();
        NObjectElement info = infoValue == null ? null : infoValue.asObject().orNull();
        NElement customValue = o.get("custom").orNull();
        NObjectElement custom = customValue == null ? null : customValue.asObject().orNull();
        boolean hasInfoId = info != null && info.get("id").isPresent();
        if (!hasInfoId && customValue == null) {
            // nothing to normalize
            return root;
        }
        // the id is removed from the info
        NElement newInfo = infoValue;
        NElement id = null;
        if (hasInfoId) {
            id = info.get("id").get();
            NObjectElementBuilder ib = Tson.obj();
            for (NElement m : Tson.members(info)) {
                if (!"id".equals(Tson.nameOf(m))) {
                    ib.add(Tson.nameOf(m), Tson.valueOf(m));
                }
            }
            newInfo = ib.build();
        }
        // the id is stored first in the custom section
        NObjectElementBuilder cb = Tson.obj();
        if (hasInfoId && (custom == null || !custom.get("openapi-document-id").isPresent())) {
            cb.add("openapi-document-id", id);
        }
        if (custom != null) {
            for (NElement m : Tson.members(custom)) {
                cb.add(Tson.nameOf(m), Tson.valueOf(m));
            }
        }
        NObjectElementBuilder rb = Tson.obj();
        boolean customAdded = false;
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("info".equals(k)) {
                rb.add("info", newInfo);
                if (!customAdded) {
                    rb.add("custom", cb.build());
                    customAdded = true;
                }
            } else if ("custom".equals(k)) {
                if (!customAdded) {
                    rb.add("custom", cb.build());
                    customAdded = true;
                }
            } else {
                rb.add(k, Tson.valueOf(m));
            }
        }
        return rb.build();
    }

    private static NElement canon(NElement e, String parentKey) {
        return canon(e, parentKey, false);
    }

    private static NElement canon(NElement e, String parentKey, boolean asParameter) {
        NObjectElement o = e.asObject().orNull();
        if (o != null) {
            NObjectElementBuilder b = Tson.obj();
            for (NElement m : Tson.members(o)) {
                String k = Tson.nameOf(m);
                NElement v = Tson.valueOf(m);
                if (isDroppable(parentKey, k, v)) {
                    continue;
                }
                b.add(k, canon(v, k, false));
            }
            NObjectElement r = b.build();
            if (isPathItem(r)) {
                r = pushPathFields(r);
            }
            r = reorderRequired(r);
            if (asParameter) {
                r = moveParamSchema(r);
            }
            if ("securitySchemes".equals(parentKey)) {
                // the members of the map are the security schemes
                NObjectElementBuilder sb = Tson.obj();
                for (NElement m : Tson.members(r)) {
                    NElement v = Tson.valueOf(m);
                    sb.add(Tson.nameOf(m), v.asObject().isPresent() ? reorderApiKeyScheme(v.asObject().get()) : v);
                }
                r = sb.build();
            }
            return r;
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null) {
            boolean itemsAreParams = "parameters".equals(parentKey);
            NArrayElementBuilder b = Tson.arr();
            for (NElement c : Tson.members(a)) {
                b.add(canon(c, null, itemsAreParams));
            }
            return b.build();
        }
        return e;
    }

    /**
     * The canonical order of the members of an {@code apiKey} security scheme: {@code type},
     * {@code name}, {@code in} (the condensed form {@code apiKey(header: X-API-KEY)} always reads
     * them in that order).
     */
    private static NObjectElement reorderApiKeyScheme(NObjectElement o) {
        if (!"apiKey".equals(Tson.asString(o.get("type").orNull()))) {
            return o;
        }
        List<String> order = Arrays.asList("type", "name", "in");
        NObjectElementBuilder b = Tson.obj();
        for (String k : order) {
            if (o.get(k).isPresent()) {
                b.add(k, o.get(k).get());
            }
        }
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if (!order.contains(k)) {
                b.add(k, Tson.valueOf(m));
            }
        }
        return b.build();
    }

    private static boolean isDroppable(String parentKey, String k, NElement v) {
        if (v == null) {
            return false;
        }
        if (("tags".equals(k) || "parameters".equals(k) || "required".equals(k))
                && v.asArray().isPresent() && v.asArray().get().size() == 0) {
            return true;
        }
        if ("components".equals(parentKey) && ("responses".equals(k) || "parameters".equals(k))
                && v.asObject().isPresent() && v.asObject().get().size() == 0) {
            return true;
        }
        return false;
    }

    private static boolean isPathItem(NObjectElement o) {
        boolean hasMethod = false;
        boolean hasOnlyMethodsOrPathFields = true;
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if (Tson.isMethod(k)) {
                hasMethod = true;
            } else if (!PATH_FIELDS.contains(k)) {
                hasOnlyMethodsOrPathFields = false;
            }
        }
        return hasMethod && hasOnlyMethodsOrPathFields;
    }

    private static NObjectElement pushPathFields(NObjectElement pathItem) {
        List<Object[]> pathFields = new ArrayList<>();
        for (NElement m : Tson.members(pathItem)) {
            String k = Tson.nameOf(m);
            if (PATH_FIELDS.contains(k)) {
                pathFields.add(new Object[]{k, Tson.valueOf(m)});
            }
        }
        NObjectElementBuilder b = Tson.obj();
        for (NElement m : Tson.members(pathItem)) {
            String k = Tson.nameOf(m);
            if (Tson.isMethod(k)) {
                NElement opv = Tson.valueOf(m);
                NObjectElement op = opv.asObject().orNull();
                if (op == null) {
                    b.add(k, opv);
                    continue;
                }
                NObjectElementBuilder ob = Tson.obj();
                for (Object[] pf : pathFields) {
                    if (!op.get((String) pf[0]).isPresent()) {
                        ob.add((String) pf[0], (NElement) pf[1]);
                    }
                }
                for (NElement om : Tson.members(op)) {
                    ob.add(Tson.nameOf(om), Tson.valueOf(om));
                }
                b.add(k, ob.build());
            }
        }
        return b.build();
    }

    private static NObjectElement reorderRequired(NObjectElement o) {
        NElement properties = o.get("properties").orNull();
        NElement required = o.get("required").orNull();
        if (properties == null || required == null || !properties.asObject().isPresent() || !required.asArray().isPresent()) {
            return o;
        }
        List<String> propOrder = new ArrayList<>();
        for (NElement m : Tson.members(properties)) {
            propOrder.add(Tson.nameOf(m));
        }
        List<String> extras = new ArrayList<>();
        for (NElement r : Tson.members(required)) {
            String s = Tson.asString(r);
            if (!propOrder.contains(s) && !extras.contains(s)) {
                extras.add(s);
            }
        }
        NArrayElementBuilder ra = Tson.arr();
        for (String s : propOrder) {
            if (contains(required, s)) {
                ra.add(Tson.str(s));
            }
        }
        for (String s : extras) {
            ra.add(Tson.str(s));
        }
        NObjectElementBuilder b = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if ("required".equals(k)) {
                b.add("required", ra.build());
            } else {
                b.add(k, Tson.valueOf(m));
            }
        }
        return b.build();
    }

    private static boolean contains(NElement arr, String s) {
        for (NElement e : Tson.members(arr)) {
            if (s.equals(Tson.asString(e))) {
                return true;
            }
        }
        return false;
    }

    private static NObjectElement moveParamSchema(NObjectElement o) {
        if (!o.get("name").isPresent() || !o.get("in").isPresent() || o.get("schema").isPresent()) {
            return o;
        }
        List<String> moved = new ArrayList<>();
        for (NElement m : Tson.members(o)) {
            if (SCHEMA_KEYWORDS.contains(Tson.nameOf(m))) {
                moved.add(Tson.nameOf(m));
            }
        }
        if (moved.isEmpty()) {
            return o;
        }
        NObjectElementBuilder schema = Tson.obj();
        NObjectElementBuilder b = Tson.obj();
        for (NElement m : Tson.members(o)) {
            String k = Tson.nameOf(m);
            if (moved.contains(k)) {
                schema.add(k, Tson.valueOf(m));
            } else {
                b.add(k, Tson.valueOf(m));
            }
        }
        b.add("schema", schema.build());
        return b.build();
    }
}
