USE bus_crawler;

-- Run once after schema_extension.sql. Safe to rerun: the event table has a
-- unique key and the bridge checks for an existing station_bus_info row.
-- This uses previously imported bus_observation rows; JSONL is unchanged.
INSERT IGNORE INTO bus_station_event
  (snapshot_id, route_name, direction, sta_code, bus_code,
   event_type, event_basis, observed_at)
SELECT
  h.snapshot_id, h.route_name, h.direction, h.sta_code, h.bus_code,
  CASE WHEN h.status = '1' THEN 'arrival' ELSE 'departure' END,
  CASE
    WHEN h.previous_at IS NOT NULL
      AND TIMESTAMPDIFF(SECOND, h.previous_at, h.observed_at) BETWEEN 0 AND 1200
      THEN 'status_change'
    ELSE 'first_seen'
  END,
  h.observed_at
FROM (
  SELECT
    o.snapshot_id, o.route_name, o.direction, o.sta_code, o.bus_code,
    o.status, o.observed_at,
    LAG(o.status) OVER (
      PARTITION BY o.route_name, o.direction, o.sta_code, o.bus_code
      ORDER BY o.observed_at, o.id
    ) AS previous_status,
    LAG(o.observed_at) OVER (
      PARTITION BY o.route_name, o.direction, o.sta_code, o.bus_code
      ORDER BY o.observed_at, o.id
    ) AS previous_at
  FROM bus_observation AS o
  WHERE o.route_name = '51A'
) AS h
WHERE
  (
    h.status = '1'
    AND (
      h.previous_at IS NULL
      OR TIMESTAMPDIFF(SECOND, h.previous_at, h.observed_at) > 1200
      OR h.previous_status = '0'
    )
  )
  OR (
    h.status = '0'
    AND h.previous_status = '1'
    AND TIMESTAMPDIFF(SECOND, h.previous_at, h.observed_at) BETWEEN 0 AND 1200
  );

-- Also make the team's station_bus_info table usable for the historical
-- entry/exit display. It stores seconds, unlike the event table's microseconds.
INSERT INTO station_bus_info
  (sta_code, bus_code, status, passenger_flow, time)
SELECT e.sta_code, e.bus_code,
       IF(e.event_type = 'arrival', 1, 0),
       o.passenger_flow, e.observed_at
FROM bus_station_event AS e
LEFT JOIN bus_observation AS o
  ON o.snapshot_id = e.snapshot_id
 AND o.sta_code = e.sta_code
 AND o.bus_code = e.bus_code
WHERE NOT EXISTS (
  SELECT 1 FROM station_bus_info AS s
  WHERE s.sta_code = e.sta_code
    AND s.bus_code = e.bus_code
    AND s.status = IF(e.event_type = 'arrival', 1, 0)
    AND s.time = CAST(e.observed_at AS DATETIME)
);
