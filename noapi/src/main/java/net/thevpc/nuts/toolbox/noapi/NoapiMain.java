package net.thevpc.nuts.toolbox.noapi;

import net.thevpc.nuts.*;
import net.thevpc.nuts.cmdline.NCmdLine;
import net.thevpc.nuts.cmdline.NCmdLineRunner;
import net.thevpc.nuts.cmdline.NArg;
import net.thevpc.nuts.io.NPath;
import net.thevpc.nuts.toolbox.noapi.model.NoapiCmdData;
import net.thevpc.nuts.toolbox.noapi.service.NOpenAPIService;
import net.thevpc.nuts.util.NAssert;
import net.thevpc.nuts.util.NMsg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@NAppDefinition
public class NoapiMain  {

    private NOpenAPIService service;
    private NoapiCmdData ref = new NoapiCmdData();

    private List<NoapiCmdData> data = new ArrayList<>();

    public static void main(String[] args) {
        NApp.builder(args).run();
    }

    @NAppRunner
    public void run() {
        NSession session = NSession.of();
        this.service = new NOpenAPIService(session);
        ref.setCommand("pdf");
        NApp.of().runCmdLine(new NCmdLineRunner() {
            @Override
            public boolean next(NArg arg, NCmdLine cmdLine) {
                if(arg.isOption()){
                    switch (arg.asString().get()) {
                        case "--yaml": {
                            cmdLine.nextFlag();
                            ref.setOpenAPIFormat("yaml");
                            if (!data.isEmpty()) {
                                data.get(data.size() - 1).setOpenAPIFormat("yaml");
                            }
                            return true;
                        }
                        case "--json": {
                            cmdLine.nextFlag();
                            ref.setOpenAPIFormat("json");
                            if (!data.isEmpty()) {
                                data.get(data.size() - 1).setOpenAPIFormat("json");
                            }
                            return true;
                        }
                        case "--keep": {
                            cmdLine.nextFlag();
                            ref.setKeep(true);
                            if (!data.isEmpty()) {
                                data.get(data.size() - 1).setKeep(true);
                            }
                            return true;
                        }
                        case "--vars": {
                            NArg a = cmdLine.nextEntry().get();
                            if (a.isUncommented()) {
                                String vars = a.getStringValue().get();
                                ref.setVars(vars);
                                if (!data.isEmpty()) {
                                    data.get(data.size() - 1).setVars(vars);
                                }
                            }
                            return true;
                        }
                        case "--var": {
                            NArg a = cmdLine.nextEntry().get();
                            if (a.isUncommented()) {
                                String vars = a.getStringValue().get();
                                NArg b = NArg.of(vars);
                                if (b.isUncommented()) {
                                    ref.getVarsMap().put(b.getKey().toStringLiteral(), b.getValue().toStringLiteral());
                                    if (!data.isEmpty()) {
                                        data.get(data.size() - 1).getVarsMap().put(b.getKey().toStringLiteral(), b.getValue().toStringLiteral());
                                    }
                                }
                            }
                            return true;
                        }
                        case "--open-api": {
                            cmdLine.nextFlag();
                            ref.setOpenAPI(true);
                            if (!data.isEmpty()) {
                                data.get(data.size() - 1).setOpenAPI(true);
                            }
                            return true;
                        }
                        case "--pdf": {
                            cmdLine.nextFlag();
                            ref.setCommand("pdf");
                            if (!data.isEmpty()) {
                                data.get(data.size() - 1).setCommand("pdf");
                            }
                            return true;
                        }
                        case "--target": {
                            NArg a = cmdLine.nextEntry().get();
                            if (a.isUncommented()) {
                                String target = a.getStringValue().get();
                                if (target.contains("*")) {
                                    ref.setTarget(target);
                                }
                                if (!data.isEmpty()) {
                                    data.get(data.size() - 1).setTarget(target);
                                }
                            }
                            return true;
                        }
                    }
                    return false;
                }else{
                    NoapiCmdData c = new NoapiCmdData();
                    c.setCommand(ref.getCommand());
                    c.setKeep(ref.isKeep());
                    c.setOpenAPI(ref.isOpenAPI());
                    c.setTarget(ref.getTarget());
                    c.setVars(ref.getVars());
                    c.setVarsMap(new HashMap<>(ref.getVarsMap()));
                    NArg pathArg = cmdLine.next().get();
                    c.setPath(pathArg.key());
                    data.add(c);
                    return true;
                }
            }

            @Override
            public void validate(NCmdLine cmdLine) {
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
                    NAssert.requireNonBlank(d.getPath(), "path");
                    if (!"pdf".equals(d.getCommand())) {
                        throw new NIllegalArgumentException(NMsg.ofC("unsupported command %s", d.getCommand()));
                    }
                }
            }

            @Override
            public void run(NCmdLine cmdLine) {

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

        });
    }


}
