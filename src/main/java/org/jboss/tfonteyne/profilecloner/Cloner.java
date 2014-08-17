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
 *
 * @author Tom Fonteyne
 */
public class Cloner {

    protected final ModelControllerClient client;
    protected final String elementName;
    protected final ModelNode source;
    protected final String destinationName;
    protected final AddressStack addresses;

    /**
     *
     * @param client
     * @param elementName for example "profile" (but see: ProfileCloner), "socket-binding-group" etc...
     * @param sourceName
     * @param destinationName
     * @throws java.io.IOException
     * @throws org.jboss.as.cli.CommandLineException
     */
    protected Cloner(ModelControllerClient client, String elementName, String sourceName, String destinationName)
        throws IOException, CommandLineException {
        this.client = client;
        this.elementName = elementName;
        this.source = getSource(elementName, sourceName);
        this.destinationName = destinationName;
        addresses = new AddressStack(elementName, destinationName);
    }

    private ModelNode getSource(String elementName, String sourceName)
        throws IOException, CommandLineException {
        ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        node.get(ClientConstants.OP_ADDR).add(elementName, sourceName);
        node.get("recursive").set(true);
        node.get("include-defaults").set(false);

        ModelNode result = client.execute(node);
        if ("failed".equals(result.get("outcome").asString())) {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get("result");
    }

    protected List<String> copy() throws IOException, CommandLineException {
        List<String> commands = new LinkedList<>();
        commands.add("batch");
        commands.addAll(getChildResource(elementName, source));
        commands.add("run-batch");
        return commands;
    }

    /**
     * Gets called recursively
     *
     * @param elementName
     * @param source
     * @return
     */
    protected List<String> getChildResource(String elementName, ModelNode source) {
        List<String> commands = new LinkedList<>();

        if (isProperty(source)) {
            addresses.push(new Address(elementName, source.asProperty().getName()));
            commands.add(buildAdd("add", handleProperty(source.asProperty().getValue(), commands)));
            addresses.pop();
        } else {
            commands.add(buildAdd("add", handleProperty(source, commands)));
        }
        return commands;
    }

    protected String buildAdd(String command, StringBuilder attributes) {
        StringBuilder cmd = addresses.toStringBuilder().append(":").append(command).append("(").append(attributes);
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

                        if (isUndefined(nodeValue)) {
                            continue;
                        }

                        if (isPrimitive(nodeValue)) {
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
            if (!isUndefined(node)) {
                cmd.append(name).append("=").append(getNode(node)).append(",");
            }
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
            throw new IllegalArgumentException("ERROR: doNode received UNDEFINED. this indicates a bug!");
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

    //TODO: any more character needed ?
    private String escape(ModelNode value) {
        return "\"" + value.asString().replaceAll("=", "\\=").replaceAll("\"", "\\") + "\"";
    }

    // for ease of use all loops add commas so cut it off when done
    private StringBuilder removeComma(StringBuilder cmd) {
        if (cmd.charAt(cmd.length() - 1) == ',') {
            cmd.deleteCharAt(cmd.length() - 1);
        }
        return cmd;
    }
}
