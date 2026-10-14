#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""鲸鱼皮肤投稿 · 收稿校验与采用上架（零服务器，GitHub Issue 单轨）。

和内容投稿（同仓的 `submission.py` → `dist/discover/`）是同一个形状：**收稿走 Issue、分发走静态文件**。
两者共用主仓的一个 Issues 列表（两张表单选题型），只有上架目录不同。这里收的是皮肤包
（`.whale-skin.json`，纯声明式 JSON，几 KB 文本），所以一张单子就是一份包——
正文里那段 ```json 直接原样收下，不用下载附件。

三个动作：

- ``--check``    收稿校验：解析皮肤包 + 验令牌 + 量对比度，回一条可更新的评论。
- ``--adopt``    采用上架：写 ``dist/skins/<id>.json``，更新 ``dist/skins/index.json``。
- ``--finalize`` 收尾：回帖、打 ``已上架``、摘 ``采用``、关单（只有真落盘才关）。

本地重跑：

    python .github/scripts/skin_submission.py --check --body-file issue.md --comment-out /tmp/c.md
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import tempfile
import traceback
import urllib.parse
import urllib.request
from pathlib import Path

MARKER = "<!-- whale-skin-check -->"
SCRATCH = tempfile.gettempdir()
COMMENT_FILE = os.path.join(SCRATCH, "whale-skin-comment.md")
RESULT_FILE = os.path.join(SCRATCH, "whale-skin-result.json")
RESULT_FILE_PATH = Path(RESULT_FILE)

ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]{1,39}$")
COLOR_RE = re.compile(r"^#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")

# 封闭枚举，与 App 侧一致（未知值一律回落到默认，但收稿这关先说清楚）
MOTIFS = {"none", "sticker", "postmark", "filmstrip"}
FONTS = {"sans", "serif", "notoserif", "yozai", "wenkai", "notomono"}
SHADOWS = {"none", "sm", "md", "lg"}

# 皮肤令牌的关键键（缺了会回落简约皮肤，但一张"没写主色"的皮肤基本等于没写）
KEY_TOKENS = ["paper", "surface", "surface2", "ink", "ink2", "ink3",
              "hairline", "hairline2", "accent", "onAccent", "soft", "softLine", "danger"]

USER_AGENT = "whale-skin-channel/1"


class Reject(Exception):
    """投稿不合格：给用户看的理由。"""


# ── 解析 ────────────────────────────────────────────────────────────────
def split_sections(body: str) -> dict[str, str]:
    out: dict[str, list[str]] = {}
    cur = None
    for line in (body or "").splitlines():
        if line.startswith("### "):
            cur = line[4:].strip()
            out.setdefault(cur, [])
        elif cur is not None:
            out[cur].append(line)
    return {k: "\n".join(v).strip() for k, v in out.items()}


def section_of(sec: dict[str, str], label: str) -> str:
    if label in sec:
        return sec[label]
    for head, text in sec.items():
        if head.startswith(label):
            return text
    return ""


def find_spec(body: str) -> dict:
    """正文里第一段 ```json 就是皮肤包（App 的预填正文就是这么生成的）。

    没围栏也认：从第一个 `{` 扫到配对的那个 `}` —— 表单字段的 ``render`` 取值各版本不一，
    投稿人不该因为少写三个反引号就被打回。
    """
    m = re.search(r"```json\s*\n(.*?)```", body or "", re.S)
    raw = m.group(1) if m else ""
    if not raw:
        start = (body or "").find("{")
        if start >= 0:
            depth, in_str, esc = 0, False, False
            for i in range(start, len(body)):
                ch = body[i]
                if in_str:
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == '"':
                        in_str = False
                    continue
                if ch == '"':
                    in_str = True
                elif ch == "{":
                    depth += 1
                elif ch == "}":
                    depth -= 1
                    if depth == 0:
                        raw = body[start:i + 1]
                        break
    if not raw:
        raise Reject(
            "没找到皮肤包。两条路：① 在 App 里「我的 → 皮肤 → 投稿」（正文会自动带上包）；"
            "② 在正文里贴一段皮肤包 JSON（`.whale-skin.json` 的内容，放在 ```json 代码块里最稳）。"
        )
    try:
        data = json.loads(raw)
    except Exception as exc:  # noqa: BLE001
        raise Reject(f"这段 JSON 读不出来：{exc}")
    if not isinstance(data, dict):
        raise Reject("皮肤包顶层应该是一个对象。")
    return data


def parse_submission(body: str) -> dict:
    sec = split_sections(body)
    spec = find_spec(body)

    def pick(label: str, *keys: str) -> str:
        v = (section_of(sec, label) or "").strip()
        if v and v != "_No response_":
            return v
        for k in keys:
            if isinstance(spec.get(k), str) and spec[k].strip():
                return spec[k].strip()
        return ""

    one_liner = pick("这套皮肤想营造什么感觉", "oneLiner")
    if not one_liner:
        # App 的预填正文把自述写成粗体小标题 + 下一行，认一下这个形状
        m = re.search(r"\*\*这套皮肤想营造什么感觉\*\*\s*\n+(.+?)(?:\n\s*\n|$)", body or "", re.S)
        if m and not m.group(1).strip().startswith("_"):
            one_liner = m.group(1).strip()
    return {
        "spec": spec,
        "id": (spec.get("id") or "").strip(),
        "name": (spec.get("name") or "").strip(),
        "one_liner": one_liner,
        "author": os.environ.get("ISSUE_AUTHOR", "").strip(),
        "body": body or "",
    }


# ── 校验 ────────────────────────────────────────────────────────────────
def luminance(hex_color: str) -> float:
    body = hex_color.lstrip("#")
    if len(body) == 8:
        body = body[2:]  # 带 alpha 的按 RGB 部分算
    if len(body) != 6:
        return 0.0
    r, g, b = (int(body[i:i + 2], 16) / 255 for i in (0, 2, 4))

    def lin(c: float) -> float:
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)


def contrast(a: str, b: str) -> float:
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def validate(sub: dict) -> tuple[list[str], list[str]]:
    """→ (非改不可的问题, 建议)。"""
    problems: list[str] = []
    notes: list[str] = []
    spec = sub["spec"]

    if not ID_RE.match(sub["id"]):
        problems.append("皮肤包的 `id` 要是英文小写短名（a-z 0-9 短横线，2~40 位），它同时是文件名。")
    if not sub["name"]:
        problems.append("皮肤包的 `name` 没填。")
    if not sub["one_liner"]:
        notes.append("没写「想营造什么感觉」，建议补一句（我上架时会拿它当简介）。")

    boxes = re.findall(r"-\s*\[([ xX])\]", sub["body"])
    if len(boxes) < 3:
        notes.append("没读到三条合规确认（表单或 App 预填正文里都有，勾掉即可）。")
    elif any(b.strip().lower() != "x" for b in boxes):
        problems.append("「合规确认」还有没勾的项（配色来源 / 字体许可 / 只含声明式数据）。")

    for tone in ("light", "dark"):
        table = spec.get(tone)
        if not isinstance(table, dict) or not table:
            problems.append(f"`{tone}` 色板是空的——浅色与深色两档都要有。")
            continue
        missing = [k for k in KEY_TOKENS if k not in table]
        if missing:
            notes.append(f"`{tone}` 少这些键（会回落简约皮肤的值）：{'、'.join(missing)}")
        bad = [f"{k}={v}" for k, v in table.items() if not (isinstance(v, str) and COLOR_RE.match(v))]
        if bad:
            problems.append(f"`{tone}` 里这些不是 `#RRGGBB` / `#AARRGGBB`：{'、'.join(bad[:6])}")

    fonts = spec.get("fonts") or {}
    if isinstance(fonts, dict):
        for slot, value in fonts.items():
            if slot in ("ui", "chat", "meta") and value not in FONTS:
                problems.append(f"字体槽 `{slot}` 的值 `{value}` 不认识，只收：{' / '.join(sorted(FONTS))}。")
    motif = spec.get("motif", "none")
    if motif not in MOTIFS:
        problems.append(f"`motif` 的值 `{motif}` 不认识，只收：{' / '.join(sorted(MOTIFS))}。")
    shape = spec.get("shape") or {}
    if isinstance(shape, dict):
        for k in ("r1", "r2", "r3"):
            v = shape.get(k)
            if v is not None and (not isinstance(v, int) or not 0 <= v <= 48):
                problems.append(f"圆角 `shape.{k}` 要在 0~48 之间（现在是 `{v}`）。")
        if shape.get("shadow") and shape["shadow"] not in SHADOWS:
            notes.append(f"`shape.shadow` = `{shape['shadow']}` 不在已知档位里，会按不投影处理。")

    # 对比度：这是皮肤最容易翻车的地方（好看但看不清）
    for tone in ("light", "dark"):
        table = spec.get(tone)
        if not isinstance(table, dict):
            continue
        ink, paper = table.get("ink"), table.get("paper")
        if isinstance(ink, str) and isinstance(paper, str) and COLOR_RE.match(ink) and COLOR_RE.match(paper):
            ratio = contrast(ink, paper)
            if ratio < 4.5:
                notes.append(f"`{tone}` 正文色对纸面只有 **{ratio:.2f}:1**（正文建议 ≥4.5:1）。")
    return problems, notes


def entry_of(sub: dict) -> dict:
    spec = sub["spec"]
    return {
        "id": sub["id"],
        "name": sub["name"],
        "oneLiner": sub["one_liner"],
        "author": sub["author"],
        "file": f"dist/skins/{sub['id']}.json",
        "specVersion": int(spec.get("version", 1) or 1),
    }


# ── 清单写入 ────────────────────────────────────────────────────────────
def load_index(path: Path) -> dict:
    if not path.is_file():
        return {"version": 1, "updated": "", "skins": []}
    return json.loads(path.read_bytes().decode("utf-8"))


def dump_index(path: Path, data: dict) -> None:
    path.write_bytes((json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode("utf-8"))


def upsert(index: dict, entry: dict) -> str:
    for i, old in enumerate(index.setdefault("skins", [])):
        if old.get("id") == entry["id"]:
            index["skins"][i] = entry
            return "更新了同名皮肤"
    index["skins"].append(entry)
    return "新增皮肤"


# ── 回帖 ────────────────────────────────────────────────────────────────
def build_comment(sub: dict, problems: list[str], notes: list[str], entry: dict | None,
                  adopted: bool, checked: bool) -> str:
    lines = [MARKER, "## 🎨 皮肤投稿 · 收稿校验", ""]
    if problems:
        lines += [f"**结果：不通过 ❌**（{len(problems)} 处要改）", ""]
        lines += [f"{i}. {p}" for i, p in enumerate(problems, 1)]
        lines += ["", "改完 Issue 会自动重跑；都对了我再打 `采用`。"]
    else:
        lines += ["**结果：通过 ✅**", ""]

    spec = sub["spec"]
    tones = []
    for tone, label in (("light", "浅色"), ("dark", "深色")):
        table = spec.get(tone)
        if isinstance(table, dict):
            tones.append(f"{label} `{table.get('paper', '—')}` / 主色 `{table.get('accent', '—')}` / 正文 `{table.get('ink', '—')}`")
    if tones:
        lines += ["| 项 | 值 |", "|---|---|",
                  f"| 皮肤 | {sub['name'] or '—'}（`{sub['id'] or '—'}`） |",
                  f"| 一句话 | {sub['one_liner'] or '—'} |"]
        lines += [f"| 色板 | {t} |" for t in tones]
        lines.append(f"| 结构 | 圆角 `{spec.get('shape', {}).get('r1', '—')}/"
                     f"{spec.get('shape', {}).get('r2', '—')}/{spec.get('shape', {}).get('r3', '—')}`，"
                     f"贴纸 `{spec.get('motif', 'none')}` |")
    if entry:
        lines.append(f"| 收录条目 | `{json.dumps(entry, ensure_ascii=False)}` |")
    for note in notes:
        lines.append(f"\n> ⚠ {note}")
    if not problems and not adopted and checked:
        lines += ["", "---", "", "维护者打上 `采用` 标签后，皮肤包会提交进 `dist/skins/`，并进 `dist/skins/index.json`。"]
    if adopted:
        lines += ["", "---", "", "已上架 ✅ 皮肤包与索引已提交。"]

    # 对比度与令牌这块**只提醒不拦人**：好不好看是人的判断，能不能看清才是这关的底线
    return "\n".join(lines)


def gh(method: str, path: str, payload: dict | None = None) -> object:
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    if not token or not repo:
        raise RuntimeError("缺 GH_TOKEN / GITHUB_REPOSITORY，无法回帖")
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(f"https://api.github.com/repos/{repo}{path}", data=data, method=method)
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("User-Agent", USER_AGENT)
    if data:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = resp.read()
    return json.loads(body) if body else None


def upsert_comment(issue: str, text: str) -> None:
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
    """回帖 ＋ 真落盘才打标签关单。上架打 `已上架`／摘 `采用`，下架打 `已下架`／摘 `下架`。"""
    result: dict = {}
    if RESULT_FILE_PATH.is_file():
        try:
            result = json.loads(RESULT_FILE_PATH.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            result = {}
    landed = bool(result)
    is_retract = result.get("action") == "retract"
    done, trigger = ("已下架", "下架") if is_retract else ("已上架", "采用")
    if comment_file.is_file():
        text = comment_file.read_text(encoding="utf-8")
    else:
        head = "皮肤投稿 · 下架" if is_retract else "皮肤投稿 · 采用上架"
        text = (f"{MARKER}\n## 🎨 {head}\n\n**结果：机器人出错了 ❌**\n\n"
                f"这一轮没能落盘，请维护者手动处理（或摘掉 `{trigger}` 再打一次）。")
    if not landed:
        upsert_comment(issue, text + f"\n\n> 本轮没有改动 `dist/skins/`，所以没有打「{done}」、也没有关单。")
        return
    upsert_comment(issue, text)
    gh("POST", f"/issues/{issue}/labels", {"labels": [done]})
    try:
        gh("DELETE", f"/issues/{issue}/labels/{urllib.parse.quote(trigger)}")
    except Exception:  # noqa: BLE001
        pass
    gh("PATCH", f"/issues/{issue}", {"state": "closed", "state_reason": "completed"})


# ── 下架（与「采用」对称） ──────────────────────────────────────────────
def run_retract(issue: str, sub: dict, root: Path, comment_out: Path, post: bool) -> int:
    """下架：删索引条目 ＋ 删 ``dist/skins/<id>.json`` ＋ 回帖。"""
    index_path = root / "dist" / "skins" / "index.json"
    index = load_index(index_path)
    skins = index.get("skins") or []
    name = (sub.get("name") or "").strip()

    hit = None
    for i, entry in enumerate(skins):
        if sub.get("id") and entry.get("id") == sub.get("id"):
            hit = (i, entry, f"标识 `{sub['id']}`")
            break
    if hit is None and name:
        for i, entry in enumerate(skins):
            if (entry.get("name") or "").strip() == name:
                hit = (i, entry, f"名称「{name}」（没找到标识，靠名字认的）")
                break

    if hit is None:
        text = "\n".join([
            MARKER, "## 🎨 皮肤投稿 · 下架", "",
            "**结果：没找到这条上架记录 ⚠️**", "",
            f"按 标识／名称 在 `dist/skins/index.json` 里找过，没有「{name or sub.get('id') or '（未填）'}」。",
            "可能是它还没上架、或者已经下架过了。",
            "",
            "> **没有改动任何文件**，标签与单子也原样留着——你确认一下再说。",
        ])
        comment_out.write_text(text, encoding="utf-8")
        print(text)
        if post:
            upsert_comment(issue, text)
        return 0

    i, entry, how = hit
    del skins[i]
    index["skins"] = skins
    removed: list[str] = []
    pack_file = root / "dist" / "skins" / f"{entry.get('id')}.json"
    if pack_file.is_file():
        pack_file.unlink()
        removed.append(pack_file.name)
    from datetime import date

    index["updated"] = date.today().isoformat()
    dump_index(index_path, index)

    text = "\n".join([
        MARKER, "## 🎨 皮肤投稿 · 下架", "",
        "**结果：已下架 ✅**", "",
        "| 项 | 值 |", "|---|---|",
        f"| 下架的皮肤 | {entry.get('name') or '—'}（`{entry.get('id') or '—'}`） |",
        f"| 认出方式 | {how} |",
        f"| 删掉的文件 | {'、'.join('`' + x + '`' for x in removed) if removed else '（索引里有条目、包文件本来就不在）'} |",
        "",
        "> 已经套上这层皮肤的人**撤不回来**（本地优先，配置早在他设备上）；",
        "> 这次停掉的是**新的**分发：别人下次打开皮肤栏目就见不到它了。",
    ])
    comment_out.write_text(text, encoding="utf-8")
    print(text)
    RESULT_FILE_PATH.write_text(
        json.dumps({"ok": True, "action": "retract", "id": entry.get("id"),
                    "name": entry.get("name"), "files": removed}, ensure_ascii=False),
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
        text = build_comment({"spec": {}, "name": "—", "id": "—", "one_liner": "", "body": body},
                             [str(exc)], [], None, False, False)
        comment_out.write_text(text, encoding="utf-8")
        print(text)
        if args.post:
            upsert_comment(issue, text)
        return 1

    if args.mode == "retract":
        return run_retract(issue, sub, root, comment_out, args.post)

    problems, notes = validate(sub)
    entry = entry_of(sub) if not problems else None
    if not problems and args.mode == "adopt":
        index_path = root / "dist" / "skins" / "index.json"
        (root / "dist" / "skins").mkdir(parents=True, exist_ok=True)
        (root / "dist" / "skins" / f"{sub['id']}.json").write_text(
            json.dumps(sub["spec"], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        index = load_index(index_path)
        notes.append(upsert(index, entry))
        from datetime import date

        index["updated"] = date.today().isoformat()
        dump_index(index_path, index)
        RESULT_FILE_PATH.write_text(json.dumps({"ok": True, "id": sub["id"], "name": sub["name"]},
                                               ensure_ascii=False), encoding="utf-8")

    adopted = args.mode == "adopt" and not problems
    text = build_comment(sub, problems, notes, entry, adopted, args.mode == "check")
    comment_out.write_text(text, encoding="utf-8")
    print(text)
    if args.post:
        upsert_comment(issue, text)
    return 0 if not problems else 1


def main() -> int:
    ap = argparse.ArgumentParser(description="鲸鱼皮肤仓投稿通道")
    mode = ap.add_mutually_exclusive_group()
    mode.add_argument("--check", dest="mode", action="store_const", const="check")
    mode.add_argument("--adopt", dest="mode", action="store_const", const="adopt")
    mode.add_argument("--finalize", dest="mode", action="store_const", const="finalize")
    mode.add_argument("--retract", dest="mode", action="store_const", const="retract")
    ap.set_defaults(mode="check")
    ap.add_argument("--body-file", help="Issue 正文文件；不给就读 $ISSUE_BODY 或 stdin")
    ap.add_argument("--issue")
    ap.add_argument("--repo-root")
    ap.add_argument("--comment-out")
    ap.add_argument("--post", action="store_true", help="校验后把评论贴回 Issue（需 GH_TOKEN）")
    args = ap.parse_args()
    try:
        return run(args)
    except Exception as exc:  # noqa: BLE001
        text = (f"{MARKER}\n## 🎨 皮肤投稿通道 · 机器人出错\n\n"
                f"**{type(exc).__name__}**: {exc}\n\n```\n{traceback.format_exc(limit=3)}\n```\n")
        try:
            Path(args.comment_out or COMMENT_FILE).write_text(text, encoding="utf-8")
        except Exception:  # noqa: BLE001
            pass
        print(text, file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
