package com.wix.RNCameraKit.gallery;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.View;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;

import java.util.ArrayList;
import java.util.Map;

import javax.annotation.Nullable;

import static com.wix.RNCameraKit.Utils.*;

public class GalleryViewManager extends SimpleViewManager<GalleryView> implements PermissionListener {

    private static final int COMMAND_REFRESH_GALLERY = 1;

    private final String UNSUPPORTED_IMAGE_KEY = "unsupportedImage";
    private final String UNSUPPORTED_TEXT_KEY = "unsupportedText";
    private final String UNSUPPORTED_TEXT_COLOR_KEY = "unsupportedTextColor";
    private final String SUPPORTED_TYPES_KEY = "supportedFileTypes";
    private final String UNSUPPORTED_OVERLAY_KEY = "unsupportedOverlayColor";
    private final String CUSTOM_BUTTON_IMAGE_KEY = "image";
    private final String CUSTOM_BUTTON_BCK_COLOR_KEY = "backgroundColor";
    private final String SELECTION_SELECTED_IMAGE_KEY = "selectedImage";
    private final String SELECTION_UNSELECTED_IMAGE_KEY = "unselectedImage";
    private final String SELECTION_POSITION_KEY = "imagePosition";
    private final String SELECTION_SIZE_KEY = "imageSizeAndroid";
    private final String SELECTION_ENABLED_KEY = "enable";
    private final String SELECTION_OVERLAY_KEY = "overlayColor";
    private Promise mPermissionPromise;
    private int mRequestCode = 0;
    private final String GRANTED = "granted";
    private final String DENIED = "denied";
    private final String NEVER_ASK_AGAIN = "never_ask_again";
    ThemedReactContext mContext;
    private String packageName;
    private SparseArray<Callback> mCallbacks;

    public interface MyRunnable extends Runnable {
        void run(String result);
    }

    /**
     * A handler is required in order to sync configurations made to the adapter - some must run off the UI thread (e.g. drawables
     * fetching), so that the finalizing call to refreshData() (from within {@link #onAfterUpdateTransaction(View)}) will be made
     * <u>strictly after all configurations have settled in</u>.
     *
     * <p>Note: It is not mandatory to invoke <b>all</b> config set-ups via the handler, but we do so anyway so as to avoid
     * races between multiple threads.</p>
     */
    private Handler adapterConfigHandler;

    @Override
    public String getName() {
        return "GalleryView";
    }

    @Override
    protected GalleryView createViewInstance(ThemedReactContext reactContext) {
        final HandlerThread handlerThread = new HandlerThread("GalleryViewManager.configThread");
        handlerThread.start();
        adapterConfigHandler = new Handler(handlerThread.getLooper());
        mContext= reactContext;
        packageName = reactContext.getPackageName();
        mCallbacks = new SparseArray<Callback>();
        GalleryView view = new GalleryView(reactContext);
        view.setAdapter(new GalleryAdapter(reactContext, view));
        return view;
    }

    @Override
    protected void onAfterUpdateTransaction(final GalleryView view) {
        dispatchRefreshDataOnJobQueue(view, false);
        super.onAfterUpdateTransaction(view);
    }

    @ReactProp(name = "minimumInteritemSpacing")
    public void setItemSpacing(GalleryView view, int itemSpacing) {
        view.setItemSpacing(itemSpacing/2);
    }

    @ReactProp(name = "minimumLineSpacing")
    public void setLineSpacing(GalleryView view, int lineSpacing) {
        view.setLineSpacing(lineSpacing/2);
    }

    @ReactProp(name = "albumName")
    public void setAlbumName(final GalleryView view, final String albumName) {
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                getViewAdapter(view).setAlbum(albumName);
            }
        });
    }

    @ReactProp(name = "columnCount")
    public void setColumnCount(GalleryView view, int columnCount) {
        view.setColumnCount(columnCount);
    }

    @ReactProp(name = "selectedImages")
    public void setSelectedUris(final GalleryView view, final ReadableArray uris) {
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                getViewAdapter(view).setSelectedUris(readableArrayToList(uris));
            }
        });
    }

    @ReactProp(name = "dirtyImages")
    public void setDirtyImages(final GalleryView view, final ReadableArray uris) {
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                getViewAdapter(view).setDirtyUris(readableArrayToList(uris));
            }
        });
    }

    @ReactProp(name = "selectedImageIcon")
    public void setSelectedImage(final GalleryView view, final String imageSource) {
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                final Drawable drawable = ResourceDrawableIdHelper.getIcon(view.getContext(), imageSource);
                getViewAdapter(view).setSelectedDrawable(drawable);
            }
        });
    }

    @ReactProp(name = "unSelectedImageIcon")
    public void setUnselectedImage(final GalleryView view, final String imageSource) {
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                final Drawable drawable = ResourceDrawableIdHelper.getIcon(view.getContext(), imageSource);
                getViewAdapter(view).setUnselectedDrawable(drawable);
            }
        });
    }

    @ReactProp(name = "selection")
    public void setSelectionProperties(final GalleryView view, final ReadableMap selectionProps) {
        final String selectedImage = getStringSafe(selectionProps, SELECTION_SELECTED_IMAGE_KEY);
        final String unselectedImage = getStringSafe(selectionProps, SELECTION_UNSELECTED_IMAGE_KEY);
        final Integer position = getIntSafe(selectionProps, SELECTION_POSITION_KEY);
        final String size = getStringSafe(selectionProps, SELECTION_SIZE_KEY);
        final Boolean enabled = getBooleanSafe(selectionProps, SELECTION_ENABLED_KEY);
        final Integer selectionOverlayColor = getIntSafe(selectionProps, SELECTION_OVERLAY_KEY);
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                final GalleryAdapter viewAdapter = getViewAdapter(view);

                if (selectedImage != null) {
                    final Drawable selectedDrawable = ResourceDrawableIdHelper.getIcon(view.getContext(), selectedImage);
                    viewAdapter.setSelectedDrawable(selectedDrawable);
                }

                if (unselectedImage != null) {
                    final Drawable unselectedDrawable = ResourceDrawableIdHelper.getIcon(view.getContext(), unselectedImage);
                    viewAdapter.setUnselectedDrawable(unselectedDrawable);
                }

                if (position != null) {
                    viewAdapter.setSelectionDrawablePosition(position);
                }

                if (size != null) {
                    final int sizeCode = size.equalsIgnoreCase("large") ? GalleryAdapter.SELECTED_IMAGE_SIZE_LARGE : GalleryAdapter.SELECTED_IMAGE_SIZE_NORMAL;
                    viewAdapter.setSelectedDrawableSize(sizeCode);
                }

                viewAdapter.setShouldEnabledSelection(enabled != null ? enabled : true);
                viewAdapter.setSelectionOverlayColor(selectionOverlayColor);
            }
        });
    }

    @ReactProp(name = "fileTypeSupport")
    public void setFileTypeSupport(final GalleryView view, final ReadableMap fileTypeSupport) {
        final ReadableArray supportedFileTypes = fileTypeSupport.getArray(SUPPORTED_TYPES_KEY);
        final String unsupportedOverlayColor = getStringSafe(fileTypeSupport, UNSUPPORTED_OVERLAY_KEY);
        final String unsupportedImageSource = getStringSafe(fileTypeSupport, UNSUPPORTED_IMAGE_KEY);
        final String unsupportedText = getStringSafe(fileTypeSupport, UNSUPPORTED_TEXT_KEY);
        final String unsupportedTextColor = getStringSafe(fileTypeSupport, UNSUPPORTED_TEXT_COLOR_KEY);

        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                Drawable unsupportedImage = null;
                if(unsupportedImageSource != null) {
                    unsupportedImage = ResourceDrawableIdHelper.getIcon(view.getContext(), unsupportedImageSource);
                }
                final Drawable unsupportedFinalImage = unsupportedImage;
                final ArrayList<String> supportedFileTypesList = new ArrayList<String>();
                if(supportedFileTypes != null && supportedFileTypes.size() != 0) {
                    for (int i = 0; i < supportedFileTypes.size(); i++) {
                        supportedFileTypesList.add(supportedFileTypes.getString(i));
                    }
                }

                getViewAdapter(view)
                        .setUnsupportedUIParams(
                                unsupportedOverlayColor,
                                unsupportedFinalImage,
                                unsupportedText,
                                unsupportedTextColor);
                getViewAdapter(view).setSupportedFileTypes(supportedFileTypesList);
            }
        });
    }

    @ReactProp(name = "customButtonStyle")
    public void setCustomButton(final GalleryView view, final ReadableMap props) {
        dispatchOnConfigJobQueue(new Runnable() {
            @Override
            public void run() {
                final String imageResource = getStringSafe(props, CUSTOM_BUTTON_IMAGE_KEY);
                final Integer backgroundColor = getIntSafe(props, CUSTOM_BUTTON_BCK_COLOR_KEY);
                final Drawable drawable = ResourceDrawableIdHelper.getIcon(view.getContext(), imageResource);

                getViewAdapter(view).setCustomButtonImage(drawable);
                if (backgroundColor != null) {
                    getViewAdapter(view).setCustomButtonBackgroundColor(backgroundColor);
                }
            }
        });
    }

    @Nullable
    @Override
    public Map getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.builder()
                .put("onTapImage", MapBuilder.of("registrationName", "onTapImage"))
                .put("onCustomButtonPress", MapBuilder.of("registrationName", "onCustomButtonPress"))
                .build();
    }

    @Nullable
    @Override
    public Map<String, Integer> getCommandsMap() {
        return MapBuilder.of("refreshGalleryView", COMMAND_REFRESH_GALLERY);
    }

    @Override
    public void receiveCommand(GalleryView view, int commandId, @Nullable ReadableArray args) {
        if (commandId == COMMAND_REFRESH_GALLERY) {
            dispatchRefreshDataOnJobQueue(view, true);
        }
    }

    private void dispatchOnConfigJobQueue(Runnable runnable) {
        adapterConfigHandler.post(runnable);
    }

    private void dispatchRefreshDataOnJobQueue(final GalleryView view, final boolean force) {
        if(checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
            dispatchOnConfigJobQueue(new Runnable() {
                @Override
                public void run() {
                    getViewAdapter(view).refreshData(force);
                }
            });
        } else {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, mPermissionPromise, new MyRunnable() {

                @Override
                public void run(String result) {
                    // TODO Auto-generated method stub
                    if(result== GRANTED){
                        dispatchOnConfigJobQueue(new Runnable() {
                            @Override
                            public void run() {
                                getViewAdapter(view).refreshData(force);
                            }
                        });
                    } else if(result== NEVER_ASK_AGAIN){


                    } else if(result== DENIED){


                    }
                }

                @Override
                public void run() {
                    // TODO Auto-generated method stub

                }
            });
        }

    }

    public void requestPermission(final String permission, final Promise promise, final MyRunnable Func) {
        Context context = mContext;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Func.run(GRANTED);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            Func.run(GRANTED);
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
                                Func.run(GRANTED);
                            } else {
                                PermissionAwareActivity activity = (PermissionAwareActivity) args[1];
                                if (activity.shouldShowRequestPermissionRationale(permission)) {
                                    Func.run(DENIED);
                                } else {
                                    Func.run(NEVER_ASK_AGAIN);
                                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
                                            mContext.getCurrentActivity());
                                    alertDialogBuilder.setTitle("Permission Request");
                                    alertDialogBuilder
                                            .setMessage("Please, Allow Storage Permission Access!")
                                            .setCancelable(false)
                                            .setPositiveButton("Open Settings",new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,int id) {
                                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                                    intent.setData(Uri.parse("package:" + mContext.getPackageName()));
                                                    if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                                                        mContext.startActivity(intent);
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


    private GalleryAdapter getViewAdapter(GalleryView view) {
        return ((GalleryAdapter) view.getAdapter());
    }

        public boolean checkPermission(final String permission) {
            Context context = mContext;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return true;
            }
            return (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
        }


    /** Method called by the activity with the result of the permission request. */
    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        mCallbacks.get(requestCode).invoke(grantResults, getPermissionAwareActivity());
        mCallbacks.remove(requestCode);
        return mCallbacks.size() == 0;
    }

    private PermissionAwareActivity getPermissionAwareActivity() {
        Activity activity = mContext.getCurrentActivity();
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
}
