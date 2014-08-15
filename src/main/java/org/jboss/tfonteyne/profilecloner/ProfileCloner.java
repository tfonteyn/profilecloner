package org.jboss.tfonteyne.profilecloner;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 * A full profile has a number of exceptions in its tree which need to be handled
 *
 * @author Tom Fonteyne
 */
public class ProfileCloner extends Cloner
{
    /**
     *
     * @param client
     * @param newProfileName
     */
    public ProfileCloner(ModelControllerClient client, String newProfileName)
    {
        super(client,newProfileName);
    }

    /**
     * generate the CLI script
     *
     * @param address: ignored, but should be set to "profile" for compatibility with future changes
     * @param root
     * @return
     * @throws java.io.IOException
     * @throws org.jboss.as.cli.CommandLineException
     */
    @Override
    public List<String> copy(String address, ModelNode root)
        throws IOException, CommandLineException
    {
        List<String> commands = new LinkedList<>();

        commands.add("batch");
        commands.add("/profile=" + toName + ":add()");

        List<ModelNode> subsystems = root.get("subsystem").asList();
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

    @Override
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
}
