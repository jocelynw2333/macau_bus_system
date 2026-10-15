import os
import json
import logging
from datetime import datetime, timedelta
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen
from zoneinfo import ZoneInfo

import mysql.connector
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from arrival_estimate import estimate_window
from segment_time import summarize_segments

load_dotenv()
BASE_DIR = Path(__file__).resolve().parent
app = FastAPI(title="澳门公交51A数据系统")
TEAM_BACKEND_URL = os.getenv("TEAM_BACKEND_URL", "").rstrip("/")
logger = logging.getLogger(__name__)


def team_backend_get(path):
    if not TEAM_BACKEND_URL:
        return None
    try:
        with urlopen(f"{TEAM_BACKEND_URL}{path}", timeout=2) as response:
            return json.load(response)
    except (URLError, TimeoutError, ValueError) as exc:
        logger.warning("Team backend unavailable; using local read API: %s", exc)
        return None


def connect():
    return mysql.connector.connect(
        host=os.getenv("DB_HOST", "127.0.0.1"),
        port=int(os.getenv("DB_PORT", "3306")),
        database=os.getenv("DB_NAME", "bus_crawler"),
        user=os.environ["DB_USER"],
        password=os.environ["DB_PASSWORD"],
    )


def query(sql, params=()):
    connection = connect()
    cursor = connection.cursor(dictionary=True)
    try:
        cursor.execute(sql, params)
        return cursor.fetchall()
    finally:
        cursor.close()
        connection.close()


@app.get("/api/health")
def health():
    try:
        rows = query("SELECT COUNT(*) AS snapshots FROM collection_snapshot")
        return {"ok": True, "snapshots": rows[0]["snapshots"]}
    except mysql.connector.Error as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@app.get("/api/backend/status")
def backend_status():
    team = team_backend_get("/api/team/health")
    if team and team.get("ok"):
        return {"active": "team-java", "team": team}
    return {"active": "python", "team": None}


@app.get("/api/routes/51A/latest")
def latest():
    team = team_backend_get("/api/team/routes/51A/latest")
    if team and all(key in team for key in ("collected_at", "buses", "stations")):
        return team
    snapshots = query(
        """
        SELECT id, collected_at, route_name, direction, station_count, bus_count
        FROM collection_snapshot
        WHERE route_name='51A'
        ORDER BY collected_at DESC
        LIMIT 1
        """
    )
    if not snapshots:
        raise HTTPException(status_code=404, detail="还没有采集数据")
    snapshot = snapshots[0]
    snapshot["buses"] = query(
        """
        SELECT sta_code, bus_code, bus_plate, bus_type, status,
               passenger_flow, speed, latitude, longitude
        FROM bus_observation
        WHERE snapshot_id=%s
        ORDER BY sta_code, bus_code
        """,
        (snapshot["id"],),
    )
    bus_codes = sorted({row["bus_code"] for row in snapshot["buses"] if row["bus_code"]})
    if bus_codes:
        placeholders = ", ".join(["%s"] * len(bus_codes))
        recent_events = query(
            f"""
            SELECT sta_code, bus_code, event_type, observed_at
            FROM bus_station_event
            WHERE route_name='51A' AND direction=%s
              AND bus_code IN ({placeholders})
              AND observed_at BETWEEN %s AND %s
            ORDER BY observed_at DESC
            """,
            (snapshot["direction"], *bus_codes,
             snapshot["collected_at"] - timedelta(minutes=30), snapshot["collected_at"]),
        )
        latest_event = {}
        for event in recent_events:
            latest_event.setdefault((event["sta_code"], event["bus_code"]), event)
        for bus in snapshot["buses"]:
            event = latest_event.get((bus["sta_code"], bus["bus_code"]))
            bus["departed_at"] = (
                event["observed_at"].isoformat()
                if str(bus["status"]) == "0" and event and event["event_type"] == "departure"
                else None
            )
    raw = query("SELECT raw_json FROM collection_snapshot WHERE id=%s", (snapshot["id"],))
    raw_record = raw[0]["raw_json"] if raw else {}
    if isinstance(raw_record, str):
        raw_record = json.loads(raw_record)
    station_rows = (
        raw_record.get("response", {}).get("data", {}).get("routeInfo", [])
        if isinstance(raw_record, dict) else []
    )
    codes = [str(row["staCode"]) for row in station_rows if isinstance(row, dict) and row.get("staCode")]
    names = {}
    if codes:
        placeholders = ", ".join(["%s"] * len(codes))
        for row in query(
            f"SELECT sta_code, sta_zh_tw, sta_zh_cn FROM station_info WHERE sta_code IN ({placeholders})",
            tuple(codes),
        ):
            names[row["sta_code"]] = row["sta_zh_tw"] or row["sta_zh_cn"]
    snapshot["stations"] = [
        {"code": code, "name": names.get(code) or code, "index": index + 1}
        for index, code in enumerate(codes)
    ]
    snapshot["collected_at"] = snapshot["collected_at"].isoformat()
    return snapshot


@app.get("/api/routes/51A/segment-times")
def segment_times():
    geo = json.loads((BASE_DIR / "static" / "route-51a.geo.json").read_text(encoding="utf-8"))
    codes = [station["code"] for station in geo["stations"]]
    now = datetime.now(ZoneInfo("Asia/Macau")).replace(tzinfo=None)
    rows = query(
        """
        SELECT bus_code, sta_code, event_type, observed_at
        FROM bus_station_event
        WHERE route_name='51A' AND direction=0
          AND observed_at BETWEEN %s AND %s
        ORDER BY bus_code, observed_at
        """,
        (now - timedelta(days=14), now),
    )
    return {"route": "51A", "direction": 0, "days": 14,
            "segments": summarize_segments(rows, codes)}


@app.get("/api/routes/51A/history")
def history(limit: int = 100):
    limit = min(max(limit, 1), 1000)
    rows = query(
        """
        SELECT collected_at, station_count, bus_count
        FROM collection_snapshot
        WHERE route_name='51A'
        ORDER BY collected_at DESC
        LIMIT %s
        """,
        (limit,),
    )
    for row in rows:
        row["collected_at"] = row["collected_at"].isoformat()
    return rows


@app.get("/api/routes/51A/stations/{sta_code}/events")
def station_events(sta_code: str, limit: int = 100):
    limit = min(max(limit, 1), 500)
    rows = query(
        """
        SELECT bus_code, event_type, event_basis, observed_at
        FROM bus_station_event
        WHERE route_name='51A' AND direction=0 AND sta_code=%s
        ORDER BY observed_at DESC LIMIT %s
        """,
        (sta_code, limit),
    )
    for row in rows:
        row["observed_at"] = row["observed_at"].isoformat()
    return rows


@app.get("/api/routes/51A/stations/{sta_code}/arrival-window")
def arrival_window(sta_code: str):
    now = datetime.now(ZoneInfo("Asia/Macau")).replace(tzinfo=None)
    rows = query(
        """
        SELECT observed_at FROM bus_station_event
        WHERE route_name='51A' AND direction=0 AND sta_code=%s
          AND event_type='arrival' AND observed_at BETWEEN %s AND %s
        ORDER BY observed_at
        """,
        (sta_code, now - timedelta(days=14), now),
    )
    estimate = estimate_window([row["observed_at"] for row in rows], now)
    if estimate is None:
        return {
            "station": sta_code, "available": False,
            "reason": "同站点近期到车记录不足，暂时无法可靠估计。",
        }
    return {
        "station": sta_code, "available": True,
        **{
            key: value.isoformat() if isinstance(value, datetime) else value
            for key, value in estimate.items()
        },
    }


app.mount("/static", StaticFiles(directory=BASE_DIR / "static"), name="static")


@app.get("/")
def index():
    return FileResponse(BASE_DIR / "static" / "index.html")
