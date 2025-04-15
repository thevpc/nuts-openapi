package net.thevpc.nuts.toolbox.noapi.model;

import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.util.NMsg;
import net.thevpc.nuts.util.NStringUtils;

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
        if(a instanceof NElement){
            if(((NElement) a).isAnyString()){
                return format(((NElement) a).asString().get());
            }
            return format(((NElement) a).toString());
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
