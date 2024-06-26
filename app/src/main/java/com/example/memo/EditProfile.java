package com.example.memo;

import static androidx.core.content.PackageManagerCompat.LOG_TAG;

import static com.example.memo.MainActivity.uri_s;
import static com.example.memo.Registration.getToken;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditProfile extends AppCompatActivity {
    private static AppDatabase db;
    private UserDao userDao;
    static final int SELECT_IMAGE_REQUEST = 1;
    private static String authToken;
    ImageButton cancelButton, selectButton, setNameButton, setPwdButton, setSignButton, setAvatarButton;
    EditText editName, oldPwd, newPwd, editSign;
    TextView IDView;
    CircularImageView iconView;
    String userName, userID, password, signature, newAvatarPath = "Old";
    Parcelable iconUrl;
    ExecutorService executorService;
    File dir;

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        this.cancelButton = findViewById(R.id.cancel_button);
        this.selectButton = findViewById(R.id.select_icon_button);
        this.setNameButton = findViewById(R.id.setname_button);
        this.setPwdButton = findViewById(R.id.setpwd_button);
        this.setSignButton = findViewById(R.id.setsign_button);
        this.setAvatarButton = findViewById(R.id.setavatar_button);
        this.IDView = findViewById(R.id.user_id);
        this.editName = findViewById(R.id.name);
        this.oldPwd = findViewById(R.id.old_password);
        this.newPwd = findViewById(R.id.new_password);
        this.editSign = findViewById(R.id.signature);

        this.dir = EditProfile.this.getFilesDir();
        if (!dir.exists()) {
            // 创建文件夹
            boolean isDirCreated = dir.mkdir();
            if (isDirCreated) {
                Log.d("Directory", "Created Successfully");
            } else {
                Log.d("Directory", "Already Exists");
            }
        }

        db = MemoPlus.getInstance().getAppDatabase();
        userDao = db.userDao();

        executorService = Executors.newFixedThreadPool(1);

        initializeInfo();

        cancelButton.setOnClickListener(v -> {
            Log.d(LOG_TAG, "Cancel button clicked!");
            finish();
        });

        setNameButton.setOnClickListener(v -> {
            executorService.submit(() -> {
                sendPOST_changeUsername(userID, editName.getText().toString(), new OnHttpCallback(){
                    @Override
                    public void onSuccess(String feedBack) {
                        if (Objects.equals(feedBack, "Success")) {
                            userDao.updateUsername(userID, editName.getText().toString());
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> {Toast.makeText(EditProfile.this, "OK", Toast.LENGTH_SHORT).show();});
                        }
                    }
                    @Override
                    public void onFailure(Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        });

        setPwdButton.setOnClickListener(v -> {
            String newPassword = newPwd.getText().toString();
            if (!password.equals(newPassword)) {
                executorService.submit(() -> {
                    sendPOST_changePassword(userID, password, newPassword, new OnHttpCallback(){
                        @Override
                        public void onSuccess(String feedBack) {
                            if (Objects.equals(feedBack, "Success")) {
                                userDao.updatePassword(userID, newPassword);
                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(() -> {Toast.makeText(EditProfile.this, "OK", Toast.LENGTH_SHORT).show();});
                            }
                        }
                        @Override
                        public void onFailure(Exception e) {
                            e.printStackTrace();
                        }
                    });
                });
            } else {
                newPwd.setError("Same!");
                newPwd.requestFocus();
            }
        });

        setSignButton.setOnClickListener(v -> {
            executorService.submit(() -> {
                sendPOST_changePersonalSignature(userID, editSign.getText().toString(), new OnHttpCallback(){
                    @Override
                    public void onSuccess(String feedBack) {
                        if (Objects.equals(feedBack, "Success")) {
                            userDao.updateSignature(userID, editSign.getText().toString());
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(() -> {Toast.makeText(EditProfile.this, "OK", Toast.LENGTH_SHORT).show();});
                        }
                    }
                    @Override
                    public void onFailure(Exception e) {
                        e.printStackTrace();
                    }
                });
            });
        });

        setAvatarButton.setOnClickListener(v -> {
            if (!Objects.equals(newAvatarPath, "Old")) {
                executorService.submit(() -> {
                    sendPOST_changeAvatar(userID, newAvatarPath, new OnHttpCallback(){
                        @Override
                        public void onSuccess(String feedBack) {
                            if (Objects.equals(feedBack, "Success")) {
                                User syujin = userDao.getAllUsers().get(0);
                                syujin.avatar = "Check";
                                userDao.updateUser(syujin);
                                Handler handler = new Handler(Looper.getMainLooper());
                                handler.post(() -> {Toast.makeText(EditProfile.this, "OK", Toast.LENGTH_SHORT).show();});
                            }
                        }
                        @Override
                        public void onFailure(Exception e) {e.printStackTrace();}
                    });
                });
            }
        });
    }

    private void initializeInfo() {
        executorService.submit(() -> {
            User user = userDao.getAllUsers().get(0);
            authToken = user.token;
            password = user.password;
            userID = user.userID;
            signature = user.signature;
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                editName.setText(user.username);
                IDView.setText(user.userID);
                oldPwd.setText(user.password);
                editSign.setText(user.signature);
            });
        });
    }

    public static void sendPOST_changeUsername(String userID, String newName, OnHttpCallback callback) {
        try {
            String feedBack = performChangeUsername(userID, newName); // 假设这是获取到的 userID
            callback.onSuccess(feedBack);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    private static String performChangeUsername(String userID, String newName) throws IOException, JSONException {
        Log.d("change-name", newName);
        URI uri = null;
        try {
            uri = new URI( uri_s + "changeUsername");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return "";
        }
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", authToken);

        JSONObject jsonInputString = new JSONObject();
        jsonInputString.put("userID", userID);
        jsonInputString.put("newUsername", newName);

        try(OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
                Log.d("change-name", response.toString());
                return "Success";
            }
        } else {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Error: " + response.toString());
            }
        }
        return "Failed";
    }

    public static void sendPOST_changePassword(String userID, String oldPassword, String newPassword, OnHttpCallback callback) {
        try {
            String feedBack = performChangePassword(userID, oldPassword, newPassword); // 假设这是获取到的 userID
            callback.onSuccess(feedBack);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    private static String performChangePassword(String userID, String oldPassword, String newPassword) throws IOException, JSONException {
        Log.d("change-pwd", oldPassword);
        URI uri = null;
        try {
            uri = new URI( uri_s + "changePassword");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return "";
        }
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", authToken);

        JSONObject jsonInputString = new JSONObject();
        jsonInputString.put("userID", userID);
        jsonInputString.put("oldPassword", oldPassword);
        jsonInputString.put("newPassword", newPassword);

        try(OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
                return "Success";
            }
        } else {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Error: " + response.toString());
            }
        }
        return "Failed";
    }

    public static void sendPOST_changePersonalSignature(String userID, String newSignature, OnHttpCallback callback) {
        try {
            String feedBack = performChangeSignature(userID, newSignature); // 假设这是获取到的 userID
            callback.onSuccess(feedBack);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    private static String performChangeSignature(String userID, String newSignature) throws IOException, JSONException {
        URI uri = null;
        try {
            uri = new URI( uri_s + "changePersonalSignature");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return "";
        }
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Authorization", authToken);

        JSONObject jsonInputString = new JSONObject();
        jsonInputString.put("userID", userID);
        jsonInputString.put("newPersonalSignature", newSignature);

        try(OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
                return "Success";
            }
        } else {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Error: " + response.toString());
            }
        }
        return "Failed";
    }

    public static void sendPOST_changeAvatar(String userID, String filePath, OnHttpCallback callback) {
        try {
            String feedBack = performChangeAvatar(userID, filePath); // 假设这是获取到的 userID
            callback.onSuccess(feedBack);
        } catch (Exception e) {
            callback.onFailure(e);
        }
    }

    private static String performChangeAvatar(String userID, String filePath) throws IOException, JSONException {
        File newAvatar = new File(filePath);
        if (!newAvatar.exists()){
            System.out.println("Avatar does not exist");
            return "Failed";
        }

        URI uri = null;
        try {
            uri = new URI(uri_s + "changeAvatar");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return "Failed";
        }
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);
        conn.setRequestProperty("Authorization", authToken);

        JSONObject jsonInputString = new JSONObject();
        jsonInputString.put("userID", userID);
        String boundary = Long.toHexString(System.currentTimeMillis());
        conn.setRequestProperty("Content-Type", "multipart/form-data; charset=utf-8; boundary=" + boundary);

        try (OutputStream output = conn.getOutputStream(); PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, "UTF-8"), true)) {
            // Send JSON data.
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"json\"").append("\r\n");
            writer.append("Content-Type: application/json; charset=UTF-8").append("\r\n");
            writer.append("\r\n");
            writer.append(jsonInputString.toString()).append("\r\n").flush();

            // Send binary file.
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"newAvatar\"; filename=\"").append(newAvatar.getName()).append("\"").append("\r\n");
            writer.append("Content-Type: ").append(URLConnection.guessContentTypeFromName(newAvatar.getName())).append("\r\n");
            writer.append("Content-Transfer-Encoding: binary").append("\r\n");
            writer.append("\r\n").flush();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.copy(newAvatar.toPath(), output);
            }
            output.flush(); // Important before continuing with writer!
            writer.append("\r\n").flush(); // CRLF is important! It indicates end of binary boundary.

            // End of multipart/form-data.
            writer.append("--").append(boundary).append("--").append("\r\n").flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println(response.toString());
            }
            return "Success";
        } else {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String responseLine = null;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                System.out.println("Error: " + response.toString());
            }
        }
        return "Failed";
    }

    public void selectIcon(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, SELECT_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        iconView = findViewById(R.id.select_icon);
        if (requestCode == SELECT_IMAGE_REQUEST && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                iconView.setImageURI(selectedImageUri);
                if (selectedImageUri != null) {
                    // 保存照片到文件中
                    newAvatarPath = saveImageToDirectory(selectedImageUri, dir);
                }
            }
        }
    }

    private String saveImageToDirectory(Uri imageUri, File directory) {
        if (imageUri == null) return null;
        File outputFile = null;
        try {
            ContentResolver contentResolver = getContentResolver();
            // 从URI获取输入流
            InputStream inputStream = contentResolver.openInputStream(imageUri);
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            outputFile = new File(directory,  "avatar-" + timeStamp + ".jpg");
            assert inputStream != null;
            writeFile(inputStream, outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputFile.getPath();
    }

    private void writeFile(InputStream inputStream, File outputFile) throws IOException {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.flush();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

}
