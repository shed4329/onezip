trap { $_ | Out-String | Set-Content -Encoding UTF8 target/native-research/policy-error.txt; exit 1 }
$ErrorActionPreference='Stop'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class CompositionPolicyProbe {
 [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
 [DllImport("dwmapi.dll")] public static extern int DwmIsCompositionEnabled(out bool enabled);
 [StructLayout(LayoutKind.Sequential)] public struct Power {public byte ac,battery,percent,saver;public uint life,full;}
 [DllImport("kernel32.dll")] public static extern bool GetSystemPowerStatus(out Power p);
}
'@
$settings=[Activator]::CreateInstance([Windows.UI.ViewManagement.UISettings,Windows.UI.ViewManagement,ContentType=WindowsRuntime])
[bool]$dwm=$false;$hr=[CompositionPolicyProbe]::DwmIsCompositionEnabled([ref]$dwm)
$power=New-Object CompositionPolicyProbe+Power;[void][CompositionPolicyProbe]::GetSystemPowerStatus([ref]$power)
$result=[ordered]@{advancedEffects=$settings.AdvancedEffectsEnabled;animations=$settings.AnimationsEnabled;remoteSession=[CompositionPolicyProbe]::GetSystemMetrics(0x1000);dwmHResult=$hr;dwmEnabled=$dwm;batterySaver=$power.saver;os=[Environment]::OSVersion.Version.ToString()}
try {$compositor=[Activator]::CreateInstance([Windows.UI.Composition.Compositor,Windows.UI.Composition,ContentType=WindowsRuntime]);$result.compositorCreated=($null -ne $compositor)}catch{$result.compositorCreated=$false;$result.compositorError=$_.Exception.Message}
$result | ConvertTo-Json -Compress | Set-Content -Encoding UTF8 target/native-research/policy.json
