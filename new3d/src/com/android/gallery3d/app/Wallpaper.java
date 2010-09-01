/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.app;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;

import com.android.gallery3d.R;
import com.android.gallery3d.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wallpaper picker for the gallery application. This just redirects to the
 * standard pick action.
 */
public class Wallpaper extends Activity {
    private static final String TAG = "SetWallpaper";

    // TODO: move this action to CropImage activity
    private static final String CROP_ACTION = "com.android.camera.action.CROP";

    static final int PHOTO_PICKED = 1;
    static final int CROP_DONE = 2;

    static final int MSG_SHOW_PROGRESS = 0;
    static final int MSG_FINISH = 1;

    static final String DO_LAUNCH_ICICLE = "do_launch";
    static final String TEMP_FILE_PATH_ICICLE = "temp_file_path";

    private ProgressDialog mProgressDialog = null;
    private boolean mDoLaunch = true;
    private File mTempFile;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SHOW_PROGRESS: {
                    CharSequence c = getText(R.string.wallpaper);
                    mProgressDialog = ProgressDialog.show(
                            Wallpaper.this, "", c, true, false);
                    break;
                }
                case MSG_FINISH: {
                    closeProgressDialog();
                    setResult(RESULT_OK);
                    finish();
                    break;
                }
            }
        }
    };

    static class SetWallpaperThread extends Thread {
        private final Bitmap mBitmap;
        private final Handler mHandler;
        private final Context mContext;
        private final File mFile;

        public SetWallpaperThread(Bitmap bitmap,
                Handler handler, Context context, File file) {
            mBitmap = bitmap;
            mHandler = handler;
            mContext = context;
            mFile = file;
        }

        @Override
        public void run() {
            try {
                WallpaperManager.getInstance(mContext).setBitmap(mBitmap);
            } catch (IOException e) {
                Log.e(TAG, "Failed to set wallpaper.", e);
            } finally {
                mHandler.sendEmptyMessage(MSG_FINISH);
                mFile.delete();
            }
        }
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle != null) {
            mDoLaunch = bundle.getBoolean(DO_LAUNCH_ICICLE);
            mTempFile = new File(bundle.getString(TEMP_FILE_PATH_ICICLE));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle saveState) {
        saveState.putBoolean(DO_LAUNCH_ICICLE, mDoLaunch);
        saveState.putString(TEMP_FILE_PATH_ICICLE, mTempFile.getAbsolutePath());
    }

    @Override
    protected void onPause() {
        closeProgressDialog();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mDoLaunch) return;
        Uri imageToUse = getIntent().getData();
        if (imageToUse != null) {
            Intent intent = new Intent();
            intent.setAction(CROP_ACTION);
            intent.setData(imageToUse);
            formatIntent(intent);
            startActivityForResult(intent, CROP_DONE);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
            intent.setType("image/*");
            intent.putExtra("crop", "true");
            formatIntent(intent);
            startActivityForResult(intent, PHOTO_PICKED);
        }
    }

    protected void formatIntent(Intent intent) {
        // TODO: A temporary file is NOT necessary
        // The CropImage intent should be able to set the wallpaper directly
        // without writing to a file, which we then need to read here to write
        // it again as the final wallpaper, this is silly
        mTempFile = getFileStreamPath("temp-wallpaper");
        mTempFile.getParentFile().mkdirs();

        int width = getWallpaperDesiredMinimumWidth();
        int height = getWallpaperDesiredMinimumHeight();
        intent.putExtra("outputX", width);
        intent.putExtra("outputY", height);
        intent.putExtra("aspectX", width);
        intent.putExtra("aspectY", height);
        intent.putExtra("scale", true);
        intent.putExtra("noFaceDetection", true);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTempFile));
        intent.putExtra("outputFormat", Bitmap.CompressFormat.PNG.name());
        // TODO: we should have an extra called "setWallpaper" to ask CropImage
        // to set the cropped image as a wallpaper directly. This means the
        // SetWallpaperThread should be moved out of this class to CropImage
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == PHOTO_PICKED || requestCode == CROP_DONE)
                && (resultCode == RESULT_OK) && (data != null)) {
            try {
                InputStream s = new FileInputStream(mTempFile);
                try {
                    Bitmap bitmap = BitmapFactory.decodeStream(s);
                    if (bitmap == null) {
                        Log.e(TAG, "Failed to set wallpaper. "
                                + "Couldn't get bitmap for path " + mTempFile);
                    } else {
                        mHandler.sendEmptyMessage(MSG_SHOW_PROGRESS);
                        new SetWallpaperThread(bitmap, mHandler, this, mTempFile).start();
                    }
                    mDoLaunch = false;
                } finally {
                    Utils.closeSilently(s);
                }
            } catch (FileNotFoundException ex) {
                Log.e(TAG, "file not found: " + mTempFile, ex);
            }
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }
}