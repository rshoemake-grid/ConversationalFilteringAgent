"""Unit tests for collect_conversational_filtering_attributes."""

import io
import json
import sys
import unittest
from pathlib import Path

_SCRIPTS_DIR = Path(__file__).resolve().parent
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

from collect_conversational_filtering_attributes import (
    aggregate,
    collect_from_files,
    collect_from_parsed_json,
    emit_csv,
    emit_json,
    parse_input_text,
)


class TestCollect(unittest.TestCase):
    def test_nested_followup_suggested_answers(self):
        raw = """
        {
          "conversationalFilteringResult": {
            "followupQuestion": {
              "followupQuestion": "What size?",
              "suggestedAnswers": [
                {"productAttributeValue": {"value": "12oz"}},
                {"productAttributeValue": {"value": "24oz"}}
              ]
            }
          }
        }
        """
        obs = collect_from_parsed_json(json.loads(raw))
        self.assertEqual(len(obs), 2)
        self.assertEqual({o.value for o in obs}, {"12oz", "24oz"})
        self.assertTrue(all(o.name_key == "" for o in obs))

    def test_attributes_type_and_brands(self):
        raw = """
        {
          "conversationalFilteringResult": {
            "followupQuestion": "What type?",
            "suggestedAnswers": [
              {"productAttributeValue": {"name": "attributes.type", "value": "Balloons"}},
              {"productAttributeValue": {"name": "attributes.type", "value": "Streamers"}}
            ]
          }
        }
        """
        obs = collect_from_parsed_json(json.loads(raw))
        keys = {o.name_key for o in obs}
        self.assertEqual(keys, {"type"})

    def test_aggregate_counts(self):
        from collect_conversational_filtering_attributes import AttributeObservation

        obs = [
            AttributeObservation("attributes.brands", "NIKE"),
            AttributeObservation("attributes.brands", "NIKE"),
            AttributeObservation("attributes.type", "X"),
        ]
        c = aggregate(obs)
        self.assertEqual(c[("attributes.brands", "brands", "NIKE")], 2)
        self.assertEqual(c[("attributes.type", "type", "X")], 1)

    def test_emit_csv_json(self):
        from collect_conversational_filtering_attributes import AttributeObservation

        c = aggregate([AttributeObservation("attributes.color", "BLK")])
        buf = io.StringIO()
        emit_csv(c, buf)
        self.assertIn("name_key", buf.getvalue())
        self.assertIn("color", buf.getvalue())
        buf2 = io.StringIO()
        emit_json(c, buf2)
        data = json.loads(buf2.getvalue())
        self.assertEqual(len(data["observations"]), 1)
        self.assertEqual(data["observations"][0]["name_key"], "color")

    def test_parse_ndjson(self):
        text = '{"a":1}\n{"conversationalFilteringResult":{"suggestedAnswers":[{"productAttributeValue":{"value":"Z"}}]}}\n'
        roots = parse_input_text(text)
        self.assertEqual(len(roots), 2)
        obs = collect_from_parsed_json(roots[1])
        self.assertEqual(obs[0].value, "Z")

    def test_collect_from_fixture_file(self):
        fixture = Path(__file__).resolve().parent / "fixtures" / "sample_conversational_response.json"
        obs = collect_from_files([fixture])
        keys = {o.name_key for o in obs}
        self.assertIn("type", keys)
        self.assertIn("brands", keys)


if __name__ == "__main__":
    unittest.main()
