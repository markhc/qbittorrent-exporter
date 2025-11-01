package qbittorrent.exporter.handler;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qbittorrent.api.ApiClient;
import qbittorrent.api.model.MainData;
import qbittorrent.api.model.Preferences;
import qbittorrent.api.model.ServerState;
import qbittorrent.api.model.Torrent;
import qbittorrent.exporter.collector.QbtCollector;
import qbittorrent.exporter.config.TrackerMapper;
import qbittorrent.exporter.config.ExporterConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class QbtHttpHandler implements HttpHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QbtHttpHandler.class);
    private static final String CONTENT_TYPE = "text/plain;charset=utf-8";
    
    private final PrometheusMeterRegistry registry;
    private final QbtCollector collector;
    private final ApiClient client;
    private final TrackerMapper trackerMapper;
    private final ExporterConfig config;
    
    // Caching fields
    private volatile long lastUpdateTime = 0;
    private volatile String cachedMetrics = null;

    public QbtHttpHandler(final ApiClient client) {
        this.client = client;
        this.config = ExporterConfig.getInstance();
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        this.collector = new QbtCollector();
        this.trackerMapper = new TrackerMapper();
        this.registry.getPrometheusRegistry().register(collector);
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) {
        try {
            // Check cache first
            final long currentTime = System.currentTimeMillis();
            final long cacheDuration = config.getCacheDurationMs();
            if (cachedMetrics != null && (currentTime - lastUpdateTime) < cacheDuration) {
                LOGGER.debug("Serving cached metrics ({}ms old)", currentTime - lastUpdateTime);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE);
                exchange.getResponseSender().send(cachedMetrics);
                return;
            }

            LOGGER.info("Beginning prometheus metrics collection...");
            final long start = System.nanoTime();
            
            collectMetrics();
            
            final String metrics = registry.scrape();
            
            // Update cache
            cachedMetrics = metrics;
            lastUpdateTime = currentTime;
            
            final long duration = (System.nanoTime() - start) / 1_000_000;
            LOGGER.info("Completed in {}ms", duration);
            
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE);
            exchange.getResponseSender().send(metrics);
        } catch (Exception e) {
            LOGGER.error("An error occurred calling API", e);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, CONTENT_TYPE);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("An error occurred. " + e.getMessage());
        }
    }
    
    private void collectMetrics() throws Exception {
        CompletableFuture<List<Torrent>> torrentsFuture = CompletableFuture.supplyAsync(() -> client.getTorrents());
        CompletableFuture<Preferences> preferencesFuture = CompletableFuture.supplyAsync(() -> client.getPreferences());
        CompletableFuture<MainData> mainDataFuture = CompletableFuture.supplyAsync(() -> client.getMainData());
        CompletableFuture<String> versionFuture = CompletableFuture.supplyAsync(() -> client.getVersion());
        
        // Wait for all API calls to complete
        final List<Torrent> torrents = torrentsFuture.get();
        final Preferences preferences = preferencesFuture.get();
        final MainData data = mainDataFuture.get();
        final String version = versionFuture.get();
        final ServerState serverState = data.getServerState();
        
        collector.clear();
        
        // Set basic app metrics
        collector.setAppVersion(version);
        collector.setTotalTorrents(torrents.size());
        
        // Set global metrics
        collector.setGlobalAlltimeDownloadedBytes(serverState.getAlltimeDl());
        collector.setGlobalAlltimeUploadedBytes(serverState.getAlltimeUl());
        collector.setGlobalSessionDownloadedBytes(serverState.getDlInfoData());
        collector.setGlobalSessionUploadedBytes(serverState.getUpInfoData());
        collector.setGlobalDownloadSpeedBytes(serverState.getDlInfoSpeed());
        collector.setGlobalUploadSpeedBytes(serverState.getUpInfoSpeed());
        collector.setGlobalRatio(Double.parseDouble(serverState.getGlobalRatio()));
        collector.setAppDownloadRateLimitBytes(serverState.getDlRateLimit());
        collector.setAppUploadRateLimitBytes(serverState.getUpRateLimit());
        collector.setAppAlternateDownloadRateLimitBytes(preferences.getAltDlLimit());
        collector.setAppAlternateUploadRateLimitBytes(preferences.getAltUpLimit());
        collector.setAppAlternateRateLimitsEnabled(serverState.isUseAltSpeedLimits());
        collector.setAppMaxActiveDownloads(preferences.getMaxActiveDownloads());
        collector.setAppMaxActiveUploads(preferences.getMaxActiveUploads());
        collector.setAppMaxActiveTorrents(preferences.getMaxActiveTorrents());

        var stateCountMap = new java.util.HashMap<String, Long>();
        var trackerCountMap = new java.util.HashMap<String, Long>();
        
        for (Torrent torrent : torrents) {
            String name = torrent.getName();
            String state = torrent.getState();
            String tracker = torrent.getTracker();
            String mappedTracker = trackerMapper.mapTracker(tracker);
            
            // Set torrent metrics in single pass
            collector.setTorrentDownloadSpeedBytes(name, torrent.getDownloadSpeed());
            collector.setTorrentUploadSpeedBytes(name, torrent.getUploadSpeed());
            collector.setTorrentDownloadSpeedBytesByTracker(name, mappedTracker, torrent.getDownloadSpeed());
            collector.setTorrentUploadSpeedBytesByTracker(name, mappedTracker, torrent.getUploadSpeed());
            collector.setTorrentTotalDownloadedBytes(name, torrent.getDownloaded());
            collector.setTorrentSessionDownloadedBytes(name, torrent.getDownloadedSession());
            collector.setTorrentTotalUploadedBytes(name, torrent.getUploaded());
            collector.setTorrentSessionUploadedBytes(name, torrent.getUploadedSession());
            collector.setTorrentEta(name, torrent.getEta());
            collector.setTorrentProgress(name, torrent.getProgress());
            collector.setTorrentTimeActive(name, torrent.getTimeActive());
            collector.setTorrentSeeders(name, torrent.getNumSeeds());
            collector.setTorrentLeechers(name, torrent.getNumLeechs());
            collector.setTorrentRatio(name, torrent.getRatio());
            collector.setTorrentAmountLeftBytes(name, torrent.getAmountLeft());
            collector.setTorrentSizeBytes(name, torrent.getSize());
            
            // The torrentInfo metric can be expensive with many labels - make it optional
            if (config.shouldCollectTorrentInfo()) {
                collector.setTorrentInfo(torrent, mappedTracker);
            }
            
            stateCountMap.merge(state, 1L, Long::sum);
            trackerCountMap.merge(mappedTracker, 1L, Long::sum);
        }

        // Set aggregate counts
        stateCountMap.forEach(collector::setTorrentStates);
        trackerCountMap.forEach(collector::setTorrentTrackers);
    }
}
