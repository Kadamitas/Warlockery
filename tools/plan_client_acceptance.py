"""Plan complete, deterministic CI coverage without changing recipe durations."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re


DEFAULT_RECIPES = Path(__file__).resolve().parents[1] / "src/main/resources/data/warlockery/warlockery_machine"
DEFAULT_RITUALS = Path(__file__).resolve().parents[1] / "src/main/resources/data/warlockery/ritual"


def read_recipes(directory: Path) -> dict[str, int]:
    recipes = {}
    for path in sorted(directory.rglob("*.json")):
        relative = path.relative_to(directory).with_suffix("").as_posix()
        if re.fullmatch(r"[a-z0-9_./-]+", relative) is None:
            raise ValueError(f"Invalid recipe ID path: {relative}")
        recipe_id = "warlockery:" + relative
        data = json.loads(path.read_text(encoding="utf-8"))
        ticks = data.get("processing_time", 200)  # MachineRecipeDefinition.CODEC default.
        if type(ticks) is not int or ticks <= 0:
            raise ValueError(f"Invalid processing_time for {recipe_id}: {ticks!r}")
        if recipe_id in recipes:
            raise ValueError(f"Duplicate recipe ID: {recipe_id}")
        recipes[recipe_id] = ticks
    if not recipes:
        raise ValueError(f"No machine recipes found in {directory}")
    return recipes


def validate_plan(recipes: dict[str, int], shards: list[dict]) -> None:
    actual = Counter(recipe for shard in shards for recipe in shard["recipe_ids"])
    if actual != Counter({recipe: 1 for recipe in recipes}):
        raise ValueError("Machine partitions must cover every source recipe exactly once")
    if [shard["shard"] for shard in shards] != list(range(len(shards))):
        raise ValueError("Machine shard indices must be contiguous and unique")
    for shard in shards:
        if not shard["recipe_ids"]:
            raise ValueError("Empty machine shard would run no recipe tests")
        if shard["processing_ticks"] != sum(recipes[recipe] for recipe in shard["recipe_ids"]):
            raise ValueError("Machine shard processing total does not match source durations")


def partition_recipes(recipes: dict[str, int], count: int = 8) -> list[dict]:
    if type(count) is not int or not 1 <= count <= len(recipes):
        raise ValueError("Shard count must be positive and cannot exceed the recipe count")
    if any(type(ticks) is not int or ticks <= 0 for ticks in recipes.values()):
        raise ValueError("Recipe processing times must be positive integers")
    shards = [{"shard": index, "recipe_ids": [], "processing_ticks": 0} for index in range(count)]
    # Longest processing first, stable ID/index ties: the ten-minute recipes
    # cannot accidentally accumulate in one job. No recipe timing is modified.
    for recipe_id, ticks in sorted(recipes.items(), key=lambda pair: (-pair[1], pair[0])):
        target = min(shards, key=lambda shard: (shard["processing_ticks"], len(shard["recipe_ids"]), shard["shard"]))
        target["recipe_ids"].append(recipe_id)
        target["processing_ticks"] += ticks
    for shard in shards:
        shard["recipe_ids"].sort()
    validate_plan(recipes, shards)
    return shards


def plan_machines(recipe_dir: Path = DEFAULT_RECIPES, count: int = 8) -> list[dict]:
    return partition_recipes(read_recipes(recipe_dir), count)


def read_rituals(directory: Path) -> set[str]:
    rituals = set()
    for path in sorted(directory.rglob("*.json")):
        ritual_id = path.relative_to(directory).with_suffix("").as_posix()
        if re.fullmatch(r"[a-z0-9_./-]+", ritual_id) is None:
            raise ValueError(f"Invalid ritual ID path: {ritual_id}")
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or not isinstance(data.get("action"), str) or not data["action"]:
            raise ValueError(f"Missing ritual action: {ritual_id}")
        rituals.add(ritual_id)
    if not rituals:
        raise ValueError(f"No rituals found in {directory}")
    return rituals


def validate_ritual_plan(rituals: set[str], shards: list[dict]) -> None:
    actual = Counter(ritual for shard in shards for ritual in shard["ritual_ids"])
    if not rituals or actual != Counter({ritual: 1 for ritual in rituals}):
        raise ValueError("Ritual partitions must cover every source ritual exactly once")
    if [shard["shard"] for shard in shards] != list(range(len(shards))):
        raise ValueError("Ritual shard indices must be contiguous and unique")
    if any(not shard["ritual_ids"] for shard in shards):
        raise ValueError("Empty ritual partition would run no acceptance cases")


def partition_rituals(rituals: set[str], count: int = 8) -> list[dict]:
    if type(count) is not int or not 1 <= count <= len(rituals):
        raise ValueError("Ritual shard count must be positive and cannot exceed the ritual count")
    # Native chalk/GUI fixture setup dominates casting time. Balance actual
    # case counts instead of pretending the JSON casting duration is total cost.
    ordered = sorted(rituals)
    shards = [{"shard": index, "ritual_ids": ordered[index::count]} for index in range(count)]
    validate_ritual_plan(rituals, shards)
    return shards


def build_matrix(recipe_dir: Path = DEFAULT_RECIPES, count: int = 8,
                 ritual_dir: Path = DEFAULT_RITUALS, ritual_count: int = 8) -> dict:
    include = [
        {"id": suite, "suite": suite, "recipe_ids": "", "representative_types": "", "ritual_ids": "", "processing_ticks": 0}
        for suite in ("manual", "jei")
    ]
    include.append({"id": "machine-representatives", "suite": "machines", "recipe_ids": "none",
                    "representative_types": "", "ritual_ids": "", "processing_ticks": 0})
    for shard in plan_machines(recipe_dir, count):
        include.append({"id": f"machines-{shard['shard']}", "suite": "machines",
                        "recipe_ids": ",".join(shard["recipe_ids"]), "representative_types": "none",
                        "ritual_ids": "", "processing_ticks": shard["processing_ticks"]})
    for shard in partition_rituals(read_rituals(ritual_dir), ritual_count):
        include.append({"id": f"rituals-{shard['shard']}", "suite": "ritual", "recipe_ids": "",
                        "representative_types": "", "ritual_ids": ",".join(shard["ritual_ids"]),
                        "processing_ticks": 0})
    return {"include": include}


def verify_report_data(recipes: dict[str, int], shards: list[dict], machine_types: set[str],
                       reports: list[dict]) -> dict:
    validate_plan(recipes, shards)
    if len(reports) != len(shards) + 1:
        raise ValueError("Expected one finished report per recipe partition and one representatives report")
    pending = {tuple(shard["recipe_ids"]) for shard in shards}
    actual = Counter()
    representatives = 0
    for report in reports:
        if report.get("failures") != [] or report.get("all_machine_types_selected") is not True:
            raise ValueError("Machine report failed or narrowed its machine-type scope")
        if Counter(report.get("selected_machine_types", [])) != Counter({kind: 1 for kind in machine_types}):
            raise ValueError("Machine report type census differs from source")
        if report.get("available_recipe_count_in_machine_scope") != len(recipes):
            raise ValueError("Runtime recipe census differs from source")
        requested = report.get("requested_recipe_ids", [])
        representative_rows = [row for row in report.get("results", []) if "machine" in row]
        if requested == ["none"]:
            representatives += 1
            if Counter(report.get("selected_representative_types", [])) != Counter({kind: 1 for kind in machine_types}):
                raise ValueError("Representative type selection is incomplete")
            if Counter(row["machine"] for row in representative_rows) != Counter({kind: 1 for kind in machine_types}):
                raise ValueError("Representative result census is incomplete or duplicated")
            if any(row.get("passed") is not True for row in representative_rows):
                raise ValueError("Representative walkthrough did not pass")
            if report.get("recipe_results") != [] or report.get("required_recipe_count") != 0:
                raise ValueError("Representatives report unexpectedly includes a recipe sweep")
            continue
        signature = tuple(sorted(requested))
        if signature not in pending:
            raise ValueError("Duplicate, omitted or unplanned recipe partition")
        pending.remove(signature)
        if report.get("selected_representative_types") != [] or representative_rows:
            raise ValueError("Recipe partition unexpectedly repeats representative walkthroughs")
        rows = report.get("recipe_results", [])
        if report.get("recipe_sweep_complete") is not True or report.get("required_recipe_count") != len(requested):
            raise ValueError("Recipe sweep has not completed its exact planned count")
        if Counter(row.get("recipe") for row in rows) != Counter(requested):
            raise ValueError("Actual recipe results differ from the planned partition")
        if any(row.get("passed") is not True or row.get("status") != "passed" for row in rows):
            raise ValueError("Recipe walkthrough failed, is unsupported or remains unfinished")
        actual.update(row["recipe"] for row in rows)
    if representatives != 1 or pending or actual != Counter({recipe: 1 for recipe in recipes}):
        raise ValueError("Completed reports do not cover every source recipe exactly once")
    return {"schema": 1, "passed": True, "recipe_count": len(recipes),
            "machine_partitions": len(shards), "representative_types": sorted(machine_types),
            "processing_ticks": sum(recipes.values()), "recipe_ids": sorted(actual)}


def verify_ritual_report_data(rituals: set[str], shards: list[dict], reports: list[dict]) -> dict:
    validate_ritual_plan(rituals, shards)
    if len(reports) != len(shards):
        raise ValueError("Expected one finished report per ritual partition")
    pending = {tuple(shard["ritual_ids"]) for shard in shards}
    actual = Counter()
    for report in reports:
        if report.get("passed") is not True or report.get("failure") is not None:
            raise ValueError("Ritual report failed or remains unfinished")
        selected = report.get("selected_ritual_ids", [])
        signature = tuple(sorted(selected))
        if signature not in pending:
            raise ValueError("Duplicate, omitted or unplanned ritual partition")
        pending.remove(signature)
        results = report.get("ritual_results", {})
        if set(results) != rituals:
            raise ValueError("Runtime ritual census differs from source")
        selected_set = set(selected)
        for ritual, result in results.items():
            expected = "PASSED" if ritual in selected_set else "NOT_RUN"
            if result.get("status") != expected:
                raise ValueError(f"Ritual {ritual} did not report its expected {expected} status")
            if ritual in selected_set and (not isinstance(result.get("evidence"), str) or not result["evidence"].strip()):
                raise ValueError(f"Ritual {ritual} has no completed outcome evidence")
        summary = report.get("ritual_summary", {})
        expected = {"total": len(rituals), "selected": len(selected), "passed": len(selected),
                    "selected_not_run": 0, "selected_not_run_ids": [], "not_run": len(rituals) - len(selected)}
        if summary != expected:
            raise ValueError("Ritual summary does not match completed partition outcomes")
        actual.update(selected)
    if pending or actual != Counter({ritual: 1 for ritual in rituals}):
        raise ValueError("Completed reports do not cover every source ritual exactly once")
    return {"ritual_count": len(rituals), "ritual_partitions": len(shards), "ritual_ids": sorted(actual)}


def verify_reports(recipe_dir: Path, report_dir: Path, count: int = 8,
                   ritual_dir: Path = DEFAULT_RITUALS, ritual_count: int = 8) -> dict:
    recipes = read_recipes(recipe_dir)
    machine_types = {json.loads(path.read_text(encoding="utf-8"))["machine"] for path in recipe_dir.rglob("*.json")}
    reports = [json.loads(path.read_text(encoding="utf-8"))
               for path in sorted(report_dir.rglob("machine-walkthrough.json"))]
    result = verify_report_data(recipes, partition_recipes(recipes, count), machine_types, reports)
    rituals = read_rituals(ritual_dir)
    ritual_reports = [json.loads(path.read_text(encoding="utf-8"))
                      for path in sorted(report_dir.rglob("ritual-walkthrough.json"))]
    result.update(verify_ritual_report_data(rituals, partition_rituals(rituals, ritual_count), ritual_reports))
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipes", type=Path, default=DEFAULT_RECIPES)
    parser.add_argument("--shards", type=int, default=8)
    parser.add_argument("--rituals", type=Path, default=DEFAULT_RITUALS)
    parser.add_argument("--ritual-shards", type=int, default=8)
    parser.add_argument("--github-output", type=Path)
    parser.add_argument("--verify-reports", type=Path)
    args = parser.parse_args()
    if args.verify_reports:
        if args.github_output:
            parser.error("--verify-reports cannot be combined with --github-output")
        print(json.dumps(verify_reports(args.recipes, args.verify_reports, args.shards, args.rituals, args.ritual_shards), separators=(",", ":")))
        return
    matrix = json.dumps(build_matrix(args.recipes, args.shards, args.rituals, args.ritual_shards), separators=(",", ":"))
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write("matrix=" + matrix + "\n")
    print(matrix)


if __name__ == "__main__":
    main()
