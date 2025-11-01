package qbittorrent.exporter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Maps tracker URLs to human-friendly names using configuration.
 */
public class TrackerMapper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(TrackerMapper.class);
    private static final String OTHER_KEY = "other";
    
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
     * Supports exact matching, regex patterns, and a special "other" fallback.
     * If no mapping exists, returns the original host or URL.
     */
    public String mapTracker(String trackerUrl) {
        if (trackerUrl == null || trackerUrl.trim().isEmpty()) {
            return "unknown";
        }
        
        try {
            String host = extractHost(trackerUrl);
            
            // First try exact match for backward compatibility and performance
            if (trackerMappings.containsKey(host)) {
                return trackerMappings.get(host);
            }
            
            // Then try regex matching (excluding the special "other" key)
            String regexMatch = findRegexMatch(host);
            if (regexMatch != null) {
                return regexMatch;
            }
            
            // Check for "other" fallback mapping
            if (trackerMappings.containsKey(OTHER_KEY)) {
                String otherMapping = trackerMappings.get(OTHER_KEY);
                LOGGER.debug("Using 'other' fallback mapping for host '{}' -> '{}'", host, otherMapping);
                return otherMapping;
            }
            
            // Return original host if no match found
            return host;
        } catch (Exception e) {
            LOGGER.debug("Failed to parse tracker URL: {}", trackerUrl, e);
            return trackerUrl;
        }
    }
    
    /**
     * Attempts to find a regex pattern match for the given host.
     * Returns the mapped name if a pattern matches, null otherwise.
     * Excludes the special "other" key from pattern matching.
     */
    private String findRegexMatch(String host) {
        for (Map.Entry<String, String> entry : trackerMappings.entrySet()) {
            String pattern = entry.getKey();
            String mappedName = entry.getValue();
            
            // Skip the special "other" key - it's handled separately as a fallback
            if (OTHER_KEY.equals(pattern)) {
                continue;
            }
            
            try {
                // Check if the pattern contains regex characters
                if (isRegexPattern(pattern)) {
                    Pattern compiledPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                    if (compiledPattern.matcher(host).find()) {
                        LOGGER.debug("Regex pattern '{}' matched host '{}' -> '{}'", pattern, host, mappedName);
                        return mappedName;
                    }
                } else {
                    // For non-regex patterns, try simple substring matching for convenience
                    // This allows patterns like "beyond-hd" to match "tracker.beyond-hd.me"
                    if (host.toLowerCase().contains(pattern.toLowerCase())) {
                        LOGGER.debug("Substring pattern '{}' matched host '{}' -> '{}'", pattern, host, mappedName);
                        return mappedName;
                    }
                }
            } catch (PatternSyntaxException e) {
                LOGGER.warn("Invalid regex pattern '{}': {}", pattern, e.getMessage());
                // Fall back to simple substring matching if regex is invalid
                if (host.toLowerCase().contains(pattern.toLowerCase())) {
                    LOGGER.debug("Fallback substring pattern '{}' matched host '{}' -> '{}'", pattern, host, mappedName);
                    return mappedName;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Determines if a pattern contains regex special characters.
     */
    private boolean isRegexPattern(String pattern) {
        // Check for common regex metacharacters
        return pattern.contains(".*") || 
               pattern.contains("\\") || 
               pattern.contains("^") || 
               pattern.contains("$") || 
               pattern.contains("[") || 
               pattern.contains("]") || 
               pattern.contains("(") || 
               pattern.contains(")") || 
               pattern.contains("{") || 
               pattern.contains("}") || 
               pattern.contains("|") || 
               pattern.contains("+") || 
               pattern.contains("?");
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