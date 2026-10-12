package net.thevpc.nuts.toolbox.noapi.convert;

import net.thevpc.nuts.elem.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Order-sensitive structural comparison of two OpenAPI trees, with the C6 rule (multi-line strings
 * are compared after trimming trailing whitespace on each line).
 */
public final class NoApiTreeComparator {

    private NoApiTreeComparator() {
    }

    public static boolean equal(NElement a, NElement b) {
        return diff(a, b, "$") == null;
    }

    /**
     * @return {@code null} when equal, otherwise a human readable path + reason of the first
     * difference.
     */
    public static String diff(NElement a, NElement b, String path) {
        if (a == null || b == null) {
            return a == b ? null : path + ": one side is null";
        }
        boolean aObj = a.asObject().isPresent();
        boolean bObj = b.asObject().isPresent();
        if (aObj || bObj) {
            if (aObj != bObj) {
                return path + ": object vs " + b.type();
            }
            return diffObject(a.asObject().get(), b.asObject().get(), path);
        }
        boolean aArr = a.asArray().isPresent();
        boolean bArr = b.asArray().isPresent();
        if (aArr || bArr) {
            if (aArr != bArr) {
                return path + ": array vs " + b.type();
            }
            return diffArray(a.asArray().get(), b.asArray().get(), path);
        }
        String sa = scalarOf(a);
        String sb = scalarOf(b);
        if (!sa.equals(sb)) {
            return path + ": '" + sa + "' != '" + sb + "'";
        }
        return null;
    }

    private static String diffObject(NObjectElement a, NObjectElement b, String path) {
        List<String> ka = new ArrayList<>();
        for (NElement m : Tson.members(a)) {
            ka.add(Tson.nameOf(m));
        }
        List<String> kb = new ArrayList<>();
        for (NElement m : Tson.members(b)) {
            kb.add(Tson.nameOf(m));
        }
        if (!ka.equals(kb)) {
            return path + ": keys " + ka + " != " + kb;
        }
        for (NElement ma : Tson.members(a)) {
            String k = Tson.nameOf(ma);
            NElement mb = Tson.member(b, k);
            String d = diff(Tson.valueOf(ma), Tson.valueOf(mb), path + "." + k);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static String diffArray(NArrayElement a, NArrayElement b, String path) {
        List<NElement> ca = Tson.members(a);
        List<NElement> cb = Tson.members(b);
        if (ca.size() != cb.size()) {
            return path + ": size " + ca.size() + " != " + cb.size();
        }
        for (int i = 0; i < ca.size(); i++) {
            String d = diff(ca.get(i), cb.get(i), path + "[" + i + "]");
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private static String scalarOf(NElement e) {
        if (e.asArray().isPresent() || e.asObject().isPresent()) {
            return e.toString();
        }
        // C6: multi-line strings compared after trimming trailing whitespace on each line
        String s = Tson.asString(e);
        if (s != null && s.indexOf('\n') >= 0) {
            return trimTrailingPerLine(s);
        }
        return s == null ? e.toString() : s;
    }

    private static String trimTrailingPerLine(String s) {
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(trimTrailing(lines[i]));
        }
        return sb.toString();
    }

    private static String trimTrailing(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == ' ' || s.charAt(end - 1) == '\t')) {
            end--;
        }
        return s.substring(0, end);
    }
}
