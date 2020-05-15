# 前言
昨天闲来无事想试试语音识别，本来想用讯飞的SDK的，但是我用百度搜索的，百度sdk第一条，我就偷个懒用了百度的SDK，实现起来果然很懒。然后突发奇想反正是个demo，不如恶搞一下，弄成只有念出特定的话才能退出APP。本来认为重点是看百度语音识别SDK文档，结果实际上手才发现不给用户退出才是大坑。Android早几年的版本十分容易实现，然而8.0之后权限管理十分严格，各大厂商客制化的时候变得更加严格，流氓软件可太难了。下面总结一下我的思路。
# 1. 如何恶意阻止用户退出APP
## 1.1退出APP的方法
首先我们来总结一下正常状态下用户如何退出一个app
1. 按返回键。
2. 按Home键盘。
3. 下拉状态栏长安功能图标跳转到设置界面，比如Wifi开关按钮。
4. 按菜单键进入任务视图，切换到别的后台进程或者杀进程清内存。
5. 通过AI助手唤醒别的APP界面，比如小爱同学，华为小易。
6. 重启手机

## 1.2对应防止退出的方法
1. 对于按返回键的情况，这是最简单的。屏蔽back键就可以了。
```java
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
```
重写Activity的onBackPressed(),把super删掉即可。
我使用的是onKeyDown()去屏蔽back键
```java
   @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(TAG, "onBackPressed count = " + backCount);
            if (backCount>0&&backCount<3){
                textView.setText("多按几次说不定成功了呢");
            }
            if (backCount > 3) {
                button.setVisibility(View.VISIBLE);
            }
            backCount++;
            return true;
        }else if (keyCode == KeyEvent.KEYCODE_HOME){
            return true;
        }
        return false;
    }
```
注意你看到了KeyEvent中有HOME按键。我很负责任的告诉你，这玩意骗人的，低版本的时候可能对应HOME键，但是Android系统早就把这个键取消了，我之前做Android TV的时候就发现了这个问题，即使是TV遥控器上的HOME键，你按下去的时候也不是一个OnKey事件，而是一个广播。Google早就想到如果是onKey事件会被恶意拦截，导致Home键失效用户无法退出。

所以对于HOME键，我是这样处理的：
```java
 private BroadcastReceiver mHomeKeyEventReceiver = new BroadcastReceiver() {
        String SYSTEM_REASON = "reason";
        String SYSTEM_HOME_KEY = "homekey";
        String SYSTEM_HOME_KEY_LONG = "recentapps";
        public static final String ACTION_PRESENT = "android.intent.action.USER_PRESENT";

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive HOME");
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS) || action.equals(ACTION_PRESENT)) { // 监听home键
                String reason = intent.getStringExtra(SYSTEM_REASON);
                Intent intent2 = new Intent(context, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent2);
                // 表示按了home键,程序到了后台

            }
        }
    };
```
Android 8.0之后大部分系统广播都不允许静态注册，当时拿到Android O的beta版本时候报了一大堆问题出来，都是因为之前的广播失效。所以我们这里只能使用动态注册，而且动态注册的优先级比较高嘛。当我们的APP知道HOME键被触发的时候，重新打开Activity。注意要使用Intent.FLAG_ACTIVITY_NEW_TASK，否则会创建新的实例，我的Activity launchMode设置的也是singleTask。

实际测试中发现会有一些问题，如果使用全面屏手势的方法进入HOME，动画没有执行完成就会回到Activity，无法退出。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200108152651232.gif)
但是如果使用虚拟键导航，在华为EMUI10上连按3次HOME键一定会退出来，在原声Android10上则会有延时，也就是说按home键退出到手机桌面之后一两秒app才会被重新启动，有时候还不会启动。
```xml
<receiver android:name=".BootBroadcastReceiver">
            <intent-filter android:priority="1000">
                <action android:name="android.intent.action.BOOT_COMPLETED"/>
                <action android:name="android.intent.action.USER_PRESENT"/>
                <category android:name="android.intent.category.HOME"/>
            </intent-filter>
        </receiver>
```
别忘了在清单文件中注册广播接收者，把优先级设置为最高。

那么如何阻止别的app启动呢？当然并不能阻止，但是我们可以把我们的APP重新叫起来。注意这里并不是进程保活，之前我也想做双进程或者1像素UI什么的，但是现在的高版本Android早就把洞堵上了，人腾讯的app可是把所有保活手段都用上了，还有白名单，咱可没这本事。
而且既然只要保证我们的Activity在前台，可以使用暴力一点的方法。比如在OnStop()的时候重新叫起Activity。
```java
    @Override
    protected void onStop() {
        super.onStop();
        isAppVisible = false;
        Log.d(TAG, "onStop()");
        if (canFinish){

        }else {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            button.setVisibility(View.INVISIBLE);
            button2.setVisibility(View.GONE);
            startActivity(intent);
        }
    }
```
可以看到当canFinish为true的时候什么都不做，如果不为true,那么我们先清空Activity栈，然后重新startActivity。这里清栈的目的是为了防止OnStop的时候Activity发现栈中有实例不去重新创建。

然而很多时候这里不会执行到，Android会忽略在OnStop中做的这个请求，让别的app启动。

所以我们可以更加暴力点，直接开一个service去启动Activity。
```java
package com.bai.psychedelic.voicequit;

import android.app.ActivityManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RebootService extends Service {
    public static final String TAG = "RebootService";
    private Context context;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try {
                        Thread.sleep(50);
                        if (!MainActivity.isAppVisible && !MainActivity.canFinish
                        ) {
                            Intent intent = new Intent(context,MainActivity.class);
                            intent .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            Log.d(TAG,"onStartCommand startActivity");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return super.onStartCommand(intent, flags, startId);

    }



}

```
Service中死循环，50ms去获取一次当前Activity是否在显示。这里我是自己维护了2个flag来判断。其实之前Android是有提供API去判断当前栈顶的Activity是哪一个，但是这个方法被废弃了，为了保护隐私。网上也能找到替代方案，但是还是需要获取敏感权限，干脆不用了，这里用flag足够了。
这里我一开始时间间隔用的不是50ms，而是500ms，然后发现如果此时有推送通知，点击通知有概率会跳转别的app，所以干脆把时间间隔调短一些。

然后重启手机怎么办？真的某得办法。同样是广播权限问题。大家都知道Android以前设置自启动只需要添加一个权限，再去监听BOOT_COMPLETED广播就ok了。可是这玩意你咋静态注册？我在Android5.1.1上试过是可以收到的。但是高版本上首先就不允许你去静态注册，而且只有System APP级别可以收到。以前工作的时候是在一个系统级进程中去动态注册，这个进程的启动时间和framework差不多，如果等到launcher都起来了那么再去注册是来不及的，广播已经发过了。像我们这种非system app的，需要引导用户自己手动开启开机自启动权限，而且有的手机有用有的手机没有。

某得办法，写一个receiver应付一下。万一碰到低版本安装这个apk呢~
```java
public class BootBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "BootBroadcastReceiver";
    private static final String ACTION_BOOT = "android.intent.action.BOOT_COMPLETED";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(ACTION_BOOT)) { //开机启动完成后，要做的事情 
            Log.i(TAG, "BootBroadcastReceiver onReceive(), Do thing!");
            Intent intent2 = new Intent(context,MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ;
            context.startActivity(intent2);
        }
    }

}
```


好了 ，防止退出的逻辑基本上就是这些。

# 2. 语音识别
语音识别我使用的是百度语音识别SDK，因为我们只需要识别一句关键词，所以很多功能用不到，为了缩小apk体积有些架构的so可以不用，选择自己适用的就好。我这里为了方便全加上了。使用简单的功能直接使用百度提供的demo中的appid就可以了，不需要自己去百度注册开发者。按照文档添加项目依赖之后可以直接继承ActivityMiniRecog.java,实现EventListener。主要的逻辑在ActivityMiniRecog的start(),stop(),onEvent()里，基本上复制源码粘贴一下就成。在onEvent中获取识别结果。
```java
   @Override
    public void onEvent(String name, String params, byte[] data, int offset, int length) {
        Log.d(TAG, "onEvent()");
        String logTxt = "name: " + name;

        if (name.equals(SpeechConstant.CALLBACK_EVENT_ASR_PARTIAL)) {
            // 识别相关的结果都在这里
            if (params == null || params.isEmpty()) {
                return;
            }
            if (params.contains("\"nlu_result\"")) {
                // 一句话的语义解析结果
                if (length > 0 && data.length > 0) {
                    logTxt += ", 语义解析结果：" + new String(data, offset, length);
                }
            } else if (params.contains("\"partial_result\"")) {
                // 一句话的临时识别结果
                logTxt += ", 临时识别结果：" + params;
            } else if (params.contains("\"final_result\"")) {
                // 一句话的最终识别结果
                logTxt += ", 最终识别结果：" + params;
                Log.d(TAG, "结果 = " + params);
                voiceResult = params;
            } else {
                // 一般这里不会运行
                logTxt += " ;params :" + params;
                if (data != null) {
                    logTxt += " ;data length=" + data.length;
                }
            }
        } else {
            // 识别开始，结束，音量，音频数据回调
            if (params != null && !params.isEmpty()) {
                logTxt += " ;params :" + params;
            }
            if (data != null) {
                logTxt += " ;data length=" + data.length;
            }
        }
    }
```
我这里只用到了最终结果。

模仿微信的按下说话，给一个button注册onTouch事件
onKeyDown的时候开始识别，onKeyUp的时候停止识别。
```java
switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        button2.setBackgroundColor(Color.WHITE);
                        startVoice();
                        break;
                    case MotionEvent.ACTION_UP:
                        stopVoice();
                        button2.setBackgroundColor(button2.getHighlightColor());
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(600);
                                    Log.d(TAG,"voiceResult = "+voiceResult);
                                    if (voiceResult != null && !"".equals(voiceResult)
                                            && voiceResult.contains("巴拉拉小魔仙")
                                            && voiceResult.contains("尼古拉斯凯奇")
                                            && voiceResult.contains("红鲤鱼绿鲤鱼与驴")) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(mContext, "牛批！", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                        canFinish = true;
                                        voiceResult = "";
                                        finish();
                                    } else {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(mContext, "说的不对！重新来！", Toast.LENGTH_LONG).show();
                                            }
                                        });
                                        voiceResult = "";
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        });

                        break;
                    default:
                        break;
                }
```
这里要注意一下识别结果是异步的，如果你onKeyUp的时候直接去获取结果可能是空的，所以我等待了600ms再去取结果。懒得写Handler或者导入RxJava了，直接使用runOnUiThread弹Toast。

# 3. 整人
下面都是一些恶搞。
首先监听返回键，用户在尝试退出的时候按返回键没有用，按到一定次数我们显示一个Button给他。

然后我们去获取这个button的坐标。
注意，在onCreate中使用getLocationInWindow()只能拿到0，因为这个时候View还没有被加载。OnResume的时候也不保险。所以干脆延时500ms去取。
```java
        if (!hasListener) {
            executorService = Executors.newSingleThreadExecutor();
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                        int[] location = new int[2];
                        button.getLocationInWindow(location);
                        x = location[0];
                        y = location[1];
                        Log.d(TAG, "x =" + x + " y=" + y);
                        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                int curValue = (int) animation.getAnimatedValue();
                                button.layout(x, y + curValue, x + button.getWidth(), y + button.getHeight() + curValue);
                            }
                        });
                        hasListener = true;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
```
取到坐标值之后我们使用一个属性动画去改变这个button的位置。在这个button的onTouch事件中取start这个动画。那么只要用户触碰到这个button，这个button的位置就会改变。这个时候我们在TextView上可以再去嘲讽一下用户。

当用户气急败坏点击不到这个button几次之后，我们再显示另外一个button，这个button就是语音识别的button。同时TextView改变提示语。
![在这里插入图片描述](https://img-blog.csdnimg.cn/20200108160556604.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3dlaXhpbl80NDY2NjE4OA==,size_16,color_FFFFFF,t_70)
ok，下面交给百度语音识别SDK，如果获取到的关键词正确，canfinish置为true，finish()activity，onStop()放行，再把canfinish置回false。搞定~

是不是挺无聊的哈哈~因为是一时兴起代码想到哪写到到，一切按照方便来。

有兴趣的朋友可以下一个玩玩

[apk下载地址](http://www.wangjingqi.com:8080/app-release2.apk)

源码放在github上了，当个玩具就好~
[项目地址](https://github.com/Psychedelic9/VoiceQuit)
