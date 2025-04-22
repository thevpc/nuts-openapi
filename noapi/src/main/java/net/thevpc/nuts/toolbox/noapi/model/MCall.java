package net.thevpc.nuts.toolbox.noapi.model;

import java.util.ArrayList;
import java.util.List;

public class MCall {
    public String method;
    public String summary;
    public String description;
    public List<MParam> headerParameters;
    public List<MParam> queryParameters;
    public List<MParam> pathParameters;
    public RequestBody requestBody;
    public List<Response> responses;

    public static class Response {
        public String code;
        public String description;
        public List<Content> contents;
    }
    public static class Content {
        public String contentType;
        public TypeInfo type;
        public String typeName;
        public List<MExample> examples=new ArrayList<>();
        public String description;
    }
    public static class RequestBody{
        public boolean required;
        public String description;
        public List<Content> contents;
    }
}
