package net.thevpc.nuts.toolbox.noapi.service;

import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import net.thevpc.nuts.toolbox.noapi.model.SupportedTargetType;
import net.thevpc.nuts.toolbox.noapi.service.docs.MainMarkdownGenerator;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;

import java.util.HashMap;
import java.util.Map;

public class MStoreAndModel {
    public NoApiStore store;
    public MStoreModel model;
    public Map<String, String> vars=new HashMap<>();
    public SupportedTargetType targetType;
    public NPath sourcePath;
    public NPath sourceFolder;
    public NPath parentPath;
    public String target;
    public boolean keep;
}
