"""Regression checks for the read-only release-JAR compatibility inspection."""
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import textwrap
import unittest
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]


class ReleaseCompatibilityTest(unittest.TestCase):
    def run_inspection(self, metadata, supporter=False):
        workflow = (ROOT / ".github/workflows/publish-curseforge.yml").read_text()
        section = workflow.split("- name: Inspect release Minecraft compatibility", 1)[1]
        source = textwrap.dedent(section.split("python3 - <<'PY'\n", 1)[1].split("\n          PY", 1)[0])
        with tempfile.TemporaryDirectory() as temporary:
            directory = pathlib.Path(temporary)
            (directory / "artifacts").mkdir()
            for index, (name, contents) in enumerate(metadata):
                with zipfile.ZipFile(directory / "artifacts" / f"mod-{index}.jar", "w") as jar:
                    jar.writestr(name, contents)
            output = directory / "environment"
            result = subprocess.run([sys.executable, "-c", source], cwd=directory,
                                    env={**os.environ, "GITHUB_ENV": str(output),
                                         "SUPPORTER_NEOFORGE_ONLY": str(supporter).lower()},
                                    capture_output=True, text=True)
            return result, output.read_text() if output.exists() else ""

    def fabric(self, target):
        return ("fabric.mod.json", json.dumps({"id": "warlockery", "depends": {"minecraft": "~" + target}}))

    def forge(self, target, metadata="META-INF/mods.toml"):
        upper = "26.4" if target == "26.3" else "26.3"
        return (metadata, f'[[dependencies.warlockery]]\nmodId="minecraft"\nversionRange="[{target},{upper})"\n')

    def test_current_fabric_target(self):
        result, output = self.run_inspection([self.fabric("26.3")])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("MINECRAFT_TARGET=26.3\n", output)

    def test_current_forge_and_neoforge_targets(self):
        result, output = self.run_inspection([self.forge("26.3"), self.forge("26.3", "META-INF/neoforge.mods.toml")])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("MINECRAFT_TARGET=26.3\n", output)

    def test_supporter_stays_on_262(self):
        result, output = self.run_inspection([self.forge("26.2", "META-INF/neoforge.mods.toml")], supporter=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("MINECRAFT_TARGET=26.2\n", output)

    def test_mixed_targets_are_rejected(self):
        result, output = self.run_inspection([self.fabric("26.3"), self.forge("26.2")])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", output)

    def test_supporter_upgrade_is_rejected(self):
        result, output = self.run_inspection([self.fabric("26.3")], supporter=True)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", output)

    def test_unsupported_constraint_is_rejected(self):
        result, output = self.run_inspection([self.fabric("26.4")])
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("", output)

    def test_missing_metadata_and_empty_release_are_rejected(self):
        for metadata in ([("unrelated.txt", "")], []):
            with self.subTest(metadata=metadata):
                result, output = self.run_inspection(metadata)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual("", output)


if __name__ == "__main__":
    unittest.main()

