package com.test.inventorysystem.zxing;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.PlanarYUVLuminanceSource;
import com.test.inventorysystem.zxing.camera.AutoFocusManager;
import com.test.inventorysystem.zxing.camera.CameraConfigurationManager;
import com.test.inventorysystem.zxing.camera.OpenCameraInterface;
import com.test.inventorysystem.zxing.camera.PreviewCallback;

import java.io.IOException;

/**
 * Created by youmengli on 6/15/16.
 */

public class CameraManager {

    private static final String TAG = CameraManager.class.getSimpleName();

    private static final int MIN_FRAME_WIDTH = 240;

    private static final int MAX_FRAME_WIDTH = 1200; // = 5/8 * 1920

    private final Context context;

    private Camera camera;

    private Rect framingRect;

    private Rect framingRectInPreview;

    private final CameraConfigurationManager configManager;

    private AutoFocusManager autoFocusManager;

    private boolean initialized;

    private int requestedFramingRectWidth;

    private int requestedFramingRectHeight;

    private boolean previewing;

    /**
     * Preview frames are delivered here, which we pass on to the registered
     * handler. Make sure to clear the handler so it will only receive one
     * message.
     */
    private final PreviewCallback previewCallback;

    public CameraManager(Context context) {
        this.context = context;
        this.configManager = new CameraConfigurationManager(context);
        previewCallback = new PreviewCallback(configManager);
    }


    /**
     * Calculates the framing rect which the UI should draw to show the user
     * where to place the barcode. This target helps with alignment as well as
     * forces the user to hold the device far enough away to ensure the image
     * will be in focus.
     *
     * @return The rectangle to draw on screen in window coordinates.
     */
    public synchronized Rect getFramingRect() {
        if (framingRect == null) {
            if (camera == null) {
                return null;
            }
            Point screenResolution = configManager.getScreenResolution();
            System.out.println(screenResolution);
            if (screenResolution == null) {
                // Called early, before init even finished
                return null;
            }

            int width = findDesiredDimensionInRange(screenResolution.x,
                    MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
            // 将扫描框设置成一个正方形
            int height = width;

            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + height);

            System.out.println("Calculated framing rect: " + framingRect);
        }

        return framingRect;
    }

    /**
     * Target 5/8 of each dimension<br/>
     * 计算结果在hardMin~hardMax之间
     *
     * @param resolution
     * @param hardMin
     * @param hardMax
     * @return
     */
    private static int findDesiredDimensionInRange(int resolution, int hardMin,
                                                   int hardMax) {
        int dim = 5 * resolution / 9; // Target 5/8 of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        if (dim > hardMax) {
            return hardMax;
        }
        return dim;
    }

    /**
     * Convenience method for
     * @link org.madmatrix.zxing.android.CaptureActivity
     */
    public synchronized void setTorch(boolean newSetting) {
        if (newSetting != configManager.getTorchState(camera)) {
            if (camera != null) {
                if (autoFocusManager != null) {
                    autoFocusManager.stop();
                }
                configManager.setTorch(camera, newSetting);
                if (autoFocusManager != null) {
                    autoFocusManager.start();
                }
            }
        }
    }

    public synchronized boolean isOpen() {
        return camera != null;
    }

    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder
     *            The surface object which the camera will draw preview frames
     *            into.
     * @throws IOException
     *             Indicates the camera driver failed to open.
     */
    public synchronized void openDriver(SurfaceHolder holder)
            throws IOException {
        Camera theCamera = camera;
        if (theCamera == null) {
            // 获取手机背面的摄像头
            theCamera = OpenCameraInterface.open();
            if (theCamera == null) {
                throw new IOException();
            }
            camera = theCamera;
        }

        // 设置摄像头预览view
        theCamera.setPreviewDisplay(holder);

        if (!initialized) {
            initialized = true;
            configManager.initFromCameraParameters(theCamera);
            if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
                setManualFramingRect(requestedFramingRectWidth,
                        requestedFramingRectHeight);
                requestedFramingRectWidth = 0;
                requestedFramingRectHeight = 0;
            }
        }

        Camera.Parameters parameters = theCamera.getParameters();
        String parametersFlattened = parameters == null ? null : parameters
                .flatten(); // Save
        // these,
        // temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera, false);
        }
        catch (RuntimeException re) {
            // Driver failed
            System.out.println("Camera rejected parameters. Setting only minimal safe-mode parameters");
            System.out.println("Resetting to saved camera params: "
                    + parametersFlattened);
            // Reset:
            if (parametersFlattened != null) {
                parameters = theCamera.getParameters();
                parameters.unflatten(parametersFlattened);
                try {
                    theCamera.setParameters(parameters);
                    configManager.setDesiredCameraParameters(theCamera, true);
                }
                catch (RuntimeException re2) {
                    // Well, darn. Give up
                    System.out.println("Camera rejected even safe-mode parameters! No configuration");
                }
            }
        }

    }

    /**
     * Closes the camera driver if still in use.
     */
    public synchronized void closeDriver() {
        if (camera != null) {
            camera.release();
            camera = null;
            // Make sure to clear these each time we close the camera, so that
            // any scanning rect
            // requested by intent is forgotten.
            framingRect = null;
            framingRectInPreview = null;
        }
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions,
     * rather than determine them automatically based on screen resolution.
     *
     * @param width
     *            The width in pixels to scan.
     * @param height
     *            The height in pixels to scan.
     */
    public synchronized void setManualFramingRect(int width, int height) {
        if (initialized) {
            Point screenResolution = configManager.getScreenResolution();
            if (width > screenResolution.x) {
                width = screenResolution.x;
            }
            if (height > screenResolution.y) {
                height = screenResolution.y;
            }
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + height);
            Log.d(TAG, "Calculated manual framing rect: " + framingRect);
            framingRectInPreview = null;
        }
        else {
            requestedFramingRectWidth = width;
            requestedFramingRectHeight = height;
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data
     *            A preview frame.
     * @param width
     *            The width of the image.
     * @param height
     *            The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    public PlanarYUVLuminanceSource buildLuminanceSource(byte[] data,
                                                         int width, int height) {
        Rect rect = getFramingRectInPreview();
        if (rect == null) {
            return null;
        }
        // Go ahead and assume it's YUV rather than die.
        return new PlanarYUVLuminanceSource(data, width, height, rect.left,
                rect.top, rect.width(), rect.height(), false);
    }

    /**
     * Like {@link #getFramingRect} but coordinates are in terms of the preview
     * frame, not UI / screen.
     */
    public synchronized Rect getFramingRectInPreview() {
        if (framingRectInPreview == null) {
            Rect framingRect = getFramingRect();
            if (framingRect == null) {
                return null;
            }
            Rect rect = new Rect(framingRect);
            Point cameraResolution = configManager.getCameraResolution();
            Point screenResolution = configManager.getScreenResolution();
            if (cameraResolution == null || screenResolution == null) {
                // Called early, before init even finished
                return null;
            }
            rect.left = rect.left * cameraResolution.y / screenResolution.x;
            rect.right = rect.right * cameraResolution.y / screenResolution.x;
            rect.top = rect.top * cameraResolution.x / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.x / screenResolution.y;
            framingRectInPreview = rect;

            Log.d(TAG, "Calculated framingRectInPreview rect: "
                    + framingRectInPreview);
            Log.d(TAG, "cameraResolution: " + cameraResolution);
            Log.d(TAG, "screenResolution: " + screenResolution);
        }

        return framingRectInPreview;
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    public synchronized void startPreview() {
        Camera theCamera = camera;
        if (theCamera != null && !previewing) {
            // Starts capturing and drawing preview frames to the screen
            // Preview will not actually start until a surface is supplied with
            // setPreviewDisplay(SurfaceHolder) or
            // setPreviewTexture(SurfaceTexture).
            theCamera.startPreview();

            previewing = true;
            autoFocusManager = new AutoFocusManager(context, camera);
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data
     * will arrive as byte[] in the message.obj field, with width and height
     * encoded as message.arg1 and message.arg2, respectively. <br/>
     *
     * 两个绑定操作：<br/>
     * 1：将handler与回调函数绑定；<br/>
     * 2：将相机与回调函数绑定<br/>
     * 综上，该函数的作用是当相机的预览界面准备就绪后就会调用hander向其发送传入的message
     *
     * @param handler
     *            The handler to send the message to.
     * @param message
     *            The what field of the message to be sent.
     */
    public synchronized void requestPreviewFrame(Handler handler, int message) {
        Camera theCamera = camera;
        if (theCamera != null && previewing) {
            previewCallback.setHandler(handler, message);

            // 绑定相机回调函数，当预览界面准备就绪后会回调Camera.PreviewCallback.onPreviewFrame
            theCamera.setOneShotPreviewCallback(previewCallback);
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    public synchronized void stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
            autoFocusManager = null;
        }
        if (camera != null && previewing) {
            camera.stopPreview();
            previewCallback.setHandler(null, 0);
            previewing = false;
        }
    }

}
