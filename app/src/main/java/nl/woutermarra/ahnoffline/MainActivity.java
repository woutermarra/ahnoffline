package nl.woutermarra.ahnoffline;

import android.app.DownloadManager;
import android.content.Context;

import com.esri.android.map.GraphicsLayer;
import com.esri.android.map.LocationDisplayManager;
import com.esri.core.geometry.GeometryEngine;
import com.esri.core.geometry.Point;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.esri.android.map.MapView;
import com.esri.android.map.RasterLayer;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.Graphic;
import com.esri.core.raster.FileRasterSource;
import com.esri.core.renderer.BlendRenderer;
import com.esri.core.renderer.HillshadeRenderer;
import com.esri.core.renderer.StretchParameters;
import com.esri.core.renderer.StretchRenderer;
import com.esri.core.symbol.PictureMarkerSymbol;

import java.io.File;
import java.io.FileNotFoundException;

public class MainActivity extends AppCompatActivity {
    MapView mMapView;
    public String TAG = "AhnOffline";
    PictureMarkerSymbol locationIcon;
    GraphicsLayer graphicsLayer = null;
    LocationDisplayManager lDisplayManager;


    // Called when the activity is first created.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        locationIcon = new PictureMarkerSymbol(getApplicationContext(), ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation));

        // Download file
        String downloadURL = getString(R.string.gisdataurl) + "/" + getString(R.string.filename);
        String rasterPath = DownloadFile(downloadURL, getString(R.string.filename));
        Log.d(TAG, rasterPath);

        // After the content of this Activity is set, the map can be accessed programmatically from the layout.
        mMapView = (MapView) findViewById(R.id.map);

        FileRasterSource rasterSource;
        try {
            rasterSource = new FileRasterSource(rasterPath);
            RasterLayer rasterLayer = new RasterLayer(rasterSource);

            StretchParameters stretchParams = new StretchParameters.MinMaxStretchParameters();
            StretchRenderer renderer = new StretchRenderer();
            renderer.setStretchParameters(stretchParams);
            HillshadeRenderer renderer2 = new HillshadeRenderer();
            //BlendRenderer renderer3 = new BlendRenderer();
            rasterLayer.setRenderer(renderer2);

            mMapView.addLayer(rasterLayer);
        } catch (IllegalArgumentException ie) {
            Log.d(TAG, "null or empty path");
        } catch (FileNotFoundException fe) {
            Log.d(TAG, "raster file doesn't exist");
        } catch (RuntimeException re) {
            Log.d(TAG, "raster file can't be opened" + re);
        }

        // start location manager
        lDisplayManager = mMapView.getLocationDisplayManager();
        lDisplayManager.start();

        // make graphics layer
        graphicsLayer = new GraphicsLayer();
        mMapView.addLayer(graphicsLayer);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items from the Menu XML to the action bar, if present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        /*
        // Get the basemap switching menu items.
        mStreetsMenuItem = menu.getItem(0);
        mTopoMenuItem = menu.getItem(1);
        mGrayMenuItem = menu.getItem(2);
        mOceansMenuItem = menu.getItem(3);

        // Also set the topo basemap menu item to be checked, as this is the default.
        mTopoMenuItem.setChecked(true);*/

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_locate:
                if (mMapView.isLoaded()) {
                    updateLocation();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);

        }
    }

    private void updateLocation(){
        // get user location

        Location location = lDisplayManager.getLocation();
        // convert to lat/lon
        double lat = location.getLatitude();
        double lon = location.getLongitude();

        Log.d(TAG, Double.toString(lat) + ", " + Double.toString(lon));
        Point p = new Point(lon, lat);
        p = (Point) GeometryEngine.project(p,
                SpatialReference.create(4326),
                mMapView.getSpatialReference());

        Log.d(TAG, mMapView.getSpatialReference().toString());
        mMapView.centerAt(p, true);
        graphicsLayer.addGraphic(new Graphic(p, locationIcon));
    }


    @Override
    protected void onPause() {
        super.onPause();

        // Call MapView.pause to suspend map rendering while the activity is paused, which can save battery usage.
        if (mMapView != null)
        {
            mMapView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Call MapView.unpause to resume map rendering when the activity returns to the foreground.
        if (mMapView != null)
        {
            mMapView.unpause();
        }
    }


    public String DownloadFile(String fileURL, String fileName) {
        try {

            // check if external storage is writeable
            boolean mExternalStorageAvailable = false;
            boolean mExternalStorageWriteable = false;
            String state = Environment.getExternalStorageState();

            if (Environment.MEDIA_MOUNTED.equals(state)) {
                // We can read and write the media
                mExternalStorageAvailable = mExternalStorageWriteable = true;
                Log.d("Downloader", "SD available and writable");
            } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                // We can only read the media
                mExternalStorageAvailable = true;
                mExternalStorageWriteable = false;
                Log.d("Downloader", "SD not writable");
                return "";
            } else {
                // Something else is wrong. It may be one of many other states, but all we need
                //  to know is we can neither read nor write
                mExternalStorageAvailable = mExternalStorageWriteable = false;
                Log.d("Downloader", "SD not available");
                return "";
            }

            // Location to store
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File root = new File(downloadDir.getAbsolutePath(), "GisData");
            String subfolder = "/Gisdata/";
            File newFile = new File(root, fileName);

            Log.d("Downloader", "newFile: " + newFile.toString());

            // check if file exists
            if(newFile.exists()) {
                Log.d("Downloader", "Skip downloading, file already exists: " + newFile.toString());
                Log.d("Downloader", "Skip downloading, file already exists: (get) " + newFile.getAbsolutePath());

                return newFile.getPath();
            }

            // make folder
            root.mkdir();

            // use download manager to download file
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(fileURL));
            request.setDescription("Downloading gisdata");
            request.setTitle(newFile.toString());
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subfolder + fileName);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);

//            Log.d("Downloader", "newFileUriPath" + newFileUri.getPath());

            Log.d("Downloader", DownloadManager.COLUMN_LOCAL_FILENAME);
            manager.enqueue(request);

            /*
            // Connect to file
            URL u = new URL(fileURL);
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setDoOutput(true);
            c.connect();

            // write in to f
            InputStream in = c.getInputStream();
            FileOutputStream f = new FileOutputStream(newFile);

            byte[] buffer = new byte[1024];
            int len1 = 0;
            while ((len1 = in.read(buffer)) > 0) {
                f.write(buffer, 0, len1);
            }
            f.close();
            */

            Log.d("Downloader", "Downloaded: " + newFile.getPath());
            return newFile.getPath();

        } catch (Exception e) {
            Log.d("Downloader", e.getMessage());
            return "";
        }

    }



}