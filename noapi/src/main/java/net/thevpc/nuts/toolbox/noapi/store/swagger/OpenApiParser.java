package net.thevpc.nuts.toolbox.noapi.store.swagger;

import net.thevpc.nuts.elem.*;
import net.thevpc.nuts.toolbox.noapi.model.*;
import net.thevpc.nuts.toolbox.noapi.store.NoApiStore;
import net.thevpc.nuts.util.NBlankable;
import net.thevpc.nuts.util.NLiteral;

import java.util.*;

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
        tt.setName(v.getStringValue("name").orElse(name0));
        tt.setType(value.getStringValue("type").orNull());
        if (NBlankable.isBlank(tt.getType())) {
            if (value.get("properties").orNull() != null) {
                tt.setType("object");
            } else if (value.get("items").orNull() != null) {
                tt.setType("array");
            } else if (
                    !NBlankable.isBlank(value.getStringValue("$ref").orNull())
                            || !NBlankable.isBlank(value.getByPath("schema", "$ref").map(NElement::asLiteral).flatMap(NLiteral::asString).orNull())
            ) {
                tt.setType("ref");
            } else {
                tt.setType("string");
            }
        }
        tt.setSmartName(tt.getType());
        tt.setDescription(v.getStringValue("description").orNull());
        tt.setSummary(v.getStringValue("summary").orNull());
        tt.getExamples().add(new MExample(null,value.get("example").orNull()));
        if (!NBlankable.isBlank(value.getStringValue("$ref").orNull())) {
            tt.setRefLong(value.getStringValue("$ref").orNull());
            tt.setRef(userNameFromRefValue(tt.getRefLong()));
            tt.setUserType("$ref");
            tt.setSmartName(tt.getRef());
        } else if (!NBlankable.isBlank(value.getByPath("schema", "$ref").map(NElement::asLiteral).flatMap(NLiteral::asString).orNull())) {
            tt.setRefLong(value.getByPath("schema", "$ref").map(NElement::asLiteral).flatMap(NLiteral::asString).orNull());
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
                        tt.getExamples().add(new MExample(null,NElements.of().ofArray((NElement) e)));
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
                        tt.getExamples().add(new MExample(null,NElements.of().ofArrayBuilder().add((NElement) e).build()));
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
                    String a = e.asStringValue().orElse("");
                    if (!NBlankable.isBlank(a)) {
                        a = a.trim();
                        requiredSet.add(a);
                    }
                }
            }
            NObjectElement a = v.getObject("properties").orNull();
            if (a != null) {
                for (NPairElement p : a.pairs()) {
                    FieldInfo ff = new FieldInfo();
                    ff.name = p.key().asStringValue().orElse("").trim();
                    NObjectElement prop = p.value().asObject().get();
                    ff.description = prop.getStringValue("description").orNull();
                    ff.summary = prop.getStringValue("summary").orNull();
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
            tt.setFormat(value.getStringValue("format").orNull());
            tt.setMinLength(value.getStringValue("minLength").orNull());
            tt.setMaxLength(value.getStringValue("maxLength").orNull());
            tt.setRefLong(value.getStringValue("$ref").orNull());
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
                    tt.getEnumValues().add(ee.asStringValue().get());
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
        for (NPairElement entry : schemas.pairs()) {
            String name0 = entry.key().asStringValue().get();
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
