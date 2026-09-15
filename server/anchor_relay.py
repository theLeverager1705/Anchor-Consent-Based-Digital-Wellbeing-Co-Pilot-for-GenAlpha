"""
Anchor Relay: the small backend for Anchor (deployable to Render).

  * Family sync: an append-only event log per family. It only ever receives what a child agreed to
    share (derived summaries and check-ins), never raw camera, audio, messages or journal text.
  * AI digest: turns aggregated, de-identified weekly numbers into a short plain-language digest
    and one conversation starter using Claude. Works without a key (the app falls back to its
    rule-based digest).
  * Website: serves ../web (landing page, the original clickable prototype, privacy policy).

Standard library HTTP server + the official `anthropic` SDK. Run locally:
    pip install -r server/requirements.txt
    python server/anchor_relay.py            # http://localhost:8787
"""

from __future__ import annotations

import hashlib
import json
import mimetypes
import os
import re
import secrets
import threading
import time
from collections import defaultdict, deque
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent.parent
WEB_DIR = ROOT / "web"
DATA_DIR = Path(os.environ.get("ANCHOR_DATA_DIR", ROOT / "server" / "data"))
DATA_FILE = DATA_DIR / "families.json"
PORT = int(os.environ.get("PORT", "8787"))
MAX_BODY = 256 * 1024
MAX_EVENTS_PER_FAMILY = 5000
PAGE_SIZE = 200
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O/1/I
ROLES = {"CHILD", "PARENT", "GUIDE"}
AI_MODEL = os.environ.get("ANCHOR_AI_MODEL", "claude-opus-5")

_lock = threading.Lock()
_rate: dict[str, deque] = defaultdict(deque)


def _hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def _load() -> dict:
    if DATA_FILE.exists():
        try:
            return json.loads(DATA_FILE.read_text("utf-8"))
        except (OSError, json.JSONDecodeError):
            pass
    return {"families": {}, "codes": {}}


def _save(db: dict) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    tmp = DATA_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(db, separators=(",", ":")), "utf-8")
    tmp.replace(DATA_FILE)


DB = _load()


def rate_limited(key: str, limit: int, window_s: int) -> bool:
    now = time.monotonic()
    q = _rate[key]
    while q and now - q[0] > window_s:
        q.popleft()
    if len(q) >= limit:
        return True
    q.append(now)
    return False


# --------------------------------------------------------------------------- AI digest

DIGEST_KEYS = {
    "checkinsThisWeek", "streakDays", "moodAvgLast7", "moodAvgPrior7",
    "toughTransitionsOfLast5", "avgDailyScreenMinutes", "categoryMix", "patterns",
}

DIGEST_SYSTEM = """You write the weekly digest for Anchor, a consent-based digital wellbeing app used by families with children aged roughly 5 to 12.

You receive only aggregated, de-identified numbers: check-in counts, mood averages on a 1-5 scale, how many recent screen-off "transition checks" were tough, average daily screen minutes, category mix, and the titles of rule-based patterns the app flagged.

Write for a parent in warm, plain language:
- digest: 2 to 3 sentences. Describe what changed this week and what looks steady. Mention something positive when the data supports it.
- starter: one specific, low-drama question the parent could ask their child, written in quotes as it would be said out loud.

Anchor is never diagnostic. Never name or imply a clinical condition, never use words like "addiction", "disorder", "depression" or "anxiety disorder", and never recommend punishment, taking devices away, or covert monitoring. Frame patterns as "worth a conversation". If the numbers are sparse, say so gently and suggest a simple check-in habit."""

DIGEST_SCHEMA = {
    "type": "object",
    "properties": {
        "digest": {"type": "string"},
        "starter": {"type": "string"},
    },
    "required": ["digest", "starter"],
    "additionalProperties": False,
}

_ai_client = None


def ai_available() -> bool:
    return bool(os.environ.get("ANTHROPIC_API_KEY"))


def clean_digest_input(raw: dict) -> dict:
    """Accept only the known aggregate fields, short values, no free text beyond pattern titles."""
    out = {}
    for key in DIGEST_KEYS:
        if key not in raw:
            continue
        value = raw[key]
        if isinstance(value, (int, float)):
            out[key] = value
        elif isinstance(value, list):
            out[key] = [str(v)[:120] for v in value[:8]]
        else:
            out[key] = str(value)[:40]
    return out


def write_digest(stats: dict) -> dict:
    global _ai_client
    import anthropic  # imported lazily so the relay runs without the SDK installed

    if _ai_client is None:
        _ai_client = anthropic.Anthropic(timeout=40.0, max_retries=1)

    response = _ai_client.beta.messages.create(
        model=AI_MODEL,
        max_tokens=16000,
        betas=["server-side-fallback-2026-07-01"],
        fallbacks="default",
        system=DIGEST_SYSTEM,
        output_config={"effort": "low", "format": {"type": "json_schema", "schema": DIGEST_SCHEMA}},
        messages=[{"role": "user", "content": "This week's aggregated family numbers:\n" + json.dumps(stats, indent=2)}],
    )
    if response.stop_reason == "refusal":
        raise RuntimeError("the model declined this request")
    text = next(block.text for block in response.content if block.type == "text")
    data = json.loads(text)
    return {"digest": data["digest"], "starter": data["starter"], "model": "Claude"}


# --------------------------------------------------------------------------- HTTP

class Handler(BaseHTTPRequestHandler):
    server_version = "AnchorRelay/1.0"

    # ---- helpers
    def _client_ip(self) -> str:
        fwd = self.headers.get("X-Forwarded-For", "")
        return fwd.split(",")[0].strip() if fwd else self.client_address[0]

    def _send_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _error(self, status: int, message: str) -> None:
        self._send_json(status, {"error": message})

    def _read_json(self) -> dict | None:
        length = int(self.headers.get("Content-Length") or 0)
        if length > MAX_BODY:
            self._error(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "payload too large")
            return None
        try:
            data = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            self._error(HTTPStatus.BAD_REQUEST, "invalid JSON")
            return None
        if not isinstance(data, dict):
            self._error(HTTPStatus.BAD_REQUEST, "expected a JSON object")
            return None
        return data

    def _family_for_token(self, family_id: str) -> dict | None:
        auth = self.headers.get("Authorization", "")
        token = auth[7:] if auth.startswith("Bearer ") else ""
        fam = DB["families"].get(family_id)
        if not fam or not token or not any(secrets.compare_digest(_hash(token), h) for h in fam["tokens"]):
            self._error(HTTPStatus.UNAUTHORIZED, "not a member of this family")
            return None
        return fam

    def log_message(self, fmt: str, *args) -> None:  # keep logs free of payloads
        print(f"{self._client_ip()} {self.command} {urlparse(self.path).path} {args[1] if len(args) > 1 else ''}")

    # ---- routes
    def do_GET(self) -> None:
        url = urlparse(self.path)
        path = url.path
        if path == "/v1/health":
            self._send_json(200, {"ok": True, "ai": ai_available(), "families": len(DB["families"])})
            return
        m = re.fullmatch(r"/v1/families/([\w-]+)/events", path)
        if m:
            with _lock:
                fam = self._family_for_token(m.group(1))
                if fam is None:
                    return
                after = int((parse_qs(url.query).get("after") or ["0"])[0] or 0)
                events = [e for e in fam["events"] if e["seq"] > after][:PAGE_SIZE]
                self._send_json(200, {"events": events, "latest": fam["seq"]})
            return
        self._serve_static(path)

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        ip = self._client_ip()

        if path == "/v1/families":
            if rate_limited(f"create:{ip}", 10, 3600):
                return self._error(HTTPStatus.TOO_MANY_REQUESTS, "too many families created, try later")
            data = self._read_json()
            if data is None:
                return
            role = data.get("role", "PARENT")
            if role not in {"PARENT", "GUIDE"}:
                return self._error(HTTPStatus.BAD_REQUEST, "only a grown-up can create a family")
            with _lock:
                family_id = secrets.token_urlsafe(12)
                code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(6))
                while code in DB["codes"]:
                    code = "".join(secrets.choice(CODE_ALPHABET) for _ in range(6))
                token = secrets.token_urlsafe(32)
                DB["families"][family_id] = {"created": int(time.time()), "code": code, "tokens": [_hash(token)], "seq": 0, "events": []}
                DB["codes"][code] = family_id
                _save(DB)
            return self._send_json(201, {"familyId": family_id, "code": code, "token": token})

        if path == "/v1/join":
            if rate_limited(f"join:{ip}", 20, 600):
                return self._error(HTTPStatus.TOO_MANY_REQUESTS, "too many attempts, wait a few minutes")
            data = self._read_json()
            if data is None:
                return
            code = str(data.get("code", "")).strip().upper()
            role = data.get("role", "CHILD")
            if role not in ROLES:
                return self._error(HTTPStatus.BAD_REQUEST, "unknown role")
            with _lock:
                family_id = DB["codes"].get(code)
                if not family_id:
                    return self._error(HTTPStatus.NOT_FOUND, "that family code wasn't found")
                token = secrets.token_urlsafe(32)
                DB["families"][family_id]["tokens"].append(_hash(token))
                _save(DB)
            return self._send_json(200, {"familyId": family_id, "token": token})

        m = re.fullmatch(r"/v1/families/([\w-]+)/events", path)
        if m:
            data = self._read_json()
            if data is None:
                return
            if not isinstance(data.get("id"), str) or not isinstance(data.get("type"), str) or not isinstance(data.get("payload"), dict):
                return self._error(HTTPStatus.BAD_REQUEST, "invalid event")
            with _lock:
                fam = self._family_for_token(m.group(1))
                if fam is None:
                    return
                existing = next((e for e in fam["events"] if e["id"] == data["id"]), None)
                if existing:
                    return self._send_json(200, {"seq": existing["seq"]})
                fam["seq"] += 1
                event = {k: data.get(k) for k in ("id", "type", "from", "fromName", "ts", "payload")}
                event["seq"] = fam["seq"]
                if event["type"] == "SNAPSHOT":
                    # keep only the newest summary per day, like the app does
                    key = (event["payload"].get("date"), event["payload"].get("sample"))
                    fam["events"] = [e for e in fam["events"] if not (e["type"] == "SNAPSHOT" and (e["payload"].get("date"), e["payload"].get("sample")) == key)]
                fam["events"].append(event)
                fam["events"] = fam["events"][-MAX_EVENTS_PER_FAMILY:]
                _save(DB)
            return self._send_json(201, {"seq": event["seq"]})

        if path == "/v1/digest":
            if not ai_available():
                return self._error(HTTPStatus.SERVICE_UNAVAILABLE, "AI digest is not configured on this relay")
            if rate_limited(f"digest:{ip}", 12, 3600):
                return self._error(HTTPStatus.TOO_MANY_REQUESTS, "digest limit reached, try again later")
            data = self._read_json()
            if data is None:
                return
            stats = clean_digest_input(data)
            if not stats:
                return self._error(HTTPStatus.BAD_REQUEST, "no aggregate numbers provided")
            try:
                return self._send_json(200, write_digest(stats))
            except Exception as exc:  # the app always has its rule-based digest to fall back on
                print(f"digest failed: {type(exc).__name__}: {exc}")
                return self._error(HTTPStatus.BAD_GATEWAY, "AI digest failed")

        self._error(HTTPStatus.NOT_FOUND, "not found")

    # ---- website
    def _serve_static(self, path: str) -> None:
        routes = {"/": "index.html", "/privacy": "privacy.html", "/prototype": "prototype/index.html", "/prototype/": "prototype/index.html"}
        rel = routes.get(path, path.lstrip("/"))
        target = (WEB_DIR / rel).resolve()
        if target.is_dir():
            target = target / "index.html"
        if not str(target).startswith(str(WEB_DIR.resolve())) or not target.is_file():
            self._error(HTTPStatus.NOT_FOUND, "not found")
            return
        body = target.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", (mimetypes.guess_type(target.name)[0] or "application/octet-stream") + ("; charset=utf-8" if target.suffix in {".html", ".css", ".js", ".txt"} else ""))
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "public, max-age=300")
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"Anchor Relay on http://localhost:{PORT} (AI digest {'on' if ai_available() else 'off: set ANTHROPIC_API_KEY'})")
    server.serve_forever()


if __name__ == "__main__":
    main()
