package com.applock.guardian.utils;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.Collections;

/**
 * FaceDetectHelper — opens front camera silently (invisible TextureView)
 * and uses Android's built-in face detection.
 *
 * When a face is detected → fires onFaceDetected callback.
 * The camera preview is NOT visible to the user.
 */
public class FaceDetectHelper {

    public interface FaceDetectCallback {
        void onFaceDetected();
        void onNoFace();
        void onError(String message);
    }

    private Context context;
    private FaceDetectCallback callback;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private TextureView hiddenTextureView;
    private boolean isRunning = false;
    private boolean faceDetectedOnce = false;

    // Face detection delay — wait X ms after face found before unlocking
    private static final long FACE_CONFIRM_DELAY_MS = 600;
    private final Handler uiHandler = new Handler();

    public FaceDetectHelper(Context context, TextureView hiddenTextureView, FaceDetectCallback callback) {
        this.context = context;
        this.hiddenTextureView = hiddenTextureView;
        this.callback = callback;
    }

    public void startDetection() {
        if (isRunning) return;
        if (!hasCameraPermission()) {
            callback.onError("Camera permission required");
            return;
        }

        isRunning = true;
        faceDetectedOnce = false;
        startBackgroundThread();

        if (hiddenTextureView.isAvailable()) {
            openFrontCamera();
        } else {
            hiddenTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    openFrontCamera();
                }
                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture s, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture s) { return true; }
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture s) {
                    // Called every frame — check face detection here
                    checkForFace();
                }
            });
        }
    }

    private void openFrontCamera() {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String frontCameraId = getFrontCameraId(cameraManager);
            if (frontCameraId == null) {
                callback.onError("No front camera found");
                return;
            }

            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                callback.onError("Camera permission denied");
                return;
            }

            cameraManager.openCamera(frontCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession();
                }
                @Override public void onDisconnected(@NonNull CameraDevice camera) { camera.close(); }
                @Override public void onError(@NonNull CameraDevice camera, int error) {
                    camera.close();
                    callback.onError("Camera error: " + error);
                }
            }, backgroundHandler);

        } catch (CameraAccessException | SecurityException e) {
            callback.onError(e.getMessage());
        }
    }

    private void createCaptureSession() {
        try {
            SurfaceTexture texture = hiddenTextureView.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(320, 240); // small, invisible

            Surface surface = new Surface(texture);

            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);

            // Enable face detection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                        CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);
            }

            cameraDevice.createCaptureSession(
                    Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                CaptureRequest request = builder.build();
                                session.setRepeatingRequest(request, captureCallback, backgroundHandler);
                            } catch (CameraAccessException e) {
                                callback.onError(e.getMessage());
                            }
                        }
                        @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            callback.onError("Camera config failed");
                        }
                    },
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            callback.onError(e.getMessage());
        }
    }

    private final CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull android.hardware.camera2.TotalCaptureResult result) {
                    if (!isRunning || faceDetectedOnce) return;

                    android.hardware.camera2.params.Face[] faces = result.get(
                            android.hardware.camera2.CaptureResult.STATISTICS_FACES);

                    if (faces != null && faces.length > 0) {
                        faceDetectedOnce = true;
                        // Delay slightly to confirm it's a real face
                        uiHandler.postDelayed(() -> {
                            if (isRunning) {
                                callback.onFaceDetected();
                                stopDetection();
                            }
                        }, FACE_CONFIRM_DELAY_MS);
                    }
                }
            };

    private void checkForFace() {
        // Handled in captureCallback
    }

    public void stopDetection() {
        isRunning = false;
        try {
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        } catch (Exception e) {
            // ignore
        }
        stopBackgroundThread();
    }

    private String getFrontCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id;
            }
        }
        return null;
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("FaceDetectThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }
}
