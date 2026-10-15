"""Extract DSAT's important scalar fields with regular expressions.

JSON decoding validates the nested response structure. Regular expressions
read station and vehicle values from the original response text. The returned
response has the same shape as before.
"""

import json
import re


FIELD_PATTERNS = {
    name: re.compile(
        rf'"{re.escape(name)}"\s*:\s*("(?:\\.|[^"\\])*"|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|true|false|null)'
    )
    for name in (
        "staCode", "busType", "busCode", "busPlate", "status",
        "isFacilities", "passengerFlow", "speed", "lat", "lng",
    )
}
BUS_OBJECT_PATTERN = re.compile(
    r'\{(?=[^{}]*"busCode"\s*:)[^{}]*\}', re.DOTALL
)


def extract_field(json_text, name):
    """Return a field's original JSON value, or raise on a missing field."""
    match = FIELD_PATTERNS[name].search(json_text)
    if match is None:
        raise ValueError(f"缺少关键字段：{name}")
    return json.loads(match.group(1))


def extract_bus_response(raw_text):
    """Preserve the previous API response shape while extracting key values."""
    response = json.loads(raw_text)
    if not isinstance(response, dict) or response.get("header") != "000":
        raise ValueError("公交接口返回无效状态")
    data = response.get("data")
    stations = data.get("routeInfo") if isinstance(data, dict) else None
    if not isinstance(stations, list):
        raise ValueError("公交接口缺少站点列表")
    if any(
        not isinstance(station, dict)
        or not isinstance(station.get("busInfo"), list)
        for station in stations
    ):
        raise ValueError("站点结构不正确")

    station_matches = list(FIELD_PATTERNS["staCode"].finditer(raw_text))
    bus_matches = list(BUS_OBJECT_PATTERN.finditer(raw_text))
    if len(station_matches) != len(stations):
        raise ValueError("正则表达式识别的站点数与响应不一致")
    if len(bus_matches) != sum(len(s.get("busInfo", [])) for s in stations):
        raise ValueError("正则表达式识别的车辆数与响应不一致")

    bus_index = 0
    for station, station_match in zip(stations, station_matches):
        station["staCode"] = json.loads(station_match.group(1))
        for bus in station["busInfo"]:
            if not isinstance(bus, dict):
                raise ValueError("车辆结构不正确")
            bus_text = bus_matches[bus_index].group()
            bus_index += 1
            for key in FIELD_PATTERNS:
                if key != "staCode" and key in bus:
                    bus[key] = extract_field(bus_text, key)
            if "busCode" not in bus or "status" not in bus:
                raise ValueError("车辆缺少编号或状态")
    return response
