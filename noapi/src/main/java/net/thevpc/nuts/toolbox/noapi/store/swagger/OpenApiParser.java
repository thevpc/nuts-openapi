package net.thevpc.nuts.toolbox.noapi.store.swagger;

import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.util.NBlankable;

import java.util.*;
import java.util.stream.Collectors;

public class OpenApiParser {

    public static Vars _fillVars(NoApiStore apiElement, Map<String, String> vars) {
        Map<String, String> all = new LinkedHashMap<>();

        for (MVar c : apiElement.findVariables()) {
            all.put(c.getId(), c.getValue());
        }

        if (vars != null) {
            all.putAll(vars);
        }
        return new Vars(all);
    }

    public static List<MVar> loadConfigVars(MConf confFile, NoApiStore apiElements, Vars vars2) {
        LinkedHashMap<String, MVar> all = new LinkedHashMap<>();
        for (MVar c : apiElements.findConfigVariables()) {
            all.put(c.getId(),
                    new MVar(
                            c.getId(),
                            vars2.format(c.getValue()),
                            vars2.format(c.getObservations()),
                            c.getExamples().toArray(new MExample[0]),
                            vars2.format(c.getValue()),
                            vars2.format(c.getObservations())
                    )
            );
        }
        if (confFile != null) {
            for (MVar v : confFile.variables) {
                MVar f = all.get(v.id);
                if (f == null) {
                    all.put(v.id, f);
                } else {
                    f.setObservations(v.observations);
                    f.setValue(v.value);
                }
            }
        }
        return new ArrayList<>(all.values());
    }

    public TypeInfo parseOneType(NObjectElement value, String name0, Map<String, TypeInfo> allTypes) {
        NObjectElement v = value.asObject().get();
        TypeInfo tt = new TypeInfo();
        tt.setName(v.getString("name").orElse(name0));
        tt.setType(value.getString("type").orNull());
        if (NBlankable.isBlank(tt.getType())) {
            if (value.get("properties").orNull() != null) {
                tt.setType("object");
            } else if (value.get("items").orNull() != null) {
                tt.setType("array");
            } else if (
                    !NBlankable.isBlank(value.getString("$ref").orNull())
                            || !NBlankable.isBlank(value.getStringByPath("schema", "$ref").orNull())
            ) {
                tt.setType("ref");
            } else {
                tt.setType("string");
            }
        }
        tt.setSmartName(tt.getType());
        tt.setDescription(v.getString("description").orNull());
        tt.setSummary(v.getString("summary").orNull());
        tt.getExamples().add(new MExample(null,value.get("example").orNull()));
        if (!NBlankable.isBlank(value.getString("$ref").orNull())) {
            tt.setRefLong(value.getString("$ref").orNull());
            tt.setRef(userNameFromRefValue(tt.getRefLong()));
            tt.setUserType("$ref");
            tt.setSmartName(tt.getRef());
        } else if (!NBlankable.isBlank(value.getStringByPath("schema", "$ref").orNull())) {
            tt.setRefLong(value.getStringByPath("schema", "$ref").orNull());
            tt.setRef(userNameFromRefValue(tt.getRefLong()));
            tt.setUserType("$ref");
            tt.setSmartName(tt.getRef());
        } else if ("array".equals(tt.getType())) {
            NObjectElement items = v.getObject("items").orNull();
            if (items == null) {
                TypeInfo a = new TypeInfo();
                a.setType("string");
                a.setSmartName(a.getType());
                tt.setArrayComponentType(a);
                TypeInfo refType = allTypes.get(a.getSmartName());
                tt.setSmartName(a.getSmartName() + "[]");
                Object e = a.getExamples();
                if (e == null && refType != null) {
                    e = refType.getExamples();
                }
                if (e != null) {
                    if (e instanceof NElement) {
                        tt.getExamples().add(new MExample(null,NElements.of().ofArray((NElement) e).build()));
                    } else {
                        tt.getExamples().add(new MExample(null,Arrays.asList(e)));
                    }
                }
            } else {
                TypeInfo a = parseOneType(items, null, allTypes);
                tt.setArrayComponentType(a);
                tt.setSmartName(a.getSmartName() + "[]");
                TypeInfo refType = allTypes.get(a.getSmartName());
                Object e = a.getExamples();
                if (e == null && refType != null) {
                    e = refType.getExamples();
                }
                if (e != null) {
                    if (e instanceof NElement) {
                        tt.getExamples().add(new MExample(null,NElements.of().ofArray().add((NElement) e).build()));
                    } else {
                        tt.getExamples().add(new MExample(null,Arrays.asList(e)));
                    }
                }
            }
            tt.setUserType(tt.getSmartName());
        } else if (value.get("properties").orNull() != null || "object".equals(tt.getType())) {
            Set<String> requiredSet = new HashSet<>();
            NArrayElement requiredElem = v.getArray("required").orNull();
            if (requiredElem != null) {
                for (NElement e : requiredElem) {
                    String a = e.asString().orElse("");
                    if (!NBlankable.isBlank(a)) {
                        a = a.trim();
                        requiredSet.add(a);
                    }
                }
            }
            NObjectElement a = v.getObject("properties").orNull();
            if (a != null) {
                for (NPairElement p : a.pairs().collect(Collectors.toList())) {
                    FieldInfo ff = new FieldInfo();
                    ff.name = p.key().asString().orElse("").trim();
                    NObjectElement prop = p.value().asObject().get();
                    ff.description = prop.getString("description").orNull();
                    ff.summary = prop.getString("summary").orNull();
                    NElement example = prop.get("example").orNull();
                    if(example!=null) {
                        ff.examples.add(new MExample(null,example));
                    }
                    ff.required = requiredSet.contains(ff.name);
                    ff.schema = parseOneType(prop, null, allTypes);
                    tt.getFields().add(ff);
                }
                return tt;
            }
        } else {
            tt.setFormat(value.getString("format").orNull());
            tt.setMinLength(value.getString("minLength").orNull());
            tt.setMaxLength(value.getString("maxLength").orNull());
            tt.setRefLong(value.getString("$ref").orNull());
            if (!NBlankable.isBlank(tt.getRefLong())) {
                tt.setRef(userNameFromRefValue(tt.getRefLong()));
            }
            if ("date".equals(tt.getFormat()) || "date-time".equals(tt.getFormat())) {
                tt.setUserType(tt.getFormat());
            } else if (!NBlankable.isBlank(tt.getRefLong())) {
                tt.setUserType(tt.getRef());
            } else if (NBlankable.isBlank(tt.getType())) {
                tt.setUserType("string");
            } else {
                tt.setUserType(tt.getType().trim().toLowerCase());
            }
            NArrayElement senum = value.getArray("enum").orElse(NArrayElement.ofEmpty());
            if (!senum.isEmpty()) {
                tt.setEnumValues(new ArrayList<>());
                if ("string".equals(tt.getUserType())) {
                    tt.setUserType("enum");
                }
                for (NElement ee : senum) {
                    tt.getEnumValues().add(ee.asString().get());
                }
            }
        }
        return tt;
    }

    public Map<String, TypeInfo> parseTypes(NObjectElement root) {

        Map<String, TypeInfo> res = new LinkedHashMap<>();
        NObjectElement schemas = root.getObjectByPath("components", "schemas").orNull();
        if (schemas == null || schemas.isEmpty()) {
            return res;
        }
        for (NPairElement entry : schemas.pairs().collect(Collectors.toList())) {
            String name0 = entry.key().asString().get();
            NElement value = entry.value();
            TypeInfo a = parseOneType(value.asObject().get(), name0, res);
            if (a != null) {
                res.put(name0, a);
            }
        }
        return res;
    }


    public String userNameFromRefValue(String dRef) {
        if (dRef != null) {
            if (dRef.startsWith("#/components/schemas/")) {
                return dRef.substring("#/components/schemas/".length());
            }
        }
        return dRef;
    }
}
