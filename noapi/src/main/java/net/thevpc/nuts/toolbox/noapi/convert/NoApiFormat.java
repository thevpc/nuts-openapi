package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.toolbox.noapi.model.OpenAPIFormat;
import net.thevpc.nuts.util.NOptional;

/**
 * File format of a NoAPI document (API document or configuration document).
 * <p>
 * This is a superset of {@link OpenAPIFormat}: it adds {@link #AUTO} used by the CLI to request
 * extension + content-sniff detection.
 */
public enum NoApiFormat {
    AUTO(""),
    JSON("json"),
    YAML("yaml"),
    TSON("tson");

    private final String id;

    NoApiFormat(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static NOptional<NoApiFormat> parse(String s) {
        if (s == null) {
            return NOptional.ofNamedEmpty("format");
        }
        switch (s.trim().toLowerCase()) {
            case "":
            case "auto":
                return NOptional.of(AUTO);
            case "json":
                return NOptional.of(JSON);
            case "yaml":
            case "yml":
                return NOptional.of(YAML);
            case "tson":
                return NOptional.of(TSON);
            default:
                return NOptional.ofNamedEmpty("format " + s);
        }
    }

    public static NoApiFormat of(OpenAPIFormat f) {
        if (f == null) {
            return AUTO;
        }
        switch (f) {
            case JSON:
                return JSON;
            case YAML:
                return YAML;
            case TSON:
                return TSON;
            default:
                return AUTO;
        }
    }

    public OpenAPIFormat toOpenAPIFormat(OpenAPIFormat def) {
        switch (this) {
            case JSON:
                return OpenAPIFormat.JSON;
            case YAML:
                return OpenAPIFormat.YAML;
            case TSON:
                return OpenAPIFormat.TSON;
            default:
                return def;
        }
    }
}
