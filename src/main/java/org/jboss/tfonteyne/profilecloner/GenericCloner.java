/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.tfonteyne.profilecloner;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Clones any "/name=value" root whether in standalone or in domain and delivers the CLI statement as a batch
 *
 * @author Tom Fonteyne
 */
public class GenericCloner implements Cloner {

    protected ModelControllerClient client = null;
    protected String elementName = null;
    protected String destinationName = null;

    protected AddressStack destinationAddress = null;
    protected AddressStack sourceAddress = null;

    protected boolean addDeployments;

    private int managementMajorVersion = 0;
    private static final String VERSION_MAJOR = "management-major-version";
    private static final String VERSION_MICRO = "management-micro-version";
    private static final String VERSION_MINOR = "management-minor-version";

    // mostly for reference, used for compatibility exceptions
    private static final int EAP6 = 1;
    private static final int WILDFLY8 = 2;
    private static final int WILDFLY9 = 3;
    private static final int WILDFLY10 = 4; // == EAP 7

    /**
     *
     * @param client
     * @param source a CLI style address, example: "/profile=default" or "/host=master/core-service=management"
     * @param destinationName the new name of the last part of the source address, example "new-default" or "management" (e.g. use same name)
     * @param addDeployments
     * @throws IOException
     * @throws CommandLineException
     */
    public GenericCloner(ModelControllerClient client, String source, String destinationName, boolean addDeployments)
        throws IOException, CommandLineException {
        this.client = client;
        this.destinationName = destinationName;
        this.addDeployments = addDeployments;

        int pos = source.lastIndexOf("/");
        this.elementName = source.substring(pos+1,source.length()-1).split("=")[0];

        sourceAddress = new AddressStack(source);
        destinationAddress = new AddressStack(source.substring(0, pos) + "/" + elementName + "=" + destinationName);

        managementMajorVersion = this.getManagementVersion(client, VERSION_MAJOR);
    }

    @Override
    public List<String> copy() throws IOException, CommandLineException {
        List<String> commands = new LinkedList<>();
        commands.add("batch");
        commands.addAll(getChildResource(elementName, getSource(sourceAddress)));
        commands.add("run-batch");
        return commands;
    }


    private int getManagementVersion(ModelControllerClient client, String version) throws IOException {
        ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        node.get("name").set(version);
        ModelNode result = client.execute(node);
        if ("failed".equals(result.get(ClientConstants.OUTCOME).asString())) {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get(ClientConstants.RESULT).asInt();
    }

    protected ModelNode getSource(AddressStack source)
        throws IOException, CommandLineException {
        ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        source.setAddress(node);
        node.get("recursive").set(true);
        node.get("include-defaults").set(false);

        ModelNode result = client.execute(node);
        if ("failed".equals(result.get("outcome").asString())) {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get("result");
    }

    /**
     * Gets called recursively
     * Note the special handling of certain nodes due to inconsistencies
     *
     * @param elementName
     * @param source
     * @return
     */
    protected List<String> getChildResource(String elementName, ModelNode source) {
        List<String> commands = new LinkedList<>();
        StringBuilder attributes;
        String addressString;

        if (isProperty(source)) {
            String addressName = source.asProperty().getName();
            destinationAddress.push(new Address(elementName, addressName));
            addressString = destinationAddress.toString();
            attributes = handleProperty(source.asProperty().getValue(), commands);

            if (managementMajorVersion < WILDFLY9) {
                // pre WildFly 9 required special handling of the JGroups protocols which were set
                // using "add-protocol" instead of the normal "add"
                if ((addressString.matches(".*/subsystem=\"jgroups\"/stack=.*/protocol=.*"))
                    // but do not do this for child resources
                    && (!addressString.matches(".*/subsystem=\"jgroups\"/stack=.*/protocol=.*/.*=.*"))
                    ) {
                    destinationAddress.pop();
                    commands.add(0, buildAdd("add-protocol", attributes));
                    return commands;
                }
            }

            // remove deployments if not allowed
            if (!addDeployments && addressString.matches("/server-group=\".*\"/deployment.*=.*")) {
                destinationAddress.pop();
                return commands;
            }

            // Messaging has a concept "runtime-queue" which shows up even when asked include-runtime=false
            if (addressString.matches(".*/subsystem=\"messaging\"/hornetq-server=.*/runtime-queue=.*")) {
                destinationAddress.pop();
                return commands;
            }

            // JGroups has protocols as read-only, but I found no way to avoid fetching this.
            if (addressString.matches(".*/subsystem=\"jgroups\"/stack=.*")
                && attributes.toString().contains("protocols=[")) {
                attributes = new StringBuilder(attributes.toString().replaceAll("protocols=\\[.*\\],", ""));
            }

            // deprecated but still present -> remove the attribute before adding
            if (addressString.matches(".*/subsystem=\"security\"/security-domain=.*/.*=\"classic\"")
                && (attributes.toString().contains("login-modules=[")
                || attributes.toString().contains("policy-modules=[")
                || attributes.toString().contains("provider-modules=[")
                || attributes.toString().contains("trust-modules=[")
                || attributes.toString().contains("mapping-modules=["))) {
                attributes = new StringBuilder(attributes.toString()
                    .replaceAll("login-modules=\\[.*\\],", "")
                    .replaceAll("policy-modules=\\[.*\\],", "")
                    .replaceAll("provider-modules=\\[.*\\],", "")
                    .replaceAll("trust-modules=\\[.*\\],", "")
                    .replaceAll("mapping-modules=\\[.*\\],", "")
                );
            }

            // JASPI has 2 similar issues as "classic"
            if (addressString.matches(".*/subsystem=\"security\"/security-domain=.*/.*=\"jaspi\"")
                && (attributes.toString().contains("auth-modules=["))) {
                attributes = new StringBuilder(attributes.toString()
                    .replaceAll("auth-modules=\\[.*\\],", "")
                );
            }

            // JASPI has 2 similar issues as "classic"
            if (addressString.matches(".*/subsystem=\"security\"/security-domain=.*/.*=\"jaspi\"/login-module-stack=.*")
                && (attributes.toString().contains("login-modules=["))) {
                attributes = new StringBuilder(attributes.toString()
                    .replaceAll("login-modules=\\[.*\\],", "")
                );
            }

            commands.add(0, buildAdd("add", attributes));
            destinationAddress.pop();
        } else {
            addressString = destinationAddress.toString();
            attributes = handleProperty(source, commands);

            // the profile comes back with a "name" attribute which is not allowed in "add"
            if ((addressString.equals("/profile=\"" + destinationName + "\""))) {
                attributes = new StringBuilder(attributes.toString().replaceAll("name=.*,", ""));
            }

            commands.add(0,buildAdd("add", attributes));
        }
        return commands;
     }

    protected String buildAdd(String command, StringBuilder attributes) {
        StringBuilder cmd = destinationAddress.toStringBuilder().append(":").append(command).append("(").append(attributes);
        return removeComma(cmd).append(")").toString();
    }

    /**
     * The bulk of the work is done in here - it will recursively call getChildResource
     *
     * There are potentially to many checks on undefined, but heck.. lets be safe
     *
     * @param root
     * @param commands
     * @return a list of attributes: name1="val1",name2="val2",...
     */
    protected StringBuilder handleProperty(ModelNode root, List<String> commands) {
        // the attributes for the add() command
        StringBuilder attributes = new StringBuilder();

        List<ModelNode> children = root.asList();
        for (ModelNode child : children) {
            // theoretically we can only have properties at this level
            if (isProperty(child)) {
                String valueName = child.asProperty().getName();
                ModelNode value = child.asProperty().getValue();

                if (isUndefined(value)) {
                    continue;
                }

                if (isList(value)) {
                    attributes.append(valueName).append("=").append(getNode(value)).append(",");
                } else if (isPrimitive(value)) {
                    attributes.append(valueName).append("=").append(getNode(value)).append(",");
                } else if (isProperty(value) || isObject(value)) {
                    boolean objectStarted = false;

                    for (ModelNode node : value.asList()) {
                        if (isUndefined(value)) {
                            continue;
                        }

                        String name = node.asProperty().getName();
                        ModelNode nodeValue = node.asProperty().getValue();

                        if (isUndefined(nodeValue) || isPrimitive(nodeValue)) {
                            if (isObject(value)) {
                                if (!objectStarted) {
                                    attributes.append(valueName).append("={");
                                    objectStarted = true;
                                }
                                attributes.append("\"").append(name).append("\"").append(" => ").append(getNode(nodeValue)).append(",");
                            } else {
                                attributes.append(name).append("=").append(getNode(nodeValue)).append(",");
                            }
                        } else if (isList(nodeValue)) {
                            attributes.append(name).append("=").append(getNode(nodeValue)).append(",");
                        } else if (isProperty(nodeValue) || isObject(nodeValue)) {
                            // and go a level deeper
                            commands.addAll(getChildResource(valueName, node));
                        } else {
                            throw new IllegalArgumentException("Unexpected node type" + value.getType());
                        }
                    }
                    if (objectStarted) {
                        attributes = removeComma(attributes).append("},");
                    }
                } else {
                    throw new IllegalArgumentException("Unexpected node type" + value.getType());
                }
            } else {
                throw new IllegalArgumentException("Expected property, but got " + child.getType());
            }
        }
        return attributes;
    }

    /**
     * @param nodes
     * @return the value for a list: ["val1","val2",...]
     */
    protected String getList(ModelNode nodes) {
        StringBuilder cmd = new StringBuilder("[");
        for (ModelNode node : nodes.asList()) {
            if (!isUndefined(node)) {
                cmd.append(getNode(node)).append(",");
            }
        }
        return removeComma(cmd).append("]").toString();
    }

    /**
     * @param nodes
     * @return the value for an object: { "name1" => "val1", "name2 => "val2", ...}
     */
    protected String getObject(ModelNode nodes) {
        StringBuilder cmd = new StringBuilder("{");
        for (String name : nodes.keys()) {
            ModelNode node = nodes.get(name);
            cmd.append(name).append("=").append(getNode(node)).append(",");
        }
        return removeComma(cmd).append("}").toString();
    }

    /**
     * used recursively for the supported types
     *
     * @param node
     * @return
     */
    protected String getNode(ModelNode node) {
        if (isUndefined(node)) {
            return "undefined";
        } else if (isPrimitive(node)) {
            return escape(node);
        } else if (isObject(node)) {
            return getObject(node);
        } else if (isList(node)) {
            return getList(node);
        } else {
            throw new IllegalArgumentException("Unknown type: " + node.getType());
        }
    }

    protected static boolean isProperty(ModelNode node) {
        return node.getType() == ModelType.PROPERTY;
    }

    protected static boolean isObject(ModelNode node) {
        return node.getType() == ModelType.OBJECT;
    }

    protected static boolean isList(ModelNode node) {
        return node.getType() == ModelType.LIST;
    }

    protected static boolean isUndefined(ModelNode node) {
        return node.getType() == ModelType.UNDEFINED;
    }

    protected static boolean isPrimitive(ModelNode node) {
        ModelType type = node.getType();
        return type == ModelType.BIG_DECIMAL
            || type == ModelType.BIG_INTEGER
            || type == ModelType.BOOLEAN
            || type == ModelType.BYTES
            || type == ModelType.DOUBLE
            || type == ModelType.EXPRESSION
            || type == ModelType.LONG
            || type == ModelType.INT
            || type == ModelType.STRING
            || type == ModelType.TYPE;
    }

    /**
     * escape the value part of a name=value (not of an Address)
     *
     * TODO: any more character needed ?
     *
     * @param value
     * @return
     */
    private String escape(ModelNode value) {
        return "\"" + value.asString().replaceAll("=", "\\=").replaceAll("\"", "\\") + "\"";
    }

    // for ease of use all loops add commas so cut it off when done
    // yes, Java 8 has a StringJoiner. But we need to support Java 7 for now.
    private StringBuilder removeComma(StringBuilder cmd) {
        if (cmd.charAt(cmd.length() - 1) == ',') {
            cmd.deleteCharAt(cmd.length() - 1);
        }
        return cmd;
    }
}
