/**
 *
 */
package org.jboss.tfonteyne.profilecloner;

import javax.enterprise.inject.spi.CDI;

import org.jboss.resteasy.cdi.CdiInjectorFactory;
import org.jboss.resteasy.cdi.ResteasyCdiExtension;
import org.jboss.resteasy.plugins.server.netty.cdi.CdiNettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class Server. Starts the profile cloner in server mode using Netty +
 * WeldSE + Resteasy
 *
 * @author Andrea Battaglia
 */
public final class Server {
    /**
     * Logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final int DEFAULT_REST_PORT = 7779;
    private static int port;

    public static void main(String[] args) {
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = DEFAULT_REST_PORT;
            LOG.debug("No custom rest port specified, using default: {}",
                    DEFAULT_REST_PORT);
        }
        new Server().startup();
    }

    public Server() {
    }

    private Weld weld = null;
    private ShutdownHook shutdownHook;
    private WeldContainer container = null;
    private ResteasyCdiExtension resteasyCdiExtension = null;
    private CdiNettyJaxrsServer cdiNettyJaxrsServer = null;
    private ResteasyDeployment resteasyDeployment = null;

    public void startup() {
        LOG.info("Initialization started...");
        weld = new Weld();
        cdiNettyJaxrsServer = new CdiNettyJaxrsServer();
        shutdownHook = new ShutdownHook(weld, cdiNettyJaxrsServer);
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        LOG.info("Starting CDI container...");
        container = weld.initialize();
        LOG.info("CDI container started...");
        resteasyCdiExtension = CDI.current().select(ResteasyCdiExtension.class)
                .get();
        resteasyDeployment = new ResteasyDeployment();
        resteasyDeployment
                .setActualResourceClasses(resteasyCdiExtension.getResources());
        resteasyDeployment
                .setInjectorFactoryClass(CdiInjectorFactory.class.getName());
        resteasyDeployment.getActualProviderClasses()
                .addAll(resteasyCdiExtension.getProviders());
        cdiNettyJaxrsServer.setDeployment(resteasyDeployment);
        cdiNettyJaxrsServer.setPort(port);
        cdiNettyJaxrsServer.setRootResourcePath("/profilecloner");
        LOG.info("Starting REST server...");
        cdiNettyJaxrsServer.start();
        LOG.info("REST server started...");

        LOG.info("Initialization completed...");
    }

    /**
     * Shut down Weld immediately. Removes shutdown hook. Blocks until Weld is
     * completely shut down.
     */
    public void shutdownNow() {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdownHook.run();
    }

    static class ShutdownHook extends Thread {
        private static final Logger LOG = LoggerFactory
                .getLogger(ShutdownHook.class);

        private final Weld weld;
        private CdiNettyJaxrsServer cdiNettyJaxrsServer;

        ShutdownHook(Weld weld, CdiNettyJaxrsServer cdiNettyJaxrsServer) {
            this.weld = weld;
            this.cdiNettyJaxrsServer = cdiNettyJaxrsServer;
        }

        @Override
        public void run() {
            LOG.info("Shutdown started...");

            LOG.info("Stopping REST server...");
            cdiNettyJaxrsServer.stop();
            LOG.info("REST server stopped...");

            LOG.info("Stopping CDI container...");
            weld.shutdown();
            LOG.info("CDI container stopped...");

            LOG.info("Shutdown completed...");
        }
    }

}