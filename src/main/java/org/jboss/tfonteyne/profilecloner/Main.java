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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Tom Fonteyne
 */
public class Main
{
    private final static String VERSION = "2014-08-15 beta";

    private String controller = "localhost";
    private int port = 9999;
    private String user;
    private String pass;

    private String filename;

    private class Element
    {
        String name;
        String source;
        String destination;

        public Element(String name, String source, String destination)
        {
            this.name = name;
            this.source = source;
            this.destination = destination;
        }

    }
    private final List<Element> elements = new LinkedList<>();

    public static void main(String[] args)
    {
        Main m = new Main(args);
    }

    public Main(String[] args)
    {
        if (!readOptions(args))
        {
            usage();
            System.exit(1);
        }

        try
        {
            CommandContext ctx = getContext();
            if (ctx.isDomainMode())
            {
                ModelControllerClient client = ctx.getModelControllerClient();
                Cloner cloner;
                List<String> commands = new LinkedList<>();
                for (Element element : elements)
                {
                    switch (element.name)
                    {
                        case "profile":
                            cloner = new ProfileCloner(client, element.destination);
                            break;
                        default:
                            cloner = new Cloner(client, element.destination);
                            break;
                    }
                    ModelNode root = cloner.getRoot(element.name, element.source);
                    commands.addAll(cloner.copy(element.name,root));
                }

                produceOutput(commands);
            }
            else
            {
                System.out.println("The server is running in standalone mode");
            }
        }
        catch (CommandLineException | IOException | RuntimeException e)
        {
            e.printStackTrace();
        }
        finally
        {
            // due to a bug in EAP 6.1.0, we need to force an exit; not needed for any other version
            System.exit(0);
        }
    }

    private void produceOutput(List<String> commands)
    {
        if (filename == null)
        {
            for (String c : commands)
            {
                System.out.println(c);
            }
        }
        else
        {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename));)
            {
                for (String c : commands)
                {
                    writer.write(c);
                    writer.newLine();
                }
            }
            catch (IOException e)
            {
                System.out.println(e.toString());
            }
        }
    }

    private CommandContext getContext() throws CommandLineException
    {
        CommandContext ctx;
        if (user != null)
        {
            ctx = CommandContextFactory.getInstance().newCommandContext(controller, port, user, pass.toCharArray());
            ctx.connectController();
        }
        else
        {
            // local auth
            ctx = CommandContextFactory.getInstance().newCommandContext();
            ctx.connectController(controller, port);
        }
        return ctx;
    }

    private boolean readOptions(String[] args)
    {
        int i = 0;
        while (i < args.length && args[i] != null && args[i].startsWith("-"))
        {
            if (args[i].startsWith("--controller="))
            {
                controller = args[i++].substring("--controller=".length());
            }
            else if ("-c".equals(args[i]))
            {
                controller = args[++i];
                i++;
            }
            else if (args[i].startsWith("--username="))
            {
                user = args[i++].substring("--username=".length());
            }
            else if ("-u".equals(args[i]))
            {
                user = args[++i];
                i++;
            }
            else if (args[i].startsWith("--password="))
            {
                pass = args[i++].substring("--password=".length());
            }
            else if ("-p".equals(args[i]))
            {
                pass = args[++i];
                i++;
            }
            else if (args[i].startsWith("--file="))
            {
                filename = args[i++].substring("--file=".length());
            }
            else if ("-f".equals(args[i]))
            {
                filename = args[++i];
                i++;
            }
            else if (args[i].startsWith("--port="))
            {
                port = Integer.parseInt(args[i++].substring("--port=".length()));
            }
            else
            {
               return false;
            }
        }

         // first non-option seen (or else the comand line was garbled), now we expect sets of 3 with a shortcut of 2 for a profile
        try
        {
            while (i < args.length && args[i] != null)
            {
                // if there are only two options at the end, we presume the user wanted to clone a profile
                if (args.length - i == 2)
                {
                    String source = args[i++];
                    String dest = args[i++];
                    elements.add(new Element("profile",source,dest));
                }
                else
                {
                    String element = args[i++];
                    String source = args[i++];
                    String dest = args[i++];
                    elements.add(new Element(element,source,dest));
                }
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            return false;
        }
        if ((user!=null && pass==null) | (user==null && pass!=null))
        {
            System.out.println("Either specify user and password, or neither for local authentication.\n");
            return false;
        }

        return true;
    }

    private static void usage()
    {
        System.out.println("JBoss AS 7 / EAP  Profile (and more) Cloner - by Tom Fonteyne - version:" + VERSION);
        System.out.println("Usage:");
        System.out.println(
            " java -jar profilecloner.jar --controller=<host> --username=<user> --password=<password --port=<number>  --file=<name> rootelement from to rootelement from to ...."
            + "  where \"rootelement from to\" is for example:\n"
            + "      socket-binding-group <from-group> <to-group> profile <fromprofile> <toprofile>  ...."
            + "each set will generate a batch/run-batch. It is recommended to clone the profile last"
            + "The names from/to can be equal if you want to execute the script on a different domain."
            +    "\n"
            +    "Defaults:\n"
            +    "  controller: localhost\n"
            +    "  port      : 9999\n"
            +    "  file      : to the console\n"

            + "\n"
            + "\n\n Secure connections need:"
            + "\n    java -Djavax.net.ssl.trustStore=/path/to/store.jks -Djavax.net.ssl.trustStorePassword=password -jar profilecloner.jar ..."
        );
    }
}
