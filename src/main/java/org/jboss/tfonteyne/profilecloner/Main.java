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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 *
 * @author Tom Fonteyne
 */
public class Main {

    private final static String VERSION = "2015-08-05";

    private static void usage() {
        System.out.println("JBoss AS 7 / WildFly / JBoss EAP 6  Profile (and more) Cloner - by Tom Fonteyne - version:" + VERSION);
        System.out.println("Usage:");
        System.out.println(
            " java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:profilecloner.jar\n"
            + "    org.jboss.tfonteyne.profilecloner.Main\n"
            + "    --controller=<host> --port=<number> --username=<user> --password=<password> \n"
            + "    --file=<name> --add-deployments=<true|false>\n"
            + "    /from=value destinationvalue [/from=value destinationvalue] ....\n"
            + "\n"
            + "Options:\n"
            + "  --controller=<host> | -c <host>       : Defaults to the setting in jboss-cli.xml if you have one,\n"
            + "  --port=<port>                           or localhost and 9999 (wildfly:9990)\n"
            + "  --username=<user> | -u <user>         : When not set, $local authentication is attempted\n"
            + "  --password=<password> | -p <password>\n"
            + "  --file=<name> | -f <name>             : The resulting CLI commands will be written to the file; if not set, they are output on the console\n"
            + "  --add-deployments=<true|false> | -ad  : By default cloning a server-group will skip the deployments\n"
            + "                                          If you first copy the content folder and clone the deployments, you can enable this\n"
            + "\n"
            + "Examples for \"/from=value destinationvalue\":\n"
            + "  Domain mode:\n"
            + "    /socket-binding-group=full-ha-sockets full-ha-sockets-copy\n"
            + "    /profile=full-ha full-ha-copy\n"
            + "    /profile=full-ha/subsystem=web web\n"
            + "\n"
            + "  Standalone server:\n"
            + "    /subsystem=security security\n"
            + "    profile\n"
            + "   The latter being a shortcut to clone all subsystems in individual batches\n"
            + "\n"
            + "Each set will generate a batch/run-batch. It is recommended to clone the profile last\n"
            + "The names from/to can be equal if you want to execute the script on a different controller.\n"
            + "\n"
            + "\n Secure connections need:"
            + "\n    -Djavax.net.ssl.trustStore=/path/to/store.jks -Djavax.net.ssl.trustStorePassword=password"
            + "\n"
        );
    }

    private String controller = null;
    private int port = 0;
    private String user;
    private String pass;

    private boolean addDeployments = false;

    private String filename;

    /**
     * used to keep a list of the elements to be cloned
     */
    private class Element {

        String source;
        String destination;

        public Element(String source) {
            this.source = source;
        }

        public Element(String from, String destination) {
            this.source = from;
            this.destination = destination;
        }
    }
    private final List<Element> elements = new LinkedList<>();

    public static void main(String[] args) {
        Main m = new Main(args);
    }

    public Main(String[] args) {
        if (!readOptions(args) || elements.isEmpty()) {
            usage();
            System.exit(0);
        }

        try {
            CommandContext ctx = getContext();
            ModelControllerClient client = ctx.getModelControllerClient();

            Cloner cloner;
            List<String> commands = new LinkedList<>();
            for (Element element : elements) {
                if ("profile".equals(element.source) && !ctx.isDomainMode()) {
                    cloner = new StandaloneCloner(client);
                } else {
                    cloner = new GenericCloner(client, element.source, element.destination, addDeployments);
                }
                commands.addAll(cloner.copy());
            }
            produceOutput(commands);

        } catch (CommandLineException | IOException | RuntimeException e) {
            e.printStackTrace();
        } finally {
            // due to a bug in EAP 6.1.0, we need to force an exit; not needed for any other version
            System.exit(0);
        }
    }

    private void produceOutput(List<String> commands) {
        if (filename == null) {
            for (String c : commands) {
                System.out.println(c);
            }
        } else {
            try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(filename), Charset.defaultCharset());) {
                for (String c : commands) {
                    writer.write(c);
                    writer.newLine();
                }
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
    }

    private CommandContext getContext() throws CommandLineException {
        CommandContextFactory ctxFactory = CommandContextFactory.getInstance();
        CommandContext ctx = ctxFactory.newCommandContext();

        if (controller==null) {
            controller = ctx.getDefaultControllerHost();
        }
        if (port == 0) {
            port = ctx.getDefaultControllerPort();
        }

        if (user != null) {
            ctx = ctxFactory.newCommandContext(user, pass.toCharArray());
        }
        ctx.connectController(controller, port);
        return ctx;
    }

    private boolean readOptions(String[] args) {
        int i = 0;
        while (i < args.length && args[i] != null && args[i].startsWith("-")) {
            if (args[i].startsWith("--controller=")) {
                controller = args[i++].substring("--controller=".length());
            } else if ("-c".equals(args[i])) {
                controller = args[++i];
                i++;
            } else if (args[i].startsWith("--username=")) {
                user = args[i++].substring("--username=".length());
            } else if ("-u".equals(args[i])) {
                user = args[++i];
                i++;
            } else if (args[i].startsWith("--password=")) {
                pass = args[i++].substring("--password=".length());
            } else if ("-p".equals(args[i])) {
                pass = args[++i];
                i++;
            } else if (args[i].startsWith("--file=")) {
                filename = args[i++].substring("--file=".length());
            } else if ("-f".equals(args[i])) {
                filename = args[++i];
                i++;
            } else if (args[i].startsWith("--add-deployments=")) {
                addDeployments = Boolean.parseBoolean(args[i++].substring("--add-deployments=".length()));
            } else if ("-ad".equals(args[i])) {
                addDeployments = true;
                i++;
            } else if (args[i].startsWith("--port=")) {
                port = Integer.parseInt(args[i++].substring("--port=".length()));
            } else {
                return false;
            }
        }

        try {
           while (i < args.length && args[i] != null) {
                if (args.length - i == 1) {
                    if ("profile".equals(args[i])) {
                        // standalone mode -> copy all subsystems
                        elements.add(new Element("profile"));
                        i++;
                    } else {
                        // domain mode -> profile with the same destination name
                        String source = args[i++];
                        elements.add(new Element("profile", source));
                    }
                } else if (args.length - i == 2) {
                    // two options -> CLI address as source, simple name as destination
                    String source = args[i++];
                    String destination = args[i++];
                    elements.add(new Element(source, destination));
                }
            }
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
        if ((user != null && pass == null) | (user == null && pass != null)) {
            System.out.println("Either specify user and password, or neither for local authentication.\n");
            return false;
        }

        return true;
    }
}
