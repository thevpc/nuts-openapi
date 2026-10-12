package net.thevpc.nuts.toolbox.noapi.convert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the diagnostics produced by the condensed TSON validation pass (prompt section 6).
 * All diagnostics are collected before failing once.
 */
public final class NoApiDiagnostics {

    public static final class Diagnostic {
        public final boolean error;
        public final String code;
        public final String message;
        public final String path;
        public final String file;

        public Diagnostic(boolean error, String code, String message, String path, String file) {
            this.error = error;
            this.code = code;
            this.message = message;
            this.path = path;
            this.file = file;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (file != null) {
                sb.append(file).append(": ");
            }
            sb.append(error ? "error" : "warning").append(' ').append(code);
            if (path != null && !path.isEmpty()) {
                sb.append(" at ").append(path);
            }
            sb.append(": ").append(message);
            return sb.toString();
        }
    }

    private final String file;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public NoApiDiagnostics(String file) {
        this.file = file;
    }

    public void error(String code, String message, String path) {
        diagnostics.add(new Diagnostic(true, code, message, path, file));
    }

    public void warn(String code, String message, String path) {
        diagnostics.add(new Diagnostic(false, code, message, path, file));
    }

    public boolean hasErrors() {
        for (Diagnostic d : diagnostics) {
            if (d.error) {
                return true;
            }
        }
        return false;
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(diagnostics);
    }

    public void throwIfErrors() {
        if (hasErrors()) {
            throw new NoApiDesugarException(diagnostics);
        }
    }

    public static String joinPath(String parent, String child) {
        if (parent == null || parent.isEmpty()) {
            return child;
        }
        if (child == null || child.isEmpty()) {
            return parent;
        }
        return parent + "." + child;
    }
}
