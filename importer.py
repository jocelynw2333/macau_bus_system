import argparse
import json
import os
import time
from datetime import datetime
from pathlib import Path

import mysql.connector
from dotenv import load_dotenv
from events import classify_event

load_dotenv()


def connect():
    return mysql.connector.connect(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        port=int(os.getenv("DB_PORT", "3306")),
        database=os.getenv("DB_NAME", "bus_crawler"),
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
        autocommit=False,
    )


def mysql_datetime(value):
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is not None:
        parsed = parsed.astimezone().replace(tzinfo=None)
    return parsed


def first_value(source, *names):
    for name in names:
        value = source.get(name)
        if value not in (None, ""):
            return value
    return None


def insert_record(connection, record):
    route = str(record.get("route", "51A"))
    direction = int(record.get("direction", 0))
    observed_at = mysql_datetime(record["collected_at"])
    response = record.get("response") or {}
    data = response.get("data") or {}
    stations = data.get("routeInfo") or []
    observations = []

    for station in stations:
        if not isinstance(station, dict):
            continue
        sta_code = first_value(station, "staCode", "stopCode", "stationCode")
        for bus in station.get("busInfo") or []:
            if not isinstance(bus, dict):
                continue
            bus_code = first_value(bus, "busCode", "plate", "busPlate")
            if sta_code and bus_code:
                observations.append((str(sta_code), str(bus_code), bus))

    cursor = connection.cursor()
    try:
        cursor.execute(
            """
            INSERT INTO collection_snapshot
              (collected_at, route_name, direction, station_count, bus_count, raw_json)
            VALUES (%s, %s, %s, %s, %s, %s)
            ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)
            """,
            (
                observed_at,
                route,
                direction,
                len(stations),
                len(observations),
                json.dumps(record, ensure_ascii=False),
            ),
        )
        snapshot_id = cursor.lastrowid
        new_snapshot = cursor.rowcount == 1
        if not new_snapshot:
            connection.commit()
            return len(stations), len(observations)

        for sta_code, bus_code, bus in observations:
            cursor.execute(
                """
                INSERT INTO bus_info
                  (bus_code, bus_type, bus_plate, is_facilities)
                VALUES (%s, %s, %s, %s)
                ON DUPLICATE KEY UPDATE
                  bus_type=VALUES(bus_type),
                  bus_plate=VALUES(bus_plate),
                  is_facilities=VALUES(is_facilities)
                """,
                (
                    bus_code,
                    first_value(bus, "busType"),
                    first_value(bus, "busPlate", "plate"),
                    first_value(bus, "isFacilities"),
                ),
            )
            cursor.execute(
                """
                SELECT status, observed_at
                FROM bus_observation
                WHERE route_name=%s AND direction=%s AND sta_code=%s
                  AND bus_code=%s AND observed_at < %s
                ORDER BY observed_at DESC LIMIT 1
                """,
                (route, direction, sta_code, bus_code, observed_at),
            )
            previous = cursor.fetchone()
            event = classify_event(
                first_value(bus, "status"), observed_at,
                previous[0] if previous else None,
                previous[1] if previous else None,
            )
            cursor.execute(
                """
                INSERT IGNORE INTO bus_observation
                  (snapshot_id, route_name, direction, sta_code, bus_code,
                   bus_plate, bus_type, status, passenger_flow, speed,
                   latitude, longitude, observed_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    snapshot_id,
                    route,
                    direction,
                    sta_code,
                    bus_code,
                    first_value(bus, "busPlate", "plate"),
                    first_value(bus, "busType"),
                    first_value(bus, "status"),
                    first_value(bus, "passengerFlow"),
                    first_value(bus, "speed"),
                    first_value(bus, "lat", "latitude"),
                    first_value(bus, "lng", "longitude"),
                    observed_at,
                ),
            )
            if event:
                event_type, basis = event
                cursor.execute(
                    """
                    INSERT IGNORE INTO bus_station_event
                      (snapshot_id, route_name, direction, sta_code, bus_code,
                       event_type, event_basis, observed_at)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (snapshot_id, route, direction, sta_code, bus_code,
                     event_type, basis, observed_at),
                )
                if cursor.rowcount:
                    # The team's station_bus_info table receives event rows.
                    # Avoid putting every ten-second snapshot into that table.
                    cursor.execute(
                        """
                        INSERT INTO station_bus_info
                          (sta_code, bus_code, status, passenger_flow, time)
                        VALUES (%s, %s, %s, %s, %s)
                        """,
                        (sta_code, bus_code, int(first_value(bus, "status")),
                         first_value(bus, "passengerFlow"), observed_at),
                    )
        connection.commit()
        return len(stations), len(observations)
    except Exception:
        connection.rollback()
        raise
    finally:
        cursor.close()


def run(path, follow):
    state_path = path.with_suffix(path.suffix + ".offset")
    offset = 0
    if state_path.exists():
        try:
            offset = int(state_path.read_text(encoding="utf-8").strip())
        except ValueError:
            offset = 0

    connection = connect()
    try:
        while True:
            if not path.exists():
                if not follow:
                    raise FileNotFoundError(path)
                time.sleep(2)
                continue

            with path.open("r", encoding="utf-8") as file:
                if offset > path.stat().st_size:
                    offset = 0
                file.seek(offset)
                while True:
                    position = file.tell()
                    line = file.readline()
                    if not line:
                        offset = position
                        break
                    try:
                        record = json.loads(line)
                        station_count, bus_count = insert_record(connection, record)
                    except json.JSONDecodeError:
                        print(f"跳过无法解析的JSONL行，字节位置：{position}", flush=True)
                        offset = file.tell()
                        state_path.write_text(str(offset), encoding="utf-8")
                        continue
                    except mysql.connector.Error:
                        connection.close()
                        connection = connect()
                        file.seek(position)
                        time.sleep(2)
                        continue
                    offset = file.tell()
                    state_path.write_text(str(offset), encoding="utf-8")
                    print(
                        f"已导入 {record.get('collected_at')}："
                        f"{station_count} 个站点，{bus_count} 条车辆记录",
                        flush=True,
                    )
            if not follow:
                return
            time.sleep(2)
    finally:
        connection.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="将51A JSONL持续导入MySQL")
    parser.add_argument(
        "--path",
        type=Path,
        default=Path(os.getenv("JSONL_PATH", "data/51A_live.jsonl")),
    )
    parser.add_argument("--follow", action="store_true", help="持续等待新记录")
    args = parser.parse_args()
    run(args.path.resolve(), args.follow)
