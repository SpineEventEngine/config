from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "update_copyright.py"


class UpdateCopyrightTest(unittest.TestCase):
    def test_default_run_skips_tracked_files_deleted_from_working_tree(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            self.write_profile(root)
            source = root / "Foo.java"
            source.write_text("class Foo {}\n", encoding="utf-8")

            subprocess.run(["git", "init", "-q"], cwd=root, check=True)
            subprocess.run(["git", "add", "Foo.java"], cwd=root, check=True)
            source.unlink()

            result = subprocess.run(
                [
                    sys.executable,
                    str(SCRIPT),
                    "--root",
                    str(root),
                    "--dry-run",
                ],
                check=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Would update 0 file(s).", result.stdout)
            self.assertEqual(result.stderr, "")

    @staticmethod
    def write_profile(root: Path) -> None:
        copyright_dir = root / ".idea" / "copyright"
        copyright_dir.mkdir(parents=True)
        (copyright_dir / "profiles_settings.xml").write_text(
            '<component name="CopyrightManager">'
            '<settings default="Default" />'
            "</component>\n",
            encoding="utf-8",
        )
        (copyright_dir / "Default.xml").write_text(
            '<component name="CopyrightManager">'
            "<copyright>"
            '<option name="notice" '
            'value="Copyright ${today.year} ACME&#10;All rights reserved" />'
            "</copyright>"
            "</component>\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    unittest.main()
