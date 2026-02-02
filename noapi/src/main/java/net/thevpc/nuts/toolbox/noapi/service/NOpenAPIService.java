package net.thevpc.nuts.toolbox.noapi.service;

import net.thevpc.nuts.core.NSession;
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
import net.thevpc.nuts.toolbox.noapi.store.swagger.SwaggerStore;
import net.thevpc.nuts.toolbox.noapi.store.ncigar.TsonStore;
import net.thevpc.nuts.toolbox.noapi.util.AppMessages;
import net.thevpc.nuts.toolbox.noapi.util.NoApiUtils;
import net.thevpc.nuts.util.NBlankable;
import net.thevpc.nuts.text.NMsg;
import net.thevpc.nuts.util.NNoSuchElementException;

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
        String sourceBaseName = sourcePath.nameParts(NPathExtensionType.SMART).getBaseName();
        MStoreAndModel rmodel=new MStoreAndModel();
        if(sourcePath.getName().endsWith(".tson")) {
            rmodel.store = new TsonStore();
        }else if(sourcePath.getName().endsWith(".json") || sourcePath.getName().endsWith(".yaml")) {
            rmodel.store=new SwaggerStore();
        }else{
            throw new IllegalArgumentException("Unsupported file type: " + sourcePath);
        }
        rmodel.model=rmodel.store.loadStoreModel(sourcePath);
        rmodel.keep=keep;
        rmodel.sourcePath=sourcePath;
        rmodel.target=target;

        if (!NBlankable.isBlank(varsPath)) {
            Map<Object, Object> m=rmodel.store.loadVars(varsPath);
            for (Map.Entry<Object, Object> o : m.entrySet()) {
                rmodel.vars.put(String.valueOf(o.getKey()), String.valueOf(o.getValue()));
            }
        }
        if (varsMap != null) {
            rmodel.vars.putAll(varsMap);
        }

        List<DocItemInfo> docInfos = rmodel.model.getMultiDocuments();
        if (docInfos.isEmpty()) {
            docInfos.add(new DocItemInfo());
        }
        String documentVersion = rmodel.model.getVersion();

//        Path path = Paths.get("/data/from-git/RapiPdf/docs/specs/maghrebia-api-1.1.2.yml");
        rmodel.targetType = NoApiUtils.resolveTarget(target, SupportedTargetType.PDF);
        rmodel.sourceFolder = rmodel.sourcePath.getParent();
        rmodel.parentPath = rmodel.sourceFolder.resolve("dist-version-" + documentVersion);
        NPath targetPathObj = NoApiUtils.addExtension(sourcePath, rmodel.parentPath, NPath.of(target), rmodel.targetType, documentVersion);

        //start copying json file
        NPath openApiFileCopy = targetPathObj.resolveSibling(targetPathObj.nameParts(NPathExtensionType.SMART).getBaseName() + "." + rmodel.sourcePath.nameParts(NPathExtensionType.SHORT).getExtension());
        rmodel.sourcePath.copyTo(openApiFileCopy);
        if (session.isPlainTrace()) {
            session.out().println(NMsg.ofC("copy open-api file %s", openApiFileCopy));
        }
        List<NPath> allConfigFiles = searchConfigPaths(rmodel.sourceFolder, sourceBaseName);
        for (DocItemInfo docInfo : docInfos) {
            String filePart = "";
            if (docInfo.id != null) {
                filePart = "-" + docInfo.id;
            }
            MFileInfo mFileInfo = new MFileInfo(rmodel, filePart, docInfo);
            for (NPath cf : allConfigFiles) {
                generateConfigDocumentFromFile(mFileInfo, cf, targetPathObj);
            }
            generateMainDocumentFromFile(mFileInfo, targetPathObj, mFileInfo.rmodel.sourceFolder);

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
        ).withDescription(NDescribables.ofDesc("config files")).toList();
    }

    private void generateMainDocumentFromFile(MFileInfo mFileInfo, NPath targetPathObj, NPath sourceFolder) {
        MainMarkdownGenerator mg = new MainMarkdownGenerator(msg);
        MdDocument md = mg.createMarkdown(mFileInfo, sourceFolder, defaultAdocHeaders);
        NoApiUtils.writeAdoc(md, targetPathObj.resolveSibling(targetPathObj.nameParts(NPathExtensionType.SMART).toName("${base}" + mFileInfo.filePart + "${fullExtension}")),
                mFileInfo.rmodel.keep,
                mFileInfo.rmodel.targetType);
    }

    private void generateConfigDocumentFromFile(MFileInfo mFileInfo, NPath cf, NPath targetPathObj) {
        MConf confFile=mFileInfo.rmodel.store.loadConfigFile(cf);
        //remove version, will be added later
        NPathNameParts smartParts = cf.nameParts(NPathExtensionType.SMART);
        NPath configFileCopy = targetPathObj.resolveSibling(smartParts.getBaseName() + mFileInfo.filePart + "-" + mFileInfo.rmodel.model.getVersion() + "." + smartParts.getExtension());
        cf.copyTo(configFileCopy);
        if (session.isPlainTrace()) {
            session.out().println(NMsg.ofC("copy  config  file %s", configFileCopy));
        }
        NPath targetPathObj2 = NoApiUtils.addExtension(mFileInfo.rmodel.sourcePath, mFileInfo.rmodel.parentPath, NPath.of(mFileInfo.rmodel.target), mFileInfo.rmodel.targetType, "");
        generateConfigDocument(mFileInfo, confFile, targetPathObj2.nameParts(NPathExtensionType.SMART).getBaseName(), targetPathObj.getName());
    }

    private void generateConfigDocument(MFileInfo mFileInfo, MConf configElements, String baseName, String apiFileName) {
        if (NBlankable.isBlank(configElements.targetId)) {
            configElements.targetId = configElements.targetName;
        }
        mFileInfo.rmodel.vars.put("config.target", configElements.targetName);
        String documentVersion = mFileInfo.rmodel.model.getVersion();

        NPath newFile = mFileInfo.rmodel.parentPath.resolve(baseName + "-" + NoApiUtils.toValidFileName(configElements.targetId) + "-" + documentVersion + ".pdf");
        ConfigMarkdownGenerator mg = new ConfigMarkdownGenerator(session, msg);
        MdDocument md = mg.createMarkdown(mFileInfo, configElements, newFile.getParent(), apiFileName, defaultAdocHeaders);
        NoApiUtils.writeAdoc(md, newFile, mFileInfo.rmodel.keep, mFileInfo.rmodel.targetType);
    }

}
