"""Historical travel durations between adjacent 51A stations."""

from collections import defaultdict
from datetime import timedelta
from statistics import median


MIN_TRAVEL = timedelta(seconds=20)
MAX_TRAVEL = timedelta(minutes=30)
MIN_SAMPLES = 5


def summarize_segments(events, station_codes):
    """Pair a bus's departure with its *next* station arrival in one trip.

    An intervening event, wrong station or long gap cancels the pair. Event
    timestamps are first observations, so these are approximate durations.
    """
    indexes = {code: index for index, code in enumerate(station_codes)}
    samples = defaultdict(list)
    pending = {}
    for event in sorted(events, key=lambda row: (row["bus_code"], row["observed_at"])):
        bus = event["bus_code"]
        code = event["sta_code"]
        kind = event["event_type"]
        at = event["observed_at"]
        previous = pending.pop(bus, None)
        if kind == "arrival" and previous:
            from_code, departed_at = previous
            elapsed = at - departed_at
            if (
                indexes.get(from_code, -2) + 1 == indexes.get(code, -1)
                and MIN_TRAVEL <= elapsed <= MAX_TRAVEL
            ):
                samples[from_code].append(round(elapsed.total_seconds()))
        if kind == "departure" and indexes.get(code, len(station_codes)) < len(station_codes) - 1:
            pending[bus] = (code, at)

    result = {}
    for code in station_codes[:-1]:
        values = sorted(samples[code])
        if len(values) < MIN_SAMPLES:
            continue
        p90_index = round(0.9 * (len(values) - 1))
        result[code] = {
            "median_seconds": round(median(values)),
            "p90_seconds": values[p90_index],
            "sample_size": len(values),
        }
    return result
