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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class MainActivity extends AppCompatActivity implements LocationListener
{
    final String endpoint = "http://webservice.recruit.co.jp/";
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

        //TODO:ViewBindにする！
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

    private class APIClient {

        private  Retrofit retrofit = null;
        private  GourmetInterface service = null;
        public Call res = null;

        public void Init() {
            retrofit = new Retrofit.Builder().baseUrl(endpoint).build();
            service = retrofit.create(GourmetInterface.class);
            res = service.requestQuery(apiKey, latitude, longitude, 5, 4);
        }

        public void Request() {
            res.enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) {
                    if(response.isSuccessful())
                    {
                        APIResponse api = (APIResponse) response.body();

                        Log.v("success", api.toString());
                    }
                }

                @Override
                public void onFailure(Call call, Throwable t) {

                }
            });
        }

        public GourmetInterface getService() {
            return service;
        }
    }

    public interface GourmetInterface {
        //http://webservice.recruit.co.jp/hotpepper/gourmet/v1/?
        //key=[APIキー]&lat=34.67&lng=135.52&range=5&order=4
        @GET("/hotpepper/gourmet/v1/")
        Call<APIResponse> requestQuery(@Query("key") String key,
                               @Query("lat") double lat,
                               @Query("lng") double lng,
                               @Query("range") int r,
                               @Query("order") int o);
    }

    public class APIResponse {
        public Results results;

        public class Results {
            public int results_available;
            public int results_returned;
            public List<Shop> shops;
        }

        public class Shop {
            private String id;
            private String name;
            private String address;
        }
    }


}

