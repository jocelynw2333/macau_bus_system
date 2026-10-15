// Y's dashboard layout is backed by the project's read-only 51A history API.
const API_BASE = "/api/routes/51A";
const REFRESH_MS = 10000;
const MACAU_CENTER = [113.5439, 22.1987];
const $ = id => document.getElementById(id);

let latestSnapshot = null;
let selectedEvents = [];
let stationCodes = "";
let refreshing = false;
let amap = null;
let AMapLibrary = null;
let mapOverlays = [];
let mapCounts = {arrived: 0, estimated: 0, segments: 0};
let routeGeo = null;
let segmentStats = {};
let hasFitMap = false;
let zoom = 1;
let mapMode = "map";

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;"
  }[char]));
}

function formatTime(value, dateToo = false) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value).replace("T", " ").slice(0, dateToo ? 19 : 16);
  return dateToo
    ? date.toLocaleString("zh-CN", {hour12: false})
    : date.toLocaleTimeString("zh-CN", {hour12: false});
}

function stateText(status) {
  if (String(status) === "1") return "入站";
  if (String(status) === "0") return "出站";
  return "未知";
}

function stateClass(status) {
  if (String(status) === "1") return "arrive";
  if (String(status) === "0") return "depart";
  return "unknown";
}

function setApiStatus(ok, text) {
  const status = $("apiStatus");
  status.textContent = text;
  status.className = "api-status " + (ok ? "ok" : "err");
}

async function getJSON(path) {
  const response = await fetch(path, {cache: "no-store"});
  if (!response.ok) {
    let detail = "";
    try { detail = (await response.json()).detail || ""; } catch (_) { /* no JSON body */ }
    throw new Error(detail || `HTTP ${response.status}`);
  }
  return response.json();
}

function stationName(code) {
  const liveName = latestSnapshot?.stations?.find(station => station.code === code)?.name;
  return liveName && liveName !== code
    ? liveName : (routeGeo?.stations?.find(station => station.code === code)?.name || code || "--");
}

function routeGeometryReady(snapshot) {
  const current = snapshot?.stations || [];
  const official = routeGeo?.stations || [];
  return current.length > 0 && current.length === official.length
    && current.every((station, index) => station.code === official[index].code);
}

function macauDate(value) {
  if (!value) return NaN;
  return Date.parse(/[zZ]|[+-]\d\d:\d\d$/.test(value) ? value : `${value}+08:00`);
}

function busLocation(bus, snapshot) {
  if (!routeGeometryReady(snapshot)) return {kind: "unavailable"};
  const index = routeGeo.stations.findIndex(station => station.code === bus.sta_code);
  if (index < 0) return {kind: "unavailable"};
  const station = routeGeo.stations[index];
  if (String(bus.status) === "1") return {kind: "station", station, index};
  if (String(bus.status) !== "0" || index + 1 >= routeGeo.stations.length) {
    return {kind: "unavailable"};
  }
  const next = routeGeo.stations[index + 1];
  const result = {kind: "segment", station, next, index, path: station.path_to_next};
  const stats = segmentStats[station.code];
  const sampleTime = macauDate(snapshot.collected_at);
  const departedTime = macauDate(bus.departed_at);
  const age = (Date.now() - sampleTime) / 1000;
  const elapsed = (sampleTime - departedTime) / 1000;
  if (stats && Number.isFinite(elapsed) && elapsed >= 0
      && elapsed <= stats.p90_seconds && age <= 60 && age >= -60
      && result.path?.length >= 2) {
    result.kind = "estimated";
    result.progress = Math.min(0.95, elapsed / stats.median_seconds);
    result.sampleSize = stats.sample_size;
  }
  return result;
}

function coordinateAlong(path, fraction) {
  const distances = [];
  let total = 0;
  for (let i = 1; i < path.length; i++) {
    const meanLat = (path[i - 1][1] + path[i][1]) / 2 * Math.PI / 180;
    const dx = (path[i][0] - path[i - 1][0]) * Math.cos(meanLat);
    const dy = path[i][1] - path[i - 1][1];
    total += Math.hypot(dx, dy);
    distances.push(total);
  }
  if (!total) return path[0];
  const target = total * fraction;
  const index = distances.findIndex(distance => distance >= target);
  if (index < 0) return path[path.length - 1];
  const before = index ? distances[index - 1] : 0;
  const portion = (target - before) / (distances[index] - before);
  return path[index].map((value, dimension) => value + (path[index + 1][dimension] - value) * portion);
}

function locationText(location) {
  if (location.kind === "station") return `入站：${location.station.name}（站点坐标）`;
  if (location.kind === "estimated") {
    return `出站：${location.station.name} → ${location.next.name}；沿路段约 ${Math.round(location.progress * 100)}%（推算）`;
  }
  if (location.kind === "segment") return `出站：${location.station.name} → ${location.next.name}；具体位置未知`;
  return "位置暂无可靠依据";
}

function renderRouteInfo(snapshot) {
  $("routeDirection").textContent = `● 51A · 方向 ${snapshot.direction}`;
  $("routeInfo").innerHTML = [
    ["线路名称", "51A"],
    ["线路编码", "51A"],
    ["方向", `方向 ${snapshot.direction}`],
    ["站点数", snapshot.station_count],
    ["采样时间", formatTime(snapshot.collected_at, true)]
  ].map(([label, value]) => `<div class="info-row"><span class="info-key">${escapeHtml(label)}</span><span class="info-value">${escapeHtml(value)}</span></div>`).join("");
}

function renderKpis(snapshot) {
  const buses = Array.isArray(snapshot.buses) ? snapshot.buses : [];
  const speeds = buses.filter(bus => bus.speed != null && bus.speed !== "")
    .map(bus => Number(bus.speed)).filter(speed => Number.isFinite(speed) && speed >= 0);
  $("kpiBus").textContent = snapshot.bus_count ?? buses.length;
  $("kpiStation").textContent = snapshot.station_count ?? snapshot.stations?.length ?? 0;
  $("kpiSpeed").textContent = speeds.length
    ? (speeds.reduce((sum, speed) => sum + speed, 0) / speeds.length).toFixed(1)
    : "--";
  $("kpiFreshness").textContent = formatTime(snapshot.collected_at);
  $("vehicleCountText").textContent = `${buses.length} 条记录`;
}

function renderVehicles(snapshot) {
  const buses = Array.isArray(snapshot.buses) ? snapshot.buses : [];
  $("vehicleList").innerHTML = buses.length ? buses.map(bus => {
    const location = busLocation(bus, snapshot);
    return `
    <div class="vehicle-card">
      <div class="vehicle-top">
        <span class="vehicle-id">${escapeHtml(bus.bus_code || "--")}</span>
        <span class="status ${stateClass(bus.status)}">${stateText(bus.status)}</span>
      </div>
      <div class="vehicle-grid">
        <div>车牌<strong>${escapeHtml(bus.bus_plate || "--")}</strong></div>
        <div>车型代码<strong>${escapeHtml(bus.bus_type ?? "--")}</strong></div>
        <div>关联站点<strong>${escapeHtml(stationName(bus.sta_code))}</strong></div>
        <div>速度<strong>${escapeHtml(bus.speed ?? "--")} km/h</strong></div>
      </div>
      <div class="vehicle-location">${escapeHtml(locationText(location))}</div>
    </div>`;
  }).join("") : '<div class="empty">当前快照没有车辆记录</div>';
}

function renderStations(snapshot) {
  const stations = Array.isArray(snapshot.stations) ? snapshot.stations : [];
  const buses = Array.isArray(snapshot.buses) ? snapshot.buses : [];
  $("stationTable").innerHTML = stations.length ? stations.map(station => {
    const here = buses.filter(bus => bus.sta_code === station.code);
    const states = [...new Set(here.map(bus => stateText(bus.status)))].join("、");
    const rawLoad = [...new Set(here.map(bus => bus.passenger_flow).filter(value => value != null))].join("、");
    return `<tr>
      <td>${escapeHtml(station.index)}</td>
      <td>${escapeHtml(stationName(station.code))}</td>
      <td>${escapeHtml(station.code)}</td>
      <td>${escapeHtml(states || "--")}</td>
      <td>${escapeHtml(rawLoad || "--")}</td>
      <td><span class="badge ${here.length ? "normal" : ""}">${here.length ? `${here.length} 条车辆记录` : "暂无记录"}</span></td>
    </tr>`;
  }).join("") : '<tr><td colspan="6" class="empty">暂无站点数据</td></tr>';
}

function renderEvents() {
  const code = $("stationSelect").value;
  $("kpiEvents").textContent = selectedEvents.length;
  $("eventTable").innerHTML = selectedEvents.length ? selectedEvents.map(event => `
    <tr>
      <td>${escapeHtml(formatTime(event.observed_at, true))}</td>
      <td>${escapeHtml(event.bus_code)}</td>
      <td>${escapeHtml(code)}</td>
      <td><span class="badge ${event.event_type === "arrival" ? "normal" : "busy"}">${event.event_type === "arrival" ? "入站" : "出站"}</span></td>
      <td>${event.event_basis === "status_change" ? "状态变化" : "首次观察到"}</td>
    </tr>`).join("") : '<tr><td colspan="5" class="empty">该站暂无入站／出站事件</td></tr>';
}

function renderSchematic(snapshot) {
  const stations = Array.isArray(snapshot.stations) ? snapshot.stations : [];
  const box = $("routeSchematic");
  if (!stations.length) {
    box.style.display = "none";
    $("mapEmpty").style.display = "flex";
    return;
  }
  $("mapEmpty").style.display = "none";
  box.style.display = "block";
  box.style.width = Math.max(760, stations.length * 120) + "px";
  const position = index => stations.length === 1 ? 50 : 5 + 90 * index / (stations.length - 1);
  const nodes = stations.map((station, index) => `
    <div class="station-node" style="left:${position(index)}%">
      <span class="station-index">${escapeHtml(station.index)}</span>
      <span class="station-label">${escapeHtml(stationName(station.code))}</span>
    </div>`).join("");
  const buses = (snapshot.buses || []).map(bus => {
    const location = busLocation(bus, snapshot);
    const index = stations.findIndex(station => station.code === bus.sta_code);
    if (index < 0) return "";
    const left = location.kind === "station" ? position(index)
      : index + 1 < stations.length
        ? position(index) + (position(index + 1) - position(index)) * (location.progress ?? 0.5)
        : position(index);
    const label = location.kind === "station" ? "入站" : location.kind === "estimated" ? "推算" : "区间";
    return `<div class="schematic-bus ${location.kind === "station" ? "" : "schematic-estimated"}" style="left:${left}%">🚌 ${escapeHtml(bus.bus_code)} · ${label}</div>`;
  }).join("");
  box.innerHTML = `<div class="route-track"></div>${nodes}${buses}`;
  box.style.transform = `scale(${zoom})`;
  box.style.transformOrigin = "left center";
}

function updateStationChoices(stations) {
  const select = $("stationSelect");
  const nextCodes = stations.map(station => station.code).join("|");
  if (nextCodes === stationCodes && stations.length === select.options.length) {
    [...select.options].forEach((option, index) => {
      option.textContent = `${stations[index].index}. ${stationName(stations[index].code)}`;
    });
    return false;
  }
  const previous = select.value;
  select.replaceChildren();
  stations.forEach(station => {
    const option = document.createElement("option");
    option.value = station.code;
    option.textContent = `${station.index}. ${stationName(station.code)}`;
    select.appendChild(option);
  });
  if (stations.some(station => station.code === previous)) select.value = previous;
  stationCodes = nextCodes;
  return true;
}

async function refreshSelectedStation() {
  const code = $("stationSelect").value;
  if (!code) {
    $("arrivalWindow").textContent = "暂无可选站点。";
    selectedEvents = [];
    renderEvents();
    return;
  }
  try {
    const base = `${API_BASE}/stations/${encodeURIComponent(code)}`;
    const [windowData, events] = await Promise.all([
      getJSON(`${base}/arrival-window`),
      getJSON(`${base}/events?limit=20`)
    ]);
    if ($("stationSelect").value !== code) return;
    selectedEvents = Array.isArray(events) ? events : [];
    renderEvents();
    $("arrivalWindow").textContent = windowData.available
      ? `下一班大约 ${formatTime(windowData.earliest)}—${formatTime(windowData.latest)} 到站；建议最迟 ${formatTime(windowData.arrive_by)} 到站。依据 ${windowData.sample_size} 组历史间隔。`
      : (windowData.reason || "近期入站记录不足，暂不能估计。");
  } catch (error) {
    if ($("stationSelect").value === code) $("arrivalWindow").textContent = `站点数据暂不可用：${error.message}`;
  }
}

function updateMapView() {
  const showMap = Boolean(amap) && mapMode === "map";
  $("amapContainer").hidden = !showMap;
  $("mapFallback").hidden = showMap;
  $("mapModeBtn").disabled = !amap;
  $("mapModeBtn").textContent = showMap ? "线路" : "地图";
  $("mapLegendText").textContent = showMap
    ? "深色：入站站点位置 · 橙色：出站推算位置／路段" : "按站序排列；区间车辆不代表精确位置";
  if (showMap) {
    if (typeof amap.resize === "function") amap.resize();
    const ready = routeGeometryReady(latestSnapshot);
    $("amapStatus").className = ready ? "amap-mask ok" : "amap-mask warn";
    $("amapStatus").innerHTML = ready
      ? `<strong>51A 方向 0</strong>入站车辆 ${mapCounts.arrived} 辆显示在站点；出站车辆 ${mapCounts.estimated} 辆显示推算位置，${mapCounts.segments} 辆只标出所在路段。橙色不是实时 GPS。`
      : "<strong>地图已加载</strong>站点坐标尚未加载，或当前站序与官方资料不一致；暂不显示位置。";
  } else if (amap) {
    $("amapStatus").className = "amap-mask warn";
    $("amapStatus").innerHTML = "<strong>线路示意</strong>车辆按关联站点显示，不代表地图上的实际位置。";
  }
}

function drawMap(snapshot) {
  if (!amap || !AMapLibrary) return;
  mapOverlays.forEach(overlay => overlay.setMap(null));
  mapOverlays = [];
  mapCounts = {arrived: 0, estimated: 0, segments: 0};
  if (!routeGeometryReady(snapshot)) { updateMapView(); return; }
  const stationMarkers = [];
  routeGeo.stations.forEach(station => {
    if (station.path_to_next.length > 1) {
      mapOverlays.push(new AMapLibrary.Polyline({
        path: station.path_to_next, strokeColor: "#8db5e7", strokeWeight: 3,
        strokeOpacity: 0.8, zIndex: 50, map: amap
      }));
    }
    const marker = new AMapLibrary.Marker({
      position: station.coordinate,
      title: `${station.code} ${station.name}`,
      content: '<div class="amap-stop-dot"></div>',
      offset: new AMapLibrary.Pixel(-6, -6), map: amap, zIndex: 100
    });
    stationMarkers.push(marker);
    mapOverlays.push(marker);
  });
  const activeSegments = new Set();
  (snapshot.buses || []).forEach(bus => {
    const location = busLocation(bus, snapshot);
    if (location.kind === "station") {
      mapCounts.arrived++;
      mapOverlays.push(new AMapLibrary.Marker({
        position: location.station.coordinate,
        title: `${bus.bus_code} 入站：${location.station.name}（站点坐标）`,
        content: `<div class="amap-bus-pin">🚌 ${escapeHtml(bus.bus_code)}</div>`,
        offset: new AMapLibrary.Pixel(-18, -28), map: amap, zIndex: 300
      }));
      return;
    }
    if (location.kind !== "segment" && location.kind !== "estimated") return;
    if (!activeSegments.has(location.station.code)) {
      activeSegments.add(location.station.code);
      mapOverlays.push(new AMapLibrary.Polyline({
        path: location.path, strokeColor: "#f59e0b", strokeWeight: 6,
        strokeOpacity: 0.9, zIndex: 150, map: amap
      }));
    }
    if (location.kind === "segment") { mapCounts.segments++; return; }
    mapCounts.estimated++;
    mapOverlays.push(new AMapLibrary.Marker({
      position: coordinateAlong(location.path, location.progress),
      title: `${bus.bus_code} 推算位置：${location.station.name} → ${location.next.name}；${location.sampleSize} 组历史样本`,
      content: `<div class="amap-bus-pin estimated">≈🚌 ${escapeHtml(bus.bus_code)}</div>`,
      offset: new AMapLibrary.Pixel(-22, -28), map: amap, zIndex: 300
    }));
  });
  if (!hasFitMap && stationMarkers.length) {
    amap.setFitView(stationMarkers);
    hasFitMap = true;
  }
  updateMapView();
}

async function loadRouteGeo() {
  try {
    routeGeo = await getJSON("/static/route-51a.geo.json");
    if (routeGeo.coordinate_system !== "GCJ-02") throw new Error("坐标系不匹配");
  } catch (error) {
    console.error("站点资料读取失败", error);
    routeGeo = null;
  }
  if (latestSnapshot) {
    renderVehicles(latestSnapshot);
    renderStations(latestSnapshot);
    renderSchematic(latestSnapshot);
    drawMap(latestSnapshot);
    updateStationChoices(latestSnapshot.stations || []);
  }
}

async function loadSegmentStats() {
  try {
    const data = await getJSON(`${API_BASE}/segment-times`);
    segmentStats = data.segments || {};
    if (latestSnapshot) {
      renderVehicles(latestSnapshot);
      renderSchematic(latestSnapshot);
      drawMap(latestSnapshot);
    }
  } catch (error) { console.error("站间历史时间读取失败", error); }
}

async function loadMap() {
  const status = $("amapStatus");
  let config;
  try {
    config = await getJSON("/static/map-config.local.json");
    if (!config.key || !config.securityCode) throw new Error("未配置地图密钥");
  } catch (_) {
    status.className = "amap-mask warn";
    status.innerHTML = "<strong>线路示意</strong>本机尚未配置高德地图，站点和车辆按线路顺序展示。";
    return;
  }
  try {
    window._AMapSecurityConfig = {securityJsCode: config.securityCode};
    if (!window.AMapLoader) {
      await new Promise((resolve, reject) => {
        const script = document.createElement("script");
        script.src = "https://webapi.amap.com/loader.js";
        script.onload = resolve;
        script.onerror = () => reject(new Error("无法加载高德地图"));
        document.head.appendChild(script);
      });
    }
    AMapLibrary = await window.AMapLoader.load({
      key: config.key, version: "2.0", plugins: ["AMap.Scale", "AMap.ToolBar"]
    });
    $("amapContainer").hidden = false;
    $("mapFallback").hidden = true;
    amap = new AMapLibrary.Map("amapContainer", {
      viewMode: "2D", zoom: 12, center: MACAU_CENTER, resizeEnable: true
    });
    amap.addControl(new AMapLibrary.Scale());
    amap.addControl(new AMapLibrary.ToolBar({position: "RB"}));
    if (latestSnapshot) drawMap(latestSnapshot);
    else updateMapView();
  } catch (error) {
    console.error("地图加载失败", error);
    $("amapContainer").hidden = true;
    $("mapFallback").hidden = false;
    amap = null;
    $("mapModeBtn").disabled = true;
    status.className = "amap-mask warn";
    status.innerHTML = `<strong>线路示意</strong>地图加载失败：${escapeHtml(error.message)}。`;
  }
}

async function refreshLatest() {
  if (refreshing) return;
  refreshing = true;
  try {
    const snapshot = await getJSON(`${API_BASE}/latest`);
    latestSnapshot = snapshot;
    renderRouteInfo(snapshot);
    renderKpis(snapshot);
    renderVehicles(snapshot);
    renderStations(snapshot);
    renderSchematic(snapshot);
    drawMap(snapshot);
    const changed = updateStationChoices(snapshot.stations || []);
    if (changed) refreshSelectedStation();
    $("lastUpdate").textContent = formatTime(snapshot.collected_at, true);
    const ageSeconds = (Date.now() - new Date(snapshot.collected_at).getTime()) / 1000;
    setApiStatus(ageSeconds <= 60, ageSeconds > 60 ? "采集超过 1 分钟未更新" : "数据持续更新");
    $("errorBox").classList.remove("show");
  } catch (error) {
    console.error("最新快照读取失败", error);
    setApiStatus(false, "接口异常");
    $("errorBox").textContent = `数据刷新失败：${error.message}`;
    $("errorBox").classList.add("show");
  } finally {
    refreshing = false;
  }
}

function updateClock() {
  $("clock").textContent = new Date().toLocaleString("zh-CN", {hour12: false});
}

$("stationSelect").addEventListener("change", refreshSelectedStation);
$("mapModeBtn").addEventListener("click", () => {
  if (!amap) return;
  mapMode = mapMode === "map" ? "schematic" : "map";
  updateMapView();
});
$("fullscreenBtn").addEventListener("click", async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await $("mapWrap").requestFullscreen();
  } catch (error) { console.error(error); }
});
$("zoomInBtn").addEventListener("click", () => {
  if (amap && mapMode === "map") amap.zoomIn();
  else { zoom = Math.min(1.8, zoom + 0.1); if (latestSnapshot) renderSchematic(latestSnapshot); }
});
$("zoomOutBtn").addEventListener("click", () => {
  if (amap && mapMode === "map") amap.zoomOut();
  else { zoom = Math.max(0.7, zoom - 0.1); if (latestSnapshot) renderSchematic(latestSnapshot); }
});
$("resetMapBtn").addEventListener("click", () => {
  if (amap && mapMode === "map") amap.setZoomAndCenter(12, MACAU_CENTER);
  else { zoom = 1; if (latestSnapshot) renderSchematic(latestSnapshot); }
});
document.addEventListener("fullscreenchange", () => {
  $("fullscreenBtn").textContent = document.fullscreenElement ? "退出全屏" : "全屏";
});

updateClock();
setInterval(updateClock, 1000);
refreshLatest();
loadMap();
loadRouteGeo();
loadSegmentStats();
setInterval(refreshLatest, REFRESH_MS);
setInterval(refreshSelectedStation, 30000);
setInterval(loadSegmentStats, 300000);
