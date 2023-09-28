package com.example.gourmetpicker;

import static androidx.core.math.MathUtils.clamp;
import static java.lang.Math.ceil;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import com.example.gourmetpicker.Data.RestaurantItem;
import com.example.gourmetpicker.Data.SearchData;
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
    private SearchData m_SearchData;
    private ArrayList<RestaurantItem> m_RestaurantArray;
    private Boolean isFirstSearch;

    //検索条件設定画面から情報を受け取るための変数
    private ActivityResultLauncher<Intent> m_Settings;

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

        //検索条件設定画面から戻ってきた場合の処理の宣言
        m_Settings = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {

                    //保存して検索ボタンを押して戻ってきた場合
                    if(result.getResultCode() == RESULT_OK) {
                        Intent receive = result.getData();

                        //保存した検索条件で検索を行う
                        m_Client.setRange(receive.getIntExtra("range", 3));
                        m_Client.setSortByDistance(receive.getBooleanExtra("sortByDistance", false));

                        m_SearchData.setPageCnt(0);
                        updateCurrentLocation();
                    }
                }
        );

        //リスナーに接続
        m_Binding.lvRestaurantList.setOnItemClickListener(new ListItemClickListener());
        m_Binding.fabPageSelect.setOnClickListener(new GoSearchSettingsListener());
        m_Binding.ibNext.setOnClickListener(new PageChangeListener(1));
        m_Binding.ibPrevious.setOnClickListener(new PageChangeListener(-1));

        m_SearchData = new SearchData();
        isFirstSearch = true;
    }

    //位置情報取得権限再要請後の流れ
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
            updateCurrentLocation();
        }
    }

    //初回のみリストビューの更新を行う
    @Override
    public void onLocationChanged(Location location) {

        if(isFirstSearch){

            isFirstSearch = false;
            updateCurrentLocation();
        }
    }

    //現在位置を取得
    //取得後、検索を行う
    private void updateCurrentLocation() {

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

                    m_SearchData.setLatitude(location.getLatitude());
                    m_SearchData.setLongitude(location.getLongitude());

                    searchRestaurant();
                }
        );
    }

    //非同期処理で店舗検索を行う
    //検索に成功した場合、リストビューの更新に進む
    public void searchRestaurant(){

        Future future;

        future = m_Client.pageGPSSearch(
                m_SearchData.getLatitude(),
                m_SearchData.getLongitude(),
                m_SearchData.getPageCnt() * 10 + 1
        );

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

    //APIから情報取得後配列に流し込む
    //UIスレッドで呼んでね
    public void onPostSearch() {

        //検索結果ゼロの場合
        if(m_Client.getResponse().body().results.results_available == 0){
            m_Binding.tvState.setText(R.string.AvailableZero);
            m_Binding.tvState.setVisibility(View.VISIBLE);
            return;
        }

        m_Binding.tvState.setVisibility(View.INVISIBLE);

        //ページ番号の描画
        Double maxPage = ceil((double)m_Client.getResponse().body().results.results_available / 10d);
        m_Binding.tvPage.setText((m_SearchData.getPageCnt() + 1) + "/" +  maxPage.intValue());

        List<APIClient.Restaurant> responseList = m_Client.getResponse().body().results.restaurants;
        m_RestaurantArray = new ArrayList<>();
        Integer requestCnt = 0;

        //受け取った数繰り返す
        for (APIClient.Restaurant response : responseList) {

            RestaurantItem inner = new RestaurantItem();
            inner.setId(response.id);
            inner.setName(response.name);
            inner.setAccess(response.mobile_access);

            //仮画像を挿入
            inner.setImage(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
            m_RestaurantArray.add(inner);

            //画像取得を別スレッドに
            ExecutorService service = Executors.newSingleThreadExecutor();
            ImageGetTask task = new ImageGetTask(response.logo_image, requestCnt);
            service.submit(task);

            requestCnt++;
        }

        reloadList();
    }

    //配列の情報を使ってリストビューの更新を行う
    private void reloadList() {

        RestaurantAdapter adapter = new RestaurantAdapter(
                MainActivity.this,
                R.layout.list_restaurant,
                m_RestaurantArray);

        ListView list = m_Binding.lvRestaurantList;
        list.setAdapter(adapter);
    }

    // 画像取得用スレッドクラス
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
                m_RestaurantArray.get(m_Number).setImage(m_Image);

                //全部更新できたらリストビューの更新を行う
                if(m_Client.getResponse().body().results.results_returned == m_Number + 1){
                    new Handler(Looper.getMainLooper()).post(() -> reloadList());
                }

            } catch (MalformedURLException e) {
            } catch (IOException e) {
            }
        }
    }

    //店舗をタッチした時に詳細画面に移る
    //IDだけ送ってあとは詳細画面側で取得させる　API複数回叩くの良くないかも？
    private class ListItemClickListener implements AdapterView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

            //選択した店舗のIDを取得して店舗詳細画面に渡す
            //店舗詳細画面側でIDを使用して情報を再取得させる
            RestaurantItem item = (RestaurantItem) adapterView.getItemAtPosition(i);

            Intent intent = new Intent(MainActivity.this, DetailActivity.class);
            intent.putExtra("ID", item.getId());

            startActivity(intent);
        }
    }

    //右下の検索マークをタッチした時に検索条件設定画面に移る
    //保存して検索ボタンで戻ってきた場合のみ設定を受け取って再検索を行う
    private class GoSearchSettingsListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {

            Intent intent = new Intent(getApplication(), SearchSettingsActivity.class);
            intent.putExtra("range", m_Client.getRange() - 1);
            intent.putExtra("sortByDistance", m_Client.getSortByDistance());

            m_Settings.launch(intent);
        }
    }

    //ページ変更ボタン用リスナー
    //コントロールに接続時、渡した数値分増減させる（コンストラクタ参照）
    private class PageChangeListener implements View.OnClickListener {
        Integer m_Value;

        PageChangeListener(int value){
            m_Value = value;
        }

        @Override
        public void onClick(View view) {

            //移動先ページが限界を超え無いよう調整
            Double maxPage = ceil((double)m_Client.getResponse().body().results.results_available / 10d);

            m_SearchData.setPageCnt(
                    clamp(
                    m_SearchData.getPageCnt() + m_Value,
                    0,
                    maxPage.intValue() - 1)
            );

            searchRestaurant();
        }
    }

    //一覧表示用アダプター
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
            title.setText(item.getName());

            TextView access = (TextView) view.findViewById(R.id.tvAccess);
            access.setText(item.getAccess());

            ImageView image = (ImageView) view.findViewById(R.id.ivLogo);
            image.setImageBitmap(item.getImage());

            return view;
        }
    }

}