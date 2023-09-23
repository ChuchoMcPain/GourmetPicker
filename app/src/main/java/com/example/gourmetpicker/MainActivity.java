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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class MainActivity extends AppCompatActivity implements LocationListener
{
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    private double latitude;
    private double longitude;
    private APIClient client;

    //TODO:バックグラウンド時にGPSを停止させる
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        client = new APIClient().Init();

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
                    @Override
                    public void accept(Location location) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();

                        client.execute();
                    }
                }
        );
    }

    //APIを操作するクラス
    private class APIClient {
        private ExecutorService executorService = Executors.newSingleThreadExecutor();
        private Response<GourmetResponse> response = null;
        private String apiKey;
        final String baseURL = "http://webservice.recruit.co.jp/";

        public void execute() {
            executorService.submit(new APIInner());
        }

        //API取得後のあれこれ
        public void PostExecute() {

            List<Shop> responseList = response.body().results.shop;
            List<Map<String, Object>> shops = new ArrayList<>();
            Map<String, Object> item;

            for (Shop shop: responseList) {
                item = new HashMap<>();
                item.put("Name", shop.name);
                item.put("Access", shop.mobile_access);
                shops.add(item);
            }

            ListView list = findViewById(R.id.lvShopList);
            list.setAdapter(new SimpleAdapter(
                    MainActivity.this,
                    shops,
                    R.layout.list_shop,
                    new String[] {"Name", "Access"},
                    new int[] {R.id.tvName, R.id.tvAccess}
            ));
        }

        public Response<GourmetResponse> getResponse() {
            return response;
        }

        public APIClient Init() {
            //api.xml(gitIgnore済み)からapikeyの取得
            ApplicationInfo appInfo;
            try {
                appInfo = getPackageManager().getApplicationInfo(
                        getPackageName(),
                        PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }

            apiKey = appInfo.metaData.getString("API_KEY");
            return this;
        }

        private class APIInner implements  Runnable {
            private Retrofit retrofit = null;

            @Override
            public void run() {
                retrofit = new Retrofit.Builder().baseUrl(baseURL).
                        addConverterFactory(GsonConverterFactory.create())
                        .build();

                try{
                    //https://webservice.recruit.co.jp/hotpepper/gourmet/v1/?key=&lat=34.69372333333333&lng=135.50225333333333&range=5&order=4&Type=lite&format=json
                    response = retrofit.create(GourmetService.class).
                            requestQuery(
                            apiKey,
                            latitude,
                            longitude,
                            5,
                            4,
                            "json")
                            .execute();

                    if(response.isSuccessful()){
                        Log.v("ok",response.raw().request().url().toString());
                        new Handler(Looper.getMainLooper()).post(() -> PostExecute());
                    }
                    else{

                    }
                }
                catch (IOException e){
                    e.printStackTrace();
                }
            }
        }
    }

    //API呼び出しフォーマット
    public interface GourmetService {
        @GET("hotpepper/gourmet/v1/")
        Call<GourmetResponse> requestQuery(
                @Query("key") String key,
                @Query("lat") double lat,
                @Query("lng") double lng,
                @Query("range") int range,
                @Query("order") int order,
                @Query("format") String format);
    }

    //APIから送信される情報のフォーマット形式(JSON)
    public class GourmetResponse {
        public Results results;
    }

    public class Results {
        private String api_version;
        private int results_available;
        private int results_returned;
        private int results_start;
        private List<Shop> shop;
    }

    public class Shop {
        private String id;
        private String name;
        private String logo_image;
        private String address;
        private double lat;
        private double lng;
        private Genre genre;
        private SubGenre sub_genre;
        private Budget budget;
        private String budget_memo;
        @SerializedName("catch")
        private String shopCatch;
        private String mobile_access;
        private ShopURL urls;
        private String open;
        private String other_memo;
        private String shop_detail_memo;
        private Photo photo;

        public class Genre {
            private String name;
            @SerializedName("catch")
            private String genreCatch;
        }

        public class SubGenre {
            private String name;
        }

        public class Budget {
            private String name;
            private String average;
        }

        public class ShopURL {
            private String pc;
        }

        public class Photo {
            private Photo_MB mobile;

            public class Photo_MB {
                private String l;
                private String s;
            }
        }
    }
}

