package com.example.imagemusicplayer;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGES = 1001;
    private static final int REQUEST_AUDIO = 1002;
    private static final long ANIMATION_MS = 650L;
    private static final String PREFS = "player_prefs";
    private static final String KEY_IMAGES = "image_uris";
    private static final String KEY_AUDIO = "audio_uri";
    private static final String KEY_SLIDE_INTERVAL_INDEX = "slide_interval_index";
    private static final long[] SLIDE_INTERVALS_MS = {2000L, 3000L, 5000L};
    private static final String[] SLIDE_INTERVAL_LABELS = {"快 2s", "舒适 3s", "慢 5s"};
    private static final int[] DEFAULT_IMAGE_RES_IDS = {
            R.drawable.default_slide_1,
            R.drawable.default_slide_2,
            R.drawable.default_slide_3,
            R.drawable.default_slide_4,
            R.drawable.default_slide_5
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Uri> imageUris = new ArrayList<>();

    private FrameLayout imageStage;
    private ImageView frontImage;
    private ImageView backImage;
    private TextView statusText;
    private TextView stageCaption;
    private Button playButton;
    private Button bgmButton;
    private Button previousButton;
    private Button nextButton;
    private Button speedButton;
    private LinearLayout stageOverlay;

    private MediaPlayer mediaPlayer;
    private Uri audioUri;
    private int currentIndex = -1;
    private int slideIntervalIndex = 1;
    private boolean playing = false;
    private boolean frontVisible = true;
    private boolean activityResumed = false;
    private boolean musicPrepared = false;
    private boolean usingDefaultAudio = true;

    private final Runnable slideRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playing || imageUris.isEmpty()) {
                return;
            }
            showNextImage();
            handler.postDelayed(this, getSlideIntervalMs());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        buildUi();
        loadLibrary();
        refreshUi();
        if (!imageUris.isEmpty()) {
            showImageAt(0, false);
        }
    }

    private void buildUi() {
        int primary = getColorValue(R.color.primary);
        int text = getColorValue(R.color.text_primary);
        int muted = getColorValue(R.color.text_muted);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(16));
        root.setBackgroundResource(R.drawable.app_background);
        root.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);

        TextView title = new TextView(this);
        title.setText("图片音乐播放器");
        title.setTextColor(text);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setTextColor(muted);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        statusText.setBackgroundResource(R.drawable.status_pill);
        statusText.setPadding(dp(12), dp(7), dp(12), dp(7));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.gravity = Gravity.CENTER_HORIZONTAL;
        statusParams.setMargins(0, dp(8), 0, dp(14));
        root.addView(statusText, statusParams);

        imageStage = new FrameLayout(this);
        imageStage.setBackgroundResource(R.drawable.player_frame);
        imageStage.setClipToOutline(true);
        imageStage.setPadding(dp(3), dp(3), dp(3), dp(3));
        frontImage = createStageImage();
        backImage = createStageImage();
        imageStage.addView(backImage);
        imageStage.addView(frontImage);
        stageOverlay = new LinearLayout(this);
        stageOverlay.setOrientation(LinearLayout.VERTICAL);
        stageOverlay.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        stageOverlay.setPadding(dp(14), dp(14), dp(14), dp(14));
        stageOverlay.setBackgroundResource(R.drawable.stage_overlay);
        stageCaption = new TextView(this);
        stageCaption.setTextColor(text);
        stageCaption.setTextSize(15);
        stageCaption.setGravity(Gravity.CENTER);
        stageCaption.setTypeface(null, android.graphics.Typeface.BOLD);
        stageCaption.setPadding(dp(12), dp(7), dp(12), dp(7));
        stageCaption.setBackgroundResource(R.drawable.stage_caption);
        stageOverlay.addView(stageCaption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        imageStage.addView(stageOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(imageStage, stageParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), dp(14), dp(8), dp(10));
        controls.setBackgroundResource(R.drawable.controls_panel);

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setGravity(Gravity.CENTER);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);

        Button addImagesButton = makeButton("添加图片", false);
        addImagesButton.setOnClickListener(v -> pickImages());
        rowOne.addView(addImagesButton, weightedButtonParams());

        bgmButton = makeButton("选择 BGM", false);
        bgmButton.setOnClickListener(v -> pickAudio());
        rowOne.addView(bgmButton, weightedButtonParams());
        controls.addView(rowOne, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setGravity(Gravity.CENTER);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);

        previousButton = makeButton("上一张", false);
        previousButton.setOnClickListener(v -> skipImage(-1));
        rowTwo.addView(previousButton, weightedButtonParams());

        playButton = makeButton("播放", true);
        playButton.setOnClickListener(v -> togglePlayback());
        rowTwo.addView(playButton, weightedButtonParams());

        nextButton = makeButton("下一张", false);
        nextButton.setOnClickListener(v -> skipImage(1));
        rowTwo.addView(nextButton, weightedButtonParams());
        LinearLayout.LayoutParams rowTwoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowTwoParams.setMargins(0, dp(10), 0, 0);
        controls.addView(rowTwo, rowTwoParams);

        LinearLayout rowThree = new LinearLayout(this);
        rowThree.setGravity(Gravity.CENTER);
        rowThree.setOrientation(LinearLayout.HORIZONTAL);

        speedButton = makeButton("速度", false);
        speedButton.setTextColor(primary);
        speedButton.setOnClickListener(v -> cycleSlideSpeed());
        rowThree.addView(speedButton, weightedButtonParams());

        Button clearButton = makeButton("清空用户图片", false);
        clearButton.setTextColor(primary);
        clearButton.setOnClickListener(v -> clearImages());
        rowThree.addView(clearButton, weightedButtonParams());
        LinearLayout.LayoutParams rowThreeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowThreeParams.setMargins(0, dp(10), 0, 0);
        controls.addView(rowThree, rowThreeParams);

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        controlsParams.setMargins(0, dp(12), 0, 0);
        root.addView(controls, controlsParams);

        ScrollView wrapper = new ScrollView(this);
        wrapper.setFillViewport(true);
        wrapper.setBackgroundResource(R.drawable.app_background);
        wrapper.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(wrapper);
    }

    private ImageView createStageImage() {
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setBackgroundColor(Color.BLACK);
        image.setAlpha(0f);
        image.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return image;
    }

    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? getColorValue(R.color.app_bg) : getColorValue(R.color.text_primary));
        button.setBackgroundResource(primary ? R.drawable.btn_primary : R.drawable.btn_secondary);
        button.setElevation(dp(2));
        ButtonMotion.attach(button);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1f);
        params.setMargins(dp(5), 0, dp(5), 0);
        return params;
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGES);
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == REQUEST_IMAGES) {
            int before = imageUris.size();
            int skipped = 0;
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    if (!addPersistedUri(data, data.getClipData().getItemAt(i).getUri(), imageUris)) {
                        skipped++;
                    }
                }
            } else if (data.getData() != null) {
                if (!addPersistedUri(data, data.getData(), imageUris)) {
                    skipped++;
                }
            }
            saveLibrary();
            refreshUi();
            if (currentIndex < 0 && !imageUris.isEmpty()) {
                showImageAt(0, false);
            }
            String message = "已添加 " + (imageUris.size() - before) + " 张图片";
            if (skipped > 0) {
                message += "，跳过 " + skipped + " 个无法持久读取的文件";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        } else if (requestCode == REQUEST_AUDIO && data.getData() != null) {
            Uri pickedAudio = data.getData();
            if (persistPermission(data, pickedAudio) && canOpenUri(pickedAudio)) {
                audioUri = pickedAudio;
                usingDefaultAudio = false;
                saveLibrary();
                refreshUi();
                if (playing) {
                    startMusic();
                }
            } else {
                Toast.makeText(this, "无法保存这个 BGM，请换一个本地音频文件", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean addPersistedUri(Intent data, Uri uri, List<Uri> target) {
        if (uri == null || target.contains(uri) || isDefaultImageUri(uri)) {
            return false;
        }
        if (!persistPermission(data, uri) || !canOpenUri(uri)) {
            return false;
        }
        target.add(uri);
        return true;
    }

    private boolean persistPermission(Intent data, Uri uri) {
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (uri == null || flags == 0) {
            return false;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void togglePlayback() {
        if (playing) {
            stopPlayback();
            return;
        }
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "请先添加图片", Toast.LENGTH_SHORT).show();
            return;
        }
        playing = true;
        startMusic();
        if (currentIndex < 0) {
            showImageAt(0, true);
        }
        restartSlideTimer();
        refreshUi();
    }

    private void stopPlayback() {
        playing = false;
        handler.removeCallbacks(slideRunnable);
        pauseMusic();
        refreshUi();
    }

    private void showNextImage() {
        if (imageUris.isEmpty()) {
            return;
        }
        int next = (currentIndex + 1) % imageUris.size();
        showImageAt(next, true);
    }

    private void showPreviousImage() {
        if (imageUris.isEmpty()) {
            return;
        }
        int previous = currentIndex <= 0 ? imageUris.size() - 1 : currentIndex - 1;
        showImageAt(previous, true);
    }

    private void skipImage(int direction) {
        if (imageUris.isEmpty()) {
            Toast.makeText(this, "请先添加图片", Toast.LENGTH_SHORT).show();
            return;
        }
        if (direction < 0) {
            showPreviousImage();
        } else {
            showNextImage();
        }
        if (playing) {
            restartSlideTimer();
        }
    }

    private void showImageAt(int index, boolean animate) {
        if (index < 0 || index >= imageUris.size()) {
            return;
        }
        Uri imageUri = imageUris.get(index);
        if (!canOpenUri(imageUri)) {
            handleMissingImage(index);
            return;
        }
        ImageView incoming = frontVisible ? backImage : frontImage;
        ImageView outgoing = frontVisible ? frontImage : backImage;

        incoming.animate().cancel();
        outgoing.animate().cancel();
        incoming.setImageURI(imageUri);
        incoming.bringToFront();
        stageOverlay.bringToFront();
        incoming.setScaleX(0.98f);
        incoming.setScaleY(0.98f);

        if (!animate) {
            incoming.setAlpha(1f);
            outgoing.setAlpha(0f);
            incoming.setScaleX(1f);
            incoming.setScaleY(1f);
        } else {
            incoming.setAlpha(0f);
            incoming.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(ANIMATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            outgoing.animate()
                    .alpha(0f)
                    .setDuration(ANIMATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        frontVisible = !frontVisible;
        currentIndex = index;
        refreshUi();
    }

    private void startMusic() {
        releaseMusic();
        Uri musicUri = getPlayableAudioUri();
        if (musicUri == null) {
            return;
        }
        mediaPlayer = new MediaPlayer();
        musicPrepared = false;
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
        mediaPlayer.setLooping(true);
        mediaPlayer.setOnPreparedListener(player -> {
            musicPrepared = true;
            if (playing && activityResumed) {
                player.start();
            }
        });
        mediaPlayer.setOnErrorListener((player, what, extra) -> {
            handleMusicFailure(musicUri);
            return true;
        });
        try {
            mediaPlayer.setDataSource(this, musicUri);
            mediaPlayer.prepareAsync();
        } catch (IOException | RuntimeException e) {
            handleMusicFailure(musicUri);
        }
    }

    private void handleMusicFailure(Uri failedUri) {
        boolean failedUserAudio = audioUri != null && audioUri.equals(failedUri);
        releaseMusic();
        if (failedUserAudio) {
            audioUri = null;
            usingDefaultAudio = true;
            saveLibrary();
            refreshUi();
            Toast.makeText(this, "BGM 无法播放，已切回默认音乐", Toast.LENGTH_SHORT).show();
            if (playing) {
                startMusic();
            }
        } else {
            Toast.makeText(this, "默认 BGM 无法播放", Toast.LENGTH_SHORT).show();
        }
    }

    private void pauseMusic() {
        if (mediaPlayer != null && musicPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private void releaseMusic() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        musicPrepared = false;
    }

    private void clearImages() {
        stopPlayback();
        imageUris.clear();
        addDefaultImages();
        currentIndex = -1;
        frontImage.setImageDrawable(null);
        backImage.setImageDrawable(null);
        saveLibrary();
        refreshUi();
        if (!imageUris.isEmpty()) {
            showImageAt(0, false);
        }
    }

    private void loadLibrary() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean changed = false;
        addDefaultImages();
        slideIntervalIndex = prefs.getInt(KEY_SLIDE_INTERVAL_INDEX, 1);
        if (slideIntervalIndex < 0 || slideIntervalIndex >= SLIDE_INTERVALS_MS.length) {
            slideIntervalIndex = 1;
            changed = true;
        }

        String imagesJson = prefs.getString(KEY_IMAGES, "[]");
        try {
            JSONArray array = new JSONArray(imagesJson);
            for (int i = 0; i < array.length(); i++) {
                Uri uri = Uri.parse(array.getString(i));
                if (isDefaultImageUri(uri)) {
                    changed = true;
                } else if (canOpenUri(uri)) {
                    imageUris.add(uri);
                } else {
                    changed = true;
                }
            }
        } catch (JSONException ignored) {
            changed = true;
        }

        String audio = prefs.getString(KEY_AUDIO, null);
        if (audio != null && !audio.isEmpty()) {
            Uri savedAudio = Uri.parse(audio);
            if (isDefaultAudioUri(savedAudio)) {
                audioUri = null;
                usingDefaultAudio = true;
                changed = true;
            } else if (canOpenUri(savedAudio)) {
                audioUri = savedAudio;
                usingDefaultAudio = false;
            } else {
                audioUri = null;
                usingDefaultAudio = true;
                changed = true;
            }
        } else {
            audioUri = null;
            usingDefaultAudio = true;
        }

        if (changed) {
            saveLibrary();
        }
    }

    private void saveLibrary() {
        JSONArray array = new JSONArray();
        for (Uri uri : imageUris) {
            if (!isDefaultImageUri(uri)) {
                array.put(uri.toString());
            }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_IMAGES, array.toString())
                .putString(KEY_AUDIO, audioUri == null ? "" : audioUri.toString())
                .putInt(KEY_SLIDE_INTERVAL_INDEX, slideIntervalIndex)
                .apply();
    }

    private void refreshUi() {
        Uri displayAudioUri = getPlayableAudioUri();
        String musicName = displayAudioUri == null ? "未选择 BGM" : "BGM：" + getDisplayName(displayAudioUri);
        int userImageCount = getUserImageCount();
        String imageState = imageUris.isEmpty()
                ? "图片库为空"
                : "图片 " + Math.max(currentIndex + 1, 1) + " / " + imageUris.size();
        String libraryState = userImageCount == 0 ? "默认图库" : "用户图片 " + userImageCount + " 张";
        statusText.setText(imageState + " · " + libraryState);
        stageCaption.setText((playing ? "正在播放" : "已暂停") + " · " + musicName);
        bgmButton.setText(usingDefaultAudio ? "选择 BGM" : "更换 BGM");
        playButton.setText(playing ? "暂停" : "播放");
        speedButton.setText("速度 " + SLIDE_INTERVAL_LABELS[slideIntervalIndex]);
        playButton.setEnabled(!imageUris.isEmpty());
        playButton.setAlpha(imageUris.isEmpty() ? 0.55f : 1f);
        previousButton.setEnabled(!imageUris.isEmpty());
        previousButton.setAlpha(imageUris.isEmpty() ? 0.55f : 1f);
        nextButton.setEnabled(!imageUris.isEmpty());
        nextButton.setAlpha(imageUris.isEmpty() ? 0.55f : 1f);
    }

    private String getDisplayName(Uri uri) {
        if (isDefaultAudioUri(uri)) {
            return "默认 BGM";
        }
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        } catch (RuntimeException ignored) {
        }
        return "已选择";
    }

    @Override
    protected void onPause() {
        super.onPause();
        activityResumed = false;
        handler.removeCallbacks(slideRunnable);
        pauseMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        removeUnavailableUserImages();
        if (playing && mediaPlayer != null && musicPrepared) {
            mediaPlayer.start();
        }
        if (playing) {
            handler.removeCallbacks(slideRunnable);
            handler.postDelayed(slideRunnable, getSlideIntervalMs());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(slideRunnable);
        releaseMusic();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getColorValue(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private long getSlideIntervalMs() {
        return SLIDE_INTERVALS_MS[slideIntervalIndex];
    }

    private void cycleSlideSpeed() {
        slideIntervalIndex = (slideIntervalIndex + 1) % SLIDE_INTERVALS_MS.length;
        saveLibrary();
        refreshUi();
        if (playing) {
            restartSlideTimer();
        }
    }

    private void restartSlideTimer() {
        handler.removeCallbacks(slideRunnable);
        handler.postDelayed(slideRunnable, getSlideIntervalMs());
    }

    private int getUserImageCount() {
        int count = 0;
        for (Uri uri : imageUris) {
            if (!isDefaultImageUri(uri)) {
                count++;
            }
        }
        return count;
    }

    private boolean canOpenUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            return inputStream != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private void addDefaultImages() {
        for (int resId : DEFAULT_IMAGE_RES_IDS) {
            Uri uri = getResourceUri(resId);
            if (!imageUris.contains(uri)) {
                imageUris.add(uri);
            }
        }
    }

    private void removeUnavailableUserImages() {
        boolean changed = false;
        Iterator<Uri> iterator = imageUris.iterator();
        while (iterator.hasNext()) {
            Uri uri = iterator.next();
            if (!isDefaultImageUri(uri) && !canOpenUri(uri)) {
                iterator.remove();
                changed = true;
            }
        }
        if (changed) {
            if (currentIndex >= imageUris.size()) {
                currentIndex = imageUris.size() - 1;
            }
            saveLibrary();
            refreshUi();
        }
    }

    private void handleMissingImage(int index) {
        Uri uri = imageUris.get(index);
        if (!isDefaultImageUri(uri)) {
            imageUris.remove(index);
            if (currentIndex >= index) {
                currentIndex--;
            }
            addDefaultImages();
            saveLibrary();
            refreshUi();
            if (!imageUris.isEmpty()) {
                showImageAt(Math.max(0, currentIndex + 1) % imageUris.size(), false);
            }
            return;
        }
        Toast.makeText(this, "图片无法读取", Toast.LENGTH_SHORT).show();
    }

    private Uri getPlayableAudioUri() {
        Uri defaultAudioUri = getDefaultAudioUri();
        if (audioUri != null && canOpenUri(audioUri)) {
            usingDefaultAudio = false;
            return audioUri;
        }
        if (audioUri != null) {
            audioUri = null;
            saveLibrary();
        }
        usingDefaultAudio = true;
        return canOpenUri(defaultAudioUri) ? defaultAudioUri : null;
    }

    private Uri getDefaultAudioUri() {
        return getResourceUri(R.raw.default_bgm);
    }

    private Uri getResourceUri(int resId) {
        return Uri.parse("android.resource://" + getPackageName() + "/" + resId);
    }

    private boolean isDefaultImageUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        for (int resId : DEFAULT_IMAGE_RES_IDS) {
            if (getResourceUri(resId).equals(uri)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDefaultAudioUri(Uri uri) {
        return uri != null && getDefaultAudioUri().equals(uri);
    }
}
