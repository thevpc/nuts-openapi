package net.thevpc.nuts.toolbox.noapi.model;

import net.thevpc.nuts.toolbox.noapi.NoapiMain;

import java.util.HashMap;
import java.util.Map;

public class NoapiCmdData {
    private String command;
    private String path;
    private String vars;
    private Map<String, String> varsMap = new HashMap<>();
    private String target;
    private boolean openAPI;
    private boolean keep;
    private OpenAPIFormat openAPIFormat;
    private String from;
    private String to;
    private String kind;
    private boolean condensed = true;
    private boolean force;
    private boolean strict;
    private String output;

    public String getCommand() {
        return command;
    }

    public NoapiCmdData setCommand(String command) {
        this.command = command;
        return this;
    }

    public String getPath() {
        return path;
    }

    public NoapiCmdData setPath(String path) {
        this.path = path;
        return this;
    }

    public String getVars() {
        return vars;
    }

    public NoapiCmdData setVars(String vars) {
        this.vars = vars;
        return this;
    }

    public Map<String, String> getVarsMap() {
        return varsMap;
    }

    public NoapiCmdData setVarsMap(Map<String, String> varsMap) {
        this.varsMap = varsMap;
        return this;
    }

    public String getTarget() {
        return target;
    }

    public NoapiCmdData setTarget(String target) {
        this.target = target;
        return this;
    }

    public boolean isOpenAPI() {
        return openAPI;
    }

    public NoapiCmdData setOpenAPI(boolean openAPI) {
        this.openAPI = openAPI;
        return this;
    }

    public boolean isKeep() {
        return keep;
    }

    public NoapiCmdData setKeep(boolean keep) {
        this.keep = keep;
        return this;
    }

    public OpenAPIFormat getOpenAPIFormat() {
        return openAPIFormat;
    }

    public NoapiCmdData setOpenAPIFormat(OpenAPIFormat openAPIFormat) {
        this.openAPIFormat = openAPIFormat;
        return this;
    }

    public String getFrom() {
        return from;
    }

    public NoapiCmdData setFrom(String from) {
        this.from = from;
        return this;
    }

    public String getTo() {
        return to;
    }

    public NoapiCmdData setTo(String to) {
        this.to = to;
        return this;
    }

    public String getKind() {
        return kind;
    }

    public NoapiCmdData setKind(String kind) {
        this.kind = kind;
        return this;
    }

    public boolean isCondensed() {
        return condensed;
    }

    public NoapiCmdData setCondensed(boolean condensed) {
        this.condensed = condensed;
        return this;
    }

    public boolean isForce() {
        return force;
    }

    public NoapiCmdData setForce(boolean force) {
        this.force = force;
        return this;
    }

    public boolean isStrict() {
        return strict;
    }

    public NoapiCmdData setStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    public String getOutput() {
        return output;
    }

    public NoapiCmdData setOutput(String output) {
        this.output = output;
        return this;
    }
}
