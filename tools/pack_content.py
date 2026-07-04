"""Content-packing pipeline for the LinguaBridge Android app.

Validates the JSON content under ``content/`` against JSON Schemas, runs a
battery of cross-file consistency checks, and (unless ``--validate-only`` is
given) builds the pre-packaged Room SQLite asset at
``app/src/main/assets/content.db``.

Usage:
    python tools/pack_content.py [--validate-only] [--content-dir DIR]
                                  [--schema-json PATH] [--out PATH]

Structured as pure functions plus a ``main(argv)`` entry point so the pytest
suite can import this module directly instead of shelling out.
"""

from __future__ import annotations

import argparse
import json
import re
import sqlite3
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

try:
    import jsonschema
except ImportError as exc:  # pragma: no cover - exercised only when dep missing
    raise SystemExit(
        "The 'jsonschema' package is required. Install with: "
        "pip install --user -r tools/requirements.txt"
    ) from exc


REPO_ROOT = Path(__file__).resolve().parent.parent

DEFAULT_CONTENT_DIR = REPO_ROOT / "content"
def _latest_schema_json() -> Path:
    """Highest-version Room-exported schema, so a DB version bump needs no
    packer change."""
    schema_dir = (
        REPO_ROOT / "app" / "schemas" / "com.linguabridge.app.data.db.content.ContentDatabase"
    )
    candidates = sorted(schema_dir.glob("*.json"), key=lambda p: int(p.stem))
    return candidates[-1] if candidates else schema_dir / "1.json"


DEFAULT_SCHEMA_JSON = _latest_schema_json()
DEFAULT_OUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "content.db"

# Glob pattern (relative to content dir) -> schema file name (relative to
# content/schemas/), and a logical "kind" used to route items to the right
# in-memory bucket / table.
CONTENT_SOURCES: list[tuple[str, str, str]] = [
    ("vocab/*.json", "vocab.schema.json", "vocab"),
    ("grammar_terms/*.json", "grammar_terms.schema.json", "grammar_terms"),
    ("stem/*.json", "stem.schema.json", "stem"),
    ("hsk/words_*.json", "hsk_words.schema.json", "hsk_words"),
    ("hsk/grammar_*.json", "hsk_grammar.schema.json", "hsk_grammar"),
    ("texts/*.json", "texts.schema.json", "texts"),
    ("dialogues/*.json", "dialogues.schema.json", "dialogues"),
    ("quizzes/*.json", "quizzes.schema.json", "quizzes"),
    ("listening/*.json", "listening_passages.schema.json", "listening"),
]

TONE_MARK_RE = re.compile(
    "[āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛ]"
)


class ContentError(Exception):
    """Raised (with a full report already printed/collected) when validation fails."""


@dataclass
class ValidationReport:
    """Accumulates errors found across all validation passes."""

    schema_errors: list[str] = field(default_factory=list)
    duplicate_id_errors: list[str] = field(default_factory=list)
    duplicate_headword_errors: list[str] = field(default_factory=list)
    duplicate_hanzi_errors: list[str] = field(default_factory=list)
    mcq_errors: list[str] = field(default_factory=list)
    passage_ref_errors: list[str] = field(default_factory=list)
    pinyin_errors: list[str] = field(default_factory=list)
    dialogue_errors: list[str] = field(default_factory=list)

    def all_errors(self) -> list[str]:
        return (
            self.schema_errors
            + self.duplicate_id_errors
            + self.duplicate_headword_errors
            + self.duplicate_hanzi_errors
            + self.mcq_errors
            + self.passage_ref_errors
            + self.pinyin_errors
            + self.dialogue_errors
        )

    def ok(self) -> bool:
        return not self.all_errors()

    def render(self) -> str:
        lines = ["Content validation FAILED:"]
        for label, errs in [
            ("Schema errors", self.schema_errors),
            ("Duplicate id errors", self.duplicate_id_errors),
            ("Duplicate headword errors", self.duplicate_headword_errors),
            ("Duplicate hanzi errors", self.duplicate_hanzi_errors),
            ("MCQ errors", self.mcq_errors),
            ("Passage reference errors", self.passage_ref_errors),
            ("Pinyin tone-mark errors", self.pinyin_errors),
            ("Dialogue errors", self.dialogue_errors),
        ]:
            if errs:
                lines.append(f"\n{label} ({len(errs)}):")
                lines.extend(f"  - {e}" for e in errs)
        lines.append(f"\nTotal errors: {len(self.all_errors())}")
        return "\n".join(lines)


@dataclass
class LoadedItem:
    """A single content item plus the file/index it came from (for error msgs)."""

    data: dict[str, Any]
    file: Path
    index: int

    @property
    def loc(self) -> str:
        return f"{self.file}[{self.index}]"


@dataclass
class ContentBundle:
    """All loaded content, keyed by kind."""

    vocab: list[LoadedItem] = field(default_factory=list)
    grammar_terms: list[LoadedItem] = field(default_factory=list)
    stem: list[LoadedItem] = field(default_factory=list)
    hsk_words: list[LoadedItem] = field(default_factory=list)
    hsk_grammar: list[LoadedItem] = field(default_factory=list)
    texts: list[LoadedItem] = field(default_factory=list)
    dialogues: list[LoadedItem] = field(default_factory=list)
    quizzes: list[LoadedItem] = field(default_factory=list)
    listening: list[LoadedItem] = field(default_factory=list)

    def kind_items(self, kind: str) -> list[LoadedItem]:
        return getattr(self, kind)

    def all_kinds(self) -> Iterable[tuple[str, list[LoadedItem]]]:
        for name in (
            "vocab",
            "grammar_terms",
            "stem",
            "hsk_words",
            "hsk_grammar",
            "texts",
            "dialogues",
            "quizzes",
            "listening",
        ):
            yield name, getattr(self, name)


# ---------------------------------------------------------------------------
# Loading
# ---------------------------------------------------------------------------


def load_content(
    content_dir: Path, report: ValidationReport
) -> ContentBundle:
    """Load and JSON-Schema-validate every content file. Missing files/dirs
    are fine (produce empty lists); malformed JSON or schema violations are
    recorded in ``report``.
    """

    bundle = ContentBundle()
    schema_dir = content_dir / "schemas"

    for glob_pattern, schema_name, kind in CONTENT_SOURCES:
        schema_path = schema_dir / schema_name
        schema = _load_schema(schema_path, report)
        if schema is None:
            continue

        # Files are validated item-by-item (for precise error indices), so the
        # validator is built from the per-item schema, not the array wrapper.
        validator = jsonschema.Draft202012Validator(schema.get("items", schema))

        # glob_pattern looks like "vocab/*.json" or "hsk/words_*.json"
        subdir, pattern = glob_pattern.split("/", 1)
        target_dir = content_dir / subdir
        if not target_dir.is_dir():
            continue  # missing directory is fine

        for file_path in sorted(target_dir.glob(pattern)):
            items = _load_json_array(file_path, report)
            if items is None:
                continue

            for idx, raw_item in enumerate(items):
                errors = sorted(
                    validator.iter_errors(raw_item), key=lambda e: list(e.path)
                )
                if errors:
                    for err in errors:
                        path_str = (
                            "/".join(str(p) for p in err.path) if err.path else "<root>"
                        )
                        report.schema_errors.append(
                            f"{file_path}[{idx}] ({path_str}): {err.message}"
                        )
                    continue  # don't add invalid items to the bundle

                if not isinstance(raw_item, dict):
                    report.schema_errors.append(
                        f"{file_path}[{idx}]: item is not an object"
                    )
                    continue

                bundle.kind_items(kind).append(
                    LoadedItem(data=raw_item, file=file_path, index=idx)
                )

    return bundle


def _load_schema(schema_path: Path, report: ValidationReport) -> dict | None:
    if not schema_path.is_file():
        report.schema_errors.append(f"Missing schema file: {schema_path}")
        return None
    try:
        with schema_path.open("r", encoding="utf-8") as fh:
            return json.load(fh)
    except json.JSONDecodeError as exc:
        report.schema_errors.append(f"{schema_path}: malformed schema JSON: {exc}")
        return None


def _load_json_array(file_path: Path, report: ValidationReport) -> list | None:
    try:
        with file_path.open("r", encoding="utf-8") as fh:
            data = json.load(fh)
    except json.JSONDecodeError as exc:
        report.schema_errors.append(f"{file_path}: malformed JSON: {exc}")
        return None
    except OSError as exc:
        report.schema_errors.append(f"{file_path}: could not read file: {exc}")
        return None

    if not isinstance(data, list):
        report.schema_errors.append(
            f"{file_path}: top-level JSON must be an array, got {type(data).__name__}"
        )
        return None

    return data


# ---------------------------------------------------------------------------
# Cross-file validation checks
# ---------------------------------------------------------------------------


def check_duplicate_ids(bundle: ContentBundle, report: ValidationReport) -> None:
    seen: dict[str, LoadedItem] = {}
    for _kind, items in bundle.all_kinds():
        for item in items:
            item_id = item.data.get("id")
            if item_id is None:
                continue
            if item_id in seen:
                first = seen[item_id]
                report.duplicate_id_errors.append(
                    f"Duplicate id '{item_id}': first seen at {first.loc}, "
                    f"again at {item.loc}"
                )
            else:
                seen[item_id] = item


def check_duplicate_headwords(bundle: ContentBundle, report: ValidationReport) -> None:
    seen: dict[str, LoadedItem] = {}
    for item in bundle.vocab:
        headword = item.data.get("headword")
        if headword is None:
            continue
        key = headword.casefold()
        if key in seen:
            first = seen[key]
            report.duplicate_headword_errors.append(
                f"Duplicate headword '{headword}' (case-insensitive): "
                f"first seen at {first.loc}, again at {item.loc}"
            )
        else:
            seen[key] = item


def check_duplicate_hanzi(bundle: ContentBundle, report: ValidationReport) -> None:
    # Keyed by (hanzi, pinyin): polyphones like 只 zhǐ "only" vs 只 zhī
    # (measure word) are distinct vocabulary items, not duplicates.
    seen: dict[tuple[str, str], LoadedItem] = {}
    for item in bundle.hsk_words:
        hanzi = item.data.get("hanzi")
        if hanzi is None:
            continue
        key = (hanzi, str(item.data.get("pinyin", "")).lower())
        if key in seen:
            first = seen[key]
            report.duplicate_hanzi_errors.append(
                f"Duplicate hanzi '{hanzi}' ({key[1]}): first seen at {first.loc}, "
                f"again at {item.loc}"
            )
        else:
            seen[key] = item


def check_mcq_answers(bundle: ContentBundle, report: ValidationReport) -> None:
    for item in bundle.quizzes:
        data = item.data
        if data.get("type") != "mcq":
            continue
        options = data.get("options") or []
        answer = data.get("answer")

        if len(options) != len(set(options)):
            report.mcq_errors.append(
                f"{item.loc}: mcq options contain duplicates: {options}"
            )

        if answer not in options:
            report.mcq_errors.append(
                f"{item.loc}: mcq answer {answer!r} not found in options {options!r}"
            )


def check_passage_refs(bundle: ContentBundle, report: ValidationReport) -> None:
    passage_ids = {item.data.get("id") for item in bundle.listening}
    for item in bundle.quizzes:
        passage_id = item.data.get("passage_id")
        if passage_id is None:
            continue
        if passage_id not in passage_ids:
            report.passage_ref_errors.append(
                f"{item.loc}: passage_id '{passage_id}' does not exist in "
                f"listening passages"
            )


def check_pinyin_tone_marks(bundle: ContentBundle, report: ValidationReport) -> None:
    fields_by_kind = {
        "hsk_words": ["example_pinyin"],
        "hsk_grammar": ["example_pinyin"],
        "grammar_terms": ["zh_example_pinyin"],
    }
    for kind, field_names in fields_by_kind.items():
        for item in bundle.kind_items(kind):
            for field_name in field_names:
                value = item.data.get(field_name)
                if value is None:
                    continue
                if not TONE_MARK_RE.search(value):
                    report.pinyin_errors.append(
                        f"{item.loc}: field '{field_name}' value {value!r} has no "
                        f"tone-marked vowel"
                    )


def check_dialogue_lines(bundle: ContentBundle, report: ValidationReport) -> None:
    for item in bundle.dialogues:
        lines = item.data.get("lines") or []
        ords = [line.get("ord") for line in lines]
        expected = list(range(1, len(lines) + 1))
        if sorted(o for o in ords if o is not None) != expected or any(
            o is None for o in ords
        ):
            report.dialogue_errors.append(
                f"{item.loc}: dialogue 'ord' values must be unique, start at 1, "
                f"and be consecutive; got {ords}"
            )

        for line_idx, line in enumerate(lines):
            if line.get("lang") == "zh" and not line.get("pinyin"):
                report.dialogue_errors.append(
                    f"{item.loc}: line[{line_idx}] (ord={line.get('ord')}) has "
                    f"lang='zh' but missing/null 'pinyin'"
                )


def run_all_checks(bundle: ContentBundle, report: ValidationReport) -> None:
    check_duplicate_ids(bundle, report)
    check_duplicate_headwords(bundle, report)
    check_duplicate_hanzi(bundle, report)
    check_mcq_answers(bundle, report)
    check_passage_refs(bundle, report)
    check_pinyin_tone_marks(bundle, report)
    check_dialogue_lines(bundle, report)


def validate_content(content_dir: Path) -> tuple[ContentBundle, ValidationReport]:
    """Run the full validation pipeline. Always returns bundle+report; caller
    decides what to do with a failing report (schema-invalid items are
    excluded from the bundle so downstream checks don't crash on them).
    """

    report = ValidationReport()
    bundle = load_content(content_dir, report)
    run_all_checks(bundle, report)
    return bundle, report


def stats_summary(bundle: ContentBundle) -> str:
    lines = ["Content stats:"]
    for kind, items in bundle.all_kinds():
        lines.append(f"  {kind}: {len(items)}")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Room schema loading
# ---------------------------------------------------------------------------


@dataclass
class RoomEntity:
    table_name: str
    create_sql: str
    index_sqls: list[str]


@dataclass
class RoomSchema:
    version: int
    identity_hash: str
    entities: list[RoomEntity]
    setup_queries: list[str]

    def entity(self, table_name: str) -> RoomEntity:
        for e in self.entities:
            if e.table_name == table_name:
                return e
        raise KeyError(f"No entity with tableName={table_name!r} in Room schema")


def load_room_schema(schema_json_path: Path) -> RoomSchema:
    with schema_json_path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)

    db = data["database"]
    entities = []
    for ent in db["entities"]:
        table_name = ent["tableName"]
        create_sql = ent["createSql"].replace("${TABLE_NAME}", table_name)
        index_sqls = [
            idx["createSql"].replace("${TABLE_NAME}", table_name)
            for idx in ent.get("indices", [])
        ]
        entities.append(
            RoomEntity(table_name=table_name, create_sql=create_sql, index_sqls=index_sqls)
        )

    return RoomSchema(
        version=db["version"],
        identity_hash=db["identityHash"],
        entities=entities,
        setup_queries=db.get("setupQueries", []),
    )


# ---------------------------------------------------------------------------
# Packing
# ---------------------------------------------------------------------------


def build_database(bundle: ContentBundle, room_schema: RoomSchema, out_path: Path) -> dict[str, int]:
    """Build the SQLite database at ``out_path`` (via a temp file + atomic
    replace). Returns a dict of table name -> row count for reporting.
    """

    out_path.parent.mkdir(parents=True, exist_ok=True)

    if out_path.exists():
        out_path.unlink()

    fd, tmp_name = tempfile.mkstemp(
        prefix=".content_db_", suffix=".tmp", dir=str(out_path.parent)
    )
    import os

    os.close(fd)
    tmp_path = Path(tmp_name)
    if tmp_path.exists():
        tmp_path.unlink()

    conn = sqlite3.connect(str(tmp_path))
    try:
        conn.execute("PRAGMA foreign_keys = OFF")
        cur = conn.cursor()

        for entity in room_schema.entities:
            cur.execute(entity.create_sql)
            for index_sql in entity.index_sqls:
                cur.execute(index_sql)

        cur.execute(f"PRAGMA user_version = {room_schema.version}")

        cur.execute(
            "CREATE TABLE IF NOT EXISTS room_master_table "
            "(id INTEGER PRIMARY KEY,identity_hash TEXT)"
        )
        cur.execute(
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)",
            (room_schema.identity_hash,),
        )

        row_counts: dict[str, int] = {}
        row_counts.update(_insert_vocab(cur, bundle))
        row_counts.update(_insert_grammar_terms(cur, bundle))
        row_counts.update(_insert_stem(cur, bundle))
        row_counts.update(_insert_hsk_words(cur, bundle))
        row_counts.update(_insert_hsk_grammar(cur, bundle))
        row_counts.update(_insert_texts(cur, bundle))
        row_counts.update(_insert_dialogues(cur, bundle))
        row_counts.update(_insert_quizzes(cur, bundle))
        row_counts.update(_insert_listening(cur, bundle))

        card_counts = _insert_cards(cur, bundle)
        row_counts["card"] = card_counts["card"]
        row_counts["card_by_type"] = card_counts["card_by_type"]  # type: ignore[assignment]

        conn.commit()
    finally:
        conn.close()

    # Atomic replace.
    os.replace(str(tmp_path), str(out_path))

    return row_counts


def _insert_vocab(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (
            d["id"], d["level"], d["headword"], d["ipa"], d["pos"],
            d["ru_translation"], d["en_definition"], d["example1"],
            d.get("example1_ru"), d["example2"], d["topic"],
        )
        for item in bundle.vocab
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO vocab (id, level, headword, ipa, pos, ru_translation, "
        "en_definition, example1, example1_ru, example2, topic) "
        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    return {"vocab": len(rows)}


def _insert_grammar_terms(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (
            d["id"], d["term"], d["en_explanation"], d["ru_translation"],
            d["zh_example"], d["zh_example_pinyin"], d["zh_example_en"],
        )
        for item in bundle.grammar_terms
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO grammar_term (id, term, en_explanation, ru_translation, "
        "zh_example, zh_example_pinyin, zh_example_en) VALUES (?,?,?,?,?,?,?)",
        rows,
    )
    return {"grammar_term": len(rows)}


def _insert_stem(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (
            d["id"], d["domain"], d["term"], d["ru_translation"], d.get("symbol"),
            d["en_definition"], d["csca_example"],
        )
        for item in bundle.stem
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO stem_term (id, domain, term, ru_translation, symbol, "
        "en_definition, csca_example) VALUES (?,?,?,?,?,?,?)",
        rows,
    )
    return {"stem_term": len(rows)}


def _insert_hsk_words(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (
            d["id"], d["hanzi"], d["pinyin"], d["en_meaning"], d["ru_meaning"],
            d["hsk_level"], d["example_zh"], d["example_pinyin"], d["example_en"],
            d.get("mnemonic_ru"),
        )
        for item in bundle.hsk_words
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO hsk_word (id, hanzi, pinyin, en_meaning, ru_meaning, "
        "hsk_level, example_zh, example_pinyin, example_en, mnemonic_ru) "
        "VALUES (?,?,?,?,?,?,?,?,?,?)",
        rows,
    )
    return {"hsk_word": len(rows)}


def _insert_hsk_grammar(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (
            d["id"], d["title"], d["pattern"], d["en_explanation"], d["example_zh"],
            d["example_pinyin"], d["example_en"], d["ru_note"],
        )
        for item in bundle.hsk_grammar
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO hsk_grammar (id, title, pattern, en_explanation, example_zh, "
        "example_pinyin, example_en, ru_note) VALUES (?,?,?,?,?,?,?,?)",
        rows,
    )
    return {"hsk_grammar": len(rows)}


def _insert_texts(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (d["id"], d["level"], d["title"], d["topic"], d["body"])
        for item in bundle.texts
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO reading_text (id, level, title, topic, body) VALUES (?,?,?,?,?)",
        rows,
    )
    return {"reading_text": len(rows)}


def _insert_dialogues(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    dialogue_rows = [
        (d["id"], d["title"], d["scenario"], d["level"])
        for item in bundle.dialogues
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO dialogue (id, title, scenario, level) VALUES (?,?,?,?)",
        dialogue_rows,
    )

    line_rows = []
    next_line_id = 1
    for item in bundle.dialogues:
        dialogue_id = item.data["id"]
        for line in item.data.get("lines", []):
            line_rows.append(
                (
                    next_line_id,
                    dialogue_id,
                    line["ord"],
                    line["speaker"],
                    line["lang"],
                    line["text"],
                    line.get("pinyin"),
                    line.get("ru_note"),
                )
            )
            next_line_id += 1

    cur.executemany(
        "INSERT INTO dialogue_line (id, dialogue_id, ord, speaker, lang, text, "
        "pinyin, ru_note) VALUES (?,?,?,?,?,?,?,?)",
        line_rows,
    )
    return {"dialogue": len(dialogue_rows), "dialogue_line": len(line_rows)}


def _insert_quizzes(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = []
    for item in bundle.quizzes:
        d = item.data
        options = d.get("options")
        options_json = json.dumps(options, ensure_ascii=False) if options is not None else None
        rows.append(
            (
                d["id"], d["category"], d["level"], d["type"], d["prompt"],
                options_json, d["answer"], d["explanation"], d.get("passage_id"),
            )
        )
    cur.executemany(
        "INSERT INTO quiz_question (id, category, level, type, prompt, options_json, "
        "answer, explanation, passage_id) VALUES (?,?,?,?,?,?,?,?,?)",
        rows,
    )
    return {"quiz_question": len(rows)}


def _insert_listening(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, int]:
    rows = [
        (d["id"], d["level"], d["text"])
        for item in bundle.listening
        for d in [item.data]
    ]
    cur.executemany(
        "INSERT INTO listening_passage (id, level, text) VALUES (?,?,?)",
        rows,
    )
    return {"listening_passage": len(rows)}


def _insert_cards(cur: sqlite3.Cursor, bundle: ContentBundle) -> dict[str, Any]:
    rows: list[tuple] = []
    by_type: dict[str, int] = {}

    def add(card_type, front, back, hint, example, tts_lang, tts_text, source_ref,
            example_ru=None):
        card_id = f"card:{card_type}:{source_ref}"
        rows.append((card_id, card_type, front, back, hint, example, example_ru,
                     tts_lang, tts_text, source_ref))
        by_type[card_type] = by_type.get(card_type, 0) + 1

    for item in bundle.vocab:
        d = item.data
        add(
            "en_ru",
            d["headword"],
            f"{d['ru_translation']}\n{d['en_definition']}",
            d["ipa"],
            d["example1"],
            "en",
            d["headword"],
            d["id"],
            example_ru=d.get("example1_ru"),
        )
        if d.get("level") == "B1B2":
            add(
                "def_en",
                d["en_definition"],
                d["headword"],
                d["pos"],
                d["example2"],
                "en",
                d["headword"],
                d["id"],
            )

    for item in bundle.hsk_words:
        d = item.data
        add(
            "zh_en",
            f"{d['hanzi']}\n{d['pinyin']}",
            f"{d['en_meaning']}\n{d['ru_meaning']}",
            d.get("mnemonic_ru"),
            f"{d['example_zh']}\n{d['example_pinyin']}\n{d['example_en']}",
            "zh",
            d["hanzi"],
            d["id"],
        )

    for item in bundle.grammar_terms:
        d = item.data
        # Russian translation first, like en_ru cards: exercises use the first
        # back line as "the translation", so it must be Russian, not the long
        # English explanation.
        add(
            "gram_term",
            d["term"],
            f"{d['ru_translation']}\n{d['en_explanation']}",
            None,
            f"{d['zh_example']}\n{d['zh_example_pinyin']}\n{d['zh_example_en']}",
            None,
            None,
            d["id"],
        )

    for item in bundle.stem:
        d = item.data
        add(
            "stem_en",
            d["term"],
            f"{d['ru_translation']}\n{d['en_definition']}",
            d.get("symbol"),
            d["csca_example"],
            "en",
            d["term"],
            d["id"],
        )

    cur.executemany(
        "INSERT INTO card (id, card_type, front, back, hint, example, example_ru, "
        "tts_lang, tts_text, source_ref) VALUES (?,?,?,?,?,?,?,?,?,?)",
        rows,
    )

    return {"card": len(rows), "card_by_type": by_type}


def render_pack_stats(row_counts: dict[str, Any]) -> str:
    lines = ["Pack stats (rows per table):"]
    card_by_type = row_counts.get("card_by_type", {})
    for table, count in row_counts.items():
        if table == "card_by_type":
            continue
        lines.append(f"  {table}: {count}")
    if card_by_type:
        lines.append("Cards by type:")
        for card_type, count in sorted(card_by_type.items()):
            lines.append(f"  {card_type}: {count}")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Validate and pack LinguaBridge content into a Room-compatible SQLite asset."
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Only run validation; do not build the database.",
    )
    parser.add_argument(
        "--content-dir",
        type=Path,
        default=DEFAULT_CONTENT_DIR,
        help="Path to the content/ directory (default: repo_root/content).",
    )
    parser.add_argument(
        "--schema-json",
        type=Path,
        default=DEFAULT_SCHEMA_JSON,
        help="Path to the Room-exported schema JSON (default: repo's ContentDatabase 1.json).",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=DEFAULT_OUT,
        help="Output path for content.db (default: app/src/main/assets/content.db).",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_arg_parser()
    args = parser.parse_args(argv)

    bundle, report = validate_content(args.content_dir)

    if not report.ok():
        print(report.render(), file=sys.stderr)
        return 1

    print(stats_summary(bundle))

    if args.validate_only:
        print("\nValidation OK.")
        return 0

    if not args.schema_json.is_file():
        print(f"Room schema JSON not found: {args.schema_json}", file=sys.stderr)
        return 1

    room_schema = load_room_schema(args.schema_json)
    row_counts = build_database(bundle, room_schema, args.out)

    print(f"\nWrote database to {args.out}")
    print(render_pack_stats(row_counts))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
