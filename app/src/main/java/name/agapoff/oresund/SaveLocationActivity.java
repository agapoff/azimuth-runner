package name.agapoff.oresund;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SaveLocationActivity extends AppCompatActivity {
    double latitude;
    double longitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_save_location);


        Intent intent = getIntent();
        latitude = intent.getDoubleExtra(MainActivity.LATITUDE, 0);
        longitude = intent.getDoubleExtra(MainActivity.LONGITUDE, 0);

        TextView tvLatitude = (TextView) findViewById(R.id.latitude_to_save);
        TextView tvLongitude = (TextView) findViewById(R.id.longitude_to_save);


        tvLatitude.append(" " + MainActivity.convertCoordToWGS(latitude));
        tvLongitude.append(" " + MainActivity.convertCoordToWGS(longitude));

    }

    public void saveLocation(View view) {
        LocationDbHelper mLocationDbHelper = new LocationDbHelper(getBaseContext());
        //Toast.makeText(SaveLocationActivity.this, "Hello: ", Toast.LENGTH_SHORT).show();
        EditText etLocationName = (EditText) findViewById(R.id.location_name);
        String locationName = etLocationName.getText().toString();

        // Gets the data repository in write mode
        SQLiteDatabase db = mLocationDbHelper.getWritableDatabase();

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put("name", locationName);
        values.put("latitude", latitude);
        values.put("longitude", longitude);

        long newRowId = db.insert("location", null, values);
        db.close();
        Toast.makeText(SaveLocationActivity.this, R.string.location_saved, Toast.LENGTH_SHORT).show();
        SaveLocationActivity.this.finish();
    }
}
