// Experimental description of a standard Windows GPU effect; no Win2D dependency.
using System;
using System.Runtime.InteropServices;
using Windows.Graphics.Effects;

[ComImport, Guid("2FC57384-A068-44D7-A331-30982FCF7177"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IOneZipEffectInterop {
 void GetEffectId(out Guid id);
 void GetNamedPropertyMapping([MarshalAs(UnmanagedType.LPWStr)]string name,out uint index,out uint mapping);
 void GetPropertyCount(out uint count);
 void GetProperty(uint index,out IntPtr value);
 void GetSource(uint index,[MarshalAs(UnmanagedType.IInspectable)]out object source);
 void GetSourceCount(out uint count);
}
[ComVisible(true), ClassInterface(ClassInterfaceType.None)]
public sealed class OneZipCompositionBlur : IGraphicsEffect, IGraphicsEffectSource, IOneZipEffectInterop {
 [ComImport,Guid("629BDBC8-D932-4FF4-96B9-8D96C5C1E858"),InterfaceType(ComInterfaceType.InterfaceIsIInspectable)]
 private interface IPropertyFactory {
  void Empty(out IntPtr value);void UInt8(byte input,out IntPtr value);void Int16(short input,out IntPtr value);void UInt16(ushort input,out IntPtr value);void Int32(int input,out IntPtr value);void UInt32(uint input,out IntPtr value);void Int64(long input,out IntPtr value);void UInt64(ulong input,out IntPtr value);void Single(float input,out IntPtr value);
 }
 [DllImport("combase.dll",CharSet=CharSet.Unicode)] private static extern int WindowsCreateString(string text,int length,out IntPtr value);
 [DllImport("combase.dll")] private static extern int WindowsDeleteString(IntPtr value);
 [DllImport("combase.dll")] private static extern int RoGetActivationFactory(IntPtr name,ref Guid iid,out IntPtr factory);
 private static IPropertyFactory Properties(){
  const string name="Windows.Foundation.PropertyValue";IntPtr text;Marshal.ThrowExceptionForHR(WindowsCreateString(name,name.Length,out text));
  try {Guid iid=typeof(IPropertyFactory).GUID;IntPtr factory;Marshal.ThrowExceptionForHR(RoGetActivationFactory(text,ref iid,out factory));try{return (IPropertyFactory)Marshal.GetObjectForIUnknown(factory);}finally{Marshal.Release(factory);}}finally{WindowsDeleteString(text);}
 }
 public static void Trace(string value){var p=Environment.GetEnvironmentVariable("ONEZIP_BLUR_TRACE");if(!string.IsNullOrEmpty(p))System.IO.File.AppendAllText(p,value+Environment.NewLine);}
 public string Name {get;set;}
 public IGraphicsEffectSource Source {get;set;}
 public float Amount {get;set;}
 public OneZipCompositionBlur(){Name="SystemBlur";Amount=20;}
 public void GetEffectId(out Guid id){Trace("GetEffectId");id=new Guid("1FEB6D69-2FE6-4AC9-8C58-1D7F93E7A6A5");}
 public void GetNamedPropertyMapping(string name,out uint index,out uint mapping){Trace("Mapping "+name);mapping=0;if(name=="Amount")index=0;else if(name=="Optimization")index=1;else if(name=="Border")index=2;else throw new COMException("Unknown effect property",unchecked((int)0x80070057));}
 public void GetPropertyCount(out uint count){Trace("GetPropertyCount");count=3;}
 public void GetProperty(uint index,out IntPtr value){
  Trace("GetProperty "+index);IntPtr raw;var factory=Properties();
  if(index==0)factory.Single(Amount,out raw);else if(index==1||index==2)factory.UInt32(1,out raw);else throw new COMException("Invalid property index",unchecked((int)0x80070057));
  try{Guid iid=new Guid("4BD682DD-7554-40E9-9A9B-82654EDE7E62");Marshal.ThrowExceptionForHR(Marshal.QueryInterface(raw,ref iid,out value));}finally{Marshal.Release(raw);}
 }
 public void GetSource(uint index,out object source){Trace("GetSource "+index+" source="+(Source==null?"null":Source.GetType().FullName));if(index!=0)throw new COMException("Invalid source index",unchecked((int)0x80070057));source=Source;}
 public void GetSourceCount(out uint count){Trace("GetSourceCount");count=1;}
}
