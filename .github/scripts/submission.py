#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发现页内容投稿通道 · 收稿校验与采用上架（零服务器，GitHub Issue 单轨）。

四个动作，对应四条命令：

- ``--check``    收稿校验：解析 Issue 正文、验字段、取样文件、报"能不能上"。
- ``--adopt``    采用上架：把文件落到 ``dist/discover/``、把条目写进 ``discover-manifest.json``。
- ``--retract``  下架（2026-10-14 加，与"采用"对称）：删清单条目、删文件（空包连封面一起删）。
- ``--finalize`` 收尾回帖：贴结论、打标签、摘掉触发标签、关单——上架打 ``已上架``、下架打 ``已下架``。

分工：**本脚本不碰 git**（落盘后由 workflow 提交推送），也不替维护者做"点头"这个决定——
"点头"＝人给 Issue 打 ``采用`` 标签，workflow 才跑 ``--adopt``。

Issue 正文有两种形状，都要认：

1. **表单形状**（用户在仓库里点「内容投稿」）：GitHub 把每个字段渲染成 ``### 字段名`` 分段；
2. **预填形状**（App 内「投稿」直接拼正文）：正文里有一段 ```json 的条目块。

两种都靠 ``解析分段`` + ``找第一个 json 代码块`` 拿数据，任何一处不认识就给出去人话的报错，
绝不让投稿人对着"未知错误"发呆。

本地跑法（不联网、不落盘、只看解析与校验）：:

    /d/Python/python.exe .github/scripts/submission.py --check --body-file sample.md --comment-out /tmp/c.md

产物命名沿用发现页既有扁平约定（清单里的 ``file`` 一律是**相对文件名**，不接受 URL）：
``card-<id>.png`` / ``book-<id>.json`` / ``story-<id>.json`` / ``thumb-<id>.jpg``。
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import struct
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

# ── 常量 ────────────────────────────────────────────────────────────────
MARKER = "<!-- whale-submission-check -->"
# 临时产物目录：CI 上是 /tmp（GitHub runner 的 TMPDIR 就是它），本地重跑落到系统临时目录。
SCRATCH = tempfile.gettempdir()
COMMENT_FILE = os.path.join(SCRATCH, "whale-submission-comment.md")
RESULT_FILE = os.path.join(SCRATCH, "whale-submission-result.json")
RESULT_FILE_PATH = Path(RESULT_FILE)

KIND_CARD = "角色卡"
KIND_BOOK = "世界书"
KIND_STORY = "故事"
KINDS = {KIND_CARD: "角色", KIND_BOOK: "世界书", KIND_STORY: "故事"}

# 卡片形态：与 DiscoverCardEntry.form 一套值（""=陪伴 / experience=多线 / play=玩法 / tool=工具）
FORM_LABELS = {
    "陪伴": "",
    "多线": "experience",
    "玩法": "play",
    "工具": "tool",
    "不适用": "",
}

FILE_PREFIX = {KIND_CARD: "card", KIND_BOOK: "book", KIND_STORY: "story"}

ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{1,39}$")
MAX_ASSET_BYTES = 16 * 1024 * 1024
THUMB_SIZE = 224

USER_AGENT = "whale-discovery-channel/1"

SECTIONS = {
    "kind": "投稿类型",
    "form": "角色形态",
    "theme": "归属主题",
    "id": "标识",
    "name": "名称",
    "tagline": "一句话",
    "intro": "内容简介",
    # 表单 2026-10-14 瘦身：原来「文件直链」＋「或直接把文件拖进来」两栏合并成一栏「文件」
    # （拖文件与贴链接本来就是一件事）；后两个名字留着只作兼容，不再出现在表单里。
    "file": "文件",
    "file_link": "文件直链",
    "attach": "或直接把文件拖进来",
    "agree": "合规确认",
}


class Reject(Exception):
    """投稿不合格：给用户看的理由，不是程序错误。"""


# ── 正文解析 ────────────────────────────────────────────────────────────
def split_sections(body: str) -> dict[str, str]:
    """把 ``### 标题`` 分段切成 {标题: 正文}。没有 ### 的正文返回空表。"""
    out: dict[str, list[str]] = {}
    cur: str | None = None
    for line in (body or "").splitlines():
        if line.startswith("### "):
            cur = line[4:].strip()
            out.setdefault(cur, [])
        elif cur is not None:
            out[cur].append(line)
    return {k: "\n".join(v).strip() for k, v in out.items()}


def find_json_block(body: str) -> dict | None:
    """正文里第一个 ```json 代码块（App 预填的条目块、或维护者手写的都走这里）。"""
    m = re.search(r"```json\s*\n(.*?)```", body or "", re.S)
    if not m:
        return None
    try:
        data = json.loads(m.group(1))
    except Exception:
        return None
    if isinstance(data, dict) and isinstance(data.get("whale_entry"), dict):
        return data["whale_entry"]
    return data if isinstance(data, dict) else None


def clean(v: str) -> str:
    v = (v or "").strip()
    if v in ("_No response_", "No response"):
        return ""
    return v


def section_of(sec: dict[str, str], label: str) -> str:
    """按标题取分段。表单里的标题就是字段名原文，但可能带「（可选）」这类后缀 ⇒ 前缀匹配兜底。"""
    if label in sec:
        return sec[label]
    for head, text in sec.items():
        if head.startswith(label):
            return text
    return ""


def parse_submission(body: str) -> dict:
    """正文 → 投稿字段。表单形状优先，缺的字段用 ```json 块补。"""
    sec = split_sections(body)
    got = {key: clean(section_of(sec, label)) for key, label in SECTIONS.items()}
    blob = find_json_block(body) or {}

    def pick(key: str, *json_keys: str) -> str:
        v = got.get(key, "")
        if v:
            return v
        for jk in json_keys:
            jv = blob.get(jk)
            if isinstance(jv, str) and jv.strip():
                return jv.strip()
        return ""

    kind_raw = pick("kind", "kind")
    # json 块里可能直接写的是清单用的值（角色/世界书/故事）
    for label, value in KINDS.items():
        if kind_raw == value:
            kind_raw = label
    if not kind_raw:
        raise Reject(
            "没找到「投稿类型」。请从仓库的「内容投稿」表单开单，或在正文里放一段 "
            "```json 的条目块（键 `kind`：角色卡 / 世界书 / 故事）。"
        )
    if kind_raw not in KINDS:
        raise Reject(f"投稿类型「{kind_raw}」不认识，只收：{' / '.join(KINDS)}。")

    form_raw = pick("form", "form")
    if form_raw in FORM_LABELS:
        form = FORM_LABELS[form_raw]
    elif form_raw in ("", "experience", "play", "tool"):
        form = form_raw
    elif form_raw in ("陪伴",):
        form = ""
    else:
        raise Reject(f"角色形态「{form_raw}」不认识，只收：陪伴 / 多线 / 玩法 / 工具 / 不适用。")

    sub = {
        "kind": kind_raw,
        "manifest_kind": KINDS[kind_raw],
        "form": form,
        "theme": pick("theme", "theme"),
        "id": pick("id", "id"),
        "name": pick("name", "name"),
        "tagline": pick("tagline", "tagline"),
        "intro": pick("intro", "intro"),
        # 合并后的单一「文件」栏；旧两栏名保留兜底（老单子/手写正文）
        "file": got.get("file", "") or got.get("file_link", "") or got.get("attach", ""),
        "agree": got.get("agree", ""),
        "body": body or "",
        "source": "json" if blob else "form",
    }
    return sub


def asset_candidates(sub: dict) -> list[str]:
    """候选文件地址：直链字段 → 正文里的附件链接 → 正文里别的 http 链接。

    只认"能当文件下"的地址；``user-attachments`` 是 Issue 里拖拽上传后的形态。
    """
    out: list[str] = []

    def push(u: str) -> None:
        u = u.strip().strip("<>()[]{}\"'").rstrip(".,;")
        if u and u not in out:
            out.append(u)

    file_raw = (sub.get("file", "") or "").strip()
    # 表单里拖进来的图，GitHub 把这一栏写成 HTML 的 <img src="…" />（不是 markdown 的 ![](…)），
    # 所以链接要从标记里抽；而"整串当本地路径"这条兜底只在**不像标记**时才敢用——
    # 否则会拿 `<img width="2048" …>` 去 open()，报一句投稿人看不懂的 "No such file or directory"。
    for m in re.finditer(r"https?://[^\s)\]<>\"']+", file_raw):
        push(m.group(0))
    looks_like_markup = bool(re.search(r"<[a-zA-Z/]|!\[|\]\(", file_raw))
    if file_raw and not file_raw.startswith("http") and "\n" not in file_raw and not looks_like_markup:
        push(file_raw)  # 本地路径 / file:// —— 只为维护者本地重跑方便

    body = sub.get("body", "")
    for m in re.finditer(r"https?://[^\s)\]<>\"']*(?:user-attachments|raw\.githubusercontent|gist\.githubusercontent)[^\s)\]<>\"']*", body):
        push(m.group(0))
    return out


# ── 资产校验 ────────────────────────────────────────────────────────────
def png_card_problem(raw: bytes) -> str:
    """PNG 卡的自检：签名 + ``chara``/``ccv3`` tEXt 块（与 App 的 PngCardCodec 同一口径）。"""
    if raw[:8] != b"\x89PNG\n\x1a\n":
        return "不是 PNG"
    pos, found = 8, False
    while pos + 8 <= len(raw):
        ln = struct.unpack(">I", raw[pos:pos + 4])[0]
        tag = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + ln]
        if tag in (b"tEXt", b"zTXt") and data.startswith((b"chara\x00", b"ccv3\x00")):
            found = True
            break
        if tag == b"IEND":
            break
        pos += 12 + ln
    return "" if found else "PNG 里没有 chara 卡数据块（不是角色卡，或卡被二次编辑过）"


def json_card_problem(raw: bytes) -> str:
    try:
        data = json.loads(raw.decode("utf-8-sig"))
    except Exception as exc:
        return f"不是合法 JSON（{exc}）"
    if not isinstance(data, dict):
        return "JSON 顶层不是对象"
    inner = data.get("data") if isinstance(data.get("data"), dict) else data
    if not (inner.get("name") or inner.get("character_name")):
        return "JSON 里没有 name / character_name"
    return ""


def book_problem(raw: bytes) -> str:
    try:
        data = json.loads(raw.decode("utf-8-sig"))
    except Exception as exc:
        return f"不是合法 JSON（{exc}）"
    if not isinstance(data, dict):
        return "世界书顶层不是对象"
    entries = data.get("entries")
    if isinstance(entries, list):
        n = len(entries)
    elif isinstance(entries, dict):
        n = len(entries)
    else:
        return "没有 entries（世界书要 SillyTavern 世界书面板导出的形状）"
    return "" if n > 0 else "entries 是空的"


def check_asset_bytes(sub: dict, raw: bytes) -> tuple[str, str]:
    """→ (扩展名, 说明)。不合格直接 Reject。"""
    if not raw:
        raise Reject("文件是空的。")
    if len(raw) > MAX_ASSET_BYTES:
        raise Reject(f"文件 {len(raw) / 1048576:.1f} MB，超过 16 MB 上限。")
    kind = sub["kind"]
    if kind == KIND_CARD:
        if raw[:8] == b"\x89PNG\n\x1a\n":
            problem = png_card_problem(raw)
            if problem:
                raise Reject(problem + "。请用鲸鱼／酒馆导出的 PNG 卡（原图别再过截图工具）。")
            return "png", f"PNG 卡（{len(raw) / 1048576:.2f} MB，含 chara 卡数据）"
        problem = json_card_problem(raw)
        if problem:
            raise Reject("角色卡读不出来：" + problem)
        return "json", f"JSON 卡（{len(raw) / 1024:.0f} KB）"
    if kind == KIND_BOOK:
        problem = book_problem(raw)
        if problem:
            raise Reject("世界书读不出来：" + problem)
        return "json", f"世界书 JSON（{len(raw) / 1024:.0f} KB）"
    if not raw.strip():
        raise Reject("故事内容为空。")
    return "json", f"故事文件（{len(raw) / 1024:.0f} KB）"


# ── 取文件 ──────────────────────────────────────────────────────────────
# 只给"确实吃这个头"的 GitHub 主机带 token。别的主机（尤其 302 之后的存储地址）见不得它。
AUTH_HOSTS = {"api.github.com", "raw.githubusercontent.com", "gist.githubusercontent.com", "codeload.github.com"}


class _MixinNoAuthAcrossHosts(urllib.request.HTTPRedirectHandler):
    """跨主机跳转时摘掉 Authorization。

    Issue 里拖进来的附件是 ``github.com/user-attachments/<uuid>``，它 302 到
    ``github-production-user-asset-*.s3.amazonaws.com`` 的**签名地址**；urllib 默认把
    请求头原样带过去，S3 见到签名之外的 Authorization 直接回 ``400 Bad Request`` ——
    单据上就显示成"文件没下下来"，而那个地址单独用浏览器/不带 token 打开是好好的。
    签名地址自带凭据，本来也不需要这个头。
    """

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new is None:
            return None
        if urllib.parse.urlsplit(newurl).hostname != urllib.parse.urlsplit(req.full_url).hostname:
            for store in (new.headers, new.unredirected_hdrs):
                for k in [k for k in store if k.lower() == "authorization"]:
                    del store[k]
        return new


_OPENER = urllib.request.build_opener(_MixinNoAuthAcrossHosts)


def fetch_bytes(url: str) -> bytes:
    if url.startswith("file://"):
        return Path(urllib.parse.urlparse(url).path).read_bytes()
    if not url.startswith("http"):
        return Path(url).read_bytes()
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token and urllib.parse.urlsplit(url).hostname in AUTH_HOSTS:
        req.add_header("Authorization", f"Bearer {token}")
    with _OPENER.open(req, timeout=60) as resp:
        return resp.read()


def fetch_first(candidates: list[str]) -> tuple[bytes, str]:
    """按序试下载，第一个成功的胜出；全失败时把每个地址的失败原因都带回去。"""
    if not candidates:
        raise Reject(
            "没找到文件。两条路选一条：把卡 PNG / 世界书 JSON 直接拖进「或直接把文件拖进来」那一栏，"
            "或填一个公开直链（gist raw、你自己仓库的 raw 都行）。"
        )
    errors = []
    for url in candidates:
        try:
            return fetch_bytes(url), url
        except Exception as exc:  # noqa: BLE001 —— 逐个试，失败原因要原样带给人看
            errors.append(f"- `{url}` → {exc}")
    raise Reject("文件都没下下来：\n" + "\n".join(errors))


# ── 清单写入 ────────────────────────────────────────────────────────────
def load_manifest(path: Path) -> tuple[dict, str]:
    """读清单，连同行尾一起带出来（这份文件是 CRLF 提交的，重写时不能翻成 LF）。

    ⚠ 不能用 `read_text()`：它按通用换行把 CRLF 折成 LF，行尾风格当场丢失（实测过一次）。
    """
    text = path.read_bytes().decode("utf-8")
    eol = "\n" if "\n" in text else "\n"
    return json.loads(text), eol


def dump_manifest(path: Path, data: dict, eol: str) -> None:
    text = json.dumps(data, ensure_ascii=False, indent=2)
    path.write_bytes(text.replace("\n", eol).encode("utf-8"))


def find_or_make_pack(manifest: dict, theme: str, issue_number: str) -> tuple[dict, str]:
    packs = manifest.setdefault("packs", [])
    for pack in packs:
        if pack.get("title") == theme:
            return pack, ""
    slug = re.sub(r"[^a-z0-9-]+", "", theme.lower())[:32].strip("-")
    pack_id = f"theme-{slug}" if slug else f"theme-issue{issue_number}"
    pack = {
        "id": pack_id,
        "title": theme,
        "tagline": "",
        "cover": "",
        "cards": [],
    }
    packs.append(pack)
    return pack, f"新建了主题包「{theme}」（id `{pack_id}`，封面与简介待补）"


def upsert_entry(pack: dict, entry: dict) -> str:
    cards = pack.setdefault("cards", [])
    for i, old in enumerate(cards):
        if old.get("id") == entry["id"]:
            cards[i] = entry
            return "更新了同名条目"
    cards.append(entry)
    return "新增条目"


def make_thumb(png_bytes: bytes, out: Path) -> str:
    """224×224 方形缩略图；没装 Pillow 就跳过（清单里 thumb 留空，UI 退回纯文字 tile）。"""
    try:
        import io

        from PIL import Image
    except Exception:  # noqa: BLE001
        return ""
    try:
        im = Image.open(io.BytesIO(png_bytes)).convert("RGB")
        side = min(im.size)
        left = (im.width - side) // 2
        top = (im.height - side) // 2
        im = im.crop((left, top, left + side, top + side)).resize((THUMB_SIZE, THUMB_SIZE), Image.LANCZOS)
        im.save(out, "JPEG", quality=82)
        return out.name
    except Exception:  # noqa: BLE001
        return ""


# ── 校验汇总 ────────────────────────────────────────────────────────────
def validate(sub: dict) -> list[str]:
    """字段级校验；返回给投稿人看的问题清单（空 = 可以通过）。"""
    problems: list[str] = []
    sid = sub["id"]
    if not ID_RE.match(sid or ""):
        problems.append("「标识」要是**英文小写短名**（a-z 0-9 短横线，2~40 位），例：`tongzhuo-luzhao`；不填我会按单号起一个。")
    if not sub["name"]:
        problems.append("「名称」没填。")
    if len(sub["name"]) > 24:
        problems.append("「名称」超过 24 字，卡片上放不下。")
    if not sub["theme"]:
        problems.append("「归属主题」没选。")
    if len(sub["tagline"]) > 40:
        problems.append("「一句话」超过 40 字（卡片上会截断）。")
    if not sub["intro"]:
        problems.append("「内容简介」没填（3~5 句，讲清它是什么、怎么玩）。")
    agree = sub["agree"] or sub["body"]
    boxes = re.findall(r"-\s*\[([ xX])\]", agree)
    if len(boxes) < 3:
        problems.append("「合规确认」三行没都在，请用仓库的「内容投稿」表单开单。")
    elif any(b.strip().lower() != "x" for b in boxes):
        problems.append("「合规确认」还有没勾的项（三项都要勾）。")
    return problems


def entry_of(sub: dict, file_name: str, thumb_name: str) -> dict:
    return {
        "id": sub["id"],
        "name": sub["name"],
        "tagline": sub["tagline"],
        "file": file_name,
        "thumb": thumb_name,
        "kind": sub["manifest_kind"],
        "form": sub["form"],
    }


def build_comment(sub: dict, problems: list[str], notes: list[str], entry: dict | None, adopted: bool) -> str:
    head = "## 🐳 内容投稿 · 收稿校验"
    lines = [MARKER, head, ""]
    if problems:
        lines += [f"**结果：不通过 ❌**（{len(problems)} 处要改）", ""]
        lines += [f"{i}. {p}" for i, p in enumerate(problems, 1)]
        lines += ["", "改完 Issue 会自动重跑一次；都对了我再回一句 `采用`。"]
    else:
        lines += ["**结果：通过 ✅**", ""]
        lines += [
            "| 项 | 值 |",
            "|---|---|",
            f"| 投稿类型 | {sub['kind']} |",
            f"| 名称 | {sub['name']}（`{sub['id']}`） |",
            f"| 归属主题 | {sub['theme']} |",
            f"| 一句话 | {sub['tagline'] or '—'} |",
        ]
        if sub["kind"] == KIND_CARD:
            form_label = next((k for k, v in FORM_LABELS.items() if v == sub["form"]), sub["form"] or "陪伴")
            lines.append(f"| 形态 | {form_label} |")
        if entry:
            lines.append(f"| 清单条目 | `{json.dumps(entry, ensure_ascii=False)}` |")
    for note in notes:
        if note:
            lines.append(f"\n> ⚠ {note}")
    if not problems and not adopted:
        lines += [
            "",
            "---",
            "",
            "维护者这边确认后，**在本单打上 `采用` 标签**（或评论一句「采用」后由维护者加标签），"
            "机器人会把文件与清单条目一起提交进 `dist/discover/`，App 下次刷新发现页即可见。",
        ]
    if adopted:
        lines += ["", "---", "", "已上架 ✅ 文件与清单已提交进 `dist/discover/`。"]
    return "\n".join(lines)


# ── GitHub 回帖 ─────────────────────────────────────────────────────────
def gh(method: str, path: str, payload: dict | None = None) -> object:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not token or not repo:
        raise RuntimeError("缺 GH_TOKEN / GITHUB_REPOSITORY，无法回帖")
    url = f"https://api.github.com/repos/{repo}{path}"
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", USER_AGENT)
    if data:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = resp.read()
    return json.loads(body) if body else None


def upsert_comment(issue: str, text: str) -> None:
    """同一单只留一条校验评论：找到带标记那条就改，没有就新开一条。"""
    page = 1
    while True:
        items = gh("GET", f"/issues/{issue}/comments?per_page=100&page={page}") or []
        for c in items:
            if MARKER in (c.get("body") or ""):
                gh("PATCH", f"/issues/comments/{c['id']}", {"body": text})
                return
        if len(items) < 100:
            break
        page += 1
    gh("POST", f"/issues/{issue}/comments", {"body": text})


def finalize(issue: str, comment_file: Path) -> None:
    """收尾：回帖 ＋ 只有**真落盘**了才打标签并关单。

    「采用」有了反向的「下架」（2026-10-14），所以标签分两种口径，靠 RESULT 文件里的
    ``action`` 分辨：上架打 `已上架`／摘 `采用`，下架打 `已下架`／摘 `下架`。
    """
    result: dict = {}
    if RESULT_FILE_PATH.is_file():
        try:
            result = json.loads(RESULT_FILE_PATH.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001 —— 结果文件坏了就当没落盘
            result = {}
    landed = bool(result)
    is_retract = result.get("action") == "retract"
    done, trigger = ("已下架", "下架") if is_retract else ("已上架", "采用")
    if comment_file.is_file():
        text = comment_file.read_text(encoding="utf-8")
    else:
        head = "内容投稿 · 下架" if is_retract else "内容投稿 · 采用上架"
        text = (f"{MARKER}\n## 🐳 {head}\n\n**结果：机器人出错了 ❌**\n\n"
                f"这一轮没能落盘，请维护者手动处理（或摘掉 `{trigger}` 再打一次）。")
    if not landed:
        text += f"\n\n> 说明：本轮没有改动 `dist/discover/`，所以**没有**打「{done}」、也没有关单。"
        upsert_comment(issue, text)
        return
    upsert_comment(issue, text)
    gh("POST", f"/issues/{issue}/labels", {"labels": [done]})
    try:
        gh("DELETE", f"/issues/{issue}/labels/{urllib.parse.quote(trigger)}")
    except Exception:  # noqa: BLE001 —— 标签本来就不在也无所谓
        pass
    gh("PATCH", f"/issues/{issue}", {"state": "closed", "state_reason": "completed"})


# ── 下架（与「采用」对称的反向通道） ────────────────────────────────────
def locate_entry(manifest: dict, sub: dict, issue: str) -> tuple[dict, dict, str] | None:
    """→ (所在分组, 清单条目, 怎么认出来的)；找不到返回 None。

    三级定位：① 表单里写的标识 → ② ``sub-<单号>``（标识留空时上架自动起的那个）→ ③ 名称。
    ⚠ 名称兜底**有风险**（同名卡可能不止一张），所以只在前面都落空时用，并把"靠名字认的"写进回帖。
    """
    packs = manifest.get("packs") or []
    for want in [x for x in (sub.get("id"), f"sub-{issue}") if x]:
        for pack in packs:
            for entry in pack.get("cards") or []:
                if entry.get("id") == want:
                    return pack, entry, f"标识 `{want}`"
    name = (sub.get("name") or "").strip()
    if name:
        same_theme = [p for p in packs if sub.get("theme") and p.get("title") == sub.get("theme")]
        for pack in same_theme + [p for p in packs if p not in same_theme]:
            for entry in pack.get("cards") or []:
                if (entry.get("name") or "").strip() == name:
                    return pack, entry, f"名称「{name}」（没找到标识，靠名字认的）"
    return None


def drop_credits_lines(path: Path, name: str) -> str:
    """图源清单里有这个名字就去掉对应行；没有就什么都不做（投稿内容通常不在那里）。"""
    if not name or not path.is_file():
        return ""
    raw = path.read_bytes().decode("utf-8")
    lines = raw.splitlines()
    kept = [ln for ln in lines if name not in ln]
    if len(kept) == len(lines):
        return ""
    path.write_bytes(("\n".join(kept) + "\n").encode("utf-8"))
    return f"图源清单 `credits.txt` 里去掉了 {len(lines) - len(kept)} 行"


def run_retract(issue: str, sub: dict, root: Path, comment_out: Path, post: bool) -> int:
    """下架：删清单条目 ＋ 删文件（分组空了连封面一起删）＋ 回帖。

    ⚠ 只对**还没下载的人**生效——已导入的人是撤回不了的（本地优先：文件早在他设备上）。
    """
    manifest_path = root / "dist" / "discover" / "discover-manifest.json"
    manifest, eol = load_manifest(manifest_path)
    name = sub.get("name") or "（未填名称）"
    hit = locate_entry(manifest, sub, issue)

    if not hit:
        text = "\n".join([
            MARKER, "## 🐳 内容投稿 · 下架", "",
            "**结果：没找到这条上架记录 ⚠️**", "",
            f"按 标识／名称 在 `dist/discover/discover-manifest.json` 里找过，没有「{name}」。",
            "可能是它还没上架、或者已经下架过了。",
            "",
            "> **没有改动任何文件**，标签与单子也原样留着——你确认一下再说。",
        ])
        comment_out.write_text(text, encoding="utf-8")
        print(text)
        if post:
            upsert_comment(issue, text)
        return 0

    pack, entry, how = hit
    removed: list[str] = []
    for key in ("file", "thumb"):
        fname = (entry.get(key) or "").strip()
        if not fname:
            continue
        target = manifest_path.parent / fname
        if target.is_file():
            target.unlink()
            removed.append(fname)

    pack["cards"] = [e for e in (pack.get("cards") or []) if e is not entry]
    pack_gone = not pack["cards"]
    if pack_gone:
        cover = (pack.get("cover") or "").strip()
        if cover and (manifest_path.parent / cover).is_file():
            (manifest_path.parent / cover).unlink()
            removed.append(cover)
        manifest["packs"] = [p for p in (manifest.get("packs") or []) if p is not pack]

    credits_note = drop_credits_lines(manifest_path.parent / "credits.txt", entry.get("name") or "")
    from datetime import date

    manifest["updated"] = date.today().isoformat()
    dump_manifest(manifest_path, manifest, eol)

    lines = [
        MARKER, "## 🐳 内容投稿 · 下架", "",
        "**结果：已下架 ✅**", "",
        "| 项 | 值 |", "|---|---|",
        f"| 下架的条目 | {entry.get('name') or '—'}（`{entry.get('id') or '—'}`） |",
        f"| 原属分组 | {pack.get('title') or '—'}"
        f"{'（该组已空，连封面一起删了）' if pack_gone else ''} |",
        f"| 认出方式 | {how} |",
        f"| 删掉的文件 | {'、'.join('`' + x + '`' for x in removed) if removed else '（清单里没有文件字段）'} |",
    ]
    if credits_note:
        lines.append(f"| 其它 | {credits_note} |")
    lines += [
        "",
        "> 已下载过的人**撤不回来**——本地优先的应用，文件早就在他自己设备上了。",
        "> 这次停掉的是**新的**分发：别人下次打开发现页就见不到它了。",
    ]
    text = "\n".join(lines)
    comment_out.write_text(text, encoding="utf-8")
    print(text)
    RESULT_FILE_PATH.write_text(
        json.dumps({"ok": True, "action": "retract", "id": entry.get("id"),
                    "name": entry.get("name"), "files": removed, "pack_removed": pack_gone},
                   ensure_ascii=False),
        encoding="utf-8",
    )
    if post:
        upsert_comment(issue, text)
    return 0


# ── 主流程 ──────────────────────────────────────────────────────────────
def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def run(args: argparse.Namespace) -> int:
    comment_out = Path(args.comment_out or COMMENT_FILE)

    if args.mode == "finalize":
        # 收尾只做回帖/标签/关单：正文与清单都已在上一步落好，这里不重新解析
        finalize(str(args.issue or os.environ.get("ISSUE_NUMBER", "") or "0"), comment_out)
        return 0

    body = ""
    if args.body_file:
        body = Path(args.body_file).read_text(encoding="utf-8")
    elif os.environ.get("ISSUE_BODY") is not None:
        body = os.environ["ISSUE_BODY"]
    elif not sys.stdin.isatty():
        body = sys.stdin.read()

    issue = str(args.issue or os.environ.get("ISSUE_NUMBER", "") or "0")
    root = Path(args.repo_root).resolve() if args.repo_root else repo_root()

    try:
        sub = parse_submission(body)
    except Reject as exc:
        stub = {"kind": "—", "name": "—", "id": "—", "theme": "—", "tagline": "", "form": ""}
        text = build_comment(stub, [str(exc)], [], None, False)
        comment_out.write_text(text, encoding="utf-8")
        print(text)
        if args.post:
            upsert_comment(issue, text)
        return 1

    notes: list[str] = []
    entry = None
    raw = None
    ext = ""
    asset_note = ""
    url_used = ""

    # 标识可以不填（表单瘦身 2026-10-14）：按单号起一个，别让投稿人为了一个文件名想英文短名。
    # 放在 validate 之前——填了但不合法仍然会报错（那是他自己写了东西，得让他知道），留空则直接通过。
    if not sub["id"]:
        sub["id"] = f"sub-{issue}"
        notes.append(f"标识留空 ⇒ 自动用 `sub-{issue}`（想换成好记的短名，回一句即可）")

    if args.mode == "retract":
        return run_retract(issue, sub, root, comment_out, args.post)

    problems = validate(sub)

    if not problems:
        try:
            raw, url_used = fetch_first(asset_candidates(sub))
            ext, asset_note = check_asset_bytes(sub, raw)
        except Reject as exc:
            problems.append(str(exc))

    if not problems and args.mode in ("adopt", "check"):
        file_name = f"{FILE_PREFIX[sub['kind']]}-{sub['id']}.{ext}"
        thumb_name = ""
        manifest_path = root / "dist" / "discover" / "discover-manifest.json"
        manifest, eol = load_manifest(manifest_path)
        pack, pack_note = find_or_make_pack(manifest, sub["theme"], issue)
        if pack_note:
            notes.append(pack_note)
        entry = entry_of(sub, file_name, f"thumb-{sub['id']}.jpg" if sub["kind"] == KIND_CARD else "")
        if args.mode == "adopt":
            out_dir = manifest_path.parent
            (out_dir / file_name).write_bytes(raw)
            if sub["kind"] == KIND_CARD and ext == "png":
                made = make_thumb(raw, out_dir / f"thumb-{sub['id']}.jpg")
                entry["thumb"] = made
                if not made:
                    notes.append("没装 Pillow／整形失败，缩略图留空（发现页该卡退回纯文字 tile）。")
            notes.append(upsert_entry(pack, entry))
            from datetime import date

            manifest["updated"] = date.today().isoformat()
            dump_manifest(manifest_path, manifest, eol)
            RESULT_FILE_PATH.write_text(
                json.dumps({"ok": True, "id": sub["id"], "name": sub["name"], "file": file_name}, ensure_ascii=False),
                encoding="utf-8",
            )
        else:
            notes.append(f"**预览**（`--check` 不落盘）：文件将写成 `{file_name}`，清单条目见上表。")
        if asset_note:
            notes.append(f"文件：{asset_note}〔来源 `{url_used}`〕")

    adopted = args.mode == "adopt" and not problems
    text = build_comment(sub, problems, notes, entry, adopted)
    comment_out.write_text(text, encoding="utf-8")
    print(text)

    if args.post:
        upsert_comment(issue, text)
    return 0 if not problems else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="发现页内容投稿通道")
    mode = ap.add_mutually_exclusive_group()
    mode.add_argument("--check", dest="mode", action="store_const", const="check", help="收稿校验（默认）")
    mode.add_argument("--adopt", dest="mode", action="store_const", const="adopt", help="采用上架（落盘 + 写清单）")
    mode.add_argument("--finalize", dest="mode", action="store_const", const="finalize", help="回帖 + 打标签 + 关单")
    mode.add_argument("--retract", dest="mode", action="store_const", const="retract",
                      help="下架：删清单条目与文件（维护者打「下架」标签触发）")
    ap.set_defaults(mode="check")
    ap.add_argument("--body-file", help="Issue 正文文件；不给就读 $ISSUE_BODY 或 stdin")
    ap.add_argument("--issue", help="Issue 编号")
    ap.add_argument("--repo-root", help="仓库根（默认＝本脚本上溯三级）")
    ap.add_argument("--comment-out", help=f"评论正文落盘路径（默认 {COMMENT_FILE}）")
    ap.add_argument("--post", action="store_true", help="校验后把评论贴回 Issue（需 GH_TOKEN）")
    args = ap.parse_args()
    try:
        return run(args)
    except Exception as exc:  # noqa: BLE001 —— 崩了也要给单子留一句话，别让人干等
        text = (
            f"{MARKER}\n## 🐳 内容投稿通道 · 机器人出错\n\n"
            f"**{type(exc).__name__}**: {exc}\n\n"
            "这不是你的问题，是这边脚本没跑通。维护者看到会处理。"
        )
        try:
            Path(args.comment_out or COMMENT_FILE).write_text(text, encoding="utf-8")
        except Exception:  # noqa: BLE001
            pass
        print(text, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
