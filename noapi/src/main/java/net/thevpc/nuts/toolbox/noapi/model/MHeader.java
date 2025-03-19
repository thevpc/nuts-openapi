package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.List;

public class MHeader {
    public String name;
    public boolean deprecated;
    public String typeName;
    public String smartTypeName;
    public String description;
    public List<MExample> examples=new ArrayList<>();
    public boolean required;
}
