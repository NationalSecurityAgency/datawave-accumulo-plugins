package datawave.prometheus;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import io.prometheus.client.Gauge;
import org.apache.accumulo.core.spi.metrics.MeterRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

public class DatawavePrometheusMeterRegistry implements MeterRegistryFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DatawavePrometheusMeterRegistry.class);
    public static final String SERVER_PORT = "prometheus.registry.port";

    @Override
    public MeterRegistry create(MeterRegistryFactory.InitParameters params) {
        Map<String,String> metricsProps = new HashMap();

        LOG.info("Creating logging metrics registry with params: {}", params);
        metricsProps.putAll(params.getOptions());
        int PORT = Integer.parseInt(metricsProps.getOrDefault(SERVER_PORT, "10200"));

        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        Gauge gauge = Gauge.build().name("test_metric").help("test help").register(prometheusRegistry.getPrometheusRegistry());
        gauge.inc();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/prometheus", httpExchange -> {
                try {
                    LOG.info("Getting ready to scrape registry");
                    String response = prometheusRegistry.scrape();
                    LOG.info("Scraping registry");
                    httpExchange.sendResponseHeaders(200, response.getBytes().length);
                    try (OutputStream os = httpExchange.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }
                catch (Throwable e) {
                    LOG.info("SCRAPE EXCEPTION" + e.getMessage(), e);
                }
            });
            server.createContext("/testonly", httpExchange -> {
                LOG.info("Getting ready to scrape registry");
                String response = "Test Response";
                LOG.info("Scraping registry");
                httpExchange.sendResponseHeaders(200, response.getBytes().length);
                try (OutputStream os = httpExchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });

            new Thread(server::start).start();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        LOG.info("Defined new prometheusRegistry: {}", prometheusRegistry.getMeters());

        return prometheusRegistry;
    }
}
