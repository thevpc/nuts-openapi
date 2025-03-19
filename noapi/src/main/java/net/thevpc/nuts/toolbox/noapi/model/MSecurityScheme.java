package net.thevpc.nuts.toolbox.noapi.model;


public class MSecurityScheme {

    public String id;
    public String typeName;
    public Type type=Type.other;
    public String name;
    public String description;
    public String in;
    public String openIdConnectUrl;
    public String scheme;
    public String bearerFormat;
    public enum Type{
        apiKey,
        http,
        oauth2,
        openIdConnect,
        other,
    }
}
