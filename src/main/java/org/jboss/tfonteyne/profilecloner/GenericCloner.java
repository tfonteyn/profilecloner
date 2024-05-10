/*
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Clones any "/name=value" root whether in standalone or in domain 
 * and delivers the CLI statement as a batch.
 */
public class GenericCloner implements Cloner {

    private static final String VERSION_MAJOR = "management-major-version";
    private static final String VERSION_MICRO = "management-micro-version";
    private static final String VERSION_MINOR = "management-minor-version";

    // mostly for reference, used for compatibility exceptions
    private static final int EAP6 = 1;
    private static final int WILDFLY8 = 2;
    private static final int WILDFLY9 = 3;
    private static final int WILDFLY10 = 4; // == EAP 7
    
    private static final String FAILED = "failed";

    private final ModelControllerClient client;
    private final String elementName;
    private final String destinationName;

    private final AddressStack destinationAddress;
    private final AddressStack sourceAddress;

    private final boolean addDeployments;

    private int managementMajorVersion = 0;

    /**
     *
     * @param client
     * @param source          a CLI style address, 
     *                        example: "/profile=default" or "/host=master/core-service=management"
     * @param destinationName the new name of the last part of the source address, 
     *                        example "new-default" or "management" (e.g. use same name)
     * @param addDeployments
     * @throws IOException
     * @throws CommandLineException
     */
    public GenericCloner(final ModelControllerClient client,
                         final String source,
                         final String destinationName,
                         final boolean addDeployments)
        throws IOException,
               CommandLineException {
        this.client = client;
        this.destinationName = destinationName;
        this.addDeployments = addDeployments;

        final int pos = source.lastIndexOf("/");
        this.elementName = source.substring(pos + 1, source.length() - 1).split("=")[0];

        sourceAddress = new AddressStack(source);
        destinationAddress = new AddressStack(source.substring(0, pos) + "/" + elementName + "=" + destinationName);

        managementMajorVersion = this.getManagementVersion(client, VERSION_MAJOR);
    }

    @Override
    public List<String> copy() 
        throws IOException,
               CommandLineException {
        final List<String> commands = new LinkedList<>();
        commands.add("batch");
        commands.addAll(processChildResource(elementName, getSource(sourceAddress)));
        commands.add("run-batch");
        return commands;
    }

    private int getManagementVersion(final ModelControllerClient client,
                                     final String version)
        throws IOException {
        final ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_ATTRIBUTE_OPERATION);
        node.get(ClientConstants.NAME).set(version);
        final ModelNode result = client.execute(node);
        if (FAILED.equals(result.get(ClientConstants.OUTCOME).asString())) {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get(ClientConstants.RESULT).asInt();
    }

    private ModelNode getSource(final AddressStack source)
        throws IOException, 
               CommandLineException {
        final ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        source.setAddress(node);
        node.get(ClientConstants.RECURSIVE).set(true);
        node.get("include-defaults").set(false);

        final ModelNode result = client.execute(node);
        if (FAILED.equals(result.get(ClientConstants.OUTCOME).asString())) {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get(ClientConstants.RESULT);
    }

    /**
     * Called recursively. Note the special handling of certain nodes due to inconsistencies.
     *
     * @param elementName
     * @param source
     *
     * @return list of commands for the child resource
     */
    private List<String> processChildResource(final String elementName,
                                              final ModelNode source) {
        final List<String> commands = new LinkedList<>();
        if (isProperty(source)) {
            destinationAddress.push(new Address(elementName, source.asProperty().getName()));

            final String addressString = destinationAddress.toString();
            List<String> attributes = processProperty(source.asProperty().getValue(), commands);

            if (managementMajorVersion < WILDFLY9) {
                // pre WildFly 9 required special handling of the JGroups protocols which were set
                // using "add-protocol" instead of the normal "add"
                if ((addressString.matches(".*/subsystem=\"jgroups\"/stack=.*/protocol=.*"))
                    // but do not do this for child resources
                    && (!addressString.matches(".*/subsystem=\"jgroups\"/stack=.*/protocol=.*/.*=.*"))) {
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

            // JGroups has "protocols" as read-only, but we get it back anyhow -> remove
            if (addressString.matches(".*/subsystem=\"jgroups\"/stack=.*")) {
                attributes = attributes.stream()
                    .filter(a -> !a.startsWith("protocols=["))
                    .collect(Collectors.toList());
            }

            // deprecated attributes -> remove
            if (addressString.matches(".*/subsystem=\"security\"/security-domain=.*/.*=\"classic\"")) {
                attributes = attributes.stream()
                    .filter(a -> !a.startsWith("login-modules=["))
                    .filter(a -> !a.startsWith("policy-modules=["))
                    .filter(a -> !a.startsWith("provider-modules=["))
                    .filter(a -> !a.startsWith("trust-modules=["))
                    .filter(a -> !a.startsWith("mapping-modules=["))
                    .collect(Collectors.toList());
            }

            // deprecated attributes -> remove
            if (addressString.matches(".*/subsystem=\"security\"/security-domain=.*/.*=\"jaspi\"")) {
                attributes = attributes.stream()
                    .filter(a -> !a.startsWith("auth-modules=["))
                    .collect(Collectors.toList());
            }

            // deprecated attributes -> remove
            if (addressString.matches(".*/subsystem=\"security\"/security-domain=.*/.*=\"jaspi\"/login-module-stack=.*")) {
                attributes = attributes.stream()
                    .filter(a -> !a.startsWith("login-modules=["))
                    .collect(Collectors.toList());
            }

            // These have "name" as read-only, but we get it back anyhow -> remove
            if (addressString.matches(".*/subsystem=\"logging\"/.*file-handler=.*")
                || addressString.matches(".*/subsystem=\"batch-jberet\"/thread-pool=.*")
                || addressString.matches(".*/subsystem=\"jca\"/.*running-threads=.*")
                || addressString.matches(".*/subsystem=\"ejb3\"/thread-pool=.*")) {
                attributes = attributes.stream()
                    .filter(a -> !a.startsWith("name="))
                    .collect(Collectors.toList());
            }

            // The batch failed with the following error (you are remaining in the batch editing mode to have a chance to co
            // rrect the error): {"WFLYCTL0062: Composite operation failed and was rolled back. Steps that failed:" => {"Operation step
            // -270" => "WFLYCTL0446: jacc-policy or alternative(s) [custom-policy] is required"}}
            // /profile="ha-copy"/subsystem="singleton":add(default="default")
            // /profile="ha-copy"/subsystem="singleton"/singleton-policy="default":add(cache-container="server")
            // /profile="ha-copy"/subsystem="singleton"/singleton-policy="default"/election-policy="simple":add()
            
            commands.add(0, buildAdd("add", attributes));
            destinationAddress.pop();

        } else {
            final String addressString = destinationAddress.toString();
            List<String> attributes = processProperty(source, commands);

            // profile has "name" as read-only, but we get it back anyhow -> remove
            if ((addressString.equals("/profile=\"" + destinationName + "\""))) {
                attributes = attributes.stream()
                    .filter(a -> !a.startsWith("name="))
                    .collect(Collectors.toList());
            }

            commands.add(0, buildAdd("add", attributes));
        }
        return commands;
    }

    private String buildAdd(final String command,
                            final List<String> attributes) {
        return destinationAddress.toStringBuilder()
            .append(":").append(command)
            .append(attributes.stream().collect(Collectors.joining(",", "(", ")")))
            .toString();
    }

    /**
     * The bulk of the work is done in here - it will recursively call processChildResource.
     * <p>
     * There are potentially to many checks on undefined, but heck.. lets be safe
     *
     * @param root
     * @param commands
     *
     * @return a list of attributes: name1="val1",name2="val2",...
     */
    private List<String> processProperty(final ModelNode root,
                                         final List<String> commands) {
        // the attributes for the add() command
        final List<String> attributes = new ArrayList<>();

        for (ModelNode child : root.asList()) {
            // theoretically we can only have properties at this level
            if (isProperty(child)) {
                final String valueName = child.asProperty().getName();
                final ModelNode value = child.asProperty().getValue();

                if (isUndefined(value)) {
                    continue;
                }

                if (isList(value) || isPrimitive(value)) {
                    attributes.add(valueName + "=" + nodeToString(value));

                } else if (isProperty(value) || isObject(value)) {
                    final StringJoiner objectAtrrs = new StringJoiner(",", "{", "}").setEmptyValue("");

                    for (ModelNode node : value.asList()) {
                        final String name = node.asProperty().getName();
                        final ModelNode nodeValue = node.asProperty().getValue();

                        if (isUndefined(nodeValue) || isPrimitive(nodeValue)) {
                            if (isObject(value)) {
                                objectAtrrs.add("\"" + name + "\" => " + nodeToString(nodeValue));
                            } else {
                                objectAtrrs.add(name + "=" + nodeToString(nodeValue));
                            }
                        } else if (isList(nodeValue)) {
                            objectAtrrs.add(name + "=" + nodeToString(nodeValue));
                        } else if (isProperty(nodeValue) || isObject(nodeValue)) {
                            // decend into prop/obj
                            commands.addAll(processChildResource(valueName, node));
                        } else {
                            throw new IllegalArgumentException("Unexpected node type" + value.getType());
                        }
                    }
                    if (objectAtrrs.length() > 0) {
                        attributes.add(valueName + "=" + objectAtrrs.toString());
                    }
                } else {
                    throw new IllegalArgumentException("Unexpected node type" + value.getType());
                }
            } else {
                throw new IllegalArgumentException("Expected a property, but got " + child.getType());
            }
        }
        return attributes;
    }

    /**
     * @param nodes
     *
     * @return the value for a list: ["val1","val2",...]
     */
    private String getList(final ModelNode nodes) {
        return nodes.asList()
            .stream()
            .filter(node -> !isUndefined(node))
            .map(this::nodeToString)
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * @param nodes
     *
     * @return the value for an object: { "name1" => "val1", "name2 => "val2", ...}
     */
    private String getObject(final ModelNode nodes) {
        return nodes.keys()
            .stream()
            .map(key -> key + "=" + nodeToString(nodes.get(key)))
            .collect(Collectors.joining(",", "{", "}"));
    }

    /**
     * Convert a node to its String representation.
     *
     * @param node
     *
     * @return
     */
    private String nodeToString(final ModelNode node) {
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

    private static boolean isProperty(final ModelNode node) {
        return node.getType() == ModelType.PROPERTY;
    }

    private static boolean isObject(final ModelNode node) {
        return node.getType() == ModelType.OBJECT;
    }

    private static boolean isList(final ModelNode node) {
        return node.getType() == ModelType.LIST;
    }

    private static boolean isUndefined(final ModelNode node) {
        return node.getType() == ModelType.UNDEFINED;
    }

    private static boolean isPrimitive(final ModelNode node) {
        final ModelType type = node.getType();
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
     * @param value
     *
     * @return
     */
    private String escape(final ModelNode value) {
        return "\"" + value.asString().replace("=", "\\=").replace("\"", "\\\"") + "\"";
    }
}
