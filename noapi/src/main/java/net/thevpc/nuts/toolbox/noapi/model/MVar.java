package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MVar {
    public String id;
    public String name;
    public String description;
    public List<MExample> examples=new ArrayList<>();
    public String value;
    public String observations;

    public MVar() {
    }

    public MVar(String id, String name, String description, MExample[] examples, String value, String observations) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.examples.addAll(Arrays.asList(examples));
        this.value = value;
        this.observations = observations;
    }

    public String getId() {
        return id;
    }

    public MVar setId(String id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public MVar setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public MVar setDescription(String description) {
        this.description = description;
        return this;
    }

    public List<MExample> getExamples() {
        return examples;
    }

    public MVar setExample(List<MExample> examples) {
        this.examples.clear();
        this.examples.addAll(examples);
        return this;
    }

    public String getValue() {
        return value;
    }

    public MVar setValue(String value) {
        this.value = value;
        return this;
    }

    public String getObservations() {
        return observations;
    }

    public MVar setObservations(String observations) {
        this.observations = observations;
        return this;
    }
}
