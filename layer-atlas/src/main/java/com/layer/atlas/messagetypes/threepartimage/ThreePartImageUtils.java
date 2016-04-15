package com.layer.atlas.messagetypes.threepartimage;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;

import com.layer.atlas.util.Log;
import com.layer.atlas.util.Util;
import com.layer.sdk.LayerClient;
import com.layer.sdk.messaging.Message;
import com.layer.sdk.messaging.MessagePart;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

public class ThreePartImageUtils 
{
    private static final String TAG = "KTThreePartImageUtils";

    //PROPERTIES
    //================================================================================
    public static final int ORIENTATION_0 = 0;
    public static final int ORIENTATION_180 = 1;
    public static final int ORIENTATION_90 = 2;
    public static final int ORIENTATION_270 = 3;

    public static final String MIME_TYPE_PREVIEW = "image/jpeg+preview";
    public static final String MIME_TYPE_INFO = "application/json+imageSize";

    public static final int PART_INDEX_FULL = 0;
    public static final int PART_INDEX_PREVIEW = 1;
    public static final int PART_INDEX_INFO = 2;

    public static final int PREVIEW_COMPRESSION_QUALITY = 75;
    public static final int PREVIEW_MAX_WIDTH = 512;
    public static final int PREVIEW_MAX_HEIGHT = 512;

    //MESSAGE PART HELPER
    //================================================================================
    public static MessagePart getInfoPart(Message message)
    {
        return message.getMessageParts().get(PART_INDEX_INFO);
    }
    public static MessagePart getPreviewPart(Message message)
    {
        return message.getMessageParts().get(PART_INDEX_PREVIEW);
    }
    public static MessagePart getFullPart(Message message)
    {
        return message.getMessageParts().get(PART_INDEX_FULL);
    }

    //LIFE CYCLE
    //================================================================================
    public static Message newThreePartImageMessage(Context context, LayerClient layerClient, Uri imageUri) throws IOException
    {
        File imageFile = getImageFile(context, imageUri);
        return newThreePartImageMessage(context, layerClient, imageFile);
    }

    public static Message newThreePartImageMessage(Context context, LayerClient layerClient, File imageFile) throws IOException
    {
        if (imageFile == null) throw new IllegalArgumentException("Null image file");
        if (!imageFile.exists()) throw new IllegalArgumentException("Image file does not exist");
        if (!imageFile.canRead()) throw new IllegalArgumentException("Cannot read image file");
        if (imageFile.length() <= 0) throw new IllegalArgumentException("Image file is empty");

        // Try parsing Exif data.
        int orientation = ORIENTATION_0;
        int exifOrientation = ExifInterface.ORIENTATION_UNDEFINED;
        try
        {
            ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
            exifOrientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);

            DebugLog.v(TAG, "Found Exif orientation: " + exifOrientation);

            switch (exifOrientation)
            {
                case ExifInterface.ORIENTATION_UNDEFINED:
                case ExifInterface.ORIENTATION_NORMAL:
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    orientation = ORIENTATION_0;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    orientation = ORIENTATION_180;
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                case ExifInterface.ORIENTATION_ROTATE_90:
                    orientation = ORIENTATION_270;
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                case ExifInterface.ORIENTATION_ROTATE_270:
                    orientation = ORIENTATION_90;
                    break;
            }
        }
        catch (IOException e)
        {
            DebugLog.e(TAG, e.toString());
        }

        return newThreePartImageMessage(context, layerClient, exifOrientation, orientation, imageFile);
    }

    /**
     * Creates a new ThreePartImage Message.  The full image is attached untouched, while the
     * preview is created from the full image by loading, resizing, and compressing.
     *
     * @param client
     * @param file   Image file
     * @return
     */
    private static Message newThreePartImageMessage(Context context, LayerClient client, int exifOrientation, int orientation, File file) throws IOException
    {
        if (client == null) throw new IllegalArgumentException("Null LayerClient");
        if (file == null) throw new IllegalArgumentException("Null image file");
        if (!file.exists()) throw new IllegalArgumentException("No image file");
        if (!file.canRead()) throw new IllegalArgumentException("Cannot read image file");

        BitmapFactory.Options justBounds = new BitmapFactory.Options();
        justBounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), justBounds);

        int fullWidth = justBounds.outWidth;
        int fullHeight = justBounds.outHeight;
        MessagePart full = client.newMessagePart("image/jpeg", new FileInputStream(file), file.length());

        boolean isSwap = orientation == ORIENTATION_270 || orientation == ORIENTATION_90;
        String intoString = "{\"orientation\":" + orientation + ", \"width\":" + (!isSwap ? fullWidth : fullHeight) + ", \"height\":" + (!isSwap ? fullHeight : fullWidth) + "}";
        MessagePart info = client.newMessagePart(MIME_TYPE_INFO, intoString.getBytes());
        DebugLog.v(TAG, "Creating image info: " + intoString);

        MessagePart preview;
        DebugLog.v(TAG, "Creating Preview from '" + file.getAbsolutePath() + "'");

        // Determine preview size
        int[] previewDim = Util.scaleDownInside(fullWidth, fullHeight, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT);
        DebugLog.v(TAG, "Preview size: " + previewDim[0] + "x" + previewDim[1]);

        // Determine sample size for preview
        int sampleSize = 1;
        int sampleWidth = fullWidth;
        int sampleHeight = fullHeight;
        while (sampleWidth > previewDim[0] && sampleHeight > previewDim[1])
        {
            sampleWidth >>= 1;
            sampleHeight >>= 1;
            sampleSize <<= 1;
        }

        if (sampleSize != 1)
            sampleSize >>= 1; // Back off 1 for scale-down instead of scale-up

        BitmapFactory.Options previewOptions = new BitmapFactory.Options();
        previewOptions.inSampleSize = sampleSize;

        DebugLog.v(TAG, "Preview sampled size: " + (sampleWidth << 1) + "x" + (sampleHeight << 1));

        // Create preview
        Bitmap sampledBitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), previewOptions);
        Bitmap previewBitmap = Bitmap.createScaledBitmap(sampledBitmap, previewDim[0], previewDim[1], true);

        File temp = new File(context.getCacheDir(), KTThreePartImageUtils.class.getSimpleName() + "." + System.nanoTime() + ".jpg");
        FileOutputStream previewStream = new FileOutputStream(temp);
        DebugLog.v(TAG, "Compressing preview to '" + temp.getAbsolutePath() + "'");

        previewBitmap.compress(Bitmap.CompressFormat.JPEG, PREVIEW_COMPRESSION_QUALITY, previewStream);
        previewBitmap.recycle();
        sampledBitmap.recycle();
        previewStream.close();

        // Preserve exif orientation
        ExifInterface preserver = new ExifInterface(temp.getAbsolutePath());
        preserver.setAttribute(ExifInterface.TAG_ORIENTATION, Integer.toString(exifOrientation));
        preserver.saveAttributes();
        DebugLog.v(TAG, "Exif orientation preserved in preview");

        preview = client.newMessagePart(MIME_TYPE_PREVIEW, new FileInputStream(temp), temp.length());
        DebugLog.v(TAG, String.format(Locale.US, "Full image bytes: %d, preview bytes: %d, info bytes: %d", full.getSize(), preview.getSize(), info.getSize()));

        MessagePart[] parts = new MessagePart[3];
        parts[PART_INDEX_FULL] = full;
        parts[PART_INDEX_PREVIEW] = preview;
        parts[PART_INDEX_INFO] = info;
        return client.newMessage(parts);
    }

    private static File getCloudImageFile(Context context, Uri imageUri) throws FileNotFoundException
    {
        File f = new File(context.getCacheDir(), KTThreePartImageUtils.class.getSimpleName() + "." + System.nanoTime() + ".jpg");
        FileOutputStream fileOutStream = new FileOutputStream(f);

        InputStream inStream = context.getContentResolver().openInputStream(imageUri);
        Bitmap bitmap = BitmapFactory.decodeStream(inStream);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutStream);
        return f;
    }

    private static File getImageFile(Context context, Uri imageUri) throws IOException
    {
        Cursor cursor = null;
        try
        {
            String[] projection = { MediaStore.Images.Media.DATA };
            cursor = context.getContentResolver().query(imageUri, projection, null, null, null);

            if (cursor == null || !cursor.moveToFirst() || !imageUri.getHost().equals("media"))
                return getCloudImageFile(context, imageUri);

            // can't get the image
            int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
            if (columnIndex < 0)
                return null;

            String path = cursor.getString(columnIndex);
            File f = new File(path);
            return f;
        }
        finally
        {
            if (cursor != null)
                cursor.close();
        }
    }
}
