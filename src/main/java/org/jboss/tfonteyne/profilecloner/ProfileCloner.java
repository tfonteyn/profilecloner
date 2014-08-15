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
public class ProfileCloner
{
    private final ModelControllerClient client;
    private final String profileName;
    private final String newProfileName;
    private final AddressStack addresses;

    /**
     *
     * @param client
     * @param profileName
     * @param newProfileName
     */
    public ProfileCloner(ModelControllerClient client, String profileName, String newProfileName)
    {
        this.client = client;
        this.profileName = profileName;
        this.newProfileName = newProfileName;

        addresses = new AddressStack(newProfileName);
    }

    /**
     * Connects to the controller and gets the complete original profile with recursive=true
     *
     */
    private ModelNode getProfile(String profile, boolean recursive)
        throws IOException, CommandLineException
    {
        ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        node.get(ClientConstants.OP_ADDR).add("profile", profile);
        node.get("recursive").set(recursive);
        node.get("include-defaults").set(false);

        ModelNode result = client.execute(node);
        if ("failed".equals(result.get(ClientConstants.OUTCOME).asString()))
        {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get(ClientConstants.RESULT);
    }

    /**
     * generate the CLI script
     *
     * @return
     * @throws java.io.IOException
     * @throws org.jboss.as.cli.CommandLineException
     */
    public List<String> copy()
        throws IOException, CommandLineException
    {
        List<String> commands = new LinkedList<String>();

        commands.add("batch");
        commands.add("/profile=" + newProfileName + ":add()");

        ModelNode profile = getProfile(profileName, true);
        List<ModelNode> subsystems = profile.get("subsystem").asList();
        for (ModelNode node : subsystems)
        {
            // for debugging single subsystems
            //if (node.asProperty().getName().equals("mail"))
                commands.addAll(getChildResource("subsystem", node));
        }

        commands.add("run-batch");
        return commands;
    }

    /**
     * Used recursively to drill down the tree of the profile
     * Each entry generated a list of CLI commands
     *
     * Note the special handling of certain nodes due to inconsistencies
     *
     * @param address
     * @param root
     * @return
     */
    public List<String> getChildResource(String address, ModelNode root)
    {
        List<String> commands = new LinkedList<String>();

        String addressName = root.asProperty().getName();
        addresses.push(new Address(address, addressName));
        String addressString = addresses.toStringBuilder().toString();

        // get the attributes for the top level. All sublevels are done with recursion
        StringBuilder attributes = handleProperty(root.asProperty().getValue(), commands);

        // JGroups protocols are set with "add-protocol" instead of the normal "add"
        if ((addressString.matches("/profile=.*/subsystem=jgroups/stack=.*/protocol=.*")))
        {
            addresses.pop();
            StringBuilder cmd = addresses.toStringBuilder().append(":add-protocol(").append(attributes);
            commands.add(0, removeComma(cmd).append(")").toString());
            //TODO: what happens if a protocol has properties ??
            return commands;
        }
        // JGroups has protocols as read-only, but I found no way to avoid fetching this.
        else if (addressString.matches("/profile=.*/subsystem=jgroups/stack=.*")
               && attributes.toString().contains("protocols=["))
        {
            //FIXME: today "protocols" is the only attribute of "stack" - if more get added, we drop them here!
            commands.add(0,addresses.toStringBuilder().append(":add()").toString());
            addresses.pop();
            return commands;
        }

        // hornet-q has some undefined attributes that must be there
        //FIXME: the first two are really hacks as the name of the connection factory might be different
        // but without the name I can't distinggish them
        if (addressString.matches("/profile=.*/subsystem=messaging/hornetq-server=.*/connection-factory=InVmConnectionFactory"))
        {
            attributes.append("connector={\"in-vm\" => undefined},");
        }
        if (addressString.matches("/profile=.*/subsystem=messaging/hornetq-server=.*/connection-factory=RemoteConnectionFactory"))
        {
            attributes.append("connector={\"netty\" => undefined},");
        }

        if (addressString.matches("/profile=.*/subsystem=messaging/hornetq-server=default/pooled-connection-factory=.*"))
        {
            attributes.append("connector={\"in-vm\" => undefined},");
        }

        // deprecated but still present -> remove the attribute before adding
        if (addressString.matches("/profile=.*/subsystem=security/security-domain=.*/.*=classic")
             && (
                    attributes.toString().contains("login-modules=[")
                 || attributes.toString().contains("policy-modules=[")
                 || attributes.toString().contains("provider-modules=[")
                 || attributes.toString().contains("trust-modules=[")
                 || attributes.toString().contains("mapping-modules=[")
                )
            )
        {
            //FIXME: today this is the only attribute of "classic" - if more get added, we loose them here!
            commands.add(0,addresses.toStringBuilder().append(":add()").toString());
            addresses.pop();
            return commands;
        }

        // the normal case
        StringBuilder cmd = addresses.toStringBuilder().append(":add(").append(attributes);
        commands.add(0,removeComma(cmd).append(")").toString());
        addresses.pop();
        return commands;
    }

    /**
     * The bulk of the work is done in here - it will recursively call getChildResource
     *
     * There are potentially to many checks on undefined, but heck.. let's be safe
     *
     * @param root
     * @param commands
     * @return
     */
    private StringBuilder handleProperty(ModelNode root, List<String> commands)
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
               throw new IllegalArgumentException("Expected property, got " + child.getType());
            }
        }
        return attributes;
    }


    /**
     * generates the value for a list:  ["val1","val2",...]
     *
     * @param nodeList
     * @return
     */
    public String getList(ModelNode nodeList)
    {
        StringBuilder cmd = new StringBuilder("[");
        for (ModelNode node : nodeList.asList())
        {
            if (!isUndefined(node))
            {
                cmd.append(getNode(node)).append(",");
            }
        }
        return removeComma(cmd).append("]").toString();
    }

    /**
     * generates the value for an object:  { "name1" => "val1", "name2 => "val2", ...}
     *
     * @param nodeObject
     * @return
     */
    public String getObject(ModelNode nodeObject)
    {
        StringBuilder cmd = new StringBuilder("{");
        for (String name : nodeObject.keys())
        {
            ModelNode node = nodeObject.get(name);
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
    private String getNode(ModelNode node)
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




    public static boolean isProperty(ModelNode node)
    {
        return node.getType() == ModelType.PROPERTY;
    }

    public static boolean isObject(ModelNode node)
    {
        return node.getType() == ModelType.OBJECT;
    }

    public static boolean isList(ModelNode node)
    {
        return node.getType() == ModelType.LIST;
    }

    public static boolean isUndefined(ModelNode node)
    {
        return node.getType() == ModelType.UNDEFINED;
    }

    public static boolean isPrimitive(ModelNode node)
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
    private String escape(ModelNode value)
    {
        return "\"" + value.asString().replaceAll("=", "\\=").replaceAll("\"", "\\") + "\"";
    }

    // for ease of use all loops add commas so cut it off when done
    private StringBuilder removeComma(StringBuilder cmd)
    {
        if (cmd.charAt(cmd.length()-1) == ',')
        {
            cmd.deleteCharAt(cmd.length()-1);
        }
        return cmd;
    }
}
