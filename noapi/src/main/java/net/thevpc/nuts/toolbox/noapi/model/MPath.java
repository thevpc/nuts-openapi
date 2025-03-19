package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.List;

public class MPath {
    public String url;
    public String enabled;
    public String summary;
    public String description;
    public List<MCall> calls=new ArrayList<>();
}
