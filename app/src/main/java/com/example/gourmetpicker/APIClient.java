package com.example.gourmetpicker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

//APIを操作するクラス
public class APIClient {
    final String baseURL = "http://webservice.recruit.co.jp/";
    final String format = "json";
    private String apiKey;
    private Retrofit m_Retrofit;
    private Response<GourmetResponse> m_Response;
    private int m_Range;
    private Boolean isSortByDistance;

    APIClient(String key){
        apiKey = key;

        m_Retrofit = new Retrofit.Builder().baseUrl(baseURL).
                addConverterFactory(GsonConverterFactory.create())
                .build();

        //クエリ用の変数
        m_Range = 3;
        isSortByDistance = false;
    }

    //* Getter & Setter *
    public Response<GourmetResponse> getResponse() {
        return m_Response;
    }
    public int getRange() { return m_Range; }
    public Boolean getSortByDistance() { return isSortByDistance; }
    public void setRange(int range) { m_Range = range; }
    public void setSortByDistance(Boolean sortByDistance) { isSortByDistance = sortByDistance; }

    //* 別スレッドでAPIを叩く処理を行う *
    //* Futureを返すのでfuture.getで同期できる *
    //* 上のgetResponseと合わせて使用する *
    public Future pageGPSSearch(double lat, double lng, int start) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        return service.submit(new PageGPSSearchTask(lat, lng, start));
    }

    public Future idSearch(String id) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        return service.submit(new IDSearchTask(id));
    }

    //GPSでの検索を行う場合のタスククラス
    private class PageGPSSearchTask implements  Runnable {
        private double m_Lat;
        private double m_Lng;
        private Integer m_Order;
        private  int m_Start;

        PageGPSSearchTask(double lat, double lng, int start) {

            m_Lat = lat;
            m_Lng = lng;
            m_Start = start;

            //trueの場合はおすすめ順でソートする
            m_Order = isSortByDistance? 4:1;
        }

        @Override
        public void run() {

            try{
                //.../v1/?lat=34.69&lng=135.50&range=5&order=4&page=2&format=json
                m_Response = m_Retrofit.create(GourmetService.class).
                        requestGPStoPage(
                                apiKey,
                                m_Lat,
                                m_Lng,
                                m_Range,
                                m_Order,
                                m_Start,
                                format)
                        .execute();

                if(m_Response.isSuccessful()){
                    Log.v("ok", m_Response.raw().request().url().toString());
                    new Handler(Looper.getMainLooper()).post(() -> onPostExecute());
                } else{}
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }

        public void onPostExecute() {

        }
    }

    //IDでの検索を行う場合のタスククラス
    private class IDSearchTask implements  Runnable {
        private String m_Id;

        IDSearchTask(String id) {
            m_Id = id;
        }

        @Override
        public void run() {

            try{
                //.../v1/?id=J000981198&format=json
                m_Response = m_Retrofit.create(GourmetService.class).
                        requestID(
                                apiKey,
                                m_Id,
                                format)
                        .execute();

                if(m_Response.isSuccessful()){
                    Log.v("ok", m_Response.raw().request().url().toString());
                    new Handler(Looper.getMainLooper()).post(() -> onPostExecute());
                } else{}
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }

        public void onPostExecute() {

        }
    }

    //API呼び出しフォーマット
    public interface GourmetService {
        final String endpoint = "hotpepper/gourmet/v1/";

        //位置情報を使用した検索
        //店舗一覧画面用
        @GET(endpoint)
        Call<GourmetResponse> requestGPStoPage(
                @Query("key") String key,
                @Query("lat") double lat,
                @Query("lng") double lng,
                @Query("range") int range,
                @Query("order") int order,
                @Query("start") int start,
                @Query("format") String format);

        //ID直入力
        //店舗詳細画面用
        @GET(endpoint)
        Call<GourmetResponse> requestID(
                @Query("key") String key,
                @Query("id") String id,
                @Query("format") String format);
    }

    //APIから送信される情報のフォーマット群
    //下記のリファレンス参照
    //https://webservice.recruit.co.jp/doc/hotpepper/reference.html
    public class GourmetResponse {
        Results results;
    }

    public class Results {
        String api_version;
        int results_available;
        int results_returned;
        int results_start;
        @SerializedName("shop")
        List<Restaurant> restaurants;
    }

    public class Restaurant {
        String id;
        String name;
        String logo_image;
        String address;
        double lat;
        double lng;
        Genre genre;
        SubGenre sub_genre;
        Budget budget;
        String budget_memo;
        @SerializedName("catch")
        String restaurantCatch;
        String mobile_access;
        RestaurantURL urls;
        String open;
        String free_drink;
        String free_food;
        String private_room;
        String other_memo;
        @SerializedName("shop_detail_memo")
        String detail_memo;
        Photo photo;
    }

    public class Genre {
        String name;
        @SerializedName("catch")
        String genreCatch;
    }

    public class SubGenre {
        String name;
    }

    public class Budget {
        String name;
        String average;
    }

    public class RestaurantURL {
        String pc;
    }

    public class Photo {
        Photo.Photo_MB mobile;

        public class Photo_MB {
            String l;
            String s;
        }
    }
}
