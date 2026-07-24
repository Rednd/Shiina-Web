package dev.osunolimits.main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.osunolimits.modules.utils.ShiinaTemplateException;
import freemarker.cache.NullCacheStorage;
import freemarker.template.Configuration;
import spark.Spark;

public class WebServer extends Spark {

    private final Logger LOG = LoggerFactory.getLogger("WebServer");

    private static final List<String> ignoredPaths = new ArrayList<>();

    public static Configuration freemarkerCfg = new Configuration(Configuration.VERSION_2_3_23);

    public static void addIgnoredPath(String path) {
        if (!ignoredPaths.contains(path)) {
            ignoredPaths.add(path);
        }
    }

    public void setThreadPool(int minThreads, int maxThreads, int timeOutMillis) {
        threadPool(maxThreads, minThreads, timeOutMillis);
    }

    public void setTemplateUpdateDelay(int delay) {
        freemarkerCfg.setTemplateUpdateDelayMilliseconds(delay);
    }

    public void createDefaultDirectories() {
        if (new File("templates/").mkdirs()) {
            LOG.info("Created templates directory 'templates/'");
        }
        if (new File("static/").mkdirs()) {
            LOG.info("Created templates directory 'static/'");
        }
    }

    public void setDefaultDirectories() {
        Spark.staticFiles.externalLocation("static/");
    }

    public void ignite(Logger logger, String ip, int port, int updateDelay) throws Exception {
        ipAddress(ip);
        port(port);
        freemarkerCfg.setWhitespaceStripping(true);
        freemarkerCfg.setTemplateExceptionHandler(new ShiinaTemplateException());
        freemarkerCfg.setLogTemplateExceptions(false);
        freemarkerCfg.setDirectoryForTemplateLoading(new File("templates/"));
        freemarkerCfg.setTemplateUpdateDelayMilliseconds(updateDelay);
        freemarkerCfg.setAutoEscapingPolicy(Configuration.ENABLE_IF_SUPPORTED_AUTO_ESCAPING_POLICY);
        if(updateDelay == 0) {
            freemarkerCfg.setCacheStorage(new NullCacheStorage());
        }

        after((req, res) -> {
            res.header("Server", "ShiinaONL");

            if(ignoredPaths.contains(req.pathInfo())) {
                return;
            }

            LOG.info(req.ip()  + " | " + req.requestMethod() + " (" + res.status() + ") | " + req.pathInfo() + " | " + req.userAgent());
        });
        awaitInitialization();

        logger.info("WebServer ignited on -> " +   App.env.get("DOMAIN") + " (" + ip + ":" + port + ")");
    }

    /**
     * Properly shuts down the web server and releases all resources.
     * This method ensures that the Spark server and all associated resources are
     * properly cleaned up to prevent port binding issues on restart.
     */
    public void shutdown() {
        LOG.debug("Performing thorough web server shutdown sequence");
        
        try {
            // Get port for debugging
            int currentPort = Spark.port();
            LOG.debug("Shutting down server on port: " + currentPort);
     
            // Stop accepting new requests
            Spark.stop();
            LOG.debug("Stopped accepting new requests");

            // Allow current requests to finish processing
            LOG.debug("Waiting for ongoing requests to complete...");
            Spark.awaitStop();
            
            System.gc();
            
            LOG.debug("Web server shutdown complete");
        } catch (Exception e) {
            LOG.error("Error during web server shutdown: " + e.getMessage(), e);
        }
    }

    public static void unregister(String route) {
        Spark.unmap(route);
        App.log.debug("Unregistered route: " + route);
    }
}
