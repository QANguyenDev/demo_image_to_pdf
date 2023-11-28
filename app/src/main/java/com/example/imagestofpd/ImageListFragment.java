package com.example.imagestofpd;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.imagestofpd.adapters.AdapterImage;
import com.example.imagestofpd.models.ModelImage;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ImageListFragment extends Fragment {

    private static final String TAG = "IMAGE_LIST_TAG";

    private static final int STORAGE_REQUEST_CODE = 100;
    private static final int CAMERA_REQUEST_CODE = 101;

    private String[] cameraPermission;
    private String[] storagePermission;

    private Uri imageUri = null;

    private FloatingActionButton addImageFab;

    private RecyclerView imagesRv;

//    private FloatingActionButton addImagFab;

    private ArrayList<ModelImage> allImageArrayList;

    private AdapterImage adapterImage;

    private ProgressDialog progressDialog;

    private Context mContext;

    public ImageListFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        mContext = context;
        super.onAttach(context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_image_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        cameraPermission = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};

        addImageFab = view.findViewById(R.id.addImageFab);
        imagesRv = view.findViewById(R.id.imagesRv);

        progressDialog = new ProgressDialog(mContext);
        progressDialog.setTitle("Please wait");
        progressDialog.setCanceledOnTouchOutside(false);

        loadImages();

        addImageFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showInputImageDialog();
            }
        });

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_images, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int itemId = item.getItemId();
        if (itemId == R.id.images_item_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("Delete Images")
                    .setMessage("Are you sure want to delete All/Selected images?")
                    .setPositiveButton("Delete All", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            deleteImages(true);
                        }
                    })
                    .setNeutralButton("Delete Selected", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            deleteImages(false);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.dismiss();
                        }
                    })
                    .show();
        } else if (itemId == R.id.images_item_pdf) {
            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
            builder.setTitle("Convert To Pdf")
                    .setMessage("Convert All/Selected Images to PDF")
                    .setPositiveButton("CONVERT ALL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            convertImagesToPdf(true);
                        }
                    })
                    .setNeutralButton("CONVERT SELECTED", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            convertImagesToPdf(false);
                        }
                    })
                    .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            convertImagesToPdf(false);
                        }
                    })
                    .show();
        }

        return super.onOptionsItemSelected(item);
    }

    private void convertImagesToPdf(boolean convertAll) {
        Log.d(TAG, "convertImagesToPdf: convertAll: "+ convertAll);

        progressDialog.setMessage("Converting to PDF...");
        progressDialog.show();

        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Handler handler = new Handler(Looper.getMainLooper());

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "run: BG work start....");

                ArrayList<ModelImage> imageToPdfList = new ArrayList<>();

                if (convertAll) {
                    imageToPdfList = allImageArrayList;
                } else {
                    for (int i = 0; i < allImageArrayList.size(); i++) {
                        if (allImageArrayList.get(i).isChecked()) {
                            imageToPdfList.add(allImageArrayList.get(i));
                        }
                    }
                }
                Log.d(TAG, "run: imageToPdfList size: " + imageToPdfList.size());

                try {
                    //1. Create folder where we will save th pdf
                    File root = new File(mContext.getExternalFilesDir(null), Constants.PDF_FOLDER);
                    root.mkdirs();
                    //2. Name with extension of the image
                    long timestamp = System.currentTimeMillis();
                    String fileName = "PDF_" + timestamp + ".pdf";

                    Log.d(TAG, "run: fileName: " + fileName);

                    File file = new File(root, fileName);

                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    PdfDocument pdfDocument = new PdfDocument();

                    for (int i = 0; i < imageToPdfList.size(); i++) {
                        Uri imageToAdInPdfUri = imageToPdfList.get(i).getImageUri();

                        try {
                            Bitmap bitmap;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(mContext.getContentResolver(), imageToAdInPdfUri));

                            } else {
                                bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), imageToAdInPdfUri);
                            }

                            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);

                            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bitmap.getWidth(), bitmap.getHeight(), i+1).create();

                            PdfDocument.Page page = pdfDocument.startPage(pageInfo);

                            Paint paint = new Paint();
                            paint.setColor(Color.WHITE);

                            Canvas canvas = page.getCanvas();
                            canvas.drawPaint(paint);
                            canvas.drawBitmap(bitmap, 0f, 0f, null);

                            pdfDocument.finishPage(page);

                            bitmap.recycle();

                        } catch (Exception e) {
                            Log.e(TAG, "run: ", e);
                        }
                    }

                    pdfDocument.writeTo(fileOutputStream);
                    pdfDocument.close();

                } catch (Exception e) {
                    //Toast.makeText(mContext, ""+e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressDialog.dismiss();
                    Log.e(TAG, "run: ", e);
                }

                //
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "run: Converted...");
                        progressDialog.dismiss();
                        Toast.makeText(mContext, "Converted...", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        });
    }

    private void deleteImages(boolean deleteAll){
        ArrayList<ModelImage> imagesToDeleteList = new ArrayList<>();
        if (deleteAll) {
            imagesToDeleteList = allImageArrayList;
        } else {
            for (int i = 0; i < allImageArrayList.size(); i++) {
                if (allImageArrayList.get(i).isChecked()) {
                    imagesToDeleteList.add(allImageArrayList.get(i));
                }
            }

//            for (int i = 0; i < imagesToDeleteList.size(); i++) {
//
//                try {
//                    String pathOfImageToDelete = imagesToDeleteList.get(i).getImageUri().getPath();
//
//                    File file = new File(pathOfImageToDelete);
//
//                    boolean isDeleted = file.delete();
//
//                    Log.d(TAG, "deleteImages: isDeleted: "+isDeleted);
//                } catch (Exception e) {
//                    Log.d(TAG, "deleteImages: ", e);
//                }
//
//            }
//
//            Toast.makeText(mContext, "Deleted", Toast.LENGTH_SHORT).show();
//
//            loadImages();
        }

        for (int i = 0; i < imagesToDeleteList.size(); i++) {

            try {
                String pathOfImageToDelete = imagesToDeleteList.get(i).getImageUri().getPath();

                File file = new File(pathOfImageToDelete);
                if (file.exists()) {
                    boolean isDeleted = file.delete();

                    Log.d(TAG, "deleteImages: isDeleted: "+isDeleted);
                }

            } catch (Exception e) {
                Log.d(TAG, "deleteImages: ", e);
            }

        }

        Toast.makeText(mContext, "Deleted", Toast.LENGTH_SHORT).show();

        loadImages();

    }

    private void loadImages(){
        Log.d(TAG, "loadImages: ");

        allImageArrayList = new ArrayList<>();
        adapterImage = new AdapterImage(mContext, allImageArrayList);
        //set adapter
        imagesRv.setAdapter(adapterImage);
        File folder = new File(mContext.getExternalFilesDir(null), Constants.IMAGES_FOLDER);

        if (folder.exists()) {
            Log.d(TAG, "loadImages: folder exists");
            File[] files = folder.listFiles();
            if (files != null) {
                Log.d(TAG, "loadImages: Folder exists and have images");

                for (File file: files) {
                    Log.d(TAG, "loadImages: fileName: " + file.getName());

                    Uri imageUri = Uri.fromFile(file);

                    ModelImage modelImage = new ModelImage(imageUri, false);
                    
                    allImageArrayList.add(modelImage);
                    adapterImage.notifyItemInserted(allImageArrayList.size());
                }
            } else {
                Log.d(TAG, "loadImages: loadImages: Folder exists but empty");
            }
        } else {
            Log.d(TAG, "loadImages: Folder doesn't exists");
        }
    }

    private void saveImageToAppLevelDirectory(Uri imageUriToBeSaved) {
        Log.d(TAG, "saveImageToAppLevelDirectory: ");
        try {
            //1. get Bitmap from imageUri
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
                //Method to get bitmap from uri in API 28 (P) and above
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(mContext.getContentResolver(), imageUriToBeSaved));
            } else {
                //Method to get bitmap from uri below API 28 (P)
                bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), imageUriToBeSaved);
            }
            //2. Creat folder where we will save the image, doesn't requires any storage permission, and is not accessible by any other app
            File directory = new File(mContext.getExternalFilesDir(null), Constants.IMAGES_FOLDER);
            directory.mkdirs();
            //3. Name with extension of the image
            long timestamp = System.currentTimeMillis();
            String fileName = timestamp+ ".jpeg";
            //4. Sub Folder and file name to be saved
            File file = new File(mContext.getExternalFilesDir(null), ""+Constants.IMAGES_FOLDER +"/" + fileName);
            //5. Save image
            try {
                FileOutputStream fos = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                fos.flush();
                fos.close();
                Log.d(TAG, "saveImageToAppLevelDirectory: Image Saved");
                Toast.makeText(mContext, "Image Saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "saveImageToAppLevelDirectory: ", e);
                Log.d(TAG, "saveImageToAppLevelDirectory: Failed to save image due to "+ e.getMessage());
                Toast.makeText(mContext, "Failed to save image due to "+e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "saveImageToAppLevelDirectory: ", e);
            Log.d(TAG, "saveImageToAppLevelDirectory: Failed to prepare image due to "+ e.getMessage());
            Toast.makeText(mContext, "Failed to prepare image due to "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showInputImageDialog(){
        Log.d(TAG, "showInputImageDialog: ");
        PopupMenu popupMenu = new PopupMenu(mContext, addImageFab);

        popupMenu.getMenu().add(Menu.NONE, 1, 1, "CAMERA");
        popupMenu.getMenu().add(Menu.NONE, 2, 2, "GALLERY");

        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {

                int itemId = menuItem.getItemId();
                if (itemId == 1) {
                    //Camera is clicked, check if camera permission are granted or not
                    Log.d(TAG, "onMenuItemClick: Camera is clicked, check if camera permission are granted or not");
                    if (checkCameraPermissions()) {
                        pickImageCamera();
                    } else {
                        requestCameraPermissions();
                    }

                } else if (itemId == 2) {
                    //Gallery is clicked, check if storage permission is granted or not
                    Log.d(TAG, "onMenuItemClick: Gallery is clicked, check if storage permission is granted or not");
                    if (checkStoragePermission()) {
                        pickImageGallery();
                    } else {
                        requestStoragePermission();
                    }

                }

                return true;
            }
        });

    }

    private void pickImageGallery(){
        Log.d(TAG, "pickImageGallery: ");
        Intent intent = new Intent(Intent.ACTION_PICK);

        intent.setType("image/*");
        galleryActivityResultLauncher.launch(intent);
    }

    private ActivityResultLauncher<Intent> galleryActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult o) {
                    //here we will receive the image, if picked
                    if (o.getResultCode() == Activity.RESULT_OK) {
                        //image picked
                        Intent data = o.getData();
                        //get uri of the image picked
                        imageUri = data.getData();
                        Log.d(TAG, "onActivityResult: Picked image gallery: "+ imageUri);
                        //save the picked image
                        saveImageToAppLevelDirectory(imageUri);

                        ModelImage modelImage = new ModelImage(imageUri, false);
                        allImageArrayList.add(modelImage);
                        adapterImage.notifyItemInserted(allImageArrayList.size());
                    } else {
                        //Cancelled
                        Toast.makeText(mContext, "Cancelled...", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void pickImageCamera(){
        Log.d(TAG, "pickImageCamera: ");
        //get ready the image data to store in MediaStore
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.Images.Media.TITLE, "TEMP IMAGE TITLE");
        contentValues.put(MediaStore.Images.Media.DESCRIPTION, "TEMP IMAGE DESCRIPTION");
        //store the camera image in imageUri variable
        imageUri = mContext.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        //Intent to launcher camera
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraActivityResultLauncher.launch(intent);

    }

    private ActivityResultLauncher<Intent> cameraActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult o) {
                    //here we will receive the image, if taken from camera
                    if (o.getResultCode() == Activity.RESULT_OK) {
                        //image is taken from camera
                        //we already have the image in imageUri using function pickImageCamera()
                        //save the picked image
                        Log.d(TAG, "onActivityResult: Picked image camera: "+ imageUri);
                        saveImageToAppLevelDirectory(imageUri);

                        ModelImage modelImage = new ModelImage(imageUri, false);
                        allImageArrayList.add(modelImage);
                        adapterImage.notifyItemInserted(allImageArrayList.size());
                    } else {
                        //cancelled
                        Toast.makeText(mContext, "Cancelled...", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private boolean checkStoragePermission(){
        Log.d(TAG, "checkStoragePermission: ");
        /*
        check if storage permission is allowed or not
        return true if allowed, false if not allowed
         */
        boolean result = ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        return result;

    }

    private void requestStoragePermission(){
        Log.d(TAG, "requestStoragePermission: ");
        requestPermissions(storagePermission, STORAGE_REQUEST_CODE);

    }

    private boolean checkCameraPermissions(){
        Log.d(TAG, "checkCameraPermission: ");
        boolean cameraResult = ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean storageResult = ContextCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        return cameraResult && storageResult;
    }

    private void requestCameraPermissions(){
        Log.d(TAG, "requestCameraPermissions: ");
        requestPermissions(cameraPermission, CAMERA_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_REQUEST_CODE) {
            //check if some action from permission dialog performed or not Allow/Deny
            if (grantResults.length > 0) {
                //check if camera, Storage permission granted, contains boolean result either true or false
                boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean storageAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                //check if both permissions are granted or not
                if (cameraAccepted && storageAccepted){
                    //both permissions (Camera & Gallery) are granted, we can lenucher camera intent
                    Log.d(TAG, "onRequestPermissionsResult: both permissions (Camera & Gallery) are granted, we can lenucher camera intent");
                    pickImageCamera();
                } else {
                    Log.d(TAG, "onRequestPermissionsResult: Camera & Storage permission are required");
                    Toast.makeText(mContext, "Camera & Storage permission are required", Toast.LENGTH_SHORT).show();
                }

            } else {
                Log.d(TAG, "onRequestPermissionsResult: Cancelled");
                Toast.makeText(mContext, "Cancelled...", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0) {

                boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                if (storageAccepted) {
                    //storage permission granted, we can launch gallery intent
                    Log.d(TAG, "onRequestPermissionsResult: storage permission granted, we can launch gallery intent");
                    pickImageGallery();
                } else {
                    //storage permission denied, can't launch gallery intent
                    Log.d(TAG, "onRequestPermissionsResult: storage permission denied, can't launch gallery intent");
                    Toast.makeText(mContext, "storage permission is required", Toast.LENGTH_SHORT).show();
                }

            } else {
                Log.d(TAG, "onRequestPermissionsResult: Cancelled");
                Toast.makeText(mContext, "Cancelled", Toast.LENGTH_SHORT).show();
            }
        }

    }
}