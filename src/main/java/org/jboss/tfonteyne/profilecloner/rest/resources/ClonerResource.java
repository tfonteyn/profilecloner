/*
 *
 */
package org.jboss.tfonteyne.profilecloner.rest.resources;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.tfonteyne.profilecloner.Cloner;
import org.jboss.tfonteyne.profilecloner.Element;
import org.jboss.tfonteyne.profilecloner.GenericCloner;
import org.jboss.tfonteyne.profilecloner.StandaloneCloner;
import org.slf4j.Logger;

/**
 * The Class SenderResource.
 *
 * @author Andrea Battaglia
 */
@Path("cloner")
@RequestScoped
public class ClonerResource {

    @Inject
    private Logger LOG;

    @GET
    @Path("clone")
    @Produces(MediaType.TEXT_PLAIN)
    public String clone(//
            @QueryParam("controller") final String controller,
            @QueryParam("port") final Integer port,
            @QueryParam("username") final String username,
            @QueryParam("password") final String password,
            @QueryParam("file") final String file,
            @QueryParam("add-deployments") final Boolean addDeployments,
            @QueryParam("from") final String from) {

        /*
         * check input
         */
        // controller
        String controllerValue = controller;
        // == null ? "localhost" : controller;

        // port
        int portValue = port == null ? 0 : port;

        // username and password
        String usernameValue = username;
        String passwordValue = password;
        if (((username != null) && (password == null))
                | ((username == null) && (password != null))) {
            throw new RuntimeException(
                    "Either specify user and password, or neither for local authentication.\n");
        }
        // destination file
        String fileValue = file;
        // to add deploymets
        boolean addDeploymentsValue = addDeployments == null ? false
                : addDeployments;
        // sources
        List<String> fromValues = Arrays.asList(from.split(","));
        List<Element> elements = new ArrayList<>();
        for (String fromValue : fromValues) {
            if (fromValue.contains(" ")) {
                // two options -> CLI address as source, simple name as
                // destination
                String split[] = fromValue.split(" ");
                elements.add(new Element(split[0], split[1]));
            } else {
                if ("profile".equals(fromValue)) {
                    // standalone mode -> copy all subsystems
                    elements.add(new Element("profile"));
                } else {
                    // domain mode -> profile with the same destination name
                    elements.add(new Element("profile", fromValue));
                }
            }
        }

        try {
            CommandContext ctx = getContext(controllerValue, portValue,
                    usernameValue, passwordValue);
            ModelControllerClient client = ctx.getModelControllerClient();

            Cloner cloner;
            List<String> commands = new ArrayList<>();
            for (Element element : elements) {
                if ("profile".equals(element.getSource())
                        && !ctx.isDomainMode()) {
                    cloner = new StandaloneCloner(client);
                } else {
                    cloner = new GenericCloner(client, element.getSource(),
                            element.getDestination(), addDeployments);
                }
                commands.addAll(cloner.copy());
            }
            return produceOutput(fileValue, commands);

        } catch (CommandLineException | IOException | RuntimeException e) {
            LOG.error("", e);
            throw new RuntimeException(e);
        } finally {
            // due to a bug in EAP 6.1.0, we need to force an exit; not needed
            // for any other version
            // System.exit(0);
        }
    }

    private String produceOutput(String filename, List<String> commands) {
        String result;
        StringBuilder sb = new StringBuilder();

        for (String c : commands) {
            sb.append(c).append("\n");
        }
        result = sb.toString();
        if (filename != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(
                    Paths.get(filename), Charset.defaultCharset());) {
                writer.write(result);
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
        return result;
    }

    private CommandContext getContext(String controller, Integer port,
            String user, String pass) throws CommandLineException {
        CommandContextFactory ctxFactory = CommandContextFactory.getInstance();
        CommandContext ctx = ctxFactory.newCommandContext();

        if (controller == null) {
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
}
