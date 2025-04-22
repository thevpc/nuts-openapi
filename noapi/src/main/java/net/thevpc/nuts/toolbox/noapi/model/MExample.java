package net.thevpc.nuts.toolbox.noapi.model;

import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.util.NBlankable;

import java.util.Objects;

public class MExample implements NBlankable {
    public String description;
    public NElement value;

    public MExample(String description, NElement value) {
        this.description = description;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MExample mExample = (MExample) o;
        return Objects.equals(description, mExample.description) && Objects.equals(value, mExample.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, value);
    }

    @Override
    public boolean isBlank() {
        if(!NBlankable.isBlank(value)){
           return false;
        }
        if(!NBlankable.isBlank(value)){
           return false;
        }
        return true;
    }
}
