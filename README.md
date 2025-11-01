# qbittorrent-exporter

![Gradle Build](https://github.com/markhc/qbittorrent-exporter/actions/workflows/build.yaml/badge.svg) ![Release](https://github.com/markhc/qbittorrent-exporter/actions/workflows/release.yaml/badge.svg)

<img src="https://github.com/caseyscarborough/qbittorrent-grafana-dashboard/blob/master/images/logo.png" width=100> <img src="https://github.com/caseyscarborough/qbittorrent-grafana-dashboard/blob/master/images/prometheus.png" width=100>

This app is a Prometheus exporter for the qBittorrent application. You must have version 4.1.0 of qBittorrent or higher for this plugin to work.

This is especially useful when integrated with
the [qbittorrent-grafana-dashboard](https://github.com/caseyscarborough/qbittorrent-grafana-dashboard).

See it on [DockerHub](https://hub.docker.com/r/markhc/qbittorrent-exporter).

## Usage

### Docker

```bash
docker run \
    --name=qbittorrent-exporter \
    -e QBITTORRENT_USERNAME=username \
    -e QBITTORRENT_PASSWORD=password \
    -e QBITTORRENT_BASE_URL=http://localhost:8080 \
    -e CONFIG_PATH=/config/config.yaml \
    -v /path/to/config:/config \
    -p 17871:17871 \
    caseyscarborough/qbittorrent-exporter:latest
```

### Docker Compose

```yaml
version: '3.8'
services:
  qbittorrent-exporter:
    image: caseyscarborough/qbittorrent-exporter:latest
    container_name: qbittorrent-exporter
    environment:
      - QBITTORRENT_USERNAME=username
      - QBITTORRENT_PASSWORD=password
      - QBITTORRENT_BASE_URL=http://qbittorrent:8080
      - CONFIG_PATH=/config/config.yaml
    volumes:
      - ./config:/config
    ports:
      - "17871:17871"
    restart: unless-stopped
```

## Parameters

|         Parameter         |                               Function                               |      Default Value      |
|:-------------------------:|:--------------------------------------------------------------------:|:-----------------------:|
|        `-p 17871`         |                         The webservice port.                         |           N/A           |
| `-e QBITTORRENT_USERNAME` |                      The qBittorrent username.                       |         `admin`         |
| `-e QBITTORRENT_PASSWORD` |                      The qBittorrent password.                       |      `adminadmin`       |
| `-e QBITTORRENT_BASE_URL` |                      The qBittorrent base URL.                       | `http://localhost:8080` |
|   `-e QBITTORRENT_HOST`   |   The qBittorrent host. Ignored when using `QBITTORRENT_BASE_URL`.   |       `localhost`       |
|   `-e QBITTORRENT_PORT`   |   The qBittorrent port. Ignored when using `QBITTORRENT_BASE_URL`.   |         `8080`          |
| `-e QBITTORRENT_PROTOCOL` | The qBittorrent protocol. Ignored when using `QBITTORRENT_BASE_URL`. |         `http`          |
|   `-e CONFIG_PATH`    |              Path to the YAML configuration file.                    |       `config.yaml`     |

## Configuration

The exporter supports configuration through a YAML file for various settings including tracker name mapping, caching, and metrics collection.

### Configuration File

Create a `config.yaml` file in the same directory as the application, or specify a custom path using:

**Environment Variable (recommended for Docker):**
```bash
export CONFIG_PATH=/path/to/config.yaml
```

**System Property:**
```bash
-Dconfig.path=/path/to/config.yaml
```

**Configuration precedence** (highest to lowest):
1. System property (`-Dconfig.path`)
2. Environment variable (`CONFIG_PATH`)
3. Default path (`config.yaml`)

**Example configuration:**

```yaml
# qBittorrent Exporter Configuration

# Server configuration
server:
  port: 17871

# Cache configuration  
cache:
  duration_ms: 5000  # Cache duration in milliseconds

# Metrics configuration
metrics:
  collect_torrent_info: true  # Collect expensive torrent_info metric

# Tracker name mappings
# Supports exact match, regex patterns, and substring matching
trackers:
  # Exact matches
  blutopia.cc: BLU
  passthepopcorn.me: PTP
  
  # Substring patterns (matches subdomains)
  beyond-hd: BHD                    # matches tracker.beyond-hd.me
  torrentleech: TL                  # matches tracker.torrentleech.org
  
  # Regex patterns
  ".*\\.thepiratebay\\..*": TPB     # matches any thepiratebay subdomain
  ".*1337x.*": 1337x                # matches any domain containing 1337x
```

#### Tracker Matching Types

The tracker mapping supports four types of pattern matching (in order of priority):

1. **Exact Match** (highest priority, best performance):
   ```yaml
   tracker.beyond-hd.me: BHD
   ```

2. **Regex Patterns** (most flexible):
   ```yaml
   ".*\\.beyond-hd\\..*": BHD        # matches any beyond-hd subdomain
   "^tracker\\..*": TRACKER          # matches any tracker.* domain
   torrentleech|tleechreload: TL     # matches either pattern
   ```

3. **Substring Match** (convenient for subdomains):
   ```yaml
   beyond-hd: BHD                    # matches tracker.beyond-hd.me, beyond-hd.me, etc.
   torrentleech: TL                  # matches tracker.torrentleech.org, www.torrentleech.org
   ```

4. **Special "other" Fallback** (lowest priority):
   ```yaml
   other: Unknown                    # fallback for any unmatched trackers
   ```

**Examples:**
- `beyond-hd` matches: `tracker.beyond-hd.me`, `beyond-hd.me`, `www.beyond-hd.me`
- `".*\\.1337x\\..*"` matches: `www.1337x.to`, `tracker.1337x.to`
- `torrentleech|tleechreload` matches: `tracker.torrentleech.org`, `tleechreload.com`
- `other: Unknown` catches: any tracker not matched by other patterns

### System Properties & Environment Variables

Configuration can be controlled via multiple methods with the following precedence (highest to lowest):

1. **System properties** (highest priority):
   - `-Dconfig.path=path/to/config.yaml` - Custom path to configuration file
   - `-Dqbt.collect.torrent.info=false` - Disable the expensive torrentInfo metric

2. **Environment variables** (medium priority):
   - `CONFIG_PATH=path/to/config.yaml` - Custom path to configuration file

3. **YAML configuration file** (lower priority)

4. **Built-in defaults** (lowest priority)

### Default Values

If no configuration file is found, the exporter will use these defaults:
- **Cache duration**: 5000ms (5 seconds)
- **Metrics port**: 17871
- **Collect torrent info**: true
- **Default tracker mappings**: Common public trackers (OpenBT, TPB, 1337x, Nyaa, RARBG)

## Setup

Add the target to your `scrape_configs` in your `prometheus.yml` configuration file of your Prometheus server.

```yml
scrape_configs:

  - job_name: 'qbittorrent'
    static_configs:

      # Update your IP address and port here
      - targets: [ '192.168.1.100:17871' ]
```

## Building Locally

Build the app and the docker container using the following commands:

```bash
./gradlew build
docker build .
```
