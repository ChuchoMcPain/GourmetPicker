package com.example.gourmetpicker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Bundle;

import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.gourmetpicker.databinding.ActivityMainBinding;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity implements LocationListener {
    private ActivityMainBinding mainBinding;
    private LocationManager locationManager;
    private LocationRequest locationRequest;
    //もっといい位置無い？
    private double latitude;
    private double longitude;
    private APIClient client;
    private ArrayList<RestaurantItem> restaurantArray;
    private ExecutorService executorService;

    //TODO:バックグラウンド時にGPSを停止させる
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //権限の確認
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {android.Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1000);

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

        client = new APIClient(getString(R.string.api_key));
        executorService = Executors.newSingleThreadExecutor();

        //ViewBind設定
        mainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = mainBinding.getRoot();
        setContentView(view);

        //更新ボタンに接続
        mainBinding.btReload.setOnClickListener(new ReloadListener());
        mainBinding.lvRestaurantList.setOnItemClickListener(new ListItemClickListener());

        getCurrentLocation();
    }

    @Override
    public void onLocationChanged(Location loc) {
    }

    //権限再要請後の流れ
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

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

    private class ListItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            RestaurantItem item = (RestaurantItem) adapterView.getItemAtPosition(i);

            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("ID", item.m_Id);
            startActivity(intent);
        }
    }

    private void getCurrentLocation() {
        //権限の確認
        if (ActivityCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION};
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1000);

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

                        //TODO:検索範囲設定
                        Future future = client.gpsSearch(latitude,longitude,5);

                        //取得が完了したら続行
                        try {
                            future.get();
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }

                        onPostSearch();
                    }
                }
        );
    }

    //API取得後リストビューを更新する
    public void onPostSearch() {
        List<APIClient.Restaurant> responseList = client.getResponse().body().results.restaurants;
        restaurantArray = new ArrayList<>();
        RestaurantItem inner;
        Integer requestCnt = 0;

        for (APIClient.Restaurant response : responseList) {
            inner = new RestaurantItem();
            inner.setId(response.id);
            inner.setName(response.name);
            inner.setAccess(response.mobile_access);
            //仮画像
            inner.setImage(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
            restaurantArray.add(inner);

            //画像取得を別スレッドに
            ImageGetTask task = new ImageGetTask(response.logo_image, requestCnt);
            executorService.submit(task);
            requestCnt++;
        }
        ListReload();
    }

    //リストビュー用アダプター
    public class RestaurantAdapter extends ArrayAdapter<RestaurantItem> {
        private int m_Resource;
        private List<RestaurantItem> m_Shops;
        private LayoutInflater m_Inflater;

        public RestaurantAdapter(Context context, int res, List<RestaurantItem> items) {
            super(context, res, items);
            m_Resource = res;
            m_Shops = items;
            m_Inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            if (convertView != null) {
                view = convertView;
            } else {
                view = m_Inflater.inflate(m_Resource, null);
            }

            RestaurantItem item = m_Shops.get(position);

            TextView title = (TextView) view.findViewById(R.id.tvName);
            title.setText(item.m_Name);

            TextView access = (TextView) view.findViewById(R.id.tvAccess);
            access.setText(item.m_Access);

            ImageView image = (ImageView) view.findViewById(R.id.ivLogo);
            image.setImageBitmap(item.m_Image);

            return view;
        }
    }

    private void ListReload() {
        RestaurantAdapter adapter = new RestaurantAdapter(MainActivity.this, R.layout.list_restaurant, restaurantArray);
        ListView list = mainBinding.lvRestaurantList;
        list.setAdapter(adapter);
    }

    // Image取得用スレッドクラス
    class ImageGetTask implements Runnable {
        private String m_Url;
        private Bitmap m_Image;
        private Integer m_Number;

        ImageGetTask(String url, Integer number) {
            this.m_Url = url;
            this.m_Number = number;
        }

        @Override
        public void run() {
            try {
                URL imageUrl = new URL(m_Url);
                InputStream imageIs;
                imageIs = imageUrl.openStream();
                m_Image = BitmapFactory.decodeStream(imageIs);
                Log.v("ok", m_Image.toString());

                //画像が取得でき次第更新
                restaurantArray.get(m_Number).m_Image = m_Image;
                new Handler(Looper.getMainLooper()).post(() -> ListReload());

            } catch (MalformedURLException e) {
            } catch (IOException e) {
            }
        }
    }

    public class RestaurantItem {
        private String m_Id;
        private String m_Name;
        private String m_Access;
        private Bitmap m_Image;

        public RestaurantItem() {
        }

        public void setId(String m_Id) {
            this.m_Id = m_Id;
        }

        public void setName(String m_Name) {
            this.m_Name = m_Name;
        }

        public void setAccess(String m_Access) {
            this.m_Access = m_Access;
        }

        public void setImage(Bitmap m_Image) {
            this.m_Image = m_Image;
        }
    }
}

