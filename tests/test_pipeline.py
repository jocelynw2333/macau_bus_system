import json
import unittest
from datetime import datetime, timedelta

from arrival_estimate import estimate_window
from events import classify_event
from regex_extract import extract_bus_response
from segment_time import summarize_segments


class PipelineTest(unittest.TestCase):
    def test_regex_extraction_keeps_existing_record_contract(self):
        response = {
            "data": {
                "lastBusType": "1",
                "routeInfo": [
                    {"staCode": "M93", "busInfo": [
                        {"busType": "1", "busCode": "HE122",
                         "busPlate": "AA\"7687", "status": "1",
                         "isFacilities": "0", "passengerFlow": "-1",
                         "speed": "2", "lat": "22.123",
                         "lng": "113.456"}
                    ]},
                    {"staCode": "M108/1", "busInfo": []},
                ],
            },
            "header": "000",
        }
        raw = json.dumps(response, ensure_ascii=False, separators=(",", ":"))
        self.assertEqual(extract_bus_response(raw), response)

    def test_first_seen_and_transition_are_distinct(self):
        now = datetime(2026, 10, 15, 10, 0)
        self.assertEqual(classify_event("1", now), ("arrival", "first_seen"))
        self.assertIsNone(classify_event("1", now, "1", now - timedelta(seconds=10)))
        self.assertEqual(
            classify_event("0", now, "1", now - timedelta(seconds=10)),
            ("departure", "status_change"),
        )
        self.assertIsNone(
            classify_event("0", now, "1", now - timedelta(hours=1))
        )

    def test_estimate_requires_recent_and_sufficient_data(self):
        now = datetime(2026, 10, 15, 10, 30)
        arrivals = []
        for day in range(7):
            base = now - timedelta(days=day)
            arrivals.extend([
                base.replace(hour=9, minute=55),
                base.replace(hour=10, minute=15),
                base.replace(hour=10, minute=35),
            ])
        result = estimate_window(arrivals, now)
        self.assertIsNotNone(result)
        self.assertLess(result["earliest"], result["latest"] + timedelta(seconds=1))
        self.assertEqual(result["arrive_by"], result["earliest"] - timedelta(minutes=2))
        self.assertIsNone(estimate_window(arrivals[:-3], now + timedelta(hours=2)))

    def test_segment_time_only_pairs_adjacent_stops_in_same_bus_trip(self):
        base = datetime(2026, 10, 15, 9, 0)
        events = []
        for index, minutes in enumerate([2, 3, 4, 5, 6]):
            start = base + timedelta(hours=index)
            events.extend([
                {"bus_code": "HE01", "sta_code": "M93", "event_type": "departure", "observed_at": start},
                {"bus_code": "HE01", "sta_code": "M108/1", "event_type": "arrival",
                 "observed_at": start + timedelta(minutes=minutes)},
            ])
        # Different bus, skipped stop and long gaps must not become samples.
        events.extend([
            {"bus_code": "HE02", "sta_code": "M93", "event_type": "departure", "observed_at": base},
            {"bus_code": "HE03", "sta_code": "M108/1", "event_type": "arrival", "observed_at": base + timedelta(minutes=2)},
            {"bus_code": "HE02", "sta_code": "M60", "event_type": "arrival", "observed_at": base + timedelta(minutes=3)},
            {"bus_code": "HE02", "sta_code": "M93", "event_type": "departure", "observed_at": base + timedelta(hours=1)},
            {"bus_code": "HE02", "sta_code": "M108/1", "event_type": "arrival", "observed_at": base + timedelta(hours=2)},
        ])
        result = summarize_segments(events, ["M93", "M108/1", "M60"])
        self.assertEqual(result["M93"], {"median_seconds": 240, "p90_seconds": 360, "sample_size": 5})
        self.assertNotIn("M108/1", result)

    def test_checked_in_route_geometry_matches_current_51a_order(self):
        from pathlib import Path

        geo_path = Path(__file__).resolve().parents[1] / "static" / "route-51a.geo.json"
        geo = json.loads(geo_path.read_text(encoding="utf-8"))
        stations = geo["stations"]
        self.assertEqual(geo["coordinate_system"], "GCJ-02")
        self.assertEqual(len(stations), 21)
        self.assertEqual((stations[0]["code"], stations[-1]["code"]), ("M93", "C690/1"))
        for current, following in zip(stations, stations[1:]):
            self.assertEqual(current["path_to_next"][0], current["coordinate"])
            self.assertEqual(current["path_to_next"][-1], following["coordinate"])


if __name__ == "__main__":
    unittest.main()
