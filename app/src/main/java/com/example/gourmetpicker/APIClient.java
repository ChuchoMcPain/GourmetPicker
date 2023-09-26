package com.example.gourmetpicker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.annotations.SerializedName;

import java.io.IOException;
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
    private Retrofit m_retrofit;
    private Response<GourmetResponse> m_response;

    APIClient(String key){
        apiKey = key;

        m_retrofit = new Retrofit.Builder().baseUrl(baseURL).
                addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public Response<GourmetResponse> getResponse() {
        return m_response;
    }

    public Future gpsSearch(double lat, double lng, int range) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        return service.submit(new GPSSearchTask(lat, lng, range));
    }

    public Future idSearch(String id) {
        ExecutorService service = Executors.newSingleThreadExecutor();
        return service.submit(new IDSearchTask(id));
    }

    //GPSでの検索を行う場合のタスククラス
    private class GPSSearchTask implements  Runnable {
        private double m_Lat;
        private double m_Lng;
        private int m_Range;

        GPSSearchTask(double lat, double lng, int range) {
            m_Lat = lat;
            m_Lng = lng;
            m_Range = range;
        }

        @Override
        public void run() {
            try{
                //https://webservice.recruit.co.jp/hotpepper/gourmet/v1/?key=&lat=34.69372333333333&lng=135.50225333333333&range=5&order=4&format=json
                m_response = m_retrofit.create(GourmetService.class).
                        requestGPS(
                                apiKey,
                                m_Lat,
                                m_Lng,
                                m_Range,
                                4,
                                format)
                        .execute();

                if(m_response.isSuccessful()){
                    Log.v("ok", m_response.raw().request().url().toString());
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
                //https://webservice.recruit.co.jp/hotpepper/gourmet/v1/?key=&lat=34.69372333333333&lng=135.50225333333333&range=5&order=4&format=json
                m_response = m_retrofit.create(GourmetService.class).
                        requestID(
                                apiKey,
                                m_Id,
                                format)
                        .execute();

                if(m_response.isSuccessful()){
                    Log.v("ok", m_response.raw().request().url().toString());
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
        @GET(endpoint)
        Call<GourmetResponse> requestGPS(
                @Query("key") String key,
                @Query("lat") double lat,
                @Query("lng") double lng,
                @Query("range") int range,
                @Query("order") int order,
                @Query("format") String format);

        //ID直入力
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
