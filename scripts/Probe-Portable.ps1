param([Parameter(Mandatory)][string]$Application,[Parameter(Mandatory)][string]$OutputDirectory,[string]$Arguments='',[string]$JvmOptions='')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @'
using System;using System.Runtime.InteropServices;
public static class PortableWindow {
 public delegate bool Callback(IntPtr h,IntPtr p);
 [StructLayout(LayoutKind.Sequential)] public struct Rect {public int Left,Top,Right,Bottom;}
 [StructLayout(LayoutKind.Sequential)] public struct Point {public int X,Y;}
 [DllImport("user32.dll")] public static extern bool EnumWindows(Callback fn,IntPtr p);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
 [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h,out Rect r);
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h,uint m,IntPtr w,IntPtr l);
 [DllImport("user32.dll")] public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr c);
 [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h,IntPtr after,int x,int y,int w,int height,uint flags);
 [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(Point point);
 public static IntPtr Find(uint pid){IntPtr result=IntPtr.Zero;long largest=0;EnumWindows((h,p)=>{uint owner;GetWindowThreadProcessId(h,out owner);Rect r;if(owner==pid&&IsWindowVisible(h)&&GetWindowRect(h,out r)){long area=(long)(r.Right-r.Left)*(r.Bottom-r.Top);if(area>largest){largest=area;result=h;}}return true;},IntPtr.Zero);return result;}
}
'@
$Application=[IO.Path]::GetFullPath($Application);$OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$previous=$env:JAVA_TOOL_OPTIONS
$settings=Join-Path $OutputDirectory 'settings'
$env:JAVA_TOOL_OPTIONS='-Donezip.settings.dir="'+$settings+'" -Donezip.settings.legacy-dir="'+(Join-Path $OutputDirectory 'no-legacy')+'" '+$JvmOptions
$watch=[Diagnostics.Stopwatch]::StartNew();$appProcess=$null;$handle=[IntPtr]::Zero
try{
 # This is a foreground GUI acceptance run, not a background helper service.
 $startOptions=@{FilePath=$Application;PassThru=$true;WindowStyle='Normal';RedirectStandardError=(Join-Path $OutputDirectory 'stderr.txt');RedirectStandardOutput=(Join-Path $OutputDirectory 'stdout.txt')}
 if($Arguments){$startOptions.ArgumentList=$Arguments}
 $appProcess=Start-Process @startOptions
 Set-Content -LiteralPath (Join-Path $OutputDirectory 'pid.txt') -Value $appProcess.Id
 $guiProcess=$null
 while($watch.Elapsed.TotalSeconds -lt 20){
  foreach($candidate in @(Get-Process -Name ([IO.Path]::GetFileNameWithoutExtension($Application)) -ErrorAction SilentlyContinue)){
   if($candidate.Path -eq $Application -and $candidate.StartTime -ge $appProcess.StartTime.AddMilliseconds(-100)){$candidateHandle=[PortableWindow]::Find($candidate.Id);if($candidateHandle -ne [IntPtr]::Zero){$handle=$candidateHandle;$guiProcess=$candidate;break}}
  }
  if($handle -ne [IntPtr]::Zero){break};Start-Sleep -Milliseconds 50
 }
 if($handle -eq [IntPtr]::Zero){throw 'No visible application window'}
 $windowMs=$watch.Elapsed.TotalMilliseconds
 [void][PortableWindow]::SetWindowPos($handle,[IntPtr]::new(-1),0,0,0,0,0x13);Start-Sleep -Milliseconds 800
 $oldDpi=[PortableWindow]::SetThreadDpiAwarenessContext([IntPtr]::new(-4))
 try{
  $before=New-Object PortableWindow+Rect;[void][PortableWindow]::GetWindowRect($handle,[ref]$before)
  $work=[Windows.Forms.Screen]::PrimaryScreen.WorkingArea
  $captureWidth=[Math]::Min($before.Right-$before.Left,$work.Width-24)
  $captureHeight=[Math]::Min($before.Bottom-$before.Top,$work.Height-24)
  [void][PortableWindow]::SetWindowPos($handle,[IntPtr]::Zero,$work.Left+12,$work.Top+12,$captureWidth,$captureHeight,0x14)
  Start-Sleep -Milliseconds 350
  $rect=New-Object PortableWindow+Rect;[void][PortableWindow]::GetWindowRect($handle,[ref]$rect)
  $point=New-Object PortableWindow+Point;$point.X=($rect.Left+$rect.Right)/2;$point.Y=($rect.Top+$rect.Bottom)/2
  [uint32]$pixelOwner=0;[void][PortableWindow]::GetWindowThreadProcessId([PortableWindow]::WindowFromPoint($point),[ref]$pixelOwner)
  if($pixelOwner -ne $guiProcess.Id){throw 'Screenshot region is occluded; evidence rejected'}
  $bitmap=New-Object Drawing.Bitmap(($rect.Right-$rect.Left),($rect.Bottom-$rect.Top));$graphics=[Drawing.Graphics]::FromImage($bitmap)
  try{$graphics.CopyFromScreen($rect.Left,$rect.Top,0,0,$bitmap.Size);$bitmap.Save((Join-Path $OutputDirectory 'portable-home.png'))}finally{$graphics.Dispose();$bitmap.Dispose()}
 }finally{[void][PortableWindow]::SetThreadDpiAwarenessContext($oldDpi);[void][PortableWindow]::SetWindowPos($handle,[IntPtr]::new(-2),0,0,0,0,0x13)}
 Start-Sleep -Milliseconds 1800;$guiProcess.Refresh()
 $result=[ordered]@{pid=$guiProcess.Id;launcherPid=$appProcess.Id;windowReadyMs=$windowMs;workingSetBytes=$guiProcess.WorkingSet64;privateBytes=$guiProcess.PrivateMemorySize64;peakWorkingSetBytes=$guiProcess.PeakWorkingSet64;nativeLauncher=$Application;arguments=$Arguments;jvmOptions=$JvmOptions;screenshot='portable-home.png'}
 [void][PortableWindow]::PostMessage($handle,16,[IntPtr]::Zero,[IntPtr]::Zero)
 if(-not $appProcess.WaitForExit(15000)){throw 'Own test application did not close normally'}
 $result.exitCode=$appProcess.ExitCode;$result|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $OutputDirectory 'result.json') -Encoding utf8
 $result|ConvertTo-Json
}finally{
 $env:JAVA_TOOL_OPTIONS=$previous
 if($appProcess -and -not $appProcess.HasExited){if($handle -ne [IntPtr]::Zero){[void][PortableWindow]::PostMessage($handle,16,[IntPtr]::Zero,[IntPtr]::Zero)};if(-not $appProcess.WaitForExit(3000)){$appProcess.Kill()}}
 if($guiProcess -and -not $guiProcess.HasExited){if(-not $guiProcess.WaitForExit(3000)){$guiProcess.Kill()}}
}
