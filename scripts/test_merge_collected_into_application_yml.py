"""Unit tests for merge_collected_into_application_yml (display merge only)."""

import sys
import unittest
from pathlib import Path

_SCRIPTS = Path(__file__).resolve().parent
if str(_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS))

from collect_conversational_filtering_attributes import AttributeObservation
from merge_collected_into_application_yml import (
    merge_observations_into_display_mapping,
    _display_text,
    _route_observation,
    _target_key_for_unnamed,
)


class TestMergeDisplay(unittest.TestCase):
    def test_route_unnamed_storage_and_size(self):
        self.assertEqual(_target_key_for_unnamed("S"), "storageType")
        self.assertEqual(_target_key_for_unnamed("12oz"), "sizes")

    def test_route_observation_brands(self):
        o = AttributeObservation("attributes.brands", "NEWCODE")
        self.assertEqual(_route_observation(o), ("brands", "NEWCODE"))

    def test_merge_adds_only_missing(self):
        m = {"brands": {"NIKE": "Nike"}}
        obs = [
            AttributeObservation("attributes.brands", "NIKE"),
            AttributeObservation("attributes.brands", "NEWCO"),
        ]
        n = merge_observations_into_display_mapping(m, obs)
        self.assertEqual(n, 1)
        self.assertEqual(m["brands"]["NEWCO"], "Newco")

    def test_display_text_slash_brand(self):
        self.assertEqual(_display_text("brands", "BHB/NPM"), "BHB/NPM")


if __name__ == "__main__":
    unittest.main()
