package com.example.gourmetpicker;

import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.os.HandlerCompat;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Bundle;

import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity implements LocationListener
{
    final String endpoint = "http://webservice.recruit.co.jp/hotpepper/gourmet/v1/?";
    private String apiKey;
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
        apiKey = appInfo.metaData.getString("API_KEY");


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

                        receiveAPIData();
                    }
                }
        );
    }

    //非同期処理でAPIを叩いてデータを受け取る
    @UiThread
    private void receiveAPIData() {
        //http://webservice.recruit.co.jp/hotpepper/gourmet/v1/?
        //http://webservice.recruit.co.jp/hotpepper/gourmet/v1/?key=[APIキー]&lat=34.67&lng=135.52&range=5&order=4
        String requestString = endpoint;
        requestString += "key=" + apiKey;
        requestString += "&lat=" + latitude + "&lng=" + longitude;
        requestString += "&range=5&order=4";

        Log.v("AccessURL",requestString);

        Looper mainLooper = Looper.getMainLooper();
        Handler handler = HandlerCompat.createAsync(mainLooper);

        APIReceiver apiReceiver = new APIReceiver(handler, requestString);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(apiReceiver);
    }

    //実際にAPIを叩くクラス
    private class APIReceiver implements Runnable {
        private final Handler handler;
        private final String requestString;

        public APIReceiver(Handler hnd, String str) {
            handler = hnd;
            requestString = str;
        }

        @WorkerThread
        @Override
        public void run() {
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            String result = "";

            try{
                URL url = new URL(requestString);
                connection = (HttpURLConnection) url.openConnection();

                connection.setConnectTimeout(3000);
                connection.setReadTimeout(5000);

                connection.connect();
                inputStream = connection.getInputStream();
                result = is2String(inputStream);
            }
            catch (MalformedURLException e) {
                Log.e("ERROR", "変換失敗");
            }
            catch (SocketException e) {
                Log.w("ERROR", "タイムアウト");
            }
            catch (IOException e) {
                Log.e("ERROR", "通信失敗");
            }

            finally {
                if(connection != null) {
                    connection.disconnect();
                }
                if(inputStream != null){
                    try{
                        inputStream.close();
                    }
                    catch (IOException e) {
                        Log.e("ERROR","解放失敗");
                    }
                }

                UIUpdater uiUpdater = new UIUpdater(result);
                handler.post(uiUpdater);
            }
        }

        private String is2String(InputStream inputStream) throws  IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            StringBuffer buffer = new StringBuffer();
            char[] b = new char[1024];
            int line;
            while (0 <= (line = reader.read(b))) {
                buffer.append(b, 0, line);
            }
            return buffer.toString();
        }
    }

    //APIから受け取ったデータを反映させるクラス
    private  class UIUpdater implements  Runnable {
        private String result;

        public UIUpdater(String res){
            result = res;
        }

        @UiThread
        @Override
        public void run() {
            //とりあえずログで出力
            Log.d("APIResponse", result);
        }
    }
}

