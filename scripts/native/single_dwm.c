#define UNICODE
#define _UNICODE
#include <windows.h>
#include <dwmapi.h>
#include <jni.h>
static HWND owned(JNIEnv* e,jstring text){const jchar* v=(*e)->GetStringChars(e,text,0);int n=(*e)->GetStringLength(e,text);WCHAR name[256];if(n>254){(*e)->ReleaseStringChars(e,text,v);return NULL;}memcpy(name,v,n*2);name[n]=0;(*e)->ReleaseStringChars(e,text,v);HWND h=FindWindowW(NULL,name);DWORD pid=0;GetWindowThreadProcessId(h,&pid);return pid==GetCurrentProcessId()?h:NULL;}
JNIEXPORT jint JNICALL Java_SingleDwmProbe_set(JNIEnv* e,jclass c,jstring title,jint material,jboolean dark){HWND h=owned(e,title);if(!h)return E_HANDLE;int d=dark?1:0;DwmSetWindowAttribute(h,20,&d,sizeof(d));return DwmSetWindowAttribute(h,38,&material,sizeof(material));}
JNIEXPORT jlongArray JNICALL Java_SingleDwmProbe_inspect(JNIEnv* e,jclass c,jstring title){HWND h=owned(e,title);int value=-1,alpha=-1;HRESULT hr=DwmGetWindowAttribute(h,38,&value,sizeof(value));HRESULT ar=DwmGetWindowAttribute(h,39,&alpha,sizeof(alpha));RECT r={0};GetWindowRect(h,&r);POINT p={(r.left+r.right)/2,r.top+16};jlong v[8]={(jlong)(intptr_t)h,(jlong)(intptr_t)GetForegroundWindow(),(jlong)GetWindowLongPtrW(h,GWL_EXSTYLE),value,hr,alpha,ar,GetAncestor(WindowFromPoint(p),GA_ROOT)==h};jlongArray a=(*e)->NewLongArray(e,8);(*e)->SetLongArrayRegion(e,a,0,8,v);return a;}

JNIEXPORT jint JNICALL Java_SingleDwmProbe_flags(JNIEnv* e,jclass c,jstring title,jint flags){HWND h=owned(e,title);if(!h)return E_HANDLE;HRESULT hr=S_OK;if(flags&1){int enabled=1;hr=DwmSetWindowAttribute(h,39,&enabled,sizeof(enabled));}if(flags&2){MARGINS margins={-1,-1,-1,-1};hr=DwmExtendFrameIntoClientArea(h,&margins);}return hr;}
