package com.robot.olpit.loomonavigation;

import com.segway.robot.sdk.locomotion.head.Angle;
import com.segway.robot.sdk.locomotion.head.Head;
import com.segway.robot.support.control.HeadPIDController;

/**
 * Created by Alex Pitkin on 24.11.2017.
 */

public class HeadControlHandlerImpl implements HeadPIDController.HeadControlHandler {

    private Head mHead;

    public HeadControlHandlerImpl(Head head) {
        mHead = head;
    }

    @Override
    public float getJointYaw() {
        Angle angle = mHead.getYawRespectBase();
        if (angle == null) {
            return 0;
        }
        return angle.getAngle();
    }

    @Override
    public float getJointPitch() {
        Angle angle = mHead.getPitchRespectBase();
        if (angle == null) {
            return 0;
        }
        return angle.getAngle();
    }

    @Override
    public void setYawAngularVelocity(float velocity) {
        mHead.setYawAngularVelocity(velocity);
    }

    @Override
    public void setPitchAngularVelocity(float velocity) {
        mHead.setPitchAngularVelocity(velocity);
    }
}
