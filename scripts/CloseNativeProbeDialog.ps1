param([Parameter(Mandatory=$true)][long]$OwnerHwnd)
$ErrorActionPreference='Stop'
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class ProbeDialogCloser {
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern IntPtr GetWindow(IntPtr window,uint kind);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr window,out uint pid);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr window,StringBuilder text,int length);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr window,StringBuilder text,int length);
 [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr window,uint message,IntPtr w,IntPtr l);
}
'@
$owner=[IntPtr]$OwnerHwnd;$title=New-Object Text.StringBuilder(256);[void][ProbeDialogCloser]::GetWindowText($owner,$title,256)
[uint32]$ownerPid=0;[void][ProbeDialogCloser]::GetWindowThreadProcessId($owner,[ref]$ownerPid)
if($title.ToString() -ne ('OneZip Native Composition Lab '+$ownerPid)){throw 'Not an exact native research owner'}
$dialog=[ProbeDialogCloser]::GetForegroundWindow();[uint32]$dialogPid=0;[void][ProbeDialogCloser]::GetWindowThreadProcessId($dialog,[ref]$dialogPid)
$class=New-Object Text.StringBuilder(128);[void][ProbeDialogCloser]::GetClassName($dialog,$class,128)
if($dialogPid -ne $ownerPid -or $class.ToString() -ne '#32770'){throw 'Foreground is not the probe process file dialog'}
$parent=$dialog;$owned=$false;for($i=0;$i -lt 6;$i++){$parent=[ProbeDialogCloser]::GetWindow($parent,4);if($parent -eq $owner){$owned=$true;break}}
if(-not $owned){throw 'Dialog owner chain differs'}
[void][ProbeDialogCloser]::PostMessage($dialog,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)
Write-Output ('Closed only native probe dialog; PID '+$ownerPid)
