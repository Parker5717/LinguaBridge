"""Pytest suite for tools/pack_content.py.

Uses small hand-authored fixtures under tools/tests/fixtures/ rather than the
real content/ directory, so tests are fast and self-contained. Imports
pack_content as a module (no subprocess) per the pure-function design.
"""

from __future__ import annotations

import json
import shutil
import sqlite3
import sys
from pathlib import Path

import pytest

TOOLS_DIR = Path(__file__).resolve().parent.parent
FIXTURES_DIR = Path(__file__).resolve().parent / "fixtures"
GOOD_CONTENT_DIR = FIXTURES_DIR / "good"
ROOM_SCHEMA_JSON = FIXTURES_DIR / "room_schema.json"

sys.path.insert(0, str(TOOLS_DIR))

import pack_content  # noqa: E402


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------


def _copy_good_content(dest: Path) -> Path:
    """Copy the known-good fixture content tree into a tmp_path so tests can
    mutate individual files without affecting the shared fixture.
    """
    target = dest / "content"
    shutil.copytree(GOOD_CONTENT_DIR, target)
    return target


def _write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=2)


def _read_json(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


# ---------------------------------------------------------------------------
# Happy path
# ---------------------------------------------------------------------------


class TestHappyPath:
    def test_validate_only_clean(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        bundle, report = pack_content.validate_content(content_dir)
        assert report.ok(), report.render()
        assert len(bundle.vocab) == 2
        assert len(bundle.grammar_terms) == 1
        assert len(bundle.stem) == 1
        assert len(bundle.hsk_words) == 1
        assert len(bundle.hsk_grammar) == 1
        assert len(bundle.texts) == 1
        assert len(bundle.dialogues) == 1
        assert len(bundle.quizzes) == 2
        assert len(bundle.listening) == 1

    def test_main_validate_only_exit_code(self, tmp_path, capsys):
        content_dir = _copy_good_content(tmp_path)
        exit_code = pack_content.main(
            ["--validate-only", "--content-dir", str(content_dir)]
        )
        assert exit_code == 0
        out = capsys.readouterr().out
        assert "Validation OK." in out

    def test_build_database(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        out_db = tmp_path / "out" / "content.db"

        bundle, report = pack_content.validate_content(content_dir)
        assert report.ok(), report.render()

        room_schema = pack_content.load_room_schema(ROOM_SCHEMA_JSON)
        row_counts = pack_content.build_database(bundle, room_schema, out_db)

        assert out_db.is_file()

        conn = sqlite3.connect(str(out_db))
        try:
            cur = conn.cursor()

            # All expected tables exist.
            cur.execute("SELECT name FROM sqlite_master WHERE type='table'")
            tables = {row[0] for row in cur.fetchall()}
            expected_tables = {
                "vocab", "grammar_term", "stem_term", "hsk_word", "hsk_grammar",
                "reading_text", "dialogue", "dialogue_line", "quiz_question",
                "listening_passage", "card", "room_master_table",
            }
            assert expected_tables.issubset(tables)

            # Row counts.
            def count(table):
                cur.execute(f"SELECT COUNT(*) FROM {table}")
                return cur.fetchone()[0]

            assert count("vocab") == 2
            assert count("grammar_term") == 1
            assert count("stem_term") == 1
            assert count("hsk_word") == 1
            assert count("hsk_grammar") == 1
            assert count("reading_text") == 1
            assert count("dialogue") == 1
            assert count("dialogue_line") == 4
            assert count("quiz_question") == 2
            assert count("listening_passage") == 1

            # Card counts per type.
            cur.execute(
                "SELECT card_type, COUNT(*) FROM card GROUP BY card_type"
            )
            by_type = dict(cur.fetchall())
            assert by_type == {
                "en_ru": 2,
                "def_en": 1,
                "zh_en": 1,
                "gram_term": 1,
                "stem_en": 1,
            }

            # user_version pragma.
            cur.execute("PRAGMA user_version")
            assert cur.fetchone()[0] == room_schema.version

            # room_master_table has the identity hash at id=42.
            cur.execute(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            )
            row = cur.fetchone()
            assert row is not None
            assert row[0] == room_schema.identity_hash

            # dialogue_line ids are globally unique.
            cur.execute("SELECT id FROM dialogue_line")
            ids = [r[0] for r in cur.fetchall()]
            assert len(ids) == len(set(ids))

            # options_json: mcq has JSON array, typing has NULL.
            cur.execute(
                "SELECT options_json FROM quiz_question WHERE id = 'quiz:vocab:0001'"
            )
            options_json = cur.fetchone()[0]
            assert json.loads(options_json) == [
                "A person who rents a house from the owner",
                "A person who owns and rents out property",
                "A person who repairs houses",
                "A type of apartment",
            ]
            cur.execute(
                "SELECT options_json FROM quiz_question WHERE id = 'quiz:listening:0001'"
            )
            assert cur.fetchone()[0] is None
        finally:
            conn.close()

        assert row_counts["vocab"] == 2
        assert row_counts["card"] == 6

    def test_main_full_pack(self, tmp_path, capsys):
        content_dir = _copy_good_content(tmp_path)
        out_db = tmp_path / "assets" / "content.db"
        exit_code = pack_content.main(
            [
                "--content-dir", str(content_dir),
                "--schema-json", str(ROOM_SCHEMA_JSON),
                "--out", str(out_db),
            ]
        )
        assert exit_code == 0
        assert out_db.is_file()
        out = capsys.readouterr().out
        assert "Wrote database to" in out

    def test_stale_output_is_replaced(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        out_db = tmp_path / "content.db"
        out_db.parent.mkdir(parents=True, exist_ok=True)
        out_db.write_text("stale garbage, not a real db", encoding="utf-8")

        bundle, report = pack_content.validate_content(content_dir)
        assert report.ok()
        room_schema = pack_content.load_room_schema(ROOM_SCHEMA_JSON)
        pack_content.build_database(bundle, room_schema, out_db)

        conn = sqlite3.connect(str(out_db))
        try:
            cur = conn.cursor()
            cur.execute("SELECT COUNT(*) FROM vocab")
            assert cur.fetchone()[0] == 2
        finally:
            conn.close()


# ---------------------------------------------------------------------------
# Rejection tests
# ---------------------------------------------------------------------------


class TestRejections:
    def test_schema_invalid_item_reported(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        vocab_file = content_dir / "vocab" / "batch01.json"
        data = _read_json(vocab_file)
        # Break required field: drop 'pos'.
        del data[0]["pos"]
        _write_json(vocab_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        joined = "\n".join(report.schema_errors)
        assert "batch01.json" in joined
        assert "[0]" in joined

    def test_duplicate_id_across_files(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        vocab_file = content_dir / "vocab" / "batch01.json"
        data = _read_json(vocab_file)
        dup_file = content_dir / "vocab" / "batch02.json"
        dup_entry = dict(data[0])
        dup_entry["headword"] = "completely-different-word"
        _write_json(dup_file, [dup_entry])

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        joined = "\n".join(report.duplicate_id_errors)
        assert dup_entry["id"] in joined
        assert "batch01.json" in joined
        assert "batch02.json" in joined

    def test_mcq_answer_not_in_options(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        quiz_file = content_dir / "quizzes" / "batch01.json"
        data = _read_json(quiz_file)
        data[0]["answer"] = "Something not in the options list"
        _write_json(quiz_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        joined = "\n".join(report.mcq_errors)
        assert "batch01.json[0]" in joined
        assert "not found in options" in joined

    def test_missing_pinyin_tone_marks(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        hsk_file = content_dir / "hsk" / "words_batch01.json"
        data = _read_json(hsk_file)
        data[0]["example_pinyin"] = "Wo shi xuesheng."  # no tone marks
        _write_json(hsk_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        joined = "\n".join(report.pinyin_errors)
        assert "words_batch01.json[0]" in joined
        assert "example_pinyin" in joined

    def test_dialogue_non_consecutive_ord(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        dlg_file = content_dir / "dialogues" / "batch01.json"
        data = _read_json(dlg_file)
        data[0]["lines"][2]["ord"] = 5  # was 3; now 1,2,5,4 -> not consecutive
        _write_json(dlg_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        joined = "\n".join(report.dialogue_errors)
        assert "batch01.json[0]" in joined

    def test_main_returns_1_on_validation_failure(self, tmp_path, capsys):
        content_dir = _copy_good_content(tmp_path)
        quiz_file = content_dir / "quizzes" / "batch01.json"
        data = _read_json(quiz_file)
        data[0]["answer"] = "not an option"
        _write_json(quiz_file, data)

        exit_code = pack_content.main(
            ["--validate-only", "--content-dir", str(content_dir)]
        )
        assert exit_code == 1
        err = capsys.readouterr().err
        assert "FAILED" in err

    def test_duplicate_headword_case_insensitive(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        vocab_file = content_dir / "vocab" / "batch01.json"
        data = _read_json(vocab_file)
        dup = dict(data[0])
        dup["id"] = "vocab:a2b1:0099"
        dup["headword"] = "LANDLORD"  # differs only by case
        data.append(dup)
        _write_json(vocab_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        joined = "\n".join(report.duplicate_headword_errors)
        assert "landlord" in joined.lower()

    def test_duplicate_hanzi(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        hsk_file = content_dir / "hsk" / "words_batch01.json"
        data = _read_json(hsk_file)
        dup = dict(data[0])
        dup["id"] = "hsk:0099"
        data.append(dup)
        _write_json(hsk_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        assert report.duplicate_hanzi_errors

    def test_quiz_passage_id_must_exist(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        quiz_file = content_dir / "quizzes" / "batch01.json"
        data = _read_json(quiz_file)
        data[1]["passage_id"] = "pass:a2:99"  # does not exist
        _write_json(quiz_file, data)

        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()
        assert report.passage_ref_errors

    def test_dialogue_zh_line_missing_pinyin(self, tmp_path):
        content_dir = _copy_good_content(tmp_path)
        dlg_file = content_dir / "dialogues" / "batch01.json"
        data = _read_json(dlg_file)
        data[0]["lines"][0]["pinyin"] = None
        _write_json(dlg_file, data)

        # A null pinyin also fails schema (pinyin has minLength: 1, type string),
        # so this should surface as a schema error which excludes the item from
        # the bundle; dialogue-line checks then have nothing to check. Assert
        # the overall report still fails.
        _, report = pack_content.validate_content(content_dir)
        assert not report.ok()

    def test_missing_files_are_fine(self, tmp_path):
        # Only vocab present; every other content dir absent entirely.
        content_dir = tmp_path / "content"
        (content_dir / "vocab").mkdir(parents=True)
        shutil.copy(
            GOOD_CONTENT_DIR / "vocab" / "batch01.json",
            content_dir / "vocab" / "batch01.json",
        )
        shutil.copytree(GOOD_CONTENT_DIR / "schemas", content_dir / "schemas")

        bundle, report = pack_content.validate_content(content_dir)
        assert report.ok(), report.render()
        assert len(bundle.vocab) == 2
        assert len(bundle.hsk_words) == 0
        assert len(bundle.dialogues) == 0
