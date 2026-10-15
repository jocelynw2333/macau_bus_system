USE bus_crawler;

CREATE TABLE IF NOT EXISTS collection_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT,
  collected_at DATETIME(6) NOT NULL,
  route_name VARCHAR(10) NOT NULL,
  direction TINYINT NOT NULL,
  station_count INT NOT NULL DEFAULT 0,
  bus_count INT NOT NULL DEFAULT 0,
  raw_json JSON NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_snapshot_time_route_dir (collected_at, route_name, direction),
  KEY idx_snapshot_route_time (route_name, direction, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bus_observation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  snapshot_id BIGINT NOT NULL,
  route_name VARCHAR(10) NOT NULL,
  direction TINYINT NOT NULL,
  sta_code VARCHAR(20) NOT NULL,
  bus_code VARCHAR(10) NOT NULL,
  bus_plate VARCHAR(20) NULL,
  bus_type VARCHAR(50) NULL,
  status VARCHAR(20) NULL,
  passenger_flow VARCHAR(20) NULL,
  speed DECIMAL(8,2) NULL,
  latitude DECIMAL(10,7) NULL,
  longitude DECIMAL(10,7) NULL,
  observed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_observation (snapshot_id, sta_code, bus_code),
  KEY idx_observation_bus_time (bus_code, observed_at),
  KEY idx_observation_station_time (sta_code, observed_at),
  CONSTRAINT fk_observation_snapshot
    FOREIGN KEY (snapshot_id) REFERENCES collection_snapshot(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A station event is the first sampled arrival/departure state in a trip,
-- or a transition from the opposite state. Times are observation times.
CREATE TABLE IF NOT EXISTS bus_station_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  snapshot_id BIGINT NOT NULL,
  route_name VARCHAR(10) NOT NULL,
  direction TINYINT NOT NULL,
  sta_code VARCHAR(20) NOT NULL,
  bus_code VARCHAR(10) NOT NULL,
  event_type VARCHAR(12) NOT NULL,
  event_basis VARCHAR(20) NOT NULL,
  observed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_observation (snapshot_id, sta_code, bus_code),
  KEY idx_event_station_time (route_name, direction, sta_code, event_type, observed_at),
  KEY idx_event_bus_time (bus_code, observed_at),
  CONSTRAINT fk_event_snapshot FOREIGN KEY (snapshot_id)
    REFERENCES collection_snapshot(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
