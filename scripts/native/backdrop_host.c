/* Isolated UI research only: no archive data, no production linkage. */
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <dwmapi.h>
#include <jni.h>
#include <stdint.h>
static HWND host,target;
static LONG_PTR previousOwner;
static HANDLE thread,ready,changed;
static HRESULT lastResult;
static DWORD threadId;
static LRESULT CALLBACK proc(HWND h,UINT m,WPARAM w,LPARAM l);
struct Accent {int state,flags,color,animation;};
struct Data {int attribute;void* data;SIZE_T size;};
typedef BOOL (WINAPI *SetComposition)(HWND,struct Data*);
static void material(int mode){
    SetComposition accent=(SetComposition)GetProcAddress(GetModuleHandleW(L"user32.dll"),"SetWindowCompositionAttribute");
    if(accent){struct Accent policy={0,2,0x44101010,0};struct Data data={19,&policy,sizeof(policy)};accent(host,&data);}
    int value=(mode==1||mode==4)?3:1;
    lastResult=DwmSetWindowAttribute(host,38,&value,sizeof(value));
    int dark=1;DwmSetWindowAttribute(host,20,&dark,sizeof(dark));
    MARGINS margins=mode?(MARGINS){-1,-1,-1,-1}:(MARGINS){0,0,0,0};DwmExtendFrameIntoClientArea(host,&margins);
    if((mode==2||mode==3)&&accent){struct Accent policy={mode==2?3:4,2,0x44101010,0};struct Data data={19,&policy,sizeof(policy)};lastResult=accent(host,&data)?0:E_FAIL;}
    SendMessageW(host,WM_NCACTIVATE,TRUE,0);
    if(mode==4){SetWindowLongPtrW(host,GWL_EXSTYLE,GetWindowLongPtrW(host,GWL_EXSTYLE)&~WS_EX_NOACTIVATE);SetWindowPos(host,HWND_TOPMOST,0,0,0,0,SWP_NOMOVE|SWP_NOSIZE|SWP_SHOWWINDOW|SWP_NOOWNERZORDER);SetForegroundWindow(host);SetActiveWindow(host);}
    InvalidateRect(host,NULL,TRUE);UpdateWindow(host);
}
static void syncBounds(){
    if(!IsWindow(target)){DestroyWindow(host);return;}
    if(!IsWindowVisible(target)||IsIconic(target)){ShowWindow(host,SW_HIDE);return;}
    RECT r;GetWindowRect(target,&r);
    SetWindowPos(host,target,r.left,r.top,r.right-r.left,r.bottom-r.top,SWP_NOACTIVATE|SWP_SHOWWINDOW|SWP_NOOWNERZORDER);
}
static LRESULT CALLBACK proc(HWND h,UINT m,WPARAM w,LPARAM l){
    if(m==WM_ERASEBKGND)return 1;
    if(m==WM_PAINT){PAINTSTRUCT p;HDC dc=BeginPaint(h,&p);FillRect(dc,&p.rcPaint,(HBRUSH)GetStockObject(BLACK_BRUSH));EndPaint(h,&p);return 0;}
    if(m==WM_TIMER){syncBounds();return 0;}
    if(m==WM_APP+1){material((int)w);SetEvent(changed);return 0;}
    if(m==WM_NCACTIVATE)return DefWindowProcW(h,m,TRUE,l);
    if(m==WM_DESTROY){if(IsWindow(target))SetWindowLongPtrW(target,GWLP_HWNDPARENT,previousOwner);KillTimer(h,1);PostQuitMessage(0);return 0;}
    return DefWindowProcW(h,m,w,l);
}
static DWORD WINAPI run(LPVOID arg){
    WNDCLASSW c={0};c.lpfnWndProc=proc;c.hInstance=GetModuleHandleW(NULL);c.lpszClassName=L"OneZipNativeBackdropResearch";RegisterClassW(&c);
    host=CreateWindowExW(WS_EX_TOOLWINDOW|WS_EX_NOACTIVATE,c.lpszClassName,L"OneZip Native Material Research",WS_POPUP,0,0,1,1,NULL,NULL,c.hInstance,NULL);
    if(host){previousOwner=GetWindowLongPtrW(target,GWLP_HWNDPARENT);SetWindowLongPtrW(target,GWLP_HWNDPARENT,(LONG_PTR)host);SetTimer(host,1,25,NULL);syncBounds();}
    SetEvent(ready);
    if(host){MSG msg;while(GetMessageW(&msg,NULL,0,0)>0){TranslateMessage(&msg);DispatchMessageW(&msg);}}
    host=NULL;return 0;
}
JNIEXPORT jboolean JNICALL Java_NativeBackdropHost_start(JNIEnv* e,jclass cls,jstring title){
    if(thread)return JNI_FALSE;
    const jchar* chars=(*e)->GetStringChars(e,title,NULL);jsize n=(*e)->GetStringLength(e,title);WCHAR name[512];if(n>510){(*e)->ReleaseStringChars(e,title,chars);return JNI_FALSE;}memcpy(name,chars,n*sizeof(WCHAR));name[n]=0;(*e)->ReleaseStringChars(e,title,chars);
    target=FindWindowW(NULL,name);DWORD pid=0;GetWindowThreadProcessId(target,&pid);if(!target||pid!=GetCurrentProcessId())return JNI_FALSE;
    ready=CreateEventW(NULL,FALSE,FALSE,NULL);changed=CreateEventW(NULL,FALSE,FALSE,NULL);thread=CreateThread(NULL,0,run,NULL,0,&threadId);
    return thread&&WaitForSingleObject(ready,5000)==WAIT_OBJECT_0&&host?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT jint JNICALL Java_NativeBackdropHost_mode(JNIEnv* e,jclass cls,jint mode){
    if(!host){return (jint)E_FAIL;}
    ResetEvent(changed);PostMessageW(host,WM_APP+1,(WPARAM)mode,0);
    if(WaitForSingleObject(changed,5000)!=WAIT_OBJECT_0){return (jint)E_FAIL;}
    return (jint)lastResult;
}
JNIEXPORT jlong JNICALL Java_NativeBackdropHost_handle(JNIEnv* e,jclass cls){return (jlong)(intptr_t)host;}
JNIEXPORT void JNICALL Java_NativeBackdropHost_stop(JNIEnv* e,jclass cls){
    if(host)PostMessageW(host,WM_CLOSE,0,0);
    if(thread){WaitForSingleObject(thread,5000);CloseHandle(thread);thread=NULL;}
    if(ready){CloseHandle(ready);ready=NULL;}if(changed){CloseHandle(changed);changed=NULL;}
}

JNIEXPORT jboolean JNICALL Java_NativeBackdropHost_ownsForeground(JNIEnv* e,jclass cls){DWORD pid=0;GetWindowThreadProcessId(GetForegroundWindow(),&pid);return pid==GetCurrentProcessId()?JNI_TRUE:JNI_FALSE;}
JNIEXPORT jboolean JNICALL Java_NativeBackdropHost_hostActive(JNIEnv* e,jclass cls){return GetForegroundWindow()==host?JNI_TRUE:JNI_FALSE;}
