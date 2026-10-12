package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.toolbox.noapi.convert.NoApiDiagnostics.Diagnostic;

import java.util.List;

/**
 * Thrown when the condensed TSON validation pass found at least one error. Carries every
 * diagnostic collected during the pass.
 */
public class NoApiDesugarException extends RuntimeException {

    private final List<Diagnostic> diagnostics;

    public NoApiDesugarException(List<Diagnostic> diagnostics) {
        super(buildMessage(diagnostics));
        this.diagnostics = diagnostics;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    private static String buildMessage(List<Diagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder("invalid condensed TSON:");
        for (Diagnostic d : diagnostics) {
            if (d.error) {
                sb.append("\n  ").append(d);
            }
        }
        return sb.toString();
    }
}
