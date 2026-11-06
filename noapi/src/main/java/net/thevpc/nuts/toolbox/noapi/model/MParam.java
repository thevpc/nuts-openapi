package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.List;

public class MParam {
    public String name;
    public String in;
    public boolean deprecated;
    public String typeName;
    public String smartTypeName;
    public String description;
    public List<MExample> examples=new ArrayList<>();
    public boolean required;
    /**
     * The parsed schema of the parameter (including a {@code $ref} to a component schema), when
     * available. Used to follow the schema usage of enabled/disabled operations.
     */
    public TypeInfo schema;
}
