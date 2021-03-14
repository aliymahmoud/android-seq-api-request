package com.example.android.sequentialproject;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.android.sequentialproject.data.earthQuakeContract.earthQuakeEntry;
import com.example.android.sequentialproject.data.earthQuakeDbHelper;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    /** URL for earthquake data from the USGS dataset from 1-1-2020 to 1-10-2020*/
    private static final String[] USGS_REQUEST_URLS =
            {"https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2020-01-01&endtime=2020-10-1&minmagnitude=1&maxmagnitude=3&limit=10",
             "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2020-01-01&endtime=2020-10-1&minmagnitude=4.7&maxmagnitude=6&limit=10",
             "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&starttime=2020-01-01&endtime=2020-10-1&minmagnitude=6.9"};
    static int requestsCounter=0;


    earthQuakeDbHelper mDbHelper = new earthQuakeDbHelper(this);
    SQLiteDatabase mDb;

    // Create a fake list of earthquakes.
    ArrayList<earthQuake> earthquakes = new ArrayList<>();

    // Find a reference to the ListView in the layout
    ListView earthquakeListView;

    // Find a reference to the buttons in the layout
    Button requestButton;
    Button saveButton;

    /** An object to execute tasks on the background thread*/
    earthQuakeAsyncTask task;

    /** Projection for database queries */
    String[] projection = new String[] {
            earthQuakeEntry.COLUMN_MAGNITUDE,
            earthQuakeEntry.COLUMN_DATE,
            earthQuakeEntry.COLUMN_LOCATION,
            earthQuakeEntry.COLUMN_URL
    };
    /** Object to store data from database.query() method */
    Cursor cursor;

    /** Object for the progress dialog to show when getting data from api and storing in the database*/
    ProgressDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestButton = findViewById(R.id.request_button);
        saveButton = findViewById(R.id.save_button);
        saveButton.setEnabled(false);
        Button displayButton = findViewById(R.id.display_button);
        earthquakeListView = (ListView) findViewById(R.id.list);

        // Find and set empty view on the ListView, so that it only shows when the list has 0 items.
        View emptyView = findViewById(R.id.empty_view);
        earthquakeListView.setEmptyView(emptyView);

        if(getCount() == 0)
            displayButton.setEnabled(false);
        else
            requestButton.setEnabled(false);


        earthquakeListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                earthQuake quake =earthquakes.get(position);

                Uri webpage = Uri.parse(quake.getURL());
                Intent intent = new Intent(Intent.ACTION_VIEW, webpage);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            }
        });

        requestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog = ProgressDialog.show(MainActivity.this, "Getting data", "Please wait...", true);
                requestButton.setEnabled(false);
                sendRequest(requestsCounter);
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                insertData();
                saveButton.setEnabled(false);
                displayButton.setEnabled(true);
            }
        });

        displayButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                updateUi();
            }
        });

    }

    public int getCount()
    {
        mDb = mDbHelper.getReadableDatabase();

        cursor = mDb.query(earthQuakeEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null);
        return cursor.getCount();
    }

    /**
     * performing request for api with different urls by calling excute function with the url
     * @param counter the index of the next url to send request
     */
    public void sendRequest(int counter)
    {
        task = new earthQuakeAsyncTask();
        task.execute(USGS_REQUEST_URLS[counter]);
        ++counter;
        Toast.makeText(MainActivity.this, "Request[" + counter + "] has been sent", Toast.LENGTH_SHORT).show();
    }

    /**
     * Insert the earthquakes data into the database
     */
    public void insertData()
    {
        mDb = mDbHelper.getWritableDatabase();
        int i;
        ContentValues values = new ContentValues();
        for(i = 0; i < earthquakes.size(); i++)
        {
            values.put(earthQuakeEntry.COLUMN_MAGNITUDE,earthquakes.get(i).getMagnitude());
            values.put(earthQuakeEntry.COLUMN_LOCATION,earthquakes.get(i).getLocation());
            values.put(earthQuakeEntry.COLUMN_DATE,earthquakes.get(i).getTimeInMilliseconds());
            values.put(earthQuakeEntry.COLUMN_URL,earthquakes.get(i).getURL());
            mDb.insert(earthQuakeEntry.TABLE_NAME,null,values);
        }
        Toast.makeText(this, R.string.data_saved, Toast.LENGTH_SHORT).show();
        earthquakes = new ArrayList<>();
    }


    /**
     * assign returned ArrayList of earthquakes to the earthquakes ArrayList
     * @param jsonResponse
     */
    private void getEarthQuakes(String jsonResponse) {
        // Create a fake list of earthquake locations.
        earthquakes.addAll(Utilities.extractEarthquakes(jsonResponse));
    }

    /**
     * Update the UI with the earthquakes Arraylist.
     */
    public void updateUi()
    {
        // load earthquakes from database to earthquakes arraylist
        loadDataFromDb();

        // attach the earthquakes arraylist to the adapter
        earthQuakeAdapter adapter = new earthQuakeAdapter(this,earthquakes);

        // Find a reference to the {@link ListView} in the layout
        ListView earthquakeListView = (ListView) findViewById(R.id.list);

        // Set the adapter on the {@link ListView}
        // so the list can be populated in the user interface
        earthquakeListView.setAdapter(adapter);
    }

    /**
     * Load earthquakes data from the database.
     */

    public void loadDataFromDb()
    {
        earthquakes = new ArrayList<>();
        mDb = mDbHelper.getReadableDatabase();

        cursor = mDb.query(earthQuakeEntry.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null);

        // Toast to inform the user that the data being loaded from the database
        Toast.makeText(this, R.string.loading_data, Toast.LENGTH_SHORT).show();

        int magnitudeColumnindex = cursor.getColumnIndex(earthQuakeEntry.COLUMN_MAGNITUDE);
        int locationColumnindex = cursor.getColumnIndex(earthQuakeEntry.COLUMN_LOCATION);
        int dateColumnindex = cursor.getColumnIndex(earthQuakeEntry.COLUMN_DATE);
        int urlColumnindex = cursor.getColumnIndex(earthQuakeEntry.COLUMN_URL);

        while(cursor.moveToNext())
        {
            earthquakes.add(new earthQuake(cursor.getFloat(magnitudeColumnindex),
                    cursor.getString(locationColumnindex),
                    cursor.getLong(dateColumnindex),
                    cursor.getString(urlColumnindex)));
        }

        // Always close the cursor when you're done reading from it. This releases all its
        // resources and makes it invalid.
        cursor.close();

    }


    private class earthQuakeAsyncTask extends AsyncTask<String, Void, String>
    {
        @Override
        protected String doInBackground(String... urls) {
            // Don't perform the request if there are no URLs, or the first URL is null.
            if (urls.length < 1 || urls[0] == null) {
                return null;
            }
            ++requestsCounter;

            String result = Utilities.fetchEarthquakeData(urls[0]);
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            // If there is no result, do nothing.
            if (result == null) {
                return;
            }
            Toast.makeText(MainActivity.this, "Data from request[" + requestsCounter + "] has been received", Toast.LENGTH_SHORT).show();

            getEarthQuakes(result);
            if (requestsCounter == 3)
            {
                saveButton.setEnabled(true);
                dialog.dismiss();
            }
            else
                sendRequest(requestsCounter);
        }
    }
}