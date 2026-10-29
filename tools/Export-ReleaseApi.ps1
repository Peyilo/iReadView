param(
    [string]$JdkHome = $env:JAVA_HOME,
    [Parameter(Mandatory=$true)][string]$GeneratedDate
)
$ErrorActionPreference='Stop'
if ([string]::IsNullOrWhiteSpace($JdkHome)) { throw 'Supply JdkHome or set JAVA_HOME.' }
if ($GeneratedDate -notmatch '^\d{4}-\d{2}-\d{2}$') { throw 'GeneratedDate must be YYYY-MM-DD.' }
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$aar=Join-Path $root 'app/ireadview/build/outputs/aar/ireadview-release.aar'
$temporary=Join-Path $root 'app/ireadview/build/api-inventory'
$output=Join-Path $root 'app/ireadview/build/reports/api'
$executableSuffix=if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) { '.exe' } else { '' }
$javap=Join-Path $JdkHome "bin/javap$executableSuffix"
$jar=Join-Path $JdkHome "bin/jar$executableSuffix"
if (!(Test-Path -LiteralPath $aar) -or !(Test-Path -LiteralPath $javap)) { throw 'Build the Release AAR and supply a JDK home first.' }
New-Item -ItemType Directory -Path $temporary,$output -Force | Out-Null
$classesJar=Join-Path $temporary 'classes.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($aar)
try {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry('classes.jar'),$classesJar,$true)
    $reader=[IO.StreamReader]::new($archive.GetEntry('AndroidManifest.xml').Open())
    try { $manifest=$reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
$entries=@(& $jar tf $classesJar)
if ($LASTEXITCODE -ne 0) { throw 'Cannot enumerate classes.jar' }
$classEntries=@($entries | Where-Object { $_ -match '\.class$' })
$selected=@($classEntries | Where-Object {
    $_ -match '^org/peyilo/ireadview/(api|spi|widget|source)/' -and
    $_ -notmatch '(?:Kt|\$\d[^/]*)\.class$' -and $_ -notmatch '(?i)lambda'
} | ForEach-Object { $_.Replace('/','.').Substring(0,$_.Length-6) } | Sort-Object)
$signatures=(& $javap -classpath $classesJar -protected -s @selected) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect release JVM signatures' }
$blocks=[regex]::Split($signatures,'(?m)(?=^Compiled from )') | Where-Object {
    $_ -match '(?m)^public .*\b(class|interface|enum)\b'
}
$aarSha=(Get-FileHash $aar -Algorithm SHA256).Hash.ToLowerInvariant()
$jarSha=(Get-FileHash $classesJar -Algorithm SHA256).Hash.ToLowerInvariant()
$header=@"
iReadView Release JVM signature inventory
Generated: $GeneratedDate
AAR SHA-256: $aarSha
classes.jar SHA-256: $jarSha
Tool: JDK 17 javap -protected -s
Scope: public bytecode declarations under api/spi/widget/source, public/protected members; file helpers and lambda classes excluded.
Includes compiler generated/Kotlin internal JVM members; not a supported API allowlist.
Use docs/integration.md and source for nullability/defaults/thread/lifecycle contracts.

"@
Set-Content -LiteralPath (Join-Path $output 'release-jvm-api.txt') -Value ($header+($blocks -join "`n")) -Encoding utf8
$inventory=[ordered]@{
    generated=$GeneratedDate; aarSha256=$aarSha; classesJarSha256=$jarSha
    classEntryCount=$classEntries.Count; publicDeclarationCount=@($blocks).Count
    demoOrTestClassCount=@($classEntries | Where-Object { $_ -match '(?i)(/demo/|/test/|Tests?\.class$)' }).Count
    manifest=$manifest; tool='JDK 17 javap -protected -s'
}
$inventory | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $output 'release-inventory.json') -Encoding utf8
$inventory | ConvertTo-Json -Depth 4
