param(
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [Parameter(Mandatory=$true)][string]$KeyStorePath,
    [string]$JdkRoot = 'C:\Program Files\Zulu\zulu-25',
    [string]$OutputDirectory = (Join-Path $PSScriptRoot 'build')
)
$ErrorActionPreference='Stop'
$env:JAVA_HOME=$JdkRoot
$env:PATH=(Join-Path $JdkRoot 'bin')+';'+$env:PATH
$platform=Get-ChildItem $SdkRoot -Recurse -Filter android.jar | Select-Object -First 1 -ExpandProperty FullName
$aapt=Get-ChildItem $SdkRoot -Recurse -Filter aapt2.exe | Select-Object -First 1 -ExpandProperty FullName
if(-not $platform -or -not $aapt){throw 'SDK must contain Android platform 36 and Build Tools 36.0.0'}
$tools=Split-Path $aapt
$api=Join-Path $PSScriptRoot 'libs\api-102.0.0.jar'
$service=Join-Path $PSScriptRoot 'libs\service-102.0.0.jar'
$interface=Join-Path $PSScriptRoot 'libs\interface-102.0.0.jar'
$classpath="$platform;$api;$service;$interface"
New-Item -ItemType Directory -Force $OutputDirectory,(Join-Path $OutputDirectory 'classes'),(Join-Path $OutputDirectory 'dex') | Out-Null
function Check([string]$Step){if($LASTEXITCODE -ne 0){throw "$Step failed ($LASTEXITCODE)"}}
$classes=Join-Path $OutputDirectory 'classes'
# The Compose UI lives in app/src/main/kotlin and is built by Gradle (see README).
# This script keeps building the original pure-Java UI from legacy-java so that
# the hand-rolled toolchain still produces a complete, working APK.
$sources=@()
$sources+=Get-ChildItem (Join-Path $PSScriptRoot 'src') -Recurse -Filter *.java | Select-Object -ExpandProperty FullName
$legacy=Join-Path $PSScriptRoot 'legacy-java'
if(Test-Path $legacy){$sources+=Get-ChildItem $legacy -Recurse -Filter *.java | Select-Object -ExpandProperty FullName}
& (Join-Path $JdkRoot 'bin\javac.exe') --release 17 -encoding UTF-8 -cp $classpath -d $classes @sources
Check 'javac'
$classJar=Join-Path $OutputDirectory 'classes.jar'
& (Join-Path $JdkRoot 'bin\jar.exe') "-J-Djava.io.tmpdir=$OutputDirectory" --create --file $classJar -C $classes .
Check 'jar'
& (Join-Path $tools 'd8.bat') --lib $platform --classpath $api --min-api 28 --output (Join-Path $OutputDirectory 'dex') $classJar $service $interface
Check 'd8'
$resources=Join-Path $OutputDirectory 'resources.zip'
& $aapt compile --dir (Join-Path $PSScriptRoot 'res') -o $resources
Check 'resources'
$unsigned=Join-Path $OutputDirectory 'unsigned.apk'
& $aapt link -o $unsigned -I $platform --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') --min-sdk-version 28 --target-sdk-version 35 $resources
Check 'link'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip=[System.IO.Compression.ZipFile]::Open($unsigned,[System.IO.Compression.ZipArchiveMode]::Update)
try {
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $OutputDirectory 'dex\classes.dex'),'classes.dex') | Out-Null
    foreach($name in @('java_init.list','scope.list','module.prop')) {
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $PSScriptRoot "resources\META-INF\xposed\$name"),"META-INF/xposed/$name") | Out-Null
    }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $PSScriptRoot 'LICENSE-APACHE-2.0.txt'),'META-INF/licenses/libxposed-LICENSE.txt') | Out-Null
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,(Join-Path $PSScriptRoot 'THIRD_PARTY_NOTICES.md'),'META-INF/licenses/libxposed-NOTICES.txt') | Out-Null
} finally { $zip.Dispose() }
$aligned=Join-Path $OutputDirectory 'aligned.apk'
& (Join-Path $tools 'zipalign.exe') -f -p 4 $unsigned $aligned
Check 'zipalign'
if(-not (Test-Path -LiteralPath $KeyStorePath)) {
    New-Item -ItemType Directory -Force (Split-Path $KeyStorePath) | Out-Null
    & (Join-Path $JdkRoot 'bin\keytool.exe') -genkeypair -keystore $KeyStorePath -storepass android -keypass android -alias fanqie-dev -dname 'CN=Fanqie Local Development' -keyalg RSA -keysize 2048 -validity 3650
    Check 'keytool'
}
$apk=Join-Path $OutputDirectory 'fanqie-customizer-5.0.5.apk'
& (Join-Path $tools 'apksigner.bat') sign --ks $KeyStorePath --ks-key-alias fanqie-dev --ks-pass pass:android --key-pass pass:android --out $apk $aligned
Check 'sign'
& (Join-Path $tools 'apksigner.bat') verify --verbose $apk
Check 'verify'
Get-FileHash -Algorithm SHA256 $apk