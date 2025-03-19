package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.List;

public class MChangeLog {
    public String date;
    public String version;
    public String observations;
    public String title;
    public List<String> details=new ArrayList<>();

    public MChangeLog() {
    }

    public MChangeLog(String date, String version, String title, String observations) {
        this.date = date;
        this.version = version;
        this.title = title;
        this.observations = observations;
    }
}
