package qbittorrent.exporter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * General configuration loader for the qBittorrent exporter.
 * Loads configuration from YAML file and provides access to various settings.
 */
public class ExporterConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ExporterConfig.class);
    private static final String DEFAULT_CONFIG_PATH = "config.yaml";
    private static final String CONFIG_PATH_PROPERTY = "config.path";
    private static final String CONFIG_PATH_ENV_VAR = "CONFIG_PATH";
    
    private final Map<String, Object> configuration;
    private static ExporterConfig instance;
    
    private ExporterConfig() {
        this.configuration = loadConfiguration();
    }
    
    /**
     * Gets the singleton instance of ExporterConfig.
     */
    public static synchronized ExporterConfig getInstance() {
        if (instance == null) {
            instance = new ExporterConfig();
        }
        return instance;
    }
    
    /**
     * Gets a configuration value by path (e.g., "trackers.blutopia.cc" or "cache.duration").
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String path, T defaultValue) {
        try {
            String[] parts = path.split("\\.");
            Object current = configuration;
            
            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                    if (current == null) {
                        return defaultValue;
                    }
                } else {
                    return defaultValue;
                }
            }
            
            return (T) current;
        } catch (Exception e) {
            LOGGER.debug("Failed to get configuration value for path: {}", path, e);
            return defaultValue;
        }
    }
    
    /**
     * Gets a section of the configuration as a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSection(String section) {
        Object value = configuration.get(section);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }
    
    /**
     * Gets tracker mappings specifically.
     */
    public Map<String, String> getTrackerMappings() {
        Map<String, String> mappings = new HashMap<>();
        Map<String, Object> trackers = getSection("trackers");
        
        for (Map.Entry<String, Object> entry : trackers.entrySet()) {
            mappings.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        
        return mappings;
    }
    
    /**
     * Gets cache duration in milliseconds.
     */
    public long getCacheDurationMs() {
        Object value = get("cache.duration_ms", 5000L);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 5000L; // fallback default
    }
    
    /**
     * Gets whether to collect torrent info metrics.
     */
    public boolean shouldCollectTorrentInfo() {
        // System property takes precedence over config file
        String systemProperty = System.getProperty("qbt.collect.torrent.info");
        if (systemProperty != null) {
            return Boolean.parseBoolean(systemProperty);
        }
        return get("metrics.collect_torrent_info", true);
    }
    
    /**
     * Gets the metrics port.
     */
    public int getMetricsPort() {
        Object value = get("server.port", 17871);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 17871; // fallback default
    }
    
    /**
     * Gets whether to enable debug logging for configuration.
     */
    public boolean isDebugEnabled() {
        return get("logging.debug", false);
    }
        
    /**
     * Gets parallel API calls setting.
     */
    public boolean useParallelApiCalls() {
        return get("performance.parallel_api_calls", true);
    }
    
    /**
     * Returns a copy of the entire configuration.
     */
    public Map<String, Object> getAll() {
        return new HashMap<>(configuration);
    }
    
    /**
     * Loads configuration from YAML file.
     */
    private Map<String, Object> loadConfiguration() {
        Map<String, Object> config = new HashMap<>();
        
        String configPath = getConfigurationPath();
        Path configFile = Paths.get(configPath);
        
        try {
            if (Files.exists(configFile)) {
                LOGGER.info("Loading configuration from: {}", configFile.toAbsolutePath());
                loadFromFile(config, configFile);
            } else {
                LOGGER.info("Configuration file not found at: {}. Using defaults.", configFile.toAbsolutePath());
                loadDefaultConfiguration(config);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to load configuration from {}. Using defaults.", configPath, e);
            loadDefaultConfiguration(config);
        }
        
        LOGGER.info("Configuration loaded successfully");
        LOGGER.debug("Configuration: {}", config);
        
        return config;
    }
    
    /**
     * Determines the configuration file path from various sources in order of priority:
     * 1. System property (-Dconfig.path=...)
     * 2. Environment variable (CONFIG_PATH=...)
     * 3. Default path (config.yaml)
     */
    private String getConfigurationPath() {
        // 1. Check system property first (highest priority)
        String configPath = System.getProperty(CONFIG_PATH_PROPERTY);
        if (configPath != null && !configPath.trim().isEmpty()) {
            LOGGER.debug("Using config path from system property: {}", configPath);
            return configPath;
        }
        
        // 2. Check environment variable (medium priority)
        configPath = System.getenv(CONFIG_PATH_ENV_VAR);
        if (configPath != null && !configPath.trim().isEmpty()) {
            LOGGER.debug("Using config path from environment variable: {}", configPath);
            return configPath;
        }
        
        // 3. Use default path (lowest priority)
        LOGGER.debug("Using default config path: {}", DEFAULT_CONFIG_PATH);
        return DEFAULT_CONFIG_PATH;
    }
    
    /**
     * Loads configuration from a YAML file.
     */
    private void loadFromFile(Map<String, Object> config, Path configFile) throws IOException {
        try (InputStream inputStream = new FileInputStream(configFile.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> yamlConfig = yaml.load(inputStream);
            
            if (yamlConfig != null) {
                config.putAll(yamlConfig);
            }
        }
    }
    
    /**
     * Loads default configuration values.
     */
    private void loadDefaultConfiguration(Map<String, Object> config) {
        // Default tracker mappings
        Map<String, String> defaultTrackers = new HashMap<>();         // fallback for any unmatched trackers
        config.put("trackers", defaultTrackers);
        
        // Default cache settings
        Map<String, Object> cache = new HashMap<>();
        cache.put("duration_ms", 5000L);
        config.put("cache", cache);
        
        // Default metrics settings
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("collect_torrent_info", true);
        config.put("metrics", metrics);
        
        // Default server settings
        Map<String, Object> server = new HashMap<>();
        server.put("port", 17871);
        config.put("server", server);
        
        // Default performance settings
        Map<String, Object> performance = new HashMap<>();
        performance.put("parallel_api_calls", true);
        config.put("performance", performance);
        
        // Default logging settings
        Map<String, Object> logging = new HashMap<>();
        logging.put("debug", false);
        config.put("logging", logging);
        
        LOGGER.info("Using default configuration");
    }
}