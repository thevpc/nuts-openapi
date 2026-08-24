package net.thevpc.nuts.toolbox.noapi;

import net.thevpc.nuts.app.NAppComplete;
import net.thevpc.nuts.app.NApplication;
import net.thevpc.nuts.app.NApp;
import net.thevpc.nuts.app.NAppRun;
import net.thevpc.nuts.cmdline.NCmdLine;
import net.thevpc.nuts.cmdline.NArg;
import net.thevpc.nuts.core.NSession;
import net.thevpc.nuts.io.NPath;
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

    public static void main(String[] args) {
        NApplication.builder(args).run();
    }

    private NCmdLine parseCmdLine() {
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
                .whenNonOption().asArg(a -> {
                    NoapiCmdData c = new NoapiCmdData();
                    c.setCommand(ref.getCommand());
                    c.setKeep(ref.isKeep());
                    c.setOpenAPI(ref.isOpenAPI());
                    c.setTarget(ref.getTarget());
                    c.setVars(ref.getVars());
                    c.setVarsMap(new HashMap<>(ref.getVarsMap()));
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
            c.setPath(NPath.ofUserDirectory().toString());
            data.add(c);
        }
        for (NoapiCmdData d : data) {
            NAssert.requireNamedNonBlank(d.getPath(), "path");
            if (!"pdf".equals(d.getCommand())) {
                cmdLine.throwUnexpectedArgument(NMsg.ofC("unsupported command %s", d.getCommand()));
            }
        }

        for (NoapiCmdData d : data) {
            switch (d.getCommand()) {
                case "pdf": {
                    NOpenAPIService service = new NOpenAPIService(session);
                    service.run(d.getPath(), d.getTarget(), d.getVars(), d.getVarsMap(), d.isKeep());
                    break;
                }
            }
        }
    }


}
