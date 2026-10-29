/* Standalone Win32 surface diagnostic, isolated from JavaFX window ownership. */
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <dwmapi.h>
#include <jni.h>
#include <stdint.h>
static HWND window;
static HANDLE worker,ready,finished;
static int options;
static HRESULT status;
struct Policy {int state,flags,color,animation;};
struct Data {int attribute;void* data;SIZE_T size;};
typedef BOOL (WINAPI *SetComposition)(HWND,struct Data*);
static void apply(int kind){
 SetComposition fn=(SetComposition)GetProcAddress(GetModuleHandleW(L"user32.dll"),"SetWindowCompositionAttribute");
 if(fn){struct Policy p={0,0,0,0};struct Data d={19,&p,sizeof(p)};fn(window,&d);}
 int material=(kind==1||kind==4)?3:1;status=DwmSetWindowAttribute(window,38,&material,sizeof(material));
 int alpha=kind==4?1:0;DwmSetWindowAttribute(window,39,&alpha,sizeof(alpha));
 int dark=1;DwmSetWindowAttribute(window,20,&dark,sizeof(dark));
 MARGINS margins={-1,-1,-1,-1};DwmExtendFrameIntoClientArea(window,&margins);
 if(kind>=2&&kind<=3&&fn){struct Policy p={kind==2?3:4,0,0x66101010,0};struct Data d={19,&p,sizeof(p)};status=fn(window,&d)?0:E_FAIL;}
 SetWindowPos(window,HWND_TOPMOST,100,100,1000,650,SWP_SHOWWINDOW);SetForegroundWindow(window);SetActiveWindow(window);
 InvalidateRect(window,NULL,TRUE);UpdateWindow(window);
}
static LRESULT CALLBACK proc(HWND h,UINT m,WPARAM w,LPARAM l){
 if(m==WM_ERASEBKGND)return 1;
 if(m==WM_PAINT){PAINTSTRUCT p;HDC dc=BeginPaint(h,&p);if(!(options&2))FillRect(dc,&p.rcPaint,(HBRUSH)GetStockObject(BLACK_BRUSH));EndPaint(h,&p);return 0;}
 if(m==WM_APP+1){apply((int)w);SetEvent(finished);return 0;}
 if(m==WM_DESTROY){PostQuitMessage(0);return 0;}
 return DefWindowProcW(h,m,w,l);
}
static DWORD WINAPI run(LPVOID arg){
 WNDCLASSW c={0};c.lpfnWndProc=proc;c.hInstance=GetModuleHandleW(NULL);c.lpszClassName=L"OneZipPureNativeSurface";RegisterClassW(&c);
 DWORD ex=WS_EX_TOPMOST|((options&1)?0x00200000:0);
 window=CreateWindowExW(ex,c.lpszClassName,L"OneZip pure native material diagnostic",WS_OVERLAPPEDWINDOW,100,100,1000,650,NULL,NULL,c.hInstance,NULL);
 if(window){ShowWindow(window,SW_SHOW);SetForegroundWindow(window);}
 SetEvent(ready);
 if(window){MSG m;while(GetMessageW(&m,NULL,0,0)>0){TranslateMessage(&m);DispatchMessageW(&m);}}
 window=NULL;return 0;
}
JNIEXPORT jboolean JNICALL Java_NativeSurfaceProbe_start(JNIEnv* e,jclass c,jint flags){
 if(worker)return JNI_FALSE;options=flags;ready=CreateEventW(NULL,FALSE,FALSE,NULL);finished=CreateEventW(NULL,FALSE,FALSE,NULL);worker=CreateThread(NULL,0,run,NULL,0,NULL);
 return worker&&WaitForSingleObject(ready,5000)==WAIT_OBJECT_0&&window?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jint JNICALL Java_NativeSurfaceProbe_mode(JNIEnv* e,jclass c,jint kind){
 if(!window)return E_FAIL;ResetEvent(finished);PostMessageW(window,WM_APP+1,(WPARAM)kind,0);if(WaitForSingleObject(finished,5000)!=WAIT_OBJECT_0)return E_FAIL;return status;
}
JNIEXPORT jintArray JNICALL Java_NativeSurfaceProbe_info(JNIEnv* e,jclass c){
 RECT r={0};GetWindowRect(window,&r);POINT hit={r.left+450,r.top+16};jint values[8]={r.left,r.top,r.right-r.left,r.bottom-r.top,GetForegroundWindow()==window,(jint)GetWindowLongPtrW(window,GWL_EXSTYLE),IsWindowVisible(window),GetAncestor(WindowFromPoint(hit),GA_ROOT)==window};jintArray a=(*e)->NewIntArray(e,8);(*e)->SetIntArrayRegion(e,a,0,8,values);return a;
}
JNIEXPORT void JNICALL Java_NativeSurfaceProbe_stop(JNIEnv* e,jclass c){
 if(window)PostMessageW(window,WM_CLOSE,0,0);if(worker){WaitForSingleObject(worker,5000);CloseHandle(worker);worker=NULL;}if(ready){CloseHandle(ready);ready=NULL;}if(finished){CloseHandle(finished);finished=NULL;}
}
