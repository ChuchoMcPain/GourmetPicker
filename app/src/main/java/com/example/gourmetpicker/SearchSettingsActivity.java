package com.example.gourmetpicker;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.example.gourmetpicker.databinding.ActivitySearchSettingsBinding;

public class SearchSettingsActivity extends AppCompatActivity {

    public ActivitySearchSettingsBinding m_Binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_settings);

        //ViewBinding設定
        m_Binding = ActivitySearchSettingsBinding.inflate(getLayoutInflater());
        View view = m_Binding.getRoot();
        setContentView(view);

        //* スピナー設定 *
        //検索半径
        Spinner spinner = m_Binding.spSearchRange;
        String[] spinnerItems = getResources().getStringArray(R.array.spRangeArray);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                spinnerItems
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setSelection(getIntent().getIntExtra("range",2));

        //ソート順
        spinner = m_Binding.spSortBy;
        spinnerItems = getResources().getStringArray(R.array.spBySortArray);

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                spinnerItems
        );
        spinner.setAdapter(adapter);

        spinner.setSelection(getIntent().getBooleanExtra("sortByDistance",true)? 1:0);

        //リスナーに接続
        m_Binding.btSaveSettings.setOnClickListener(new saveListener());
    }

    private class saveListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {

            //オススメ順が選択されていればtrue
            Boolean isSortByDistance = m_Binding.spSortBy.getSelectedItemPosition() == 1? true:false;

            Intent intent = new Intent();
            intent.putExtra("range", m_Binding.spSearchRange.getSelectedItemPosition() + 1);
            intent.putExtra("sortByDistance", isSortByDistance);

            //ステータスコードOKで終了
            setResult(RESULT_OK, intent);
            finish();
        }
    }
}