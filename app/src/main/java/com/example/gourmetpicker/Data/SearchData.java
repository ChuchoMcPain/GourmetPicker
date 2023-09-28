package com.example.gourmetpicker.Data;

//検索情報を保存しておくクラス
public class SearchData {
    private double m_Latitude;
    private double m_Longitude;
    private int m_PageCnt;

    public SearchData(){
        m_Latitude = 0d;
        m_Longitude = 0d;
        m_PageCnt = 0;
    }

    public void setLatitude(double latitude) { m_Latitude = latitude; }
    public void setLongitude(double longitude) { m_Longitude = longitude; }
    public void setPageCnt(int pageCnt) { m_PageCnt = pageCnt; }
    public double getLatitude() {return m_Latitude; }
    public double getLongitude() { return m_Longitude; }
    public int getPageCnt() { return m_PageCnt; }
}
