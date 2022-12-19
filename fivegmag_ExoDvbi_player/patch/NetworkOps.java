/************************************************************************************************************

* Copyright --- 2022, Dolby Laboratories Inc.

* Licensed under the License terms and conditions for use, reproduction, and distribution of 5G-MAG
* software (the “License”).
* You may not use this file except in compliance with the License. You may obtain a copy of the License
* at https://www.5g-mag.com/reference-tools.
* Unless required by applicable law or agreed to in writing, software distributed under the License is
* distributed on an “AS IS” BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and limitations under the License.

 ************************************************************************************************************/


package com.google.android.exoplayer2.demo;

import android.content.Context;
import android.os.AsyncTask;
import com.google.android.exoplayer2.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class NetworkOps extends AsyncTask<Void, Void, Void> {

    private Context mContext;

    public NetworkOps (Context context){
        mContext = context;
    }

    @Override
    protected Void doInBackground(Void... voids) {

        try {
            //Point to the remote file and open connection:
            URL service_url = new URL("http://stage.sofiadigital.fi/dvb/dvb-i-reference-application/backend/servicelists/example.xml");
            URLConnection cn = service_url.openConnection();
            cn.connect();
            InputStream stream = cn.getInputStream();

            //Download preparations:
            File downloadingXMLFile = new File(mContext.getCacheDir(),"example.xml");
            FileOutputStream out = new FileOutputStream(downloadingXMLFile);
            byte buf[] = new byte[1024];
            int bufferLength = 0;
            int downloadedSize = 0;

            //Download file:
            while ((bufferLength = stream.read(buf)) > 0) {
                Log.d("dlb", "Downloading...");
                //add the data in the buffer to the output stream file
                out.write(buf, 0, bufferLength);
                //add up the size so we know how much is downloaded
                downloadedSize += bufferLength;
                Log.d("dlb", Integer.toString(downloadedSize));
            }

            //Close output stream
            out.close();
            //handle exceptions
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


}

