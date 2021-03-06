package com.qualcomm.ftcrobotcontroller.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorController;
import com.qualcomm.robotcore.hardware.DeviceInterfaceModule;
import com.qualcomm.robotcore.hardware.I2cDevice;
import com.qualcomm.robotcore.hardware.OpticalDistanceSensor;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.ServoController;

import org.swerverobotics.library.ClassFactory;
import org.swerverobotics.library.exceptions.UnexpectedI2CDeviceException;
import org.swerverobotics.library.interfaces.IBNO055IMU;

/* Created by team 8487 on 11/29/2015.
 */
public class ClimberDump extends OpMode {

    double[] dists = {-7656, -100, 1500, 500, 0};
    double[] turns = {0, Math.PI * 3 / 4, Math.PI, Math.PI / 2};

    float startAngle;
    double leftSpeed = 0;//variables for motor speeds
    double rightSpeed = 0;
    double target = 0;
    double integral = 0.05;
    double integralValue = 0;
    double allClearSpeed = 0;
    double allClearTarget = 0;
    double plowTarget = 1;
    double plowSpeed = 0.1; //distance per loop the plaw goes
    private OpticalDistanceSensor lightSensor;
    private DcMotorController DcDrive, DcDrive2, ArmDrive;//create a DcMotoController
    private DcMotor leftMotor, rightMotor, leftMotor2, rightMotor2, arm1, arm2;//objects for the left and right motors
    private DeviceInterfaceModule cdi;
    private ServoController servoCont;
    private Servo climberThing,climberThing2, allclear, allclear2;
    private I2cDevice gyro;
    private IBNO055IMU imu;
    private Servo plow;
    private Servo plow2;
    boolean gyroActive=true;
    float angle;
    double startBrightness;
    enum Mode {ResetEncoders, StartEncoders, Next, Moving, Turning, FindLine, End}
    Mode mode;
    int moveState = 0;
    int threshold = 10;
    double kP;
    public ClimberDump(){}
    public void init(){
        lightSensor = hardwareMap.opticalDistanceSensor.get("ODS");
        DcDrive = hardwareMap.dcMotorController.get("drive_controller");//find the motor controller on the robot
        DcDrive2 = hardwareMap.dcMotorController.get("drive_controller2");//find the motor controller on the robot
        cdi = hardwareMap.deviceInterfaceModule.get("cdi");
        ArmDrive = hardwareMap.dcMotorController.get("arm controller");
        leftMotor = hardwareMap.dcMotor.get("drive_left1");//find the motors on th robot
        rightMotor = hardwareMap.dcMotor.get("drive_right1");
        leftMotor2 = hardwareMap.dcMotor.get("drive_left2");
        rightMotor2 = hardwareMap.dcMotor.get("drive_right2");
        arm1 = hardwareMap.dcMotor.get("arm1");
        arm2 = hardwareMap.dcMotor.get("arm2");
        servoCont = hardwareMap.servoController.get("SrvCnt");
        climberThing = hardwareMap.servo.get("Srv");
        climberThing2 = hardwareMap.servo.get("Srv2");
        leftMotor.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        rightMotor2.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        mode = Mode.ResetEncoders;
        plow = hardwareMap.servo.get("Srv3");
        plow2 = hardwareMap.servo.get("Srv4");
        allclear = hardwareMap.servo.get("Srv5");
        allclear2 = hardwareMap.servo.get("Srv6");
        startBrightness = lightSensor.getLightDetected();
        climberThing.setPosition(0.8);
        climberThing2.setPosition(0);
        allclear.setPosition(1);
        allclear2.setPosition(1);
        try {
            gyro = hardwareMap.i2cDevice.get("Gyro");
            imu = ClassFactory.createAdaFruitBNO055IMU(ClimberDump.this, gyro);
        }catch (UnexpectedI2CDeviceException e){
            gyroActive=false;
        }
        if(gyroActive){
            startAngle = (float) imu.getAngularOrientation().heading;
        }else{
            startAngle = 0;
        }
        plow.setPosition(1);
        plow2.setPosition(0);
        allclear.setPosition(0);
        allclear2.setPosition(1);
    }
    public void loop(){
        if(gyroActive) {
            angle = (float) imu.getAngularOrientation().heading - startAngle;
        }else{
            angle = 0;
            telemetry.addData("","oH NoES IT fAiLED");
        }
        //float angle = (float) 3.14;
        switch(mode) {
            case ResetEncoders:
                setEncoderState(DcMotorController.RunMode.RESET_ENCODERS);
                mode = Mode.StartEncoders;
                break;
            case StartEncoders:
                if (Math.abs(leftMotor.getCurrentPosition()) < 30 && Math.abs(rightMotor2.getCurrentPosition()) < 30) {
                    setEncoderState(DcMotorController.RunMode.RUN_USING_ENCODERS);
                    mode = Mode.Next;
                } else {
                    mode = Mode.ResetEncoders;
                }
                break;
            case Next:
                if (moveState < dists.length * 2) {
                    if (moveState % 2 == 0) {
                        kP = 0.002;
                        target = dists[moveState / 2];
                        mode = Mode.Moving;
                    } else {

                        if(gyroActive) {
                            kP = 8;

                            mode = Mode.Turning;
                            integral = 0.05;
                            integralValue = 0;
                            target = (turns[(moveState - 1) / 2] + Math.PI * 2) % (Math.PI * 2);//set the target, use remainder calculation to make it positive.
                        } else {
                            mode = Mode.Next;
                        }
                    }
                    switch(moveState){
                        case 0:
                            plowTarget = 0.26;
                            break;
                        case 1:
                            plowTarget = 1;
                            mode = Mode.FindLine;
                            break;
                        case 2:
                            setAllClear(0,500);
                            break;
                        case 3:
                            setAllClear(1, 50);
                            plowTarget = 0.26;
                            break;
                        default:
                            break;
                    }
                    moveState++;
                } else {
                    mode = Mode.End;
                    leftSpeed = 0;
                    rightSpeed = 0;
                }
                break;
            case Moving:
                if (!(Math.abs(-target - leftMotor.getCurrentPosition()) < threshold && Math.abs(target - rightMotor2.getCurrentPosition()) < threshold && leftMotor.getPower() == 0 && rightMotor2.getPower() == 0)) {
                    getSpeeds();
                    telemetry.addData("oh noes-ness", Math.abs(target - leftMotor.getCurrentPosition()) + Math.abs(target - rightMotor.getCurrentPosition()));
                } else {
                    mode = Mode.ResetEncoders;
                    leftSpeed = 0;
                    rightSpeed = 0;
                }
                break;
            case Turning:
                if(Math.abs(angle - target) < Math.abs(angle - (target - Math.PI * 2))){
                    leftSpeed  = kP * (angle - target);
                    rightSpeed = kP * (angle - target);
                    integralValue += integral * (angle - target);
                    leftSpeed += integral * integralValue;
                    rightSpeed += integral * integralValue;
                }else{
                    leftSpeed  = kP * (angle - (target - Math.PI * 2));
                    rightSpeed = kP * (angle - (target - Math.PI * 2));
                    integralValue += integral * (angle - (target - Math.PI * 2));
                    leftSpeed += integral * integralValue;
                    rightSpeed += integral * integralValue;
                }
                limitValues();
                if (Math.abs(target - angle) < 0.1 || Math.abs(angle - (target - Math.PI * 2)) < 0.1){
                    mode = Mode.ResetEncoders;
                }
                break;
            case FindLine:
                leftSpeed = 0.2;
                rightSpeed = -0.2;
                if(lightSensor.getLightDetected() > startBrightness + 0.2) {
                    mode = Mode.ResetEncoders;
                }
                break;
            default:
                telemetry.addData("","yay");
                break;
        }
        runMotors();
        moveServos();
        telemetry.addData("angle", angle);
        telemetry.addData("diff", target - rightMotor2.getCurrentPosition());
        telemetry.addData("mode", mode + " " + moveState);
    }
    void moveServos(){
        if(Math.abs(allclear.getPosition() - allClearTarget) < allClearSpeed){
            allclear.setPosition(allClearTarget);
            allclear2.setPosition(1 - allClearTarget);
        }else{
            allclear.setPosition(allclear.getPosition() + allClearSpeed);
            allclear2.setPosition(allclear2.getPosition() - allClearSpeed);
        }
        if(Math.abs(plow.getPosition() - plowTarget) < plowSpeed){
            plow.setPosition(plowTarget);
            plow2.setPosition(1 - plowTarget);
        }else{
            plow.setPosition(plow.getPosition() + plowSpeed);
            plow2.setPosition(plow2.getPosition() - plowSpeed);
        }
    }

    void runMotors(){

        leftMotor.setPower(leftSpeed);
        rightMotor2.setPower(rightSpeed);
        leftMotor2.setPower(leftSpeed);
        rightMotor.setPower(rightSpeed);
    }
    void setEncoderState(DcMotorController.RunMode r){
        leftMotor.setMode(r);
        rightMotor2.setMode(r);
    }
    //calculate the motor speeds
    void getSpeeds(){
        leftSpeed = (-target - leftMotor.getCurrentPosition()) * kP;//set the proportional drivers
        rightSpeed = (target - rightMotor2.getCurrentPosition()) * kP;
        limitValues();//limit the values
    }
    void limitValues(){
        if(leftSpeed > 0.3){//limit the values to 1
            leftSpeed = 0.3;
        }else if(leftSpeed < -0.3){
            leftSpeed = -0.3;
        }
        if(rightSpeed > 0.3){
            rightSpeed= 0.3;
        }else if(rightSpeed < -0.3){
            rightSpeed = -0.3;
        }
    }
    void setAllClear(double position, int loops){
        allClearSpeed = (allclear.getPosition() - position) / loops;
        allClearTarget = position;
    }

    public void stop(){
        imu.close();
        gyro.close();
    }
}



//         ,,,,,
//        _|||||_
//       {~*~*~*~}
//     __{*~*~*~*}__
