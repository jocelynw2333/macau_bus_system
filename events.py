"""Turn repeated bus observations into approximate station events."""

from datetime import timedelta

MAX_CONTINUOUS_GAP = timedelta(minutes=20)


def classify_event(status, observed_at, previous_status=None, previous_at=None):
    """Return (event_type, basis) or None.

    1/0 follows the team's SQL comment: arrival/departure. An event time is
    the first *observed* state, not an exact door-opening timestamp.
    """
    status = str(status) if status is not None else None
    if status not in ("0", "1"):
        return None
    recent = (
        previous_at is not None
        and timedelta(0) <= observed_at - previous_at <= MAX_CONTINUOUS_GAP
    )
    if recent and str(previous_status) == status:
        return None
    if recent and str(previous_status) in ("0", "1"):
        return ("arrival" if status == "1" else "departure", "status_change")
    if status == "1":
        return "arrival", "first_seen"
    return None
