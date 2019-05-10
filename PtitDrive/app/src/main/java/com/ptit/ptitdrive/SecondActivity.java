package com.ptit.ptitdrive;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.CreateFileActivityOptions;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.nbsp.materialfilepicker.MaterialFilePicker;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SecondActivity extends AppCompatActivity {
    private ImageView img_camera, img_gallery, img_file;
    private TextView tv_name_2;
    private static final String TAG = "drive-quickstart";
    private static final int REQUEST_CODE_SIGN_IN = 0;
    private static final int REQUEST_CODE_CAPTURE_IMAGE = 1;
    private static final int REQUEST_CODE_PICK_IMAGE = 2;
    private static final int REQUEST_CODE_PICK_FILE = 3;
    private static final int REQUEST_CODE_IMAGE_CREATOR = 10;
    private static final int REQUEST_CODE_FILE_CREATOR = 11;
    private static final int REQUEST_CODE_CAMERA = 12;
    private static final int REQUEST_CODE_GALLERY = 13;
    private static final int REQUEST_CODE_FILE = 14;
    private DriveClient mDriveClient;
    private DriveResourceClient mDriveResourceClient;
    private Bitmap mBitmapToSave;
    private GoogleApiClient mGoogleApiClient;
    protected static final int REQUEST_CODE_RESOLUTION = 1337;
    private String FOLDER_NAME = "xTests6";
    private String filePath;
    static final int BUFF_SIZE = 2048;
    static final String DEFAULT_ENCODING = "utf-8";

    String mCurrentPhotoPath;

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        mDriveClient = Drive.getDriveClient(this, GoogleSignIn.getLastSignedInAccount(this));

        // Build a drive resource client.
        mDriveResourceClient =
                Drive.getDriveResourceClient(this, GoogleSignIn.getLastSignedInAccount(this));

        tv_name_2 = findViewById(R.id.tv_name_2);
        img_camera = (ImageView) findViewById(R.id.camera);
        img_gallery = (ImageView) findViewById(R.id.gallery);
        img_file = (ImageView) findViewById(R.id.file);
        tv_name_2.setText("Hello " + GoogleSignIn.getLastSignedInAccount(this).getDisplayName());

        img_camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(SecondActivity.this,
                        new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
            }
        });

        img_gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(SecondActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_GALLERY);
            }
        });

        img_file.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(SecondActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_FILE);
            }
        });
    }


    private void saveImageToDrive() {
        // Start by creating a new contents, and setting a callback.
        Log.i(TAG, "Creating new contents.");
        final Bitmap image = mBitmapToSave;

        mDriveResourceClient
                .createContents()
                .continueWithTask(
                        new Continuation<DriveContents, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<DriveContents> task) throws Exception {
                                return SecondActivity.this.createImageIntentSender(task.getResult(), image, null);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Failed to create new contents.", e);
                            }
                        });
    }

    private void saveFileToDrive() {
        // Start by creating a new contents, and setting a callback.
        Log.i(TAG, "Creating new contents.");
        //            final String file = readFileToString(filePath,"");
        final File file = new File(filePath);
        mDriveResourceClient
                .createContents()
                .continueWithTask(
                        new Continuation<DriveContents, Task<Void>>() {
                            @Override
                            public Task<Void> then(@NonNull Task<DriveContents> task) throws Exception {
                                return SecondActivity.this.createFileIntentSender(task.getResult(), file);
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.w(TAG, "Failed to create new contents.", e);
                            }
                        });
    }

    private Task<Void> createImageIntentSender(DriveContents driveContents, Bitmap image, String fileName) {
        Log.i(TAG, "New contents created.");
        // Get an output stream for the contents.
        OutputStream outputStream = driveContents.getOutputStream();
        // Write the bitmap data from it.
        ByteArrayOutputStream bitmapStream = new ByteArrayOutputStream();
        String ext = null;
        if (fileName != null) {
            String[] tmp = fileName.split(".");
            ext = tmp[tmp.length - 1];
        }
        if (ext != null) {
            image.compress(Bitmap.CompressFormat.valueOf(ext), 100, bitmapStream);
        } else
            image.compress(Bitmap.CompressFormat.JPEG, 100, bitmapStream);
        try {
            outputStream.write(bitmapStream.toByteArray());
        } catch (IOException e) {
            Log.w(TAG, "Unable to write file contents.", e);
        }

        // Create the initial metadata - MIME type and title.
        // Note that the user will be able to change the title later.
        MetadataChangeSet metadataChangeSet =
                new MetadataChangeSet.Builder()
                        .setMimeType("image/"+(ext==null?"*":ext))
                        .setTitle(fileName==null?"image.jpg":fileName)
                        .build();
        // Set up options to configure and display the create file activity.
        CreateFileActivityOptions createFileActivityOptions =
                new CreateFileActivityOptions.Builder()
                        .setInitialMetadata(metadataChangeSet)
                        .setInitialDriveContents(driveContents)
                        .build();

        return mDriveClient
                .newCreateFileActivityIntentSender(createFileActivityOptions)
                .continueWith(
                        new Continuation<IntentSender, Void>() {
                            @Override
                            public Void then(@NonNull Task<IntentSender> task) throws Exception {
                                SecondActivity.this.startIntentSenderForResult(task.getResult(), REQUEST_CODE_IMAGE_CREATOR, null, 0, 0, 0);
                                return null;
                            }
                        });
    }

    private Task<Void> createFileIntentSender(DriveContents driveContents, File file) {
        Log.i(TAG, "New contents created.");
        try {
//            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(this.openFileOutput("config.txt", Context.MODE_PRIVATE));
            OutputStream outputStream = driveContents.getOutputStream();
//            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(outputStream));
//            bw.write(file);
            FileInputStream fis = new FileInputStream(file);
            byte[] fileByte = new byte[255];
            while (fis.read(fileByte) > 0) {
//                fis.read(fileByte);
//            ByteArrayOutputStream fileArray = new ByteArrayOutputStream();
//            fileArray.write(fileByte);
                outputStream.write(fileByte);
                fileByte = new byte[255];
            }
//            bw.close();
            fis.close();
            outputStream.close();
        } catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
//        try {
//            File file1 = new File(Environment.getExternalStorageDirectory() + "/test.txt");
//
//            if (!file1.exists()) {
//                file1.createNewFile();
//            }
//            FileWriter writer = new FileWriter(file);
//            writer.append(file);
//            writer.flush();
//            writer.close();
//        } catch (IOException e) {
//        }
//        Log.d("abc",file);
//        com.google.api.services.drive.model.File fileTest = driveContents
        MetadataChangeSet metadataChangeSet =
                new MetadataChangeSet.Builder()
                        .setMimeType("application/pdf")
                        .setTitle(file.getName())
                        .build();
        CreateFileActivityOptions createFileActivityOptions =
                new CreateFileActivityOptions.Builder()
                        .setInitialMetadata(metadataChangeSet)
                        .setInitialDriveContents(driveContents)
                        .build();

        return mDriveClient
                .newCreateFileActivityIntentSender(createFileActivityOptions)
                .continueWith(
                        new Continuation<IntentSender, Void>() {
                            @Override
                            public Void then(@NonNull Task<IntentSender> task) throws Exception {
                                SecondActivity.this.startIntentSenderForResult(task.getResult(), REQUEST_CODE_FILE_CREATOR, null, 0, 0, 0);
                                return null;
                            }
                        });
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_CAMERA:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    startActivityForResult(
//                            new Intent(MediaStore.ACTION_IMAGE_CAPTURE), REQUEST_CODE_CAPTURE_IMAGE);
                    // Create the File where the photo should go
                    Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    // Ensure that there's a camera activity to handle the intent
                    if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                        // Create the File where the photo should go
                        File photoFile = null;
                        try {
                            photoFile = createImageFile();
                        } catch (IOException ex) {
                            // Error occurred while creating the File
                        }
                        // Continue only if the File was successfully created
                        if (photoFile != null) {
                            Uri photoURI = FileProvider.getUriForFile(this,
                                    "com.ptit.ptitdrive.fileprovider",
                                    photoFile);
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                            startActivityForResult(takePictureIntent, REQUEST_CODE_CAPTURE_IMAGE);
                        }
                    }
                } else {
                    Toast.makeText(SecondActivity.this, "Don't open camera.", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_CODE_GALLERY:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setType("image/*");
                    startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE);
                } else {
                    Toast.makeText(SecondActivity.this, "Don't open gallery.", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_CODE_FILE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
////                    intent.setType("file/*");
////                    //intent.setType("application/pdf");
////                    startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
                    new MaterialFilePicker()
                            .withActivity(this)
                            .withRequestCode(REQUEST_CODE_PICK_FILE)
//                            .withFilter(Pattern.compile(".*\\.txt$")) // Filtering files and directories by file name using regexp
//                            .withFilterDirectories(true) // Set directories filterable (false by default)
                            .withHiddenFiles(true) // Show hidden files and folders
                            .start();
                }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_CAPTURE_IMAGE:
                Log.i(TAG, "capture image request code");
                // Called after a photo has been taken.
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Image captured successfully.");
                    // Store the image data as a bitmap for writing later.
//                    mBitmapToSave = (Bitmap) data.getExtras().get("data");
                    File file = new File(mCurrentPhotoPath);
                    try {
                        mBitmapToSave = MediaStore.Images.Media
                                .getBitmap(this.getContentResolver(), Uri.fromFile(file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (mBitmapToSave != null) {
                    }
                    saveImageToDrive();
                }
                break;
            case REQUEST_CODE_PICK_IMAGE:
                Log.i(TAG, "pick image request code");
                // Called after a photo has been taken.
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "Image picked successfully.");
                    // Store the image data as a bitmap for writing later.
                    Uri uri = data.getData();
                    Log.d("truong", uri.toString());
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        mBitmapToSave = BitmapFactory.decodeStream(inputStream);
                        saveImageToDrive();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
                break;
            case REQUEST_CODE_PICK_FILE:
                Log.i(TAG, "file request code");
                // Called after a photo has been taken.
                if (resultCode == Activity.RESULT_OK) {
                    Log.i(TAG, "file picked successfully.");
                    // Store the image data as a bitmap for writing later.
                    filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
                    Log.i(TAG, filePath);
                    saveFileToDrive();
                }
                break;
            case REQUEST_CODE_IMAGE_CREATOR:
                Log.i(TAG, "creator request code");
                // Called after a file is saved to Drive.
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "Image successfully saved.");
                    Toast.makeText(SecondActivity.this, "successfully", Toast.LENGTH_SHORT).show();
                    mBitmapToSave = null;
                }
                break;
            case REQUEST_CODE_FILE_CREATOR:
                Log.i(TAG, "creator request code");
                // Called after a file is saved to Drive.
                if (resultCode == RESULT_OK) {
                    Log.i(TAG, "File successfully saved.");
                    Toast.makeText(SecondActivity.this, "successfully", Toast.LENGTH_SHORT).show();
                    filePath = null;
                }
                break;

        }
    }


}
