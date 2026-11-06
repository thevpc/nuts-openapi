package net.thevpc.nuts.toolbox.noapi.model;

import java.util.Objects;

public class TypeCrossRef {
    private String url;
    private String location;
    private String type;
    private String method;

    public TypeCrossRef(String type, String url, String location) {
        this(type, null, url, location);
    }

    public TypeCrossRef(String type, String method, String url, String location) {
        this.setUrl(url);
        this.setMethod(method);
        this.setLocation(location);
        this.setType(type);
    }

    public String getUrl() {
        return url;
    }

    public TypeCrossRef setUrl(String url) {
        this.url = url;
        return this;
    }

    /**
     * The HTTP method of the operation that uses the type; {@code null} for global references.
     */
    public String getMethod() {
        return method;
    }

    public TypeCrossRef setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getLocation() {
        return location;
    }

    public TypeCrossRef setLocation(String location) {
        this.location = location;
        return this;
    }

    public String getType() {
        return type;
    }

    public TypeCrossRef setType(String type) {
        this.type = type;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        TypeCrossRef that = (TypeCrossRef) o;
        return Objects.equals(url, that.url)
                && Objects.equals(method, that.method)
                && Objects.equals(location, that.location)
                && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, location, type);
    }
}
