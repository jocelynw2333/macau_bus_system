import asyncio
import json
import os
import re
import signal
import time
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

from dotenv import load_dotenv
from playwright.async_api import Error, async_playwright
from regex_extract import extract_bus_response

load_dotenv()

PAGE_URL = (
    "https://bis.dsat.gov.mo/macauweb/routeLine.html"
    "?routeName=51A&direction=0&language=zh-tw"
    "&ver=3.8.6&routeType=0&fromDzzp=false"
)
OUTPUT = Path(os.getenv("JSONL_PATH", "data/51A_live.jsonl")).resolve()
HEADLESS = os.getenv("COLLECTOR_HEADLESS", "false").lower() == "true"
ROUTE_PARAM = re.compile(r"(?:^|&)routeName=51A(?:&|$)")
DIRECTION_PARAM = re.compile(r"(?:^|&)dir=0(?:&|$)")


async def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    stopping = asyncio.Event()
    last_success = time.monotonic()
    tasks = set()

    def request_stop():
        stopping.set()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, request_stop)
        except NotImplementedError:
            pass

    async def handle_response(response):
        nonlocal last_success
        if urlparse(response.url).path != "/macauweb/routestation/bus":
            return

        post_data = response.request.post_data or ""
        if not ROUTE_PARAM.search(post_data) or not DIRECTION_PARAM.search(post_data):
            return

        try:
            if response.status != 200:
                print(f"公交请求 HTTP {response.status}，等待后续更新。", flush=True)
                return
            # 正则表达式提取关键字段；返回结构仍与旧 JSONL 一致。
            result = extract_bus_response(await response.text())
            data = result.get("data") if isinstance(result, dict) else None
            stations = data.get("routeInfo") if isinstance(data, dict) else None
            if result.get("header") != "000" or not isinstance(stations, list):
                print("没有获得有效线路数据，等待下一次更新。", flush=True)
                return
        except (Error, ValueError, TypeError):
            print("本次响应无法读取，等待下一次更新。", flush=True)
            return

        record = {
            "collected_at": datetime.now().astimezone().isoformat(),
            "route": "51A",
            "direction": "0",
            "response": result,
        }
        try:
            with OUTPUT.open("a", encoding="utf-8") as file:
                file.write(json.dumps(record, ensure_ascii=False) + "\n")
                file.flush()
        except OSError as exc:
            print(f"保存失败：{exc}", flush=True)
            return

        last_success = time.monotonic()
        bus_count = sum(
            len(station.get("busInfo") or [])
            for station in stations
            if isinstance(station, dict)
        )
        print(
            f"{record['collected_at']} 已保存：{len(stations)} 个站点，"
            f"{bus_count} 条车辆记录",
            flush=True,
        )

    def on_response(response):
        task = asyncio.create_task(handle_response(response))
        tasks.add(task)
        task.add_done_callback(tasks.discard)

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=HEADLESS)
        page = await browser.new_page()
        page.on("response", on_response)
        try:
            try:
                await page.goto(PAGE_URL, wait_until="domcontentloaded", timeout=60000)
            except Error:
                print("网页加载未完成，请检查网络或刷新页面。", flush=True)

            print(f"采集已启动，保存位置：{OUTPUT}", flush=True)
            print("按 Control+C 可安全停止。", flush=True)
            while not stopping.is_set() and not page.is_closed():
                try:
                    await asyncio.wait_for(stopping.wait(), timeout=5)
                except asyncio.TimeoutError:
                    pass
                if time.monotonic() - last_success > 120:
                    print("超过两分钟没有有效数据，请检查网页和网络。", flush=True)
                    last_success = time.monotonic()
        finally:
            page.remove_listener("response", on_response)
            if tasks:
                await asyncio.gather(*tasks, return_exceptions=True)
            await browser.close()


if __name__ == "__main__":
    asyncio.run(main())
