package net.thevpc.nuts.toolbox.noapi.model;

import net.thevpc.nuts.elem.NObjectElement;

import java.util.HashMap;
import java.util.Map;

public class DocItemInfo {
    public String id;
    public NObjectElement raw;
    public Map<String, String> vars = new HashMap<>();
}
