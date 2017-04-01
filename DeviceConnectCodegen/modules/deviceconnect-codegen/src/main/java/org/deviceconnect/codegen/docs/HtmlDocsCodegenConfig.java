package org.deviceconnect.codegen.docs;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.DefaultCodegen;
import io.swagger.codegen.SupportingFile;
import io.swagger.models.*;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.deviceconnect.codegen.DConnectCodegenConfig;
import org.deviceconnect.codegen.models.DConnectOperation;

import java.util.*;

public class HtmlDocsCodegenConfig extends DefaultCodegen implements DConnectCodegenConfig {

    private Map<String, Swagger> profileSpecs;

    @Override
    public String getDefaultDisplayName() {
        return "Device Connect API Specifications";
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.DOCUMENTATION;
    }

    @Override
    public String getName() {
        return "deviceConnectHtmlDocs";
    }

    @Override
    public String getHelp() {
        return "";
    }

    @Override
    public Map<String, Swagger> getProfileSpecs() {
        return this.profileSpecs;
    }

    @Override
    public void setProfileSpecs(final Map<String, Swagger> profileSpecs) {
        this.profileSpecs = profileSpecs;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        List<ProfileDocs> profileHtmlList = new ArrayList<>();
        for (Map.Entry<String, Swagger> specEntry : profileSpecs.entrySet()) {
            final String profileName = specEntry.getKey();
            profileHtmlList.add(new ProfileDocs() {
                public String profileName() { return profileName; }
            });
        }
        Collections.sort(profileHtmlList, new Comparator<ProfileDocs>() {
            @Override
            public int compare(ProfileDocs o1, ProfileDocs o2) {
                return o1.profileName().compareTo(o2.profileName());
            }
        });
        additionalProperties.put("profileHtmlList", profileHtmlList);

        ArrayList<ProfileDocs> swaggerList = new ArrayList<>();
        for (Map.Entry<String, Swagger> specEntry : profileSpecs.entrySet()) {
            final String profileName = specEntry.getKey();
            final Swagger profileSpec = specEntry.getValue();
            String basePath = profileSpec.getBasePath();
            if (basePath == null) {
                basePath = "/gotapi/" + profileName;
            }

            final List<Object> operationList = new ArrayList<>();
            for (Map.Entry<String, Path> pathEntry : profileSpec.getPaths().entrySet()) {
                String pathName = pathEntry.getKey();
                Path path = pathEntry.getValue();
                if ("/".equals(pathName)) {
                    pathName = "";
                }
                final String fullPathName = basePath + pathName;

                for (Map.Entry<HttpMethod, Operation> opEntry : path.getOperationMap().entrySet()) {
                    final String method = opEntry.getKey().name().toUpperCase();
                    final Operation op = opEntry.getValue();
                    final List<Object> paramList = new ArrayList<>();
                    for (final Parameter param : op.getParameters()) {
                        paramList.add(new Object() {
                            String name = param.getName();
                            String type() {
                                String type;
                                String format;
                                if (param instanceof QueryParameter) {
                                    type = ((QueryParameter) param).getType();
                                    format = ((QueryParameter) param).getFormat();
                                } else if (param instanceof FormParameter) {
                                    type = ((FormParameter) param).getType();
                                    format = ((FormParameter) param).getFormat();
                                } else {
                                    return null;
                                }
                                if (format == null) {
                                    return type;
                                }
                                return type + " (" + format + ")";
                            }
                            String required = param.getRequired() ? "Yes" : "No";
                            String description = param.getDescription();
                        });
                    }

                    operationList.add(new Object() {
                        String id() {
                            String id = method + "-" + fullPathName.replaceAll("/", "-");
                            return id.toLowerCase();
                        }
                        String name = method + " " + fullPathName;
                        String type = (String) op.getVendorExtensions().get("x-type");
                        String summary = op.getSummary();
                        String description = op.getDescription();
                        List<Object> paramList() {
                            return paramList;
                        }
                        Object response = createResponseDocument(profileSpec, op);
                        Object event = createEventDocument(profileSpec, op);
                    });
                }
            }

            ProfileDocs swaggerObj = new ProfileDocs() {
                public String profileName() { return profileName; }
                String version = profileSpec.getInfo().getVersion();
                String title = profileSpec.getInfo().getTitle();
                String description = profileSpec.getInfo().getDescription();
                List<Object> operationList() { return operationList; }
            };
            swaggerList.add(swaggerObj);
        }
        Collections.sort(swaggerList, new Comparator<ProfileDocs>() {
            @Override
            public int compare(ProfileDocs o1, ProfileDocs o2) {
                return o1.profileName().compareTo(o2.profileName());
            }
        });
        additionalProperties.put("swaggerList", swaggerList);

        embeddedTemplateDir = templateDir = getName();
        supportingFiles.add(new SupportingFile("index.html.mustache", "", "index.html"));
        supportingFiles.add(new SupportingFile("css/profile.css", "css", "profile.css"));
        supportingFiles.add(new SupportingFile("css/operation-list.css", "css", "operation-list.css"));
        supportingFiles.add(new SupportingFile("html/profile-list.html.mustache", "html", "profile-list.html"));
        supportingFiles.add(new SupportingFile("html/operation-list.html.mustache", "html", "operation-list.html"));
        supportingFiles.add(new SupportingFile("html/all-operations.html.mustache", "html", "all-operations.html"));
    }

    private Object createResponseDocument(final Swagger swagger, final Operation operation) {
        final Map<String, Response> responses = operation.getResponses();
        if (responses != null) {
            Response response = responses.get("200");
            if (response != null) {
                Property schema = response.getSchema();
                return createMessageDocument(swagger, schema);
            }
        }
        return null;
    }

    private Object createEventDocument(final Swagger swagger, final Operation operation) {
        DConnectOperation dConnectOperation = DConnectOperation.parse(swagger, operation);
        if (dConnectOperation == null) {
            return null;
        }
        Response eventModel = dConnectOperation.getEventModel();
        if (eventModel == null) {
            return null;
        }
        Property schema = eventModel.getSchema();
        if (schema == null) {
            return null;
        }
        return createMessageDocument(swagger, schema);
    }

    private Object createMessageDocument(final Swagger swagger, final Property schema) {
        ObjectProperty root;
        if (schema instanceof ObjectProperty) {
            root = (ObjectProperty) schema;
        } else if (schema instanceof RefProperty) {
            RefProperty ref = (RefProperty) schema;
            Model model = findDefinition(swagger, ref.getSimpleRef());
            Map<String, Property> properties;
            if (model instanceof ComposedModel) {
                properties = getProperties(swagger, (ComposedModel) model);
            } else if (model instanceof ModelImpl) {
                properties = model.getProperties();
            } else {
                return null;
            }
            if (properties == null) {
                return null;
            }
            root =  new ObjectProperty();
            root.setProperties(properties);
        } else {
            return null;
        }

        final List<ResponseParamDoc> paramDocList = new ArrayList<>();
        Map<String, Property> props = root.getProperties();
        if (props != null && props.size() > 0) {
            createResponseParameterDocument(root, paramDocList, 1);
        }

        final int maxNestLevel = getMaxNestLevel(paramDocList);
        for (ResponseParamDoc paramDoc : paramDocList) {
            paramDoc.setMaxNestLevel(maxNestLevel);
        }

        return new Object() {
            List<ResponseParamDoc> paramList() { return paramDocList; }

            int maxNestLevel() { return maxNestLevel; }
        };
    }

    private int getMaxNestLevel(final List<ResponseParamDoc> paramDocList) {
        int level = 1;
        for (ResponseParamDoc paramDoc : paramDocList) {
            if (level < paramDoc.nestLevel) {
                level = paramDoc.nestLevel;
            }
        }
        return level;
    }

    private void createResponseParameterDocument(final ObjectProperty root,
                                                 final List<ResponseParamDoc> paramDocList,
                                                 final int nestLevel) {
        Map<String, Property> props = root.getProperties();
        if (props == null) {
            return;
        }
        for (Map.Entry<String, Property> propEntry : props.entrySet()) {
            String propName = propEntry.getKey();
            Property prop = propEntry.getValue();

            String type = prop.getType();
            String format = prop.getFormat();
            String title = prop.getTitle();
            String description = prop.getDescription();
            boolean isRequired = prop.getRequired();
            ResponseParamDoc paramDoc = new ResponseParamDoc(propName, type, format, title, description, isRequired, nestLevel);
            paramDocList.add(paramDoc);

            if ("array".equals(type)) {
                ArrayProperty arrayProp;
                if (!(prop instanceof  ArrayProperty)) {
                    continue;
                }
                arrayProp = (ArrayProperty) prop;
                Property itemsProp = arrayProp.getItems();
                if ("object".equals(itemsProp.getType())) {
                    createResponseParameterDocument((ObjectProperty) itemsProp, paramDocList, nestLevel + 1);
                    continue;
                }
            } else if ("object".equals(type)) {
                createResponseParameterDocument((ObjectProperty) prop, paramDocList, nestLevel + 1);
                continue;
            }
        }
    }

    private Model findDefinition(final Swagger swagger, final String simpleRef) {
        Map<String, Model> definitions = swagger.getDefinitions();
        if (definitions == null) {
            return null;
        }
        return definitions.get(simpleRef);
    }

    private Map<String, Property> getProperties(final Swagger swagger, final ComposedModel parent) {
        Map<String, Property> result = new HashMap<>();
        Stack<ComposedModel> stack = new Stack<>();
        stack.push(parent);
        do {
            ComposedModel model = stack.pop();
            List<Model> children = model.getAllOf();
            for (Model child : children) {
                if (child instanceof ModelImpl) {
                    if (child.getProperties() != null) {
                        result.putAll(child.getProperties());
                    }
                } else if (child instanceof ComposedModel) {
                    stack.push((ComposedModel) child);
                } else if (child instanceof RefModel) {
                    String refName = ((RefModel) child).getSimpleRef();
                    Model m = findDefinition(swagger, refName);
                    if (m == null) {
                        continue;
                    }
                    if (m.getProperties() != null) {
                        result.putAll(m.getProperties());
                    }
                }
            }
        } while (!stack.empty());
        return result;
    }

    private interface ProfileDocs {
        String profileName();
    }

    private class ResponseParamDoc {
        final String name;
        final String type;
        final String format;
        final String title;
        final String description;
        final boolean isRequired;
        final int nestLevel;
        int maxNestLevel;

        ResponseParamDoc(final String name,
                         final String type,
                         final String format,
                         final String title,
                         final String description,
                         final boolean isRequired,
                         final int nestLevel) {
            this.name = name;
            this.type = type;
            this.format = format;
            this.title = title;
            this.description = description;
            this.isRequired = isRequired;
            this.nestLevel = nestLevel;
        }

        String required() {
            return this.isRequired ? "Yes" : "No";
        }

        void setMaxNestLevel(final int max) {
            this.maxNestLevel = max;
        }

        int colSpan() {
            return this.maxNestLevel - this.nestLevel + 1;
        }

        List<Object> indents() {
            List<Object> indents = new ArrayList<>();
            for (int cnt = 0; cnt < this.nestLevel - 1; cnt++) {
                indents.add("");
            }
            return indents;
        }
    }
}
