package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Low level helpers shared by the condensed TSON desugarer, canonicalizer and writer. They hide
 * the TSON data-model difference between a pair ({@code k: v}) and a named container
 * ({@code k { … }}, {@code k […]}, {@code k(…) …}) behind one "member" view (prompt 2.3 / P11).
 */
public final class Tson {

    public static final Set<String> PRIMITIVES = new LinkedHashSet<>(Arrays.asList(
            "string", "number", "integer", "boolean", "array", "object"));

    public static final Set<String> RESERVED_SCHEMA_NAMES = new LinkedHashSet<>(Arrays.asList(
            "string", "number", "integer", "boolean", "array", "object", "void"));

    public static final Set<String> METHODS = new LinkedHashSet<>(Arrays.asList(
            "get", "post", "put", "delete", "patch", "head", "options", "trace"));

    public static final Map<String, String> MEDIA_SHORTHANDS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("json", "application/json");
        m.put("xml", "application/xml");
        m.put("text", "text/plain");
        m.put("form", "application/x-www-form-urlencoded");
        m.put("multipart", "multipart/form-data");
        m.put("binary", "application/octet-stream");
        MEDIA_SHORTHANDS = Collections.unmodifiableMap(m);
    }

    private static final Map<Integer, String> REASONS = new HashMap<>();

    static {
        REASONS.put(200, "OK");
        REASONS.put(201, "Created");
        REASONS.put(202, "Accepted");
        REASONS.put(204, "No Content");
        REASONS.put(301, "Moved Permanently");
        REASONS.put(302, "Found");
        REASONS.put(304, "Not Modified");
        REASONS.put(400, "Bad Request");
        REASONS.put(401, "Unauthorized");
        REASONS.put(403, "Forbidden");
        REASONS.put(404, "Not Found");
        REASONS.put(405, "Method Not Allowed");
        REASONS.put(409, "Conflict");
        REASONS.put(415, "Unsupported Media Type");
        REASONS.put(422, "Unprocessable Entity");
        REASONS.put(429, "Too Many Requests");
        REASONS.put(500, "Internal Server Error");
        REASONS.put(501, "Not Implemented");
        REASONS.put(502, "Bad Gateway");
        REASONS.put(503, "Service Unavailable");
        REASONS.put(504, "Gateway Timeout");
    }

    private Tson() {
    }

    public static String reasonPhrase(String status) {
        try {
            return REASONS.get(Integer.parseInt(status.trim()));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static boolean isPrimitive(String name) {
        return name != null && PRIMITIVES.contains(name);
    }

    public static boolean isMethod(String name) {
        return name != null && METHODS.contains(name.toLowerCase());
    }

    public static String mediaType(String shorthand) {
        if (shorthand == null) {
            return null;
        }
        String s = MEDIA_SHORTHANDS.get(shorthand);
        return s != null ? s : shorthand;
    }

    /** Members (children) of an object or array container, in order. */
    public static List<NElement> members(NElement container) {
        if (container == null) {
            return Collections.emptyList();
        }
        NObjectElement o = container.asObject().orNull();
        if (o != null) {
            return o.children();
        }
        NArrayElement a = container.asArray().orNull();
        if (a != null) {
            return a.children();
        }
        return Collections.emptyList();
    }

    /** Name of a member (pair key name or named-container name). */
    public static String nameOf(NElement e) {
        if (e == null) {
            return null;
        }
        NPairElement p = e.asPair().orNull();
        if (p != null) {
            return nameOf(p.key());
        }
        String s = e.asStringValue().orNull();
        if (s != null && !e.isNamed()) {
            return s;
        }
        NNamedElement n = e.asNamed().orNull();
        if (n != null) {
            String nm = n.name().orNull();
            if (nm != null) {
                return nm;
            }
        }
        return s;
    }

    /** Annotations of a member, covering both the pair-level and the key-level placement (P2/P3). */
    public static List<NElementAnnotation> annotationsOf(NElement member) {
        List<NElementAnnotation> ann = new ArrayList<>();
        if (member == null) {
            return ann;
        }
        ann.addAll(member.annotations());
        NPairElement p = member.asPair().orNull();
        if (p != null) {
            ann.addAll(p.key().annotations());
        }
        return ann;
    }

    public static NElementAnnotation annotation(NElement member, String name) {
        for (NElementAnnotation a : annotationsOf(member)) {
            if (a.name().equals(name)) {
                return a;
            }
        }
        return null;
    }

    public static boolean hasAnnotation(NElement member, String name) {
        return annotation(member, name) != null;
    }

    /** Value of a member: the pair value, or the container itself (named objects/arrays). */
    public static NElement valueOf(NElement member) {
        NPairElement p = member.asPair().orNull();
        if (p != null) {
            return p.value();
        }
        return member;
    }

    public static boolean isPair(NElement e) {
        return e != null && e.isPair();
    }

    /** True when the member was written with a head (named object/array/tuple), not a plain pair. */
    public static boolean isHeaded(NElement e) {
        if (e == null || e.isPair()) {
            return false;
        }
        return e.isNamed() && (e.asObject().isPresent() || e.asArray().isPresent());
    }

    /** Positional params of a member head: for {@code out(200)} the tuple params, for {@code GET(…)} the object params. */
    public static List<NElement> headParams(NElement member) {
        NElement head = member;
        if (member != null && member.isPair()) {
            head = member.asPair().get().key();
        }
        return paramsOf(head);
    }

    public static List<NElement> paramsOf(NElement e) {
        if (e == null) {
            return Collections.emptyList();
        }
        NObjectElement o = e.asObject().orNull();
        if (o != null && o.isParametrized()) {
            return o.params().orElse(Collections.emptyList());
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null && a.isParametrized()) {
            return a.params().orElse(Collections.emptyList());
        }
        NTupleElement t = e.asTuple().orNull();
        if (t != null) {
            return t.params();
        }
        return Collections.emptyList();
    }

    public static String asString(NElement e) {
        if (e == null) {
            return null;
        }
        String s = e.asStringValue().orNull();
        if (s != null) {
            return s;
        }
        return e.toString();
    }

    public static boolean asBoolean(NElement e, boolean def) {
        if (e == null) {
            return def;
        }
        Boolean b = e.asBooleanValue().orNull();
        if (b != null) {
            return b;
        }
        String s = e.asStringValue().orNull();
        if (s != null) {
            if ("true".equalsIgnoreCase(s)) {
                return true;
            }
            if ("false".equalsIgnoreCase(s)) {
                return false;
            }
        }
        return def;
    }

    public static boolean isNativeDate(NElement e) {
        return e != null && e.type() == NElementType.LOCAL_DATE;
    }

    public static String dateString(NElement e) {
        LocalDate d = e.asLocalDateValue().orNull();
        if (d != null) {
            return d.toString();
        }
        return asString(e);
    }

    /** Strips the name of a named container (named object/array) so it can be used as a plain value. */
    public static NElement unnamed(NElement e) {
        if (e == null) {
            return null;
        }
        NObjectElement o = e.asObject().orNull();
        if (o != null && o.isNamed()) {
            NObjectElementBuilder b = NObjectElementBuilder.of();
            b.addAll(o.children());
            return b.build();
        }
        NArrayElement a = e.asArray().orNull();
        if (a != null && a.isNamed()) {
            NArrayElementBuilder b = NArrayElementBuilder.of();
            b.addAll(a.children());
            return b.build();
        }
        return e;
    }

    public static NElement str(String s) {
        return NElement.ofString(s);
    }

    public static NElement ofBoolean(boolean b) {
        return NElement.ofBoolean(b);
    }

    public static NObjectElementBuilder obj() {
        return NObjectElementBuilder.of();
    }

    public static NArrayElementBuilder arr() {
        return NArrayElementBuilder.of();
    }
}
