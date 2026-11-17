"""离线三资产与解包回归；不请求 JitPack/GitHub。"""
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('release_maven', Path(__file__).with_name('release-maven.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
SHA = 'a' * 40


class ReleaseMavenTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.archive = self.root / 'location-maven.tar.gz'
        self.tar('com/example/file', b'published bytes')
        self.receipt = dict(repository=dict(identity='gycrosskit/location', id=1395496718), component='location',
                            releaseVersion='1.0.2', source=dict(sha=SHA),
                            archive=dict(name=self.archive.name, size=self.archive.stat().st_size,
                                         sha256=hashlib.sha256(self.archive.read_bytes()).hexdigest()))
        self.write_receipt()
        (self.root / 'SHA256SUMS').write_text(self.receipt['archive']['sha256'] + '  ' + self.archive.name + '\n')

    def tar(self, name, data=b'', kind=tarfile.REGTYPE):
        with tarfile.open(self.archive, 'w:gz') as archive:
            member = tarfile.TarInfo(name)
            member.type = kind
            member.size = len(data) if kind == tarfile.REGTYPE else 0
            member.linkname = 'outside'
            archive.addfile(member, io.BytesIO(data))

    def write_receipt(self):
        (self.root / 'source-receipt.json').write_text(json.dumps(self.receipt))

    def test_valid_assets_extract_to_both_destinations(self):
        archive = release.verify(self.root, 'location', '1.0.2', SHA)
        for name in ['m2', 'staging']:
            release.extract(archive, self.root / name)
            self.assertEqual(b'published bytes', (self.root / name / 'com/example/file').read_bytes())

    def test_wrong_source_repository_version_and_archive_identity_are_rejected(self):
        for field, wrong in [('repository', dict(identity='gycrosskit/other', id=1395496718)),
                             ('releaseVersion', '1.0.1'), ('source', dict(sha='b' * 40)),
                             ('archive', dict(name='other.tar.gz', size=1, sha256='0' * 64))]:
            original = self.receipt[field]
            self.receipt[field] = wrong
            self.write_receipt()
            with self.assertRaises(ValueError):
                release.verify(self.root, 'location', '1.0.2', SHA)
            self.receipt[field] = original
        self.write_receipt()

    def test_wrong_checksum_and_archive_bytes_are_rejected(self):
        checksums = self.root / 'SHA256SUMS'
        original = checksums.read_bytes()
        checksums.write_bytes(b'wrong\n')
        with self.assertRaises(ValueError):
            release.verify(self.root, 'location', '1.0.2', SHA)
        checksums.write_bytes(original)
        self.archive.write_bytes(self.archive.read_bytes() + b'corrupt')
        with self.assertRaises(ValueError):
            release.verify(self.root, 'location', '1.0.2', SHA)

    def test_unsafe_members_and_existing_destination_links_are_rejected(self):
        for name, kind in [('../outside', tarfile.REGTYPE), ('/absolute', tarfile.REGTYPE),
                           ('link', tarfile.SYMTYPE), ('hardlink', tarfile.LNKTYPE), ('fifo', tarfile.FIFOTYPE)]:
            with self.subTest(name=name):
                self.tar(name, kind=kind)
                with self.assertRaises(ValueError):
                    release.extract(self.archive, self.root / 'output')
        self.tar('com/file')
        output = self.root / 'output'
        output.mkdir()
        (output / 'com').symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(ValueError):
            release.extract(self.archive, output)


if __name__ == '__main__':
    unittest.main()
