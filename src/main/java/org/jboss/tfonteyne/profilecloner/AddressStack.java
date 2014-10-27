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

    public AddressStack(String root, String source) {
        adresses.push(new Address(root, source));
    }

    public AddressStack(String source) {
        String[] nvPairs = source.split("/");
        for (int i=1; i<nvPairs.length; i++) {
            adresses.push(new Address(nvPairs[i]));
        }
    }

    public void push(Address address) {
        adresses.push(address);
    }

    public void push(String nvString) {
        adresses.push(new Address(nvString));
    }

    public void pop() {
        adresses.pop();
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    public StringBuilder toStringBuilder() {
        StringBuilder adressString = new StringBuilder();
        for (Address address : adresses) {
            adressString.append(address.toString());
        }
        return adressString;
    }

    public void setAddress(ModelNode node) {
        for (Address address : adresses) {
            node.get(ClientConstants.OP_ADDR).add(address.name, address.value);
        }
    }
}
