param([int]$PeerPid,[string]$DialogTitle,[string]$ImagePath,[string]$FileNameLabel="File name",[switch]$Cancel)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class PickerWindow {
 public delegate bool Callback(IntPtr h,IntPtr p);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern IntPtr FindWindow(string cls,string title);
 [DllImport("user32.dll")] public static extern IntPtr GetWindow(IntPtr h,uint cmd);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
 [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h,uint msg,IntPtr w,IntPtr l);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h,StringBuilder text,int size);
 [DllImport("user32.dll")] public static extern bool EnumWindows(Callback callback,IntPtr p);
 public static string Owned(uint pid) {var text=new StringBuilder();EnumWindows((h,p)=>{uint w,o;GetWindowThreadProcessId(h,out w);GetWindowThreadProcessId(GetWindow(h,4),out o);if(w==pid||o==pid){var title=new StringBuilder(512);GetWindowText(h,title,512);text.Append(title).Append(" [pid=").Append(w).Append(",owner=").Append(o).Append("] ; ");}return true;},IntPtr.Zero);return text.ToString();}
}
'@
$window=$null
try {
 $condition=New-Object System.Windows.Automation.AndCondition @(
  (New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ProcessIdProperty,$PeerPid)),
  (New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty,$DialogTitle)))
 for($attempt=0;$attempt -lt 30;$attempt++){
  $native=[PickerWindow]::FindWindow([NullString]::Value,$DialogTitle)
  if($native -ne [IntPtr]::Zero){
   [uint32]$direct=0;[uint32]$owner=0
   [void][PickerWindow]::GetWindowThreadProcessId($native,[ref]$direct)
   [void][PickerWindow]::GetWindowThreadProcessId([PickerWindow]::GetWindow($native,4),[ref]$owner)
   if($direct -eq $PeerPid -or $owner -eq $PeerPid){$window=[System.Windows.Automation.AutomationElement]::FromHandle($native)}
  }
  if($null -ne $window){break};Start-Sleep -Milliseconds 200
 }
 if($null -eq $window){throw ('Exact file-picker window not found: requested='+$DialogTitle+'; owned='+[PickerWindow]::Owned($PeerPid))}
 if($Cancel){
  $window.GetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern).Close()
  Write-Output 'PASS: cancelled the verified native picker'
  return
 }
 $field=$null;$pattern=$null;$inventory=@()
 for($ready=0;$ready -lt 30;$ready++){
  try {
  $native=[PickerWindow]::FindWindow([NullString]::Value,$DialogTitle)
  if($native -eq [IntPtr]::Zero){Start-Sleep -Milliseconds 200;continue}
  [uint32]$verifiedPid=0;[uint32]$verifiedOwner=0
  [void][PickerWindow]::GetWindowThreadProcessId($native,[ref]$verifiedPid)
  [void][PickerWindow]::GetWindowThreadProcessId([PickerWindow]::GetWindow($native,4),[ref]$verifiedOwner)
  if($verifiedPid -ne $PeerPid -and $verifiedOwner -ne $PeerPid){Start-Sleep -Milliseconds 200;continue}
  $window=[System.Windows.Automation.AutomationElement]::FromHandle($native)
  $edits=$window.FindAll([System.Windows.Automation.TreeScope]::Descendants,(New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty,[System.Windows.Automation.ControlType]::Edit)))
  $inventory=@()
  foreach($edit in $edits){
   $inventory+=($edit.Current.AutomationId+' / '+$edit.Current.Name)
   if(($edit.Current.Name -match $FileNameLabel) -or $edit.Current.AutomationId -eq '1001'){
    $value=$null
    if($edit.TryGetCurrentPattern([System.Windows.Automation.ValuePattern]::Pattern,[ref]$value)){$field=$edit;$pattern=$value;break}
   }
  }
  if($null -ne $field){break};Start-Sleep -Milliseconds 200
  }catch{Start-Sleep -Milliseconds 200}
 }
 if($null -eq $field){throw ('Editable file-name field not ready; edits='+($inventory -join '; '))}
 $pattern.SetValue($ImagePath)
 Start-Sleep -Milliseconds 200
 $open=$window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,(New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::AutomationIdProperty,'1')))
 if($null -eq $open){throw 'Open control 1 not found'}
 $open.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern).Invoke()
 Write-Output 'PASS: native file picker selected the explicit local image'
}catch{
 if($null -ne $window){try{$window.GetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern).Close()}catch{}}
 $close=[PickerWindow]::FindWindow([NullString]::Value,$DialogTitle)
 if($close -ne [IntPtr]::Zero){[uint32]$direct=0;[uint32]$owner=0;[void][PickerWindow]::GetWindowThreadProcessId($close,[ref]$direct);[void][PickerWindow]::GetWindowThreadProcessId([PickerWindow]::GetWindow($close,4),[ref]$owner);if($direct -eq $PeerPid -or $owner -eq $PeerPid){[void][PickerWindow]::PostMessage($close,16,[IntPtr]::Zero,[IntPtr]::Zero)}}
 throw
}
