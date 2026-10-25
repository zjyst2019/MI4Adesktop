# Windows equivalent of the port's verify.sh: build, then check the APK itself.
# Requires JDK 17, Android SDK platform 34 + build-tools 34.0.0, Python 3.
# The port's own verify.sh is the reference; this runs the same steps on Windows.
#
# Usage (from this repository's root):
#   $env:JAVA_HOME    = 'C:\path\to\jdk17'
#   $env:ANDROID_HOME = 'C:\path\to\android-sdk'
#   pwsh .\tools\build-xmen-api23.ps1
param(
    # The port lives in launcher/ inside this repository; -Port overrides it.
    [string]$Port = (Join-Path $PSScriptRoot '..\launcher')
)
$ErrorActionPreference = 'Stop'
$py = 'python'

Set-Location $port
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

'=== gradle: testReleaseUnitTest lintRelease assembleRelease ==='
Push-Location (Join-Path $port 'launcher')
& .\gradlew.bat --no-daemon --console=plain testReleaseUnitTest lintRelease assembleRelease
$gradleExit = $LASTEXITCODE
Pop-Location
if ($gradleExit -ne 0) { throw "gradle failed with exit $gradleExit" }

'=== APK verification ==='
& $py -c @"
from pathlib import Path
import xml.etree.ElementTree as ET
import struct
import zipfile

base = Path(r'$Port\app\build')
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(base / 'intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml').getroot()
assert manifest.get('package') == 'com.peter.tvhome.xmen', manifest.get('package')
assert manifest.find('uses-sdk').get(android + 'minSdkVersion') == '23'
permissions = {node.get(android + 'name') for node in manifest.findall('uses-permission')}
assert 'android.permission.INTERNET' not in permissions, 'INTERNET permission present'

lint = ET.parse(base / 'reports/lint-results-release.xml').getroot()
errors = [i for i in lint.findall('issue') if i.get('severity') in {'Error', 'Fatal'}]
assert not errors, [i.get('id') for i in errors]

results = [ET.parse(p).getroot() for p in (base / 'test-results/testReleaseUnitTest').glob('TEST-*.xml')]
assert results, 'no test results'
assert all(int(r.get('failures', 0)) == 0 and int(r.get('errors', 0)) == 0 for r in results)

apk = base / 'outputs/apk/release/app-release.apk'
definitions = set()
with zipfile.ZipFile(apk) as archive:
    dex_files = [archive.read(n) for n in archive.namelist() if n.endswith('.dex')]
    for data in dex_files:
        assert data[:8] == b'dex\n035\x00', 'DEX version must support API 23'
        def uint(offset):
            return struct.unpack_from('<I', data, offset)[0]
        strings = []
        for index in range(uint(56)):
            offset = uint(uint(60) + 4 * index)
            while data[offset] & 128:
                offset += 1
            offset += 1
            strings.append(data[offset:data.index(b'\0', offset)].decode(errors='replace'))
        types = [strings[uint(uint(68) + 4 * i)] for i in range(uint(64))]
        definitions.update(types[uint(uint(100) + 32 * i)] for i in range(uint(96)))
assert 'Lj`$/time/LocalDate;' in definitions, 'Missing time backport implementation'
assert 'Ljava/util/function/Predicate;' in definitions, 'Missing API 23 function implementation'

print('JVM tests:', sum(int(r.get('tests', 0)) for r in results))
print('Lint errors: 0')
print('package: com.peter.tvhome.xmen; minSdk: 23; INTERNET: absent')
print('APK DEX 035: time and functional-interface backport classes verified')
print('APK:', f'{apk.stat().st_size:,} bytes ({apk.stat().st_size / 1024:.1f} KiB)')
print('Build verification only. API 23 device runtime and remote navigation are NOT verified.')
"@
if ($LASTEXITCODE -ne 0) { throw "APK verification failed" }

'=== done ==='
