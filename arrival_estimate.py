"""Conservative historical headway estimate for one 51A stop."""

from collections import defaultdict
from datetime import timedelta


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def estimate_window(arrivals, now):
    """Estimate the next arrival from same-stop arrival events.

    Uses 10th–90th percentile headways around the current time of day.
    No estimate is returned if the last observed bus or sample is stale.
    """
    estimate, _ = estimate_window_with_reason(arrivals, now)
    return estimate


def estimate_window_with_reason(arrivals, now):
    """Return an estimate or an explanation of why one is unavailable."""
    by_day = defaultdict(list)
    for arrival in arrivals:
        if arrival <= now:
            by_day[arrival.date()].append(arrival)
    today = sorted(by_day.get(now.date(), []))
    if not today:
        return None, "今天尚未记录到该站有车辆入站，暂无法估计下一班。"
    if now - today[-1] > timedelta(minutes=90):
        return None, f"最近一次入站记录是 {today[-1]:%H:%M}，距今已超过 90 分钟，暂无法可靠估计。"

    headways = []
    for day_arrivals in by_day.values():
        day_arrivals.sort()
        for earlier, later in zip(day_arrivals, day_arrivals[1:]):
            minutes = (later - earlier).total_seconds() / 60
            hour_distance = abs(
                (earlier.hour * 60 + earlier.minute)
                - (now.hour * 60 + now.minute)
            )
            if 3 <= minutes <= 90 and hour_distance <= 120:
                headways.append(minutes)
    if len(headways) < 5:
        return None, f"近 14 天该站相近时段只有 {len(headways)} 组有效到车间隔，至少需要 5 组才能估计。"

    earliest = today[-1] + timedelta(minutes=percentile(headways, 0.10))
    latest = today[-1] + timedelta(minutes=percentile(headways, 0.90))
    if latest <= now:
        return None, (
            f"最近一次入站记录是 {today[-1]:%H:%M}，"
            "按历史间隔推算的下一班时间范围已过去；可能有未记录的到站或班次变化，暂不猜测。"
        )
    shown_earliest = max(earliest, now)
    return {
        "earliest": shown_earliest,
        "latest": latest,
        "arrive_by": max(shown_earliest - timedelta(minutes=2), now),
        "last_arrival": today[-1],
        "sample_size": len(headways),
        "method": "近14天同站点、相近时段的历史到车间隔（10%—90%分位）",
    }, None
