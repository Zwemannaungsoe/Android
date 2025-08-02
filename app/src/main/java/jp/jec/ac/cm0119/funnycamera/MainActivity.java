package jp.jec.ac.cm0119.funnycamera;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // WindowInsetsの調整
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Spinnerの初期設定を行う
        var items = Arrays.stream(CameraMode.values())
                .map(CameraMode::getDisplayName)
                .collect(Collectors.toList());
        var spCameraMode = (Spinner) findViewById(R.id.sp_camera_mode);
        spCameraMode.setAdapter(
                new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items)
        );

        // startボタンクリック時の処理
        findViewById(R.id.btn_start).setOnClickListener(v -> {
            // カメラ画面にはSpinnerの選択アイテムのpositionを渡す。遷移先の画面でカメラモードの判断材料としたい。
            var selectedItemPosition = spCameraMode.getSelectedItemPosition();
            Log.d("MainActivity", "selectedItemPosition: " + selectedItemPosition);
            var starter = new Intent(this, CameraActivity.class);
            starter.putExtra("position", spCameraMode.getSelectedItemPosition());
            startActivity(starter);
        });
    }
}
