"""Tests for full-coverage client acceptance partitioning; no Minecraft process."""
import copy
import json
from pathlib import Path
import tempfile
import unittest

import plan_client_acceptance as planner


class ClientAcceptancePlanTest(unittest.TestCase):
    def test_longest_first_spreads_expensive_recipes(self):
        source = {"warlockery:" + key: ticks for key, ticks in zip("abcdefgh", (12000, 12000, 400, 400, 200, 200, 200, 200))}
        result = planner.partition_recipes(source, 2)
        self.assertEqual([shard["processing_ticks"] for shard in result], [12800, 12800])
        self.assertEqual(sum(len(shard["recipe_ids"]) for shard in result), 8)

    def test_order_does_not_depend_on_discovery(self):
        source = {"warlockery:" + key: 200 for key in "abcdefgh"}
        self.assertEqual(planner.partition_recipes(source, 3), planner.partition_recipes(dict(reversed(list(source.items()))), 3))

    def test_more_shards_than_recipes_fails(self):
        for count in (0, -1, True, 1.5, 3):
            with self.assertRaises(ValueError):
                planner.partition_recipes({"warlockery:a": 200, "warlockery:b": 100}, count)

    def test_invalid_processing_times_fail(self):
        for ticks in (0, -10, True, "200", 2.5):
            with self.assertRaises(ValueError):
                planner.partition_recipes({"warlockery:a": ticks}, 1)

    def test_missing_duplicate_unknown_or_wrong_weight_fails(self):
        source = {"warlockery:a": 200, "warlockery:b": 100}
        good = planner.partition_recipes(source, 2)
        for mutation in ("missing", "duplicate", "unknown", "weight", "index"):
            bad = copy.deepcopy(good)
            if mutation == "missing":
                bad[0]["recipe_ids"] = []
            elif mutation == "duplicate":
                bad[1]["recipe_ids"] = bad[0]["recipe_ids"].copy()
            elif mutation == "unknown":
                bad[0]["recipe_ids"] = ["warlockery:missing"]
            elif mutation == "weight":
                bad[0]["processing_ticks"] += 1
            else:
                bad[1]["shard"] = 0
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                planner.validate_plan(source, bad)

    def test_codec_default_and_nested_ids(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            (path / "nested").mkdir()
            (path / "nested/default.json").write_text('{"machine":"brazier"}', encoding="utf-8")
            (path / "explicit.json").write_text('{"processing_time":600}', encoding="utf-8")
            self.assertEqual(planner.read_recipes(path), {"warlockery:explicit":600, "warlockery:nested/default":200})

    def test_empty_census_fails(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(ValueError):
                planner.read_recipes(Path(root))

    def test_invalid_json_duration_fails(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            (path / "invalid.json").write_text('{"processing_time":false}', encoding="utf-8")
            with self.assertRaises(ValueError):
                planner.read_recipes(path)

    def report_fixture(self):
        source = {"warlockery:" + key: 200 for key in "abcdefgh"}
        shards = planner.partition_recipes(source, 2)
        common = {"failures": [], "all_machine_types_selected": True, "selected_machine_types": ["oven", "brazier"],
                  "available_recipe_count_in_machine_scope": len(source)}
        reports = [dict(common, requested_recipe_ids=["none"], selected_representative_types=["oven", "brazier"],
                        results=[{"machine": name, "passed": True} for name in ("oven", "brazier")],
                        recipe_results=[], required_recipe_count=0)]
        for shard in shards:
            reports.append(dict(common, requested_recipe_ids=shard["recipe_ids"].copy(), selected_representative_types=[],
                                results=[], required_recipe_count=len(shard["recipe_ids"]), recipe_sweep_complete=True,
                                recipe_results=[{"recipe": recipe, "passed": True, "status": "passed"} for recipe in shard["recipe_ids"]]))
        return source, shards, reports

    def test_finished_report_union_passes(self):
        source, shards, reports = self.report_fixture()
        result = planner.verify_report_data(source, shards, {"oven", "brazier"}, reports)
        self.assertIs(result["passed"], True)
        self.assertEqual(result["recipe_count"], 8)
        self.assertEqual(result["recipe_ids"], sorted(source))

    def test_incomplete_duplicate_or_failed_reports_rejected(self):
        for mutation in ("missing_report", "duplicate_report", "missing_recipe", "duplicate_recipe",
                         "unfinished", "failed_recipe", "unsupported", "failed_representative",
                         "missing_representative", "missing_scope", "wrong_census", "failure_list"):
            source, shards, reports = self.report_fixture()
            if mutation == "missing_report": reports.pop()
            elif mutation == "duplicate_report": reports[-1] = copy.deepcopy(reports[1])
            elif mutation == "missing_recipe": reports[1]["recipe_results"].pop()
            elif mutation == "duplicate_recipe": reports[1]["recipe_results"].append(reports[1]["recipe_results"][0].copy())
            elif mutation == "unfinished": reports[1]["recipe_sweep_complete"] = False
            elif mutation == "failed_recipe": reports[1]["recipe_results"][0]["passed"] = False
            elif mutation == "unsupported": reports[1]["recipe_results"][0]["status"] = "unsupported"
            elif mutation == "failed_representative": reports[0]["results"][0]["passed"] = False
            elif mutation == "missing_representative": reports[0]["results"].pop()
            elif mutation == "missing_scope": reports[1]["selected_machine_types"] = ["oven"]
            elif mutation == "wrong_census": reports[1]["available_recipe_count_in_machine_scope"] = 7
            else: reports[1]["failures"] = ["native assertion failed"]
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                planner.verify_report_data(source, shards, {"oven", "brazier"}, reports)

    def test_real_census_has_exact_once_matrix_coverage(self):
        source = planner.read_recipes(planner.DEFAULT_RECIPES)
        matrix = planner.build_matrix()
        self.assertEqual(len(matrix["include"]), 19)
        self.assertEqual([job["suite"] for job in matrix["include"][:2]], ["manual", "jei"])
        self.assertEqual(matrix["include"][2]["recipe_ids"], "none")
        self.assertEqual(matrix["include"][2]["representative_types"], "")
        shards = [job for job in matrix["include"] if job["id"].startswith("machines-")]
        actual = [recipe for job in shards for recipe in job["recipe_ids"].split(",")]
        self.assertEqual(len(actual), len(source))
        self.assertEqual(set(actual), set(source))
        self.assertTrue(all(job["representative_types"] == "none" for job in shards))
        self.assertEqual(sum(job["processing_ticks"] for job in shards), sum(source.values()))
        self.assertEqual(len({job["id"] for job in matrix["include"]}), len(matrix["include"]))
        ritual_jobs = [job for job in matrix["include"] if job["suite"] == "ritual"]
        self.assertEqual(len(ritual_jobs), 8)
        ritual_ids = [ritual for job in ritual_jobs for ritual in job["ritual_ids"].split(",")]
        self.assertEqual(len(ritual_ids), 109)
        self.assertEqual(set(ritual_ids), planner.read_rituals(planner.DEFAULT_RITUALS))
        self.assertTrue(all(not job["ritual_ids"] for job in matrix["include"] if job["suite"] != "ritual"))

    def test_ritual_partitions_are_balanced_and_deterministic(self):
        source = set("abcdefghijk")
        shards = planner.partition_rituals(source, 3)
        self.assertEqual([len(shard["ritual_ids"]) for shard in shards], [4, 4, 3])
        self.assertEqual(shards, planner.partition_rituals(set(reversed(sorted(source))), 3))
        for count in (0, -1, True, 1.5, 12):
            with self.subTest(count=count), self.assertRaises(ValueError):
                planner.partition_rituals(source, count)

    def test_invalid_ritual_plans_rejected(self):
        source = set("abcd")
        for mutation in ("missing", "duplicate", "unknown", "index", "empty"):
            shards = planner.partition_rituals(source, 2)
            if mutation == "missing": shards[0]["ritual_ids"].pop()
            elif mutation == "duplicate": shards[1]["ritual_ids"] = shards[0]["ritual_ids"].copy()
            elif mutation == "unknown": shards[0]["ritual_ids"][0] = "unknown"
            elif mutation == "index": shards[1]["shard"] = 0
            else: shards.append({"shard": 2, "ritual_ids": []})
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                planner.validate_ritual_plan(source, shards)

    def test_read_rituals_validates_census_and_nested_ids(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            with self.assertRaises(ValueError): planner.read_rituals(path)
            (path / "nested").mkdir()
            target = path / "nested/rite.json"
            target.write_text('{"action":"summon_entity"}', encoding="utf-8")
            self.assertEqual(planner.read_rituals(path), {"nested/rite"})
            for invalid in ('{}', '{"action":""}', '{"action":false}', '[]'):
                target.write_text(invalid, encoding="utf-8")
                with self.subTest(invalid=invalid), self.assertRaises(ValueError):
                    planner.read_rituals(path)

    def ritual_report_fixture(self):
        rituals = set("abcdefgh")
        shards = planner.partition_rituals(rituals, 2)
        reports = []
        for shard in shards:
            selected = shard["ritual_ids"].copy()
            reports.append({"passed": True, "selected_ritual_ids": selected,
                            "ritual_results": {ritual: {"status": "PASSED" if ritual in selected else "NOT_RUN",
                                                       "evidence": "Native activation outcome."} for ritual in rituals},
                            "ritual_summary": {"total": 8, "selected": 4, "passed": 4,
                                               "selected_not_run": 0, "selected_not_run_ids": [], "not_run": 4}})
        return rituals, shards, reports

    def test_finished_ritual_union_passes(self):
        rituals, shards, reports = self.ritual_report_fixture()
        reports[0]["selected_ritual_ids"].reverse()
        result = planner.verify_ritual_report_data(rituals, shards, reports)
        self.assertEqual(result, {"ritual_count": 8, "ritual_partitions": 2, "ritual_ids": sorted(rituals)})

    def test_incomplete_duplicate_or_failed_ritual_reports_rejected(self):
        for mutation in ("missing_report", "duplicate_report", "unfinished", "failure", "duplicate_selection",
                         "unknown_selection", "missing_census", "extra_census", "failed", "not_run",
                         "unselected_passed", "missing_evidence", "wrong_summary", "missing_summary"):
            rituals, shards, reports = self.ritual_report_fixture()
            first = reports[0]
            selected = first["selected_ritual_ids"][0]
            unselected = reports[1]["selected_ritual_ids"][0]
            if mutation == "missing_report": reports.pop()
            elif mutation == "duplicate_report": reports[1] = copy.deepcopy(first)
            elif mutation == "unfinished": first["passed"] = False
            elif mutation == "failure": first["failure"] = "native assertion failed"
            elif mutation == "duplicate_selection": first["selected_ritual_ids"].append(selected)
            elif mutation == "unknown_selection": first["selected_ritual_ids"][0] = "unknown"
            elif mutation == "missing_census": first["ritual_results"].pop(unselected)
            elif mutation == "extra_census": first["ritual_results"]["unknown"] = {"status": "NOT_RUN"}
            elif mutation == "failed": first["ritual_results"][selected]["status"] = "FAILED"
            elif mutation == "not_run": first["ritual_results"][selected]["status"] = "NOT_RUN"
            elif mutation == "unselected_passed": first["ritual_results"][unselected]["status"] = "PASSED"
            elif mutation == "missing_evidence": first["ritual_results"][selected]["evidence"] = ""
            elif mutation == "wrong_summary": first["ritual_summary"]["passed"] = 3
            else: first.pop("ritual_summary")
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                planner.verify_ritual_report_data(rituals, shards, reports)

    def test_filesystem_aggregate_requires_both_exact_unions(self):
        recipes, _, machine_reports = self.report_fixture()
        rituals, _, ritual_reports = self.ritual_report_fixture()
        with tempfile.TemporaryDirectory() as root:
            path = Path(root)
            machine_dir, ritual_dir, report_dir = (path / name for name in ("machines", "rituals", "reports"))
            for directory in (machine_dir, ritual_dir, report_dir): directory.mkdir()
            for index, (recipe, ticks) in enumerate(recipes.items()):
                (machine_dir / (recipe.split(":")[1] + ".json")).write_text(
                    json.dumps({"processing_time": ticks, "machine": "oven" if index % 2 else "brazier"}), encoding="utf-8")
            for ritual in rituals:
                (ritual_dir / (ritual + ".json")).write_text('{"action":"summon_entity"}', encoding="utf-8")
            for filename, reports in (("machine-walkthrough.json", machine_reports), ("ritual-walkthrough.json", ritual_reports)):
                for index, report in enumerate(reports):
                    target = report_dir / (filename + str(index))
                    target.mkdir()
                    (target / filename).write_text(json.dumps(report), encoding="utf-8")
            result = planner.verify_reports(machine_dir, report_dir, 2, ritual_dir, 2)
            self.assertEqual(result["recipe_count"], 8)
            self.assertEqual(result["ritual_count"], 8)
            self.assertIs(result["passed"], True)
            (report_dir / "ritual-walkthrough.json0" / "ritual-walkthrough.json").unlink()
            with self.assertRaises(ValueError):
                planner.verify_reports(machine_dir, report_dir, 2, ritual_dir, 2)


if __name__ == "__main__":
    unittest.main()
