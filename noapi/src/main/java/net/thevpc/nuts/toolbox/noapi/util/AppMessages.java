package net.thevpc.nuts.toolbox.noapi.util;

import net.thevpc.nuts.elem.NElementReader;
import net.thevpc.nuts.text.NMsg;
import net.thevpc.nuts.util.NOptional;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NPairElement;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AppMessages {
    private Map<String, String> values = new HashMap<>();
    private AppMessages parent;

    public AppMessages(AppMessages parent, URL url) {
        this.parent = parent;
        NElement e = NElementReader.ofJson().read(url);
        for (NPairElement entry : e.asObject().get().pairs()) {
            values.put(entry.key().asStringValue().get(), entry.value().asStringValue().get());
        }
    }


    public NOptional<String> get(String key) {
        String value = values.get(key);
        if (value == null) {
            if (parent != null) {
                return parent.get(key);
            }
            return NOptional.ofError(() -> NMsg.ofC("key not found : %s", key));
        }
        return NOptional.of(value);
    }
}
