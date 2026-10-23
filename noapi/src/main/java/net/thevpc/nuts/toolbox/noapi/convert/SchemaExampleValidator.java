package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.NArrayElement;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates that the examples of a document follow the constraints of the schema they document.
 *
 * <p>An example is a part of the contract of an API: a customer copies it in its own code and
 * expects it to be accepted by the server. This pass checks every example of the document
 * (the examples of the schemas, of their properties, of the arrays and of the request bodies and
 * responses) against the schema it belongs to, and reports one {@code E_EXAMPLE_MISMATCH}
 * diagnostic per violation.</p>
 *
 * <p>The following constraints are checked: {@code type}, {@code enum}, {@code minLength},
 * {@code maxLength}, {@code pattern}, {@code minimum}, {@code maximum}, {@code exclusiveMinimum},
 * {@code exclusiveMaximum}, {@code multipleOf}, {@code required}, {@code nullable}, the
 * {@code date} and {@code date-time} formats, and the recursive validity of the properties and of
 * the items of an array. The {@code allOf}, {@code anyOf} and {@code oneOf} combinators are
 * honoured.</p>
 *
 * <p>The pass is read-only: it never modifies the document.</p>
 */
public class SchemaExampleValidator {

    /**
     * The diagnostic code reported when an example does not follow the constraints of its schema.
     */
    public static final String ERROR_CODE = "E_EXAMPLE_MISMATCH";

    private static final String REF_PREFIX = "#/components/schemas/";

    private final NoApiDiagnostics diagnostics;
    private final Map<String, NElement> schemas = new LinkedHashMap<>();
    private final Set<String> visitedSchemas = new HashSet<>();

    public SchemaExampleValidator(NoApiDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Validates all the examples of an OpenAPI document.
     *
     * @param model       the document (the desugared tree)
     * @param diagnostics the diagnostics the violations are reported to
     */
    public static void validateDocument(NElement model, NoApiDiagnostics diagnostics) {
        new SchemaExampleValidator(diagnostics).run(model);
    }

    public void run(NElement model) {
        NObjectElement root = model == null ? null : model.asObject().orNull();
        if (root == null) {
            return;
        }
        NElement components = root.get("components").orNull();
        if (components != null) {
            NElement declared = components.asObject().orNull() == null ? null
                    : components.asObject().get().get("schemas").orNull();
            if (declared != null && declared.asObject().isPresent()) {
                for (NElement m : declared.asObject().get()) {
                    String name = Tson.nameOf(m);
                    if (name != null) {
                        schemas.put(name, Tson.valueOf(m));
                    }
                }
            }
        }
        // the examples of the declared schemas and of their properties
        NElement declared = components == null || components.asObject().orNull() == null ? null
                : components.asObject().get().get("schemas").orNull();
        if (declared != null && declared.asObject().isPresent()) {
            for (NElement m : declared.asObject().get()) {
                String name = Tson.nameOf(m);
                if (name != null) {
                    validateSchema(Tson.valueOf(m), "$.components.schemas." + name);
                }
            }
        }
        // the examples of the headers and of the parameters
        if (components != null && components.asObject().isPresent()) {
            NElement headers = components.asObject().get().get("headers").orNull();
            if (headers != null && headers.asObject().isPresent()) {
                for (NElement m : headers.asObject().get()) {
                    String name = Tson.nameOf(m);
                    NObjectElement header = Tson.valueOf(m).asObject().orNull();
                    if (name != null && header != null) {
                        checkMemberExample(header, "$.components.headers." + name);
                    }
                }
            }
            NElement parameters = components.asObject().get().get("parameters").orNull();
            if (parameters != null && parameters.asObject().isPresent()) {
                for (NElement m : parameters.asObject().get()) {
                    String name = Tson.nameOf(m);
                    NObjectElement parameter = Tson.valueOf(m).asObject().orNull();
                    if (name != null && parameter != null) {
                        checkMemberExample(parameter, "$.components.parameters." + name);
                    }
                }
            }
        }
        // the examples of the request bodies and of the responses
        NElement paths = root.get("paths").orNull();
        if (paths != null && paths.asObject().isPresent()) {
            for (NElement p : paths.asObject().get()) {
                String pathName = Tson.nameOf(p);
                NObjectElement path = Tson.valueOf(p).asObject().orNull();
                if (pathName == null || path == null) {
                    continue;
                }
                for (NElement op : path) {
                    String method = Tson.nameOf(op);
                    NObjectElement operation = Tson.valueOf(op).asObject().orNull();
                    if (method == null || operation == null) {
                        continue;
                    }
                    String opPath = "$.paths." + pathName + "." + method;
                    NElement requestBody = operation.get("requestBody").orNull();
                    if (requestBody != null && requestBody.asObject().isPresent()) {
                        validateContent(requestBody.asObject().get(), opPath + ".requestBody");
                    }
                    NElement responses = operation.get("responses").orNull();
                    if (responses != null && responses.asObject().isPresent()) {
                        for (NElement r : responses.asObject().get()) {
                            String status = Tson.nameOf(r);
                            NObjectElement response = Tson.valueOf(r).asObject().orNull();
                            if (status != null && response != null) {
                                validateContent(response, opPath + ".responses." + status);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks the example of a header or of a parameter against its schema.
     */
    private void checkMemberExample(NObjectElement member, String path) {
        NElement example = member.get("example").orNull();
        if (example == null) {
            return;
        }
        NElement schema = member.get("schema").orNull();
        check(schema, example, path + ".example");
    }

    /**
     * Checks the examples of the media types of a request body or of a response.
     */
    private void validateContent(NObjectElement owner, String path) {
        NElement content = owner.get("content").orNull();
        if (content == null || !content.asObject().isPresent()) {
            return;
        }
        for (NElement m : content.asObject().get()) {
            String mediaType = Tson.nameOf(m);
            NObjectElement media = Tson.valueOf(m).asObject().orNull();
            if (mediaType == null || media == null) {
                continue;
            }
            String mediaPath = path + ".content." + mediaType;
            NElement schema = media.get("schema").orNull();
            NElement example = media.get("example").orNull();
            if (example != null) {
                check(schema, example, mediaPath + ".example");
            }
            NElement examples = media.get("examples").orNull();
            if (examples != null && examples.asObject().isPresent()) {
                for (NElement e : examples.asObject().get()) {
                    String name = Tson.nameOf(e);
                    NElement value = Tson.valueOf(e);
                    NElement v = value.asObject().orNull() == null ? null
                            : value.asObject().get().get("value").orNull();
                    if (name != null && v != null) {
                        check(schema, v, mediaPath + ".examples." + name + ".value");
                    }
                }
            }
        }
    }

    /**
     * Walks a schema and checks the examples it declares (its own example and the examples of its
     * properties and of its items).
     */
    private void validateSchema(NElement schema, String path) {
        if (schema == null) {
            return;
        }
        String ref = refNameOf(schema);
        if (ref != null) {
            if (!visitedSchemas.add(ref)) {
                // a recursive schema: its examples have already been checked
                return;
            }
        }
        NElement resolved = resolve(schema);
        NObjectElement o = resolved == null ? null : resolved.asObject().orNull();
        if (o == null) {
            return;
        }
        NElement example = o.get("example").orNull();
        if (example != null) {
            check(resolved, example, path + ".example");
        }
        NElement properties = o.get("properties").orNull();
        if (properties != null && properties.asObject().isPresent()) {
            for (NElement p : properties.asObject().get()) {
                String name = Tson.nameOf(p);
                if (name != null) {
                    validateSchema(Tson.valueOf(p), path + ".properties." + name);
                }
            }
        }
        NElement items = o.get("items").orNull();
        if (items != null) {
            validateSchema(items, path + ".items");
        }
        NElement additional = o.get("additionalProperties").orNull();
        if (additional != null && additional.asObject().isPresent()) {
            validateSchema(additional, path + ".additionalProperties");
        }
        for (String combinator : new String[]{"allOf", "anyOf", "oneOf"}) {
            NElement list = o.get(combinator).orNull();
            if (list != null && list.asArray().isPresent()) {
                int i = 0;
                for (NElement sub : list.asArray().get().children()) {
                    validateSchema(sub, path + "." + combinator + "[" + i + "]");
                    i++;
                }
            }
        }
    }

    /**
     * Checks one example against one schema.
     */
    private void check(NElement schema, NElement example, String path) {
        if (schema == null || example == null) {
            return;
        }
        NElement resolved = resolve(schema);
        NObjectElement o = resolved == null ? null : resolved.asObject().orNull();
        if (o == null) {
            return;
        }
        if (example.isNull()) {
            if (!isNullable(o)) {
                error(path, "is null, but the schema does not allow null");
            }
            return;
        }
        String type = stringMember(o, "type");
        if (type != null && !matchesType(type, example)) {
            error(path, "is " + describeWithType(example) + ", but the schema requires " + typeLabel(type));
            return;
        }
        NElement enumValues = o.get("enum").orNull();
        if (enumValues != null && enumValues.asArray().isPresent()) {
            List<NElement> values = enumValues.asArray().get().children();
            boolean found = false;
            StringBuilder allowed = new StringBuilder();
            for (NElement v : values) {
                if (allowed.length() > 0) {
                    allowed.append(", ");
                }
                allowed.append(describe(v));
                if (sameValue(v, example)) {
                    found = true;
                }
            }
            if (!found) {
                error(path, "is " + describe(example) + ", but the schema allows only one of [" + allowed + "]");
                return;
            }
        }
        if (type == null || "string".equals(type)) {
            checkString(o, example, path);
        }
        if (type == null || "number".equals(type) || "integer".equals(type)) {
            checkNumber(o, example, path);
        }
        if (type == null || "object".equals(type)) {
            checkObject(o, example, path);
        }
        if (type == null || "array".equals(type)) {
            checkArray(o, example, path);
        }
        for (String combinator : new String[]{"allOf", "anyOf", "oneOf"}) {
            NElement list = o.get(combinator).orNull();
            if (list == null || !list.asArray().isPresent()) {
                continue;
            }
            List<NElement> branches = list.asArray().get().children();
            if (branches.isEmpty()) {
                continue;
            }
            int matching = 0;
            for (NElement branch : branches) {
                if (matches(branch, example)) {
                    matching++;
                }
            }
            if ("allOf".equals(combinator)) {
                for (NElement branch : branches) {
                    check(branch, example, path);
                }
            } else if ("anyOf".equals(combinator) && matching == 0) {
                error(path, "is " + describe(example) + ", but it matches none of the " + branches.size()
                        + " alternatives of the schema");
            } else if ("oneOf".equals(combinator) && matching != 1) {
                error(path, "is " + describe(example) + ", but it matches " + matching
                        + " alternatives of the schema instead of exactly one");
            }
        }
    }

    /**
     * True when the example satisfies the schema (used by the {@code anyOf}/{@code oneOf}
     * combinators).
     */
    private boolean matches(NElement schema, NElement example) {
        NoApiDiagnostics probe = new NoApiDiagnostics("probe");
        SchemaExampleValidator v = new SchemaExampleValidator(probe);
        v.schemas.putAll(schemas);
        v.check(schema, example, "$");
        return !probe.hasErrors();
    }

    private void checkString(NObjectElement schema, NElement example, String path) {
        if (!Tson.isStringLike(example) && !Tson.isTemporal(example)) {
            return;
        }
        String value = Tson.isTemporal(example) ? Tson.dateString(example) : Tson.asString(example);
        if (value == null) {
            return;
        }
        Integer minLength = intMember(schema, "minLength");
        if (minLength != null && value.length() < minLength) {
            error(path, "is " + value.length() + " characters long, but the schema requires at least "
                    + minLength + " characters");
            return;
        }
        Integer maxLength = intMember(schema, "maxLength");
        if (maxLength != null && value.length() > maxLength) {
            error(path, "is " + value.length() + " characters long, but the schema allows at most "
                    + maxLength + " characters");
            return;
        }
        String pattern = stringMember(schema, "pattern");
        if (pattern != null) {
            try {
                if (!Pattern.compile(pattern).matcher(value).find()) {
                    error(path, "is " + describe(example) + ", which does not match the pattern " + pattern);
                    return;
                }
            } catch (PatternSyntaxException ex) {
                // an invalid pattern is a defect of the schema, not of the example
            }
        }
        String format = stringMember(schema, "format");
        if ("date-time".equals(format) && !isDateTime(value)) {
            error(path, "is " + describe(example) + ", which is not a valid date-time"
                    + " (an ISO-8601 date and time, as in 2026-10-10T09:15:00Z)");
            return;
        }
        if ("date".equals(format) && !isDate(value)) {
            error(path, "is " + describe(example) + ", which is not a valid date"
                    + " (an ISO-8601 date, as in 2026-10-10)");
        }
    }

    private void checkNumber(NObjectElement schema, NElement example, String path) {
        if (!example.isNumber()) {
            return;
        }
        double value = example.asNumberValue().orElse(0d).doubleValue();
        Double minimum = doubleMember(schema, "minimum");
        if (minimum != null && value < minimum) {
            error(path, "is " + trim(value) + ", but the schema requires a value greater than or equal to "
                    + trim(minimum));
            return;
        }
        Double maximum = doubleMember(schema, "maximum");
        if (maximum != null && value > maximum) {
            error(path, "is " + trim(value) + ", but the schema allows a value less than or equal to "
                    + trim(maximum));
            return;
        }
        Double exclusiveMinimum = doubleMember(schema, "exclusiveMinimum");
        if (exclusiveMinimum != null && value <= exclusiveMinimum) {
            error(path, "is " + trim(value) + ", but the schema requires a value greater than "
                    + trim(exclusiveMinimum));
            return;
        }
        Double exclusiveMaximum = doubleMember(schema, "exclusiveMaximum");
        if (exclusiveMaximum != null && value >= exclusiveMaximum) {
            error(path, "is " + trim(value) + ", but the schema requires a value less than "
                    + trim(exclusiveMaximum));
            return;
        }
        Double multipleOf = doubleMember(schema, "multipleOf");
        if (multipleOf != null && multipleOf != 0) {
            double ratio = value / multipleOf;
            if (Math.abs(ratio - Math.rint(ratio)) > 1e-9) {
                error(path, "is " + trim(value) + ", but the schema requires a multiple of "
                        + trim(multipleOf));
            }
        }
    }

    private void checkObject(NObjectElement schema, NElement example, String path) {
        NObjectElement value = example.asObject().orNull();
        if (value == null) {
            return;
        }
        NElement required = schema.get("required").orNull();
        if (required != null && required.asArray().isPresent()) {
            for (NElement r : required.asArray().get().children()) {
                String name = Tson.asString(r);
                if (name != null && !value.get(name).isPresent()) {
                    error(path, "is missing the required property \"" + name + "\"");
                }
            }
        }
        NElement properties = schema.get("properties").orNull();
        if (properties != null && properties.asObject().isPresent()) {
            for (NElement p : properties.asObject().get()) {
                String name = Tson.nameOf(p);
                if (name == null) {
                    continue;
                }
                NElement v = value.get(name).orNull();
                if (v != null) {
                    check(Tson.valueOf(p), v, path + "." + name);
                }
            }
        }
    }

    private void checkArray(NObjectElement schema, NElement example, String path) {
        NArrayElement value = example.asArray().orNull();
        if (value == null) {
            return;
        }
        NElement items = schema.get("items").orNull();
        if (items == null) {
            return;
        }
        int i = 0;
        for (NElement item : value.children()) {
            check(items, item, path + "[" + i + "]");
            i++;
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Resolves a {@code $ref} to the schema it points to. The siblings of a reference (its
     * description for instance) are ignored: they do not carry any constraint.
     */
    private NElement resolve(NElement schema) {
        NElement current = schema;
        for (int i = 0; i < 32; i++) {
            NObjectElement o = current == null ? null : current.asObject().orNull();
            if (o == null) {
                return current;
            }
            String ref = stringMember(o, "$ref");
            if (ref == null) {
                return current;
            }
            String name = ref.startsWith(REF_PREFIX) ? ref.substring(REF_PREFIX.length()) : null;
            if (name == null) {
                return current;
            }
            NElement target = schemas.get(name);
            if (target == null) {
                return current;
            }
            current = target;
        }
        return current;
    }

    private static String refNameOf(NElement schema) {
        NObjectElement o = schema == null ? null : schema.asObject().orNull();
        if (o == null) {
            return null;
        }
        String ref = stringMember(o, "$ref");
        if (ref == null || !ref.startsWith(REF_PREFIX)) {
            return null;
        }
        return ref.substring(REF_PREFIX.length());
    }

    private static boolean isNullable(NObjectElement schema) {
        NElement nullable = schema.get("nullable").orNull();
        return nullable != null && Tson.asBoolean(nullable, false);
    }

    private static boolean matchesType(String type, NElement example) {
        switch (type) {
            case "string": {
                return Tson.isStringLike(example) || Tson.isTemporal(example);
            }
            case "number": {
                return example.isNumber();
            }
            case "integer": {
                if (!example.isNumber()) {
                    return false;
                }
                double v = example.asNumberValue().orElse(0d).doubleValue();
                return v == Math.rint(v);
            }
            case "boolean": {
                return example.isBoolean();
            }
            case "object": {
                return example.isAnyObject();
            }
            case "array": {
                return example.isAnyArray();
            }
            default: {
                return true;
            }
        }
    }

    private static String typeLabel(String type) {
        switch (type) {
            case "string": {
                return "a string";
            }
            case "number": {
                return "a number";
            }
            case "integer": {
                return "an integer";
            }
            case "boolean": {
                return "a boolean";
            }
            case "object": {
                return "an object";
            }
            case "array": {
                return "an array";
            }
            default: {
                return "a " + type;
            }
        }
    }

    /**
     * A readable description of a value, used by the messages.
     */
    private static String describe(NElement e) {
        if (e == null) {
            return "nothing";
        }
        if (e.isNull()) {
            return "null";
        }
        if (Tson.isTemporal(e)) {
            return "\"" + Tson.dateString(e) + "\"";
        }
        if (Tson.isStringLike(e)) {
            return "\"" + Tson.asString(e) + "\"";
        }
        if (e.isBoolean()) {
            return String.valueOf(Tson.asBoolean(e, false));
        }
        if (e.isNumber()) {
            return trim(e.asNumberValue().orElse(0d).doubleValue());
        }
        if (e.isAnyArray()) {
            return "an array";
        }
        if (e.isAnyObject()) {
            return "an object";
        }
        return String.valueOf(e.type()).toLowerCase();
    }

    /**
     * A readable description of a value and of its kind, used when the kind is the problem.
     */
    private static String describeWithType(NElement e) {
        if (e == null) {
            return "nothing";
        }
        if (e.isNull()) {
            return "null";
        }
        if (Tson.isTemporal(e) || Tson.isStringLike(e)) {
            return "a string (" + describe(e) + ")";
        }
        if (e.isBoolean()) {
            return "a boolean (" + describe(e) + ")";
        }
        if (e.isNumber()) {
            return "a number (" + describe(e) + ")";
        }
        if (e.isAnyArray()) {
            return "an array";
        }
        if (e.isAnyObject()) {
            return "an object";
        }
        return describe(e);
    }

    /**
     * True when two values are the same, tolerating the numeric types.
     */
    private static boolean sameValue(NElement a, NElement b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.asNumberValue().orElse(0d).doubleValue()
                    == b.asNumberValue().orElse(0d).doubleValue();
        }
        if (a.isBoolean() && b.isBoolean()) {
            return Tson.asBoolean(a, false) == Tson.asBoolean(b, false);
        }
        if (Tson.isStringLike(a) && Tson.isStringLike(b)) {
            return String.valueOf(Tson.asString(a)).equals(Tson.asString(b));
        }
        if (a.isNull() && b.isNull()) {
            return true;
        }
        return false;
    }

    private static String stringMember(NObjectElement o, String name) {
        NElement v = o.get(name).orNull();
        if (v == null) {
            return null;
        }
        return Tson.asString(v);
    }

    private static Integer intMember(NObjectElement o, String name) {
        Double v = doubleMember(o, name);
        return v == null ? null : (int) Math.rint(v);
    }

    private static Double doubleMember(NObjectElement o, String name) {
        NElement v = o.get(name).orNull();
        if (v == null || !v.isNumber()) {
            return null;
        }
        return v.asNumberValue().orElse(0d).doubleValue();
    }

    private static boolean isDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (RuntimeException ignore) {
            // not an offset date time
        }
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (RuntimeException ignore) {
            // not a local date time
        }
        return false;
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException ignore) {
            return false;
        }
    }

    private static String trim(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private void error(String path, String what) {
        diagnostics.error(ERROR_CODE, "the example of " + path + " " + what, path);
    }
}
