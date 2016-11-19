package hk.org.ust.csit.tryonglasses;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import java.util.ArrayList;

public class SelectGlassesActivity extends AppCompatActivity {

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    ArrayList<Integer> parms = new ArrayList<Integer>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_glasses);
        GridView gridview = (GridView) findViewById(R.id.gridview);
        gridview.setAdapter(new ImageAdapter(this));


        gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v,
                                    int position, long id) {

                ImageView clickedView = (ImageView)v;

                int resId = ImageAdapter.mThumbIds[position];

                if (!v.isSelected()) {
                    v.setSelected(true);
                    v.setBackgroundColor(Color.rgb(0, 255, 255));

                    if(!parms.contains(resId)){
                        parms.add(resId);
                    }
                }else{
                    v.setSelected(false);
                    v.setBackgroundColor(Color.rgb(255, 255, 255));
                    if(parms.contains(resId)){
                        parms.remove(resId);
                    }
                }
                Toast.makeText(getApplicationContext(),"Check ed"+resId,
                        Toast.LENGTH_SHORT).show();

            }
        });

        final Button button = (Button) findViewById(R.id.btnTryon);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(SelectGlassesActivity.this, CameraActivity.class);
                intent.putIntegerArrayListExtra("imageArray", parms);

                Toast.makeText(getApplicationContext(),"Check ed"+parms,
                        Toast.LENGTH_SHORT).show();
                startActivity(intent);
            }
        });

    }



}
