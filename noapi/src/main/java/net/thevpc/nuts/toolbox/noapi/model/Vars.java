package net.thevpc.nuts.toolbox.noapi.model;

import net.thevpc.nuts.util.NMsg;
import net.thevpc.nuts.util.NStringUtils;
import net.thevpc.tson.TsonElement;

import java.util.Map;

public class Vars {
    private Map<String, String> m;

    public Vars(Map<String, String> m) {
        this.m = m;
    }

    public void putDefault(String a,String b) {
        if(!m.containsKey(a)){
            m.put(a,b);
        }
    }

    public Map<String, String> toMap() {
        return m;
    }

    public String formatTrimmed(String a) {
        if(a==null){
            return "";
        }
        return NStringUtils.trim(NMsg.ofV(a, s -> m.get(s)).toString());
    }

    public String formatObject(Object a) {
        if(a==null){
            return "";
        }
        if(a instanceof TsonElement){
            if(((TsonElement) a).isAnyString()){
                return format(((TsonElement) a).toStr().value());
            }
            return format(((TsonElement) a).toString());
        }
        return format(a.toString()).trim();
    }

    public String format(String a) {
        if(a==null){
            return "";
        }
        return NMsg.ofV(a, s -> m.get(s)).toString();
    }
}
