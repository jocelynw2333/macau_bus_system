"""Fill the team's route/station tables from the newest 51A snapshot.

Station names remain for the station-data teammate to enrich.
"""

import json

from importer import connect


def main():
    connection = connect()
    cursor = connection.cursor()
    try:
        cursor.execute(
            """
            SELECT raw_json FROM collection_snapshot
            WHERE route_name='51A' AND direction=0
            ORDER BY collected_at DESC LIMIT 1
            """
        )
        row = cursor.fetchone()
        if row is None:
            raise RuntimeError("没有51A快照，无法同步站点目录")
        record = row[0] if isinstance(row[0], dict) else json.loads(row[0])
        stations = record["response"]["data"]["routeInfo"]
        route_code = "51A".zfill(5)
        cursor.execute(
            """
            INSERT IGNORE INTO route_info
              (route_code, route_name, direction)
            VALUES (%s, %s, %s)
            """,
            (route_code, "51A", 0),
        )
        for index, station in enumerate(stations, start=1):
            code = str(station["staCode"])
            cursor.execute(
                "INSERT IGNORE INTO station_info (sta_code) VALUES (%s)",
                (code,),
            )
            cursor.execute(
                """
                SELECT id FROM route_station_info
                WHERE route_code=%s AND direction=0 AND sta_code=%s
                ORDER BY id LIMIT 1
                """,
                (route_code, code),
            )
            found = cursor.fetchone()
            if found:
                cursor.execute(
                    "UPDATE route_station_info SET sta_index=%s WHERE id=%s",
                    (index, found[0]),
                )
            else:
                cursor.execute(
                    """
                    INSERT INTO route_station_info
                      (route_code, sta_code, direction, sta_index)
                    VALUES (%s, %s, 0, %s)
                    """,
                    (route_code, code, index),
                )
        connection.commit()
        print(f"已同步 51A 方向0：{len(stations)} 个站点。站名可由组员继续补充。")
    except Exception:
        connection.rollback()
        raise
    finally:
        cursor.close()
        connection.close()


if __name__ == "__main__":
    main()
