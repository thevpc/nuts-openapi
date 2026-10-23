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

    /**
     * Names that cannot be used as schema names, because they are type heads of the dialect.
     * {@code datetime} is an accepted alias of {@code timestamp}.
     */
    public static final Set<String> RESERVED_SCHEMA_NAMES = new LinkedHashSet<>(Arrays.asList(
            "string", "number", "integer", "boolean", "array", "object", "void", "enum", "timestamp",
            "datetime"));

    /**
     * Names that cannot be used as security scheme names: {@code all} introduces a security group
     * inside a security list, so it would be ambiguous.
     */
    public static final Set<String> RESERVED_SECURITY_NAMES = new LinkedHashSet<>(Arrays.asList(
            "all"));

    public static final Set<String> METHODS = new LinkedHashSet<>(Arrays.asList(
            "get", "post", "put", "delete", "patch", "head", "options", "trace"));

    public static final Map<String, String> MEDIA_SHORTHANDS;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("json", "application/json");
        m.put("xml", "application/xml");
        m.put("text", "text/plain");
        m.put("tson", "application/tson");
        m.put("yaml", "application/yaml");
        m.put("yml", "application/yaml");
        m.put("csv", "text/csv");
        m.put("html", "text/html");
        m.put("pdf", "application/pdf");
        m.put("xls", "application/vnd.ms-excel");
        m.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        m.put("doc", "application/msword");
        m.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        m.put("zip", "application/zip");
        m.put("form", "application/x-www-form-urlencoded");
        m.put("multipart", "multipart/form-data");
        m.put("binary", "application/octet-stream");
        MEDIA_SHORTHANDS = Collections.unmodifiableMap(m);
    }

    /**
     * Content types that are recognised when they are written as a bare media type (the reverse of
     * {@link #MEDIA_SHORTHANDS}). A media type that is exactly one of these is written back as its
     * shorthand; anything else is written out in full.
     */
    public static boolean isKnownMediaType(String mediaType) {
        return mediaType != null && MEDIA_SHORTHANDS.containsValue(mediaType);
    }

    /**
     * Temporal literals of TSON (dates, times and instants are written without quotes). NoAPI keeps
     * them as they are and serializes them as strings.
     */
    public static boolean isTemporal(NElement e) {
        if (e == null) {
            return false;
        }
        switch (e.type()) {
            case LOCAL_DATE:
            case LOCAL_TIME:
            case LOCAL_DATETIME:
            case INSTANT:
                return true;
            default:
                return false;
        }
    }

    /** Any string-like element, including the TSON {@code ¶}/{@code ¶¶} literals and identifiers. */
    public static boolean isStringLike(NElement e) {
        return e != null && e.type().isAnyStringOrName();
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

    /**
     * The media type of a shorthand: a bare {@code json} becomes {@code application/json}, while an
     * already complete content type ({@code application/json}, {@code text/csv}) is kept as it is.
     */
    public static String mediaTypeOf(NElement e) {
        String s = asString(e);
        if (s == null) {
            return null;
        }
        String m = MEDIA_SHORTHANDS.get(s);
        if (m != null) {
            return m;
        }
        if (s.indexOf('/') >= 0) {
            return s;
        }
        return s;
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

    /** The member of {@code container} whose name equals {@code name}, or {@code null}. */
    public static NElement member(NElement container, String name) {
        if (container == null || name == null) {
            return null;
        }
        for (NElement m : members(container)) {
            if (name.equals(nameOf(m))) {
                return m;
            }
        }
        return null;
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

    /**
     * Annotations of a member, covering both the pair-level and the key-level placement (P2/P3).
     * The parser attaches an annotation written between the key and the value to the value, so the
     * value annotations are part of the member as well.
     */
    public static List<NElementAnnotation> annotationsOf(NElement member) {
        List<NElementAnnotation> ann = new ArrayList<>();
        if (member == null) {
            return ann;
        }
        ann.addAll(member.annotations());
        NPairElement p = member.asPair().orNull();
        if (p != null) {
            ann.addAll(p.key().annotations());
            ann.addAll(p.value().annotations());
        }
        return ann;
    }

    /**
     * The annotations that decorate the member itself (not its value): the annotations of the member
     * and the ones of the key of a pair. This is what flags a member as {@code @metadata}.
     */
    public static List<NElementAnnotation> memberAnnotationsOf(NElement member) {
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

    public static NElementAnnotation memberAnnotation(NElement member, String name) {
        for (NElementAnnotation a : memberAnnotationsOf(member)) {
            if (a.name().equals(name)) {
                return a;
            }
        }
        return null;
    }

    public static boolean hasAnnotation(NElement member, String name) {
        return annotation(member, name) != null;
    }

    public static boolean hasMemberAnnotation(NElement member, String name) {
        return memberAnnotation(member, name) != null;
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

    /**
     * The named params of a head: the pairs given in the parentheses as {@code key: value}
     * ({@code string(description: "d")}). The other params are positional.
     */
    public static List<NPairElement> namedParamsOf(NElement e) {
        List<NPairElement> r = new ArrayList<>();
        for (NElement p : paramsOf(e)) {
            NPairElement pair = p.asPair().orNull();
            if (pair != null) {
                r.add(pair);
            }
        }
        return r;
    }

    /**
     * The positional params of a head: the params that are not pairs ({@code out(200)},
     * {@code url("https://x")}, {@code http(bearer)}).
     */
    public static List<NElement> positionalParamsOf(NElement e) {
        List<NElement> r = new ArrayList<>();
        for (NElement p : paramsOf(e)) {
            if (!p.isPair()) {
                r.add(p);
            }
        }
        return r;
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
