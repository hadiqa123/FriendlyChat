package com.example.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.InputStream;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final int RC_SIGN_IN = 1;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;


    private String mUsername;
    ///firebase
    private DatabaseReference mDatabaseRef;
    private FirebaseDatabase mfire;
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
private FirebaseStorage mFirebaseStorage;
private StorageReference mstorageReference;
    //child listeners
    private ChildEventListener childEventListener;
///

    private static final int RC_PHOTO_PICKER = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;
        mAuth = FirebaseAuth.getInstance();
        mfire = FirebaseDatabase.getInstance();
        mDatabaseRef = mfire.getReference().child("messages");
        mFirebaseStorage=FirebaseStorage.getInstance();
        mstorageReference=mFirebaseStorage.getReference().child("upload_photos");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FreindlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                FreindlyMessage freindlyMessage = new FreindlyMessage((mMessageEditText.getText().toString()), mUsername, null);
                mfire.getReference("messages").push().setValue(freindlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });
        childEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                FreindlyMessage freindlyMessage = dataSnapshot.getValue(FreindlyMessage.class);
                mMessageAdapter.add(freindlyMessage);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }

        };
        mDatabaseRef.addChildEventListener(childEventListener);

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                if (firebaseUser != null) {
                    Toast.makeText(MainActivity.this, "You're now signed in. Welcome to FriendlyChat.", Toast.LENGTH_SHORT).show();
                    onSignedInInitialize(firebaseUser.getDisplayName());
                } else {
                   /* startActivityForResult(AuthUI.getInstance()
                            .createSignInIntentBuilder()
                            .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(Arrays.asList(
                                            new AuthUI.IdpConfig.EmailBuilder().build(),
                                           new AuthUI.IdpConfig.GoogleBuilder().build(),
                                            new AuthUI.IdpConfig.FacebookBuilder().build()))
                                    .build(),
                            RC_SIGN_IN);
                            */
                    List<AuthUI.IdpConfig> providers = Arrays.asList(
                            new AuthUI.IdpConfig.EmailBuilder().build(),
                            new AuthUI.IdpConfig.GoogleBuilder().build());

                    // Create and launch sign-in intent
                    onSignedOutCleanup();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setAvailableProviders(providers)
                                    .build(),
                            RC_SIGN_IN);
                    ;
                }

            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }


    @Override
    protected void onPause() {
        super.onPause();
        mAuth.removeAuthStateListener(authStateListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAuth.addAuthStateListener(authStateListener);
    }

    private void onSignedInInitialize(String username) {
        mUsername = username;
        attachDatabaseReadListener();
    }

    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    private void attachDatabaseReadListener() {
        if (childEventListener == null) {
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FreindlyMessage friendlyMessage = dataSnapshot.getValue(FreindlyMessage.class);
                    mMessageAdapter.add(friendlyMessage);
                }

                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                }

                public void onChildRemoved(DataSnapshot dataSnapshot) {
                }

                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                }

                public void onCancelled(DatabaseError databaseError) {
                }
            };
            mDatabaseRef.addChildEventListener(childEventListener);
        }
    }
///detach listener
    private void detachDatabaseReadListener() {
        if (childEventListener != null) {
            mDatabaseRef.removeEventListener(childEventListener);
            childEventListener = null;
        }
    }
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // Sign-in succeeded, set up the UI
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                // Sign in was canceled by the user, finish the activity
                Toast.makeText(this, "Sign in canceled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }    /// image reference and get uri
            else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK){


                Uri mUri = data.getData();

            StorageReference MphotoREF = mstorageReference.getStorage().getReference().child(mUri.getLastPathSegment());
//upload to firebase

                MphotoREF.putFile(mUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        // When the image has successfully uploaded, we get its download URL

                        Task<Uri> downloadUrl = taskSnapshot.getStorage().getDownloadUrl();
                        FreindlyMessage friendlyMessage = new FreindlyMessage(null, mUsername, downloadUrl.toString());
                        mDatabaseRef.push().setValue(friendlyMessage);

                    }

                });
            }
        }


    //sign out
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}