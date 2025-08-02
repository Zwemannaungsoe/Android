package jp.jec.ac.cm0119.funnycamera;

import androidx.annotation.NonNull;

enum CameraMode {
    NORMAL("NormalMode", -1),
    CHARACTER("CharacterMode", R.drawable.character),
    SERIFU("SerifuMode", R.drawable.serifu);

    @NonNull
    private final String displayName;
    private final int drawableResId;

    CameraMode(@NonNull String displayName, int drawableResId) {
        this.displayName = displayName;
        this.drawableResId = drawableResId;
    }

    @NonNull
    String getDisplayName() {
        return displayName;
    }

    int getDrawableResId() {
        return drawableResId;
    }
}