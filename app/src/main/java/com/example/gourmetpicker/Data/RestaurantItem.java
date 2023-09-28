package com.example.gourmetpicker.Data;

import android.graphics.Bitmap;

//店舗一覧画面のリストビュー用クラス
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
