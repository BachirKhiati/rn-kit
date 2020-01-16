package com.wix.RNCameraKit.camera;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import androidx.annotation.ColorInt;
import androidx.arch.core.util.Function;

import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.uimanager.ThemedReactContext;
import com.wix.RNCameraKit.Utils;
import com.wix.RNCameraKit.camera.barcode.BarcodeFrame;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

public class CameraView extends FrameLayout implements SurfaceHolder.Callback, PermissionListener {


    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1002;
    private static final int PERMISSION_GRANTED = 1;
    private static final int PERMISSION_NOT_DETERMINED = -1;
    private static final int PERMISSION_DENIED = 0;

    private int mRequestCode = 1002;
    private final String GRANTED = "granted";
    private final String DENIED = "denied";
    private final String NEVER_ASK_AGAIN = "never_ask_again";
    private Promise mPermissionPromise;
    private static ThemedReactContext reactContext;

    private SparseArray<Callback> mCallbacks;

    private SurfaceView surface;

    private boolean showFrame;
    private Rect frameRect;
    private BarcodeFrame barcodeFrame;
    @ColorInt private int frameColor = Color.GREEN;
    @ColorInt private int laserColor = Color.RED;

    public CameraView(ThemedReactContext context) {
        super(context);
        mCallbacks = new SparseArray<Callback>();
        CameraView.reactContext = context;
        surface = new SurfaceView(context);
        setBackgroundColor(Color.BLACK);
        addView(surface, MATCH_PARENT, MATCH_PARENT);
        surface.getHolder().addCallback(this);
    }

    public interface MyRunnable extends Runnable {
        void run(String result, CameraView view);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int actualPreviewWidth = getResources().getDisplayMetrics().widthPixels;
        int actualPreviewHeight = getResources().getDisplayMetrics().heightPixels;
        int height = Utils.convertDeviceHeightToSupportedAspectRatio(actualPreviewWidth, actualPreviewHeight);
        surface.layout(0, 0, actualPreviewWidth, height);
        if (barcodeFrame != null) {
            ((View) barcodeFrame).layout(0, 0, actualPreviewWidth, height);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if(checkPermission(Manifest.permission.CAMERA)){
            CameraViewManager.setCameraView(this);
        } else {
            requestPermission(Manifest.permission.CAMERA, mPermissionPromise, new MyRunnable() {

                @Override
                public void run(String result, CameraView act) {
                    if(result== GRANTED){
                        CameraViewManager.setCameraView(act);
                    } else if(result== NEVER_ASK_AGAIN){

                    } else if(result== DENIED){
                    }
                }

                @Override
                public void run() {
                    // TODO Auto-generated method stub

                }
            }, this);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if(checkPermission(Manifest.permission.CAMERA)) {
            CameraViewManager.setCameraView(this);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        CameraViewManager.removeCameraView();
    }


    public SurfaceHolder getHolder() {
        return surface.getHolder();
    }

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };

    @Override
    public void requestLayout() {
        super.requestLayout();
        post(measureAndLayout);
    }

    public void setShowFrame(boolean showFrame) {
        this.showFrame = showFrame;
    }

    public void showFrame() {
        if (showFrame) {
            barcodeFrame = new BarcodeFrame(getContext());
            barcodeFrame.setFrameColor(frameColor);
            barcodeFrame.setLaserColor(laserColor);
            addView(barcodeFrame);
            requestLayout();
        }
    }

    public Rect getFramingRectInPreview(int previewWidth, int previewHeight) {
        if (frameRect == null) {
            if (barcodeFrame != null) {
                Rect framingRect = new Rect(barcodeFrame.getFrameRect());
                int frameWidth = barcodeFrame.getWidth();
                int frameHeight = barcodeFrame.getHeight();

                if (previewWidth < frameWidth) {
                    framingRect.left = framingRect.left * previewWidth / frameWidth;
                    framingRect.right = framingRect.right * previewWidth / frameWidth;
                }
                if (previewHeight < frameHeight) {
                    framingRect.top = framingRect.top * previewHeight / frameHeight;
                    framingRect.bottom = framingRect.bottom * previewHeight / frameHeight;
                }

                frameRect = framingRect;
            } else {
                frameRect = new Rect(0, 0, previewWidth, previewHeight);
            }
        }
        return frameRect;
    }

    public void setFrameColor(@ColorInt int color) {
        this.frameColor = color;
        if (barcodeFrame != null) {
            barcodeFrame.setFrameColor(color);
        }
    }

    public void setLaserColor(@ColorInt int color) {
        this.laserColor = color;
        if (barcodeFrame != null) {
            barcodeFrame.setLaserColor(laserColor);
        }
    }

    /**
     * Set background color for Surface view on the period, while camera is not loaded yet.
     * Provides opportunity for user to hide period while camera is loading
     * @param color - color of the surfaceview
     */
    public void setSurfaceBgColor(@ColorInt int color) {
        surface.setBackgroundColor(color);
    }


    public void requestPermission(final String permission, final Promise promise, final MyRunnable Func, final CameraView cameraView) {
        Context context = reactContext;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Func.run(GRANTED, cameraView);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            Func.run(GRANTED, cameraView);
        }

        try {
            PermissionAwareActivity activity = getPermissionAwareActivity();
            mCallbacks.put(
                    mRequestCode,
                    new Callback() {
                        @Override
                        public void invoke(Object... args) {
                            int[] results = (int[]) args[0];
                            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                                Func.run(GRANTED, cameraView);
                            } else {
                                PermissionAwareActivity activity = (PermissionAwareActivity) args[1];
                                if (activity.shouldShowRequestPermissionRationale(permission)) {
                                    Func.run(DENIED , null);
                                } else {
                                    Func.run(NEVER_ASK_AGAIN, null);
                                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                                            reactContext.getCurrentActivity());
                                    alertDialogBuilder.setTitle("Permission Request");
                                    alertDialogBuilder
                                            .setMessage("Please, Allow Storage Permission Access!")
                                            .setCancelable(false)
                                            .setPositiveButton("Open Settings",new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,int id) {
                                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                                    intent.setData(Uri.parse("package:" + reactContext.getPackageName()));
                                                    if (intent.resolveActivity(reactContext.getPackageManager()) != null) {
                                                        reactContext.startActivity(intent);
                                                    }
                                                }
                                            })
                                            .setNegativeButton("CANCEL",new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int id) {
                                                    dialog.cancel();
                                                }
                                            });
                                    AlertDialog alertDialog = alertDialogBuilder.create();
                                    alertDialog.show();


                                }
                            }
                        }
                    });

            activity.requestPermissions(new String[] {permission}, mRequestCode, this);
            mRequestCode++;
        } catch (IllegalStateException e) {
        }
    }


    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        mCallbacks.get(requestCode).invoke(grantResults, getPermissionAwareActivity());
        mCallbacks.remove(requestCode);
        return mCallbacks.size() == 0;
    }

    private PermissionAwareActivity getPermissionAwareActivity() {
        Activity activity = reactContext.getCurrentActivity();
        if (activity == null) {
            throw new IllegalStateException(
                    "Tried to use permissions API while not attached to an " + "Activity.");
        } else if (!(activity instanceof PermissionAwareActivity)) {
            throw new IllegalStateException(
                    "Tried to use permissions API but the host Activity doesn't"
                            + " implement PermissionAwareActivity.");
        }
        return (PermissionAwareActivity) activity;
    }

    public boolean checkPermission(final String permission) {
        Context context = reactContext;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
    }
}
