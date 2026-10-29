param([Parameter(Mandatory=$true)][string]$Title,[Parameter(Mandatory=$true)][int]$PeerPid,[ValidateSet('Legacy','Acrylic','None','Inspect','AccentBlur','AccentAcrylic')][string]$Mode='Acrylic')
$ErrorActionPreference='Stop'
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class BackdropProbe {
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern IntPtr FindWindow(string cls,string title);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll",EntryPoint="GetWindowLongPtrW")] public static extern IntPtr GetWindowLongPtr(IntPtr h,int index);
 [StructLayout(LayoutKind.Sequential)] public struct AccentPolicy {public int state,flags,color,animation;}
 [StructLayout(LayoutKind.Sequential)] public struct CompositionData {public int attribute;public IntPtr data;public UIntPtr size;}
 [DllImport("user32.dll")] public static extern bool SetWindowCompositionAttribute(IntPtr h,ref CompositionData data);
 public static bool Accent(IntPtr h,int state) {
   var policy=new AccentPolicy {state=state,flags=2,color=0x33101010};
   var ptr=Marshal.AllocHGlobal(Marshal.SizeOf(typeof(AccentPolicy)));
   try {Marshal.StructureToPtr(policy,ptr,false);var data=new CompositionData {attribute=19,data=ptr,size=(UIntPtr)Marshal.SizeOf(typeof(AccentPolicy))};return SetWindowCompositionAttribute(h,ref data);}finally{Marshal.FreeHGlobal(ptr);}
 }
 [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr h,int attr,ref int value,int size);
 [DllImport("dwmapi.dll")] public static extern int DwmGetWindowAttribute(IntPtr h,int attr,out int value,int size);
 [StructLayout(LayoutKind.Sequential)] public struct Margins {public int left,right,top,bottom;}
 [StructLayout(LayoutKind.Sequential)] public struct Blur {public uint flags;[MarshalAs(UnmanagedType.Bool)] public bool enabled;public IntPtr region;[MarshalAs(UnmanagedType.Bool)] public bool transition;}
 [DllImport("dwmapi.dll")] public static extern int DwmExtendFrameIntoClientArea(IntPtr h,ref Margins margins);
 [DllImport("dwmapi.dll")] public static extern int DwmEnableBlurBehindWindow(IntPtr h,ref Blur blur);
}
'@
$handle=[BackdropProbe]::FindWindow([NullString]::Value,$Title)
if($handle -eq [IntPtr]::Zero){throw 'Exact probe window not found'}
[uint32]$actualPid=0
[void][BackdropProbe]::GetWindowThreadProcessId($handle,[ref]$actualPid)
if($actualPid -ne $PeerPid){throw 'Probe window owner mismatch'}
if($Mode -eq 'Inspect'){
 [void][BackdropProbe]::SetForegroundWindow($handle)
 Start-Sleep -Milliseconds 150
 [int]$value=0;$hr=[BackdropProbe]::DwmGetWindowAttribute($handle,38,[ref]$value,4)
 [uint32]$foregroundPid=0;[void][BackdropProbe]::GetWindowThreadProcessId([BackdropProbe]::GetForegroundWindow(),[ref]$foregroundPid)
 [ordered]@{mode=$Mode;os=[Environment]::OSVersion.Version.ToString();ownerPid=$actualPid;foregroundPid=$foregroundPid;getHResult=$hr;readback=$value} | ConvertTo-Json -Compress
 exit 0
}
$accent=$null
if($Mode -eq 'None'){$accent=[BackdropProbe]::Accent($handle,0)}
if($Mode -in @('AccentBlur','AccentAcrylic')){
 $state=if($Mode -eq 'AccentBlur'){3}else{4}
 $ok=[BackdropProbe]::Accent($handle,$state)
 [ordered]@{mode=$Mode;os=[Environment]::OSVersion.Version.ToString();ownerPid=$actualPid;accentResult=$ok;extendedStyle=[BackdropProbe]::GetWindowLongPtr($handle,-20).ToInt64()} | ConvertTo-Json -Compress
 exit 0
}
$margins=New-Object BackdropProbe+Margins
if($Mode -ne 'None'){$margins.left=-1;$margins.right=-1;$margins.top=-1;$margins.bottom=-1}
$frame=[BackdropProbe]::DwmExtendFrameIntoClientArea($handle,[ref]$margins)
$legacy=$null
if($Mode -eq 'Legacy'){$blur=New-Object BackdropProbe+Blur;$blur.flags=1;$blur.enabled=$true;$legacy=[BackdropProbe]::DwmEnableBlurBehindWindow($handle,[ref]$blur)}
$material=if($Mode -eq 'Acrylic'){3}else{1}
$set=[BackdropProbe]::DwmSetWindowAttribute($handle,38,[ref]$material,4)
$alpha=if($Mode -eq 'Acrylic'){1}else{0}
$alphaResult=[BackdropProbe]::DwmSetWindowAttribute($handle,39,[ref]$alpha,4)
[int]$readback=0
$get=[BackdropProbe]::DwmGetWindowAttribute($handle,38,[ref]$readback,4)
[ordered]@{mode=$Mode;os=[Environment]::OSVersion.Version.ToString();hwnd=$handle.ToInt64();ownerPid=$actualPid;frameHResult=$frame;legacyHResult=$legacy;setHResult=$set;alphaHResult=$alphaResult;getHResult=$get;readback=$readback;extendedStyle=[BackdropProbe]::GetWindowLongPtr($handle,-20).ToInt64()} | ConvertTo-Json -Compress
