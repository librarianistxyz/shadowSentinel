package com.shadow.eyes;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.*;

public class SentinelCamera {
    private static final String TAG = "SentinelCamera";
    private final Context context;
    private final SentinelListener listener;

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequestBuilder;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private ImageReader imageReader;
    private MediaRecorder mediaRecorder;
    private String cameraId;
    private String frontCameraId;
    private String backCameraId;
    private boolean isRecordingVideo = false;
    private boolean isCapturingSequence = false;
    private boolean autoCaptureEnabled = false; // Strictly disabled to prevent random photos/videos
    private int burstCount = 0;
    private long lastFaceDetectTime = 0;

    public interface FrameCallback {
        void onFrameCaptured(String base64Jpeg);
        void onError(String message);
    }
    public volatile FrameCallback pendingFrameCallback = null;

    public interface SentinelListener {
        void onFaceSpotted(int faceCount);
        void onPhotoTaken(String path, int index);
        void onVideoStarted(String path);
        void onVideoStopped(String path);
        void onError(String message);
    }

    public SentinelCamera(Context context, SentinelListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void start() {
        if (cameraDevice != null) {
            return;
        }
        startBackgroundThread();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT && frontCameraId == null) {
                    frontCameraId = id;
                } else if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK && backCameraId == null) {
                    backCameraId = id;
                }
            }
            if (frontCameraId == null && manager.getCameraIdList().length > 0) {
                frontCameraId = manager.getCameraIdList()[0];
            }
            cameraId = frontCameraId;

            if (cameraId != null) {
                openCamera(manager);
            }
        } catch (SecurityException | CameraAccessException e) {
            Log.e(TAG, "Camera init failed", e);
            if (listener != null) listener.onError("Camera error: " + e.getMessage());
        }
    }

    private void openCamera(CameraManager manager) throws SecurityException, CameraAccessException {
        manager.openCamera(cameraId, new DeviceStateCallback(this), backgroundHandler);
    }

    static class DeviceStateCallback extends CameraDevice.StateCallback {
        private final SentinelCamera parent;
        public DeviceStateCallback(SentinelCamera parent) { this.parent = parent; }

        @Override
        public void onOpened(CameraDevice camera) {
            parent.cameraDevice = camera;
            parent.startSurveillancePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            parent.cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            parent.cameraDevice = null;
        }
    }

    private void startSurveillancePreview() {
        if (cameraDevice == null) return;
        try {
            SurfaceTexture dummyTexture = new SurfaceTexture(10);
            dummyTexture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(dummyTexture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
                imageReader.setOnImageAvailableListener(new ImageAvailableCallback(this), backgroundHandler);
            }
            surfaces.add(imageReader.getSurface());

            cameraDevice.createCaptureSession(surfaces,
                    new PreviewSessionCallback(this), backgroundHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "startSurveillancePreview error", e);
        }
    }

    static class PreviewSessionCallback extends CameraCaptureSession.StateCallback {
        private final SentinelCamera parent;
        public PreviewSessionCallback(SentinelCamera parent) { this.parent = parent; }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            parent.captureSession = session;
            try {
                if (parent.captureSession != null && parent.previewRequestBuilder != null && parent.backgroundHandler != null) {
                    parent.captureSession.setRepeatingRequest(parent.previewRequestBuilder.build(),
                            new FaceDetectionCallback(parent), parent.backgroundHandler);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed setRepeatingRequest", e);
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.e(TAG, "Preview session configuration failed");
        }
    }

    static class FaceDetectionCallback extends CameraCaptureSession.CaptureCallback {
        private final SentinelCamera parent;
        public FaceDetectionCallback(SentinelCamera parent) { this.parent = parent; }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            android.hardware.camera2.params.Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            if (faces != null && faces.length > 0) {
                long now = System.currentTimeMillis();
                if (now - parent.lastFaceDetectTime > 8000) {
                    parent.lastFaceDetectTime = now;
                    // Notify listener so living eyes & animated mouth react (smirk/look) without taking photos/videos
                    if (parent.listener != null) parent.listener.onFaceSpotted(faces.length);
                    if (parent.autoCaptureEnabled) {
                        parent.executeMenaceSequence();
                    }
                }
            }
        }
    }

    public void executeMenaceSequence() {
        if (!autoCaptureEnabled || isCapturingSequence || cameraDevice == null) return;
        isCapturingSequence = true;
        burstCount = 0;
        takePhotoBurst(1);
    }

    private void takePhotoBurst(final int photoIndex) {
        if (cameraDevice == null || captureSession == null) return;
        try {
            CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            captureSession.capture(captureBuilder.build(), new BurstCaptureCallback(this), backgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed capturing burst photo", e);
            isCapturingSequence = false;
        }
    }

    static class NextBurstTask implements Runnable {
        private final SentinelCamera parent;
        public NextBurstTask(SentinelCamera parent) { this.parent = parent; }
        @Override
        public void run() { parent.takePhotoBurst(2); }
    }

    static class ShiftToVideoTask implements Runnable {
        private final SentinelCamera parent;
        public ShiftToVideoTask(SentinelCamera parent) { this.parent = parent; }
        @Override
        public void run() { parent.shiftToVideoRecording(); }
    }

    static class BurstCaptureCallback extends CameraCaptureSession.CaptureCallback {
        private final SentinelCamera parent;
        public BurstCaptureCallback(SentinelCamera parent) { this.parent = parent; }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            parent.burstCount++;
            if (parent.burstCount < 2) {
                parent.backgroundHandler.postDelayed(new NextBurstTask(parent), 300);
            } else {
                parent.backgroundHandler.postDelayed(new ShiftToVideoTask(parent), 500);
            }
        }
    }

    static class SingleStillCaptureCallback extends CameraCaptureSession.CaptureCallback {
        private final SentinelCamera parent;
        public SingleStillCaptureCallback(SentinelCamera parent) { this.parent = parent; }
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            Log.d(TAG, "Still frame capture hardware complete");
        }
        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            Log.e(TAG, "Still frame capture failed: " + failure.getReason());
            if (parent.pendingFrameCallback != null) {
                FrameCallback cb = parent.pendingFrameCallback;
                parent.pendingFrameCallback = null;
                cb.onError("Hardware capture failure: " + failure.getReason());
            }
        }
    }

    static class ImageAvailableCallback implements ImageReader.OnImageAvailableListener {
        private final SentinelCamera parent;
        public ImageAvailableCallback(SentinelCamera parent) { this.parent = parent; }

        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    image.close();
                    image = null;

                    if (parent.pendingFrameCallback != null) {
                        FrameCallback cb = parent.pendingFrameCallback;
                        parent.pendingFrameCallback = null;
                        String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                        cb.onFrameCaptured(base64);
                    } else if (parent.autoCaptureEnabled) {
                        parent.saveImageToStorage(bytes, parent.burstCount);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onImageAvailable", e);
                if (parent.pendingFrameCallback != null) {
                    FrameCallback cb = parent.pendingFrameCallback;
                    parent.pendingFrameCallback = null;
                    cb.onError("Image processing error: " + e.getMessage());
                }
            } finally {
                if (image != null) image.close();
            }
        }
    }

    static class SwitchCameraStateCallback extends CameraDevice.StateCallback {
        private final SentinelCamera parent;
        private final Runnable onReady;
        public SwitchCameraStateCallback(SentinelCamera parent, Runnable onReady) {
            this.parent = parent;
            this.onReady = onReady;
        }
        @Override
        public void onOpened(CameraDevice camera) {
            parent.cameraDevice = camera;
            parent.startSurveillancePreviewWithCallback(onReady);
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            parent.cameraDevice = null;
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            parent.cameraDevice = null;
            if (onReady != null) onReady.run();
        }
    }

    static class SwitchPreviewSessionCallback extends CameraCaptureSession.StateCallback {
        private final SentinelCamera parent;
        private final Runnable onReady;
        public SwitchPreviewSessionCallback(SentinelCamera parent, Runnable onReady) {
            this.parent = parent;
            this.onReady = onReady;
        }
        @Override
        public void onConfigured(CameraCaptureSession session) {
            parent.captureSession = session;
            try {
                if (parent.captureSession != null && parent.previewRequestBuilder != null && parent.backgroundHandler != null) {
                    parent.captureSession.setRepeatingRequest(parent.previewRequestBuilder.build(),
                            new FaceDetectionCallback(parent), parent.backgroundHandler);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed setRepeatingRequest after switch", e);
            }
            if (onReady != null) {
                parent.backgroundHandler.postDelayed(onReady, 120);
            }
        }
        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.e(TAG, "Switch session configure failed");
            if (onReady != null) onReady.run();
        }
    }

    private void startSurveillancePreviewWithCallback(final Runnable onReady) {
        if (cameraDevice == null) {
            if (onReady != null) onReady.run();
            return;
        }
        try {
            SurfaceTexture dummyTexture = new SurfaceTexture(10);
            dummyTexture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(dummyTexture);

            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(previewSurface);
            previewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                    CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            if (imageReader == null) {
                imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
                imageReader.setOnImageAvailableListener(new ImageAvailableCallback(this), backgroundHandler);
            }
            surfaces.add(imageReader.getSurface());

            cameraDevice.createCaptureSession(surfaces,
                    new SwitchPreviewSessionCallback(this, onReady), backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "startSurveillancePreviewWithCallback error", e);
            if (onReady != null) onReady.run();
        }
    }

    private void switchCamera(String targetId, final Runnable onReady) {
        try {
            if (captureSession != null) {
                try { captureSession.close(); } catch(Exception e) {}
                captureSession = null;
            }
            if (cameraDevice != null) {
                try { cameraDevice.close(); } catch(Exception e) {}
                cameraDevice = null;
            }
            cameraId = targetId;
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            manager.openCamera(cameraId, new SwitchCameraStateCallback(this, onReady), backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "switchCamera error", e);
            if (onReady != null) onReady.run();
        }
    }

    static class TriggerStillTask implements Runnable {
        private final SentinelCamera parent;
        private final FrameCallback callback;
        public TriggerStillTask(SentinelCamera parent, FrameCallback callback) {
            this.parent = parent;
            this.callback = callback;
        }
        @Override
        public void run() {
            parent.triggerStillCapture(callback);
        }
    }

    static class FrameCaptureDispatchTask implements Runnable {
        private final SentinelCamera parent;
        private final boolean useBackCamera;
        private final FrameCallback callback;
        public FrameCaptureDispatchTask(SentinelCamera parent, boolean useBackCamera, FrameCallback callback) {
            this.parent = parent;
            this.useBackCamera = useBackCamera;
            this.callback = callback;
        }
        @Override
        public void run() {
            String targetId = useBackCamera && parent.backCameraId != null ? parent.backCameraId : (parent.frontCameraId != null ? parent.frontCameraId : parent.cameraId);
            if (targetId != null && !targetId.equals(parent.cameraId)) {
                parent.switchCamera(targetId, new TriggerStillTask(parent, callback));
            } else {
                parent.triggerStillCapture(callback);
            }
        }
    }

    public void captureSingleFrame(final boolean useBackCamera, final FrameCallback callback) {
        if (backgroundHandler == null) {
            if (callback != null) callback.onError("Background thread not initialized");
            return;
        }
        backgroundHandler.post(new FrameCaptureDispatchTask(this, useBackCamera, callback));
    }

    private void triggerStillCapture(FrameCallback callback) {
        if (cameraDevice == null || captureSession == null || imageReader == null) {
            if (callback != null) callback.onError("Camera session not ready");
            return;
        }
        this.pendingFrameCallback = callback;
        try {
            CaptureRequest.Builder stillBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            stillBuilder.addTarget(imageReader.getSurface());
            stillBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 75);
            stillBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            stillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureSession.capture(stillBuilder.build(), new SingleStillCaptureCallback(this), backgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "triggerStillCapture exception", e);
            this.pendingFrameCallback = null;
            if (callback != null) callback.onError("Capture failed: " + e.getMessage());
        }
    }

    private void saveImageToStorage(byte[] bytes, int index) {
        File dir = getStorageDir();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File file = new File(dir, "SHADOW_IMG_" + index + "_" + timeStamp + ".jpg");

        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(bytes);
            if (listener != null) listener.onPhotoTaken(file.getAbsolutePath(), index);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save photo", e);
        }
    }

    private void shiftToVideoRecording() {
        if (cameraDevice == null || isRecordingVideo) return;
        try {
            stopSurveillancePreview();

            File dir = getStorageDir();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            final File videoFile = new File(dir, "SHADOW_VID_" + timeStamp + ".mp4");

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(videoFile.getAbsolutePath());
            mediaRecorder.setVideoEncodingBitRate(10000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(1920, 1080);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.prepare();

            Surface recorderSurface = mediaRecorder.getSurface();
            SurfaceTexture dummyTexture = new SurfaceTexture(20);
            dummyTexture.setDefaultBufferSize(640, 480);
            Surface previewSurface = new Surface(dummyTexture);

            final CaptureRequest.Builder videoBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            videoBuilder.addTarget(recorderSurface);
            videoBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Arrays.asList(recorderSurface, previewSurface),
                    new VideoSessionCallback(this, videoBuilder, videoFile), backgroundHandler);

        } catch (Exception e) {
            Log.e(TAG, "shiftToVideoRecording error", e);
            isCapturingSequence = false;
        }
    }

    static class StopVideoTask implements Runnable {
        private final SentinelCamera parent;
        private final String path;
        public StopVideoTask(SentinelCamera parent, String path) {
            this.parent = parent;
            this.path = path;
        }
        @Override
        public void run() {
            parent.stopVideoRecording(path);
        }
    }

    static class VideoSessionCallback extends CameraCaptureSession.StateCallback {
        private final SentinelCamera parent;
        private final CaptureRequest.Builder videoBuilder;
        private final File videoFile;

        public VideoSessionCallback(SentinelCamera parent, CaptureRequest.Builder videoBuilder, File videoFile) {
            this.parent = parent;
            this.videoBuilder = videoBuilder;
            this.videoFile = videoFile;
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
            parent.captureSession = session;
            try {
                parent.captureSession.setRepeatingRequest(videoBuilder.build(), null, parent.backgroundHandler);
                parent.mediaRecorder.start();
                parent.isRecordingVideo = true;
                if (parent.listener != null) parent.listener.onVideoStarted(videoFile.getAbsolutePath());

                parent.backgroundHandler.postDelayed(new StopVideoTask(parent, videoFile.getAbsolutePath()), 10000);

            } catch (Exception e) {
                Log.e(TAG, "MediaRecorder start failed", e);
                parent.isCapturingSequence = false;
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            Log.e(TAG, "Video CaptureSession configuration failed");
            parent.isCapturingSequence = false;
        }
    }

    public void stopVideoRecording(String savedPath) {
        if (!isRecordingVideo) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecordingVideo = false;
            if (listener != null) listener.onVideoStopped(savedPath);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping MediaRecorder", e);
        } finally {
            isCapturingSequence = false;
            startSurveillancePreview();
        }
    }

    private void stopSurveillancePreview() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                captureSession.abortCaptures();
                captureSession.close();
                captureSession = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping preview session", e);
            }
        }
    }

    private File getStorageDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Shadow");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void stop() {
        stopVideoRecording("");
        stopSurveillancePreview();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
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
                Log.e(TAG, "Interrupted stopping thread", e);
            }
        }
    }
}
