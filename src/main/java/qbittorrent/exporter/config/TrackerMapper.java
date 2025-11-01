package qbittorrent.exporter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

/**
 * Maps tracker URLs to human-friendly names using configuration.
 */
public class TrackerMapper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerMapper.class);
    
    private final Map<String, String> trackerMappings;
    
    public TrackerMapper() {
        this.trackerMappings = ExporterConfig.getInstance().getTrackerMappings();
        LOGGER.info("Loaded {} tracker mappings", trackerMappings.size());
        if (!trackerMappings.isEmpty()) {
            LOGGER.debug("Tracker mappings: {}", trackerMappings);
        }
    }
    
    /**
     * Maps a tracker URL to a human-friendly name.
     * If no mapping exists, returns the original host or URL.
     */
    public String mapTracker(String trackerUrl) {
        if (trackerUrl == null || trackerUrl.trim().isEmpty()) {
            return "unknown";
        }
        
        try {
            String host = extractHost(trackerUrl);
            return trackerMappings.getOrDefault(host, host);
        } catch (Exception e) {
            LOGGER.debug("Failed to parse tracker URL: {}", trackerUrl, e);
            return trackerUrl;
        }
    }
    
    /**
     * Extracts the host from a tracker URL.
     */
    private String extractHost(String trackerUrl) {
        try {
            // Handle common tracker URL formats
            if (trackerUrl.startsWith("http://") || trackerUrl.startsWith("https://")) {
                URI uri = new URI(trackerUrl);
                return uri.getHost();
            } else if (trackerUrl.startsWith("udp://")) {
                // Handle UDP trackers
                String withoutProtocol = trackerUrl.substring(6); // Remove "udp://"
                int colonIndex = withoutProtocol.indexOf(':');
                if (colonIndex > 0) {
                    return withoutProtocol.substring(0, colonIndex);
                }
                return withoutProtocol;
            } else {
                // Fallback: try to find host-like pattern
                String[] parts = trackerUrl.split("/");
                if (parts.length > 0) {
                    String hostPart = parts[0];
                    // Remove port if present
                    int colonIndex = hostPart.indexOf(':');
                    if (colonIndex > 0) {
                        return hostPart.substring(0, colonIndex);
                    }
                    return hostPart;
                }
            }
        } catch (URISyntaxException e) {
            LOGGER.debug("Failed to parse URI: {}", trackerUrl, e);
        }
        
        return trackerUrl;
    }
    
    /**
     * Returns a copy of all loaded tracker mappings.
     */
    public Map<String, String> getMappings() {
        return Map.copyOf(trackerMappings);
    }
}