"""Keep generated bundles identical under Windows and POSIX default path sorting."""
import builtins
import contextlib
import io
from pathlib import Path
import runpy
import tempfile
import unittest


class BundlePortabilityTest(unittest.TestCase):
    def render(self, destination, case_insensitive):
        module = runpy.run_path(str(Path(__file__).with_name('build_contract_bundle.py')))
        build = module['build']
        build.__globals__['OUTPUT'] = destination

        def platform_sorted(values, *args, **kwargs):
            values = list(values)
            if 'key' not in kwargs and values and all(isinstance(p, Path) for p in values):
                kwargs['key'] = lambda p: p.as_posix().casefold() if case_insensitive else p.as_posix()
            return builtins.sorted(values, *args, **kwargs)

        build.__globals__['sorted'] = platform_sorted
        with contextlib.redirect_stdout(io.StringIO()):
            build()
        return {p.name: p.read_bytes() for p in destination.iterdir()}

    def test_windows_and_posix_render_identical_bundles(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.assertEqual(self.render(root / 'windows', True), self.render(root / 'posix', False))


if __name__ == '__main__':
    unittest.main()
