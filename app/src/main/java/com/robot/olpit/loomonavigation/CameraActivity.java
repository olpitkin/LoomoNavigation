package com.robot.olpit.loomonavigation;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by Alex Pitkin on 24.11.2017.
 */

public class CameraActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, DtsFragment.newInstance())
                    .commit();
        }
    }

}
