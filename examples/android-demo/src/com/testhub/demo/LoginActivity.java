package com.testhub.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** Minimal login screen used by real-device UI automation demos. */
public class LoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText user = findViewById(R.id.et_user);
        EditText password = findViewById(R.id.et_pwd);
        TextView result = findViewById(R.id.tv_result);
        Button login = findViewById(R.id.btn_login);

        login.setOnClickListener(view -> {
            String name = user.getText().toString().trim();
            String pass = password.getText().toString().trim();
            if (name.isEmpty()) {
                result.setText("请输入用户名");
            } else if ("tester".equals(name) && "secret".equals(pass)) {
                result.setText("登录成功");
            } else {
                result.setText("登录失败");
            }
        });
    }
}
