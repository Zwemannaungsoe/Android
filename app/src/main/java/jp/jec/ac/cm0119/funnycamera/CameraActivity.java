package jp.jec.ac.cm0119.funnycamera;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.camera2.CameraDevice;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat.Type;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = CameraActivity.class.getSimpleName(); // ログ出力時のタグ

    /**
     * OS version 10以降のAndroidデバイスでCameraと共有ストレージへの書き込みで必要な権限リクエスト
     */
    public static final String[] REQUIRED_PERMISSIONS_API29 = new String[]{android.Manifest.permission.CAMERA};
    /**
     * 権限リクエストの結果を受け取るためのActivityResultLauncher
     */
    private final ActivityResultLauncher<String[]> requestPermissions = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> grantStates) -> {
        var isPermissionAllGranted = grantStates.entrySet().stream().allMatch(Map.Entry::getValue);
        Log.d(TAG, "isPermissionAllGranted: " + isPermissionAllGranted);
        if (isPermissionAllGranted) {
            // Camera機能を使うための権限が付与されている場合のみCameraのプレビュー表示を行う
            startCamera();
        } else {
            Log.w(TAG, "全ての権限を許可しないとアプリが正常に動作しません");
            Snackbar.make(findViewById(R.id.main), "全ての権限を許可しないとアプリが正常に動作しません", Snackbar.LENGTH_LONG).show();
        }
    });

    @Nullable
    private ImageCapture imageCapture;

    private int screenX;
    private int screenY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Camera機能を使うために権限リクエストを行う
        requestPermissions.launch(REQUIRED_PERMISSIONS_API29);

        var position = getIntent().getIntExtra("position", 0);
        var cameraMode = CameraMode.values()[position];
        Log.d(TAG, cameraMode.toString());

        var image = (ImageView) findViewById(R.id.img);
        var txtSerifu = (TextView) findViewById(R.id.txt_serifu);

        // カメラモードに応じた処理を行う
        switch (cameraMode) {
            case NORMAL -> {
                // ノーマルモード時の制御をここで行う
                txtSerifu.setVisibility(View.GONE);
            }
            case CHARACTER -> {
                // キャラクターモード時の制御をここで行う
                image.setImageResource(cameraMode.getDrawableResId());
                txtSerifu.setVisibility(View.GONE);
            }
            case SERIFU -> {
                // セリフモード時の制御をここで行う
                image.setImageResource(cameraMode.getDrawableResId());
                txtSerifu.setVisibility(View.VISIBLE);
            }
        }

        findViewById(R.id.frame_layout).setOnTouchListener((v, event) -> {
            var x = (int) event.getRawX();
            var y = (int) event.getRawY();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    screenX = x;
                    screenY = y;
                }
                case MotionEvent.ACTION_MOVE -> { // Viewの座標更新を行う
                    var dx = v.getLeft() + (x - screenX);
                    var dy = v.getTop() + (y - screenY);
                    v.layout(dx, dy, dx + v.getWidth(), dy + v.getHeight());
                    screenX = x;
                    screenY = y;
                }
                case MotionEvent.ACTION_UP -> v.performClick(); // （アクセシビリティ対応）
            }
            return true;
        });

        findViewById(R.id.btn_capture).setOnClickListener(v -> {
            v.setVisibility(View.INVISIBLE); // captureボタンを非表示にする
            takePhoto();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        var rootView = findViewById(R.id.main); // レイアウト内のトップのViewを指定する
        rootView.postDelayed(() -> hideSystemUI(rootView), 500); // 500msは適当な遅延時間}
    }

    /**
     * Cameraのプレビュー表示を行う
     */
    private void startCamera() {
        // 現在のプロセスに関連付けられているProcessCameraProviderを取得
        var cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        // イベントリスナーの登録
        cameraProviderFuture.addListener(() -> {
            try {
                var cameraProvider = cameraProviderFuture.get();

                final PreviewView viewFinder = findViewById(R.id.view_finder);
                var preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();

                // フロントカメラかバックカメラを指定する
                var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // 再バインドする前にユースケースのバインドを解除する
                cameraProvider.unbindAll();

                // ユースケース(今回の場合、画像キャプチャのみ)をカメラにバインドする
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "error: ", e);
                Snackbar.make(findViewById(R.id.main), "Cameraの起動に失敗しました", Snackbar.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 写真撮影と撮影データの保存を行う
     */
    private void takePhoto() {
        if(imageCapture == null) return;
        var dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd.HH.mm.ss.SSS");
        var fileName = LocalDateTime.now().format(dateTimeFormatter);
        var contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
        contentValues.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FunnyCamera");
        var imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        var outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                imageCollection,
                contentValues
        ).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                var savedUri = output.getSavedUri();
                Log.d(TAG, "savedUri" + savedUri);
                if (savedUri == null) return;

                var screenshot =screenShot();
                if (screenshot == null)return;

                try(var inputStream = getContentResolver().openInputStream(savedUri);
                    var outputStream = getContentResolver().openOutputStream(savedUri)){
                    var photo = BitmapFactory.decodeStream(inputStream);
                    var result = combineBitmap(photo, screenshot);
                    Log.d(TAG,"outputStream" + outputStream);
                    if (outputStream != null){
                        result.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    }
                }catch (IOException e){
                    Log.e(TAG, "error", e);
                    Snackbar.make(findViewById(R.id.main),"保存に失敗しました",Snackbar.LENGTH_SHORT).show();
                }

            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "error",exception);
                findViewById(R.id.btn_capture).setVisibility(View.VISIBLE);

            }
        });


    }

    /**
     * 2つのビットマップを合成する
     *
     * @param base    ベースとなる背景画像
     * @param overlay 重ねる前景画像
     * @return 合成された新しいビットマップ
     */
    @NonNull
    private Bitmap combineBitmap(@NonNull final Bitmap base, @NonNull final Bitmap overlay) {
        var result = Bitmap.createBitmap(base.getWidth(), base.getHeight(), Bitmap.Config.ARGB_8888);
        var canvas = new Canvas(result);
        canvas.drawBitmap(base, 0, 0, null);

        var srcRect = new Rect(0, 0, overlay.getWidth(), overlay.getHeight());
        var dstRect = new Rect(0, 0, result.getWidth(), result.getHeight());
        canvas.drawBitmap(overlay, srcRect, dstRect, null);
        return result;
    }

    // 端末画面のスクリーンショットを取り、Bitmap形式のデータとして返す
    @Nullable
    private Bitmap screenShot() {
        var rootView = (ViewGroup) getWindow().getDecorView().findViewById(android.R.id.content);
        if (rootView == null || rootView.getChildCount() == 0) {
            return null;
        }

        var frameView = rootView.getChildAt(0);
        var bitmap = Bitmap.createBitmap(frameView.getWidth(), frameView.getHeight(), Bitmap.Config.ARGB_8888);

        var canvas = new Canvas(bitmap);
        frameView.draw(canvas);
        return bitmap;
    }

    /**
     * SystemUIを非表示にする
     * <pre>
     *     var rootView = findViewById(R.id.main); // レイアウト内のトップのViewを指定する
     *     rootView.postDelayed(() -> hideSystemUI(rootView), 500); // 500msは適当な遅延時間}
     * </pre>
     *
     * @param rootView レイアウト内のトップのViewを指定する
     */
    private void hideSystemUI(@NonNull final View rootView) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        var controller = new WindowInsetsControllerCompat(getWindow(), rootView);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

}
