package com.bai.psychedelic.voicequit;

import androidx.appcompat.app.AppCompatActivity;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.baidu.aip.asrwakeup3.core.mini.ActivityMiniRecog;
import com.baidu.aip.asrwakeup3.core.mini.AutoCheck;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.asr.SpeechConstant;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends ActivityMiniRecog implements EventListener {
    public static final String TAG = "MainActivity";
    private Button button, button2;
    private TextView textView;
    private ValueAnimator valueAnimator;
    private int x = 0, y = 0;
    private Context mContext;
    private EventManager asr;
    private boolean hasListener = false;
    protected boolean enableOffline = false; // 测试离线命令词，需要改成true
    private volatile String voiceResult;
    public static volatile boolean canFinish = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        canFinish = false;
        button = findViewById(R.id.main_button);
        button2 = findViewById(R.id.main_button_press_to_talk);
        textView = findViewById(R.id.main_text_view);
        registerReceiver(mHomeKeyEventReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
        valueAnimator = ValueAnimator.ofInt(0, 500, 0);
        valueAnimator.setDuration(500);
        textView.setText("你退出试试");
        ButtonListener listener = new ButtonListener();
        button.setOnTouchListener(listener);
        asr = EventManagerFactory.create(this, "asr");
        asr.registerListener(this); //  EventListener 中 onEvent方法
        if (enableOffline) {
            loadOfflineEngine(); // 测试离线命令词请开启, 测试 ASR_OFFLINE_ENGINE_GRAMMER_FILE_PATH 参数时开启
        }
        button2.setOnTouchListener(listener);
        Intent intent3 = new Intent(this,RebootService.class);
        startService(intent3);
    }

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
    ExecutorService executorService;
    public static boolean isAppVisible = false;
    @Override
    protected void onResume() {
        super.onResume();
        isAppVisible = true;
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
    }

    private static int touchCount = 0;

    class ButtonListener implements View.OnTouchListener, View.OnClickListener {

        @Override
        public void onClick(View v) {
            textView.setText("点到了又怎么样，还是没用");
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (v.getId() == R.id.main_button) {
                if (touchCount > 8) {
                    button2.setVisibility(View.VISIBLE);
                    textView.setText("好了，按住上面按钮，大声连续念出\n“巴啦啦小魔仙 尼古拉斯凯奇 红鲤鱼绿鲤鱼与驴”\n就真的可以退出了");
                } else {
                    textView.setText("你点不着你气不气？");
                }
                valueAnimator.start();
                touchCount++;

            } else if (v.getId() == R.id.main_button_press_to_talk) {
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
            }

            return true;
        }
    }

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
    private int backCount = 0;

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mHomeKeyEventReceiver);
        // 基于SDK集成4.2 发送取消事件
        asr.send(SpeechConstant.ASR_CANCEL, "{}", null, 0, 0);
        if (enableOffline) {
            unloadOfflineEngine(); // 测试离线命令词请开启, 测试 ASR_OFFLINE_ENGINE_GRAMMER_FILE_PATH 参数时开启
        }

        // 基于SDK集成5.2 退出事件管理器
        // 必须与registerListener成对出现，否则可能造成内存泄露
        asr.unregisterListener(this);
    }

    private void startVoice() {
        Log.d(TAG, "startVoice()");
        txtLog.setText("");
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        String event = null;
        event = SpeechConstant.ASR_START; // 替换成测试的event

        if (enableOffline) {
            params.put(SpeechConstant.DECODER, 2);
        }
        // 基于SDK集成2.1 设置识别参数
        params.put(SpeechConstant.ACCEPT_AUDIO_VOLUME, false);
        // params.put(SpeechConstant.NLU, "enable");
        // params.put(SpeechConstant.VAD_ENDPOINT_TIMEOUT, 0); // 长语音

        // params.put(SpeechConstant.IN_FILE, "res:///com/baidu/android/voicedemo/16k_test.pcm");
        // params.put(SpeechConstant.VAD, SpeechConstant.VAD_DNN);
        // params.put(SpeechConstant.PID, 1537); // 中文输入法模型，有逗号

        /* 语音自训练平台特有参数 */
        // params.put(SpeechConstant.PID, 8002);
        // 语音自训练平台特殊pid，8002：搜索模型类似开放平台 1537  具体是8001还是8002，看自训练平台页面上的显示
        // params.put(SpeechConstant.LMID,1068); // 语音自训练平台已上线的模型ID，https://ai.baidu.com/smartasr/model
        // 注意模型ID必须在你的appId所在的百度账号下
        /* 语音自训练平台特有参数 */

        /* 测试InputStream*/
        // InFileStream.setContext(this);
        // params.put(SpeechConstant.IN_FILE, "#com.baidu.aip.asrwakeup3.core.inputstream.InFileStream.createMyPipedInputStream()");

        // 请先使用如‘在线识别’界面测试和生成识别参数。 params同ActivityRecog类中myRecognizer.start(params);
        // 复制此段可以自动检测错误
        (new AutoCheck(getApplicationContext(), new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == 100) {
                    AutoCheck autoCheck = (AutoCheck) msg.obj;
                    synchronized (autoCheck) {
                        String message = autoCheck.obtainErrorMessage(); // autoCheck.obtainAllMessage();
                        txtLog.append(message + "\n");
                        ; // 可以用下面一行替代，在logcat中查看代码
                        // Log.w("AutoCheckMessage", message);
                    }
                }
            }
        }, enableOffline)).checkAsr(params);
        String json = null; // 可以替换成自己的json
        json = new JSONObject(params).toString(); // 这里可以替换成你需要测试的json
        asr.send(event, json, null, 0, 0);
    }

    private void stopVoice() {
        Log.d(TAG, "stopVoice()");
        asr.send(SpeechConstant.ASR_STOP, null, null, 0, 0); //
    }

    private void loadOfflineEngine() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        params.put(SpeechConstant.DECODER, 2);
        params.put(SpeechConstant.ASR_OFFLINE_ENGINE_GRAMMER_FILE_PATH, "assets://baidu_speech_grammar.bsg");
        asr.send(SpeechConstant.ASR_KWS_LOAD_ENGINE, new JSONObject(params).toString(), null, 0, 0);
    }

    private void unloadOfflineEngine() {
        asr.send(SpeechConstant.ASR_KWS_UNLOAD_ENGINE, null, null, 0, 0); //
    }

    @Override
    protected void onPause() {
        super.onPause();
        asr.send(SpeechConstant.ASR_CANCEL, "{}", null, 0, 0);
        Log.i("ActivityMiniRecog", "On pause");
    }

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
}
