package com.encore.chunkoid.core.runtime;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RootFSInstaller {
    private static final String TAG = "RootFSInstaller";
    private static final String ROOTFS_ZIP = "rootfs.zip";

    public interface ProgressCallback {
        void onProgress(int progress);
        void onComplete(boolean success);
    }

    public static boolean extractRootFS(Context context) {
        return extractRootFS(context, null);
    }

    public static boolean extractRootFS(Context context, ProgressCallback callback) {
        File rootfsDir = new File(context.getFilesDir().getParentFile(), "files/rootfs");

        Log.d(TAG, "Starting RootFS extraction from ZIP...");
        Log.d(TAG, "rootfsDir = " + rootfsDir.getAbsolutePath());

        if (rootfsDir.exists() && rootfsDir.listFiles() != null && rootfsDir.listFiles().length > 0) {
            if (isJavaValid(rootfsDir)) {
                Log.d(TAG, "RootFS already exists and Java is valid, skipping extraction");
                if (callback != null) {
                    callback.onProgress(100);
                    callback.onComplete(true);
                }
                return true;
            } else {
                Log.d(TAG, "RootFS exists but Java is invalid, forcing re-extraction");
                deleteDir(rootfsDir);
            }
        }

        if (!rootfsDir.mkdirs()) {
            Log.e(TAG, "Failed to create rootfs directory");
            if (callback != null) {
                callback.onComplete(false);
            }
            return false;
        }

        try {
            extractZipAsset(context, ROOTFS_ZIP, rootfsDir, callback);

            File javaBin = findJavaBinary(rootfsDir);
            Log.d(TAG, "Extraction complete. Java binary exists: " + javaBin.exists());
            if (javaBin.exists()) {
                Log.d(TAG, "Java binary length: " + javaBin.length());
            }

            if (callback != null) {
                callback.onProgress(100);
                callback.onComplete(true);
            }
            Log.d(TAG, "RootFS extraction completed successfully");
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to extract RootFS", e);
            deleteDir(rootfsDir);
            if (callback != null) {
                callback.onComplete(false);
            }
            return false;
        }
    }

    private static void extractZipAsset(Context context, String zipName, File destDir, ProgressCallback callback) throws IOException {
        Log.d(TAG, "Opening ZIP asset for extraction: " + zipName);

        try (InputStream is = context.getAssets().open(zipName);
             ZipInputStream zis = new ZipInputStream(is)) {

            byte[] buffer = new byte[65536];
            ZipEntry entry;
            int extractedEntries = 0;
            int estimatedTotalEntries = 150;
            int lastReportedProgress = -1;

            while ((entry = zis.getNextEntry()) != null) {
                File newFile = newFile(destDir, entry);

                if (entry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory: " + parent);
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.flush();
                    }

                    String entryName = entry.getName().toLowerCase();
                    if (entryName.contains("bin/") || entryName.contains("lib/") ||
                        entryName.endsWith(".so") || entryName.endsWith(".elf") || entryName.endsWith(".sh")) {
                        newFile.setExecutable(true, false);
                        newFile.setReadable(true, false);
                    }
                }

                extractedEntries++;

                if (callback != null) {
                    int progress = Math.min((int) ((extractedEntries * 100.0) / estimatedTotalEntries), 99);
                    if (progress != lastReportedProgress) {
                        lastReportedProgress = progress;
                        callback.onProgress(progress);
                    }
                }

                zis.closeEntry();
            }

            Log.d(TAG, "Extracted " + extractedEntries + " entries from ZIP");
        }
    }

    private static File newFile(File destDir, ZipEntry entry) throws IOException {
        File destFile = new File(destDir, entry.getName());

        String destDirPath = destDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry outside target directory: " + entry.getName());
        }

        return destFile;
    }

    public static boolean isRootFSReady(Context context) {
        File rootfsDir = new File(context.getFilesDir().getParentFile(), "files/rootfs");
        return rootfsDir.exists() && isJavaValid(rootfsDir);
    }

    public static File getRootFSDir(Context context) {
        return new File(context.getFilesDir().getParentFile(), "files/rootfs");
    }

    private static boolean isJavaValid(File rootfsDir) {
        File javaBin = findJavaBinary(rootfsDir);
        return javaBin.exists() && javaBin.length() > 0;
    }

    private static File findJavaBinary(File rootfsDir) {
        File javaBin = new File(rootfsDir, "bin/java");
        if (javaBin.exists() && javaBin.length() > 0) {
            return javaBin;
        }

        javaBin = new File(rootfsDir, "usr/lib/jvm/java-17-openjdk/bin/java");
        if (javaBin.exists() && javaBin.length() > 0) {
            return javaBin;
        }

        javaBin = new File(rootfsDir, "usr/lib/jvm/openjdk-17/bin/java");
        if (javaBin.exists() && javaBin.length() > 0) {
            return javaBin;
        }

        javaBin = new File(rootfsDir, "usr/bin/java");
        return javaBin;
    }

    private static void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDir(file);
                }
            }
        }
        dir.delete();
    }
}
