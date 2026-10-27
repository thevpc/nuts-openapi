package net.thevpc.nuts.toolbox.noapi;

import net.thevpc.nuts.app.NAppComplete;
import net.thevpc.nuts.app.NApplication;
import net.thevpc.nuts.app.NApp;
import net.thevpc.nuts.app.NAppRun;
import net.thevpc.nuts.cmdline.NCmdLine;
import net.thevpc.nuts.cmdline.NArg;
import net.thevpc.nuts.core.NSession;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.convert.NoApiFileKind;
import net.thevpc.nuts.toolbox.noapi.convert.NoApiFormat;
import net.thevpc.nuts.toolbox.noapi.convert.NoapiConvertCommand;
import net.thevpc.nuts.toolbox.noapi.model.NoapiCmdData;
import net.thevpc.nuts.toolbox.noapi.model.OpenAPIFormat;
import net.thevpc.nuts.toolbox.noapi.service.NOpenAPIService;
import net.thevpc.nuts.util.NAssert;
import net.thevpc.nuts.text.NMsg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@NApp
public class NoapiMain {

    private NOpenAPIService service;
    private final NoapiCmdData ref = new NoapiCmdData();

    private final List<NoapiCmdData> data = new ArrayList<>();
    private boolean convertTokenSeen = false;

    public static void main(String[] args) {
        NApplication.builder(args).run();
    }

    private NCmdLine parseCmdLine() {
        convertTokenSeen = false;
        NCmdLine cmdLine = NApplication.of().cmdLine();
        cmdLine
                .matcher()
                .when("--yaml").asTrueFlag(a -> {
                    ref.setOpenAPIFormat(OpenAPIFormat.YAML);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setOpenAPIFormat(OpenAPIFormat.YAML);
                    }
                })
                .when("--json").asTrueFlag(a -> {
                    ref.setOpenAPIFormat(OpenAPIFormat.JSON);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setOpenAPIFormat(OpenAPIFormat.JSON);
                    }
                })
                .when("--tson").asTrueFlag(a -> {
                    ref.setOpenAPIFormat(OpenAPIFormat.TSON);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setOpenAPIFormat(OpenAPIFormat.TSON);
                    }
                })
                .when("--keep").asFlag(a -> {
                    ref.setKeep(a.booleanValue());
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setKeep(a.booleanValue());
                    }
                })
                .when("--vars").asEntry(a -> {
                    String vars = a.getStringValue().get();
                    ref.setVars(vars);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setVars(vars);
                    }
                })
                .when("--var").asEntry(a -> {
                    String vars = a.getStringValue().get();
                    NArg b = NArg.of(vars);
                    ref.getVarsMap().put(b.getKey().toStringLiteral(), b.literalValue().toStringLiteral());
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).getVarsMap().put(b.getKey().toStringLiteral(), b.literalValue().toStringLiteral());
                    }
                })
                .when("--open-api").asFlag(a -> {
                    ref.setOpenAPI(a.booleanValue());
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setOpenAPI(a.booleanValue());
                    }
                })
                .when("--pdf").asTrueFlag(a -> {
                    ref.setCommand("pdf");
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setCommand("pdf");
                    }
                })
                .when("--target").asEntry(a -> {
                    String target = a.getStringValue().get();
                    if (target.contains("*")) {
                        ref.setTarget(target);
                    }
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setTarget(target);
                    }
                })
                .when("--convert").asTrueFlag(a -> {
                    ref.setCommand("convert");
                })
                .when("--from").asEntry(a -> {
                    String v = a.getStringValue().get();
                    ref.setFrom(v);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setFrom(v);
                    }
                })
                .when("--to").asEntry(a -> {
                    String v = a.getStringValue().get();
                    ref.setTo(v);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setTo(v);
                    }
                })
                .when("--kind").asEntry(a -> {
                    String v = a.getStringValue().get();
                    ref.setKind(v);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setKind(v);
                    }
                })
                .when("--style").asEntry(a -> {
                    boolean condensed = !"plain".equalsIgnoreCase(a.getStringValue().get());
                    ref.setCondensed(condensed);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setCondensed(condensed);
                    }
                })
                .when("--force").asFlag(a -> {
                    boolean f = a.booleanValue();
                    ref.setForce(f);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setForce(f);
                    }
                })
                .when("--strict").asFlag(a -> {
                    boolean f = a.booleanValue();
                    ref.setStrict(f);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setStrict(f);
                    }
                })
                .when("--output", "-o").asEntry(a -> {
                    String v = a.getStringValue().get();
                    ref.setOutput(v);
                    if (!data.isEmpty()) {
                        data.get(data.size() - 1).setOutput(v);
                    }
                })
                .whenNonOption().asArg(a -> {
                    if (!convertTokenSeen && data.isEmpty() && "convert".equals(a.image())) {
                        convertTokenSeen = true;
                        ref.setCommand("convert");
                        return;
                    }
                    NoapiCmdData c = new NoapiCmdData();
                    c.setCommand(ref.getCommand());
                    c.setKeep(ref.isKeep());
                    c.setOpenAPI(ref.isOpenAPI());
                    c.setTarget(ref.getTarget());
                    c.setVars(ref.getVars());
                    c.setVarsMap(new HashMap<>(ref.getVarsMap()));
                    c.setOpenAPIFormat(ref.getOpenAPIFormat());
                    c.setFrom(ref.getFrom());
                    c.setTo(ref.getTo());
                    c.setKind(ref.getKind());
                    c.setCondensed(ref.isCondensed());
                    c.setForce(ref.isForce());
                    c.setStrict(ref.isStrict());
                    c.setOutput(ref.getOutput());
                    c.setPath(a.image());
                    data.add(c);
                })
                .requireAll();
        return cmdLine;
    }

    @NAppComplete
    public void complete() {
        parseCmdLine().printCompleteResult();
    }

    @NAppRun
    public void run() {
        NSession session = NSession.of();
        this.service = new NOpenAPIService(session);
        ref.setCommand("pdf");
        NCmdLine cmdLine = parseCmdLine();

        if (data.isEmpty()) {
            NoapiCmdData c = new NoapiCmdData();
            c.setCommand(ref.getCommand());
            c.setKeep(ref.isKeep());
            c.setOpenAPI(ref.isOpenAPI());
            c.setTarget(ref.getTarget());
            c.setVars(ref.getVars());
            c.setVarsMap(new HashMap<>(ref.getVarsMap()));
            c.setStrict(ref.isStrict());
            c.setPath(NPath.ofUserDirectory().toString());
            data.add(c);
        }
        for (NoapiCmdData d : data) {
            NAssert.requireNamedNonBlank(d.getPath(), "path");
            if (!"pdf".equals(d.getCommand()) && !"convert".equals(d.getCommand())) {
                cmdLine.throwUnexpectedArgument(NMsg.ofC("unsupported command %s", d.getCommand()));
            }
        }

        for (NoapiCmdData d : data) {
            switch (d.getCommand()) {
                case "pdf": {
                    NOpenAPIService service = new NOpenAPIService(session);
                    service.run(d.getPath(), d.getTarget(), d.getVars(), d.getVarsMap(), d.isKeep(), d.getOpenAPIFormat(), d.isStrict());
                    break;
                }
                case "convert": {
                    NoapiConvertCommand.Options o = new NoapiConvertCommand.Options()
                            .setInput(d.getPath())
                            .setOutput(d.getOutput())
                            .setForce(d.isForce())
                            .setStrict(d.isStrict())
                            .setCondensed(d.isCondensed());
                    if (d.getFrom() != null) {
                        o.setFrom(NoApiFormat.parse(d.getFrom()).orNull());
                    }
                    if (d.getTo() != null) {
                        NoApiFormat to = NoApiFormat.parse(d.getTo()).orNull();
                        if (to != null) {
                            o.setTo(to);
                        }
                    } else if (d.getOpenAPIFormat() != null) {
                        o.setTo(NoApiFormat.of(d.getOpenAPIFormat()));
                    }
                    if (d.getKind() != null) {
                        NoApiFileKind k = NoApiFileKind.parse(d.getKind()).orNull();
                        if (k != null) {
                            o.setKind(k);
                        }
                    }
                    NoapiConvertCommand.Result r = NoapiConvertCommand.run(o);
                    session.out().println(NMsg.ofC("converted %s -> %s%s",
                            d.getPath(), r.output(),
                            r.condensed() ? " (condensed)" : ""));
                    if (r.note() != null) {
                        session.out().println(NMsg.ofC("note: %s", r.note()));
                    }
                    break;
                }
            }
        }
    }


}
