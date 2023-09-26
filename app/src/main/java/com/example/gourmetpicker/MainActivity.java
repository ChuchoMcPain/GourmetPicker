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

public class MainActivity extends AppCompatActivity implements LocationListener {
    private ActivityMainBinding m_Binding;
    private LocationManager m_locationManager;
    private LocationRequest m_locationRequest;
    private APIClient m_Client;
    private ArrayList<RestaurantItem> m_RestaurantArray;

    //TODO:バックグラウンド時にGPSを停止させる
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //権限の確認
        if (ActivityCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION )
                != PackageManager.PERMISSION_GRANTED) {

            String[] permissions = {
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION };

            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1000);

            return;
        }

        //locationManagerの初期化
        m_locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        //intervalMillis:GPS更新頻度(ミリ秒)
        //durationMillis:座標保持時間(ミリ秒)
        m_locationRequest = new LocationRequest.
                Builder(1000).
                setDurationMillis(1000).
                build();

        //minTimeMs:GPS更新頻度(ミリ秒)
        //minDistanceM:最短更新距離(メートル)
        m_locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                10000L,
                5.0f,
                (LocationListener) this
        );

        //APIキーを取得して流し込み
        m_Client = new APIClient(getString(R.string.api_key));

        //ViewBind設定
        m_Binding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = m_Binding.getRoot();
        setContentView(view);

        //リスナーに接続
        m_Binding.lvRestaurantList.setOnItemClickListener(new ListItemClickListener());


        /*
        String[] spinnerItems = {"半径300m", "半径500m", "半径1km", "半径2km", "半径5km"};
        Spinner spinner = mainBinding.spSearchRange;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                spinnerItems
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(2);*/
    }



    @Override
    public void onLocationChanged(Location loc) {
        getCurrentLocation();
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

    //店をタッチしたときに詳細画面に移る
    //IDだけ送ってあとは詳細画面側で取得させる　API複数回叩くの良くないかも？
    private class ListItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

            RestaurantItem item = (RestaurantItem) adapterView.getItemAtPosition(i);

            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("ID", item.getId());
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

        ExecutorService service = Executors.newSingleThreadExecutor();

        //現在位置取得
        //成功時の流れ
        m_locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                m_locationRequest,
                null,
                service,
                location -> {

                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();

                    //TODO:検索範囲設定
                    Future future = m_Client.gpsSearch(latitude,longitude,3);

                    //取得が完了したら続行してリストビューに流し込む
                    try {
                        future.get();
                    }
                    catch (ExecutionException e) {
                        return;
                    } catch (InterruptedException e) {
                        return;
                    }

                    //UIスレッドでリストビューの更新
                    MainActivity.this.runOnUiThread(this::onPostSearch);
                }
        );
    }

    //APIから情報取得後配列に流し込む
    //UIスレッドで呼んでね
    public void onPostSearch() {

        //検索結果ゼロの場合
        if(m_Client.getResponse().body().results.results_available == 0){
            m_Binding.tvState.setText(R.string.AvailableZero);
            return;
        }

        m_Binding.tvState.setVisibility(View.INVISIBLE);

        List<APIClient.Restaurant> responseList = m_Client.getResponse().body().results.restaurants;
        m_RestaurantArray = new ArrayList<>();
        Integer requestCnt = 0;


        for (APIClient.Restaurant response : responseList) {

            RestaurantItem inner = new RestaurantItem();
            inner.setId(response.id);
            inner.setName(response.name);
            inner.setAccess(response.mobile_access);

            //仮画像
            inner.setImage(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
            m_RestaurantArray.add(inner);

            //画像取得を別スレッドに
            ExecutorService service = Executors.newSingleThreadExecutor();

            ImageGetTask task = new ImageGetTask(response.logo_image, requestCnt);
            service.submit(task);
            requestCnt++;
        }

        ListReload();
    }

    //アダプター
    public class RestaurantAdapter extends ArrayAdapter<RestaurantItem> {
        private int m_Resource;
        private List<RestaurantItem> m_ItemList;
        private LayoutInflater m_Inflater;

        public RestaurantAdapter(Context context, int res, List<RestaurantItem> items) {
            super(context, res, items);

            m_Resource = res;
            m_ItemList = items;
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

            RestaurantItem item = m_ItemList.get(position);

            TextView title = (TextView) view.findViewById(R.id.tvName);
            title.setText(item.m_Name);

            TextView access = (TextView) view.findViewById(R.id.tvAccess);
            access.setText(item.m_Access);

            ImageView image = (ImageView) view.findViewById(R.id.ivLogo);
            image.setImageBitmap(item.m_Image);

            return view;
        }
    }

    //配列の情報を使ってリストビューの更新を行う
    private void ListReload() {

        RestaurantAdapter adapter = new RestaurantAdapter(
                MainActivity.this,
                R.layout.list_restaurant,
                m_RestaurantArray);

        ListView list = m_Binding.lvRestaurantList;
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

                //画像が取得でき次第配列を更新
                m_RestaurantArray.get(m_Number).m_Image = m_Image;

                //全部更新できたらリストビューの更新を行う
                if(m_Client.getResponse().body().results.results_returned == m_Number + 1){
                    new Handler(Looper.getMainLooper()).post(() -> ListReload());
                }

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
            m_Id = "";
            m_Name = "";
            m_Access = "";
            m_Image = null;
        }

        public void setId(String id) { m_Id = id; }
        public void setName(String name) {
            m_Name = name;
        }
        public void setAccess(String access) {
            m_Access = access;
        }
        public void setImage(Bitmap image) {
            m_Image = image;
        }
        public String getId() { return m_Id; }
        public String getName() { return m_Name; }
        public String getAccess() { return m_Access; }
        public Bitmap getImage() { return m_Image; }
    }
}

