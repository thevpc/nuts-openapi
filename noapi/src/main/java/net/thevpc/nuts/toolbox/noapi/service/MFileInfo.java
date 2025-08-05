package net.thevpc.nuts.toolbox.noapi.service;

import net.thevpc.nuts.toolbox.noapi.model.DocItemInfo;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import net.thevpc.nuts.toolbox.noapi.model.Vars;
import net.thevpc.nuts.toolbox.noapi.service.docs.MainMarkdownGenerator;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;

import java.util.HashMap;
import java.util.Map;

public class MFileInfo {
    public NoApiStore store;
    public MStoreModel model;
    public Map<String, String> vars = new HashMap<>();
    public Map<String, String> vars2 = new HashMap<>();
    public String filePart;
    public MStoreAndModel rmodel;
    public Vars vvars;
    public MainMarkdownGenerator.Templater templater;
    public DocItemInfo docInfo;

    public MFileInfo(MStoreAndModel rmodel, String filePart, DocItemInfo docInfo) {
        this.rmodel = rmodel;
        this.filePart = filePart;
        this.store = rmodel.store;
        this.model = rmodel.model;
        this.vars = rmodel.vars;
        this.docInfo = docInfo;
        this.vars2 = new HashMap<>(rmodel.vars);
        this.vars2.putAll(docInfo.vars);
    }
}
