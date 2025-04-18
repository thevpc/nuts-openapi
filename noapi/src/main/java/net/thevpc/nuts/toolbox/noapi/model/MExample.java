package net.thevpc.nuts.toolbox.noapi.model;

import net.thevpc.nuts.elem.NElement;

public class MExample {
    public String description;
    public NElement value;

    public MExample(String description, NElement value) {
        this.description = description;
        this.value = value;
    }
}
