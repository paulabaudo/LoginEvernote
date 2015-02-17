package paulabaudo.globant.com.loginevernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.evernote.client.android.AsyncLinkedNoteStoreClient;
import com.evernote.client.android.EvernoteSession;
import com.evernote.client.android.EvernoteUtil;
import com.evernote.client.android.OnClientCallback;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Data;
import com.evernote.edam.type.LinkedNotebook;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Resource;
import com.evernote.thrift.transport.TTransportException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity {

    private static final String CONSUMER_KEY = "app791";
    private static final String CONSUMER_SECRET = "47dd457bf08c50da";
    private static final EvernoteSession.EvernoteService EVERNOTE_SERVICE = EvernoteSession.EvernoteService.SANDBOX;
    private EvernoteSession mEvernoteSession;
    private Button mButtonLogin;
    private Button mButtonNewNote;
    private static final String LOGTAG = "MainActivity";
    private Button mButtonSelect;
    private String mSelectedNotebookGuid;

    private OnClientCallback<Note> mNoteCreateCallback = new OnClientCallback<Note>() {
        @Override
        public void onSuccess(Note note) {
            Toast.makeText(getApplicationContext(), "Nota guardada", Toast.LENGTH_LONG).show();
            removeDialog(101);
        }

        @Override
        public void onException(Exception exception) {
            Log.e(LOGTAG, "Error saving note", exception);
            Toast.makeText(getApplicationContext(), "Error al guardar nota", Toast.LENGTH_LONG).show();
            removeDialog(101);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mEvernoteSession = EvernoteSession.getInstance(this, CONSUMER_KEY, CONSUMER_SECRET, EVERNOTE_SERVICE, true);
        prepareButtonLogin();
        prepareButtonSelect();
        prepareButtonNewNote();
    }

    private void prepareButtonSelect() {
        mButtonSelect = (Button) findViewById(R.id.button_select);
        mButtonSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectNotebook();
            }
        });
    }

    private void prepareButtonNewNote() {
        mButtonNewNote = (Button) findViewById(R.id.button_new_note);
        mButtonNewNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveNote();
            }
        });
    }

    private byte[] convertBitmapImageToByteArray(Bitmap image) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    public void saveNote() {
        Bitmap image = BitmapFactory.decodeResource(getResources(), R.drawable.flor);
        byte[] imageByte = convertBitmapImageToByteArray(image);

        String title = "Final test title";
        String content = "Final test content";
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(content)) {
            Toast.makeText(getApplicationContext(), "Content vacío", Toast.LENGTH_LONG).show();
            return;
        }

        Note note = new Note();
        note.setTitle(title);

        //TODO: Creating data
        Data data = new Data();
        data.setBodyHash(EvernoteUtil.hash(imageByte));
        data.setBody(imageByte);

        //TODO: Creating resource
        Resource resource = new Resource();
        resource.setMime("image/png");
        resource.setData(data);

        String tag = EvernoteUtil.createEnMediaTag(resource);

        List<Resource> resourceList = new ArrayList<>();
        resourceList.add(resource);
        note.setResources(resourceList);

        //TODO: line breaks need to be converted to render in ENML
        String noteBody = EvernoteUtil.NOTE_PREFIX + content + "<br/>" + tag + EvernoteUtil.NOTE_SUFFIX;

        note.setContent(noteBody);

        if(!mEvernoteSession.getAuthenticationResult().isAppLinkedNotebook()) {
            //If User has selected a notebook guid, assign it now
            if (!TextUtils.isEmpty(mSelectedNotebookGuid)) {
                note.setNotebookGuid(mSelectedNotebookGuid);
            }
            showDialog(101);
            try {
                mEvernoteSession.getClientFactory().createNoteStoreClient().createNote(note, mNoteCreateCallback);
            } catch (TTransportException exception) {
                Log.e(LOGTAG, "Error creating notestore", exception);
                Toast.makeText(getApplicationContext(), "Error creating notestore", Toast.LENGTH_LONG).show();
                removeDialog(101);
            }
        } else {
            createNoteInAppLinkedNotebook(note, mNoteCreateCallback);
        }
    }

    protected void createNoteInAppLinkedNotebook(final Note note, final OnClientCallback<Note> createNoteCallback) {
        showDialog(101);
        invokeOnAppLinkedNotebook(new OnClientCallback<Pair<AsyncLinkedNoteStoreClient, LinkedNotebook>>() {
            @Override
            public void onSuccess(final Pair<AsyncLinkedNoteStoreClient, LinkedNotebook> pair) {
                // Rely on the callback to dismiss the dialog
                pair.first.createNoteAsync(note, pair.second, createNoteCallback);
            }

            @Override
            public void onException(Exception exception) {
                Log.e(LOGTAG, "Error creating linked notestore", exception);
                Toast.makeText(getApplicationContext(), "Error al crear NoteStore", Toast.LENGTH_LONG).show();
                removeDialog(101);
            }
        });
    }

    protected void invokeOnAppLinkedNotebook(final OnClientCallback<Pair<AsyncLinkedNoteStoreClient, LinkedNotebook>> callback) {
        try {
            // We need to get the one and only linked notebook
            mEvernoteSession.getClientFactory().createNoteStoreClient().listLinkedNotebooks(new OnClientCallback<List<LinkedNotebook>>() {
                @Override
                public void onSuccess(List<LinkedNotebook> linkedNotebooks) {
                    // We should only have one linked notebook
                    if (linkedNotebooks.size() != 1) {
                        Log.e(LOGTAG, "Error getting linked notebook - more than one linked notebook");
                        callback.onException(new Exception("Not single linked notebook"));
                    } else {
                        final LinkedNotebook linkedNotebook = linkedNotebooks.get(0);
                        mEvernoteSession.getClientFactory().createLinkedNoteStoreClientAsync(linkedNotebook, new OnClientCallback<AsyncLinkedNoteStoreClient>() {
                            @Override
                            public void onSuccess(AsyncLinkedNoteStoreClient asyncLinkedNoteStoreClient) {
                                // Finally create the note in the linked notebook
                                callback.onSuccess(new Pair<AsyncLinkedNoteStoreClient, LinkedNotebook>(asyncLinkedNoteStoreClient, linkedNotebook));
                            }

                            @Override
                            public void onException(Exception exception) {
                                callback.onException(exception);
                            }
                        });
                    }
                }

                @Override
                public void onException(Exception exception) {
                    callback.onException(exception);
                }
            });
        } catch (TTransportException exception) {
            callback.onException(exception);
        }
    }

    private void prepareButtonLogin() {
        mButtonLogin = (Button) findViewById(R.id.button_login);

        mButtonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEvernoteSession.authenticate(MainActivity.this);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            // Update UI when oauth activity returns result
            case EvernoteSession.REQUEST_CODE_OAUTH:
                if (resultCode == Activity.RESULT_OK) {

                } else {
                    Toast.makeText(this, "Not success", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    public void selectNotebook() {
        if(mEvernoteSession.isAppLinkedNotebook()) {
            Toast.makeText(getApplicationContext(), "No puede listar", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            mEvernoteSession.getClientFactory().createNoteStoreClient().listNotebooks(new OnClientCallback<List<Notebook>>() {
                int mSelectedPos = -1;

                @Override
                public void onSuccess(final List<Notebook> notebooks) {
                    CharSequence[] names = new CharSequence[notebooks.size()];
                    int selected = -1;
                    Notebook notebook = null;
                    for (int index = 0; index < notebooks.size(); index++) {
                        notebook = notebooks.get(index);
                        names[index] = notebook.getName();
                        if (notebook.getGuid().equals(mSelectedNotebookGuid)) {
                            selected = index;
                        }
                    }

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                    builder
                            .setSingleChoiceItems(names, selected, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mSelectedPos = which;
                                }
                            })
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (mSelectedPos > -1) {
                                        mSelectedNotebookGuid = notebooks.get(mSelectedPos).getGuid();
                                    }
                                    dialog.dismiss();
                                }
                            })
                            .create()
                            .show();
                }

                @Override
                public void onException(Exception exception) {
                    Log.e(LOGTAG, "Error listing notebooks", exception);
                    Toast.makeText(getApplicationContext(), "Error al listar notebooks", Toast.LENGTH_LONG).show();
                    removeDialog(101);
                }
            });
        } catch (TTransportException exception) {
            Log.e(LOGTAG, "Error creating notestore", exception);
            Toast.makeText(getApplicationContext(), "Error al crear notestore", Toast.LENGTH_LONG).show();
            removeDialog(101);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
