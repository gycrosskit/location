#!/usr/bin/env python3
"""下载并验收准确 Tag 的三资产，再安全展开 Maven 归档。"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import tarfile
import tempfile


def require(condition, message):
    if not condition:
        raise ValueError(message)


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, '重复 JSON 字段')
        result[key] = value
    return result


def verify(directory, repository, version, sha):
    receipt = json.loads((directory / 'source-receipt.json').read_text(), object_pairs_hook=unique)
    require(receipt.get('repository') == {'identity': 'gycrosskit/' + repository, 'id': 1395496718}, '仓库身份不符')
    require(receipt.get('component') == repository and receipt.get('releaseVersion') == version, '组件或版本不符')
    require(receipt.get('source', {}).get('sha') == sha, '源码 SHA 与 Tag 不符')
    archive = receipt.get('archive', {})
    name = repository + '-maven.tar.gz'
    require(set(archive) == {'name', 'sha256', 'size'} and archive['name'] == name
            and type(archive['size']) is int and 0 < archive['size'] <= 512 * 1024**2
            and isinstance(archive['sha256'], str) and re.fullmatch('[0-9a-f]{64}', archive['sha256']), '归档身份无效')
    require((directory / 'SHA256SUMS').read_bytes() == (archive['sha256'] + '  ' + name + '\n').encode(), '摘要文件不符')
    path = directory / name
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    require(path.stat().st_size == archive['size'] and digest.hexdigest() == archive['sha256'], '归档字节不符')
    return path


def extract(archive, destination):
    root = destination.resolve()
    with tarfile.open(archive) as bundle:
        members = bundle.getmembers()
        require(len(members) <= 100_000 and sum(member.size for member in members) <= 2 * 1024**3, '归档展开超限')
        names = set()
        for member in members:
            path = PurePosixPath(member.name)
            require(not path.is_absolute() and '..' not in path.parts and member.name not in names
                    and (member.isfile() or member.isdir()), '归档路径、类型或重复成员无效')
            names.add(member.name)
            target = root.joinpath(*path.parts)
            resolved = target.resolve()
            require(resolved == root or root in resolved.parents, '归档越界')
            require(not any(p.is_symlink() for p in [target, *target.parents] if p == root or root in p.parents), '目标含链接')
        # 所有成员先验收，避免恶意成员发现前就写入合法文件。
        root.mkdir(parents=True, exist_ok=True)
        for member in members:
            target = root / member.name
            if member.isdir():
                target.mkdir(parents=True, exist_ok=True)
            else:
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(member) as source, target.open('wb') as output:
                    shutil.copyfileobj(source, output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', default='location', choices=['location'])
    parser.add_argument('--version', required=True)
    parser.add_argument('--expected-sha', required=True)
    parser.add_argument('destinations', type=Path, nargs='+')
    args = parser.parse_args()
    require(re.fullmatch('[0-9][0-9A-Za-z._-]*', args.version) and re.fullmatch('[0-9a-f]{40}', args.expected_sha), '版本或 SHA 无效')
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        for name in [args.repository + '-maven.tar.gz', 'source-receipt.json', 'SHA256SUMS']:
            limit = 512 * 1024**2 if name.endswith('.tar.gz') else 1024**2
            subprocess.run(['curl', '-fsSL', '--retry', '3', '--connect-timeout', '30', '--max-time', '300',
                            '--max-filesize', str(limit), '-o', str(directory / name),
                            'https://github.com/gycrosskit/' + args.repository + '/releases/download/' + args.version + '/' + name], check=True)
        archive = verify(directory, args.repository, args.version, args.expected_sha)
        for destination in args.destinations:
            extract(archive, destination)


if __name__ == '__main__':
    main()
