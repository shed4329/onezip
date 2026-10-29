param([Parameter(Mandatory=$true)][string]$Directory,[Parameter(Mandatory=$true)][int]$ParentPid,[Parameter(Mandatory=$true)][string]$Nonce,[string]$TargetTitle='')
$ErrorActionPreference='Stop'
$Directory=[IO.Path]::GetFullPath($Directory)
$step='initialization'
trap { ($step+': '+($_ | Out-String)) | Set-Content -Encoding UTF8 ([IO.Path]::Combine($Directory,'error.txt')); exit 1 }
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Windows.Forms,System.Drawing @'
using System;
using System.Windows.Forms;
using System.Runtime.InteropServices;
public class BackdropForm : Form {
 public bool Carrier;
 protected override bool ShowWithoutActivation {get{return Carrier;}}
 protected override CreateParams CreateParams {get {var p=base.CreateParams;p.ExStyle|=0x00200000;if(Carrier)p.ExStyle|=0x080000A0;return p;}}
 protected override void OnPaintBackground(PaintEventArgs e) {}
 protected override void OnPaint(PaintEventArgs e) {}
}
public static class WinRtBackdropBridge {
 [StructLayout(LayoutKind.Sequential)] public struct QueueOptions {public int size,threadType,apartment;}
 [StructLayout(LayoutKind.Sequential)] public struct Rect {public int left,top,right,bottom;}
 [StructLayout(LayoutKind.Sequential)] public struct Point {public int x,y;}
 [DllImport("CoreMessaging.dll")] public static extern int CreateDispatcherQueueController(QueueOptions options,out IntPtr controller);
 [DllImport("user32.dll")] public static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr window,out Rect rect);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern IntPtr FindWindow(string cls,string title);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr window,out uint pid);
 [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr window);
 [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr window);
 [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr window,IntPtr after,int x,int y,int width,int height,uint flags);
 [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(Point p);
 [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr window,int flags);
 public static bool OwnTitle(IntPtr window,Rect rect){return GetAncestor(WindowFromPoint(new Point {x=rect.left+450,y=rect.top+16}),2)==window;}
 public static bool OwnFooter(IntPtr window){Rect r;GetWindowRect(window,out r);return GetAncestor(WindowFromPoint(new Point {x=r.left+20,y=r.bottom-16}),2)==window;}
 [DllImport("user32.dll",EntryPoint="GetWindowLongPtrW")] public static extern IntPtr GetWindowLongPtr(IntPtr window,int index);
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
 [DllImport("dwmapi.dll")] public static extern int DwmSetWindowAttribute(IntPtr window,int attribute,ref int value,int size);
 [StructLayout(LayoutKind.Sequential)] public struct Accent {public int state,flags,color,animation;}
 [StructLayout(LayoutKind.Sequential)] public struct AttributeData {public int attribute;public IntPtr data;public UIntPtr size;}
 [DllImport("user32.dll")] public static extern bool SetWindowCompositionAttribute(IntPtr window,ref AttributeData data);
 public static bool HostAccess(IntPtr window,int state){var policy=new Accent {state=state};var p=Marshal.AllocHGlobal(Marshal.SizeOf(typeof(Accent)));try{Marshal.StructureToPtr(policy,p,false);var d=new AttributeData {attribute=19,data=p,size=(UIntPtr)Marshal.SizeOf(typeof(Accent))};return SetWindowCompositionAttribute(window,ref d);}finally{Marshal.FreeHGlobal(p);}}
 [ComImport,Guid("29E691FA-4567-4DCA-B319-D0F207EB6807"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
 public interface ICompositorDesktopInterop {
  void CreateDesktopWindowTarget(IntPtr window,[MarshalAs(UnmanagedType.Bool)]bool topmost,[MarshalAs(UnmanagedType.IInspectable)]out object target);
  void EnsureOnThread(uint thread);
 }
 public static object Target(object compositor,IntPtr window){object target;((ICompositorDesktopInterop)compositor).CreateDesktopWindowTarget(window,true,out target);return target;}
}
'@
[void][WinRtBackdropBridge]::SetThreadDpiAwarenessContext([IntPtr](-4))
$step='DispatcherQueue'
$options=New-Object WinRtBackdropBridge+QueueOptions;$options.size=12;$options.threadType=2;$options.apartment=2
[IntPtr]$queue=[IntPtr]::Zero;$queueHr=[WinRtBackdropBridge]::CreateDispatcherQueueController($options,[ref]$queue)
if($queueHr -ne 0){throw ('DispatcherQueue HRESULT '+$queueHr)}
$step='Compositor'
$graphicsMetadata=[IO.Path]::Combine($env:SystemRoot,'System32\WinMetadata\Windows.Graphics.winmd')
$foundationMetadata=[IO.Path]::Combine($env:SystemRoot,'System32\WinMetadata\Windows.Foundation.winmd')
Add-Type -Path ([IO.Path]::Combine($PSScriptRoot,'..\target\native-research\CompositionBlurEffect.dll'))
$compositor=[Activator]::CreateInstance([Windows.UI.Composition.Compositor,Windows.UI.Composition,ContentType=WindowsRuntime])
$targetType=[Windows.UI.Composition.Desktop.DesktopWindowTarget,Windows.UI.Composition,ContentType=WindowsRuntime]
$carrier=($TargetTitle.Length -ne 0)
$targetWindow=[IntPtr]::Zero
if($carrier){$targetWindow=[WinRtBackdropBridge]::FindWindow([NullString]::Value,$TargetTitle);[uint32]$targetPid=0;[void][WinRtBackdropBridge]::GetWindowThreadProcessId($targetWindow,[ref]$targetPid);if($targetWindow -eq [IntPtr]::Zero -or $targetPid -ne $ParentPid){throw 'OneZip target identity mismatch'}}
$form=New-Object BackdropForm;$form.Carrier=$carrier;if($carrier){$form.FormBorderStyle='None';$form.ShowInTaskbar=$false};$form.Text='OneZip WinRT HostBackdrop research';$form.StartPosition='Manual';$form.Location=New-Object Drawing.Point(100,100);$form.ClientSize=New-Object Drawing.Size(900,580);$form.TopMost=$true
$hwnd=$form.Handle
[int]$on=1;$attributeHr=[WinRtBackdropBridge]::DwmSetWindowAttribute($hwnd,17,[ref]$on,4)
$hostAccess=[WinRtBackdropBridge]::HostAccess($hwnd,5)
$step='DesktopWindowTarget'
$target=[WinRtBackdropBridge]::Target($compositor,$hwnd)
$step='HostBackdropBrush'
$sprite=$compositor.CreateSpriteVisual();$size=$sprite.Size;$size.X=900;$size.Y=580;$sprite.Size=$size
$hostBrush=$compositor.CreateHostBackdropBrush()
$plainBrush=$compositor.CreateBackdropBrush()
$step='Windows Gaussian effect graph'
$source=[Windows.UI.Composition.CompositionEffectSourceParameter,Windows.UI.Composition,ContentType=WindowsRuntime]::new('Backdrop')
$description=New-Object OneZipCompositionBlur;$description.Source=$source
$blurFactory=$compositor.CreateEffectFactory($description)
$blurBrush=$blurFactory.CreateBrush();$blurBrush.SetSourceParameter('Backdrop',$plainBrush)
$clearBrush=$compositor.CreateColorBrush();$clearColor=$clearBrush.Color;$clearColor.A=0;$clearBrush.Color=$clearColor
$solidBrush=$compositor.CreateColorBrush();$color=$solidBrush.Color;$color.A=255;$color.R=24;$color.G=24;$color.B=24;$solidBrush.Color=$color
$sprite.Brush=$hostBrush;$target.Root=$sprite
$step='message loop'
$form.Show();if(-not $carrier){$form.Activate();[void][WinRtBackdropBridge]::SetForegroundWindow($hwnd)}
$timer=[Diagnostics.Stopwatch]::StartNew();$last=-1;$mode='on'
try {
 while($timer.Elapsed.TotalSeconds -lt 150 -and -not $form.IsDisposed){
  if($null -eq (Get-Process -Id $ParentPid -ErrorAction SilentlyContinue)){break}
  $commandFile=[IO.Path]::Combine($Directory,'command.json')
  if([IO.File]::Exists($commandFile)){
   try {$command=[IO.File]::ReadAllText($commandFile) | ConvertFrom-Json}catch{$command=$null}
   if($null -ne $command -and $command.nonce -eq $Nonce -and $command.seq -ne $last){
    $last=$command.seq
    if($command.mode -eq 'stop'){break}
    if($command.mode -eq 'off'){$sprite.Brush=$solidBrush;$mode='off'}
    if($command.mode -eq 'on'){$sprite.Brush=$hostBrush;$mode='on'}
    if($command.mode -eq 'clear'){$sprite.Brush=$clearBrush;$mode='clear'}
    if($command.mode -eq 'plain'){$sprite.Brush=$plainBrush;$mode='plain'}
    if($command.mode -eq 'blur'){$sprite.Brush=$blurBrush;$mode='blur'}
    if($command.mode -match '^on([035])$'){$hostAccess=[WinRtBackdropBridge]::HostAccess($hwnd,[int]$Matches[1]);$sprite.Brush=$hostBrush;$mode=$command.mode}
    if(-not $carrier){$form.Activate();[void][WinRtBackdropBridge]::SetForegroundWindow($hwnd)}
   }
  }
  [Windows.Forms.Application]::DoEvents()
  if($carrier){
   if([WinRtBackdropBridge]::FindWindow([NullString]::Value,$TargetTitle) -ne $targetWindow){break}
   if([WinRtBackdropBridge]::IsIconic($targetWindow) -or -not [WinRtBackdropBridge]::IsWindowVisible($targetWindow)){$form.Hide();Start-Sleep -Milliseconds 40;continue}
   $parentRect=New-Object WinRtBackdropBridge+Rect;[void][WinRtBackdropBridge]::GetWindowRect($targetWindow,[ref]$parentRect)
   [void][WinRtBackdropBridge]::SetWindowPos($hwnd,$targetWindow,$parentRect.left,$parentRect.top,($parentRect.right-$parentRect.left),($parentRect.bottom-$parentRect.top),0x0250)
   $size=$sprite.Size;$size.X=$parentRect.right-$parentRect.left;$size.Y=$parentRect.bottom-$parentRect.top;$sprite.Size=$size
  }
  $rect=New-Object WinRtBackdropBridge+Rect;[void][WinRtBackdropBridge]::GetWindowRect($hwnd,[ref]$rect)
  $foregroundWindow=if($carrier){$targetWindow}else{$hwnd}
  $state=[ordered]@{nonce=$Nonce;pid=$PID;hwnd=$hwnd.ToInt64();carrier=$carrier;targetWindow=$targetWindow.ToInt64();foreground=([WinRtBackdropBridge]::GetForegroundWindow() -eq $foregroundWindow);ownFooter=[WinRtBackdropBridge]::OwnFooter($foregroundWindow);ownTitle=[WinRtBackdropBridge]::OwnTitle($hwnd,$rect);x=$rect.left;y=$rect.top;width=($rect.right-$rect.left);height=($rect.bottom-$rect.top);mode=$mode;seq=$last;queueHResult=$queueHr;attributeHResult=$attributeHr;hostAccess=$hostAccess;extendedStyle=[WinRtBackdropBridge]::GetWindowLongPtr($hwnd,-20).ToInt64();targetType=$target.GetType().FullName}
  $temporary=[IO.Path]::Combine($Directory,'state.tmp');$ready=[IO.Path]::Combine($Directory,'state.json')
  [IO.File]::WriteAllText($temporary,($state | ConvertTo-Json -Compress));Move-Item -LiteralPath $temporary -Destination $ready -Force
  Start-Sleep -Milliseconds 40
 }
}finally{$target.Root=$null;[void][WinRtBackdropBridge]::HostAccess($hwnd,0);$form.Close();$form.Dispose();[Runtime.InteropServices.Marshal]::Release($queue) | Out-Null;[IO.File]::WriteAllText([IO.Path]::Combine($Directory,'closed.txt'),'Target cleared; form destroyed; process ending')}
