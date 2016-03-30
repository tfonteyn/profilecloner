/*
 *
 */
package org.jboss.tfonteyne.profilecloner.rest.resources;

import java.io.InputStream;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;

/**
 * The Class HelpResource.
 *
 * @author Andrea Battaglia
 */
@Path("help")
@ApplicationScoped
public class HelpResource {

    @Inject
    private Logger LOG;
    private String version;
    private String appVersion;
    private String usageString;

    void init(@Observes @Initialized(ApplicationScoped.class) Object event) {
        LOG.debug("Starting");
    }

    @PostConstruct
    private void init() {
        try (InputStream is = getClass()
                .getResourceAsStream("/version.properties")) {
            Properties appInfoProperties = new Properties();
            appInfoProperties.load(is);
            version = appInfoProperties.getProperty("app.version");
            appVersion = "Profile Cloner - Version " + version;
        } catch (Exception e) {
            LOG.warn("Error reading application info text file: {}",
                    e.getMessage());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(
                "JBoss AS 7 / WildFly / JBoss EAP 6  Profile (and more) Cloner version:"
                        + version);
        sb.append("\n");
        sb.append("Usage:\n");
        sb.append(
                " java -cp $JBOSS_HOME/bin/client/jboss-cli-client.jar:profilecloner.jar\n");
        sb.append("    org.jboss.tfonteyne.profilecloner.Main\n");
        sb.append(
                "    --controller=<host> --port=<number> --username=<user> --password=<password> \n");
        sb.append("    --file=<name> --add-deployments=<true|false>\n");
        sb.append(
                "    /from=value destinationvalue [/from=value destinationvalue] ....\n");
        sb.append("\n");
        sb.append("Options:\n");
        sb.append(
                "  --controller=<host> | -c <host>       : Defaults to the setting in jboss-cli.xml if you have one,\n");
        sb.append(
                "  --port=<port>                           or localhost and 9999 (wildfly:9990)\n");
        sb.append(
                "  --username=<user> | -u <user>         : When not set, $local authentication is attempted\n");
        sb.append("  --password=<password> | -p <password>\n");
        sb.append(
                "  --file=<name> | -f <name>             : The resulting CLI commands will be written to the file; if not set, they are output on the console\n");
        sb.append(
                "  --add-deployments=<true|false> | -ad  : By default cloning a server-group will skip the deployments\n");
        sb.append(
                "                                          If you first copy the content folder and clone the deployments, you can enable this\n");
        sb.append("\n");
        sb.append("Examples for \"/from=value destinationvalue\":\n");
        sb.append("  Domain mode:\n");
        sb.append(
                "    /socket-binding-group=full-ha-sockets full-ha-sockets-copy\n");
        sb.append("    /profile=full-ha full-ha-copy\n");
        sb.append("    /profile=full-ha/subsystem=web web\n");
        sb.append("\n");
        sb.append("  Standalone server:\n");
        sb.append("    /subsystem=security security\n");
        sb.append("    profile\n");
        sb.append(
                "   The latter being a shortcut to clone all subsystems in individual batches\n");
        sb.append("\n");
        sb.append(
                "Each set will generate a batch/run-batch. It is recommended to clone the profile last\n");
        sb.append(
                "The names from/to can be equal if you want to execute the script on a different controller.\n");
        sb.append("\n");
        sb.append("\n Secure connections need:");
        sb.append(
                "\n    -Djavax.net.ssl.trustStore=/path/to/store.jks -Djavax.net.ssl.trustStorePassword=password");
        sb.append("\n");

        usageString = sb.toString();
        sb = null;
    }

    /**
     * Simple test method.
     */
    @GET
    @Path("usage")
    @Produces(MediaType.TEXT_PLAIN)
    public String getUsage() {
        return usageString;
    }

    /**
     * Simple test method.
     */
    @GET
    @Path("version")
    public String getVersion() {
        return appVersion;
    }
}
