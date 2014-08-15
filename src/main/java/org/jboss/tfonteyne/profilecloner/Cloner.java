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
public class Cloner
{
    protected final ModelControllerClient client;
    protected final String toName;
    protected final AddressStack addresses;

    protected Cloner(ModelControllerClient client, String newGroupName)
    {
        this.client = client;
        this.toName = newGroupName;

        addresses = new AddressStack(newGroupName);
    }

    /**
     *
     * @param root  for example "profile", "socket-binding-group" etc...
     * @param name
     * @return
     * @throws java.io.IOException
     * @throws org.jboss.as.cli.CommandLineException
     */
    public ModelNode getRoot(String root, String name)
        throws IOException, CommandLineException
    {
        ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        node.get(ClientConstants.OP_ADDR).add(root, name);
        node.get("recursive").set(true);
        node.get("include-defaults").set(false);

        ModelNode result = client.execute(node);
        if ("failed".equals(result.get(ClientConstants.OUTCOME).asString()))
        {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get(ClientConstants.RESULT);
    }

    protected List<String> copy(String address, ModelNode root)
        throws IOException, CommandLineException
    {
        List<String> commands = new LinkedList<>();
        commands.add("batch");
        commands.addAll(getChildResource(address, root));
        commands.add("run-batch");
        return commands;
    }

    protected List<String> getChildResource(String address, ModelNode root)
    {
        List<String> commands = new LinkedList<>();
        StringBuilder attributes;

        if (isProperty(root))
        {
            String addressName = root.asProperty().getName();
            addresses.push(new Address(address, addressName));

            // get the attributes for the top level. All sublevels are done with recursion
            attributes = handleProperty(root.asProperty().getValue(), commands);

            StringBuilder cmd = addresses.toStringBuilder().append(":add(").append(attributes);
            commands.add(0,removeComma(cmd).append(")").toString());
            addresses.pop();
            return commands;
        }
        else
        {
            attributes = handleProperty(root, commands);
            StringBuilder cmd = addresses.toStringBuilder().append(":add(").append(attributes);
            commands.add(0,removeComma(cmd).append(")").toString());
            return commands;
        }
    }

    /**
     * The bulk of the work is done in here - it will recursively call getChildResource
     *
     * There are potentially to many checks on undefined, but heck.. lets be safe
     *
     * @param root
     * @param commands
     * @return a list of attributes:    name1="val1",name2="val2",...
     */
    protected StringBuilder handleProperty(ModelNode root, List<String> commands)
    {
        // the attributes for the add() command
        StringBuilder attributes= new StringBuilder();

        List<ModelNode> children = root.asList();
        for (ModelNode child : children)
        {
            // theoretically we can only have properties at this level
            if (isProperty(child))
            {
                String valueName = child.asProperty().getName();
                ModelNode value = child.asProperty().getValue();

                if (isUndefined(value))
                {
                    continue;
                }

                if (isList(value))
                {
                    attributes.append(valueName).append("=").append(getNode(value)).append(",");
                }
                else if (isPrimitive(value))
                {
                    attributes.append(valueName).append("=").append(getNode(value)).append(",");
                }
                else if (isProperty(value) || isObject(value))
                {
                    boolean objectStarted = false;

                    for (ModelNode node : value.asList())
                    {
                        if (isUndefined(value))
                        {
                            continue;
                        }

                        String name = node.asProperty().getName();
                        ModelNode nodeValue = node.asProperty().getValue();

                        if (isUndefined(nodeValue))
                        {
                            continue;
                        }

                        if (isPrimitive(nodeValue))
                        {
                            if (isObject(value))
                            {
                                if (!objectStarted)
                                {
                                    attributes.append(valueName).append("={");
                                    objectStarted = true;
                                }
                                attributes.append("\"").append(name).append("\"").append(" => ").append(getNode(nodeValue)).append(",");
                            }
                            else
                            {
                                attributes.append(name).append("=").append(getNode(nodeValue)).append(",");
                            }
                        }
                        else if (isList(nodeValue))
                        {
                            attributes.append(name).append("=").append(getNode(nodeValue)).append(",");
                        }
                        else if (isProperty(nodeValue) || isObject(nodeValue))
                        {
                            // and go a level deeper
                            commands.addAll(getChildResource(valueName, node));
                        }
                        else
                        {
                            throw new IllegalArgumentException("Unexpected node type" + value.getType());
                        }
                    }
                    if (objectStarted)
                    {
                        attributes = removeComma(attributes).append("},");
                    }
                }
                else
                {
                    throw new IllegalArgumentException("Unexpected node type" + value.getType());
                }
            }
            else
            {
               throw new IllegalArgumentException("Expected property, but got " + child.getType());
            }
        }
        return attributes;
    }

    /**
     * @param nodes
     * @return the value for a list:  ["val1","val2",...]
     */
    protected String getList(ModelNode nodes)
    {
        StringBuilder cmd = new StringBuilder("[");
        for (ModelNode node : nodes.asList())
        {
            if (!isUndefined(node))
            {
                cmd.append(getNode(node)).append(",");
            }
        }
        return removeComma(cmd).append("]").toString();
    }

    /**
     * @param nodes
     * @return the value for an object:  { "name1" => "val1", "name2 => "val2", ...}
     */
    protected String getObject(ModelNode nodes)
    {
        StringBuilder cmd = new StringBuilder("{");
        for (String name : nodes.keys())
        {
            ModelNode node = nodes.get(name);
            if (!isUndefined(node))
            {
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
    protected String getNode(ModelNode node)
    {
        if (isUndefined(node))
        {
            throw new IllegalArgumentException("ERROR: doNode received UNDEFINED. this indicates a bug!");
        }
        else if (isPrimitive(node))
        {
            return escape(node);
        }
        else if (isObject(node))
        {
            return getObject(node);
        }
        else if (isList(node))
        {
            return getList(node);
        }
        else
        {
            throw new IllegalArgumentException("Unknown type: " + node.getType());
        }
    }




    protected static boolean isProperty(ModelNode node)
    {
        return node.getType() == ModelType.PROPERTY;
    }

    protected static boolean isObject(ModelNode node)
    {
        return node.getType() == ModelType.OBJECT;
    }

    protected static boolean isList(ModelNode node)
    {
        return node.getType() == ModelType.LIST;
    }

    protected static boolean isUndefined(ModelNode node)
    {
        return node.getType() == ModelType.UNDEFINED;
    }

    protected static boolean isPrimitive(ModelNode node)
    {
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
            || type == ModelType.TYPE
            ;
    }

    //TODO: any more character needed ?
    protected String escape(ModelNode value)
    {
        return "\"" + value.asString().replaceAll("=", "\\=").replaceAll("\"", "\\") + "\"";
    }

    // for ease of use all loops add commas so cut it off when done
    protected StringBuilder removeComma(StringBuilder cmd)
    {
        if (cmd.charAt(cmd.length()-1) == ',')
        {
            cmd.deleteCharAt(cmd.length()-1);
        }
        return cmd;
    }
}
