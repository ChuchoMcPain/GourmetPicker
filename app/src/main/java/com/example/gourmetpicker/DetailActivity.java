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
    private ActivityDetailBinding m_Binding;
    private APIClient m_Client;
    private APIClient.Restaurant m_Response;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        Intent intent = getIntent();

        //ViewBind設定
        m_Binding = ActivityDetailBinding.inflate(getLayoutInflater());
        View view = m_Binding.getRoot();
        setContentView(view);

        m_Client = new APIClient(getString(R.string.api_key));
        Future future = m_Client.idSearch(intent.getStringExtra("ID"));

        //取得が完了したら続行
        try {
            future.get();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        m_Response = m_Client.getResponse().body().results.restaurants.get(0);

        //サブジャンルが無い場合
        try {
            m_Binding.tvGenre.setText(m_Response.genre.name + "\n" + m_Response.sub_genre.name);
        }
        catch (Exception e){
            m_Binding.tvGenre.setText(m_Response.genre.name);
        }

        m_Binding.tvRestaurantName.setText(m_Response.name);
        m_Binding.tvAddress.setText(getText(R.string.Address) + "\n" + m_Response.address);
        m_Binding.tvOpen.setText(getText(R.string.Open) + "\n " + m_Response.open);

        m_Binding.tvFreeDrink.setText(getText(R.string.FreeDrink) + "\n " + m_Response.free_drink);
        m_Binding.tvFreeFood.setText(getText(R.string.FreeFood) + "\n " + m_Response.free_food);
        m_Binding.tvPrivateRoom.setText(getText(R.string.PrivateRoom) + "\n " + m_Response.private_room);
        m_Binding.tvOtherMemo.setText(getText(R.string.OtherMemo) + "\n " + m_Response.other_memo);

        m_Binding.btGoSite.setOnClickListener(new goSiteListener());
        m_Binding.btGoMap.setOnClickListener(new goMapListener());

        //画像取得を別スレッドに
        ImageGetTask task = new ImageGetTask(m_Response.photo.mobile.s);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(task);
    }

    private class goSiteListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            Uri uri = Uri.parse(m_Response.urls.pc);
            Intent intent = new Intent(Intent.ACTION_VIEW,uri);
            startActivity(intent);
        }
    }

    //地図APIキー取得出来ないから検索で代用
    private class goMapListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            String str = "https://www.google.com/search?q=" + m_Response.address + " 地図";

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
            m_Url = url;
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
                 m_Binding.ivPhoto.setImageBitmap(m_Image);

            } catch (MalformedURLException e) {
            } catch (IOException e) {
            }
        }
    }
}

