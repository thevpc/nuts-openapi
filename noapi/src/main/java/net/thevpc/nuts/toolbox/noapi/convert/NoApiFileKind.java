package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElement;
import net.thevpc.nuts.util.NOptional;

/**
 * Whether a document is an OpenAPI API document or a NoAPI configuration document.
 */
public enum NoApiFileKind {
    AUTO,
    API,
    CONFIG;

    public static NOptional<NoApiFileKind> parse(String s) {
        if (s == null) {
            return NOptional.ofNamedEmpty("kind");
        }
        switch (s.trim().toLowerCase()) {
            case "":
            case "auto":
                return NOptional.of(AUTO);
            case "api":
            case "openapi":
                return NOptional.of(API);
            case "config":
            case "configuration":
                return NOptional.of(CONFIG);
            default:
                return NOptional.ofNamedEmpty("kind " + s);
        }
    }

    /**
     * Detects the document kind by its filename first ({@code *.config.*}), then by its root keys
     * ({@code openapi-config} =&gt; config, {@code openapi} / {@code @openapi} =&gt; api).
     */
    public static NoApiFileKind detect(String fileName, NElement root) {
        if (fileName != null) {
            if (fileName.endsWith(".config.json") || fileName.endsWith(".config.yaml")
                    || fileName.endsWith(".config.yml") || fileName.endsWith(".config.tson")) {
                return CONFIG;
            }
        }
        if (root != null && root.isObject()) {
            NObjectElement o = root.asObject().get();
            if (o.get("openapi-config").isPresent()) {
                return CONFIG;
            }
            if (o.get("openapi").isPresent()) {
                return API;
            }
            if (o.get("target-name").isPresent() || o.get("target-id").isPresent()) {
                return CONFIG;
            }
            if (o.get("info").isPresent() || o.get("paths").isPresent() || o.get("components").isPresent()) {
                // "@openapi" is parsed as an annotation on the first node (usually info); the
                // desugarer lifts it, but for kind detection the presence of info/paths is enough.
                return API;
            }
        }
        return AUTO;
    }
}
