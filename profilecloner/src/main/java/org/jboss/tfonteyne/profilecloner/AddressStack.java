package org.jboss.tfonteyne.profilecloner;

import java.util.Stack;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Tom Fonteyne
 */
public class AddressStack
{

    private final Stack<Address> adresses = new Stack<Address>();

    public AddressStack(String profile)
    {
        this.profile = profile;
    }

    private final String profile;

    public void push(Address address)
    {
        adresses.push(address);
    }

    public void pop()
    {
        adresses.pop();
    }

    public StringBuilder toStringBuilder()
    {
        StringBuilder adressString = new StringBuilder("/profile=" + profile);
        for (Address address : adresses)
        {
            adressString.append(address.getPath());
        }
        return adressString;
    }

    public void toAddress(ModelNode node)
    {
        node.get(ClientConstants.OP_ADDR).add("profile", profile);
        for (Address address : adresses)
        {
            node.get(ClientConstants.OP_ADDR).add(address.name, address.value);
        }
    }
}
