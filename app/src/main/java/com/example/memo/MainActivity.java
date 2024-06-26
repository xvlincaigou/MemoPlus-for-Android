package com.example.memo;

import static androidx.core.content.PackageManagerCompat.LOG_TAG;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.LayoutInflater;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE = 1;
    AppDatabase db;
    private UserDao userDao;
    private NoteDao noteDao;
    private static String authToken, userID;
    RecyclerView memoRecyclerView;
    MemoAdapter adapter;
    TextView bottomSum;
    EditText searchBar;
    ImageButton homeButton, addMemo, aiButton, searchButton;
    boolean login = false; // false
    List<MemoItem> MemoList;
    static ExecutorService executorService;
    int maxID = -1;

    public static String uri_s = "http://android.xulincaigou.online:8000/NotepadServer/";

    @SuppressLint({"SetTextI18n", "RestrictedApi", "NotifyDataSetChanged"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = MemoPlus.getInstance().getAppDatabase();
        userDao = db.userDao();
        noteDao = db.noteDao();

        this.memoRecyclerView = findViewById(R.id.recycler_view);
        this.bottomSum = findViewById(R.id.bottom_bar);
        this.homeButton = findViewById(R.id.home_button);
        this.addMemo = findViewById(R.id.add_memo_button);
        this.aiButton = findViewById(R.id.ai_button);
        this.searchBar = findViewById(R.id.search_bar);
        this.searchButton = findViewById(R.id.search_button);
        this.MemoList = new ArrayList<>();

        executorService = Executors.newFixedThreadPool(1);

        aiButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Chat.class);
            intent.putExtra("ID", userID);
            startActivity(intent);
        });

        homeButton.setOnClickListener(v -> {
            Log.d(LOG_TAG, "Home button clicked!");
            Intent intent;
            if(login) intent = new Intent(MainActivity.this, PersonalProfile.class);
            else intent = new Intent(MainActivity.this, Login.class);
            startActivityForResult(intent, REQUEST_CODE);
        });

        searchButton.setOnClickListener(v -> {
            if (login && !searchBar.getText().toString().equals("")) {
                Intent intent = new Intent(MainActivity.this, Search.class);
                intent.putExtra("KEY", searchBar.getText().toString());
                startActivity(intent);
            }
        });

        addMemo.setOnClickListener(v -> {
            if (login) {
                executorService.submit(() -> {
                    Note newNote = new Note();
                    maxID += 1;
                    newNote.title = "New Title " + maxID;
                    newNote.id = maxID;
                    Log.d("title-id-new", String.valueOf(maxID));
                    newNote.type = "Default Type";
                    String timeStamp = new SimpleDateFormat("MM.dd HH:mm").format(new Date());
                    newNote.last_edit = "Newly created at " + timeStamp;
                    newNote.last_save = "Local";
                    newNote.files = new ArrayList<>();
                    noteDao.insertNote(newNote);
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(() -> {
                        MemoItem item = new MemoItem();
                        item.title = newNote.title;
                        item.memo_abstract = "What's going on?";
                        item.edit_time = newNote.last_edit;
                        item.labelNoteID = newNote.id;
                        item.type = newNote.type;
                        MemoList.add(item);
                        adapter.notifyDataSetChanged();
                        bottomSum.setText("Total: " + MemoList.size() + " memos");
                    });
                });
            }
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onResume() {
        super.onResume();
        //if (login) {
            executorService.submit(() -> {
                List<User> users = userDao.getAllUsers();
                if (users != null) {
                    User user = users.get(0);
                    login = true;
                    authToken = user.userID;
                    userID = user.userID;
                }
                if (login) {
                    List<Note> notes = noteDao.getAllNotes();
                    if (notes.isEmpty()) {
                        Log.d("notes", "files list is empty");
                        initializeMain();
                    } else {
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(() -> {
                            fetchFromLocal(notes);
                            setAdapter();
                            bottomSum.setText("Total: " + MemoList.size() + " memos");
                        });
                    }
                }
            });
        //}
    }

    @SuppressLint("NotifyDataSetChanged")
    public void initializeMain() {
        Note Intro = new Note();
        Intro.title = "Intro: Start your own Memo+";
        String timeStamp = new SimpleDateFormat("MM.dd HH:mm").format(new Date());
        Intro.last_edit = "Newly created at " + timeStamp;
        Intro.last_save = "Local";
        Intro.type = "Introduction";
        Intro.id = 0;
        String content = "{\"content\": \"Try our new functions from the advancing AI. Basic functions can be adopted by pressing buttons below.\"," +
                "\"type\": \"text\"}";
        Log.d("notes", content);
        Intro.files = new ArrayList<>();
        Intro.files.add(content);
        noteDao.insertNote(Intro);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            List<Note> onlyOne = new ArrayList<>();
            onlyOne.add(Intro);
            fetchFromLocal(onlyOne);
            setAdapter();
            adapter.notifyDataSetChanged();
            bottomSum.setText("Total: " + MemoList.size() + " memos");
        });
    }

    void fetchFromLocal(List<Note> notes) {
        MemoList.clear();
        Log.d("notes", String.valueOf(notes.size()));
        for(Note note: notes) {
            if (note.id > maxID) {
                maxID = note.id;
            }
            Log.d("title-id-main", String.valueOf(maxID));
            MemoItem item = new MemoItem();
            item.title = note.title;
            Log.d("title", note.title);
            item.edit_time = note.last_edit;
            String abs = "";
            try {
                if (!note.files.isEmpty()) {
                    JSONObject jsonAbs = new JSONObject(note.files.get(0));
                    abs = jsonAbs.getString("content");
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            if (abs.length() > 18) {
                abs = abs.substring(0,18) + "...";
            }
            item.memo_abstract = abs;
            item.type = note.type;
            Log.d("type", note.type);
            item.labelNoteID = note.id;
            MemoList.add(item);
            Log.d("title", String.valueOf(MemoList.size()));
        }
    }

    public void setAdapter() {
        this.adapter = new MemoAdapter(MainActivity.this, this.MemoList);
        memoRecyclerView.setAdapter(adapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(MainActivity.this);
        memoRecyclerView.setLayoutManager(layoutManager);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                this.login = data.getBooleanExtra("login", false);
                Log.d("home-login", String.valueOf(this.login));
            }
        }
    }


    static class MemoAdapter extends RecyclerView.Adapter<MemoAdapter.MemoViewHolder> {
        LayoutInflater inflater;
        List<MemoItem> MemoList;
        Context context;

        public MemoAdapter(Context context, List<MemoItem> memoItems) {
            this.inflater = LayoutInflater.from(context);
            this.MemoList = memoItems;
            this.context = context;
        }
        @NonNull
        @Override
        public MemoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = inflater.inflate(R.layout.memo_item, parent, false);
            return new MemoViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull MemoViewHolder holder, int position) {
            MemoItem item = MemoList.get(position);
            holder.titleView.setText(item.title);
            holder.abstractView.setText(item.memo_abstract);
            holder.timeView.setText("Last edit: " + item.edit_time);
            holder.type.setText(item.type);
            holder.noteID = item.labelNoteID;
            holder.background.setOnClickListener(v -> {
                Intent intent = new Intent(context, MemoContent.class);
                intent.putExtra("TITLE", holder.titleView.getText().toString());
                intent.putExtra("TIME", holder.timeView.getText().toString());
                intent.putExtra("TAG", holder.type.getText().toString());
                Log.d("type-show", holder.type.getText().toString());
                intent.putExtra("ID", item.labelNoteID);
                context.startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return MemoList.size();
        }

        static class MemoViewHolder extends RecyclerView.ViewHolder {
            TextView titleView;
            TextView abstractView;
            TextView timeView;
            TextView bottomBar;
            ImageView typeIcon;
            TextView type;
            TextView background;
            int noteID = -1;

            public MemoViewHolder(@NonNull View itemView) {
                super(itemView);
                titleView = itemView.findViewById(R.id.memo_title);
                abstractView = itemView.findViewById(R.id.memo_abstract);
                timeView = itemView.findViewById(R.id.edit_time);
                bottomBar = itemView.findViewById(R.id.item_bottom_line);
                typeIcon = itemView.findViewById(R.id.type_icon);
                type = itemView.findViewById(R.id.type);
                background = itemView.findViewById(R.id.item_background);
            }
        }
    }
}