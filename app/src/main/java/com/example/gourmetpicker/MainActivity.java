package com.example.gourmetpicker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Bundle;

import android.Manifest;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity implements LocationListener
{
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    private Location location;
    private double latitude;
    private double longitude;

    //TODO:バックグラウンド時にGPSを停止させる
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //api.xml(gitIgnore済み)からapikeyの取得
        ApplicationInfo appInfo;
        try {
            appInfo = getPackageManager().getApplicationInfo(
                    getPackageName(),
                    PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        String apiKey = appInfo.metaData.getString("api_key");


        //権限の確認
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
            ActivityCompat.requestPermissions(MainActivity.this,permissions,1000);

            return;
        }

        //locationManagerの初期化
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        //intervalMillis:GPS更新頻度(ミリ秒)
        //durationMillis:座標保持時間(ミリ秒)
        locationRequest = new LocationRequest.
                Builder(1000).
                setDurationMillis(1000).
                build();

        //minTimeMs:GPS更新頻度(ミリ秒)
        //minDistanceM:最短更新距離(メートル)
        locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                10000L,
                5.0f,
                (LocationListener) this
        );

        //現在位置取得
        getCurrentLocation();

        //更新ボタンに接続
        Button btClick = findViewById(R.id.btReload);
        ReloadListener reloadListener = new ReloadListener();
        btClick.setOnClickListener(reloadListener);
    }

    @Override
    public void onLocationChanged(Location loc) {}

    //権限再要請後の流れ
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode == 1000 && grantResults[0] == getPackageManager().PERMISSION_GRANTED) {
            if (ActivityCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                //拒否られたのでやれることなし。
                return;
            }
            //許可されたので再試行
            getCurrentLocation();
        }
    }

    private class ReloadListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            getCurrentLocation();
        }
    }

    private  void getCurrentLocation(){
        //権限の確認
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };
            ActivityCompat.requestPermissions(MainActivity.this,permissions,1000);

            return;
        }

        //現在位置取得
        locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                locationRequest,
                null,
                getApplication().getMainExecutor(),
                new Consumer<Location>() {
                    //成功時の流れ
                    //TODO:APIに接続後データ取得
                    @Override
                    public void accept(Location loc) {
                        location = loc;

                        latitude = location.getLatitude();
                        longitude = location.getLongitude();

                        TextView tvLatitude = findViewById(R.id.tvLatitudeValue);
                        tvLatitude.setText(Double.toString(latitude));

                        TextView tvLongitude = findViewById(R.id.tvLongitudeValue);
                        tvLongitude.setText(Double.toString(longitude));
                    }
                }
        );
    }
}

