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
import org.jboss.dmr.ModelNode;

/**
 * A full profile has a number of exceptions in its tree which need to be handled
 *
 * @author Tom Fonteyne
 */
public class ProfileCloner extends Cloner {

    /**
     *
     * @param client
     * @param elementName should be set to "profile" for compatibility to any future changes
     * @param source
     * @param destination
     * @throws java.io.IOException
     * @throws org.jboss.as.cli.CommandLineException
     */
    public ProfileCloner(ModelControllerClient client, String elementName, String source, String destination)
        throws IOException, CommandLineException {
        super(client, elementName, source, destination);
    }

    /**
     * Note the special handling of certain nodes due to inconsistencies
     *
     * @param elementName
     * @param root
     * @return
     */
    @Override
    public List<String> getChildResource(String elementName, ModelNode root) {
        List<String> commands = new LinkedList<>();
        StringBuilder attributes;
        String addressString;

        if (!isProperty(root)) {
            addressString = addresses.toStringBuilder().toString();
            attributes = handleProperty(source, commands);

             // the profile comes back with a "name" attribute which is not allowed in "add"
            if ((addressString.equals("/profile=" + destinationName))) {
                attributes = new StringBuilder(attributes.toString().replaceAll("name=.*,", ""));
            }

            commands.add(0, buildAdd("add", attributes));
            return commands;
        } else {
            String addressName = root.asProperty().getName();
            addresses.push(new Address(elementName, addressName));
            addressString = addresses.toStringBuilder().toString();
            attributes = handleProperty(root.asProperty().getValue(), commands);

            // JGroups protocols are set with "add-protocol" instead of the normal "add"
            //TODO: what happens if a protocol has child resources ? none today but...
            if ((addressString.matches("/profile=.*/subsystem=\"jgroups\"/stack=.*/protocol=.*"))) {
                addresses.pop();
                commands.add(0, buildAdd("add-protocol", attributes));
                return commands;
            }

            // JGroups has protocols as read-only, but I found no way to avoid fetching this.
            if (addressString.matches("/profile=.*/subsystem=\"jgroups\"/stack=.*")
                && attributes.toString().contains("protocols=[")) {
                attributes = new StringBuilder(attributes.toString().replaceAll("protocols=\\[.*\\],", ""));
            }
            // deprecated but still present -> remove the attribute before adding
            else if (addressString.matches("/profile=.*/subsystem=\"security\"/security-domain=.*/.*=\"classic\"")
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

            commands.add(0, buildAdd("add", attributes));
            addresses.pop();
            return commands;
        }
    }
}
