package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.List;

public class FieldInfo {
    public String name;
    public String summary;
    public String description;
    public String fieldTypeName;
    public String baseFieldTypeName;
    public int arrays;
    public List<String> arrayConstraints=new ArrayList<>();
    public TypeInfo schema;
    public boolean required;
    public boolean deprecated;
    public List<MExample> examples=new ArrayList<>();
}
