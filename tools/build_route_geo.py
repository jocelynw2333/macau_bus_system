"""Build the checked-in 51A map geometry from Macao's official open dataset.

Usage: python tools/build_route_geo.py /path/to/ExportShapeFile.zip
Optional build dependencies: pyshp, pyproj, xlrd (not needed to run the app).
"""

import argparse
import io
import json
import math
import zipfile
from pathlib import Path

import shapefile
import xlrd
from pyproj import Transformer


SOURCE_URL = "https://data.gov.mo/Detail?id=e7b2e84d-3333-42f0-b676-64ce95306f0d"
ROUTE_CODES = [
    "M93", "M108/1", "M60", "M1/17", "M222/2", "M244", "M219",
    "M235/1", "T316", "T345/5", "T344", "T356/1", "T358", "T373/2",
    "T367", "T394", "T396", "T377", "T359", "C688/2", "C690/1",
]


def transform_lat(x, y):
    result = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y
    result += 0.2 * math.sqrt(abs(x))
    result += (20 * math.sin(6 * x * math.pi) + 20 * math.sin(2 * x * math.pi)) * 2 / 3
    result += (20 * math.sin(y * math.pi) + 40 * math.sin(y / 3 * math.pi)) * 2 / 3
    result += (160 * math.sin(y / 12 * math.pi) + 320 * math.sin(y * math.pi / 30)) * 2 / 3
    return result


def transform_lng(x, y):
    result = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y
    result += 0.1 * math.sqrt(abs(x))
    result += (20 * math.sin(6 * x * math.pi) + 20 * math.sin(2 * x * math.pi)) * 2 / 3
    result += (20 * math.sin(x * math.pi) + 40 * math.sin(x / 3 * math.pi)) * 2 / 3
    result += (150 * math.sin(x / 12 * math.pi) + 300 * math.sin(x / 30 * math.pi)) * 2 / 3
    return result


def wgs84_to_gcj02(lng, lat):
    """Convert WGS84 to the coordinates used by the AMap base map."""
    if not (72.004 <= lng <= 137.8347 and 0.8293 <= lat <= 55.8271):
        return lng, lat
    dlat = transform_lat(lng - 105, lat - 35)
    dlng = transform_lng(lng - 105, lat - 35)
    radlat = lat / 180 * math.pi
    magic = 1 - 0.006693421622965943 * math.sin(radlat) ** 2
    sqrt_magic = math.sqrt(magic)
    dlat = dlat * 180 / ((6335552.717000426 / (magic * sqrt_magic)) * math.pi)
    dlng = dlng * 180 / ((6378245 / sqrt_magic) * math.cos(radlat) * math.pi)
    return lng + dlng, lat + dlat


def read_shape(archive, stem):
    prefix = next(name.rsplit("/", 1)[0] for name in archive.namelist() if name.endswith("/" + stem + ".shp"))
    return shapefile.Reader(
        shp=io.BytesIO(archive.read(prefix + "/" + stem + ".shp")),
        shx=io.BytesIO(archive.read(prefix + "/" + stem + ".shx")),
        dbf=io.BytesIO(archive.read(prefix + "/" + stem + ".dbf")),
        encoding="utf-8",
    )


def create_route(archive):
    # The archive's PRJ omits datum-shift parameters. EPSG:8433 includes the
    # official Macao 1920 -> WGS84 transformation; using the PRJ alone puts
    # stops hundreds of metres away from the street map.
    to_wgs84 = Transformer.from_crs("EPSG:8433", "EPSG:4326", always_xy=True)

    def coordinate(point):
        lng, lat = to_wgs84.transform(*point)
        return [round(value, 6) for value in wgs84_to_gcj02(lng, lat)]

    poles = {
        str(item.record.as_dict()["POLE_ID"]): item
        for item in read_shape(archive, "BUS_POLE").iterShapeRecords()
    }
    roads = {
        str(item.record.as_dict()["NETWORK_ID"]): item.shape.points
        for item in read_shape(archive, "ROUTE_NETWORK").iterShapeRecords()
    }
    xls_name = next(name for name in archive.namelist() if name.endswith("/BUS_ROUTE_SEQ.xls"))
    sheet = xlrd.open_workbook(file_contents=archive.read(xls_name)).sheet_by_index(0)
    rows = [
        sheet.row_values(i) for i in range(1, sheet.nrows)
        if sheet.cell_value(i, 0) == "51A" and sheet.cell_value(i, 2) == "A"
    ]
    rows.sort(key=lambda row: int(row[3]))
    stop_rows = [row for row in rows if row[5]]
    if len(stop_rows) != len(ROUTE_CODES):
        raise ValueError("官方站点数量与已核对的 51A 方向 0 不一致，请重新核对线路")

    stations = []
    for index, row in enumerate(stop_rows):
        pole = poles[row[5]]
        alias = str(pole.record.as_dict()["P_ALIAS"]).replace("_", "/")
        code = ROUTE_CODES[index]
        if alias != code and not ("/" not in code and alias == code + "/1"):
            raise ValueError(f"第 {index + 1} 站编号不一致：{code} / {alias}")
        start = pole.shape.points[0]
        path = [start]
        if index + 1 < len(stop_rows):
            next_row = stop_rows[index + 1]
            for part in rows:
                seq = int(part[3])
                if int(row[3]) < seq < int(next_row[3]) and part[6]:
                    points = list(roads[part[6]])
                    if math.dist(path[-1], points[-1]) < math.dist(path[-1], points[0]):
                        points.reverse()
                    if math.dist(path[-1], points[0]) > 1:
                        raise ValueError(f"道路片段 {part[6]} 与上一片段未接通")
                    path.extend(points[1:])
            end = poles[next_row[5]].shape.points[0]
            if math.dist(path[-1], end) > 1:
                raise ValueError(f"{code} 到下一站的道路未接通")
            path[-1] = end
        stations.append({
            "code": code,
            "pole_alias": alias,
            "name": pole.record.as_dict()["P_NAME"],
            "coordinate": coordinate(start),
            "path_to_next": [coordinate(point) for point in path] if index + 1 < len(stop_rows) else [],
        })
    return {
        "route": "51A", "direction": 0, "coordinate_system": "GCJ-02",
        "source": SOURCE_URL, "source_version": "2026-09-25",
        "stations": stations,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "static" / "route-51a.geo.json")
    args = parser.parse_args()
    with zipfile.ZipFile(args.archive) as archive:
        route = create_route(archive)
    args.output.write_text(json.dumps(route, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"已生成 {args.output}，{len(route['stations'])} 个站点")


if __name__ == "__main__":
    main()
