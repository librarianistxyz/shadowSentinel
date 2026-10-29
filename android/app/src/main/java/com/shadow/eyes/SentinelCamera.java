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
    private boolean isRecordingVideo = false;
    private boolean isCapturingSequence = false;
    private boolean autoCaptureEnabled = false; // Strictly disabled to prevent random photos/videos
    private int burstCount = 0;
    private long lastFaceDetectTime = 0;

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
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null && manager.getCameraIdList().length > 0) {
                cameraId = manager.getCameraIdList()[0];
            }

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
            if (autoCaptureEnabled) {
                imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2);
                imageReader.setOnImageAvailableListener(new ImageAvailableCallback(this), backgroundHandler);
                surfaces.add(imageReader.getSurface());
            }

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
                    parent.saveImageToStorage(bytes, parent.burstCount);
                }
            } finally {
                if (image != null) image.close();
            }
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
