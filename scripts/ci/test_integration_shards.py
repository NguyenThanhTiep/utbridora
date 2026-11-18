import tempfile
import unittest
from pathlib import Path
from integration_shards import check_reports, discover, partition


class IntegrationShardsTest(unittest.TestCase):
    def test_complete_disjoint_balanced_partition_including_new_classes(self):
        classes = ['p.SlowIT', 'p.MediumIT', 'p.NewIT', 'p.OtherIT']
        groups = partition(classes, {'p.SlowIT': 90, 'p.MediumIT': 50}, 3)
        self.assertEqual(sorted(classes), sorted(name for group in groups for name in group))
        self.assertTrue(all(groups))
        self.assertEqual(groups, partition(list(reversed(classes)), {'p.SlowIT': 90, 'p.MediumIT': 50}, 3))

    def test_discovery_matches_failsafe_defaults(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'src/test/java/p'
            source.mkdir(parents=True)
            for name in ['ITFirst', 'LastIT', 'LastITCase', 'NormalTest', 'SomeITHelper']:
                (source / f'{name}.java').write_text('package p;\nclass ' + name + ' {}', encoding='utf-8')
            self.assertEqual(['p.ITFirst', 'p.LastIT', 'p.LastITCase'], discover(root))

    def test_missing_and_extra_reports_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            plan = {'classes': ['p.FirstIT', 'p.SecondIT']}
            (folder / 'TEST-first.xml').write_text('<testsuite name="p.FirstIT"><testcase/></testsuite>')
            with self.assertRaises(ValueError):
                check_reports(plan, folder)
            (folder / 'TEST-second.xml').write_text('<testsuite name="p.SecondIT"><testcase/></testsuite>')
            self.assertEqual(2, check_reports(plan, folder))
            (folder / 'TEST-extra.xml').write_text('<testsuite name="p.ExtraIT"><testcase/></testsuite>')
            with self.assertRaises(ValueError):
                check_reports(plan, folder)

    def test_failed_empty_and_duplicate_suites_fail(self):
        for report in ['<testcase><failure/></testcase>', '', '<testcase><error/></testcase>']:
            with self.subTest(report=report), tempfile.TemporaryDirectory() as directory:
                folder = Path(directory)
                path = folder / 'TEST-first.xml'
                path.write_text(f'<testsuite name="p.FirstIT">{report}</testsuite>')
                with self.assertRaises(ValueError):
                    check_reports({'classes': ['p.FirstIT']}, folder)
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for name in ['first', 'duplicate']:
                (folder / f'TEST-{name}.xml').write_text('<testsuite name="p.FirstIT"><testcase/></testsuite>')
            with self.assertRaises(ValueError):
                check_reports({'classes': ['p.FirstIT']}, folder)


if __name__ == '__main__':
    unittest.main()
