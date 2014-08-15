package org.jboss.tfonteyne.profilecloner;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 *
 * @author Tom Fonteyne
 */
public class Main
{
    private final static String VERSION = "2014-08-15 beta";

    private static String controller = "localhost";
    private static int port = 9999;
    private static String user;
    private static String pass;

    private static String filename;

    private static String source;
    private static String destination;

    public static void main(String[] args)
    {
        if (!readOptions(args))
        {
            usage();
            System.exit(1);
        }

        try
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


            ModelControllerClient client = ctx.getModelControllerClient();
            if (ctx.isDomainMode())
            {
                ProfileCloner cloner = new ProfileCloner(client, source, destination);
                List<String> commands = cloner.copy();

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

    private static boolean readOptions(String[] args)
    {
        int i = 0;
        while (i < args.length -2 && args[i] != null)
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

        try
        {
            source = args[i++];
            destination = args[i++];
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
        System.out.println("JBoss AS 7 / EAP  Profile Cloner - by Tom Fonteyne - version:" + VERSION);
        System.out.println("Usage:");
        System.out.println(
            " java -jar profilecloner.jar --controller=<host> --username=<user> --password=<password --port=<number>  --file=<name> <fromprofile> <toprofile>"
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
