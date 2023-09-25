package com.example.gourmetpicker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.example.gourmetpicker.databinding.ActivityDetailBinding;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DetailActivity extends AppCompatActivity {
    private ActivityDetailBinding detailBinding;
    private APIClient client;
    private APIClient.Restaurant response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        Intent intent = getIntent();

        //ViewBind設定
        detailBinding = ActivityDetailBinding.inflate(getLayoutInflater());
        View view = detailBinding.getRoot();
        setContentView(view);

        client = new APIClient(getString(R.string.api_key));
        Future future = client.idSearch(intent.getStringExtra("ID"));

        //取得が完了したら続行
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        response = client.getResponse().body().results.restaurants.get(0);

        detailBinding.tvRestaurantName.setText(response.name);
        detailBinding.tvGenre.setText(response.genre.name + "\n" + response.sub_genre.name);

        detailBinding.tvAddress.setText(getText(R.string.tvAddress) + "\n" + response.address);
        detailBinding.tvOpen.setText(getText(R.string.tvOpen) + "\n " + response.open);

        detailBinding.tvFreeDrink.setText(getText(R.string.tvFreeDrink) + "\n " + response.free_drink);
        detailBinding.tvFreeFood.setText(getText(R.string.tvFreeFood) + "\n " + response.free_food);
        detailBinding.tvPrivateRoom.setText(getText(R.string.tvPrivateRoom) + "\n " + response.private_room);
        detailBinding.tvOtherMemo.setText(getText(R.string.tvOtherMemo) + "\n " + response.other_memo);

        detailBinding.btNet.setOnClickListener(new goSiteListener());
        detailBinding.btMap.setOnClickListener(new goMapListener());

        //画像取得を別スレッドに
        ImageGetTask task = new ImageGetTask(response.photo.mobile.s);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(task);
    }

    private class goSiteListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Uri uri = Uri.parse(response.urls.pc);
            Intent intent = new Intent(Intent.ACTION_VIEW,uri);
            startActivity(intent);
        }
    }

    //地図APIキー取得出来ないから検索で代用
    private class goMapListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            String str = "https://www.google.com/search?q=" + response.address + " 地図";

            Uri uri = Uri.parse(str);
            Intent intent = new Intent(Intent.ACTION_VIEW,uri);
            startActivity(intent);
        }
    }

    // Image取得用スレッドクラス
    class ImageGetTask implements Runnable {
        private String m_Url;
        private Bitmap m_Image;

        ImageGetTask(String url) {
            this.m_Url = url;
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
                 detailBinding.ivPhoto.setImageBitmap(m_Image);

            } catch (MalformedURLException e) {
            } catch (IOException e) {
            }
        }
    }
}

