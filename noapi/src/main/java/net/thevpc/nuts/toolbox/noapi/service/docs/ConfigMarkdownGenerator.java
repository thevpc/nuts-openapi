package net.thevpc.nuts.toolbox.noapi.service.docs;

import net.thevpc.nuts.*;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.MConf;
import net.thevpc.nuts.toolbox.noapi.model.MContact;
import net.thevpc.nuts.toolbox.noapi.service.MFileInfo;
import net.thevpc.nuts.toolbox.noapi.service.MStoreAndModel;
import net.thevpc.nuts.util.*;
import net.thevpc.nuts.lib.md.*;
import net.thevpc.nuts.toolbox.noapi.util.AppMessages;
import net.thevpc.nuts.toolbox.noapi.util.NoApiUtils;
import net.thevpc.nuts.toolbox.noapi.store.swagger.OpenApiParser;
import net.thevpc.nuts.toolbox.noapi.model.MVar;
import net.thevpc.nuts.toolbox.noapi.model.Vars;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigMarkdownGenerator {
    private NSession session;
    private AppMessages msg;
    private OpenApiParser openApiParser = new OpenApiParser();
    private int maxExampleInlineLength = 80;

    public ConfigMarkdownGenerator(NSession session, AppMessages msg) {
        this.session = session;
        this.msg = msg;
    }

    public MdDocument createMarkdown(
            MFileInfo mFileInfo, MConf confFile,
            NPath targetFolder,
            String apiDocumentFileName,
            List<String> defaultAdocHeaders) {
        String apiDocumentTitle = mFileInfo.rmodel.model.getTitle();
        String apiDocumentVersion = mFileInfo.rmodel.model.getVersion();
        String configDocumentVersion = confFile.version;
        if (NBlankable.isBlank(configDocumentVersion)) {
            configDocumentVersion = apiDocumentVersion;
        }
        MdDocumentBuilder doc = new MdDocumentBuilder();
        String targetName = confFile.targetName;
        String targetId = confFile.targetId;
        String apiDocumentIdFromConfig = confFile.documentId;
        String apiDocumentIdFromApi = mFileInfo.rmodel.model.getId();
        if (!NBlankable.isBlank(apiDocumentIdFromConfig)) {
            if (!Objects.equals(apiDocumentIdFromConfig, apiDocumentIdFromApi)) {
                throw new NIllegalArgumentException(NMsg.ofC("invalid api version %s <> %s", apiDocumentIdFromConfig, apiDocumentIdFromApi));
            }
        }
        List<String> options = new ArrayList<>(defaultAdocHeaders);
        if (mFileInfo.rmodel.sourceFolder.resolve("logo.png").exists()) {
            options.add(":title-logo-image: " + mFileInfo.rmodel.sourceFolder.resolve("logo.png").normalize().toAbsolute().toString());
        }
        doc.setProperty("headers", options.toArray(new String[0]));
        doc.setDate(LocalDate.now());
        doc.setSubTitle("RESTRICTED - INTERNAL");

        List<MdElement> all = new ArrayList<>();
        all.add(MdFactory.endParagraph());
        String configDocumentTitle = apiDocumentTitle + " Configuration : " + targetName;
        doc.setTitle(configDocumentTitle);
        doc.setVersion(configDocumentVersion);

        all.add(MdFactory.title(1, configDocumentTitle));
//        all.add(new MdImage(null,null,"Logo, 64,64","./logo.png"));
//        all.add(MdFactory.endParagraph());
//        all.add(MdFactory.seq(NoApiUtils.asText("API Reference")));

        Vars vars = OpenApiParser._fillVars(mFileInfo);

        List<MVar> configVars = OpenApiParser.loadConfigVars(confFile, mFileInfo, vars);
        _fillIntroduction(confFile, mFileInfo, all, apiDocumentFileName);
        _fillConfigVars(confFile, all, vars, configVars);
        doc.setContent(MdFactory.seq(all));
        return doc.build();
    }

    private void _fillIntroduction(MConf confFile, MFileInfo mFileInfo,
                                   List<MdElement> all, String apiDocumentFileName) {
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(2, msg.get("INTRODUCTION").get()));
        all.add(MdFactory.endParagraph());
        all.add(NoApiUtils.asText(NStringUtils.firstNonNull(mFileInfo.rmodel.model.getConfigDescription(), "").trim()));
        all.add(MdFactory.endParagraph());
        String targetName = confFile.targetName;
        all.add(NoApiUtils.asText(
                NMsg.ofV(msg.get("section.config.introduction.body").get(), NMaps.of("name", targetName)
                ).toString()));

        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(3, msg.get("CONTACT").get()));
        all.add(NoApiUtils.asText(
                msg.get("section.contact.body").get()
        ));
        all.add(MdFactory.endParagraph());
        MContact contact = NOptional.of(mFileInfo.rmodel.model.getContact()).orElseGet(MContact::new);
        all.add(MdFactory.table()
                .addColumns(
                        MdFactory.column().setName(msg.get("NAME").get()),
                        MdFactory.column().setName(msg.get("EMAIL").get()),
                        MdFactory.column().setName(msg.get("URL").get())
                )
                .addRows(
                        MdFactory.row().addCells(
                                NoApiUtils.asTextTrimmed(contact.name),
                                NoApiUtils.asTextTrimmed(contact.email),
                                NoApiUtils.asTextTrimmed(contact.url)
                        )
                ).build()
        );
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(3, msg.get("REFERENCE_DOCUMENTS").get()));
        all.add(NoApiUtils.asText(
                msg.get("section.reference-document.body").get()
        ));

        String apiDocumentTitle = NStringUtils.firstNonNull(mFileInfo.rmodel.model.getTitle(), "");
        String apiDocumentVersion = NStringUtils.firstNonNull(mFileInfo.rmodel.model.getVersion(), "");

        all.add(MdFactory.endParagraph());
        all.add(MdFactory.table()
                .addColumns(
                        MdFactory.column().setName(msg.get("NAME").get()),
                        MdFactory.column().setName(msg.get("VERSION").get()),
                        MdFactory.column().setName(msg.get("DOCUMENT").get())
                )
                .addRows(
                        MdFactory.row().addCells(
                                NoApiUtils.asText(apiDocumentTitle),
                                NoApiUtils.asText(apiDocumentVersion),
                                NoApiUtils.asText(apiDocumentFileName)
                        )
                ).build()
        );
    }


    private void _fillConfigVars(MConf confFile, List<MdElement> all, Vars vars, List<MVar> configVars) {
        String targetName = confFile.targetName;
        String observations = confFile.observations;
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(2, msg.get("CONFIGURATION").get()));
        all.add(NoApiUtils.asText(
                NMsg.ofV(msg.get("section.config.body").get(), NMaps.of("name", targetName)
                ).toString()));

        if (!NBlankable.isBlank(observations)) {
            all.add(MdFactory.newLine());
            all.add(NoApiUtils.asTextTrimmed(vars.format(observations)));
        }

        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(3, msg.get("CUSTOM_PARAMETER_LIST").get()));
        all.add(NoApiUtils.asText(
                NMsg.ofV(msg.get("section.config.customVars.body").get(), NMaps.of("name", targetName)
                ).toString()));
        all.add(MdFactory.endParagraph());

        for (MVar configVar : configVars) {
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.title(4, configVar.getName()));
            all.add(NoApiUtils.asText(configVar.getDescription()));
            if (!NBlankable.isBlank(configVar.getObservations())) {
                all.add(MdFactory.endParagraph());
                all.add(NoApiUtils.asText(configVar.getObservations()));
            }
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.codeBacktick3("", vars.format(configVar.getValue()), false));
        }
    }
}
