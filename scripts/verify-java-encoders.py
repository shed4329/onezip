"""Verify active codecs and the distribution have no archive/codec library delegation."""
from pathlib import Path
import sys, zipfile, re

root=Path(__file__).resolve().parent.parent
forbidden=['java.util.zip.', 'DeflaterOutputStream','InflaterInputStream','net.sf.sevenzipjbinding',
           'net.lingala.zip4j', 'org.apache.commons.compress', 'org.tukaani.xz',
           'System.loadLibrary','System.load(','ProcessBuilder','Runtime.getRuntime().exec']
sources=list((root/'src').rglob('*.java'))
# The sole process exception is UI-only Explorer selection, never an archive codec.
file_manager=root/'src/cn/shed4329/onezip/themes/fxJava/fluent/component/FileManager.java'
issues=[]
manager_text=file_manager.read_text(encoding='utf-8')
if manager_text.count('new ProcessBuilder(')!=1 or 'newProcessBuilder(explorer.toString(),"/select,",path.toString()).start();' not in re.sub(r'\s+', '', manager_text):
    issues.append('Unexpected file-manager process command')
for source in (root/'src/cn/shed4329/onezip/engine').rglob('*.java'):
    text=source.read_text(encoding='utf-8')
    if re.search(r'import\s+(?:javafx\.|org\.controlsfx\.|jfxtras\.|cn\.shed4329\.onezip\.(?:themes|setting|FX|application|i18n)\.)', text):
        issues.append(f'{source.relative_to(root)} violates engine/UI-settings dependency boundary')
for source in sources:
    text=source.read_text(encoding='utf-8')
    for token in forbidden:
        if token in text and not (source==file_manager and token=="ProcessBuilder"):issues.append(f'{source.relative_to(root)} contains {token}')
if len(sys.argv)>1:
    with zipfile.ZipFile(sys.argv[1]) as jar:
        if 'cn/shed4329/onezip/Launcher.class' not in jar.namelist():issues.append('Missing migrated application launcher')
        manifest=jar.read('META-INF/MANIFEST.MF').decode('utf-8')
        if 'Main-Class: cn.shed4329.onezip.Launcher' not in manifest:issues.append('Incorrect application main class')
        for name in jar.namelist():
            if name.startswith('onezip/'):issues.append('Stale pre-migration class/resource: '+name)
            lower=name.lower()
            if any(token in lower for token in ['sevenzipjbinding','ark.x64','7z.dll','net/lingala/zip4j/',
                    'org/apache/commons/compress/', 'org/tukaani/xz/', 'cn/shed4329/onezip/compressutils/']):
                issues.append('Bundled legacy bridge or third-party archive implementation: '+name)
            if name.endswith('.class') and name.startswith('cn/shed4329/onezip/'):
                data=jar.read(name)
                for token in ['java/util/zip/', 'net/lingala/zip4j/', 'org/apache/commons/compress/',
                              'org/tukaani/xz/', 'net/sf/sevenzipjbinding/']:
                    if token.encode() in data:issues.append(f'{name} references {token}')
if issues:raise SystemExit('\n'.join(issues))
print(f'PASS: {len(sources)} active Java codec/integration sources; no JNI/process/archive-library/JDK ZIP codec delegation.')
if len(sys.argv)>1:print('PASS: packaged JAR excludes archive libraries and legacy wrappers; OneZip classes have no JDK ZIP codec references.')
