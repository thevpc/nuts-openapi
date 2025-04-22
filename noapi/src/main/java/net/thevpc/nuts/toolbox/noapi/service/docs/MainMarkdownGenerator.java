package net.thevpc.nuts.toolbox.noapi.service.docs;

import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.expr.*;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.util.*;
import net.thevpc.nuts.lib.md.*;
import net.thevpc.nuts.toolbox.noapi.util.AppMessages;
import net.thevpc.nuts.toolbox.noapi.util.NoApiUtils;
import net.thevpc.nuts.toolbox.noapi.store.swagger.OpenApiParser;
import net.thevpc.nuts.toolbox.noapi.util._StringUtils;
import net.thevpc.nuts.toolbox.noapi.model.*;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class MainMarkdownGenerator {
    private AppMessages msg;
    private Properties httpCodes = new Properties();
    private OpenApiParser openApiParser = new OpenApiParser();
    private int maxExampleInlineLength = 80;

    public MainMarkdownGenerator(AppMessages msg) {
        this.msg = msg;
        try (InputStream is = getClass().getResourceAsStream("/net/thevpc/nuts/toolbox/noapi/http-codes.properties")) {
            httpCodes.load(is);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public MdDocument createMarkdown(NoApiStore store, NPath folder, Map<String, String> vars0, List<String> defaultAdocHeaders) {
        MdDocumentBuilder doc = new MdDocumentBuilder();

        List<String> options = new ArrayList<>(defaultAdocHeaders);
        if (folder.resolve("logo.png").exists()) {
            options.add(":title-logo-image: " + folder.resolve("logo.png").normalize().toAbsolute().toString());
        }
        doc.setProperty("headers", options.toArray(new String[0]));
        doc.setDate(LocalDate.now());
        doc.setSubTitle("RESTRICTED - INTERNAL");

        List<MdElement> all = new ArrayList<>();
        Vars vars = OpenApiParser._fillVars(store, vars0);

        all.add(MdFactory.endParagraph());
        String documentTitle = store.getTitle().orNull();
        Templater templater = new Templater(vars.toMap());
        doc.setTitle(templater.prepareString(documentTitle));
        String documentVersion = store.getVersion().orNull();
        doc.setVersion(templater.prepareString(documentVersion));

        all.add(MdFactory.title(1, templater.prepareString(documentTitle)));
//        all.add(new MdImage(null,null,"Logo, 64,64","./logo.png"));
//        all.add(MdFactory.endParagraph());
//        all.add(MdFactory.seq(NoApiUtils.asText("API Reference")));
        _fillIntroduction(store, all, vars, templater);
        _fillConfigVars(store, all, vars, templater);
        _fillServerList(store, all, vars, templater);
        _fillHeaders(store, all, vars, templater);
        _fillSecuritySchemes(store, all, vars, templater);
        _fillApiPaths(store, all, vars, templater);
        _fillSchemaTypes(store, all);
        doc.setContent(MdFactory.seq(all));
        return doc.build();
    }

    private static class Templater {
        private Map<String, String> vars0;
        private NExprTemplate bashStyleTemplate;
        private NExprMutableDeclarations declarations;

        public Templater(Map<String, String> vars0) {
            this.vars0 = vars0;
            declarations = NExprs.of().newMutableDeclarations(new NExprEvaluator() {
                @Override
                public NOptional<NExprVar> getVar(String varName, NExprDeclarations context) {
                    String u = vars0.get(varName);
                    if (u != null) {
                        return NOptional.of(context.ofVar(varName, u));
                    }
                    return NOptional.of(context.ofVar(varName, null));
                }
            });
        }

        public boolean evalBoolean(String enabled, boolean defaultValue) {
            if (enabled != null) {
                return NLiteral.of(prepareString(enabled)).asBoolean().orElse(defaultValue);
            }
            return defaultValue;
        }

        public String prepareString(String text) {
            if (bashStyleTemplate == null) {
                bashStyleTemplate = declarations.ofTemplate().withBashStyle();
            }
            return bashStyleTemplate.processString(text);
        }
    }

    private void _fillConfigVars(NoApiStore store, List<MdElement> all, Vars vars2, Templater templater) {
        String target = "your-company";
        vars2.putDefault("config.target", target);
        List<MVar> configVars = OpenApiParser.loadConfigVars(null, store, vars2);
        if (configVars.isEmpty()) {
            return;
        }
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(2, msg.get("CONFIGURATION").get()));
        all.add(NoApiUtils.asText(
                NMsg.ofV(msg.get("section.config.master.body").get(), NMaps.of("name", target)
                ).toString()));
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(3, msg.get("CUSTOM_PARAMETER_LIST").get()));
        all.add(NoApiUtils.asText(
                NMsg.ofV(msg.get("section.config.master.customVars.body").get(), NMaps.of("name", target)
                ).toString()));
        all.add(MdFactory.endParagraph());

        for (MVar configVar : configVars) {
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.title(4, "<<" + configVar.getId() + ">> : " + configVar.getName()));
            all.add(NoApiUtils.asText(configVar.getDescription()));
            all.add(MdFactory.endParagraph());
            List<MExample> examples = configVar.getExamples();
            if (examples.size() == 1) {
                all.add(NoApiUtils.asText("The following is an example :"));
                MExample example = examples.get(0);
                if (!NBlankable.isBlank(example.description)) {
                    all.add(NoApiUtils.asTextTrimmed(example.description));
                }
                all.add(MdFactory.codeBacktick3("", vars2.formatObject(example.value), false));
            } else if (examples.size() > 1) {
                all.add(NoApiUtils.asText("The following are some examples :"));
                int eIndex = 1;
                for (MExample example : examples) {
                    all.add(NoApiUtils.asText("Example " + eIndex));
                    if (!NBlankable.isBlank(example.description)) {
                        all.add(NoApiUtils.asTextTrimmed(example.description));
                    }
                    all.add(MdFactory.codeBacktick3("", vars2.formatObject(example.value), false));
                    eIndex++;
                }
            }
        }
    }


    private void _fillIntroduction(NoApiStore store, List<MdElement> all, Vars vars, Templater templater) {
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(2, msg.get("INTRODUCTION").get()));
        all.add(MdFactory.endParagraph());
        all.add(NoApiUtils.asText(store.getDescription().orElse("").trim()));
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(3, msg.get("CONTACT").get()));
        all.add(NoApiUtils.asText(
                msg.get("section.contact.body").get()
        ));
        all.add(MdFactory.endParagraph());

        MContact contact = store.getContact().orElse(new MContact());
        all.add(MdFactory.table()
                .addColumns(
                        MdFactory.column().setName(msg.get("NAME").get()),
                        MdFactory.column().setName(msg.get("EMAIL").get()),
                        MdFactory.column().setName(msg.get("URL").get())
                )
                .addRows(
                        MdFactory.row().addCells(
                                NoApiUtils.asText(NStringUtils.trim(contact.name)),
                                NoApiUtils.asText(NStringUtils.trim(contact.email)),
                                NoApiUtils.asText(NStringUtils.trim(contact.url))
                        )
                ).build()
        );
        all.add(MdFactory.endParagraph());

        all.add(MdFactory.title(3, msg.get("CHANGES").get()));
        all.add(NoApiUtils.asText(
                msg.get("section.changes.body").get()
        ));
        all.add(MdFactory.endParagraph());

        MdTableBuilder changeLogTable = MdFactory.table()
                .addColumns(
                        MdFactory.column().setName(msg.get("DATE").get()),
                        MdFactory.column().setName(msg.get("VERSION").get()),
                        MdFactory.column().setName(msg.get("DESCRIPTION").get())
                );
        List<MChangeLog> changeLogs = store.findChangeLogs();
        for (MChangeLog cl : changeLogs) {
            changeLogTable.addRows(
                    MdFactory.row().addCells(
                            NoApiUtils.asTextTrimmed(cl.date),
                            NoApiUtils.asTextTrimmed(cl.version),
                            NoApiUtils.asTextTrimmed(cl.title)
                    )
            );
        }

        all.add(changeLogTable.build());
        all.add(MdFactory.endParagraph());
        for (MChangeLog e : changeLogs) {
            all.add(MdFactory.title(4, "VERSION " + NStringUtils.trim(e.version) + " : " + NStringUtils.trim(e.title)));
            all.add(MdFactory.endParagraph());
            all.add(NoApiUtils.asText(
                    NStringUtils.trim(e.observations)
            ));
            all.add(MdFactory.endParagraph());
            for (String item : e.details) {
                all.add(MdFactory.ul(1, NoApiUtils.asTextTrimmed(item)));
            }
            all.add(MdFactory.endParagraph());
        }
    }

    private void _fillHeaders(NoApiStore store, List<MdElement> all, Vars vars2, Templater templater) {
        List<MHeader> headers = store.findHeaders();
        if (!headers.isEmpty()) {
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.title(3, msg.get("HEADERS").get()));
            all.add(MdFactory.endParagraph());
            all.add(NoApiUtils.asText(msg.get("section.headers.body").get()));
            all.add(MdFactory.endParagraph());
            MdTableBuilder table = MdFactory.table()
                    .addColumns(
                            MdFactory.column().setName(msg.get("NAME").get()),
                            MdFactory.column().setName(msg.get("TYPE").get()),
                            MdFactory.column().setName(msg.get("DESCRIPTION").get())
                    );

            for (MHeader item : headers) {
                String k = NStringUtils.trim(item.name);
                k = k + (item.deprecated ? (" [" + msg.get("DEPRECATED").get() + "]") : "");
                k = k + requiredSuffix(item.required);
                table.addRows(
                        MdFactory.row().addCells(
                                MdFactory.codeBacktick3("", k),
                                MdFactory.codeBacktick3("", item.typeName),
                                NoApiUtils.asText(item.description)
                        )
                );
            }
            all.add(table.build());
        }
    }

    private void _fillSecuritySchemes(NoApiStore store, List<MdElement> all, Vars vars2, Templater templater) {
        // NObjectElement entries
        List<MSecurityScheme> securitySchemes = store.findSecuritySchemes();
        if (!securitySchemes.isEmpty()) {
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.title(3, msg.get("SECURITY_AND_AUTHENTICATION").get()));
            all.add(MdFactory.endParagraph());
            all.add(NoApiUtils.asText(msg.get("section.security.body").get()));
            for (MSecurityScheme item : securitySchemes) {
//                String type = ee.value().asObject().get().getString("type").orElse("");
//                String description = ee.value().asObject().get().getString("description").orElse("");
//                String name = ee.value().asObject().get().getString("name").orElse("");
//                String in = ee.value().asObject().get().getString("in").orElse("");
//                String scheme = ee.value().asObject().get().getString("scheme").orElse("");
//                String bearerFormat = ee.value().asObject().get().getString("bearerFormat").orElse("");
                switch (item.type) {
                    case apiKey: {
                        all.add(MdFactory.endParagraph());
                        all.add(MdFactory.title(4, item.id + " (Api Key)"));
                        all.add(MdFactory.endParagraph());
                        all.add(NoApiUtils.asText(vars2.format(item.description)));
                        all.add(MdFactory.endParagraph());
                        all.add(MdFactory
                                .table().addColumns(
                                        MdFactory.column().setName(msg.get("NAME").get()),
                                        MdFactory.column().setName(msg.get("IN").get())
                                )
                                .addRows(MdFactory.row()
                                        .addCells(
                                                MdFactory.codeBacktick3("",
                                                        vars2.format(item.name)),
                                                MdFactory.codeBacktick3("",
                                                        vars2.format(item.in.toUpperCase())
                                                )
                                        ))
                                .build()
                        );
                        break;
                    }
                    case http: {
                        all.add(MdFactory.endParagraph());
                        all.add(MdFactory.title(4, item.id + " (Http)"));
                        all.add(MdFactory.endParagraph());
                        all.add(NoApiUtils.asText(
                                vars2.format(item.description)));
                        all.add(MdFactory
                                .table().addColumns(
                                        MdFactory.column().setName(msg.get("SCHEME").get()),
                                        MdFactory.column().setName(msg.get("BEARER").get())
                                )
                                .addRows(MdFactory.row()
                                        .addCells(
                                                NoApiUtils.asTextTrimmed(vars2.format(item.scheme)),
                                                NoApiUtils.asTextTrimmed(vars2.format(item.bearerFormat))
                                        ))
                                .build()
                        );
                        break;
                    }
                    case oauth2: {
                        all.add(MdFactory.endParagraph());
                        all.add(MdFactory.title(4, item.id + " (Oauth2)"));
                        all.add(MdFactory.endParagraph());
                        all.add(NoApiUtils.asTextTrimmed(vars2.format(item.description)));
//                        all.add(MdFactory
//                                .table().addColumns(
//                                        MdFactory.column().setName("SCHEME"),
//                                        MdFactory.column().setName("BEARER")
//                                )
//                                .addRows(MdFactory.row()
//                                        .addCells(
//                                                asText(ee.getValue().asObject().getString("scheme")),
//                                                asText(ee.getValue().asObject().getString("bearerFormat"))
//                                        ))
//                        );
                        break;
                    }
                    case openIdConnect: {
                        all.add(MdFactory.endParagraph());
                        all.add(MdFactory.title(4, item.id + " (OpenId Connect)"));
                        all.add(MdFactory.endParagraph());
                        all.add(NoApiUtils.asText(item.description));
                        all.add(MdFactory
                                .table().addColumns(
                                        MdFactory.column().setName("URL")
                                )
                                .addRows(MdFactory.row()
                                        .addCells(
                                                NoApiUtils.asTextTrimmed(item.openIdConnectUrl)
                                        ))
                                .build()
                        );
                        break;
                    }
                    default: {
                        all.add(MdFactory.endParagraph());
                        all.add(MdFactory.title(4, item.id + " (" + item.typeName + ")"));
                        all.add(NoApiUtils.asText(vars2.formatTrimmed(item.description)));
                    }
                }
            }
        }

    }


    private void _fillSchemaTypes(NoApiStore store, List<MdElement> all) {
        Map<String, TypeInfo> allTypes = store.findTypesMap();
        if (allTypes.isEmpty()) {
            return;
        }
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(2, msg.get("SCHEMA_TYPES").get()));
        for (Map.Entry<String, TypeInfo> entry : allTypes.entrySet()) {
            TypeInfo v = entry.getValue();
            if ("object".equals(v.getType())) {
                all.add(MdFactory.endParagraph());
                all.add(MdFactory.title(3, entry.getKey()));
                String d1 = v.getDescription();
                String d2 = v.getSummary();
                if (!NBlankable.isBlank(d1) && !NBlankable.isBlank(d2)) {
                    all.add(NoApiUtils.asText(d1));
                    all.add(MdFactory.text(". "));
                    all.add(NoApiUtils.asText(d2));
                    if (!NBlankable.isBlank(d2) && !d2.endsWith(".")) {
                        all.add(MdFactory.text("."));
                    }
                } else if (!NBlankable.isBlank(d1)) {
                    all.add(NoApiUtils.asText(d1));
                    if (!NBlankable.isBlank(d1) && !d1.endsWith(".")) {
                        all.add(MdFactory.text("."));
                    }
                } else if (!NBlankable.isBlank(d2)) {
                    all.add(NoApiUtils.asText(d2));
                    if (!NBlankable.isBlank(d2) && !d2.endsWith(".")) {
                        all.add(NoApiUtils.asText("."));
                    }
                }
                List<TypeCrossRef> types = store.typeCrossRefs().stream().filter(x -> x.getType().equals(v.getName())).collect(Collectors.toList());
                if (types.size() > 0) {
                    all.add(MdFactory.endParagraph());
                    all.add(NoApiUtils.asText(msg.get("ThisTypeIsUsedIn").get()));
                    all.add(MdFactory.endParagraph());
                    for (TypeCrossRef type : types) {
                        all.add(MdFactory.ul(1,
                                MdFactory.ofListOrEmpty(
                                        new MdElement[]{
                                                MdFactory.codeBacktick3("", type.getUrl()),
                                                NoApiUtils.asText(" (" + type.getLocation() + ")"),
                                        }
                                )
                        ));
                    }
                    all.add(MdFactory.endParagraph());
                }

                MdTableBuilder mdTableBuilder = MdFactory.table().addColumns(
                        MdFactory.column().setName(msg.get("NAME").get()),
                        MdFactory.column().setName(msg.get("TYPE").get()),
                        MdFactory.column().setName(msg.get("DESCRIPTION").get()),
                        MdFactory.column().setName(msg.get("EXAMPLE").get())
                );
                for (FieldInfo p : v.getFields()) {
                    mdTableBuilder.addRows(
                            MdFactory.row().addCells(
                                    NoApiUtils.asText(p.name),
                                    NoApiUtils.codeElement(p.schema, false, requiredSuffix(p.required), msg),
                                    NoApiUtils.asText(p.description == null ? "" : p.description.trim()),
                                    NoApiUtils.jsonTextElementInlined(p.examples.isEmpty() ? null : p.examples.get(0).value)
                            )
                    );
                }
                all.add(mdTableBuilder.build());
            }
            for (MExample example : v.getExamples()) {
                if (!NBlankable.isBlank(example.value)) {
                    all.add(MdFactory.endParagraph());
                    all.add(NoApiUtils.asText(msg.get("EXAMPLE").get()));
                    all.add(NoApiUtils.asText(":"));
                    all.add(MdFactory.endParagraph());
                    all.add(NoApiUtils.jsonTextElement(example.value));
                }
            }
        }
    }


    private void _fillApiPaths(NoApiStore store, List<MdElement> all, Vars vars2, Templater templater) {
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(2, msg.get("API_PATHS").get()));
        List<MPath> paths = store.findPaths();
        paths = paths.stream().filter(x -> templater.evalBoolean(x.enabled, true)).collect(Collectors.toList());
        int apiSize = paths.size();
        all.add(NoApiUtils.asText(NMsg.ofV(msg.get("API_PATHS.body").get(), NMaps.of("apiSize", apiSize)).toString()));
        all.add(MdFactory.endParagraph());
        for (MPath path : paths) {
            String url = path.url;
            all.add(MdFactory.ul(1, MdFactory.codeBacktick3("", url)));
        }
        all.add(MdFactory.endParagraph());
        all.add(NoApiUtils.asText(msg.get("API_PATHS.text").get()));
        for (MPath path : paths) {
            for (MCall call : path.calls) {
                _fillApiPathMethod(store, call, path, all);
            }
        }
    }

    private void _fillServerList(NoApiStore store, List<MdElement> all, Vars vars2, Templater templater) {
        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(3, "SERVER LIST"));
        all.add(NoApiUtils.asText(
                msg.get("section.serverlist.body").get()
        ));
        for (MServer srv : store.findServers()) {
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.title(4, vars2.formatTrimmed(srv.url)));
            all.add(NoApiUtils.asText(vars2.formatTrimmed(srv.description)));
            if (!srv.variables.isEmpty()) {
                MdTableBuilder mdTableBuilder = MdFactory.table().addColumns(
                        MdFactory.column().setName("NAME"),
                        MdFactory.column().setName("SPEC"),
                        MdFactory.column().setName("DESCRIPTION")
                );
                for (MVar item : srv.variables) {
                    mdTableBuilder.addRows(
                            MdFactory.row().addCells(
                                    NoApiUtils.asText(item.name),
                                    //                                asText(variables.getValue().asObject().getString("enum")),
                                    NoApiUtils.asText(vars2.format(item.value)),
                                    NoApiUtils.asText(vars2.format(item.description))
                            )
                    );
                }
                all.add(mdTableBuilder.build());
            }
        }
    }


    private String getSmartTypeName(NObjectElement obj) {
        String e = _StringUtils.nvl(obj.get("type").flatMap(x -> x.asStringValue()).orNull(), "string");
        if ("array".equals(e)) {
            NObjectElement items = obj.getObject("items").orNull();
            if (items != null) {
                return getSmartTypeName(items) + "[]";
            } else {
                return e;
            }
        } else {
            return e;
        }
    }

    private void _fillApiPathMethodParam(List<MParam> headerParameters, List<MdElement> all) {
        MdTable tab = new MdTable(
                new MdColumn[]{
                        new MdColumn(NoApiUtils.asText(msg.get("NAME").get()), MdHorizontalAlign.LEFT),
                        new MdColumn(NoApiUtils.asText(msg.get("TYPE").get()), MdHorizontalAlign.LEFT),
                        new MdColumn(NoApiUtils.asText(msg.get("DESCRIPTION").get()), MdHorizontalAlign.LEFT),
                        new MdColumn(NoApiUtils.asText(msg.get("EXAMPLE").get()), MdHorizontalAlign.LEFT)
                },
                headerParameters.stream().map(
                        obj -> {
                            String name = obj.name;
                            String type = obj.smartTypeName
                                    + requiredSuffix(obj.required);
                            return new MdRow(
                                    new MdElement[]{
                                            MdFactory.codeBacktick3("", _StringUtils.nvl(name, "unknown")
                                                    + (obj.deprecated ? (" [" + msg.get("DEPRECATED").get() + "]") : "")
                                            ),
                                            MdFactory.codeBacktick3("", type),
                                            NoApiUtils.asTextTrimmed(obj.description),
                                            NoApiUtils.jsonTextElementInlined(obj.description),
                                    }, false
                            );
                        }
                ).toArray(MdRow[]::new)
        );
        all.add(tab);
    }

    private String requiredSuffix(NObjectElement obj) {
        return requiredSuffix(obj.getBooleanValue("required").orElse(false));
    }

    private String requiredSuffix(boolean obj) {
        return obj ? (" [" + msg.get("REQUIRED").get() + "]") : (" [" + msg.get("OPTIONAL").get() + "]");
    }

    private void _fillApiPathMethod(NoApiStore store, MCall call, MPath path, List<MdElement> all) {

        String nsummary = NStringUtils.firstNonBlank(call.summary, path.summary);
        String ndescription = NStringUtils.firstNonBlank(call.description, path.description);
        all.add(MdFactory.endParagraph());
        String method = NStringUtils.trim(call.method).toUpperCase();
        String url = NStringUtils.trim(path.url);
        all.add(MdFactory.title(3, method + " " + url));
        all.add(NoApiUtils.asText(nsummary));
        if (!NBlankable.isBlank(nsummary) && !nsummary.endsWith(".")) {
            all.add(NoApiUtils.asText("."));
        }
        all.add(MdFactory.endParagraph());
        all.add(
                MdFactory.codeBacktick3("", "[" + method + "] " + url)
        );
        all.add(MdFactory.endParagraph());
        if (ndescription != null) {
            all.add(NoApiUtils.asText(ndescription));
            if (!NBlankable.isBlank(ndescription) && !ndescription.endsWith(".")) {
                all.add(NoApiUtils.asText("."));
            }
            all.add(MdFactory.endParagraph());
        }
        boolean withRequestHeaderParameters = !call.headerParameters.isEmpty();
        boolean withRequestPathParameters = !call.pathParameters.isEmpty();
        boolean withRequestQueryParameters = !call.queryParameters.isEmpty();
        boolean withRequestBody = (call.requestBody != null);
        if (
                withRequestHeaderParameters
                        || !call.queryParameters.isEmpty()
                        || withRequestPathParameters
                        || (call.requestBody != null)

        ) {
            all.add(MdFactory.endParagraph());
            all.add(MdFactory.title(4, msg.get("REQUEST").get()));

            // paragraph details the expected request parameters and body to be provided by the caller

            if ((
                    (withRequestHeaderParameters ? 1 : 0) +
                            (withRequestQueryParameters ? 1 : 0) +
                            (withRequestPathParameters ? 1 : 0) +
                            (withRequestBody ? 1 : 0)
            ) > 1) {
                all.add(NoApiUtils.asText(msg.get("endpoint.info.1").get()));
            } else if (withRequestHeaderParameters) {
                all.add(NoApiUtils.asText(msg.get("endpoint.info.2").get()));
            } else if (withRequestQueryParameters) {
                all.add(NoApiUtils.asText(msg.get("endpoint.info.3").get()));
            } else if (withRequestPathParameters) {
                all.add(NoApiUtils.asText(msg.get("endpoint.info.4").get()));
            } else if (withRequestBody) {
                all.add(NoApiUtils.asText(msg.get("endpoint.info.5").get()));
            }

            if (withRequestHeaderParameters) {
                all.add(MdFactory.endParagraph());
                all.add(MdFactory.title(5, msg.get("HEADER_PARAMETERS").get()));
                _fillApiPathMethodParam(call.headerParameters, all);
            }
            if (withRequestPathParameters) {
                all.add(MdFactory.endParagraph());
                all.add(MdFactory.title(5, msg.get("PATH_PARAMETERS").get()));
                _fillApiPathMethodParam(call.pathParameters, all);
            }
            if (withRequestQueryParameters) {
                all.add(MdFactory.endParagraph());
                all.add(MdFactory.title(5, msg.get("QUERY_PARAMETERS").get()));
                _fillApiPathMethodParam(call.queryParameters, all);
            }
            if (withRequestBody) {
                boolean required = call.requestBody.required;
                String desc = call.requestBody.description;
                for (MCall.Content item : call.requestBody.contents) {
                    all.add(MdFactory.endParagraph());
                    all.add(MdFactory.title(5, msg.get("REQUEST_BODY").get() + " - " + item.contentType +
                            requiredSuffix(required)));
                    all.add(NoApiUtils.asText(desc));
                    if (!NBlankable.isBlank(desc) && !desc.endsWith(".")) {
                        all.add(MdFactory.text("."));
                    }
                    TypeInfo o = item.type;
                    if (o.getRef() != null) {
//                        all.add(MdFactory.endParagraph());
//                        all.add(MdFactory.title(5, "REQUEST TYPE - " + o.ref));
                        all.add(NoApiUtils.asText(" "));
                        all.add(NoApiUtils.asText(NMsg.ofV(msg.get("requestType.info").get(), NMaps.of("type", o.getRef())).toString()));

                        MdTable tab = new MdTable(
                                new MdColumn[]{
                                        new MdColumn(NoApiUtils.asText(msg.get("NAME").get()), MdHorizontalAlign.LEFT),
                                        new MdColumn(NoApiUtils.asText(msg.get("TYPE").get()), MdHorizontalAlign.LEFT),
                                        new MdColumn(NoApiUtils.asText(msg.get("DESCRIPTION").get()), MdHorizontalAlign.LEFT),
//                                        new MdColumn(NoApiUtils.asText(msg.get("EXAMPLE").get()), MdHorizontalAlign.LEFT)
                                },
                                new MdRow[]{
                                        new MdRow(
                                                new MdElement[]{
                                                        MdFactory.codeBacktick3("", "request-body"),
                                                        MdFactory.codeBacktick3("", o.getRef()),
                                                        NoApiUtils.asTextTrimmed(item.description),
//                                                        jsonTextElementInlined(example),
                                                }, false
                                        )
                                }

                        );
                        all.add(tab);
                        if (item.examples.size() == 1) {
                            all.add(MdFactory.text(msg.get("request.body.example.intro").get()));
                            all.add(MdFactory.text(":\n"));
                            MExample example = item.examples.get(0);
                            if (!NBlankable.isBlank(example.description)) {
                                all.add(NoApiUtils.asTextTrimmed(example.description));
                            }
                            all.add(NoApiUtils.jsonTextElement(example.value));
                        } else if (item.examples.size() > 1) {
                            all.add(MdFactory.text(msg.get("request.body.example.intro.multi").get()));
                            all.add(MdFactory.text(":\n"));
                            int eIndex = 1;
                            for (MExample example : item.examples) {
                                all.add(NoApiUtils.asText("Example " + eIndex));
                                all.add(MdFactory.text(":\n"));
                                if (!NBlankable.isBlank(example.description)) {
                                    all.add(NoApiUtils.asTextTrimmed(example.description));
                                }
                                all.add(NoApiUtils.jsonTextElement(example.value));
                                eIndex++;
                            }
                        }


                    } else {
                        all.add(MdFactory.endParagraph());
                        all.add(NoApiUtils.codeElement(o, true, "", msg));
                    }
                }
            }
        }

        all.add(MdFactory.endParagraph());
        all.add(MdFactory.title(4, msg.get("RESPONSE").get()));
        all.add(NoApiUtils.asText(NMsg.ofV(msg.get("section.response.body").get(), NMaps.of("path", url)).toString()));
        for (MCall.Response response : call.responses) {
            all.add(MdFactory.endParagraph());
            String codeDescription = evalCodeDescription(response.code);
            all.add(MdFactory.title(5, msg.get("STATUS_CODE").get() + " - " + response.code
                    + (NBlankable.isBlank(codeDescription) ? "" : (" - " + codeDescription))
            ));
            String description = response.description;
            all.add(NoApiUtils.asText(description));
            if (!NBlankable.isBlank(description) && !description.endsWith(".")) {
                all.add(MdFactory.text("."));
            }
            for (MCall.Content content : response.contents) {
                all.add(MdFactory.endParagraph());
                if (content.type.getUserType().equals("$ref")) {
                    all.add(MdFactory.table()
                            .addColumns(
                                    MdFactory.column().setName(msg.get("RESPONSE_MODEL").get()),
                                    MdFactory.column().setName(msg.get("RESPONSE_TYPE").get())//,
                            )
                            .addRows(
                                    MdFactory.row().addCells(
                                            NoApiUtils.asText(content.contentType),
                                            NoApiUtils.asText(content.type.getRef())
                                    )
                            ).build()
                    );
                } else {
                    all.add(MdFactory.table()
                            .addColumns(
                                    MdFactory.column().setName(msg.get("RESPONSE_MODEL").get()),
                                    MdFactory.column().setName(msg.get("RESPONSE_TYPE").get())
                            )
                            .addRows(
                                    MdFactory.row().addCells(
                                            NoApiUtils.asText(content.contentType),
                                            NoApiUtils.codeElement(content.type, true, "", msg)
                                            //NoApiUtils.asText(o.getRef())
                                    )
                            ).build()
                    );
                }
                all.add(MdFactory.endParagraph());
                List<MExample> examples = content.type.getExamples().stream().filter(x -> !NBlankable.isBlank(x) && !NBlankable.isBlank(x.value)).distinct().collect(Collectors.toList());
                if (!examples.isEmpty()) {
                    for (int i = 0; i < examples.size(); i++) {
                        MExample example = examples.get(0);
                        if(i==0){
                            all.add(MdFactory.text(msg.get("response.body.example.intro").get()));
                        }else{
                            all.add(MdFactory.text(msg.get("response.body.example.intro.other").get()));
                        }
                        all.add(MdFactory.text(":\n"));
                        all.add(NoApiUtils.jsonTextElement(example.value));
                    }
                }
            }
        }

    }


    private String evalCodeDescription(String s) {
        if (s == null) {
            return "";
        }

        String c = httpCodes.getProperty(s.trim());
        if (c != null) {
            return c;
        }
        return "";
    }

}
