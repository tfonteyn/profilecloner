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
import java.util.LinkedList;
import java.util.List;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;

/**
 * As there is no actual "profile" root in standalone, we must walk the list of subsystems instead.
 * Each subsystem is set within batch/run-batch as the user will likely only want copy/paste some of them.
 * This is still easier then cloning one subsystem at a time.
 *
 * TODO: it seems that creating all subsystems in one single batch fails due to dependency issues.
 *       For now, each subsystem gets its own batch. 
 *       Interdependencies will require re-ordering before adding.
 */
public class StandaloneCloner implements Cloner {

    private static final String FAILED = "failed";

    private final ModelControllerClient client;

    public StandaloneCloner(final ModelControllerClient client) {
        this.client = client;
    }

    @Override
    public List<String> copy()
        throws IOException, 
               CommandLineException {
        final List<String> commands = new LinkedList<>();
        final List<ModelNode> subsystems = getSubsystems();
        for (ModelNode subsystem : subsystems) {
            final String name = subsystem.asProperty().getName();
            final Cloner cloner = new GenericCloner(client, "/subsystem=" + name, name, false);
            commands.addAll(cloner.copy());
        }
        return commands;
    }

    private List<ModelNode> getSubsystems() 
        throws IOException, 
               CommandLineException {
        final ModelNode node = new ModelNode();
        node.get(ClientConstants.OP).set(ClientConstants.READ_RESOURCE_OPERATION);
        node.get(ClientConstants.RECURSIVE).set(false);

        final ModelNode result = client.execute(node);
        if (FAILED.equals(result.get(ClientConstants.OUTCOME).asString())) {
            throw new java.lang.RuntimeException(result.asString());
        }
        return result.get(ClientConstants.RESULT).get("subsystem").asList();
    }
}
