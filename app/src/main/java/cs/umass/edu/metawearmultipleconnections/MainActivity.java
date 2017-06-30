package cs.umass.edu.metawearmultipleconnections;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import com.mbientlab.metawear.Data;
import com.mbientlab.metawear.MetaWearBoard;
import com.mbientlab.metawear.Route;
import com.mbientlab.metawear.Subscriber;
import com.mbientlab.metawear.android.BtleService;
import com.mbientlab.metawear.builder.RouteBuilder;
import com.mbientlab.metawear.builder.RouteComponent;
import com.mbientlab.metawear.data.Acceleration;
import com.mbientlab.metawear.module.Accelerometer;

import java.util.HashMap;
import java.util.Map;

import bolts.Continuation;
import bolts.Task;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    private static final String[] deviceUUIDs = {"E8:0C:92:A3:3B:83", "E8:B9:62:B6:E3:4B", "F6:8D:FC:1A:E4:50", "FB:A1:90:00:61:7A"};

    private BtleService.LocalBinder serviceBinder;

    private Map<String, TextView> sensorOutputs = new HashMap<>();

    private Map<String, Accelerometer> accelerometerSensors = new HashMap<>();

    private Route streamRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getApplicationContext().bindService(new Intent(this, BtleService.class), this, BIND_AUTO_CREATE);

        ViewGroup layout = (ViewGroup) findViewById(R.id.layout_main);
        LayoutInflater inflater = getLayoutInflater();

        for (final String deviceUUID : deviceUUIDs) {

            View metawearView = inflater.inflate(R.layout.view_metawear, null);
            metawearView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            Switch metawearSwitch = (Switch) metawearView.findViewById(R.id.switchMetawear);
            TextView sensorOutput = (TextView) metawearView.findViewById(R.id.txtSensorOutput);

            sensorOutputs.put(deviceUUID, sensorOutput);

            metawearSwitch.setOnCheckedChangeListener((compoundButton, enable) -> {
                if (enable) {
                    connectToMetawear(deviceUUID);
                } else {
                    stopAccelerometer(deviceUUID);
                }
            });

            metawearSwitch.setText("Device " + deviceUUID);
            sensorOutput.setText("- -");
            layout.addView(metawearView);
        }
    }

    public static Task<Void> reconnect(final MetaWearBoard board) {
        return board.connectAsync()
                .continueWithTask(task -> {
                    if (task.isFaulted()) {
                        return reconnect(board);
                    } else if (task.isCancelled()) {
                        return task;
                    }
                    return Task.forResult(null);
                });
    }

    public void connectToMetawear(String deviceUUID){
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothDevice btDevice = btManager.getAdapter().getRemoteDevice(deviceUUID);
        MetaWearBoard mwBoard = serviceBinder.getMetaWearBoard(btDevice);

        mwBoard.connectAsync()
                .continueWithTask(task -> {
                    if (task.isCancelled()) {
                        return task;
                    }
                    return task.isFaulted() ? reconnect(mwBoard) : Task.forResult(null);
                })
                .continueWith(task -> {
                    if (!task.isCancelled()) {
                        startAccelerometer(mwBoard);
                    }
                    return null;
                });
    }

    private void startAccelerometer(MetaWearBoard mwBoard){
        Accelerometer accelerometer = accelerometerSensors.get(mwBoard.getMacAddress());
        if (accelerometer == null) {
            accelerometer = mwBoard.getModule(Accelerometer.class);
            accelerometerSensors.put(mwBoard.getMacAddress(), accelerometer);
        }

        TextView sensorOutput = sensorOutputs.get(mwBoard.getMacAddress());

        accelerometer.acceleration().addRouteAsync(source -> source.stream((data, env) -> {
            final Acceleration value = data.value(Acceleration.class);

            runOnUiThread(() -> sensorOutput.setText(value.x() + ", " + value.y() + ", " + value.z()));
        })).continueWith(task -> {
            streamRoute = task.getResult();
            accelerometerSensors.get(mwBoard.getMacAddress()).acceleration().start();
            accelerometerSensors.get(mwBoard.getMacAddress()).start();

            return null;
        });
    }

    protected void stopAccelerometer(String deviceUUID) {
        Accelerometer accelerometer = accelerometerSensors.get(deviceUUID);
        accelerometer.stop();
        accelerometer.acceleration().stop();
        if (streamRoute != null){
            streamRoute.remove();
        }
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        serviceBinder = (BtleService.LocalBinder) iBinder;
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {

    }
}
