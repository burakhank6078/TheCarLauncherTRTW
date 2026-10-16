# Lists English UI strings used in the code (s("sr","en"), Mib.t, LoadingBanner.t) that are
# missing from mib/Tr.java. Run after every upstream merge:  powershell -ExecutionPolicy Bypass -File tools/check-translations.ps1
$root = Split-Path $PSScriptRoot -Parent
$src  = Join-Path $root 'app\src\main\java'
$tr   = [IO.File]::ReadAllText((Join-Path $src 'com\thecarlauncher\mib\Tr.java'))
$lit  = '"(?:[^"\]|\.)*"'
$cat  = "$lit(?:\s*\+\s*$lit)*"
$re   = "(?s)(?=($cat)\s*,\s*($cat))"
$dia  = '[šđčćžŠĐČĆŽ]|nije |Podeš|Dodir'
$missing = @{}
foreach ($f in Get-ChildItem $src -Recurse -Filter *.java) {
    if ($f.Name -eq 'Tr.java') { continue }
    $t = [IO.File]::ReadAllText($f.FullName)
    # (a) calls s("sr","en") / Mib.t(ctx,"sr","en") / LoadingBanner.t("sr","en")
    foreach ($m in [regex]::Matches($t, "(?s)(?:\bs|Mib\.t|LoadingBanner\.t)\(\s*(?:\w+\s*,\s*)?($cat)\s*,\s*($cat)\s*\)")) {
        $en = $m.Groups[2].Value -replace '"\s*\+\s*"', ''
        if (-not $tr.Contains("p($en,")) { $missing[$en] = $f.Name }
    }
    # (b) Serbian/English literal pairs passed to other helpers (e.g. loading(key, sr, en, cb))
    foreach ($m in [regex]::Matches($t, $re)) {
        $en = $m.Groups[2].Value -replace '"\s*\+\s*"', ''
        if ($m.Groups[1].Value -match $dia -and -not $tr.Contains("p($en,")) { $missing[$en] = $f.Name }
    }
}
if ($missing.Count -eq 0) { 'All English UI strings have a Turkish translation.'; exit 0 }
"$($missing.Count) string(s) missing from Tr.java (they show in English until added):"
$missing.GetEnumerator() | Sort-Object Value | ForEach-Object { '  {0,-28} {1}' -f $_.Value, $_.Key }
exit 1
