package com.naso.wxforward;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("WxForward v1.0\n\nWebhook: http://100.89.179.30:17800/wxmsg\n\n请在LSPosed中激活并勾选微信");
        tv.setPadding(40, 40, 40, 40);
        tv.setTextSize(16);
        setContentView(tv);
    }
}
