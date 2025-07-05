package net.thevpc.nuts.toolbox.noapi.service;

import net.thevpc.nuts.*;
import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.io.NPathExtensionType;
import net.thevpc.nuts.io.NPathNameParts;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.lib.md.*;
import net.thevpc.nuts.toolbox.noapi.model.DocItemInfo;
import net.thevpc.nuts.toolbox.noapi.model.MConf;
import net.thevpc.nuts.toolbox.noapi.service.docs.ConfigMarkdownGenerator;
import net.thevpc.nuts.toolbox.noapi.service.docs.MainMarkdownGenerator;
import net.thevpc.nuts.toolbox.noapi.model.SupportedTargetType;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.toolbox.noapi.store.swagger.SwaggerStore;
import net.thevpc.nuts.toolbox.noapi.store.TsonStore;
import net.thevpc.nuts.toolbox.noapi.util.AppMessages;
import net.thevpc.nuts.toolbox.noapi.util.NoApiUtils;
import net.thevpc.nuts.util.NBlankable;
import net.thevpc.nuts.util.NMsg;

import java.util.*;
import java.util.stream.Collectors;

public class NOpenAPIService {

    private NSession session;
    private AppMessages msg;
    private List<String> defaultAdocHeaders = Arrays.asList(
            ":source-highlighter: coderay",
            ":icons: font",
            ":icon-set: pf",
            ":doctype: book",
            ":toc:",
            ":toclevels: 3",
            ":appendix-caption: Appx",
            ":sectnums:",
            ":chapter-label:"
    );
    ;

    public NOpenAPIService(NSession session) {
        this.session = session;
        msg = new AppMessages(null, getClass().getResource("/net/thevpc/nuts/toolbox/noapi/messages-en.json"));
    }

    public NPath resolvePath(String source) {
        NPath sourcePath = NPath.of(source).normalize().toAbsolute();
        if (!sourcePath.exists()) {
            throw new NNoSuchElementException(NMsg.ofC("file not found %s", sourcePath));
        }
        if (sourcePath.isDirectory()) {
            List<NPath> found = sourcePath.list().stream().filter(x -> NoApiUtils.isProjectMainFileName(x.getName())).collect(Collectors.toList());
            if (found.size() == 1) {
                return found.get(0);
            }
            if (found.isEmpty()) {
                throw new NNoSuchElementException(NMsg.ofC("missing json files in folder %s", sourcePath));
            }
            throw new NNoSuchElementException(NMsg.ofC("too many json files in folder %s", sourcePath));
        }
        return sourcePath;
    }

    public void run(String source, String target, String varsPath, Map<String, String> varsMap, boolean keep) {

        NPath sourcePath = resolvePath(source);
        if (session.isPlainTrace()) {
            session.out().println(NMsg.ofC("read open-api file %s", sourcePath));
        }
        String sourceBaseName = sourcePath.getNameParts(NPathExtensionType.SMART).getBaseName();
        NoApiStore store;
        if(sourcePath.getName().endsWith(".tson")) {
            store = new TsonStore(sourcePath);
        }else if(sourcePath.getName().endsWith(".json") || sourcePath.getName().endsWith(".yaml")) {
            store=new SwaggerStore(sourcePath);
        }else{
            throw new IllegalArgumentException("Unsupported file type: " + sourcePath);
        }
        Map<String, String> vars = new HashMap<>();

        if (!NBlankable.isBlank(varsPath)) {
            Map<Object, Object> m=store.loadVars(varsPath);
            for (Map.Entry<Object, Object> o : m.entrySet()) {
                vars.put(String.valueOf(o.getKey()), String.valueOf(o.getValue()));
            }
        }
        if (varsMap != null) {
            vars.putAll(varsMap);
        }

        List<DocItemInfo> docInfos = store.getMultiDocuments();
        if (docInfos.isEmpty()) {
            docInfos.add(new DocItemInfo());
        }
        String documentVersion = store.getVersion().orNull();

//        Path path = Paths.get("/data/from-git/RapiPdf/docs/specs/maghrebia-api-1.1.2.yml");
        SupportedTargetType targetType = NoApiUtils.resolveTarget(target, SupportedTargetType.PDF);
        NPath sourceFolder = sourcePath.getParent();
        NPath parentPath = sourceFolder.resolve("dist-version-" + documentVersion);
        NPath targetPathObj = NoApiUtils.addExtension(sourcePath, parentPath, NPath.of(target), targetType, documentVersion, session);

        //start copying json file
        NPath openApiFileCopy = targetPathObj.resolveSibling(targetPathObj.getNameParts(NPathExtensionType.SMART).getBaseName() + "." + sourcePath.getNameParts(NPathExtensionType.SHORT).getExtension());
        sourcePath.copyTo(openApiFileCopy);
        if (session.isPlainTrace()) {
            session.out().println(NMsg.ofC("copy open-api file %s", openApiFileCopy));
        }
        List<NPath> allConfigFiles = searchConfigPaths(sourceFolder, sourceBaseName);
        for (DocItemInfo docInfo : docInfos) {
            String filePart = "";
            if (docInfo.id != null) {
                filePart = "-" + docInfo.id;
            }
            for (NPath cf : allConfigFiles) {
                generateConfigDocumentFromFile(cf, targetPathObj, filePart, documentVersion, targetType, sourcePath, parentPath, store, target, sourceFolder, vars, keep);
            }
            generateMainDocumentFromFile(docInfo, targetPathObj, filePart, documentVersion, targetType, sourcePath, parentPath, store, target, sourceFolder, vars, keep);

        }
    }

    private List<NPath> searchConfigPaths(NPath sourceFolder, String sourceBaseName) {
        return sourceFolder.stream().filter(
                (NPath x) ->
                {
                    return NoApiUtils.isProjectConfigFileName(x.getName())
                            && (
                            x.getName().startsWith(sourceBaseName + ".")
                                    || x.getName().startsWith(sourceBaseName + "-")
                                    || x.getName().startsWith(sourceBaseName + "_")
                    );
                }
        ).redescribe(NDescribables.ofDesc("config files")).toList();
    }

    private void generateMainDocumentFromFile(DocItemInfo docInfo, NPath targetPathObj, String filePart, String documentVersion, SupportedTargetType targetType, NPath sourcePath, NPath parentPath, NoApiStore store, String target, NPath sourceFolder, Map<String, String> vars, boolean keep) {
        MainMarkdownGenerator mg = new MainMarkdownGenerator(msg);
        Map<String, String> vars2 = new HashMap<>(vars);
        vars2.putAll(docInfo.vars);
        MdDocument md = mg.createMarkdown(store, sourceFolder, vars2, defaultAdocHeaders);
        NoApiUtils.writeAdoc(md, targetPathObj.resolveSibling(targetPathObj.getNameParts(NPathExtensionType.SMART).toName("${base}" + filePart + "${fullExtension}")), keep, targetType, session);
    }

    private void generateConfigDocumentFromFile(NPath cf, NPath targetPathObj, String filePart, String documentVersion, SupportedTargetType targetType, NPath sourcePath, NPath parentPath, NoApiStore store, String target, NPath sourceFolder, Map<String, String> vars, boolean keep) {
        MConf confFile=store.loadConfigFile(cf);
        //remove version, will be added later
        NPathNameParts smartParts = cf.getNameParts(NPathExtensionType.SMART);
        NPath configFileCopy = targetPathObj.resolveSibling(smartParts.getBaseName() + filePart + "-" + documentVersion + "." + smartParts.getExtension());
        cf.copyTo(configFileCopy);
        if (session.isPlainTrace()) {
            session.out().println(NMsg.ofC("copy  config  file %s", configFileCopy));
        }
        NPath targetPathObj2 = NoApiUtils.addExtension(sourcePath, parentPath, NPath.of(target), targetType, "", session);
        generateConfigDocument(confFile, store, parentPath, sourceFolder, targetPathObj2.getNameParts(NPathExtensionType.SMART).getBaseName(), targetPathObj.getName(), targetType, keep, vars);
    }

    private void generateConfigDocument(MConf configElements, NoApiStore apiElement, NPath parentPath, NPath sourceFolder, String baseName, String apiFileName, SupportedTargetType targetType, boolean keep, Map<String, String> vars) {
        if (NBlankable.isBlank(configElements.targetId)) {
            configElements.targetId = configElements.targetName;
        }
        vars.put("config.target", configElements.targetName);
        String documentVersion = apiElement.getVersion().orNull();

        NPath newFile = parentPath.resolve(baseName + "-" + NoApiUtils.toValidFileName(configElements.targetId) + "-" + documentVersion + ".pdf");
        ConfigMarkdownGenerator mg = new ConfigMarkdownGenerator(session, msg);
        MdDocument md = mg.createMarkdown(configElements, apiElement, newFile.getParent(), sourceFolder, apiFileName, vars, defaultAdocHeaders);
        NoApiUtils.writeAdoc(md, newFile, keep, targetType, session);
    }

}
