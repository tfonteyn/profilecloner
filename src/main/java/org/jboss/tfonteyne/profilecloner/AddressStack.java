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

import java.util.Stack;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Tom Fonteyne
 */
public class AddressStack {

    private final Stack<Address> adresses = new Stack<>();

    public AddressStack(String rootName, String name) {
        this.rootName = rootName;
        this.name = name;
    }

    private final String rootName;
    private final String name;

    public void push(Address address) {
        adresses.push(address);
    }

    public void pop() {
        adresses.pop();
    }

    public StringBuilder toStringBuilder() {
        StringBuilder adressString = new StringBuilder("/").append(rootName).append("=").append(name);
        for (Address address : adresses) {
            adressString.append(address.toString());
        }
        return adressString;
    }

    public void toAddress(ModelNode node) {
        node.get(ClientConstants.OP_ADDR).add(rootName, name);
        for (Address address : adresses) {
            node.get(ClientConstants.OP_ADDR).add(address.name, address.value);
        }
    }
}
