package name.agapoff.oresund;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.view.ContextThemeWrapper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class LoadLocationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_location);

        LocationDbHelper mLocationDbHelper = new LocationDbHelper(getBaseContext());

        // Gets the data repository in write mode
        SQLiteDatabase db = mLocationDbHelper.getReadableDatabase();

        // Define a projection that specifies columns from the database

        String[] projection = {
                "_id",
                "name",
                "latitude",
                "longitude"
        };

        // How you want the results sorted in the resulting Cursor
        String sortOrder = "name ASC";

        Cursor c = db.query(
            "location",  // The table to query
            projection,                               // The columns to return
            null,                                // The columns for the WHERE clause
            null,                            // The values for the WHERE clause
            null,                                     // don't group the rows
            null,                                     // don't filter by row groups
            sortOrder                                 // The sort order
        );
        // Cursor c = db.rawQuery("SELECT * FROM location", null);

        // Find ListView to populate
        final ListView lvItems = (ListView) findViewById(R.id.location_list);

        // Setup cursor adapter using cursor from last step
        final ListCursorAdapter listAdapter = new ListCursorAdapter(this, c);

        // Attach cursor adapter to the ListView
        lvItems.setAdapter(listAdapter);

        lvItems.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                Cursor item = (Cursor) parent.getItemAtPosition(position);   // getString(cursor.getColumnIndexOrThrow("name"));
                final int db_id = item.getInt(item.getColumnIndexOrThrow("_id"));
                final String name = item.getString(item.getColumnIndexOrThrow("name"));
                final double latitude = item.getDouble(item.getColumnIndexOrThrow("latitude"));
                final double longitude = item.getDouble(item.getColumnIndexOrThrow("longitude"));


                AlertDialog.Builder builder = new AlertDialog.Builder(LoadLocationActivity.this);
                builder.setMessage(MainActivity.convertCoordToWGS(latitude) + " " + MainActivity.convertCoordToWGS(longitude))
                       .setTitle(name)
                       .setPositiveButton(R.string.go, new DialogInterface.OnClickListener() {
                           public void onClick(DialogInterface dialog, int id) {
                               //Toast.makeText(LoadLocationActivity.this, "Go: " + latitude, Toast.LENGTH_SHORT).show();
                               Intent data = new Intent();
                               data.putExtra("latitude", latitude);
                               data.putExtra("longitude", longitude);
                               setResult(RESULT_OK, data);
                               LoadLocationActivity.this.finish();
                           }
                       })
                       .setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
                           public void onClick(DialogInterface dialog, int id) {
                               if (deleteLocation(db_id)) {
                                   Toast.makeText(LoadLocationActivity.this, R.string.location_deleted, Toast.LENGTH_SHORT).show();

                                   // I dunno how to remove item from CursorAdapter ListView so I just restart the Activity
                                   //Intent intent = getIntent();
                                   //finish();
                                   //startActivity(intent,1);
                               }
                               else
                                   Toast.makeText(LoadLocationActivity.this, R.string.location_not_deleted, Toast.LENGTH_SHORT).show();
                           }
                       })
                       .setIcon(android.R.drawable.btn_star)
                       .show();

                //AlertDialog dialog = builder.create();
                //dialog.show();
            }

        });

    }

    public boolean deleteLocation(int id) {
        LocationDbHelper mLocationDbHelper = new LocationDbHelper(getBaseContext());

        // Gets the data repository in write mode
        SQLiteDatabase db = mLocationDbHelper.getWritableDatabase();

        // delete the row
        return db.delete("location", "_id=" + id, null) > 0;
    }
}
